package dev.kord.core

import dev.kord.common.annotation.KordInternal
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.Event
import dev.kord.core.event.automoderation.*
import dev.kord.core.event.channel.*
import dev.kord.core.event.channel.thread.*
import dev.kord.core.event.guild.*
import dev.kord.core.event.message.*
import dev.kord.core.event.role.RoleCreateEvent
import dev.kord.core.event.role.RoleDeleteEvent
import dev.kord.core.event.role.RoleUpdateEvent
import dev.kord.core.event.user.PresenceUpdateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.Intent.*
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.json.JsonErrorCode
import dev.kord.rest.request.RestRequestException
import dev.kord.rest.route.Position
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

internal inline fun <T> catchNotFound(block: () -> T): T? {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block()
    } catch (exception: RestRequestException) {
        if (exception.status.code == 404) null
        else throw exception
    }
}

internal inline fun <T> catchDiscordError(vararg codes: JsonErrorCode, block: () -> T): T? {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block()
    } catch (exception: RestRequestException) {
        when {
            codes.isEmpty() -> null
            exception.error?.code in codes -> null
            else -> throw exception
        }
    }
}


internal fun <T : Comparable<T>> Flow<T>.sorted(): Flow<T> = flow {
    toList().sorted().forEach { emit(it) }
}

/**
 * The terminal operator that returns `true` if any of the elements match [predicate].
 * The flow's collection is cancelled when a match is found.
 */
internal suspend inline fun <T : Any> Flow<T>.any(crossinline predicate: suspend (T) -> Boolean): Boolean =
    firstOrNull { predicate(it) } != null

/**
 * The non-terminal operator that returns a new flow that will emit values of the second [flow] only after the first
 * flow finished collecting without values.
 *
 * ```kotlin
 * emptyFlow<String>().switchIfEmpty(flowOf("hello", "world")) //["hello", "world"]
 *
 * flowOf("hello", "world").switchIfEmpty(flowOf("goodbye", "world")) //["hello", "world"]
 * ```
 */
internal fun <T> Flow<T>.switchIfEmpty(flow: Flow<T>): Flow<T> = onEmpty {
    emitAll(flow)
}

/**
 * The terminal operator that returns the index of the first element emitted by the flow that matches the [predicate]
 * and then cancels flow's collection.
 * Returns `null` if the flow was empty or no element matched the [predicate].
 */
internal suspend fun <T> Flow<T>.indexOfFirstOrNull(predicate: suspend (T) -> Boolean): Int? {
    var counter = 0
    return map {
        val pair = counter to it
        counter += 1
        pair
    }
        .filter { predicate(it.second) }
        .take(1)
        .singleOrNull()?.first
}

internal fun <Batch : Collection<Item>, Item : Any, Direction : Position.BeforeOrAfter> paginate(
    start: Snowflake,
    batchSize: Int,
    itemSelector: (Batch) -> Item?,
    idSelector: (Item) -> Snowflake,
    directionSelector: (Snowflake) -> Direction,
    request: suspend (Direction) -> Batch,
): Flow<Item> = flow {

    var direction = directionSelector(start)

    while (true) {
        val batch = request(direction)
        for (item in batch) emit(item)

        if (batch.size < batchSize) break

        val item = itemSelector(batch) ?: break
        direction = directionSelector(idSelector(item))
    }
}


/**
 * Discord returns values in order newest -> oldest (big -> small) (confirmed for messages),
 * meaning that the first item returned is the one last created (youngest) in the batch.
 */
internal fun <T> youngestItem(idSelector: (T) -> Snowflake): (Collection<T>) -> T? = function@{
    if (it.size <= 1) return@function it.firstOrNull()

    val first = it.first()
    val last = it.last()

    val firstId = idSelector(first).value
    val lastId = idSelector(last).value

    if (firstId > lastId) first
    else last
}

/**
 * Discord returns values in order oldest -> newest (big -> small) (confirmed for messages),
 * meaning that the last item returned is the one first created (oldest) in the batch.
 */
internal fun <T> oldestItem(idSelector: (T) -> Snowflake): (Collection<T>) -> T? = function@{
    if (it.size <= 1) return@function it.firstOrNull()
    val first = it.first()
    val last = it.last()

    val firstId = idSelector(first).value
    val lastId = idSelector(last).value

    if (firstId < lastId) first
    else last
}

/**
 *  Selects the [Position.After] the youngest item in the batch.
 */
internal fun <T : Any> paginateForwards(
    batchSize: Int,
    start: Snowflake = Snowflake.min,
    idSelector: (T) -> Snowflake,
    request: suspend (after: Position.After) -> Collection<T>,
): Flow<T> = paginate(
    start,
    batchSize,
    itemSelector = youngestItem(idSelector),
    idSelector,
    directionSelector = Position::After,
    request,
)

/**
 *  Selects the [Position.Before] the oldest item in the batch.
 */
internal fun <T : Any> paginateBackwards(
    batchSize: Int,
    start: Snowflake = Snowflake.max,
    idSelector: (T) -> Snowflake,
    request: suspend (before: Position.Before) -> Collection<T>,
): Flow<T> = paginate(
    start,
    batchSize,
    itemSelector = oldestItem(idSelector),
    idSelector,
    directionSelector = Position::Before,
    request,
)

/**
 * Paginates the [Collection] returned by [request] with [start] as an initial reference in time.
 * [instantSelector] is used to select the new reference to fetch from.
 *
 * Termination scenarios:
 * * [Collection]'s size fall behind [batchSize].
 * * [instantSelector] returns null.
 */
internal fun <Batch : Collection<Item>, Item : Any> paginateByDate(
    batchSize: Int,
    start: Instant?,
    instantSelector: (Batch) -> Instant?,
    request: suspend (Instant) -> Batch,
): Flow<Item> = flow {

    var currentTimestamp = start ?: Clock.System.now() // get default current time as late as possible

    while (true) {
        val batch = request(currentTimestamp)
        for (item in batch) emit(item)

        if (batch.size < batchSize) break

        currentTimestamp = instantSelector(batch) ?: break
    }
}

/**
 * A special function to paginate [ThreadChannel] endpoints.
 * selects the earliest reference in time found in the response of the request on each pagination.
 * see [paginateByDate]
 */
internal fun paginateThreads(
    batchSize: Int,
    start: Instant?,
    request: suspend (Instant) -> Collection<ThreadChannel>,
) = paginateByDate(
    batchSize,
    start,
    instantSelector = { threads -> threads.minOfOrNull { it.archiveTimestamp } },
    request,
)


/**
 * Adds the necessary [Intent]s to receive the specified type of event in all variations and with all data available.
 *
 * E.g. [MessageCreateEvent] will add the [GuildMessages], [DirectMessages] and [MessageContent] intents to receive
 * messages in Guilds and DMs with the full [content][Message.content].
 *
 * Note that enabling one type of event might also enable several other types of events since most [Intent]s enable more
 * than one event.
 */
public inline fun <reified T : Event> Intents.Builder.enableEvent(): Unit = enableEvent(T::class)

@Suppress("DEPRECATION_ERROR")
@Deprecated("'Intents.IntentsBuilder' is deprecated, use 'Intents.Builder' instead.", level = DeprecationLevel.HIDDEN)
public inline fun <reified T : Event> Intents.IntentsBuilder.enableEvent(): Unit = enableEvent0(T::class)

/**
 * Adds the necessary [Intent]s to receive the specified types of [events] in all variations and with all data
 * available.
 *
 * E.g. [MessageCreateEvent] will add the [GuildMessages], [DirectMessages] and [MessageContent] intents to receive
 * messages in Guilds and DMs with the full [content][Message.content].
 *
 * Note that enabling one type of event might also enable several other types of events since most [Intent]s enable more
 * than one event.
 */
public fun Intents.Builder.enableEvents(events: Iterable<KClass<out Event>>): Unit =
    events.forEach { enableEvent(it) }

@Suppress("DEPRECATION_ERROR")
@Deprecated("'Intents.IntentsBuilder' is deprecated, use 'Intents.Builder' instead.", level = DeprecationLevel.HIDDEN)
public fun Intents.IntentsBuilder.enableEvents(events: Iterable<KClass<out Event>>): Unit =
    events.forEach { enableEvent0(it) }

/**
 * Adds the necessary [Intent]s to receive the specified types of [events] in all variations and with all data
 * available.
 *
 * E.g. [MessageCreateEvent] will add the [GuildMessages], [DirectMessages] and [MessageContent] intents to receive
 * messages in Guilds and DMs with the full [content][Message.content].
 *
 * Note that enabling one type of event might also enable several other types of events since most [Intent]s enable more
 * than one event.
 */
public fun Intents.Builder.enableEvents(vararg events: KClass<out Event>): Unit =
    events.forEach { enableEvent(it) }

@Suppress("DEPRECATION_ERROR")
@Deprecated("'Intents.IntentsBuilder' is deprecated, use 'Intents.Builder' instead.", level = DeprecationLevel.HIDDEN)
public fun Intents.IntentsBuilder.enableEvents(vararg events: KClass<out Event>): Unit =
    events.forEach { enableEvent0(it) }

/**
 * Adds the necessary [Intent]s to receive the specified type of [event] in all variations and with all data available.
 *
 * E.g. [MessageCreateEvent] will add the [GuildMessages], [DirectMessages] and [MessageContent] intents to receive
 * messages in Guilds and DMs with the full [content][Message.content].
 *
 * Note that enabling one type of event might also enable several other types of events since most [Intent]s enable more
 * than one event.
 */
@OptIn(PrivilegedIntent::class)
public fun Intents.Builder.enableEvent(event: KClass<out Event>): Unit = when (event) {
// see https://discord.com/developers/docs/topics/gateway#list-of-intents

    /*
     * events requiring a single intent:
     */

    GuildCreateEvent::class,
    GuildUpdateEvent::class,
    GuildDeleteEvent::class,

    RoleCreateEvent::class,
    RoleUpdateEvent::class,
    RoleDeleteEvent::class,

    ChannelCreateEvent::class,
    CategoryCreateEvent::class,
    DMChannelCreateEvent::class,
    NewsChannelCreateEvent::class,
    StageChannelCreateEvent::class,
    TextChannelCreateEvent::class,
    ForumChannelCreateEvent::class,
    MediaChannelCreateEvent::class,
    UnknownChannelCreateEvent::class,
    VoiceChannelCreateEvent::class,

    ChannelUpdateEvent::class,
    CategoryUpdateEvent::class,
    DMChannelUpdateEvent::class,
    NewsChannelUpdateEvent::class,
    StageChannelUpdateEvent::class,
    TextChannelUpdateEvent::class,
    ForumChannelUpdateEvent::class,
    MediaChannelUpdateEvent::class,
    UnknownChannelUpdateEvent::class,
    VoiceChannelUpdateEvent::class,

    ChannelDeleteEvent::class,
    CategoryDeleteEvent::class,
    DMChannelDeleteEvent::class,
    NewsChannelDeleteEvent::class,
    StageChannelDeleteEvent::class,
    TextChannelDeleteEvent::class,
    ForumChannelDeleteEvent::class,
    MediaChannelDeleteEvent::class,
    UnknownChannelDeleteEvent::class,
    VoiceChannelDeleteEvent::class,

    ThreadChannelCreateEvent::class,
    NewsChannelThreadCreateEvent::class,
    TextChannelThreadCreateEvent::class,
    UnknownChannelThreadCreateEvent::class,

    ThreadUpdateEvent::class,
    NewsChannelThreadUpdateEvent::class,
    TextChannelThreadUpdateEvent::class,
    UnknownChannelThreadUpdateEvent::class,

    ThreadChannelDeleteEvent::class,
    NewsChannelThreadDeleteEvent::class,
    TextChannelThreadDeleteEvent::class,
    UnknownChannelThreadDeleteEvent::class,

    ThreadListSyncEvent::class,

    ThreadMemberUpdateEvent::class,
    -> +Guilds


    MemberJoinEvent::class, MemberUpdateEvent::class, MemberLeaveEvent::class -> +GuildMembers


    GuildAuditLogEntryCreateEvent::class, BanAddEvent::class, BanRemoveEvent::class -> +GuildModeration


    EmojisUpdateEvent::class -> +GuildEmojis


    IntegrationsUpdateEvent::class, IntegrationCreateEvent::class, IntegrationUpdateEvent::class, IntegrationDeleteEvent::class -> +GuildIntegrations


    WebhookUpdateEvent::class -> +GuildWebhooks


    InviteCreateEvent::class, InviteDeleteEvent::class -> +GuildInvites


    VoiceStateUpdateEvent::class -> +GuildVoiceStates


    PresenceUpdateEvent::class -> +GuildPresences


    MessageBulkDeleteEvent::class -> +GuildMessages // no message content


    GuildScheduledEventEvent::class,
    GuildScheduledEventCreateEvent::class,
    GuildScheduledEventUpdateEvent::class,
    GuildScheduledEventDeleteEvent::class,

    GuildScheduledEventUserEvent::class,
    GuildScheduledEventUserAddEvent::class,
    GuildScheduledEventUserRemoveEvent::class,
    -> +GuildScheduledEvents


    AutoModerationRuleConfigurationEvent::class,
    AutoModerationRuleCreateEvent::class,
    AutoModerationRuleUpdateEvent::class,
    AutoModerationRuleDeleteEvent::class,
    -> +AutoModerationConfiguration


    /*
     * events requiring multiple intents:
     */

    ChannelPinsUpdateEvent::class -> {
        +Guilds
        +DirectMessages
    }

    ThreadMembersUpdateEvent::class -> {
        +Guilds
        +GuildMembers
    }

    MessageCreateEvent::class, MessageUpdateEvent::class -> {
        +GuildMessages
        +DirectMessages
        +MessageContent
    }

    MessageDeleteEvent::class -> {
        +GuildMessages
        +DirectMessages
        // no message content
    }

    ReactionAddEvent::class, ReactionRemoveEvent::class, ReactionRemoveAllEvent::class, ReactionRemoveEmojiEvent::class -> {
        +GuildMessageReactions
        +DirectMessagesReactions
    }

    TypingStartEvent::class -> {
        +GuildMessageTyping
        +DirectMessageTyping
    }

    AutoModerationActionExecutionEvent::class -> {
        +AutoModerationExecution
        +MessageContent
    }

    AutoModerationEvent::class -> {
        // supertype of AutoModerationRuleConfigurationEvent and AutoModerationActionExecutionEvent
        // -> requires intents for both
        +AutoModerationConfiguration
        +AutoModerationExecution
        +MessageContent
    }


    else -> Unit
}

@Suppress("DEPRECATION_ERROR")
@Deprecated("'Intents.IntentsBuilder' is deprecated, use 'Intents.Builder' instead.", level = DeprecationLevel.HIDDEN)
public fun Intents.IntentsBuilder.enableEvent(event: KClass<out Event>) {
    enableEvent0(event)
}

@Suppress("DEPRECATION_ERROR")
@PublishedApi
@KordInternal
internal fun Intents.IntentsBuilder.enableEvent0(event: KClass<out Event>) {
    +(Intents.Builder(flags().code).apply { enableEvent(event) }.build())
}

// Replacement of Objects.hash
internal fun hash(vararg values: Any?) = values.contentHashCode()

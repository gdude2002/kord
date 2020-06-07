package com.gitlab.kordlib.core.behavior.channel

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.common.exception.RequestException
import com.gitlab.kordlib.core.EntitySupplyStrategy
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.cache.data.MessageData
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.entity.Strategizable
import com.gitlab.kordlib.core.entity.channel.MessageChannel
import com.gitlab.kordlib.core.exception.EntityNotFoundException
import com.gitlab.kordlib.rest.builder.message.EmbedBuilder
import com.gitlab.kordlib.rest.builder.message.MessageCreateBuilder
import com.gitlab.kordlib.rest.request.RestRequestException
import com.gitlab.kordlib.rest.service.RestClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.time.ClockMark
import kotlin.time.TimeMark
import kotlin.time.seconds

/**
 * The behavior of a Discord channel that can use messages.
 */
interface MessageChannelBehavior : ChannelBehavior, Strategizable {

    /**
     * Requests to get the this behavior as a [MessageChannel] through the [strategy].
     *
     * @throws [RequestException] if something went wrong during the request.
     * @throws [EntityNotFoundException] if the channel wasn't present.
     * @throws [ClassCastException] if the channel isn't a guild message channel.
     */
    override suspend fun asChannel(): MessageChannel = super.asChannel() as MessageChannel

    /**
     * Requests to get this behavior as a [MessageChannel] through the [strategy],
     * returns null if the channel isn't present or if the channel isn't a guild channel.
     *
     * @throws [RequestException] if something went wrong during the request.
     */
    override suspend fun asChannelOrNull(): MessageChannel? = super.asChannelOrNull() as? MessageChannel

    /**
     * Requests to get all messages in this channel through the [strategy].
     *
     * Messages retrieved by this function will be emitted in chronological order (oldest -> newest).
     * Unrestricted consumption of the returned [Flow] is a potentially performance intensive operation, it is thus recommended
     * to limit the amount of messages requested by using [Flow.take], [Flow.takeWhile] or other functions that limit the amount
     * of messages requested.
     *
     * ```kotlin
     *  channel.getMessagesBefore(newer.id).takeWhile { it.id > older.id }
     * ```
     *
     * The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     */
    val messages: Flow<Message> get() = getMessagesAfter(Snowflake(0))

    /**
     * Requests to get the pinned messages in this channel.
     *
     * This property is not resolvable through cache and will always use the [RestClient] instead.
     *
     * The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     */
    val pinnedMessages: Flow<Message>
        get() = flow {
            val responses = kord.rest.channel.getChannelPins(id.value)

            for (response in responses) {
                val data = MessageData.from(response)
                emit(Message(data, kord))
            }
        }

    /**
     * Requests to create a message with only a [MessageCreateBuilder.content].
     *
     * @throws [RestRequestException] if something went wrong during the request.
     */
    suspend fun createMessage(content: String): Message = createMessage { this.content = content }

    /**
     * Requests to delete a message in this channel.
     *
     * @throws [RestRequestException] if something went wrong during the request.
     */
    suspend fun deleteMessage(id: Snowflake): Unit = kord.rest.channel.deleteMessage(this.id.value, id.value)

    /**
     * Requests to get all messages in this channel that were created **before** [messageId] through the [strategy].
     *
     * Messages retrieved by this function will be emitted in reverse-chronological older (newest -> oldest).
     *
     * The flow may use paginated requests to supply messages, [limit] will limit the maximum number of messages
     * supplied and may optimize the batch size accordingly. A value of [Int.MAX_VALUE] means no limit.
     *
     * Unrestricted consumption of the returned [Flow] is a potentially performance intensive operation, it is thus recommended
     * to limit the amount of messages requested by using [Flow.take], [Flow.takeWhile] or other functions that limit the amount
     * of messages requested.
     *
     * ```kotlin
     *  channel.getMessagesBefore(newer.id).takeWhile { it.id > older.id }
     * ```
     *
     * The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     *
     * @throws IllegalArgumentException if a [limit] < 1 was supplied.
     */
    fun getMessagesBefore(messageId: Snowflake, limit: Int = Int.MAX_VALUE): Flow<Message> =
            strategy.supply(kord).getMessagesBefore(channelId = id, messageId = messageId, limit = limit)

    /**
     * Requests to get all messages in this channel that were created **after** [messageId] through the [strategy].
     *
     * Messages retrieved by this function will be emitted in chronological older (oldest -> newest).
     *
     * The flow may use paginated requests to supply messages, [limit] will limit the maximum number of messages
     * supplied and may optimize the batch size accordingly. A value of [Int.MAX_VALUE] means no limit.
     *
     * Unrestricted consumption of the returned [Flow] is a potentially performance intensive operation, it is thus recommended
     * to limit the amount of messages requested by using [Flow.take], [Flow.takeWhile] or other functions that limit the amount
     * of messages requested.
     *
     * ```kotlin
     *  channel.getMessagesAfter(older.id).takeWhile { it.id < newer.id }
     * ```
     * The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     *
     * @throws IllegalArgumentException if a [limit] < 1 was supplied.
     */
    fun getMessagesAfter(messageId: Snowflake, limit: Int = Int.MAX_VALUE): Flow<Message> =
            strategy.supply(kord).getMessagesAfter(channelId = id, messageId = messageId, limit = limit)

    /**
     * Requests to get messages around (both older and newer) the [messageId] through the [strategy].
     *
     * Messages retrieved by this function will be emitted in chronological older (oldest -> newest).
     *
     * Unlike [getMessagesAfter] and [getMessagesBefore], this flow can return **a maximum of 100 messages**.
     * As such, the accepted range of [limit] is reduced to 1..100.
     *
     * Supplied messages will be equally distributed  before and after the [messageId].
     * The remaining message for an odd [limit] is undefined and may appear on either side.
     *
     * The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     *
     * @throws IllegalArgumentException if the [limit] is outside the range of 1..100.
     */
    fun getMessagesAround(messageId: Snowflake, limit: Int = 100): Flow<Message> =
            strategy.supply(kord).getMessagesAround(channelId = id, messageId = messageId, limit = 100)

    /**
     * Requests to get a message with the given [messageId] through the [strategy].
     *
     * @throws RequestException if something went wrong during the request.
     * @throws EntityNotFoundException if the message is null.
     */
    suspend fun getMessage(messageId: Snowflake): Message = strategy.supply(kord).getMessage(id, messageId)

    /**
     * Requests to get a message with the given [messageId] through the [strategy],
     * returns null if the message isn't present or is not part of this channel.
     *
     * @throws RequestException if something went wrong during the request.
     */
    suspend fun getMessageOrNull(messageId: Snowflake): Message? = strategy.supply(kord).getMessageOrNull(id, messageId)

    /**
     * Requests to trigger the typing indicator for the bot in this channel.
     * The typing status will persist for 10 seconds or until the bot creates a message in the channel.
     *
     * @throws [RestRequestException] if something went wrong during the request.
     */
    suspend fun type() {
        kord.rest.channel.triggerTypingIndicator(id.value)
    }

    /**
     * Requests to trigger the typing indicator for the bot in this channel.
     * The typing status will persist until the [mark] is reached.
     *
     * @throws [RestRequestException] if something went wrong during the request.
     */
    suspend fun typeUntil(mark: TimeMark) {
        while (mark.hasNotPassedNow()) {
            type()
            delay(8.seconds.toLongMilliseconds()) //bracing ourselves for some network delays
        }
    }

    /**
     * Requests to trigger the typing indicator for the bot in this channel.
     * The typing status will persist until the [instant] is reached.
     *
     * @throws [RestRequestException] if something went wrong during the request.
     */
    suspend fun typeUntil(instant: Instant) {
        while (instant.isBefore(Instant.now())) {
            type()
            delay(8.seconds.toLongMilliseconds()) //bracing ourselves for some network delays
        }
    }

    /**
     * Returns a new [MessageChannelBehavior] with the given [strategy].
     */
    override fun withStrategy(strategy: EntitySupplyStrategy): MessageChannelBehavior = MessageChannelBehavior(id, kord, strategy)

    companion object {
        internal operator fun invoke(id: Snowflake, kord: Kord, strategy: EntitySupplyStrategy = kord.resources.defaultStrategy) = object : MessageChannelBehavior {
            override val id: Snowflake = id
            override val kord: Kord = kord
            override val strategy: EntitySupplyStrategy = strategy
        }
    }

}

/**
 * Requests to create a message configured by the [builder].
 *
 * @throws [RestRequestException] if something went wrong during the request.
 */
suspend inline fun MessageChannelBehavior.createMessage(builder: MessageCreateBuilder.() -> Unit): Message {
    val response = kord.rest.channel.createMessage(id.value, builder)
    val data = MessageData.from(response)

    return Message(data, kord)
}

/**
 * Requests to create a message with only an [embed][MessageCreateBuilder.embed].
 *
 * @throws [RestRequestException] if something went wrong during the request.
 */
suspend inline fun MessageChannelBehavior.createEmbed(block: EmbedBuilder.() -> Unit): Message = createMessage { embed(block) }

/**
 * Requests to trigger the typing indicator for the bot in this channel.
 * The typing status will be refreshed until the [block] has been completed.
 *
 * ```kotlin
 * channel.withTyping {
 *     delay(20.seconds.toLongMilliseconds()) //some very long task
 *     createMessage("done!")
 * }
 * ```
 *
 * @throws [RestRequestException] if something went wrong during the request.
 */
suspend inline fun <T : MessageChannelBehavior> T.withTyping(block: T.() -> Unit) {
    var typing = true

    kord.launch(context = coroutineContext) {
        while (typing) {
            type()
            delay(8.seconds.toLongMilliseconds())
        }
    }

    try {
        block()
    } finally {
        typing = false
    }
}

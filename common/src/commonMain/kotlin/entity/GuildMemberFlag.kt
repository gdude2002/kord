@file:Suppress(names = arrayOf("IncorrectFormatting", "ReplaceArrayOfWithLiteral",
                "SpellCheckingInspection", "GrazieInspection"))

package dev.kord.common.entity

import dev.kord.common.Class
import dev.kord.common.java
import kotlin.LazyThreadSafetyMode.PUBLICATION
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * See [GuildMemberFlag]s in the
 * [Discord Developer Documentation](https://discord.com/developers/docs/resources/guild#guild-member-object-guild-member-flags).
 */
public sealed class GuildMemberFlag(
    /**
     * The position of the bit that is set in this [GuildMemberFlag]. This is always in 0..30.
     */
    public val shift: Int,
) {
    init {
        require(shift in 0..30) { """shift has to be in 0..30 but was $shift""" }
    }

    /**
     * The raw code used by Discord.
     */
    public val code: Int
        get() = 1 shl shift

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` and
     * [flag].
     */
    public operator fun plus(flag: GuildMemberFlag): GuildMemberFlags =
            GuildMemberFlags(this.code or flag.code, null)

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` and
     * [flags].
     */
    public operator fun plus(flags: GuildMemberFlags): GuildMemberFlags =
            GuildMemberFlags(this.code or flags.code, null)

    final override fun equals(other: Any?): Boolean = this === other ||
            (other is GuildMemberFlag && this.shift == other.shift)

    final override fun hashCode(): Int = shift.hashCode()

    final override fun toString(): String = if (this is Unknown)
            "GuildMemberFlag.Unknown(shift=$shift)" else "GuildMemberFlag.${this::class.simpleName}"

    /**
     * @suppress
     */
    @Suppress(names = arrayOf("DeprecatedCallableAddReplaceWith"))
    @Deprecated(message =
            "GuildMemberFlag is no longer an enum class. Deprecated without a replacement.", level = DeprecationLevel.HIDDEN)
    public fun name(): String = name
    private val name get() = this::class.simpleName!!

    /**
     * @suppress
     */
    @Suppress(names = arrayOf("DeprecatedCallableAddReplaceWith"))
    @Deprecated(message =
            "GuildMemberFlag is no longer an enum class. Deprecated without a replacement.", level = DeprecationLevel.HIDDEN)
    public fun ordinal(): Int = when (this) {
        DidRejoin -> 0
        CompletedOnboarding -> 1
        BypassesVerification -> 2
        StartedOnboarding -> 3
        is Unknown -> Int.MAX_VALUE
    }

    /**
     * @suppress
     */
    @Deprecated(
        message = "GuildMemberFlag is no longer an enum class.",
        replaceWith = ReplaceWith(expression = "GuildMemberFlag::class.java", imports =
                    arrayOf("dev.kord.common.entity.GuildMemberFlag")),
        DeprecationLevel.HIDDEN,
    )
    public fun getDeclaringClass(): Class<GuildMemberFlag> = GuildMemberFlag::class.java

    /**
     * An unknown [GuildMemberFlag].
     *
     * This is used as a fallback for [GuildMemberFlag]s that haven't been added to Kord yet.
     */
    public class Unknown internal constructor(
        shift: Int,
    ) : GuildMemberFlag(shift)

    /**
     * Member has left and rejoined the guild.
     */
    public object DidRejoin : GuildMemberFlag(0)

    /**
     * Member has completed onboarding.
     */
    public object CompletedOnboarding : GuildMemberFlag(1)

    /**
     * Member is exempt from guild verification requirements.
     */
    public object BypassesVerification : GuildMemberFlag(2)

    /**
     * Member has started onboarding.
     */
    public object StartedOnboarding : GuildMemberFlag(3)

    public companion object {
        /**
         * A [List] of all known [GuildMemberFlag]s.
         */
        public val entries: List<GuildMemberFlag> by lazy(mode = PUBLICATION) {
            listOf(
                DidRejoin,
                CompletedOnboarding,
                BypassesVerification,
                StartedOnboarding,
            )
        }

        // TODO uncomment annotation in Member.kt and delete this file when this function is removed after deprecation
        //  cycle
        @Deprecated(
            "'GuildMemberFlag' is no longer serializable, serialize 'GuildMemberFlags' instead. Deprecated without a " +
                "replacement.",
            level = DeprecationLevel.HIDDEN,
        )
        public fun serializer(): KSerializer<GuildMemberFlag> = Serializer

        private object Serializer : KSerializer<GuildMemberFlag> {
            override val descriptor =
                PrimitiveSerialDescriptor("dev.kord.common.entity.GuildMemberFlag", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: GuildMemberFlag) = encoder.encodeString(value.name)
            override fun deserialize(decoder: Decoder) = valueOf0(decoder.decodeString())
        }

        @Deprecated(
            level = DeprecationLevel.HIDDEN,
            message = "Binary compatibility",
        )
        @JvmField
        public val DidRejoin: GuildMemberFlag = DidRejoin

        @Deprecated(
            level = DeprecationLevel.HIDDEN,
            message = "Binary compatibility",
        )
        @JvmField
        public val CompletedOnboarding: GuildMemberFlag = CompletedOnboarding

        @Deprecated(
            level = DeprecationLevel.HIDDEN,
            message = "Binary compatibility",
        )
        @JvmField
        public val BypassesVerification: GuildMemberFlag = BypassesVerification

        @Deprecated(
            level = DeprecationLevel.HIDDEN,
            message = "Binary compatibility",
        )
        @JvmField
        public val StartedOnboarding: GuildMemberFlag = StartedOnboarding

        /**
         * Returns an instance of [GuildMemberFlag] with [GuildMemberFlag.shift] equal to the
         * specified [shift].
         *
         * @throws IllegalArgumentException if [shift] is not in 0..30.
         */
        public fun fromShift(shift: Int): GuildMemberFlag = when (shift) {
            0 -> DidRejoin
            1 -> CompletedOnboarding
            2 -> BypassesVerification
            3 -> StartedOnboarding
            else -> Unknown(shift)
        }

        /**
         * @suppress
         */
        @Suppress(names = arrayOf("NON_FINAL_MEMBER_IN_OBJECT", "DeprecatedCallableAddReplaceWith"))
        @Deprecated(message =
                "GuildMemberFlag is no longer an enum class. Deprecated without a replacement.", level = DeprecationLevel.HIDDEN)
        @JvmStatic
        public open fun valueOf(name: String): GuildMemberFlag = valueOf0(name)
        private fun valueOf0(name: String) = when (name) {
            "DidRejoin" -> DidRejoin
            "CompletedOnboarding" -> CompletedOnboarding
            "BypassesVerification" -> BypassesVerification
            "StartedOnboarding" -> StartedOnboarding
            else -> throw IllegalArgumentException(name)
        }

        /**
         * @suppress
         */
        @Suppress(names = arrayOf("NON_FINAL_MEMBER_IN_OBJECT"))
        @Deprecated(
            message = "GuildMemberFlag is no longer an enum class.",
            replaceWith = ReplaceWith(expression = "GuildMemberFlag.entries.toTypedArray()", imports
                        = arrayOf("dev.kord.common.entity.GuildMemberFlag")),
            DeprecationLevel.HIDDEN,
        )
        @JvmStatic
        public open fun values(): Array<GuildMemberFlag> = entries.toTypedArray()
    }
}

/**
 * A collection of multiple [GuildMemberFlag]s.
 *
 * ## Creating an instance of [GuildMemberFlags]
 *
 * You can create an instance of [GuildMemberFlags] using the following methods:
 * ```kotlin
 * // from individual GuildMemberFlags
 * val guildMemberFlags1 = GuildMemberFlags(GuildMemberFlag.DidRejoin,
 * GuildMemberFlag.CompletedOnboarding)
 *
 * // from an Iterable
 * val iterable: Iterable<GuildMemberFlag> = TODO()
 * val guildMemberFlags2 = GuildMemberFlags(iterable)
 *
 * // using a builder
 * val guildMemberFlags3 = GuildMemberFlags {
 *     +guildMemberFlags2
 *     +GuildMemberFlag.DidRejoin
 *     -GuildMemberFlag.CompletedOnboarding
 * }
 * ```
 *
 * ## Modifying an existing instance of [GuildMemberFlags]
 *
 * You can create a modified copy of an existing instance of [GuildMemberFlags] using the [copy]
 * method:
 * ```kotlin
 * guildMemberFlags.copy {
 *     +GuildMemberFlag.DidRejoin
 * }
 * ```
 *
 * ## Mathematical operators
 *
 * All [GuildMemberFlags] objects can use `+`/`-` operators:
 * ```kotlin
 * val guildMemberFlags1 = guildMemberFlags + GuildMemberFlag.DidRejoin
 * val guildMemberFlags2 = guildMemberFlags - GuildMemberFlag.CompletedOnboarding
 * val guildMemberFlags3 = guildMemberFlags1 + guildMemberFlags2
 * ```
 *
 * ## Checking for [GuildMemberFlag]s
 *
 * You can use the [contains] operator to check whether an instance of [GuildMemberFlags] contains
 * specific [GuildMemberFlag]s:
 * ```kotlin
 * val hasGuildMemberFlag = GuildMemberFlag.DidRejoin in guildMemberFlags
 * val hasGuildMemberFlags = GuildMemberFlags(GuildMemberFlag.DidRejoin,
 * GuildMemberFlag.CompletedOnboarding) in guildMemberFlags
 * ```
 *
 * ## Unknown [GuildMemberFlag]s
 *
 * Whenever [GuildMemberFlag]s haven't been added to Kord yet, they will be deserialized as
 * instances of [GuildMemberFlag.Unknown].
 *
 * You can also use [GuildMemberFlag.fromShift] to check for [unknown][GuildMemberFlag.Unknown]
 * [GuildMemberFlag]s.
 * ```kotlin
 * val hasUnknownGuildMemberFlag = GuildMemberFlag.fromShift(23) in guildMemberFlags
 * ```
 *
 * @see GuildMemberFlag
 * @see GuildMemberFlags.Builder
 */
@Serializable(with = GuildMemberFlags.Serializer::class)
public class GuildMemberFlags internal constructor(
    /**
     * The raw code used by Discord.
     */
    public val code: Int,
    @Suppress("UNUSED_PARAMETER") unused: Nothing?,
) {
    // TODO uncomment annotation in Member.kt and delete this file when this constructor is removed after deprecation
    //  cycle
    @Deprecated(
        "Don't construct an instance of 'GuildMemberFlags' from a raw code. Use the factory functions described in " +
            "the documentation instead.",
        ReplaceWith("GuildMemberFlags.Builder(code).build()", "dev.kord.common.entity.GuildMemberFlags"),
        DeprecationLevel.HIDDEN,
    )
    public constructor(code: Int) : this(code, null)

    /**
     * A [Set] of all [GuildMemberFlag]s contained in this instance of [GuildMemberFlags].
     */
    public val values: Set<GuildMemberFlag>
        get() = buildSet {
            var remaining = code
            var shift = 0
            while (remaining != 0) {
                if ((remaining and 1) != 0) add(GuildMemberFlag.fromShift(shift))
                remaining = remaining ushr 1
                shift++
            }
        }

    /**
     * Checks if this instance of [GuildMemberFlags] has all bits set that are set in [flag].
     */
    public operator fun contains(flag: GuildMemberFlag): Boolean =
            this.code and flag.code == flag.code

    /**
     * Checks if this instance of [GuildMemberFlags] has all bits set that are set in [flags].
     */
    public operator fun contains(flags: GuildMemberFlags): Boolean =
            this.code and flags.code == flags.code

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` and
     * [flag].
     */
    public operator fun plus(flag: GuildMemberFlag): GuildMemberFlags =
            GuildMemberFlags(this.code or flag.code, null)

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` and
     * [flags].
     */
    public operator fun plus(flags: GuildMemberFlags): GuildMemberFlags =
            GuildMemberFlags(this.code or flags.code, null)

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` except
     * the bits that are set in [flag].
     */
    public operator fun minus(flag: GuildMemberFlag): GuildMemberFlags =
            GuildMemberFlags(this.code and flag.code.inv(), null)

    /**
     * Returns an instance of [GuildMemberFlags] that has all bits set that are set in `this` except
     * the bits that are set in [flags].
     */
    public operator fun minus(flags: GuildMemberFlags): GuildMemberFlags =
            GuildMemberFlags(this.code and flags.code.inv(), null)

    /**
     * Returns a copy of this instance of [GuildMemberFlags] modified with [builder].
     */
    public inline fun copy(builder: Builder.() -> Unit): GuildMemberFlags {
        contract { callsInPlace(builder, EXACTLY_ONCE) }
        return Builder(code).apply(builder).build()
    }

    override fun equals(other: Any?): Boolean = this === other ||
            (other is GuildMemberFlags && this.code == other.code)

    override fun hashCode(): Int = code.hashCode()

    override fun toString(): String = "GuildMemberFlags(values=$values)"

    /**
     * @suppress
     */
    @Deprecated(
        message = "GuildMemberFlags is no longer a data class.",
        replaceWith = ReplaceWith(expression = "this.code", imports = arrayOf()),
        DeprecationLevel.HIDDEN,
    )
    public operator fun component1(): Int = code

    /**
     * @suppress
     */
    @Suppress(names = arrayOf("DeprecatedCallableAddReplaceWith"))
    @Deprecated(message =
            "GuildMemberFlags is no longer a data class. Deprecated without a replacement.", level = DeprecationLevel.HIDDEN)
    public fun copy(code: Int = this.code): GuildMemberFlags = GuildMemberFlags(code, null)

    public class Builder(
        private var code: Int = 0,
    ) {
        /**
         * Sets all bits in the [Builder] that are set in this [GuildMemberFlag].
         */
        public operator fun GuildMemberFlag.unaryPlus() {
            this@Builder.code = this@Builder.code or this.code
        }

        /**
         * Sets all bits in the [Builder] that are set in this [GuildMemberFlags].
         */
        public operator fun GuildMemberFlags.unaryPlus() {
            this@Builder.code = this@Builder.code or this.code
        }

        /**
         * Unsets all bits in the [Builder] that are set in this [GuildMemberFlag].
         */
        public operator fun GuildMemberFlag.unaryMinus() {
            this@Builder.code = this@Builder.code and this.code.inv()
        }

        /**
         * Unsets all bits in the [Builder] that are set in this [GuildMemberFlags].
         */
        public operator fun GuildMemberFlags.unaryMinus() {
            this@Builder.code = this@Builder.code and this.code.inv()
        }

        /**
         * Returns an instance of [GuildMemberFlags] that has all bits set that are currently set in
         * this [Builder].
         */
        public fun build(): GuildMemberFlags = GuildMemberFlags(code, null)
    }

    internal object Serializer : KSerializer<GuildMemberFlags> {
        override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("dev.kord.common.entity.GuildMemberFlags",
                PrimitiveKind.INT)

        private val `delegate`: KSerializer<Int> = Int.serializer()

        override fun serialize(encoder: Encoder, `value`: GuildMemberFlags) {
            encoder.encodeSerializableValue(delegate, value.code)
        }

        override fun deserialize(decoder: Decoder): GuildMemberFlags =
                GuildMemberFlags(decoder.decodeSerializableValue(delegate), null)
    }
}

/**
 * Returns an instance of [GuildMemberFlags] built with [GuildMemberFlags.Builder].
 */
public inline fun GuildMemberFlags(builder: GuildMemberFlags.Builder.() -> Unit = {}):
        GuildMemberFlags {
    contract { callsInPlace(builder, EXACTLY_ONCE) }
    return GuildMemberFlags.Builder().apply(builder).build()
}

/**
 * Returns an instance of [GuildMemberFlags] that has all bits set that are set in any element of
 * [flags].
 */
public fun GuildMemberFlags(vararg flags: GuildMemberFlag): GuildMemberFlags = GuildMemberFlags {
    flags.forEach { +it }
}

/**
 * Returns an instance of [GuildMemberFlags] that has all bits set that are set in any element of
 * [flags].
 */
public fun GuildMemberFlags(vararg flags: GuildMemberFlags): GuildMemberFlags = GuildMemberFlags {
    flags.forEach { +it }
}

/**
 * Returns an instance of [GuildMemberFlags] that has all bits set that are set in any element of
 * [flags].
 */
public fun GuildMemberFlags(flags: Iterable<GuildMemberFlag>): GuildMemberFlags = GuildMemberFlags {
    flags.forEach { +it }
}

/**
 * Returns an instance of [GuildMemberFlags] that has all bits set that are set in any element of
 * [flags].
 */
@JvmName("GuildMemberFlags0")
public fun GuildMemberFlags(flags: Iterable<GuildMemberFlags>): GuildMemberFlags =
        GuildMemberFlags {
    flags.forEach { +it }
}

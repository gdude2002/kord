package dev.kord.gateway

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmField

@Serializable(with = OpCode.Serializer::class)
public enum class OpCode(public val code: Int) {
    /** The default code for unknown values. */
    Unknown(Int.MIN_VALUE),

    /**
     * An event was dispatched.
     */
    Dispatch(0),

    /**
     * Fired periodically by the client to keep the connection alive.
     */
    Heartbeat(1),

    /**
     * Starts a new session during the initial handshake.
     */
    Identify(2),

    /**
     * Update the client's presence.
     */
    StatusUpdate(3),

    /**
     * Used to join/leave or move between voice channels.
     */
    VoiceStateUpdate(4),

    /**
     * You should attempt to reconnect and resume immediately.
     */
    Resume(6),

    /**
     * You should attempt to reconnect and resume immediately.
     */
    Reconnect(7),

    /**
     * Request information about offline guild members in a large guild.
     */
    RequestGuildMembers(8),

    /**
     * The session has been invalidated. You should reconnect and identify/resume accordingly.
     */
    InvalidSession(9),

    /**
     * Sent immediately after connecting, contains the `heartbeat_interval` to use.
     */
    Hello(10),

    /**
     * Sent in response to receiving a heartbeat to acknowledge that it has been received.
     */
    HeartbeatACK(11);

    internal object Serializer : KSerializer<OpCode> {
        override val descriptor = PrimitiveSerialDescriptor("dev.kord.gateway.OpCode", PrimitiveKind.INT)
        override fun serialize(encoder: Encoder, value: OpCode) = encoder.encodeInt(value.code)
        private val entriesByCode = entries.associateBy { it.code }
        override fun deserialize(decoder: Decoder) = entriesByCode[decoder.decodeInt()] ?: Unknown
    }

    public companion object {
        @Suppress("DEPRECATION_ERROR")
        @Deprecated(
            "Renamed to 'Companion', which no longer implements 'KSerializer<OpCode>'.",
            ReplaceWith("OpCode.serializer()", imports = ["dev.kord.gateway.OpCode"]),
            DeprecationLevel.HIDDEN,
        )
        @JvmField
        public val OpCodeSerializer: OpCodeSerializer = OpCodeSerializer()
    }

    @Deprecated(
        "Renamed to 'Companion', which no longer implements 'KSerializer<OpCode>'.",
        ReplaceWith("OpCode.serializer()", imports = ["dev.kord.gateway.OpCode"]),
        DeprecationLevel.HIDDEN,
    )
    public class OpCodeSerializer internal constructor() : KSerializer<OpCode> by Serializer {
        public fun serializer(): KSerializer<OpCode> = this
    }
}

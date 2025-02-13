package dev.kord.rest.builder.message.create

import dev.kord.common.annotation.KordDsl
import dev.kord.common.entity.InteractionResponseType
import dev.kord.common.entity.optional.map
import dev.kord.common.entity.optional.mapList
import dev.kord.common.entity.optional.optional
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.builder.message.buildMessageFlags
import dev.kord.rest.json.request.InteractionApplicationCommandCallbackData
import dev.kord.rest.json.request.InteractionResponseCreateRequest
import dev.kord.rest.json.request.MultipartInteractionResponseCreateRequest

@KordDsl
public class UpdateMessageInteractionResponseCreateBuilder :
    AbstractMessageCreateBuilder(),
    RequestBuilder<MultipartInteractionResponseCreateRequest> {
    // see https://discord.com/developers/docs/interactions/receiving-and-responding#create-interaction-response

    // TODO make override final in AbstractMessageCreateBuilder when this is removed
    @set:Deprecated(
        "This setter will be removed in the future, replace with files.clear() followed by files.addAll(...).",
        ReplaceWith("this.files.clear()\nthis.files.addAll(value)"),
        DeprecationLevel.ERROR,
    )
    override var files: MutableList<NamedFile> = mutableListOf()

    override fun toRequest(): MultipartInteractionResponseCreateRequest = MultipartInteractionResponseCreateRequest(
        request = InteractionResponseCreateRequest(
            type = InteractionResponseType.UpdateMessage,
            data = InteractionApplicationCommandCallbackData(
                tts = _tts,
                content = _content,
                embeds = _embeds.mapList { it.toRequest() },
                allowedMentions = _allowedMentions.map { it.build() },
                flags = buildMessageFlags(flags, suppressEmbeds, suppressNotifications),
                components = _components.mapList { it.build() },
                attachments = _attachments.mapList { it.toRequest() },
            ).optional(),
        ),
        files = files.toList(),
    )
}

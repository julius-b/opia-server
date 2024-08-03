package app.opia.services.messaging

import app.opia.routes.Message
import app.opia.routes.MessagePacket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RTMessage {
    // chat message for one peer: Message + MessagePacket
    @Serializable
    @SerialName("chat")
    data class ChatMessage(val msg: Message, val packet: MessagePacket) : RTMessage()

    // TODO Receipt(rcpt: ...)
}

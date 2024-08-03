package app.opia.routes

import app.opia.services.installationsService
import app.opia.services.messagesService
import app.opia.services.messaging.RTMessage
import app.opia.services.messaging.messagingService
import app.opia.utils.ByteArraySerializer
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

// TODO UByteArray, b64 serializer
@Serializable
data class CreateMessage(
    @SerialName("id") @Serializable(UUIDSerializer::class) val id: UUID,
    @SerialName("rcpt_id") @Serializable(UUIDSerializer::class) val rcptId: UUID,
    val timestamp: Instant,
    val packets: List<MessagePacket>
)

@Serializable
data class Message(
    @Serializable(UUIDSerializer::class) val id: UUID,
    @SerialName("from_id") @Serializable(UUIDSerializer::class) val fromId: UUID,
    @SerialName("rcpt_id") @Serializable(UUIDSerializer::class) val rcptId: UUID,
    @SerialName("att_cnt") val attCnt: Int,
    val packets: List<MessagePacket>,
    val receipts: List<MessageReceipt>,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)

@Serializable
data class MessagePacket(
    @SerialName("rcpt_ioid") @Serializable(UUIDSerializer::class) val rcptIOID: UUID,
    val dup: Int,
    //@SerialName("hs_id") @Serializable(UUIDSerializer::class) val hsId: UUID? = null,
    @SerialName("seqno") val seqno: Int,
    @SerialName("payload_enc") @Serializable(ByteArraySerializer::class) val payloadEnc: ByteArray,
    @SerialName("msg") val msg: Message? = null
)

@Serializable
data class MessageReceipt(
    @SerialName("msg_id") @Serializable(UUIDSerializer::class) val msgId: UUID,
    @SerialName("rcpt_ioid") @Serializable(UUIDSerializer::class) val rcptIOID: UUID,
    val dup: Int,
    @SerialName("recv_at") val rectAt: Instant?,
    @SerialName("rjct_at") val rjctAt: Instant?,
    @SerialName("read_at") val readAt: Instant?
)

fun Route.messagesApi() {
    val log = KtorSimpleLogger("messages-api")

    route("messages") {
        // TODO verify rcptId > rcptIOID
        post {
            val principal = call.principal<JWTPrincipal>()!!
            val handle = principal.payload.getClaim("handle").asString()
            val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
            val selfIOID = UUID.fromString(principal.payload.getClaim("ioid").asString())
            log.info("post - handle: $handle")
            val req = call.receive<CreateMessage>()

            if (req.packets.isEmpty()) throw ValidationException(Code.Required, "packets")

            // illegal state or whatever
            val expectedTargets = installationsService.listLinks(req.rcptId).toMutableList()
            if (expectedTargets.isEmpty()) throw ValidationException(Code.Reference, "links")

            if (req.packets.size != expectedTargets.size) throw ValidationException2(
                Status(Code.Reference, error = "too many / few"), "packets"
            )

            for (packet in req.packets) {
                expectedTargets.removeIf { it.id == packet.rcptIOID }
            }
            if (expectedTargets.isNotEmpty()) {
                log.info("post - expected targets not empty: $expectedTargets")
                throw ValidationException2(Status(Code.Reference, error = "missing required targets"), "packets")
            }

            val msg = messagesService.add(selfId, selfIOID, req)

            val rawMsg = msg.copy(packets = emptyList())
            for (packet in msg.packets) {
                // TODO skip self
                log.debug("post - sending packet for: ${msg.rcptId}/${packet.rcptIOID}")
                messagingService.send(packet.rcptIOID, RTMessage.ChatMessage(rawMsg, packet))
            }

            // TODO must contain the rcpts
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = msg))
        }
    }
}

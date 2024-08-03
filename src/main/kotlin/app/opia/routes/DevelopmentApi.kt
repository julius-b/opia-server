package app.opia.routes

import app.opia.services.actorsService
import app.opia.services.installationsService
import app.opia.services.messagesService
import app.opia.services.messaging.RTMessage
import app.opia.services.messaging.messagingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import java.util.*

fun Route.devApi() {
    val log = KtorSimpleLogger("dev-api")

    route("send") {
        post("{from_handle}/{peer_handle}") {
            val fromHandle = call.parameters["from_handle"]!!
            val self = actorsService.getByHandle(fromHandle) ?: throw ValidationException(Code.Reference, "from_handle")
            val senderLinks = installationsService.listLinks(self.id)
            if (senderLinks.isEmpty()) throw ValidationException(Code.Reference, "sender_links")

            val peerHandle = call.parameters["peer_handle"]!!
            val peer = actorsService.getByHandle(peerHandle) ?: throw ValidationException(Code.Reference, "peer_handle")
            val links = installationsService.listLinks(peer.id)

            val packets = links.map { MessagePacket(it.id, 0, 0, "Hi".encodeBase64().encodeToByteArray()) }

            val createMsg = CreateMessage(UUID.randomUUID(), peer.id, Clock.System.now(), packets)

            // sync with MessagesApi
            val msg = messagesService.add(self.id, senderLinks[0].id, createMsg)
            val rawMsg = msg.copy(packets = emptyList())
            for (packet in msg.packets) {
                log.debug("send - sending packet for ${msg.rcptId}/${packet.rcptIOID}")
                messagingService.send(packet.rcptIOID, RTMessage.ChatMessage(rawMsg, packet))
            }

            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = msg))
        }
    }
    route("dev") {
        route("actor") {
            post("{handle}") {
                val handle = call.parameters["handle"]!!
                val actor = actorsService.create(handle, handle, "password")
                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = actor))
            }
        }
    }
}

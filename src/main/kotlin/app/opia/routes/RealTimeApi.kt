package app.opia.routes

import app.opia.services.messagesService
import app.opia.services.messaging.Connection
import app.opia.services.messaging.RTMessage
import app.opia.services.messaging.messagingService
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import java.util.*

fun Route.realtimeApi() {
    val log = KtorSimpleLogger("rt-api")

    authenticate("auth-jwt") {
        // socket is send-only, doesn't receive
        // http post is better defined
        // client-rx for perf
        webSocket("/rt") {
            val principal = call.principal<JWTPrincipal>()!!
            val handle = principal.payload.getClaim("handle").asString()
            val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
            val selfIOID = UUID.fromString(principal.payload.getClaim("ioid").asString())
            val conn = Connection(this, selfId, selfIOID, handle)
            try {
                log.info("conn opened: ${conn.name}")

                // register with MessagingRepo to receive live messages
                messagingService.registerClient(conn)

                // send unacknowledged messages
                // TODO paging
                val outstanding = messagesService.listUnacknowledged(selfId, selfIOID)
                for (packet in outstanding) {
                    sendSerialized(RTMessage.ChatMessage(packet.msg!!, packet.copy(msg = null)) as RTMessage)
                }

                while (isActive) {
                    val msg = receiveDeserialized<RTMessage>()
                    log.warn("unexpected recv [${conn.name}]: $msg")

                    // don't really care :)
                }
            } catch (e: Throwable) {
                if (e is ClosedReceiveChannelException) log.info("conn closed: ${conn.name}")
                else log.error("err <${conn.name}>:", e)
            } finally {
                messagingService.unregisterClient(conn)
            }
        }
    }
}

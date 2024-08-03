package app.opia.services.messaging

import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import io.ktor.websocket.*
import io.ktor.websocket.CloseReason.Codes.*
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// socket messages:
// - initial connect infos
// - messages...

data class Connection(val session: WebSocketServerSession, val aid: UUID, val ioid: UUID, val handle: String) {
    companion object {
        val lastId = AtomicInteger(0)
    }

    val name = "conn-${lastId.getAndIncrement()}/$aid:$ioid/$handle"

    // prevents being required to use `as Message` to prevent:
    // JsonDecodingException: Class discriminator was missing and no default serializers were registered in the polymorphic scope of 'Message'
    suspend fun send(msg: RTMessage) {
        session.sendSerialized<RTMessage>(msg)
    }
}

// NOTE: ensure only one instance is used across the server (DI)
class MessagingService {
    private val log = KtorSimpleLogger("messaging-svc")

    // <ioid, conn>
    private val clients = ConcurrentHashMap<UUID, Connection>()

    suspend fun registerClient(conn: Connection) {
        clients.compute(conn.ioid) { k, curr ->
            if (curr != null) {
                log.warn("register - disconnecting curr: ${curr.name}...")
                // TODO try?
                runBlocking {
                    curr.session.close(CloseReason(VIOLATED_POLICY, "duplicate"))
                }
            }
            conn
        }
        log.info("register - connected: ${conn.name}")
    }

    suspend fun unregisterClient(conn: Connection) {
        clients.remove(conn.ioid)
        log.debug("unregister - disconnected: ${conn.name}")
    }

    suspend fun send(rcpt: UUID, msg: RTMessage) {
        log.info("send - rcpt: $rcpt, msg: $msg")
        for ((_, client) in clients) {
            if (client.ioid == rcpt) {
                // TODO try, catch=remove from list
                log.info("send - client connected: $rcpt")
                client.send(msg)
            }
        }
    }
}

val messagingService = MessagingService()

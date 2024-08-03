package app.opia.routes

import app.opia.services.eventsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CreateEvent(val name: String, val desc: String)

fun Route.eventsApi() {
    val log = KtorSimpleLogger("events-api")

    route("events") {
        post {
            val principal = call.principal<JWTPrincipal>()!!
            val handle = principal.payload.getClaim("handle").asString()
            val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
            val createEvent = call.receive<CreateEvent>()

            log.info("post - <$handle> creating [${createEvent.name}]")
            val event = eventsService.add(createEvent.name, createEvent.desc, selfId)
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = event))
        }
        get {
            call.respond(ApiSuccessResponse(data = eventsService.all()))
        }
        // with posts
        get("extended") {

        }
    }
}

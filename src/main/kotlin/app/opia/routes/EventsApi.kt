package app.opia.routes

import app.opia.services.eventsService
import app.opia.utils.UUIDSerializer
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

enum class Tag {
    Music, Fsk18, NoDog, Dog, Alcohol, NoAlcohol
}

@Serializable
data class CreateEvent(val name: String, val desc: String)

@Serializable
data class Event(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val name: String,
    val desc: String,
    //val href: String,
    //val color: Int,
    //@SerialName("profile_id") @Serializable(UUIDSerializer::class) @EncodeDefault(EncodeDefault.Mode.ALWAYS) val profileId: UUID?,
    @SerialName("created_by") @Serializable(UUIDSerializer::class) val createdBy: UUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    val posts: List<Post>
)

@Serializable
data class EventMembership(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val event: Event,
    val account: Actor,
    @SerialName("created_at") val createdAt: Instant
)

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

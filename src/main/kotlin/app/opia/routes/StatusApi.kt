package app.opia.routes

import app.opia.services.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.serialization.Serializable

@Serializable
data class StatusResp(
    val actors: List<Actor>,
    val actorProperties: List<ActorProperty>,
    val installations: List<Installation>,
    val authSessions: List<AuthSession>,
    val installationLinks: List<InstallationLink>,
    val medias: List<Media>
)

// TODO rename to stats api?
fun Route.statusApi() {
    val log = KtorSimpleLogger("status-api")

    route("status") {
        get("list") {
            val actors = actorsService.all()
            val actorProperties = actorPropertiesService.all()
            val installations = installationsService.all()
            val authSessions = authSessionsService.all()
            val installationLinks = installationsService.allLinks()
            val medias = mediasService.all()
            call.respond(StatusResp(actors, actorProperties, installations, authSessions, installationLinks, medias))
        }
    }
}

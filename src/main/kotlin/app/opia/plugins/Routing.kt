package app.opia.plugins

import app.opia.routes.actorsApi
import app.opia.routes.authSessionsApi
import app.opia.routes.devApi
import app.opia.routes.eventsApi
import app.opia.routes.feedApi
import app.opia.routes.installationsApi
import app.opia.routes.mediasApi
import app.opia.routes.messagesApi
import app.opia.routes.postsApi
import app.opia.routes.realtimeApi
import app.opia.routes.statusApi
import app.opia.services.SecurityConfig
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun Application.configureRouting(securityCfg: SecurityConfig) {
    install(Resources)
    install(AutoHeadResponse)
    routing {
        get("/") {
            call.respondText("Opia Api")
        }
        // Static plugin. Try to access `/static/index.html`
        static("/static") {
            resources("static")
        }
        get<Articles> { article ->
            // Get all articles ...
            call.respond("List of articles sorted starting from ${article.sort}")
        }
        route("/api/v1") {
            statusApi()
            installationsApi()
            actorsApi()
            authSessionsApi(securityCfg)
            // TODO fully wrap in jwt-auth
            mediasApi()
            devApi()

            authenticate("auth-jwt") {
                messagesApi()
                eventsApi()
                postsApi()
                feedApi()
            }
        }
        authenticate("auth-jwt") {
            realtimeApi()
        }
    }
}

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")

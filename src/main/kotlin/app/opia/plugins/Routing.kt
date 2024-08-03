package app.opia.plugins

import app.opia.routes.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.configureRouting(tokenAudience: String, tokenIssuer: String, tokenSecret: String) {
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
            authSessionsApi(tokenAudience, tokenIssuer, tokenSecret)
            // TODO fully wrap in jwt-auth
            mediasApi()
            devApi()

            authenticate("auth-jwt") {
                messagesApi()
                eventsApi()
                postsApi()
            }
        }
        realtimeApi()
    }
}

@Serializable
@Resource("/articles")
class Articles(val sort: String? = "new")

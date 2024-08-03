package app.opia.plugins

import app.opia.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

fun Application.configureAdministration() {
    val log = KtorSimpleLogger("admin")

    install(ShutDownUrl.ApplicationCallPlugin) {
        shutDownUrl = "/admin/shutdown"
        exitCodeSupplier = { 0 }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                // TODO provide get without ?, will return default resp
                // TODO don't know field
                is EntityNotFoundException -> {
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiErrorResponse(Code.Reference))
                }

                is ValidationException -> {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity, ApiErrorResponse(
                            cause.code, cause.fields.associateBy({ it.first }, { arrayOf(Status(cause.code)) })
                        )
                        // TODO , raw = it.second.toString() - probably make all these values string...
                        // TODO all use of @Contextual seems to be very wrong, could just as well use @Serializer(AnySerializer::class) and do toString there
                    )
                }

                is ValidationException2 -> {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity, ApiErrorResponse(
                            cause.status.code, cause.fields.associateBy({ it.first }, { arrayOf(cause.status) })
                        )
                    )
                }

                else -> {
                    log.info("uncaught exception:", cause)

                    // TODO disable for prod :)
                    // eg:500: io.ktor.server.plugins.BadRequestException: Failed to convert request body to class app.opia.routes.CreateMessage
                    call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

package app.opia.plugins

import app.opia.routes.ApiError
import app.opia.routes.ApiErrorResponse
import app.opia.routes.ValidationException
import app.opia.routes.ValidationScope
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
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiErrorResponse(errors = mapOf("" to arrayOf(ApiError.Reference())))
                    )
                }

                is ValidationException -> {
                    val status = if (cause.errors.any { it is ApiError.Conflict }) HttpStatusCode.Conflict
                    else if (cause.errors.any { it is ApiError.Unauthenticated }) HttpStatusCode.Unauthorized
                    else if (cause.errors.any { it is ApiError.Forbidden }) HttpStatusCode.Forbidden
                    else HttpStatusCode.UnprocessableEntity
                    call.respond(
                        status, ApiErrorResponse(
                            errors = mapOf(cause.field to cause.errors),
                            scope = cause.scope ?: ValidationScope.Request
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

package app.opia.plugins

import app.opia.routes.ApiErrorResponse
import app.opia.routes.ApiError
import app.opia.services.SecurityConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*

fun Application.configureSecurity(securityCfg: SecurityConfig) {
    authentication {
        jwt("auth-jwt") {
            realm = securityCfg.realm
            verifier(
                JWT.require(Algorithm.HMAC256(securityCfg.secret)).withAudience(securityCfg.aud)
                    .withIssuer(securityCfg.iss).build()
            )
            validate { cred ->
                val aud = cred.payload.audience
                val handle = cred.payload.getClaim("handle").asString()
                UUID.fromString(cred.payload.getClaim("actor_id").asString())
                if (aud.contains(securityCfg.aud) && handle != "") {
                    JWTPrincipal(cred.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                // unauthorized (not authenticated / authentication failed)
                // ApiError only knows Forbidden
                call.respond(
                    HttpStatusCode.Unauthorized, ApiErrorResponse(
                        errors = mapOf(Authorization to arrayOf(ApiError.Forbidden(auth = realm)))
                    )
                )
            }
        }
    }
}

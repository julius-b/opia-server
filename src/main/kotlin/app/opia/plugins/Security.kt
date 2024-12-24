package app.opia.plugins

import app.opia.routes.ApiErrorResponse
import app.opia.routes.Code
import app.opia.routes.Status
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
                call.respond(
                    HttpStatusCode.Unauthorized, ApiErrorResponse(
                        Code.Unauthenticated, mapOf(
                            Authorization to arrayOf(Status(Code.Unauthenticated, error = "invalid or expired token"))
                        )
                    )
                )
            }
        }
    }
}

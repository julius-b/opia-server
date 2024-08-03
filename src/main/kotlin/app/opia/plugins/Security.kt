package app.opia.plugins

import app.opia.routes.ApiErrorResponse
import app.opia.routes.Code
import app.opia.routes.Status
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*

fun Application.configureSecurity(
    tokenRealm: String, tokenAudience: String, tokenIssuer: String, tokenSecret: String
) {
    // Please read the jwt property from the config file if you are using EngineMain
    val jwtAudience = "jwt-audience"
    val jwtDomain = "https://jwt-provider-domain/"
    val jwtRealm = "ktor sample app"
    val jwtSecret = "secret"
    authentication {
        jwt("auth-jwt") {
            realm = tokenRealm
            verifier(
                JWT.require(Algorithm.HMAC256(tokenSecret)).withAudience(tokenAudience).withIssuer(tokenIssuer).build()
            )
            validate { cred ->
                val aud = cred.payload.audience
                val handle = cred.payload.getClaim("handle").asString()
                UUID.fromString(cred.payload.getClaim("actor_id").asString())
                if (aud.contains(tokenAudience) && handle != "") {
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
        jwt {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret)).withAudience(jwtAudience).withIssuer(jwtDomain).build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

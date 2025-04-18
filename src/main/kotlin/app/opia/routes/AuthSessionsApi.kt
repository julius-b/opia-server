package app.opia.routes

import app.opia.services.*
import app.opia.utils.UUIDSerializer
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.random.Random

@Serializable
data class CreateAuthSession(
    val unique: String,
    val secret: String,
    @SerialName("cap_chat") val capChat: Boolean,
    @Serializable(UUIDSerializer::class) val ioid: UUID? = null
)

@Serializable
data class AuthSession(
    @Serializable(UUIDSerializer::class) val id: UUID,
    @SerialName("actor_id") @Serializable(UUIDSerializer::class) val actorId: UUID,
    @SerialName("installation_id") @Serializable(UUIDSerializer::class) val installationId: UUID,
    @Serializable(UUIDSerializer::class) val ioid: UUID,
    @SerialName("secret_update_id") @Serializable(UUIDSerializer::class) val secretUpdateId: UUID,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("access_token") var accessToken: String?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)

@Serializable
data class AuthHints(
    val properties: List<ActorProperty>, val actor: Actor
)

fun Route.authSessionsApi(securityCfg: SecurityConfig) {
    val log = KtorSimpleLogger("auth-sessions-api")

    fun genAccessToken(actorId: UUID, ioid: UUID, handle: String): String {
        return JWT.create().withAudience(securityCfg.aud)
            .withIssuer(securityCfg.iss)
            .withClaim("handle", handle)
            .withClaim("actor_id", actorId.toString())
            .withClaim("ioid", ioid.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(securityCfg.secret))
    }

    route("auth_sessions") {
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<CreateAuthSession>()

            val unique = req.unique.sanitizeUnique()
            val secret = req.secret.sanitizeSecret()
            var ioid = req.ioid

            var actor = actorsService.getByHandle(unique)
            if (actor == null) {
                val property = actorPropertiesService.getPrimaryByContent(unique) ?: throw ValidationException(
                    "unique", ApiError.Reference(unique)
                )
                actor = actorsService.get(property.actorId!!)!!
            }

            if (actor.secret != secret) throw ValidationException("secret", ApiError.Forbidden())

            val secretUpdate = actorsService.getLatestSecretUpdate(actor.id, actor.secret)
            if (secretUpdate == null) {
                log.error("post - failed to find SecretUpdate, actor=${actor.id}")
                throw ValidationException("secret_update", ApiError.Internal())
            }

            if (ioid != null) {
                val link = installationsService.getLink(ioid)
                    ?: throw ValidationException("ioid", ApiError.Reference(ioid.toString()))
                if (link.actorId != actor.id)
                    throw ValidationException("ioid", ApiError.Forbidden(ioid.toString(), "actor_id"))
                // TODO delete keys (?)
            } else {
                log.info("post - linking actor ${actor.id} with installation $installationId")
                installationsService.deleteLinks(actor.id, installationId)
                val link = installationsService.linkInstallation(actor.id, installationId)
                ioid = link.id
            }

            val accessToken = genAccessToken(actor.id, ioid, actor.handle)
            val refreshToken = Random.nextBytes(64).encodeBase64()
            //val refreshToken = getRandomString(64, AlphaNumCharset)

            val authSession = authSessionsService.create(
                actor.id, installationId, ioid, secretUpdate.id.value, refreshToken
            )
            authSession.accessToken = accessToken

            val properties = actorPropertiesService.list(actor.id)

            call.respond(
                HttpStatusCode.Created,
                HintedApiSuccessResponse(data = authSession, hints = AuthHints(properties, actor))
            )
        }
        post("refresh") {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            //val auth = call.request.headerOrFail(HttpHeaders.Authorization)
            val auth = call.request.headerOrFail(KeyRefreshToken)
            val tokens = auth.split(' ')
            if (tokens.size != 2 || tokens[0] != "Bearer") {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val currRefreshToken = tokens[1]
            val curr = authSessionsService.getByRefreshToken(currRefreshToken, installationId)
            if (curr == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            if (curr.installationId != installationId) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val actor = actorsService.get(curr.actorId)
            if (actor == null) {
                call.respond(HttpStatusCode.UnprocessableEntity)
                return@post
            }

            val accessToken = genAccessToken(actor.id, curr.ioid, actor.handle)
            val refreshToken = Random.nextBytes(64).encodeBase64()
            val authSession = authSessionsService.create(
                curr.actorId, curr.installationId, curr.ioid, curr.secretUpdateId, refreshToken
            )
            authSession.accessToken = accessToken

            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = authSession))
        }
        authenticate("auth-jwt") {
            get {
                val authSessions = authSessionsService.all()
                call.respond(ApiSuccessResponse(count = authSessions.size, data = authSessions))
            }
        }
    }
}

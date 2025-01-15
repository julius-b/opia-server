package app.opia.routes

import app.opia.services.*
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

@Serializable
data class CreateUserAccount(
    val handle: String, val name: String, val secret: String
)

@Serializable
data class Actor(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val type: Type,
    val auth: Auth,
    val handle: String,
    val name: String,
    val desc: String?,
    @Transient val secret: String = "",
    @SerialName("profile_id") @Serializable(UUIDSerializer::class) @EncodeDefault(EncodeDefault.Mode.ALWAYS) val profileId: UUID?,
    @SerialName("banner_id") @Serializable(UUIDSerializer::class) @EncodeDefault(EncodeDefault.Mode.ALWAYS) val bannerId: UUID?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    enum class Type {
        @SerialName("account")
        Account, Group, Channel, Automated
    }

    enum class Auth {
        @SerialName("default")
        Default, Privileged, Root
    }
}

@Serializable
data class CreateActorProperty(
    val content: String, val type: ActorProperty.Type? = null, val scope: Scope = Scope.Signup
) {
    enum class Scope {
        @SerialName("signup")
        Signup,

        @SerialName("2fa")
        TwoFactor
    }
}

@Serializable
data class CreateActorLink(
    @SerialName("actor_id") @Serializable(UUIDSerializer::class) val actorId: UUID?,
    @SerialName("peer_id") @Serializable(UUIDSerializer::class) val peerId: UUID,
    @SerialName("perm") val perm: ActorLink.Perm
)

@Serializable
data class ActorProperty(
    @Serializable(UUIDSerializer::class) val id: UUID,
    @SerialName("actor_id") @Serializable(UUIDSerializer::class) val actorId: UUID?,
    @SerialName("installation_id") @Serializable(UUIDSerializer::class) val installationId: UUID,
    val type: Type,
    val content: String,
    @SerialName("verification_code") val verificationCode: String,
    val valid: Boolean,
    val primary: Boolean?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    enum class Type {
        @SerialName("phone_no")
        PhoneNo,

        @SerialName("email")
        Email
    }
}

@Serializable
data class ActorLink(
    @SerialName("actor_id") @Serializable(UUIDSerializer::class) val actorId: UUID,
    @SerialName("peer_id") @Serializable(UUIDSerializer::class) val peerId: UUID,
    val perm: Perm,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("created_by") @Serializable(UUIDSerializer::class) val createdBy: UUID,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    // NOTE: add ordinal to compare authorization
    enum class Perm {
        @SerialName("invited")
        Invited,

        @SerialName("read")
        Read,

        @SerialName("write")
        Write,

        @SerialName("read_write")
        ReadWrite,

        @SerialName("admin")
        Admin
    }
}

@Serializable
data class ActorHints(
    val properties: List<ActorProperty>
)

fun Route.actorsApi() {
    val log = KtorSimpleLogger("actors-api")

    route("actors") {
        // TODO validate & create actor in one transaction
        // tx not necessary, updating Valid is fine to do multiple times
        post {
            val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
            val req = call.receive<CreateUserAccount>()

            val handle = req.handle.trim()
            val name = req.name.trim()
            val secret = req.secret.sanitizeSecret()
            if (handle.length < 3 || handle.length > 20) throw ValidationException(
                "handle", ApiError.Size(handle, min = 3, max = 20)
            )
            if (name.length < 3 || name.length > 50) throw ValidationException(
                "name", ApiError.Size(name, min = 3, max = 50)
            )
            if (secret.length < 8) throw ValidationException("secret", ApiError.Size(min = 8))

            val responses = call.request.headers.getAll(KeyChallengeResponse) ?: throw ValidationException(
                KeyChallengeResponse, ApiError.Required()
            )
            val properties = mutableListOf<ActorProperty>()
            // NOTE: multiple values for one key might be joined by commas or semicolons
            responses.map { it.split(';', ',') }.flatten().forEach { resp ->
                val split = resp.split("=")
                if (split.size != 2) {
                    log.warn("actors/post - invalid challenge-response: $resp ($split)")
                    throw ValidationException(KeyChallengeResponse, ApiError.Schema(resp, "<uuid>=<code>"))
                }
                val id = UUID.fromString(split[0])
                var property = actorPropertiesService.get(id) ?: throw ValidationException(
                    KeyChallengeResponse, ApiError.Reference(id.toString())
                )
                // client needs to know which submitted property id is incorrect
                // access is forbidden to resource actor_property[$id]
                if (property.installationId != installationId) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(id.toString(), "installation_id")
                )
                // already assigned to another account
                if (property.actorId != null) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(id.toString(), "actor_id")
                )
                if (property.valid) {
                    properties.add(property)
                    return@forEach
                }
                val code = split[1]
                // TODO return property id as well
                if (property.verificationCode != code) throw ValidationException(
                    KeyChallengeResponse, ApiError.Forbidden(code, "code")
                )
                // save validated property
                property = actorPropertiesService.validateProperty(property.id) ?: throw ValidationException(
                    KeyChallengeResponse, ApiError.Reference(id.toString())
                )
                properties.add(property)
            }
            if (properties.none { it.type == ActorProperty.Type.PhoneNo }) {
                throw ValidationException(
                    KeyChallengeResponse, ApiError.Required(category = ActorProperty.Type.PhoneNo.name)
                )
            }

            // TODO same tx as actor.create!! otherwise the failure would never be corrected
            val actor = actorsService.create(handle, name, secret)
            for (i in 0 until properties.size) {
                // TODO possibly multiple of the same type, only primarize one per type
                // TODO race: first check is necessary before updating valid, but this should also ensure actorId!=null
                val owned = actorPropertiesService.ownAndPrimarizeProperty(properties[i].id, actor.id)
                    ?: throw ValidationException(
                        KeyChallengeResponse, ApiError.Reference(properties[i].id.toString())
                    )
                properties[i] = owned
            }

            actorsService.createSecretUpdate(actor.id, actor.secret)
            call.respond(HttpStatusCode.Created, HintedApiSuccessResponse(data = actor, hints = ActorHints(properties)))
        }
        authenticate("auth-jwt") {
            get {
                val actors = actorsService.all()
                call.respond(ApiSuccessResponse(count = actors.size, data = actors))
            }
            get("{id}") {
                val id = UUID.fromString(call.parameters["id"])
                val actor = actorsService.get(id) ?: throw ValidationException("id", ApiError.Reference(id.toString()))
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = actor))
            }
            // TODO protect, only self or admin...
            // delete is status-only
            delete("{id}") {
                val id = UUID.fromString(call.parameters["id"])
                if (actorsService.delete(id)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.UnprocessableEntity)
                }
            }

            get("by-handle/{handle}") {
                val handle = call.parameters["handle"] ?: throw ValidationException(
                    "handle", ApiError.Required()
                )
                val actor = actorsService.getByHandle(handle) ?: throw ValidationException(
                    "handle", ApiError.Reference(handle)
                )
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = actor))
            }

            route("links") {
                post {
                    val principal = call.principal<JWTPrincipal>()!!
                    val handle = principal.payload.getClaim("handle").asString()
                    val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
                    log.info("post - handle: $handle, self-id: $selfId")

                    val createActorLink = call.receive<CreateActorLink>()

                    val linkActorId = createActorLink.actorId ?: selfId
                    val link = actorLinksService.create(linkActorId, createActorLink.peerId)
                    call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = link))
                }
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val handle = principal.payload.getClaim("handle").asString()
                    val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
                    log.info("get - handle: $handle, self-id: $selfId")

                    val links = actorLinksService.listByActor(selfId)
                    call.respond(HttpStatusCode.OK, ApiSuccessResponse(count = links.size, data = links))
                }
            }

            get("{id}/installations") {
                val principal = call.principal<JWTPrincipal>()!!
                val handle = principal.payload.getClaim("handle").asString()
                val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())
                val peerId = UUID.fromString(call.parameters["id"])

                val peerInstallations = installationsService.listLinks(peerId)
                call.respond(
                    HttpStatusCode.OK, ApiSuccessResponse(count = peerInstallations.size, data = peerInstallations)
                )
            }
        }
        route("properties") {
            post {
                val installationId = UUID.fromString(call.request.headerOrFail(KeyInstallationID))
                val createActorProperty = call.receive<CreateActorProperty>()
                val content = createActorProperty.content.trim()
                val type = createActorProperty.type
                    ?: if (content.contains('@')) ActorProperty.Type.Email else ActorProperty.Type.PhoneNo

                // TODO validate phone no
                // NOTE: content is only unique among primary properties
                // since it's not primary during creating, verify for conflict manually
                if (createActorProperty.scope == CreateActorProperty.Scope.Signup) {
                    if (actorPropertiesService.getPrimaryByContent(content) != null) {
                        log.warn("properties/post - value already exists as primary: $content")
                        throw ValidationException("content", ApiError.Conflict(content))
                    }
                }

                val actorProperty = actorPropertiesService.create(installationId, type, content)
                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = actorProperty))
            }
        }
    }
}

package app.opia.routes

import app.opia.services.actorLinksService
import app.opia.services.actorPropertiesService
import app.opia.services.actorsService
import app.opia.services.installationsService
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.datetime.Instant
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
    @SerialName("profile_id") @Serializable(UUIDSerializer::class) val profileId: UUID?,
    @SerialName("banner_id") @Serializable(UUIDSerializer::class) val bannerId: UUID?,
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
            val secret = req.secret.trim()
            if (handle.length < 3 || handle.length > 20) throw ValidationException(Code.Constraint, "handle")
            if (name.length < 3 || name.length > 50) throw ValidationException(Code.Constraint, "name")
            if (secret.length < 8) throw ValidationException(Code.Constraint, "secret")

            val responses = call.request.headers.getAll(KeyChallengeResponse) ?: throw ValidationException(
                Code.Required, KeyChallengeResponse
            )
            val properties = mutableListOf<ActorProperty>()
            responses.forEach { resp ->
                val split = resp.split(",")
                if (split.size > 2) throw ValidationException(Code.Schema, KeyChallengeResponse)
                val id = UUID.fromString(split[0])
                var property = actorPropertiesService.get(id) ?: throw ValidationException(
                    Code.Reference, KeyChallengeResponse to id
                )
                if (property.installationId != installationId) throw ValidationException(
                    Code.Forbidden, KeyInstallationID to installationId
                )
                if (property.valid) {
                    properties.add(property)
                    return@forEach
                }
                if (split.size != 2) throw ValidationException(Code.Schema, KeyChallengeResponse)
                val code = split[1]
                if (property.verificationCode != code) throw ValidationException(
                    Code.Forbidden, KeyChallengeResponse to id
                )
                // save validated property
                property = actorPropertiesService.validateProperty(property.id) ?: throw ValidationException(
                    Code.Reference, KeyChallengeResponse to id
                )
                properties.add(property)
            }
            if (properties.none { it.type == ActorProperty.Type.PhoneNo }) {
                throw ValidationException(Code.Required, KeyChallengeResponse to ActorProperty.Type.PhoneNo)
            }

            val actor = actorsService.create(handle, name, secret)
            for (i in 0 until properties.size) {
                // TODO possibly multiple of the same type, only primarize one
                val owned = actorPropertiesService.ownAndPrimarizeProperty(properties[i].id, actor.id)
                    ?: throw ValidationException(Code.Reference, KeyChallengeResponse to properties[i].id)
                properties[i] = owned
            }

            actorsService.createSecretUpdate(actor.id, actor.secret)
            call.respond(HttpStatusCode.Created, HintedApiSuccessResponse(Code.OK, data = actor, hints = properties))
        }
        authenticate("auth-jwt") {
            get {
                call.respond(actorsService.all())
            }
            get("{id}") {
                val id = UUID.fromString(call.parameters["id"])
                val actor = actorsService.get(id) ?: throw ValidationException(Code.Reference, "id")
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
                val handle = call.parameters["handle"] ?: throw ValidationException(Code.Required, "id")
                val actor = actorsService.getByHandle(handle) ?: throw ValidationException(Code.Reference, "handle")
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
                        throw ValidationException(Code.Conflict, "content" to content)
                    }
                }

                val actorProperty = actorPropertiesService.create(installationId, type, content)
                call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = actorProperty))
            }
        }
    }
}

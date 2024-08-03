package app.opia.routes

import app.opia.routes.Installation.Os
import app.opia.services.installationsService
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.server.application.*
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
data class Installation(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val name: String,
    val desc: String,
    val os: Os,
    @SerialName("client_vname") val clientVersionName: String,
    @SerialName("created_at") val createdAt: Instant,
    // don't share with client as they don't care / understand
    @Transient @SerialName("deleted_at") val deletedAt: Instant? = null
) {
    enum class Os {
        @SerialName("desktop")
        Desktop,

        @SerialName("windows")
        Windows,

        @SerialName("mac_os")
        MacOS,

        @SerialName("linux")
        Linux,

        @SerialName("mobile")
        Mobile,

        @SerialName("android")
        Android,

        @SerialName("ios")
        iOS,

        @SerialName("web")
        Web
    }
}

@Serializable
data class CreateInstallation(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val name: String,
    val desc: String,
    val os: Os,
    @SerialName("client_vname") val clientVersionName: String
)

@Serializable
data class InstallationLink(
    @Serializable(UUIDSerializer::class) val id: UUID,
    @SerialName("installation_id") @Serializable(UUIDSerializer::class) val installationId: UUID,
    @SerialName("actor_id") @Serializable(UUIDSerializer::class) val actorId: UUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)

fun Route.installationsApi() {
    val log = KtorSimpleLogger("installations-api")

    route("installations") {
        put {
            val req = call.receive<CreateInstallation>()
            val installation = installationsService.create(req.id, req.name, req.desc, req.os, req.clientVersionName)
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = installation))
        }
        get {
            val installations = installationsService.all()
            call.respond(ApiSuccessResponse(count = installations.size, data = installations))
        }
        get("{id}") {
            val id = UUID.fromString(call.parameters["id"])
            val installation = installationsService.get(id)
            if (installation != null) {
                call.respond(HttpStatusCode.OK, ApiSuccessResponse(data = installation))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        get("by-actor/{actorId}") {
            val id = UUID.fromString(call.parameters["actorId"])
        }
        delete("{id}") {
            val id = UUID.fromString(call.parameters["id"])
            if (installationsService.delete(id)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity)
            }
        }
    }
}

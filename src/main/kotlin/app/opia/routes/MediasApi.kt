package app.opia.routes

import app.opia.services.mediasService
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

@Serializable
data class Media(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val size: Int,
    val name: String,
    val contentDesc: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)

@Serializable
sealed class MediaRef {
    abstract val id: @Serializable(UUIDSerializer::class) UUID

    @Serializable
    @SerialName("actor")
    data class Actor(
        override val id: @Serializable(UUIDSerializer::class) UUID,
        val property: ActorMediaProperty,
        val actor: app.opia.routes.Actor? = null
    ) : MediaRef()

    @Serializable
    @SerialName("event")
    data class Event(
        override val id: @Serializable(UUIDSerializer::class) UUID,
        val event: app.opia.routes.Event? = null
    ) : MediaRef()

    @Serializable
    @SerialName("post")
    data class Post(
        override val id: @Serializable(UUIDSerializer::class) UUID,
        val post: app.opia.routes.Post? = null,
        val attachment: PostAttachment? = null
    ) : MediaRef()

    @Serializable
    @SerialName("message")
    data class Message(
        override val id: @Serializable(UUIDSerializer::class) UUID,
        val message: app.opia.routes.Message? = null
    ) : MediaRef()
}

enum class ActorMediaProperty {
    @SerialName("profile")
    Profile,

    @SerialName("banner")
    Banner
}

@Serializable
data class MediaHints<T : @Serializable Any>(
    val ref: T
)

fun Route.mediasApi() {
    val log = KtorSimpleLogger("medias-api")

    route("medias") {
        authenticate("auth-jwt") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val handle = principal.payload.getClaim("handle").asString()
                log.info("post - handle: $handle")

                val contentLength = call.request.headerOrFail(HttpHeaders.ContentLength)
                val multipartData = call.receiveMultipart()

                var name = ""
                var contentDesc = ""
                var size: Int? = null
                var ref: MediaRef? = null
                val mediaId = UUID.randomUUID()
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "content-desc") contentDesc = part.value
                            if (part.name == "ref") ref = Json.decodeFromString<MediaRef>(part.value)
                        }

                        is PartData.FileItem -> {
                            // TODO stream
                            name = part.originalFileName as String
                            val fileBytes = part.streamProvider().readBytes()
                            size = fileBytes.size
                            File("uploads/media").mkdirs()
                            File("uploads/media/$mediaId").writeBytes(fileBytes)
                        }

                        else -> {}
                    }
                    part.dispose()
                }

                if (size == null) {
                    log.warn("no file submitted, rejecting,,,")
                    throw ValidationException("file", ApiError.Required())
                }
                if (ref == null) {
                    log.warn("no ref info submitted, rejecting...")
                    throw ValidationException("ref", ApiError.Required())
                }

                log.info("post - id: $mediaId, content-length: $contentLength, size: $size, name: $name, ref: $ref")
                val media = mediasService.add(size!!, name, contentDesc, ref!!, mediaId)
                    ?: throw ValidationException("ref", ApiError.Reference())
                call.respond(
                    HttpStatusCode.Created,
                    HintedApiSuccessResponse(data = media.first, hints = MediaHints(media.second))
                )
            }
        }
        get {
            val medias = mediasService.all()
            call.respond(ApiSuccessResponse(count = medias.size, data = medias))
        }
        get("{id}/media") {
            val id = UUID.fromString(call.parameters["id"])
            val media = mediasService.get(id) ?: throw ValidationException("id", ApiError.Reference(id.toString()))
            val file = File("uploads/media/${media.id}")
            if (file.exists()) {
                call.response.header(
                    HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, media.name
                    ).toString()
                )
                call.respondFile(file)
                return@get
            }
            log.error("get media - missing: ${media.id}")
            call.respond(HttpStatusCode.NotFound)
        }
        get("{id}/raw") {
            val id = UUID.fromString(call.parameters["id"])
            val media = mediasService.get(id) ?: throw ValidationException("id", ApiError.Reference(id.toString()))
            val file = File("uploads/media/${media.id}")
            if (file.exists()) {
                // TODO add Content-Type header
                call.respondBytes(file.readBytes())
                return@get
            }
            log.error("get raw - missing: ${media.id}")
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

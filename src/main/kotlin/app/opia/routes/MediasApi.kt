package app.opia.routes

import app.opia.services.mediasService
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.http.content.*
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

fun Route.mediasApi() {
    val log = KtorSimpleLogger("medias-api")

    route("medias") {
        // TODO add actor_id to media :)
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
                val mediaId = UUID.randomUUID()
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "content-desc") contentDesc = part.value
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
                    log.warn("no file submitted, rejecting")
                    throw ValidationException(Code.Required, "file")
                }

                log.info("content-length: $contentLength, size: $size, name: $name")
                val media = mediasService.add(size!!, name, contentDesc, mediaId)
                call.respond(HttpStatusCode.Created, media)
            }
        }
        get {
            call.respond(mediasService.all())
        }
        get("{id}/media") {
            val id = UUID.fromString(call.parameters["id"])
            val media = mediasService.get(id) ?: throw ValidationException(Code.Reference, "id")
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
            val media = mediasService.get(id) ?: throw ValidationException(Code.Reference, "id")
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

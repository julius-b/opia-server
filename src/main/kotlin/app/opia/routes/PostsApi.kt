package app.opia.routes

import app.opia.services.postsService
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
import java.util.*

@Serializable
data class CreatePost(
    val title: String,
    val text: String,
    @SerialName("event_id") @Serializable(UUIDSerializer::class) val eventId: UUID,
    // TODO jwt
    @SerialName("created_by") @Serializable(UUIDSerializer::class) val createdBy: UUID,
    @SerialName("media_ids") val mediaIds: List<@Serializable(UUIDSerializer::class) UUID> = listOf(),
    @SerialName("created_as") @Serializable(UUIDSerializer::class) val createdAs: UUID? = null
)

@Serializable
data class Post(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val title: String,
    val text: String,
    @SerialName("event_id") @Serializable(UUIDSerializer::class) val eventId: UUID,
    @SerialName("created_by") @Serializable(UUIDSerializer::class) val createdBy: UUID,
    @SerialName("created_as") @Serializable(UUIDSerializer::class) val createdAs: UUID?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    val medias: List<Media>
)

fun Route.postsApi() {
    val log = KtorSimpleLogger("posts-api")

    route("posts") {
        post {
            val createPost = call.receive<CreatePost>()
            val post = postsService.add(
                createPost.title,
                createPost.text,
                createPost.eventId,
                createPost.createdBy,
                createPost.mediaIds,
                createPost.createdAs
            )
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = post))
        }
        get {
            call.respond(postsService.all())
        }
    }
}

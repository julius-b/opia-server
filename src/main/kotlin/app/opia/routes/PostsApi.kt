package app.opia.routes

import app.opia.services.postsService
import app.opia.utils.UUIDSerializer
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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

@Serializable
data class PostAttachment(
    @Serializable(UUIDSerializer::class) val postId: UUID,
    @Serializable(UUIDSerializer::class) val mediaId: UUID
)

fun Route.postsApi() {
    val log = KtorSimpleLogger("posts-api")

    route("posts") {
        post {
            val principal = call.principal<JWTPrincipal>()!!
            val selfId = UUID.fromString(principal.payload.getClaim("actor_id").asString())

            val createPost = call.receive<CreatePost>()
            val post = postsService.add(
                createPost.title,
                createPost.text,
                createPost.eventId,
                selfId,
                createPost.mediaIds,
                createPost.createdAs
            )
            call.respond(HttpStatusCode.Created, ApiSuccessResponse(data = post))
        }
        get {
            val posts = postsService.all()
            call.respond(ApiSuccessResponse(count = posts.size, data = posts))
        }
    }
}

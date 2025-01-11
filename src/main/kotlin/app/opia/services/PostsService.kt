package app.opia.services

import app.opia.routes.Post
import app.opia.routes.PostAttachment
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Posts : UUIDTable() {
    val title = varchar("title", 50)
    val text = varchar("text", 1000)
    val eventId = reference("event_id", Events)
    val createdBy = reference("created_by", Actors)
    val createdAs = reference("created_as", Events).nullable()
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class PostEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostEntity>(Posts)

    var title by Posts.title
    var text by Posts.text
    var eventId by Posts.eventId
    var createdBy by Posts.createdBy
    var createdAs by Posts.createdAs
    val createdAt by Posts.createdAt
    val deletedAt by Posts.deletedAt

    var medias by MediaEntity via PostAttachments
}

fun PostEntity.toDTO() = Post(
    id.value,
    title,
    text,
    eventId.value,
    createdBy.value,
    createdAs?.value,
    createdAt,
    deletedAt,
    medias.map(MediaEntity::toDTO)
)

object PostAttachments : CompositeIdTable() {
    val postId = reference("post_id", Posts)
    val mediaId = reference("media_id", Medias)

    init {
        addIdColumn(postId)
        addIdColumn(mediaId)
    }

    override val primaryKey = PrimaryKey(postId, mediaId)
}

class PostAttachmentEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<PostAttachmentEntity>(PostAttachments)

    var postId by PostAttachments.postId
    val mediaId by PostAttachments.mediaId
}

fun PostAttachmentEntity.toDTO() = PostAttachment(postId.value, mediaId.value)

class PostsService {
    suspend fun all(): List<Post> = tx {
        PostEntity.all().map(PostEntity::toDTO)
    }

    suspend fun add(
        title: String,
        text: String,
        eventId: UUID,
        createdBy: UUID,
        medias: List<UUID> = listOf(),
        createdAs: UUID? = null
    ): Post = tx {
        val mediaEntities = medias.map { MediaEntity.findById(it)!! }

        println("mediaEntities: $mediaEntities")
        val p = PostEntity.new {
            this.title = title
            this.text = text
            this.eventId = EntityID(eventId, Events)
            this.createdBy = EntityID(createdBy, Actors)
            this.createdAs = createdAs?.let { EntityID(it, Events) }
            this.medias = SizedCollection(mediaEntities)
        }
        println("p: ${p.medias}")
        // TODO existing entities are not returned by new
        p.medias = SizedCollection(mediaEntities)
        p.toDTO()
    }

    // NOTE: no tx
    fun linkMedia(id: UUID, mediaId: UUID): Pair<Post, PostAttachment>? {
        val attachmentId = CompositeID {
            it[PostAttachments.postId] = id
            it[PostAttachments.mediaId] = mediaId
        }

        val attachment = PostAttachmentEntity.new(attachmentId) {}.toDTO()

        // query after creating the attachment to include new media in medias
        val post = PostEntity.findById(id) ?: return null
        println("post.medias: ${post.medias.map(MediaEntity::toDTO)}}")

        return Pair(post.toDTO(), attachment)
    }
}

val postsService = PostsService()

package app.opia.services

import app.opia.routes.Post
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Table
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

fun PostEntity.toPost() = Post(
    id.value,
    title,
    text,
    eventId.value,
    createdBy.value,
    createdAs?.value,
    createdAt,
    deletedAt,
    medias.map(MediaEntity::toMedia)
)

// PostAttachment
object PostAttachments : Table() {
    val post = reference("post", Posts)
    val media = reference("media", Medias)

    override val primaryKey = PrimaryKey(post, media)
}

class PostsService {
    suspend fun all(): List<Post> = tx {
        PostEntity.all().map(PostEntity::toPost)
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
        p.toPost()
    }
}

val postsService = PostsService()

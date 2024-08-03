package app.opia.services

import app.opia.routes.Post
import app.opia.services.DatabaseSingleton.tx
import app.opia.utils.UUIDSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Events : UUIDTable() {
    val name = varchar("name", 50)
    val desc = varchar("desc", 1000)
    val createdBy = reference("created_by", Actors)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class EventEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EventEntity>(Events)

    var name by Events.name
    var desc by Events.desc
    var createdBy by Events.createdBy
    var createdAt by Events.createdAt
    var deletedAt by Events.deletedAt

    val posts by PostEntity referrersOn Posts.eventId
}

fun EventEntity.toEvent() =
    Event(id.value, name, desc, createdBy.value, createdAt, deletedAt, posts.map(PostEntity::toPost))

@Serializable
data class Event(
    @Serializable(UUIDSerializer::class) val id: UUID,
    val name: String,
    val desc: String,
    @Serializable(UUIDSerializer::class) val createdBy: UUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?,
    val posts: List<Post>
)

class EventsService {
    suspend fun all(): List<Event> = tx {
        EventEntity.all().map { it.toEvent() }
    }

    suspend fun get(id: UUID): Event = tx {
        EventEntity[id].toEvent()
    }

    suspend fun add(name: String, desc: String, createdBy: UUID): Event = tx {
        EventEntity.new {
            this.name = name.trim()
            this.desc = desc.trim()
            this.createdBy = EntityID(createdBy, Actors)
        }.toEvent()
    }
}

val eventsService = EventsService()

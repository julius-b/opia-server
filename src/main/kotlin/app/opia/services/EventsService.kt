package app.opia.services

import app.opia.routes.Event
import app.opia.routes.EventMembership
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

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

    //var href by Events.href
    var createdBy by Events.createdBy
    var createdAt by Events.createdAt
    var deletedAt by Events.deletedAt

    val posts by PostEntity referrersOn Posts.eventId
}

object EventMemberships : UUIDTable() {
    val actorId = reference("actor_id", Actors)
    val eventId = reference("event_id", Events)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class EventMembershipEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<EventMembershipEntity>(EventMemberships)

    var actorId by EventMemberships.actorId
    var eventId by EventMemberships.eventId
    var createdAt by EventMemberships.createdAt

    val actor by ActorEntity referencedOn EventMemberships.actorId
    val event by EventEntity referencedOn EventMemberships.eventId
}

class EventsService {
    suspend fun all(): List<Event> = tx {
        EventEntity.all().map { it.toDTO() }
    }

    suspend fun get(id: UUID): Event = tx {
        EventEntity[id].toDTO()
    }

    suspend fun add(name: String, desc: String, createdBy: UUID): Event = tx {
        EventEntity.new {
            this.name = name.trim()
            this.desc = desc.trim()
            this.createdBy = EntityID(createdBy, Actors)
        }.toDTO()
    }

    suspend fun join(eventId: UUID, actorId: UUID): EventMembership = tx {
        EventMembershipEntity.new {
            this.eventId = EntityID(eventId, Events)
            this.actorId = EntityID(actorId, Actors)
        }.toDTO()
    }
}

fun EventEntity.toDTO() =
    Event(id.value, name, desc, createdBy.value, createdAt, deletedAt, posts.map(PostEntity::toDTO))

fun EventMembershipEntity.toDTO() =
    EventMembership(id.value, event.toDTO(), actor.toDTO(), createdAt)

val eventsService = EventsService()

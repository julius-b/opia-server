package app.opia.services

import app.opia.routes.ActorLink
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object ActorLinks : UUIDTable() {
    val actorId = reference("actor_id", Actors)
    val peerId = reference("peer_id", Actors)
    val perm = enumerationByName<ActorLink.Perm>("perm", 10).default(ActorLink.Perm.Invited)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val createdBy = reference("created_by", Actors)
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(actorId, peerId) { deletedAt eq null }
    }
}

class ActorLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ActorLinkEntity>(ActorLinks)

    var actorId by ActorLinks.actorId
    var peerId by ActorLinks.peerId
    var perm by ActorLinks.perm
    var createdAt by ActorLinks.createdAt
    var createdBy by ActorLinks.createdBy
    var deletedAt by ActorLinks.deletedAt
}

fun ActorLinkEntity.toDTO() = ActorLink(actorId.value, peerId.value, perm, createdAt, createdBy.value, deletedAt)

class ActorLinksService {
    suspend fun all(): List<ActorLink> = tx {
        ActorLinkEntity.all().map(ActorLinkEntity::toDTO)
    }

    suspend fun listByActor(actorId: UUID): List<ActorLink> = tx {
        ActorLinkEntity.find { (ActorLinks.actorId eq actorId) and (ActorLinks.deletedAt eq null) }
            .map(ActorLinkEntity::toDTO)
    }

    suspend fun get(id: UUID): ActorLink? = tx {
        ActorLinkEntity.findById(id)?.toDTO()
    }

    suspend fun create(actorId: UUID, peerId: UUID): ActorLink = tx {
        ActorLinkEntity.new {
            this.actorId = EntityID(actorId, Actors)
            this.peerId = EntityID(peerId, Actors)
        }.toDTO()
    }
}

val actorLinksService = ActorLinksService()

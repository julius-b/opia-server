package app.opia.services

import app.opia.routes.Actor
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Actors : UUIDTable() {
    val type = enumerationByName<Actor.Type>("type", 10).default(Actor.Type.Account)
    val auth = enumerationByName<Actor.Auth>("auth", 10).default(Actor.Auth.Default)
    val handle = varchar("handle", 18)
    val name = varchar("name", 50)
    val desc = varchar("desc", 250).nullable()
    val secret = varchar("secret", 1000)

    val profileId = reference("profile_id", Medias).nullable()
    val bannerId = reference("banner_id", Medias).nullable()
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(handle) { deletedAt eq null }
    }
}

class ActorEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ActorEntity>(Actors)

    var type by Actors.type
    var auth by Actors.auth
    var handle by Actors.handle
    var name by Actors.name
    var desc by Actors.desc
    var secret by Actors.secret

    var profileId by Actors.profileId
    var bannerId by Actors.bannerId
    var createdAt by Actors.createdAt
    var deletedAt by Actors.deletedAt
}

fun ActorEntity.toDTO() =
    Actor(id.value, type, auth, handle, name, desc, secret, profileId?.value, bannerId?.value, createdAt, deletedAt)

object SecretUpdates : UUIDTable() {
    val actorId = reference("actor_id", Actors)
    val secret = varchar("secret", 1000)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class SecretUpdateEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<SecretUpdateEntity>(SecretUpdates)

    var actorId by SecretUpdates.actorId
    var secret by SecretUpdates.secret
    var createdAt by SecretUpdates.createdAt
}

class ActorsService {
    // TODO filter deletedAt
    suspend fun all(): List<Actor> = tx {
        ActorEntity.all().map(ActorEntity::toDTO)
    }

    // TODO filter deletedAt
    suspend fun get(id: UUID): Actor? = tx {
        ActorEntity.findById(id)?.toDTO()
    }

    suspend fun getByHandle(handle: String): Actor? = tx {
        ActorEntity.find { (Actors.handle eq handle.sanitizeHandle()) and (Actors.deletedAt eq null) }.firstOrNull()
            ?.toDTO()
    }

    suspend fun create(handle: String, name: String, secret: String): Actor = tx {
        ActorEntity.new {
            this.handle = handle.sanitizeHandle()
            this.name = name
            this.secret = secret
        }.toDTO()
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        Actors.deleteWhere { Actors.id eq id } > 0
    }

    suspend fun patch(id: UUID, name: String? = null, desc: String? = null): Actor? = tx {
        ActorEntity.findByIdAndUpdate(id) { actor ->
            name?.let { actor.name = it }
            desc?.let { actor.desc = it }
        }?.toDTO()
    }

    // NOTE: no tx
    fun updateProfile(id: UUID, mediaId: UUID): Actor? {
        return ActorEntity.findByIdAndUpdate(id) {
            it.profileId = EntityID(mediaId, Medias)
        }?.toDTO()
    }

    // NOTE: no tx
    fun updateBanner(id: UUID, mediaId: UUID): Actor? {
        return ActorEntity.findByIdAndUpdate(id) {
            it.bannerId = EntityID(mediaId, Medias)
        }?.toDTO()
    }

    suspend fun createSecretUpdate(actorId: UUID, secret: String): Unit = tx {
        SecretUpdateEntity.new {
            this.actorId = EntityID(actorId, Actors)
            this.secret = secret
        }
    }

    // TODO ensure latest created_at
    // secret: hash
    // TODO maybe delete old
    suspend fun getLatestSecretUpdate(actorId: UUID, secret: String): SecretUpdateEntity? = tx {
        SecretUpdateEntity.find { (SecretUpdates.actorId eq actorId) and (SecretUpdates.secret eq secret) }
            .firstOrNull()
    }
}

fun String.sanitizeUnique() = this.sanitizeHandle()
fun String.sanitizeHandle() = this.trim().lowercase()
fun String.sanitizeSecret() = this.trim().replaceFirstChar { it.lowercaseChar() }

val actorsService = ActorsService()

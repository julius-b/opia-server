package app.opia.services

import app.opia.routes.AuthSession
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object AuthSessions : UUIDTable() {
    val actorId = reference("actor_id", Actors)
    val installationId = reference("installation_id", Installations)

    // TODO ilid
    val ioid = reference("ioid", InstallationLinks)
    val secretUpdateId = reference("secret_update_id", SecretUpdates)
    val refreshToken = varchar("refresh_token", 100)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class AuthSessionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AuthSessionEntity>(AuthSessions)

    var actorId by AuthSessions.actorId
    var installationId by AuthSessions.installationId
    var ioid by AuthSessions.ioid
    var secretUpdateId by AuthSessions.secretUpdateId
    var refreshToken by AuthSessions.refreshToken
    var createdAt by AuthSessions.createdAt
    var deletedAt by AuthSessions.deletedAt
}

fun AuthSessionEntity.toDTO() = AuthSession(
    id.value,
    actorId.value,
    installationId.value,
    ioid.value,
    secretUpdateId.value,
    refreshToken,
    null,
    createdAt,
    deletedAt
)

class AuthSessionsService {
    suspend fun all(): List<AuthSession> = tx {
        AuthSessionEntity.all().map { it.toDTO() }
    }

    suspend fun get(id: UUID): AuthSession? = tx {
        AuthSessionEntity.findById(id)?.toDTO()
    }

    suspend fun getByRefreshToken(refreshToken: String, installationId: UUID): AuthSession? = tx {
        AuthSessionEntity.find { (AuthSessions.refreshToken eq refreshToken) and (AuthSessions.installationId eq installationId) }
            .firstOrNull()?.toDTO()
    }

    suspend fun create(
        actorId: UUID, installationId: UUID, ioid: UUID, secretUpdateId: UUID, refreshToken: String
    ): AuthSession = tx {
        AuthSessionEntity.new {
            this.actorId = EntityID(actorId, Actors)
            this.installationId = EntityID(installationId, Installations)
            this.ioid = EntityID(ioid, InstallationLinks)
            this.secretUpdateId = EntityID(secretUpdateId, SecretUpdates)
            this.refreshToken = refreshToken
        }.toDTO()
    }
}

val authSessionsService = AuthSessionsService()

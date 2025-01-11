package app.opia.services

import app.opia.routes.Installation
import app.opia.routes.InstallationLink
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

object Installations : UUIDTable() {
    val name = varchar("name", 50)
    val desc = varchar("desc", 250)
    val os = enumerationByName<Installation.Os>("os", 10)
    val clientVersionName = varchar("client_version_name", 50)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

class InstallationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InstallationEntity>(Installations)

    var name by Installations.name
    var desc by Installations.desc
    var os by Installations.os
    var clientVersionName by Installations.clientVersionName
    var createdAt by Installations.createdAt
    var deletedAt by Installations.deletedAt
}

fun InstallationEntity.toDTO() =
    Installation(id.value, name, desc, os, clientVersionName, createdAt, deletedAt)

object InstallationLinks : UUIDTable() {
    val installationId = reference("installation_id", Installations)
    val actorId = reference("actor_id", Actors)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(installationId, actorId) { deletedAt eq null }
    }
}

class InstallationLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InstallationLinkEntity>(InstallationLinks)

    var installationId by InstallationLinks.installationId
    var actorId by InstallationLinks.actorId
    var createdAt by InstallationLinks.createdAt
    var deletedAt by InstallationLinks.deletedAt
}

fun InstallationLinkEntity.toDTO() = InstallationLink(
    id.value, installationId.value, actorId.value, createdAt, deletedAt
)

class InstallationsService {
    suspend fun all(): List<Installation> = tx {
        InstallationEntity.all().map(InstallationEntity::toDTO)
    }

    suspend fun get(id: UUID): Installation? = tx {
        InstallationEntity.findById(id)?.toDTO()
    }

    suspend fun create(
        id: UUID, name: String, desc: String, os: Installation.Os, clientVersionName: String
    ): Installation = tx {
        val curr = InstallationEntity.findByIdAndUpdate(id) {
            it.name = name
            it.desc = desc
            it.os = os
            it.clientVersionName = clientVersionName
        }
        if (curr != null) return@tx curr.toDTO()

        InstallationEntity.new(id) {
            this.name = name
            this.desc = desc
            this.os = os
            this.clientVersionName = clientVersionName
        }.toDTO()
    }

    suspend fun linkInstallation(actorId: UUID, installationId: UUID): InstallationLink = tx {
        InstallationLinkEntity.new {
            this.installationId = EntityID(installationId, Installations)
            this.actorId = EntityID(actorId, Actors)
        }.toDTO()
    }

    suspend fun listLinks(aid: UUID): List<InstallationLink> = tx {
        InstallationLinkEntity.find { InstallationLinks.actorId eq aid }.map(InstallationLinkEntity::toDTO)
    }

    suspend fun allLinks(): List<InstallationLink> = tx {
        InstallationLinkEntity.all().map(InstallationLinkEntity::toDTO)
    }

    suspend fun getLink(ioid: UUID): InstallationLink? = tx {
        InstallationLinkEntity.findById(ioid)?.toDTO()
    }

    suspend fun deleteLinks(actorId: UUID, installationId: UUID) = tx {
        InstallationLinkEntity.find { (InstallationLinks.actorId eq actorId) and (InstallationLinks.installationId eq installationId) }
            .forUpdate().forEach { it.deletedAt = Clock.System.now() }
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        Installations.deleteWhere { Installations.id eq id } > 0
    }
}

val installationsService = InstallationsService()

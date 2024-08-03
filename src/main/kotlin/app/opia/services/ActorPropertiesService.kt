package app.opia.services

import app.opia.routes.ActorProperty
import app.opia.services.DatabaseSingleton.tx
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.security.SecureRandom
import java.util.*

object ActorProperties : UUIDTable() {
    // nullable during signup
    val actorId = reference("actor_id", Actors).nullable()
    val installationId = reference("installation_id", Installations)
    val type = enumerationByName<ActorProperty.Type>("type", 10)
    val content = varchar("content", 125)
    val verificationCode = varchar("verification_code", 25)
    val valid = bool("valid").default(false)
    val primary = bool("primary").nullable()
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()

    init {
        uniqueIndex(content) { (valid eq true) and (primary eq true) and (deletedAt eq null) }
    }
}

class ActorPropertyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ActorPropertyEntity>(ActorProperties)

    var actorId by ActorProperties.actorId
    var installationId by ActorProperties.installationId
    var type by ActorProperties.type
    var content by ActorProperties.content
    var verificationCode by ActorProperties.verificationCode
    var valid by ActorProperties.valid
    var primary by ActorProperties.primary
    var createdAt by ActorProperties.createdAt
    var deletedAt by ActorProperties.deletedAt
}

fun ActorPropertyEntity.toActorProperty() = ActorProperty(
    id.value,
    actorId?.value,
    installationId.value,
    type,
    content,
    verificationCode,
    valid,
    primary,
    createdAt,
    deletedAt
)

class ActorPropertiesService {
    private val log = KtorSimpleLogger("actor-props-svc")

    suspend fun all(): List<ActorProperty> = tx {
        ActorPropertyEntity.all().map(ActorPropertyEntity::toActorProperty)
    }

    suspend fun get(id: UUID): ActorProperty? = tx {
        ActorPropertyEntity.find { (ActorProperties.id eq id) and (ActorProperties.deletedAt eq null) }.firstOrNull()
            ?.toActorProperty()
    }

    suspend fun list(actorId: UUID): List<ActorProperty> = tx {
        ActorPropertyEntity.find { (ActorProperties.actorId eq actorId) and (ActorProperties.deletedAt eq null) }
            .map(ActorPropertyEntity::toActorProperty)
    }

    suspend fun getPrimaryByContent(content: String): ActorProperty? = tx {
        ActorPropertyEntity.find { (ActorProperties.primary eq true) and (ActorProperties.content eq content) and (ActorProperties.deletedAt eq null) }
            .firstOrNull()?.toActorProperty()
    }

    suspend fun create(installationId: UUID, type: ActorProperty.Type, content: String): ActorProperty = tx {
        // [min, max)
        val verificationCode = SecureRandom.getInstanceStrong().nextInt(100_000, 1_000_000)
        log.info("create - verificationCode: $verificationCode")

        ActorPropertyEntity.new {
            this.installationId = EntityID(installationId, Installations)
            this.type = type
            this.content = content
            this.verificationCode = verificationCode.toString()
        }.toActorProperty()
    }

    suspend fun validateProperty(id: UUID): ActorProperty? = tx {
        ActorPropertyEntity.findByIdAndUpdate(id) {
            it.valid = true
        }?.toActorProperty()
    }

    suspend fun ownAndPrimarizeProperty(id: UUID, actorId: UUID): ActorProperty? = tx {
        ActorPropertyEntity.findByIdAndUpdate(id) {
            it.actorId = EntityID(actorId, Actors)
            it.primary = true
        }?.toActorProperty()
    }

    // TODO Entity syntax
    suspend fun delete(id: UUID): Boolean = tx {
        ActorProperties.deleteWhere { ActorProperties.id eq id } > 0
    }
}

val actorPropertiesService = ActorPropertiesService()

package app.opia.services

import app.opia.routes.Media
import app.opia.services.DatabaseSingleton.tx
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Medias : UUIDTable() {
    val size = integer("size")
    val name = varchar("name", 128)
    val contentDesc = varchar("content_desc", 1024)
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

// TODO keep filename as an internal thing, name idk
// caption is per usage
class MediaEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MediaEntity>(Medias)

    var size by Medias.size
    var name by Medias.name
    var contentDesc by Medias.contentDesc
    val createdAt by Medias.createdAt
    val deletedAt by Medias.deletedAt
}

fun MediaEntity.toMedia() = Media(id.value, size, name, contentDesc, createdAt, deletedAt)

// it's a word: https://en.wiktionary.org/wiki/medias#English
class MediasService {
    suspend fun all(): List<Media> = tx {
        MediaEntity.all().map(MediaEntity::toMedia)
    }

    suspend fun get(id: UUID): Media? = tx {
        MediaEntity.findById(id)?.toMedia()
    }

    suspend fun add(size: Int, name: String, contentDesc: String, id: UUID? = null): Media = tx {
        MediaEntity.new(id) {
            this.size = size
            this.name = name
            this.contentDesc = contentDesc
        }.toMedia()
    }
}

val mediasService = MediasService()

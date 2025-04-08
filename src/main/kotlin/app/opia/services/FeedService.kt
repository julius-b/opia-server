package app.opia.services

import app.opia.routes.SimpleFeedItem
import app.opia.services.DatabaseSingleton.tx
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.selectAll

object FeedView : Table("feed") {
    val postId = reference("post_id", Posts).nullable()
    val eventMembershipId = reference("event_membership_id", EventMemberships).nullable()
    val createdAt = timestamp("created_at")
}

class FeedService {
    suspend fun all(): List<SimpleFeedItem> = tx {
        FeedView.selectAll().map {
            SimpleFeedItem(
                it[FeedView.postId]?.value,
                it[FeedView.eventMembershipId]?.value,
                it[FeedView.createdAt]
            )
        }
    }
}

val feedService = FeedService()

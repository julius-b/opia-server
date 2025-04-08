package app.opia.routes

import app.opia.services.feedService
import app.opia.utils.UUIDSerializer
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SimpleFeedItem(
    @Serializable(UUIDSerializer::class) val postId: UUID?,
    @Serializable(UUIDSerializer::class) val eventMembershipId: UUID?,
    @SerialName("created_at") val createdAt: Instant,
)

// no id, view queried server-side & locally (latest)
@Serializable
sealed class FeedItem {
    abstract val event: Event
    abstract val account: Actor
    abstract val createdAt: LocalDateTime

    @Serializable
    data class EventJoin(
        // TODO event_membership, for detail view
        override val event: Event,
        override val account: Actor,
        override val createdAt: LocalDateTime,
    ) : FeedItem()

    data class EventPost(
        val post: Post,
        override val event: Event,
        override val account: Actor,
        override val createdAt: LocalDateTime,
    ) : FeedItem()
}

fun Route.feedApi() {
    route("feed") {
        get {
            call.respond(ApiSuccessResponse(data = feedService.all()))
        }
    }
}

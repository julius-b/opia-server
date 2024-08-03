package app.opia.services

import app.opia.routes.CreateMessage
import app.opia.routes.Message
import app.opia.routes.MessagePacket
import app.opia.routes.MessageReceipt
import app.opia.services.DatabaseSingleton.tx
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Messages : UUIDTable() {
    val fromId = reference("from_id", Actors)
    val rcptId = reference("rcpt_id", Actors)
    val attCnt = integer("att_cnt")
    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
    val deletedAt = timestamp("deleted_at").nullable()
}

//val hsId =
object MessagePackets : UUIDTable() {
    val msgId = reference("msg_id", Messages)
    val fromIOID = reference("from_ioid", InstallationLinks)
    val rcptId = reference("rcpt_id", Actors)
    val rcptIOID = reference("rcpt_ioid", InstallationLinks)
    val dup = integer("dup").default(0)
    val seqno = integer("seqno")
    val timestamp = timestamp("timestamp")
    val payloadEnc = binary("payload_enc")
}

// unique: msg_id-rcpt_ioid
object MessageReceipts : UUIDTable() {
    val msgId = reference("msg_id", Messages)
    val rcptIOID = reference("rcpt_ioid", InstallationLinks)
    val dup = integer("dup").default(0)
    val recvAt = timestamp("recv_at").nullable().default(null)
    val rjctAt = timestamp("rjct_at").nullable().default(null)
    val readAt = timestamp("read_at").nullable().default(null)
}

class MessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageEntity>(Messages)

    var fromId by Messages.fromId
    var rcptId by Messages.rcptId
    var attCnt by Messages.attCnt
    val createdAt by Messages.createdAt
    val deletedAt by Messages.deletedAt

    val packets by MessagePacketEntity referrersOn MessagePackets.msgId
    val receipts by MessageReceiptEntity referrersOn MessageReceipts.msgId
}

fun MessageEntity.toMessage() = Message(
    id.value,
    fromId.value,
    rcptId.value,
    attCnt,
    packets.map(MessagePacketEntity::toMessagePacket),
    receipts.map(MessageReceiptEntity::toMessageReceipt),
    createdAt,
    deletedAt
)

fun MessageEntity.toRawMessage() = Message(
    id.value, fromId.value, rcptId.value, attCnt, emptyList(), emptyList(), createdAt, deletedAt
)

class MessagePacketEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessagePacketEntity>(MessagePackets)

    var msgId by MessagePackets.msgId
    var fromIOID by MessagePackets.fromIOID
    var rcptId by MessagePackets.rcptId
    var rcptIOID by MessagePackets.rcptIOID
    var dup by MessagePackets.dup
    var seqno by MessagePackets.seqno
    var timestamp by MessagePackets.timestamp
    var payloadEnc by MessagePackets.payloadEnc

    var msg by MessageEntity referencedOn MessagePackets.msgId
}

fun MessagePacketEntity.toMessagePacket() = MessagePacket(rcptIOID.value, dup, seqno, payloadEnc)
fun MessagePacketEntity.toFullMessagePacket() =
    MessagePacket(rcptIOID.value, dup, seqno, payloadEnc, msg.toRawMessage())

class MessageReceiptEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageReceiptEntity>(MessageReceipts)

    var msgId by MessageReceipts.msgId
    var rcptIOID by MessageReceipts.rcptIOID
    var dup by MessageReceipts.dup
    var recvAt by MessageReceipts.recvAt
    var rjctAt by MessageReceipts.rjctAt
    var readAt by MessageReceipts.readAt
}

fun MessageReceiptEntity.toMessageReceipt() = MessageReceipt(msgId.value, rcptIOID.value, dup, recvAt, rjctAt, readAt)

class MessagesService {
    val log = KtorSimpleLogger("messages-service")

    suspend fun all(): List<Message> = tx {
        MessageEntity.all().map(MessageEntity::toMessage)
    }

    suspend fun get(id: UUID): Message? = tx {
        MessageEntity.findById(id)?.toMessage()
    }

    suspend fun add(aid: UUID, ioid: UUID, msg: CreateMessage): Message = tx {
        val curr = MessageEntity.findById(msg.id)
        if (curr != null) {
            // maybe verify packets, etc. match
            // but let's consider it the client's job to understand the response correctly
            log.info("add: returning existing entity")
            return@tx curr.toMessage()
        }

        val result = MessageEntity.new(msg.id) {
            this.fromId = EntityID(aid, Actors)
            this.rcptId = EntityID(msg.rcptId, Actors)
            this.attCnt = 0
        }

        val packets = mutableListOf<MessagePacketEntity>()
        val receipts = mutableListOf<MessageReceiptEntity>()

        for (packet in msg.packets) {
            packets += MessagePacketEntity.new {
                this.msgId = EntityID(msg.id, Messages)
                this.fromIOID = EntityID(ioid, InstallationLinks)
                this.rcptId = EntityID(msg.rcptId, Actors)
                this.rcptIOID = EntityID(packet.rcptIOID, InstallationLinks)
                this.dup = packet.dup
                this.seqno = packet.seqno
                this.timestamp = msg.timestamp
                this.payloadEnc = packet.payloadEnc
            }

            receipts += MessageReceiptEntity.new {
                this.msgId = EntityID(msg.id, Messages)
                this.rcptIOID = EntityID(packet.rcptIOID, InstallationLinks)
                this.dup = packet.dup
            }
        }

        // load probably not necessary so long as toMessage is called within tx
        result.load(MessageEntity::packets).load(MessageEntity::receipts).toMessage()
    }

    // list all messages for aid but only include packets for ioid
    // -> client can request messages where no packet for them exists (but this doesn't even happen...)
    // or maybe the server should request
    suspend fun listUnacknowledged(aid: UUID, ioid: UUID): List<MessagePacket> = tx {
        // either msg.rcpt = aid or aid member of msg.rcpt
        // or just go by packet :)
        //val q = MessagePackets.innerJoin(Messages)
        //    .select(MessagePackets.columns, Messages.columns)
        //    .where { MessagePackets.rcptIOID eq ioid }
        //MessagePacketEntity.wrapRows(q).toList()

        MessagePacketEntity.find { MessagePackets.rcptIOID eq ioid }.map(MessagePacketEntity::toFullMessagePacket)
    }
}

val messagesService = MessagesService()

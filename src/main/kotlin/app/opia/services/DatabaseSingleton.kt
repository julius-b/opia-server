package app.opia.services

import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseSingleton {
    private val LOGGER = KtorSimpleLogger("DB")

    fun init(config: ApplicationConfig) {
        val driver = config.property("storage.driver").getString()
        val url = config.property("storage.url").getString()

        LOGGER.info("driver: $driver, url: $url")
        val database = Database.connect(url, driver)
        transaction(database) {
            SchemaUtils.create(Installations)
            SchemaUtils.create(Actors)
            SchemaUtils.create(InstallationLinks)
            SchemaUtils.create(ActorProperties)
            SchemaUtils.create(ActorLinks)
            SchemaUtils.create(AuthSessions)
            SchemaUtils.create(Messages)
            SchemaUtils.create(MessagePackets)
            SchemaUtils.create(MessageReceipts)
            SchemaUtils.create(SecretUpdates)
            SchemaUtils.create(Medias)
            SchemaUtils.create(Events)
            SchemaUtils.create(Posts)
            SchemaUtils.create(PostAttachments)
        }
    }

    // `newSuspendedTransaction` and `suspendedTransactionAsync` are always executed in a new transaction to prevent
    // concurrency issues when query execution order could be changed by the `CoroutineDispatcher`. This means that
    // nesting these suspend transactions may not result in the same behavior as nested `transaction`s
    // (when `useNestedTransactions = false`), as was shown in the previous section.
    // https://jetbrains.github.io/Exposed/transactions.html#advanced-parameters-and-usage
    suspend fun <T> tx(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
}

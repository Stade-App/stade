package app.stade

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.stade.contact.ContactManager
import app.stade.contact.HandshakeService
import app.stade.crypto.CryptoApi
import app.stade.crypto.PqCrypto
import app.stade.crypto.RatchetSessions
import app.stade.crypto.platformCrypto
import app.stade.crypto.platformPq
import app.stade.db.DriverFactory
import app.stade.db.StadeDb
import app.stade.group.GroupChatService
import app.stade.group.GroupManager
import app.stade.identity.IdentityManager
import app.stade.message.ChatService
import app.stade.message.FingerprintService
import app.stade.message.MessageManager
import app.stade.security.SecretStore
import app.stade.security.Vault
import app.stade.sync.Outbox
import app.stade.sync.SyncEngine
import app.stade.transport.ConnectionManager
import app.stade.transport.ConnectionRegistry
import app.stade.transport.TransportPlugin
import app.stade.transport.TransportSettings
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(
    driverFactory: DriverFactory,
    val vault: Vault,
    transportFactory: (StadeDb) -> List<TransportPlugin> = { emptyList() }
) {
    val crypto: CryptoApi = platformCrypto()
    val pq: PqCrypto = platformPq()

    val db: StadeDb = run {
        val driver = driverFactory.create(vault.plaintextDbPath())
        val schemaOk = runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT mlkemPublicKey FROM Contact LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess
        if (!schemaOk) {
            val indexes = listOf("idxMessageContact", "idxOutboxContact")
            val tables = listOf(
                "Outbox", "Message", "Contact", "PendingContact",
                "LocalIdentity", "TransportSetting", "KeyValue"
            )
            indexes.forEach { runCatching { driver.execute(null, "DROP INDEX IF EXISTS $it", 0) } }
            tables.forEach { runCatching { driver.execute(null, "DROP TABLE IF EXISTS $it", 0) } }
            StadeDb.Schema.create(driver)
        }
        val database = StadeDb(driver)
        runCatching {
            database.stadeDbQueries.putKv("schema.version", "2".encodeToByteArray())
        }
        runCatching {
            driver.executeQuery(null, "SELECT id FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { driver.execute(null, "CREATE TABLE IF NOT EXISTS StadeGroup (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, inviteToken TEXT NOT NULL, createdAt INTEGER NOT NULL)", 0) }
            runCatching { driver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMember (groupId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(groupId, contactId))", 0) }
            runCatching { driver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMessage (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, senderId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { driver.execute(null, "CREATE INDEX IF NOT EXISTS idxGroupMessage ON GroupMessage(groupId, timestamp)", 0) }
        }
        database
    }

    val identities = IdentityManager(db, crypto, pq)
    val contacts = ContactManager(db, crypto)
    val messages = MessageManager(db, crypto)
    val fingerprint = FingerprintService(crypto)
    val handshake = HandshakeService(crypto, pq)
    val outbox = Outbox(db, crypto)
    val ratchet = RatchetSessions(crypto, pq, contacts)
    val groups = GroupManager(db, crypto)
    val sync = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, handshake, groups)
    val chat = ChatService(messages, sync)
    val groupChat = GroupChatService(groups, sync, contacts, crypto)
    val transports = ConnectionRegistry().also { reg ->
        transportFactory(db).forEach { reg.register(it) }
    }
    val transportSettings = TransportSettings(db)
    val connections = ConnectionManager(transports, contacts, sync).also {
        sync.selfAddressesProvider = { it.selfAddresses() }
    }

    val screenshotSettingTick = MutableStateFlow(0)

    val secrets = SecretStore(db, crypto, vault,
        onScreenshotSettingChanged = { screenshotSettingTick.value++ })

    @Volatile var activeContactId: String? = null

    var isAppInForeground = MutableStateFlow(true)

    val pendingInvite = MutableStateFlow<String?>(null)

    suspend fun wipeAllData() {
        runCatching { connections.stop() }
        pendingInvite.value = null
        activeContactId = null
        db.stadeDbQueries.transaction {
            db.stadeDbQueries.wipeGroupMessages()
            db.stadeDbQueries.wipeGroupMembers()
            db.stadeDbQueries.wipeGroups()
            db.stadeDbQueries.wipeOutbox()
            db.stadeDbQueries.wipeMessages()
            db.stadeDbQueries.wipePending()
            db.stadeDbQueries.wipeContacts()
            db.stadeDbQueries.wipeIdentities()
            db.stadeDbQueries.wipeTransports()
            db.stadeDbQueries.wipeKeyValue()
        }
        runCatching {
            db.stadeDbQueries.putKv("schema.version", "2".encodeToByteArray())
        }
        runCatching { vault.wipe() }
    }
}

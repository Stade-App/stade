package app.stade

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
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

    val db: StadeDb
    private val driver: SqlDriver

    init {
        val createdDriver = driverFactory.create(vault.plaintextDbPath())
        driver = createdDriver
        val schemaOk = runCatching {
            createdDriver.executeQuery(
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
            indexes.forEach { runCatching { createdDriver.execute(null, "DROP INDEX IF EXISTS $it", 0) } }
            tables.forEach { runCatching { createdDriver.execute(null, "DROP TABLE IF EXISTS $it", 0) } }
            StadeDb.Schema.create(createdDriver)
        }
        val database = StadeDb(createdDriver)
        runCatching {
            database.stadeDbQueries.putKv("schema.version", "2".encodeToByteArray())
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadeGroup (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, inviteToken TEXT NOT NULL, createdAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMember (groupId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(groupId, contactId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMessage (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, senderId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxGroupMessage ON GroupMessage(groupId, timestamp)", 0) }
        }
        db = database
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
        runCatching {
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
        }
        // Windows'ta plaintext DB dosyasını silebilmek için sürücüyü kapatmamız şart;
        // aksi hâlde vault.wipe() dosyayı silemez ve yeni container açıldığında
        // eski veri/şema kalıntıları çakışmaya yol açar.
        runCatching { driver.close() }
        runCatching { vault.wipe() }
    }

    /**
     * Container'ı kapatır ve veri tabanı sürücüsünü serbest bırakır.
     * NOT: Bağlantılar suspend olduğu için bunları çağırmadan önce ayrıca
     * `connections.stop()` çağrılmalı.
     */
    fun close() {
        runCatching { driver.close() }
    }
}

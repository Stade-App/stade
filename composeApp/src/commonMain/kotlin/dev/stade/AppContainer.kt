package dev.stade

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import dev.stade.chat.PinnedChats
import dev.stade.contact.ContactManager
import dev.stade.contact.HandshakeService
import dev.stade.crypto.CryptoApi
import dev.stade.crypto.PqCrypto
import dev.stade.crypto.RatchetSessions
import dev.stade.crypto.platformCrypto
import dev.stade.crypto.platformPq
import dev.stade.db.DriverFactory
import dev.stade.db.StadeDb
import dev.stade.group.GroupChatService
import dev.stade.group.GroupManager
import dev.stade.identity.IdentityManager
import dev.stade.message.ChatService
import dev.stade.message.FingerprintService
import dev.stade.message.MessageManager
import dev.stade.security.SecretStore
import dev.stade.security.Vault
import dev.stade.stadium.StadiumChatService
import dev.stade.stadium.StadiumManager
import dev.stade.sticker.StickerManager
import dev.stade.sync.Outbox
import dev.stade.sync.SyncEngine
import dev.stade.transport.ConnectionManager
import dev.stade.transport.ConnectionRegistry
import dev.stade.transport.TransportPlugin
import dev.stade.transport.TransportSettings
import dev.stade.vanish.VanishManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadeGroup (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, inviteToken TEXT NOT NULL, createdAt INTEGER NOT NULL, creatorStadeId TEXT NOT NULL DEFAULT '')", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMember (groupId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(groupId, contactId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS GroupMessage (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, senderId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxGroupMessage ON GroupMessage(groupId, timestamp)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT creatorStadeId FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE StadeGroup ADD COLUMN creatorStadeId TEXT NOT NULL DEFAULT ''", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT messageId FROM MessageReaction LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS MessageReaction (messageId TEXT NOT NULL, fromId TEXT NOT NULL, emoji TEXT NOT NULL, PRIMARY KEY(messageId, fromId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxMessageReaction ON MessageReaction(messageId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT kind FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN kind INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM Stadium LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS Stadium (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, name TEXT NOT NULL, creatorStadeId TEXT NOT NULL, isOwner INTEGER NOT NULL DEFAULT 0, inviteToken TEXT NOT NULL, memberCount INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, muted INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadiumMember (stadiumId TEXT NOT NULL, contactId TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(stadiumId, contactId))", 0) }
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS StadiumMessage (id TEXT NOT NULL PRIMARY KEY, stadiumId TEXT NOT NULL, body TEXT NOT NULL, timestamp INTEGER NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxStadiumMessage ON StadiumMessage(stadiumId, timestamp)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM Stadium LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Stadium ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM Contact LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Contact ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT muted FROM StadeGroup LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE StadeGroup ADD COLUMN muted INTEGER NOT NULL DEFAULT 0", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT messageId FROM ProcessedEnvelope LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS ProcessedEnvelope (messageId TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, processedAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxProcessedEnvelope ON ProcessedEnvelope(processedAt)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT id FROM Sticker LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS Sticker (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, imageBytes BLOB NOT NULL, createdAt INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxSticker ON Sticker(ownerId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT sessionId FROM VanishSession LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "CREATE TABLE IF NOT EXISTS VanishSession (sessionId TEXT NOT NULL PRIMARY KEY, contactId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxVanishSessionContact ON VanishSession(contactId)", 0) }
        }
        runCatching {
            createdDriver.executeQuery(null, "SELECT vanishSessionId FROM Message LIMIT 0",
                { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
        }.onFailure {
            runCatching { createdDriver.execute(null, "ALTER TABLE Message ADD COLUMN vanishSessionId TEXT", 0) }
            runCatching { createdDriver.execute(null, "CREATE INDEX IF NOT EXISTS idxMessageVanishSession ON Message(vanishSessionId)", 0) }
        }
        db = database
    }

    val identities = IdentityManager(db, crypto, pq)
    val contacts = ContactManager(db, crypto)
    val messages = MessageManager(db, crypto)
    val vanish = VanishManager(db, messages)
    val fingerprint = FingerprintService(crypto)
    val handshake = HandshakeService(crypto, pq)
    val outbox = Outbox(db, crypto)
    val ratchet = RatchetSessions(crypto, pq, contacts)
    val groups = GroupManager(db, crypto)
    val stadiums = StadiumManager(db, crypto)
    val stickers = StickerManager(db, crypto)
    val pinnedChats = PinnedChats(db)
    val sync = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, handshake, vanish, groups, stadiums)
    val chat = ChatService(messages, sync, vanish)
    val groupChat = GroupChatService(groups, sync, contacts, crypto)
    val stadiumChat = StadiumChatService(stadiums, sync, contacts, crypto, messages, groups)
    val transports = ConnectionRegistry().also { reg ->
        transportFactory(db).forEach { reg.register(it) }
    }
    val transportSettings = TransportSettings(db)
    val connections = ConnectionManager(transports, contacts, sync, transportSettings, isForeground = { isAppInForeground.value }).also {
        sync.selfAddressesProvider = { it.selfAddresses() }
    }

    val screenshotSettingTick = MutableStateFlow(0)

    val secrets = SecretStore(db, crypto, vault,
        onScreenshotSettingChanged = { screenshotSettingTick.value++ })

    @Volatile var activeContactId: String? = null

    var isAppInForeground = MutableStateFlow(true)

    val pendingInvite = MutableStateFlow<String?>(null)

    val pendingOpenChat = MutableStateFlow<String?>(null)

    val pendingOpenStadium = MutableStateFlow<String?>(null)

    val pendingOpenGroup = MutableStateFlow<String?>(null)

    val pendingGoHome = MutableStateFlow(false)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile var isClosed = false
        private set

    suspend fun wipeAllData() {
        isClosed = true
        runCatching { connections.stop() }
        pendingInvite.value = null
        pendingOpenChat.value = null
        pendingGoHome.value = false
        activeContactId = null
        runCatching {
            db.stadeDbQueries.transaction {
                db.stadeDbQueries.wipeGroupMessages()
                db.stadeDbQueries.wipeGroupMembers()
                db.stadeDbQueries.wipeGroups()
                db.stadeDbQueries.wipeStadiumMessages()
                db.stadeDbQueries.wipeStadiumMembers()
                db.stadeDbQueries.wipeStadiums()
                db.stadeDbQueries.wipeOutbox()
                db.stadeDbQueries.wipeMessages()
                db.stadeDbQueries.wipePending()
                db.stadeDbQueries.wipeContacts()
                db.stadeDbQueries.wipeIdentities()
                db.stadeDbQueries.wipeTransports()
                db.stadeDbQueries.wipeKeyValue()
                db.stadeDbQueries.wipeProcessedEnvelope()
                db.stadeDbQueries.wipeStickers()
                db.stadeDbQueries.wipeReactions()
                db.stadeDbQueries.wipeVanishSessions()
            }
        }
        runCatching { driver.close() }
        runCatching { vault.wipe() }
        runCatching { appScope.cancel() }
    }

    fun close() {
        isClosed = true
        appScope.launch {
            runCatching { connections.stop() }
            runCatching { appScope.cancel() }
        }
        runCatching { driver.close() }
    }
}

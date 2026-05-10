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
import app.stade.identity.IdentityManager
import app.stade.message.ChatService
import app.stade.message.FingerprintService
import app.stade.message.MessageManager
import app.stade.security.SecretStore
import app.stade.sync.Outbox
import app.stade.sync.SyncEngine
import app.stade.transport.ConnectionManager
import app.stade.transport.ConnectionRegistry
import app.stade.transport.TransportPlugin
import app.stade.transport.TransportSettings

class AppContainer(
    driverFactory: DriverFactory,
    transportFactory: (StadeDb) -> List<TransportPlugin> = { emptyList() }
) {
    val crypto: CryptoApi = platformCrypto()
    val pq: PqCrypto = platformPq()

    val db: StadeDb = run {
        val driver = driverFactory.create()
        // Schema sürüm probe — yeni v2 sütunu yoksa eski DB; sıfırla.
        val schemaOk = runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT mlkemPublicKey FROM Contact LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess
        if (!schemaOk) {
            // Tüm tabloları drop et ve schema'yı yeniden oluştur. Clean-slate.
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
        // Schema sürümünü işaretle (gelecekte ek migration tetikleyebilir)
        runCatching {
            database.stadeDbQueries.putKv("schema.version", "2".encodeToByteArray())
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
    val sync = SyncEngine(crypto, pq, contacts, messages, ratchet, outbox, handshake)
    val chat = ChatService(messages, sync)
    val transports = ConnectionRegistry().also { reg ->
        transportFactory(db).forEach { reg.register(it) }
    }
    val transportSettings = TransportSettings(db)
    val connections = ConnectionManager(transports, contacts, sync).also {
        sync.selfAddressesProvider = { it.selfAddresses() }
    }
    val secrets = SecretStore(db, crypto)

    /**
     * Şu an hangi kişinin sohbeti ekranda açık.
     * ChatScreen giriş/çıkışında set/clear edilir.
     */
    @Volatile var activeContactId: String? = null

    /**
     * Uygulama ön planda mı? Android'de ActivityLifecycleCallbacks ile yönetilir.
     * Arka plana geçildiğinde false olur → aktif sohbet için de bildirim gönderilir.
     */
    @Volatile var isAppInForeground: Boolean = true
}

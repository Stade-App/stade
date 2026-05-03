package app.stade

import app.stade.contact.ContactManager
import app.stade.contact.HandshakeService
import app.stade.crypto.CryptoApi
import app.stade.crypto.RatchetSessions
import app.stade.crypto.platformCrypto
import app.stade.db.DriverFactory
import app.stade.db.StadeDb
import app.stade.db.createDatabase
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
    val db: StadeDb = createDatabase(driverFactory)
    val identities = IdentityManager(db, crypto)
    val contacts = ContactManager(db, crypto)
    val messages = MessageManager(db, crypto)
    val fingerprint = FingerprintService(crypto)
    val handshake = HandshakeService(crypto)
    val outbox = Outbox(db, crypto)
    val ratchet = RatchetSessions(crypto, contacts)
    val sync = SyncEngine(crypto, contacts, messages, ratchet, outbox, handshake)
    val chat = ChatService(messages, sync)
    val transports = ConnectionRegistry().also { reg ->
        transportFactory(db).forEach { reg.register(it) }
    }
    val transportSettings = TransportSettings(db)
    val connections = ConnectionManager(transports, contacts, sync).also {
        sync.selfAddressesProvider = { it.selfAddresses() }
    }
    val secrets = SecretStore(db, crypto)
}

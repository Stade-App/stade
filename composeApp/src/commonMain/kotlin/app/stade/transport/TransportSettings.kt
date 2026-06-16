package app.stade.transport

import app.stade.db.StadeDb

data class TransportConfig(val type: TransportType, val enabled: Boolean, val config: String)

class TransportSettings(private val db: StadeDb) {

    fun all(): List<TransportConfig> {
        val rows = db.stadeDbQueries.selectTransports().executeAsList()
        val byType = rows.associateBy { it.type }
        return TransportType.entries.map {
            val row = byType[it.name]
            TransportConfig(it, enabled = row?.enabled?.let { e -> e == 1L } ?: defaultEnabled(it), config = row?.config ?: "")
        }
    }

    fun get(type: TransportType): TransportConfig {
        val row = db.stadeDbQueries.selectTransport(type.name).executeAsOneOrNull()
        return TransportConfig(type, row?.enabled?.let { it == 1L } ?: defaultEnabled(type), row?.config ?: "")
    }

    fun setEnabled(type: TransportType, enabled: Boolean) {
        val current = get(type)
        db.stadeDbQueries.upsertTransport(type.name, if (enabled) 1 else 0, current.config)
    }

    fun setConfig(type: TransportType, config: String) {
        val current = get(type)
        db.stadeDbQueries.upsertTransport(type.name, if (current.enabled) 1 else 0, config)
    }

    private fun defaultEnabled(type: TransportType): Boolean = when (type) {
        TransportType.LAN -> true
        TransportType.TOR -> true
        TransportType.REMOVABLE -> false
    }
}

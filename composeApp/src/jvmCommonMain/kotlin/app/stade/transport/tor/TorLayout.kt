package app.stade.transport.tor

import java.io.File

data class TorLayout(
    val torDir: File,
    val executable: File,
    val dataDir: File,
    val geoipFile: File?,
    val geoip6File: File?
)


package dev.stade.transport

expect val isTorBuiltIn: Boolean

/** True where the obfs4 pluggable-transport binary is bundled and bridge support actually works. */
expect val torBridgesSupported: Boolean


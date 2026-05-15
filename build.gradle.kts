plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.sqldelight) apply false
}

val rootPath = rootDir.absolutePath.replace('\\', '/')
val isCloudSynced = listOf("OneDrive", "Dropbox", "Google Drive", "iCloudDrive", "pCloud", "Box Sync")
    .any { rootPath.contains("/$it", ignoreCase = true) || rootPath.contains("/$it/", ignoreCase = true) }

if (isCloudSynced) {
    val safeRoot = File(System.getProperty("user.home"), ".stade-build/${rootDir.name}")
    allprojects {
        layout.buildDirectory = File(safeRoot, project.path.removePrefix(":").replace(':', '/').ifEmpty { "_root" } + "/build")
    }
}

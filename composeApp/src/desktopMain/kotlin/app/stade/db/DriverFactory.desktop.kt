package app.stade.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual class DriverFactory {
    actual fun create(dbFilePath: String): SqlDriver {
        val dbFile = File(dbFilePath)
        dbFile.parentFile?.mkdirs()
        val fresh = !dbFile.exists() || dbFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())
        if (fresh) {
            StadeDb.Schema.create(driver)
        }
        return driver
    }
}

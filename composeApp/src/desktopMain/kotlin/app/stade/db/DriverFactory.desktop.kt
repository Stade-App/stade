package app.stade.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual class DriverFactory {
    actual fun create(): SqlDriver {
        val home = System.getProperty("user.home")
        val dir = File(home, ".stade").apply { mkdirs() }
        val dbFile = File(dir, "stade.db")
        val fresh = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties())
        if (fresh) {
            StadeDb.Schema.create(driver)
        } else {
            runCatching { driver.execute(null, "ALTER TABLE Contact ADD COLUMN addresses TEXT NOT NULL DEFAULT ''", 0) }
        }
        return driver
    }
}

package dev.stade.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File

actual class DriverFactory(private val context: Context) {
    actual fun create(dbFilePath: String): SqlDriver {
        val name = File(dbFilePath).name
        return AndroidSqliteDriver(StadeDb.Schema, context, name)
    }
}

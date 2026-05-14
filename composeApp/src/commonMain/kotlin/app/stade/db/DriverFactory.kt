package app.stade.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun create(dbFilePath: String): SqlDriver
}

fun createDatabase(factory: DriverFactory, dbFilePath: String): StadeDb =
    StadeDb(factory.create(dbFilePath))

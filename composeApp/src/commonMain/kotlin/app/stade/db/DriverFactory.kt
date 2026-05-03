package app.stade.db

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DriverFactory): StadeDb = StadeDb(factory.create())

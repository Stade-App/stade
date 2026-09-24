package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseSchemaTest {
    @Test
    fun currentSchemaIsAccepted() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            StadeDb.Schema.create(driver)

            DatabaseSchema.requireCompatible(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun incompatibleSchemaIsRejectedWithoutChangingStoredRows() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE Contact (id TEXT NOT NULL PRIMARY KEY)", 0)
            driver.execute(null, "INSERT INTO Contact(id) VALUES ('contact-1')", 0)

            assertFailsWith<DatabaseSchemaException> {
                DatabaseSchema.requireCompatible(driver)
            }

            val contactId = driver.executeQuery(
                null,
                "SELECT id FROM Contact",
                { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
                0
            ).value
            assertEquals("contact-1", contactId)
        } finally {
            driver.close()
        }
    }
}

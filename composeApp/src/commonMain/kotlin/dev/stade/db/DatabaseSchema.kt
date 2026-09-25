package dev.stade.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

/** Eski veritabanı bu sürümle açılamadığında kullanılır. */
class DatabaseSchemaException : IllegalStateException("The local database needs a compatible update before it can be opened.")

internal object DatabaseSchema {
    /**
     * Servisler başlamadan gereken sütunlara bakar.
     * Hata olursa tabloya veya kayıtlara dokunmaz.
     */
    fun requireCompatible(driver: SqlDriver) {
        val compatible = runCatching {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT mlkemPublicKey, mldsaPublicKey FROM Contact LIMIT 0",
                mapper = { _: SqlCursor -> QueryResult.Value(Unit) },
                parameters = 0
            )
        }.isSuccess
        if (!compatible) throw DatabaseSchemaException()
    }
}

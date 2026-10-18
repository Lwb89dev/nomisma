package com.nomisma.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.nomisma.app.security.DeviceSecrets
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * One-way move from the original plaintext SQLite file to SQLCipher.
 * The plaintext file is deleted only after the sealed copy opens and the
 * row counts match. A failed attempt leaves the original file in place.
 */
internal object PlainLedgerMigrator {
    private const val DATABASE_NAME = "nomisma.db"
    private const val SEALED_NAME = "nomisma-sealed.db"

    fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        SqlCipherLibrary.load()
        val directory = context.getDatabasePath(DATABASE_NAME).parentFile
            ?: error("Database directory is unavailable")
        directory.mkdirs()
        val plain = File(directory, DATABASE_NAME)
        val sealed = File(directory, SEALED_NAME)
        if (!plain.exists() && sealed.exists()) {
            check(sealed.renameTo(plain)) { "Could not restore the sealed ledger" }
            return
        }
        if (!isPlainSqlite(plain)) return
        if (sealed.exists() && !sealed.delete()) error("Could not replace a partial sealed ledger")
        copy(plain, sealed, passphrase)
        check(countsMatch(plain, sealed, passphrase)) { "Sealed ledger does not match the original" }
        deleteSidecars(plain)
        check(plain.delete()) { "Could not remove the plaintext ledger" }
        check(sealed.renameTo(plain)) { "Could not move the sealed ledger into place" }
    }

    private fun copy(plain: File, sealed: File, passphrase: ByteArray) {
        val source = android.database.sqlite.SQLiteDatabase.openDatabase(
            plain.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        val destination = SQLiteDatabase.openOrCreateDatabase(sealed, passphrase, null, null, null)
        try {
            destination.execSQL("PRAGMA foreign_keys = OFF")
            destination.beginTransaction()
            try {
                createObjects(source, destination)
                copyRows(source, destination)
                destination.setTransactionSuccessful()
            } finally {
                destination.endTransaction()
            }
            destination.version = source.version
        } finally {
            destination.close()
            source.close()
        }
    }

    private fun createObjects(
        source: android.database.sqlite.SQLiteDatabase,
        destination: SQLiteDatabase
    ) {
        source.rawQuery(
            """
            SELECT sql FROM sqlite_master
            WHERE sql IS NOT NULL
              AND name NOT LIKE 'sqlite_%'
              AND name != 'android_metadata'
              AND type IN ('table', 'index')
            ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) destination.execSQL(cursor.getString(0))
        }
    }

    private fun copyRows(
        source: android.database.sqlite.SQLiteDatabase,
        destination: SQLiteDatabase
    ) {
        tableNames(source).forEach { table ->
            source.rawQuery("SELECT * FROM ${quote(table)}", null).use { rows ->
                val columns = rows.columnNames
                while (rows.moveToNext()) {
                    val values = ContentValues()
                    columns.forEachIndexed { index, name -> putColumn(values, rows, index, name) }
                    check(destination.insert(table, null, values) != -1L) { "Could not copy $table" }
                }
            }
        }
    }

    private fun putColumn(values: ContentValues, rows: Cursor, index: Int, name: String) {
        when (rows.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> values.putNull(name)
            Cursor.FIELD_TYPE_INTEGER -> values.put(name, rows.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> values.put(name, rows.getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> values.put(name, rows.getBlob(index))
            else -> values.put(name, rows.getString(index))
        }
    }

    private fun countsMatch(plain: File, sealed: File, passphrase: ByteArray): Boolean {
        val source = android.database.sqlite.SQLiteDatabase.openDatabase(
            plain.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        val destination = SQLiteDatabase.openOrCreateDatabase(sealed, passphrase, null, null, null)
        return try {
            source.version >= 1 &&
                source.version == destination.version &&
                tableNames(source).all { table -> count(source, table) == count(destination, table) }
        } finally {
            destination.close()
            source.close()
        }
    }

    private fun tableNames(database: android.database.sqlite.SQLiteDatabase): List<String> {
        val names = mutableListOf<String>()
        database.rawQuery(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names
    }

    private fun count(database: android.database.sqlite.SQLiteDatabase, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM ${quote(table)}", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun count(database: SQLiteDatabase, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM ${quote(table)}", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun quote(name: String): String {
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Unexpected table name" }
        return "\"$name\""
    }

    private fun deleteSidecars(database: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(database.parentFile, database.name + suffix).delete()
        }
    }

    internal fun isPlainSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { input -> check(input.read(header) == 16) }
        return header.contentEquals(PLAIN_HEADER)
    }

    private val PLAIN_HEADER = "SQLite format 3\u0000".encodeToByteArray()
}

internal object SqlCipherLibrary {
    @Volatile private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}

internal fun openEncryptedLedger(context: Context): NomismaDatabase {
    SqlCipherLibrary.load()
    val passphrase = DeviceSecrets.databasePassphrase(context)
    PlainLedgerMigrator.migrateIfNeeded(context, passphrase)
    return NomismaDatabase(context, passphrase)
}

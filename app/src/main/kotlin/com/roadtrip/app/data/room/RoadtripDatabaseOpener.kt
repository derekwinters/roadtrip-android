package com.roadtrip.app.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Opens [RoadtripDatabase] so that unsynced outbox events survive every schema change
 * (docs/spec/01-sync.md "Local schema changes", ANDSYNC-009..011).
 *
 * - A version with a registered [Migration] path upgrades through Room as usual.
 * - A version with no path (older, including pre-release v1, or newer after a downgrade) is
 *   reset *before* Room opens it: `cursors`, `cache` and Room's bookkeeping are dropped so they
 *   are rebuilt from the server, while `outbox` is set aside as `outbox_salvage`. Room then
 *   creates the current schema, and on open every salvaged row is copied into the new
 *   `outbox` before the salvage table is dropped (ANDSYNC-010).
 *
 * Invariant: no code path drops or recreates `outbox` on a version change, and
 * Room's destructive-migration fallback is never used.
 */
object RoadtripDatabaseOpener {

    /**
     * Every registered schema migration. Each version bump adds one here, plus a migration test
     * against the previous version's exported schema in `app/schemas/` (ANDSYNC-011).
     */
    val MIGRATIONS: Array<Migration> = emptyArray()

    private const val TAG = "RoadtripDatabase"
    private const val OUTBOX = "outbox"
    private const val SALVAGE = "outbox_salvage"

    fun builder(
        context: Context,
        name: String,
        migrations: Array<Migration> = MIGRATIONS,
    ): RoomDatabase.Builder<RoadtripDatabase> {
        resetIfNoMigrationPath(context.getDatabasePath(name), RoadtripDatabase.SCHEMA_VERSION, migrations)
        return Room.databaseBuilder(context.applicationContext, RoadtripDatabase::class.java, name)
            .addMigrations(*migrations)
            .addCallback(RestoreSalvagedOutbox)
    }

    private fun hasMigrationPath(from: Int, to: Int, migrations: Array<Migration>): Boolean =
        RoomDatabase.MigrationContainer()
            .apply { addMigrations(*migrations) }
            .findMigrationPath(from, to) != null

    /** Prepares an on-device database Room could not otherwise open without losing the outbox. */
    private fun resetIfNoMigrationPath(file: File, current: Int, migrations: Array<Migration>) {
        if (!file.exists()) return
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val onDisk = db.version
            if (onDisk == 0 || onDisk == current || hasMigrationPath(onDisk, current, migrations)) return
            Log.w(TAG, "No migration path from schema $onDisk to $current: rebuilding caches, keeping outbox")
            db.beginTransaction()
            try {
                val sql = FrameworkSql(db)
                val tables = sql.tableNames()
                if (OUTBOX in tables) {
                    if (SALVAGE in tables) {
                        // An earlier salvage was never restored: merge into it, never drop rows.
                        sql.copyRows(from = OUTBOX, into = SALVAGE)
                        db.execSQL("DROP TABLE `$OUTBOX`")
                    } else {
                        db.execSQL("ALTER TABLE `$OUTBOX` RENAME TO `$SALVAGE`")
                    }
                }
                tables.filter { it != OUTBOX && it != SALVAGE && isDroppable(it) }
                    .forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
                // Version 0 makes Room create the current schema from scratch on open.
                db.version = 0
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun isDroppable(table: String) = table != "android_metadata" && !table.startsWith("sqlite_")

    /** Moves salvaged outbox rows into the freshly created `outbox` (columns both tables share). */
    private object RestoreSalvagedOutbox : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            val sql = SupportSql(db)
            if (SALVAGE !in sql.tableNames()) return
            db.beginTransaction()
            try {
                sql.copyRows(from = SALVAGE, into = OUTBOX)
                db.execSQL("DROP TABLE `$SALVAGE`")
                db.setTransactionSuccessful()
            } catch (e: SQLiteException) {
                // The salvage table is kept (rows retained) and the restore retried on next open.
                Log.e(TAG, "Could not restore salvaged outbox rows; keeping them for the next open", e)
            } finally {
                db.endTransaction()
            }
        }
    }

    /** The two SQLite APIs in play (framework before Room opens, Room's afterwards). */
    private abstract class Sql {
        abstract fun exec(statement: String)
        abstract fun strings(query: String, column: String): List<String>

        fun tableNames(): Set<String> =
            strings("SELECT name FROM sqlite_master WHERE type = 'table'", "name").toSet()

        fun copyRows(from: String, into: String) {
            val target = strings("PRAGMA table_info(`$into`)", "name").toSet()
            val shared = strings("PRAGMA table_info(`$from`)", "name").filter { it in target }
            val columns = shared.joinToString(", ") { "`$it`" }
            exec("INSERT OR IGNORE INTO `$into` ($columns) SELECT $columns FROM `$from`")
        }
    }

    private class FrameworkSql(private val db: SQLiteDatabase) : Sql() {
        override fun exec(statement: String) = db.execSQL(statement)
        override fun strings(query: String, column: String): List<String> =
            db.rawQuery(query, null).use { c ->
                val index = c.getColumnIndexOrThrow(column)
                buildList { while (c.moveToNext()) add(c.getString(index)) }
            }
    }

    private class SupportSql(private val db: SupportSQLiteDatabase) : Sql() {
        override fun exec(statement: String) = db.execSQL(statement)
        override fun strings(query: String, column: String): List<String> =
            db.query(query).use { c ->
                val index = c.getColumnIndexOrThrow(column)
                buildList { while (c.moveToNext()) add(c.getString(index)) }
            }
    }
}

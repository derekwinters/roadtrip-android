package com.roadtrip.app.data.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Local persistence for the core storage ports (docs/spec/01-sync.md): the outbox of
 * locally-created events, sync cursors, and a generic key->JSON cache for read models.
 * Schemas are intentionally simple; exportSchema is off (no migrations promised pre-1.0).
 */

@Entity(tableName = "outbox")
data class OutboxEventRow(
    @PrimaryKey val eventId: String,
    val type: String,
    /** ISO-8601 instant string; ordering is re-derived in memory by the sync engine. */
    val clientTs: String,
    /** JSON-encoded payload object. */
    val payload: String,
    /** Quarantined rows are out of the retry loop but retained with their reason (ANDSYNC-004). */
    val quarantined: Boolean,
    val reason: String?,
    /** Attribution override: pings upload under the enabling parent's id (ANDLOC-008). */
    val actorProfileId: String?,
)

@Entity(tableName = "cursors")
data class CursorRow(
    @PrimaryKey val cursorKey: String,
    val cursorValue: Long,
)

@Entity(tableName = "cache")
data class CacheRow(
    @PrimaryKey val cacheKey: String,
    val json: String,
    /** ISO-8601 instant of when the value was stored (freshness labels, ANDMAP-004). */
    val storedAt: String,
)

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(row: OutboxEventRow)

    @Query("SELECT * FROM outbox WHERE quarantined = 0")
    fun pending(): List<OutboxEventRow>

    @Query("DELETE FROM outbox WHERE eventId IN (:eventIds) AND quarantined = 0")
    fun remove(eventIds: List<String>)

    @Query("UPDATE outbox SET quarantined = 1, reason = :reason WHERE eventId = :eventId")
    fun quarantine(eventId: String, reason: String)

    @Query("SELECT * FROM outbox WHERE quarantined = 1")
    fun quarantined(): List<OutboxEventRow>
}

@Dao
interface CursorDao {
    @Query("SELECT cursorValue FROM cursors WHERE cursorKey = :key")
    fun get(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(row: CursorRow)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE cacheKey = :key")
    fun get(key: String): CacheRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(row: CacheRow)
}

@Database(
    entities = [OutboxEventRow::class, CursorRow::class, CacheRow::class],
    version = 2,
    exportSchema = false,
)
abstract class RoadtripDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun cursorDao(): CursorDao
    abstract fun cacheDao(): CacheDao
}

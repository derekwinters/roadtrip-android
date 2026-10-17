package com.roadtrip.app.data.room

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.TrackPoint
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.sync.OutboxEntry
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema-change safety of the on-device database (docs/spec/01-sync.md "Local schema changes").
 * covers: ANDSYNC-009 (an upgrade preserves pending and quarantined outbox rows),
 * covers: ANDSYNC-010 (no migration path: cursors/cache cleared, outbox retained),
 * covers: ANDSYNC-011 (schema exported and the current version's JSON committed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoadtripDatabaseOpenerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "opener-test.db"
    private val opened = mutableListOf<RoadtripDatabase>()

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(name)
    }

    private fun open(migrations: Array<Migration> = RoadtripDatabaseOpener.MIGRATIONS): RoadtripDatabase =
        RoadtripDatabaseOpener.builder(context, name, migrations)
            .allowMainThreadQueries()
            .build()
            .also { opened += it }

    private fun currentVersion(): Int {
        val db = open()
        return db.openHelper.readableDatabase.version.also { db.close() }
    }

    private val pending = OutboxEntry(
        eventId = "event-pending",
        type = OutboxEntry.TYPE_LOCATION_PING,
        clientTs = Instant.parse("2026-07-18T12:00:00Z"),
        payload = buildJsonObject {
            put("lat", 39.7392)
            put("lon", -104.9903)
        },
        actorProfileId = "p-parent",
    )

    private val bad = OutboxEntry(
        eventId = "event-bad",
        type = OutboxEntry.TYPE_JOURNAL_POST,
        clientTs = Instant.parse("2026-07-18T12:01:00Z"),
        payload = buildJsonObject { put("text", "rejected by the server") },
    )

    private val mapState = MapState(
        current = TrackPoint(44.5, -110.3, "2026-07-18T12:00:00Z"),
        breadcrumb = listOf(TrackPoint(44.0, -110.0)),
        legMiles = 12.5,
    )

    /** Seeds a current-schema database with outbox (pending + quarantined), a cursor and a cache row. */
    private fun seedCurrentSchema() {
        val db = open()
        val outbox = RoomOutboxStore(db.outboxDao())
        outbox.add(pending)
        outbox.add(bad)
        outbox.quarantine(bad.eventId, "unknown_event_type")
        RoomCursorStore(db.cursorDao()).set(CursorStore.EVENTS, 42L)
        RoomCacheStore(db.cacheDao(), "map", MapState.serializer())
            .write(mapState, Instant.parse("2026-07-18T12:34:00Z"))
        db.close()
    }

    /** Rewrites the on-disk user_version, as an older or newer app build would have left it. */
    private fun setOnDiskVersion(version: Int) {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { it.version = version }
    }

    private fun assertOutboxIntact(db: RoadtripDatabase) {
        val outbox = RoomOutboxStore(db.outboxDao())
        assertEquals(listOf(pending), outbox.pending())
        val quarantined = outbox.quarantined().single()
        assertEquals(bad, quarantined.entry)
        assertEquals("unknown_event_type", quarantined.reason)
    }

    // ---- ANDSYNC-009 -------------------------------------------------------------------------

    @Test
    fun reopeningAtTheSameVersionKeepsEveryTable() {
        seedCurrentSchema()

        val db = open()

        assertOutboxIntact(db)
        assertEquals(42L, RoomCursorStore(db.cursorDao()).get(CursorStore.EVENTS))
    }

    @Test
    fun registeredMigrationUpgradePreservesPendingAndQuarantinedOutboxRows() {
        seedCurrentSchema()
        val current = currentVersion()
        setOnDiskVersion(current - 1)
        var migrated = false
        val upgrade = object : Migration(current - 1, current) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrated = true
            }
        }

        val db = open(arrayOf(upgrade))

        assertOutboxIntact(db)
        assertTrue(migrated, "the registered migration ran")
        // A real migration path is not a reset: rebuildable tables are left alone too.
        assertEquals(42L, RoomCursorStore(db.cursorDao()).get(CursorStore.EVENTS))
    }

    // ---- ANDSYNC-010 -------------------------------------------------------------------------

    private fun assertRebuildableTablesCleared(db: RoadtripDatabase) {
        assertEquals(0L, RoomCursorStore(db.cursorDao()).get(CursorStore.EVENTS))
        assertNull(RoomCacheStore(db.cacheDao(), "map", MapState.serializer()).read())
    }

    @Test
    fun olderVersionWithNoMigrationPathClearsCursorsAndCacheButKeepsOutbox() {
        seedCurrentSchema()
        setOnDiskVersion(currentVersion() - 1)

        val db = open()

        assertRebuildableTablesCleared(db)
        assertOutboxIntact(db)
    }

    @Test
    fun newerOnDeviceVersionDowngradeClearsCursorsAndCacheButKeepsOutbox() {
        seedCurrentSchema()
        setOnDiskVersion(currentVersion() + 1)

        val db = open()

        assertRebuildableTablesCleared(db)
        assertOutboxIntact(db)
    }

    @Test
    fun preReleaseV1DatabaseKeepsItsOutboxRowsInTheCurrentSchema() {
        // The pre-release v1 layout: outbox had no actorProfileId column yet.
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `outbox` (`eventId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                    "`clientTs` TEXT NOT NULL, `payload` TEXT NOT NULL, `quarantined` INTEGER NOT NULL, " +
                    "`reason` TEXT, PRIMARY KEY(`eventId`))",
            )
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `cursors` (`cursorKey` TEXT NOT NULL, `cursorValue` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`cursorKey`))",
            )
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS `cache` (`cacheKey` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                    "`storedAt` TEXT NOT NULL, PRIMARY KEY(`cacheKey`))",
            )
            raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-hash')")
            raw.execSQL(
                "INSERT INTO outbox VALUES ('v1-pending', '${OutboxEntry.TYPE_JOURNAL_POST}', " +
                    "'2026-07-01T10:00:00Z', '{\"text\":\"from v1\"}', 0, NULL)",
            )
            raw.execSQL(
                "INSERT INTO outbox VALUES ('v1-bad', '${OutboxEntry.TYPE_JOURNAL_POST}', " +
                    "'2026-07-01T10:01:00Z', '{\"text\":\"bad\"}', 1, 'unknown_event_type')",
            )
            raw.execSQL("INSERT INTO cursors VALUES ('${CursorStore.EVENTS}', 7)")
            raw.execSQL("INSERT INTO cache VALUES ('map', '{}', '2026-07-01T10:00:00Z')")
            raw.version = 1
        }

        val db = open()

        val outbox = RoomOutboxStore(db.outboxDao())
        val kept = outbox.pending().single()
        assertEquals("v1-pending", kept.eventId)
        assertEquals(Instant.parse("2026-07-01T10:00:00Z"), kept.clientTs)
        assertNull(kept.actorProfileId)
        val quarantined = outbox.quarantined().single()
        assertEquals("v1-bad", quarantined.entry.eventId)
        assertEquals("unknown_event_type", quarantined.reason)
        assertRebuildableTablesCleared(db)
    }

    @Test
    fun invariantNoDestructiveFallbackAnywhereInAppSources() {
        // Split so this file itself stays clean for `grep -rn <name> app/` (acceptance check).
        val forbidden = "fallbackToDestructive" + "Migration"
        val sources = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no app sources found from ${File(".").absolutePath}")
        val offenders = sources.filter { it.readText().contains(forbidden) }.map { it.path }
        assertTrue(offenders.isEmpty(), "$forbidden used in $offenders")
    }

    // ---- ANDSYNC-011 -------------------------------------------------------------------------

    @Test
    fun committedSchemaJsonMatchesCurrentDatabaseVersionAndIdentity() {
        val db = open()
        val version = db.openHelper.readableDatabase.version
        val identity = db.openHelper.readableDatabase
            .query("SELECT identity_hash FROM room_master_table WHERE id = 42")
            .use { c -> assertTrue(c.moveToFirst()); c.getString(0) }

        val schema = File("schemas/${RoadtripDatabase::class.java.name}/$version.json")
        assertTrue(schema.isFile, "missing committed schema ${schema.path}")
        val database = Json.parseToJsonElement(schema.readText()).jsonObject["database"]?.jsonObject
        assertNotNull(database)
        assertEquals(version, database["version"]?.jsonPrimitive?.int)
        assertEquals(identity, database["identityHash"]?.jsonPrimitive?.content)
        assertFalse(identity.isNullOrBlank())
    }
}

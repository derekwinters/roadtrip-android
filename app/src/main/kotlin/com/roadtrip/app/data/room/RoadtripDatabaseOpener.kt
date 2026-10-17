package com.roadtrip.app.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** Stub reproducing today's behavior (red step); replaced by the real opener. */
object RoadtripDatabaseOpener {
    val MIGRATIONS: Array<Migration> = emptyArray()

    fun builder(
        context: Context,
        name: String,
        migrations: Array<Migration> = MIGRATIONS,
    ): RoomDatabase.Builder<RoadtripDatabase> =
        Room.databaseBuilder(context.applicationContext, RoadtripDatabase::class.java, name)
            .addMigrations(*migrations)
            .fallbackToDestructiveMigration()
}

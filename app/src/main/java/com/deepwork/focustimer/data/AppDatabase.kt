package com.deepwork.focustimer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FocusSession::class, ImportBatch::class, TimerPreset::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun timerPresetDao(): TimerPresetDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: tag existing sessions as TIMER-origin, add the import link,
         * and create the new batch/preset tables. Done explicitly so existing
         * recorded sessions survive the upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE focus_sessions ADD COLUMN origin TEXT NOT NULL DEFAULT 'TIMER'"
                )
                db.execSQL(
                    "ALTER TABLE focus_sessions ADD COLUMN importBatchId INTEGER"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_batches (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        fileName TEXT NOT NULL,
                        format TEXT NOT NULL,
                        importedAtEpochMillis INTEGER NOT NULL,
                        sessionCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS timer_presets (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        focusMinutes INTEGER NOT NULL,
                        breakMinutes INTEGER NOT NULL,
                        rounds INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_timer.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}

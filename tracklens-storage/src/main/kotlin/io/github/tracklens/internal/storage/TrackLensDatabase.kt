package io.github.tracklens.internal.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.tracklens.InternalTrackLensApi
import java.io.File

/**
 * TrackLens' Room database, schema v1 (plan section 3). The file lives in `noBackupFilesDir`, so it is never part of
 * Auto Backup or device transfer, whatever the host's backup rules say. Before 1.0 a schema change drops the data
 * (it is debug-only and disposable); from 1.0 every version ships a migration.
 */
@InternalTrackLensApi
@Database(
    entities = [
        SessionEntity::class,
        EventEntity::class,
        EventParamEntity::class,
        ScreenVisitEntity::class,
        WarningEntity::class,
        DiagnosticEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
public abstract class TrackLensDatabase : RoomDatabase() {
    /** Sessions. */
    public abstract fun sessions(): SessionDao

    /** Batch writes. */
    public abstract fun writes(): WriteDao

    /** Dashboard chart queries. */
    public abstract fun dashboard(): DashboardDao

    /** Warnings, funnels and comparison. */
    public abstract fun findings(): FindingsDao

    /** Event list, detail and search. */
    public abstract fun eventList(): EventListDao

    /** Creating the database, and settings the tests check. */
    public companion object {
        /** File name inside `noBackupFilesDir`. */
        public const val FILE_NAME: String = "tracklens.db"

        /** SQLite's `PRAGMA auto_vacuum` value for INCREMENTAL. */
        public const val AUTO_VACUUM_INCREMENTAL: Int = 2

        /** `PRAGMA auto_vacuum` as stored in the file, read on the primary connection. */
        public fun autoVacuumMode(db: SupportSQLiteDatabase): Int {
            db.beginTransaction()
            try {
                return db.query("PRAGMA auto_vacuum").use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
            } finally {
                db.endTransaction()
            }
        }

        /** The database file: `noBackupFilesDir/tracklens.db`. */
        public fun file(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)

        /** Opens (creating if needed) the database file. Never call on the main thread: the first open does I/O. */
        public fun open(context: Context): TrackLensDatabase =
            Room
                .databaseBuilder(context.applicationContext, TrackLensDatabase::class.java, file(context).absolutePath)
                .configure()
                .build()

        /** An in-memory database, for tests. */
        public fun inMemory(context: Context): TrackLensDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, TrackLensDatabase::class.java).configure().build()

        private fun RoomDatabase.Builder<TrackLensDatabase>.configure(): RoomDatabase.Builder<TrackLensDatabase> =
            fallbackToDestructiveMigration(dropAllTables = true).addCallback(Setup)
    }

    /**
     * Turns on incremental vacuum (so retention can return space without a full VACUUM) and foreign keys (deleting a
     * session deletes everything in it).
     *
     * SQLite applies a new `auto_vacuum` mode only after a VACUUM, which Room's table creation (a transaction) does not
     * allow, so it happens here, once: on first open the database is still empty and the VACUUM is cheap. The current
     * mode is read inside a transaction because in WAL mode reads outside one go to a pooled read connection, which
     * can report the mode from before the change; misreading it would rewrite the whole database on every open.
     */
    private object Setup : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
            if (autoVacuumMode(db) != AUTO_VACUUM_INCREMENTAL) {
                db.execSQL("PRAGMA auto_vacuum = INCREMENTAL")
                db.execSQL("VACUUM")
            }
        }
    }
}

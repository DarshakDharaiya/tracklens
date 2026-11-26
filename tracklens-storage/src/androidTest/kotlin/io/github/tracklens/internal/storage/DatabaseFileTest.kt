package io.github.tracklens.internal.storage

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real database file: where it lives and the SQLite settings it is created with (plan sections 3.1 and 3.5). */
@RunWith(AndroidJUnit4::class)
class DatabaseFileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteFile() {
        context.deleteDatabase(TrackLensDatabase.file(context).absolutePath)
    }

    @Test
    fun livesInNoBackupFilesWithIncrementalVacuumAndForeignKeys() {
        val db = TrackLensDatabase.open(context)
        val sqlite = db.openHelper.writableDatabase

        try {
            val file = TrackLensDatabase.file(context)
            assertTrue("created at $file", file.exists())
            assertEquals(context.noBackupFilesDir, file.parentFile)
            assertEquals(TrackLensDatabase.AUTO_VACUUM_INCREMENTAL, TrackLensDatabase.autoVacuumMode(sqlite))
            // Per connection; what matters is the primary (write) connection, where deletes cascade.
            sqlite.beginTransaction()
            try {
                assertEquals(1, pragma(sqlite, "foreign_keys"))
            } finally {
                sqlite.endTransaction()
            }
            assertEquals(1, pragma(sqlite, "user_version"))
        } finally {
            db.close()
        }

        // Stored in the file itself: a plain, separate connection sees it too.
        val plain = SQLiteDatabase.openDatabase(TrackLensDatabase.file(context).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(
                TrackLensDatabase.AUTO_VACUUM_INCREMENTAL,
                plain.rawQuery("PRAGMA auto_vacuum", null).use {
                    it.moveToFirst()
                    it.getInt(0)
                },
            )
        } finally {
            plain.close()
        }
    }

    private fun pragma(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        name: String,
    ): Int = db.query("PRAGMA $name").use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
}

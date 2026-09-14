package com.quransunah.app.data.local.user

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    private val databaseName = "user-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFrom1To2PreservesBookmarksAndCreatesMemorizationTable() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO bookmarks(
                    id, surah, ayah, page_number, word_id, word_index, ayah_text, saved_at
                ) VALUES ('2:255', 2, 255, 42, 2551, 1, 'اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ', 1234)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            UserDatabaseMigrations.MIGRATION_1_2,
        ).apply {
            query("SELECT COUNT(*) FROM bookmarks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            execSQL(
                """
                INSERT INTO memorization_progress(
                    id, surah, ayah, page_number, ayah_text, state,
                    review_count, created_at, updated_at
                ) VALUES ('2:255', 2, 255, 42, 'اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ', 'LEARNING', 0, 1234, 1234)
                """.trimIndent(),
            )
            query("SELECT state, review_count FROM memorization_progress WHERE id = '2:255'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("LEARNING", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            close()
        }
    }
}

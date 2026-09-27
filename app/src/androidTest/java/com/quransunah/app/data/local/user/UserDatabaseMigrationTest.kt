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

    @Test
    fun migrationFrom2To3CreatesPlansAndSessionsTables() {
        helper.createDatabase("user-migration-test-v2", 2).close()
        helper.runMigrationsAndValidate(
            "user-migration-test-v2",
            3,
            true,
            UserDatabaseMigrations.MIGRATION_2_3,
        ).apply {
            execSQL(
                "INSERT INTO memorization_plans(id, name, start_surah, start_ayah, end_surah, end_ayah, daily_target, created_at, updated_at) " +
                    "VALUES ('plan:1:1-1:7', 'الفاتحة', 1, 1, 1, 7, 2, 1, 1)",
            )
            execSQL(
                "INSERT INTO memorization_sessions(id, plan_id, started_at, finished_at, reviewed_count, mastered_count) " +
                    "VALUES ('session-1', 'plan:1:1-1:7', 1, NULL, 0, 0)",
            )
            query("SELECT COUNT(*) FROM memorization_plans").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrationFrom3To4CreatesRecordingsTable() {
        helper.createDatabase("user-migration-test-v3", 3).close()
        helper.runMigrationsAndValidate(
            "user-migration-test-v3",
            4,
            true,
            UserDatabaseMigrations.MIGRATION_3_4,
        ).apply {
            execSQL(
                "INSERT INTO memorization_recordings(id, session_id, file_path, created_at, duration_ms) " +
                    "VALUES ('recording-1', 'plan:1:1-1:7:session:1', '/cache/recording.m4a', 1, 1200)",
            )
            query("SELECT COUNT(*) FROM memorization_recordings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }
}

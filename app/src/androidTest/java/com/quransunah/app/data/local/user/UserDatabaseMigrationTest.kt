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

    @Test
    fun migrationFrom6To5KeepsUserRowsAndTrainingLog() {
        val name = "user-migration-test-v6"
        helper.createDatabase(name, 5).apply {
            execSQL(
                """
                INSERT INTO bookmarks(
                    id, surah, ayah, page_number, word_id, word_index, ayah_text, saved_at
                ) VALUES ('1:1', 1, 1, 1, 1, 0, 'بِسْمِ اللَّهِ', 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO memorization_progress(
                    id, surah, ayah, page_number, ayah_text, state,
                    review_count, created_at, updated_at
                ) VALUES ('1:1', 1, 1, 1, 'بِسْمِ اللَّهِ', 'MASTERED', 2, 10, 11)
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_log` (
                    `id` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `started_at` INTEGER NOT NULL,
                    `surah` INTEGER NOT NULL,
                    `start_ayah` INTEGER NOT NULL,
                    `end_ayah` INTEGER NOT NULL,
                    `last_ayah` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `practiced_ayahs` TEXT NOT NULL,
                    `assessments` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO training_log(
                    id, mode, started_at, surah, start_ayah, end_ayah, last_ayah,
                    status, practiced_ayahs, assessments, updated_at
                ) VALUES (
                    'session-1', 'NEW', 20, 1, 1, 7, 7, 'ACTIVE', '[1]', '[]', 21
                )
                """.trimIndent(),
            )
            execSQL("PRAGMA user_version = 6")
            close()
        }

        helper.runMigrationsAndValidate(
            name,
            5,
            false,
            UserDatabaseMigrations.MIGRATION_6_5,
        ).apply {
            query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(5, cursor.getInt(0))
            }
            query("SELECT surah, state FROM memorization_progress WHERE id = '1:1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("MASTERED", cursor.getString(1))
            }
            query("SELECT surah, status, last_ayah FROM training_log WHERE id = 'session-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("ACTIVE", cursor.getString(1))
                assertEquals(7, cursor.getInt(2))
            }
            close()
        }
    }

    @Test
    fun migrationFrom5To7KeepsMemorizationAndAddsTrainingTables() {
        val name = "user-migration-test-v5-training"
        helper.createDatabase(name, 5).apply {
            execSQL(
                """
                INSERT INTO memorization_progress(
                    id, surah, ayah, page_number, ayah_text, state,
                    review_count, created_at, updated_at
                ) VALUES ('2:255', 2, 255, 42, 'آية الكرسي', 'MASTERED', 1, 10, 11)
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(
            name,
            7,
            true,
            UserDatabaseMigrations.MIGRATION_5_7,
        ).apply {
            query("SELECT state, page_number FROM memorization_progress WHERE id = '2:255'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("MASTERED", cursor.getString(0))
                assertEquals(42, cursor.getInt(1))
            }
            execSQL(
                "INSERT INTO training_position(id, surah, ayah, page_number, updated_at) VALUES (1, 2, 255, 42, 12)",
            )
            close()
        }
    }

    @Test
    fun migrationFromLegacyVersion6KeepsTrainingHistory() {
        val name = "user-migration-test-legacy-log-v7"
        helper.createDatabase(name, 5).apply {
            execSQL(
                """
                INSERT INTO memorization_progress(
                    id, surah, ayah, page_number, ayah_text, state,
                    review_count, created_at, updated_at
                ) VALUES ('1:1', 1, 1, 1, 'بِسْمِ اللَّهِ', 'LEARNING', 0, 10, 11)
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_log` (
                    `id` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `started_at` INTEGER NOT NULL,
                    `surah` INTEGER NOT NULL,
                    `start_ayah` INTEGER NOT NULL,
                    `end_ayah` INTEGER NOT NULL,
                    `last_ayah` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `practiced_ayahs` TEXT NOT NULL,
                    `assessments` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO training_log(
                    id, mode, started_at, surah, start_ayah, end_ayah, last_ayah,
                    status, practiced_ayahs, assessments, updated_at
                ) VALUES (
                    'session-1', 'NEW', 20, 1, 1, 7, 7, 'ACTIVE', '[1]', '[]', 21
                )
                """.trimIndent(),
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `index_training_log_surah_updated_at` ON `training_log` (`surah`, `updated_at`)",
            )
            execSQL("PRAGMA user_version = 6")
            close()
        }
        helper.runMigrationsAndValidate(
            name,
            7,
            true,
            UserDatabaseMigrations.MIGRATION_6_7,
        ).apply {
            query("SELECT surah, status, last_ayah, practiced_ayahs, assessments FROM training_log WHERE id = 'session-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("ACTIVE", cursor.getString(1))
                assertEquals(7, cursor.getInt(2))
                assertEquals("[1]", cursor.getString(3))
                assertEquals("[]", cursor.getString(4))
            }
            query("SELECT end_surah, last_surah, page_number FROM training_log WHERE id = 'session-1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
            }
            query("SELECT state FROM memorization_progress WHERE id = '1:1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("LEARNING", cursor.getString(0))
            }
            close()
        }
    }
}

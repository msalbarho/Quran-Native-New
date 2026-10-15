package com.quransunah.app.data.local.user

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object UserDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memorization_progress` (
                    `id` TEXT NOT NULL,
                    `surah` INTEGER NOT NULL,
                    `ayah` INTEGER NOT NULL,
                    `page_number` INTEGER NOT NULL,
                    `ayah_text` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `review_count` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_memorization_progress_surah_ayah` " +
                    "ON `memorization_progress` (`surah`, `ayah`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memorization_progress_state_updated_at` " +
                    "ON `memorization_progress` (`state`, `updated_at`)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memorization_plans` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `start_surah` INTEGER NOT NULL,
                    `start_ayah` INTEGER NOT NULL,
                    `end_surah` INTEGER NOT NULL,
                    `end_ayah` INTEGER NOT NULL,
                    `daily_target` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memorization_plans_updated_at` ON `memorization_plans` (`updated_at`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memorization_sessions` (
                    `id` TEXT NOT NULL,
                    `plan_id` TEXT NOT NULL,
                    `started_at` INTEGER NOT NULL,
                    `finished_at` INTEGER,
                    `reviewed_count` INTEGER NOT NULL,
                    `mastered_count` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memorization_sessions_plan_id_started_at` ON `memorization_sessions` (`plan_id`, `started_at`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memorization_recordings` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `file_path` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `duration_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memorization_recordings_session_id_created_at` ON `memorization_recordings` (`session_id`, `created_at`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recitation_attempts` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `recording_id` TEXT,
                    `score_percent` INTEGER NOT NULL,
                    `missing_count` INTEGER NOT NULL,
                    `extra_count` INTEGER NOT NULL,
                    `different_count` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recitation_attempts_session_id_created_at` ON `recitation_attempts` (`session_id`, `created_at`)")
        }
    }

    /**
     * Some installed databases are already at version 6 because a previous build
     * added `training_log`. This schema is version 5, so Room otherwise refuses
     * to open them. Leave every table and row in place, including `training_log`.
     */
    val MIGRATION_6_5 = object : Migration(6, 5) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    /**
     * Adds the resume row and brings `training_log` up to the current columns.
     * Existing rows, including a legacy log left behind by version 6, stay in place.
     */
    val MIGRATION_5_7 = object : Migration(5, 7) {
        override fun migrate(db: SupportSQLiteDatabase) = ensureTrainingStorage(db)
    }

    /** Databases that are still on the old version-6 log get the same non-destructive upgrade. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) = ensureTrainingStorage(db)
    }

    fun ensureTrainingStorage(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `training_position` (
                `id` INTEGER NOT NULL,
                `surah` INTEGER NOT NULL,
                `ayah` INTEGER NOT NULL,
                `page_number` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        if (!tableExists(db, "training_log")) {
            db.execSQL(
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
                    `page_number` INTEGER NOT NULL DEFAULT 0,
                    `end_surah` INTEGER NOT NULL DEFAULT 0,
                    `last_surah` INTEGER NOT NULL DEFAULT 0,
                    `ended_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        } else {
            if (!columnExists(db, "training_log", "page_number")) {
                db.execSQL("ALTER TABLE `training_log` ADD COLUMN `page_number` INTEGER NOT NULL DEFAULT 0")
            }
            if (!columnExists(db, "training_log", "end_surah")) {
                db.execSQL("ALTER TABLE `training_log` ADD COLUMN `end_surah` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `training_log` SET `end_surah` = `surah`")
            }
            if (!columnExists(db, "training_log", "last_surah")) {
                db.execSQL("ALTER TABLE `training_log` ADD COLUMN `last_surah` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `training_log` SET `last_surah` = `surah`")
            }
            if (!columnExists(db, "training_log", "ended_at")) {
                db.execSQL("ALTER TABLE `training_log` ADD COLUMN `ended_at` INTEGER")
            }
        }
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_training_log_started_at` ON `training_log` (`started_at`)",
        )
        // Legacy v6 logs also had this index. Room rejects any index the entity does not declare.
        db.execSQL("DROP INDEX IF EXISTS `index_training_log_surah_updated_at`")
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(name) == column) return@use true
            }
            false
        }
}

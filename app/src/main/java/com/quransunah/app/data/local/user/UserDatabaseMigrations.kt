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
}

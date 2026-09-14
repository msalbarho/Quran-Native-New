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
}

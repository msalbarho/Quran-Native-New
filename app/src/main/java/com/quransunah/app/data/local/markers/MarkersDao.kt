package com.quransunah.app.data.local.markers

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.quransunah.app.data.local.markers.entity.HizbMarkerEntity
import com.quransunah.app.data.local.markers.entity.JuzMarkerEntity
import com.quransunah.app.data.local.markers.entity.RubEntity
import com.quransunah.app.data.local.markers.entity.SajdaEntity

@Dao
interface MarkersDao {
    @SkipQueryVerification
    @Query(
        """
        SELECT
            marker_number AS rub_number,
            verse_count AS verses_count,
            start_surah || ':' || start_ayah AS first_verse_key,
            end_surah || ':' || end_ayah AS last_verse_key,
            NULL AS verse_mapping,
            marker_hizb_number AS hizb_number,
            NULL AS quarter_type_label,
            marker_label AS label
        FROM divisions
        WHERE marker_number IS NOT NULL
        ORDER BY marker_number
        """,
    )
    suspend fun getRubMarkers(): List<RubEntity>

    @SkipQueryVerification
    @Query("SELECT sajdah_number, verse_key, sajdah_type FROM sajdah ORDER BY sajdah_number")
    suspend fun getSajdaMarkers(): List<SajdaEntity>

    @SkipQueryVerification
    @Query("SELECT juz_number, verses_count, first_verse_key, last_verse_key, verse_mapping FROM marker_juz ORDER BY juz_number")
    suspend fun getJuzMarkers(): List<JuzMarkerEntity>

    @SkipQueryVerification
    @Query("SELECT hizb_number, verses_count, first_verse_key, last_verse_key, verse_mapping FROM marker_hizb ORDER BY hizb_number")
    suspend fun getHizbMarkers(): List<HizbMarkerEntity>
}

package com.quransunah.app.data.local.mushaf

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.quransunah.app.data.local.mushaf.entity.DivisionEntity
import com.quransunah.app.data.local.mushaf.entity.MushafEditionEntity
import com.quransunah.app.data.local.mushaf.entity.SurahEntity
import com.quransunah.app.data.local.mushaf.entity.WordEntity

@Dao
interface MushafDao {
    @Query("SELECT id, name, number_of_pages, lines_per_page, font_name FROM mushaf_editions LIMIT 1")
    suspend fun getEdition(): MushafEditionEntity?

    @Query("SELECT COUNT(*) FROM words")
    suspend fun wordCount(): Int

    @Query("SELECT id, name_arabic, number_of_ayahs, revelation_type FROM surahs ORDER BY id")
    suspend fun getSurahs(): List<SurahEntity>

    @Query("SELECT id, name_arabic, number_of_ayahs, revelation_type FROM surahs WHERE id = :surahId")
    suspend fun getSurah(surahId: Int): SurahEntity?

    @Query(
        """
        SELECT
            id,
            division_type,
            division_number,
            name,
            page_id,
            start_surah,
            start_ayah,
            division_type_id,
            end_surah,
            end_ayah,
            quarter_number,
            quarter_type_label,
            end_page_number,
            ayah_id,
            mark,
            hex_str,
            parent_id
        FROM divisions
        WHERE division_type IN ('juz', 'hizb')
           OR division_type_id IN (1, 2)
        ORDER BY id
        """,
    )
    suspend fun getDivisions(): List<DivisionEntity>

    @Query(
        """
        SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
        FROM words
        WHERE surah_id = :surahId AND ayah_number = :ayahNumber
        ORDER BY word_position
        """,
    )
    suspend fun getAyahWords(surahId: Int, ayahNumber: Int): List<WordEntity>

    @Query(
        """
        SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
        FROM words
        WHERE surah_id = 1 AND ayah_number = 1 AND word_position < 5
        ORDER BY word_position
        """,
    )
    suspend fun getBasmallahWords(): List<WordEntity>

    @Query(
        """
        SELECT
            line_number AS lineNumber,
            line_type AS lineType,
            is_centered AS isCentered,
            surah_id AS surahId,
            first_word_index AS firstWordIndex,
            last_word_index AS lastWordIndex
        FROM mushaf_pages
        WHERE page_number = :pageNumber
        ORDER BY line_number
        """,
    )
    suspend fun getPageStructure(pageNumber: Int): List<PageStructureRow>

    @Query(
        """
        SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
        FROM words
        WHERE word_id BETWEEN :firstWordId AND :lastWordId
        ORDER BY word_id
        """,
    )
    suspend fun getWordsInRange(firstWordId: Int, lastWordId: Int): List<WordEntity>

    @Query(
        """
        SELECT line_number, line_type, is_centered, surah_id
        FROM mushaf_pages
        WHERE page_number = :pageNumber
          AND line_type IN ('surah_name', 'basmallah')
        ORDER BY line_number
        """,
    )
    suspend fun getDecorativeLines(pageNumber: Int): List<DecorativeLineRow>

    @Query(
        """
        SELECT surah_id, MIN(page_number) AS startPage
        FROM mushaf_pages
        WHERE line_type = 'surah_name' AND surah_id IS NOT NULL
        GROUP BY surah_id
        """,
    )
    suspend fun getSurahStartPages(): List<SurahStartPageRow>

    @Query(
        """
        SELECT surah_id
        FROM mushaf_pages
        WHERE line_type = 'surah_name' AND surah_id IS NOT NULL AND page_number <= :pageNumber
        ORDER BY page_number DESC
        LIMIT 1
        """,
    )
    suspend fun getSurahForPage(pageNumber: Int): Int?

    @Query(
        """
        SELECT surah_id
        FROM mushaf_pages
        WHERE page_number = :pageNumber
          AND line_type = 'surah_name'
          AND surah_id IS NOT NULL
        ORDER BY line_number
        """,
    )
    suspend fun getSurahsOnPage(pageNumber: Int): List<Int>

    @Query(
        """
        SELECT MIN(mp.page_number) AS page_number
        FROM mushaf_pages mp
        JOIN words w
          ON mp.line_type = 'ayah'
         AND mp.first_word_index IS NOT NULL
         AND mp.last_word_index IS NOT NULL
         AND w.word_id BETWEEN mp.first_word_index AND mp.last_word_index
        WHERE w.surah_id = :surahId AND w.ayah_number = :ayahNumber
        """,
    )
    suspend fun getPageForAyah(surahId: Int, ayahNumber: Int): Int?

    @SkipQueryVerification
    @Query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='page_meta' LIMIT 1")
    suspend fun hasPageMetaTable(): Int?

    @SkipQueryVerification
    @Query(
        """
        SELECT page_number AS pageNumber, juz_number AS juzNumber, hizb_number AS hizbNumber
        FROM page_meta
        WHERE page_number = :pageNumber
        """,
    )
    suspend fun getPackagedPageMeta(pageNumber: Int): PackagedPageMetaRow?

    @SkipQueryVerification
    @Query(
        """
        SELECT global_ayah_index AS globalAyahIndex, surah_id AS surahId, ayah_number AS ayahNumber
        FROM ayahs
        """,
    )
    suspend fun getAyahIndex(): List<AyahIndexRow>

    @Query("SELECT ayah_id AS ayahId, word, text FROM meanings")
    suspend fun getMeaningPhrases(): List<MeaningPhraseRow>

    @Query(
        """
        SELECT word_id AS wordId, surah_id AS surahId, ayah_number AS ayahNumber, text_hafs AS textHafs
        FROM words
        """,
    )
    suspend fun getMeaningWordRows(): List<MeaningWordRow>

    @Query(
        """
        SELECT surah_id AS surahId, ayah_number AS ayahNumber,
               word_id AS wordId, text_hafs AS textHafs
        FROM words
        WHERE COALESCE(is_ayah_marker, 0) = 0
        ORDER BY surah_id, ayah_number, word_id
        """,
    )
    suspend fun getSearchWordRows(): List<SearchWordRow>

    @Query(
        """
        SELECT page_number AS pageNumber,
               first_word_index AS firstWordIndex,
               last_word_index AS lastWordIndex
        FROM mushaf_pages
        WHERE line_type = 'ayah'
          AND first_word_index IS NOT NULL
          AND last_word_index IS NOT NULL
        ORDER BY first_word_index
        """,
    )
    suspend fun getAyahLineRanges(): List<AyahLineRangeRow>

    @Query(
        """
        SELECT surah_id AS surahId, ayah_number AS ayahNumber, MIN(word_id) AS firstWordId
        FROM words
        GROUP BY surah_id, ayah_number
        """,
    )
    suspend fun getAyahFirstWordIds(): List<AyahFirstWordRow>
}

data class PageStructureRow(
    val lineNumber: Int,
    val lineType: String,
    val isCentered: Int,
    val surahId: Int?,
    val firstWordIndex: Int?,
    val lastWordIndex: Int?,
)

data class DecorativeLineRow(
    @ColumnInfo(name = "line_number") val lineNumber: Int,
    @ColumnInfo(name = "line_type") val lineType: String,
    @ColumnInfo(name = "is_centered") val isCentered: Int,
    @ColumnInfo(name = "surah_id") val surahId: Int?,
)

data class SurahStartPageRow(
    @ColumnInfo(name = "surah_id") val surahId: Int,
    val startPage: Int,
)

data class MeaningPhraseRow(
    val ayahId: Int,
    val word: String?,
    val text: String?,
)

data class MeaningWordRow(
    val wordId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val textHafs: String?,
)

data class AyahIndexRow(
    val globalAyahIndex: Int,
    val surahId: Int,
    val ayahNumber: Int,
)

data class SearchWordRow(
    val surahId: Int,
    val ayahNumber: Int,
    val wordId: Int,
    val textHafs: String?,
)

data class AyahLineRangeRow(
    val pageNumber: Int,
    val firstWordIndex: Int,
    val lastWordIndex: Int,
)

data class PackagedPageMetaRow(
    val pageNumber: Int,
    val juzNumber: Int,
    val hizbNumber: Int,
)

data class AyahFirstWordRow(
    val surahId: Int,
    val ayahNumber: Int,
    val firstWordId: Int,
)

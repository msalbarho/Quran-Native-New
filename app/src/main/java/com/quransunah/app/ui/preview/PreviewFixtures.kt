package com.quransunah.app.ui.preview

import com.quransunah.app.data.catalog.AyahAudioType
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.domain.model.AyahSearchHit
import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.ui.bookmarks.BookmarkListItem
import com.quransunah.app.ui.bookmarks.BookmarkTarget
import com.quransunah.app.ui.bookmarks.BookmarksUiState
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.index.QuranIndexUiState
import com.quransunah.app.ui.index.derived
import com.quransunah.app.ui.listening.ListeningUiState
import com.quransunah.app.ui.mushaf.LineRecord
import com.quransunah.app.ui.mushaf.WordRecord
import com.quransunah.app.ui.search.SearchUiState
import com.quransunah.app.ui.study.StudyUiState

object PreviewFixtures {
    val reciters = listOf(
        SurahReciter(92, "الشيخ ياسر الدوسري", "ي", emptyList()),
        SurahReciter(5, "الشيخ أحمد العجمي", "ع", emptyList()),
        SurahReciter(123, "الشيخ مشاري العفاسي", "م", emptyList()),
    )

    val ayahReciters = listOf(
        AyahReciter("yasser-al-dosari", "الشيخ ياسر الدوسري", AyahAudioType.EQURAN, "", true),
        AyahReciter("ar.alafasy", "الشيخ مشاري العفاسي", AyahAudioType.ISLAMIC_NETWORK, "", true),
    )

    val surahs = listOf(
        SurahInfo(1, "الفاتحة", 7, "Meccan", 1),
        SurahInfo(2, "البقرة", 286, "Medinan", 2),
        SurahInfo(36, "يس", 83, "Meccan", 440),
        SurahInfo(55, "الرحمن", 78, "Medinan", 531),
        SurahInfo(112, "الإخلاص", 4, "Meccan", 604),
    )

    val indexUi = QuranIndexUiState(
        tab = QuranIndexTab.Surah,
        loaded = true,
        currentSurah = 2,
        currentJuz = 1,
        currentHizb = 1,
        surahs = surahs,
        juz = listOf(
            DivisionInfo(1, "juz", 1, "الجزء ١", 1, 1, 1),
            DivisionInfo(2, "juz", 2, "الجزء ٢", 22, 2, 142),
        ),
        hizbs = listOf(
            DivisionInfo(101, "hizb", 1, "الحزب الأول", 1, 1, 1),
            DivisionInfo(102, "hizb", 2, "الحزب الثاني", 11, 2, 75),
        ),
        quarters = listOf(
            DivisionInfo(201, "quarter", 2, "ربع الحزب الأول", 5, 2, 26, parentNumber = 1),
            DivisionInfo(202, "quarter", 3, "نصف الحزب الأول", 7, 2, 44, parentNumber = 1),
        ),
    ).derived()

    val listeningUi = ListeningUiState(
        reciters = reciters,
        selectedReciter = reciters.first(),
        selectedSurah = 1,
        availableSurahs = listOf(1, 2, 36, 55, 112),
        surahs = surahs,
        downloadFrom = 1,
        downloadTo = 5,
        cachedInRange = 2,
        rangeTotal = 5,
        selectedIsCached = true,
        repeatMode = SurahRepeatMode.OFF,
    )

    val playback = PlaybackSnapshot(
        domain = PlaybackDomain.SURAH,
        isPlaying = true,
        reciterName = "الشيخ ياسر الدوسري",
        surah = 1,
        surahName = "الفاتحة",
        positionMs = 45_000L,
        durationMs = 180_000L,
        fromLocalCache = true,
    )

    val searchUi = SearchUiState(
        query = "الله",
        results = listOf(
            AyahSearchHit(
                surah = 2,
                ayah = 255,
                pageNumber = 42,
                juzNumber = 3,
                text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
                surahName = "البقرة",
                snippet = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
                highlightStart = 0,
                highlightEnd = 6,
            ),
            AyahSearchHit(
                surah = 112,
                ayah = 1,
                pageNumber = 604,
                juzNumber = 30,
                text = "قُلْ هُوَ اللَّهُ أَحَدٌ",
                surahName = "الإخلاص",
                snippet = "قُلْ هُوَ اللَّهُ أَحَدٌ",
                highlightStart = 8,
                highlightEnd = 14,
            ),
        ),
        indexed = true,
    )

    val word = WordRecord(
        id = 2551,
        surah = 2,
        ayah = 255,
        position = 1,
        uthmanic = "ٱللَّهُ",
        qcfLigature = "",
        isAyahMarker = false,
        meaning = "الإله المعبود بحق",
    )

    val studyUi = StudyUiState(
        word = word,
        surahName = "البقرة",
        ayahWords = listOf(
            QuranWord(2551, 2, 255, 1, "", "ٱللَّهُ", false, "الإله المعبود بحق"),
            QuranWord(2552, 2, 255, 2, "", "لَآ", false, "نافية"),
            QuranWord(2553, 2, 255, 3, "", "إِلَٰهَ", false, "معبود"),
            QuranWord(2554, 2, 255, 4, "", "إِلَّا", false, "أداة استثناء"),
            QuranWord(2555, 2, 255, 5, "", "هُوَ", false, "ضمير"),
        ),
        ayahText = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ",
        pageNumber = 42,
        bookmarked = true,
        reciters = ayahReciters,
        surahReciters = reciters,
        selectedReciterId = ayahReciters.first().id,
        videoReciterId = ayahReciters.first().id,
        videoFromAyah = 255,
        videoToAyah = 257,
    )

    val bookmarks = listOf(
        BookmarkListItem(
            bookmark = ReadingBookmark(
                id = "2:255",
                surah = 2,
                ayah = 255,
                pageNumber = 42,
                wordId = 2551,
                wordIndex = 1,
                ayahText = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ",
                savedAt = 1_704_067_200_000L,
            ),
            surahName = "البقرة",
        ),
        BookmarkListItem(
            bookmark = ReadingBookmark(
                id = "1:1",
                surah = 1,
                ayah = 1,
                pageNumber = 1,
                wordId = 1,
                wordIndex = 1,
                ayahText = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
                savedAt = 2L,
            ),
            surahName = "الفاتحة",
        ),
    )

    val bookmarksUi = BookmarksUiState(
        items = bookmarks,
        target = BookmarkTarget(pageNumber = 42, surah = 2, ayah = 255, wordId = 2551, wordIndex = 1),
        targetSurahName = "البقرة",
        currentSaved = true,
        loaded = true,
    )

    val canvasLines = listOf(
        LineRecord(
            lineNum = 1,
            lineType = LineType.SURAH_NAME,
            centered = true,
            words = emptyList(),
            surahNumber = 1,
        ),
        LineRecord(
            lineNum = 2,
            lineType = LineType.BASMALLAH,
            centered = true,
            words = emptyList(),
            surahNumber = 1,
        ),
        LineRecord(
            lineNum = 3,
            lineType = LineType.AYAH,
            centered = false,
            words = listOf(
                WordRecord(1, 1, 2, 1, "ٱلْحَمْدُ", "", false),
                WordRecord(2, 1, 2, 2, "لِلَّهِ", "", false),
                WordRecord(3, 1, 2, 3, "رَبِّ", "", false),
                WordRecord(4, 1, 2, 4, "ٱلْعَٰلَمِينَ", "", false),
            ),
        ),
        LineRecord(
            lineNum = 4,
            lineType = LineType.AYAH,
            centered = false,
            words = listOf(
                WordRecord(5, 1, 3, 1, "ٱلرَّحْمَٰنِ", "", false),
                WordRecord(6, 1, 3, 2, "ٱلرَّحِيمِ", "", false, "ذي الرحمة الواسعة"),
            ),
        ),
    )
}

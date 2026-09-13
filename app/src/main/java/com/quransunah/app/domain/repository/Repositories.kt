package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.PageMeta
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo

interface MushafRepository {
    suspend fun warmup(): Result<Unit>
    suspend fun prefetchHeavyData()
    suspend fun prefetchIndexCatalog()
    suspend fun getPage(pageNumber: Int): MushafPage
    fun overlayMarkers(page: MushafPage): MushafPage
    suspend fun getSurahs(): List<SurahInfo>
    suspend fun getDivisions(): List<DivisionInfo>
    suspend fun getQuarters(): List<DivisionInfo>
    suspend fun getSajdas(): List<SajdaMarker>
    suspend fun getPageMeta(pageNumber: Int): PageMeta
    suspend fun getAyahWords(surah: Int, ayah: Int): List<QuranWord>
    suspend fun getPageForAyah(surah: Int, ayah: Int): Int
    suspend fun getSurahForPage(pageNumber: Int): Int
    suspend fun meaningForWord(wordId: Int): String?
}

interface TafsirRepository {
    suspend fun getAyahTafsir(surah: Int, ayah: Int): String?
    fun release()
}

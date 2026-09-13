package com.quransunah.app.media.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.AppLog
import com.quransunah.app.data.audio.SurahAudioStore
import com.quransunah.app.data.catalog.MoshafEdition
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.data.local.mushaf.entity.SurahEntity
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackProgress
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.media.PlaybackMediaIds
import com.quransunah.app.media.PlaybackSessionPolicy

class AutoLibrary(
    private val context: Context,
    private val reciterCatalog: ReciterCatalog,
    private val surahAudioStore: SurahAudioStore,
    private val sessionPolicy: PlaybackSessionPolicy,
) {
    private var surahById: Map<Int, SurahEntity> = emptyMap()
    private var reciterItems: ImmutableList<MediaItem> = ImmutableList.of()
    private val reciterSurahs = HashMap<Int, ImmutableList<MediaItem>>()
    private var flatSurahs: ImmutableList<MediaItem> = ImmutableList.of()
    private var flatSurahsMoshafId: Int? = null
    private var searchQuery: String? = null
    private var searchHits: ImmutableList<MediaItem> = ImmutableList.of()

    fun load(surahs: List<SurahEntity>) {
        surahById = surahs.mapNotNull { surah ->
            surah.id?.let { it to surah }
        }.toMap()
        reciterItems = ImmutableList.copyOf(
            reciterCatalog.surahReciters()
                .filter { it.moshafs.isNotEmpty() }
                .map { reciterFolder(it) },
        )
        reciterSurahs.clear()
        flatSurahs = ImmutableList.of()
        flatSurahsMoshafId = null
        searchQuery = null
        searchHits = ImmutableList.of()
        if (sessionPolicy.activeMoshafId == null) {
            reciterCatalog.defaultSurahReciter()?.let { sessionPolicy.activate(it) }
        }
        AppLog.i(TAG) {
            "loaded reciters=${reciterItems.size} surahs=${surahs.size} " +
                "reciterArt=${AutoArtwork.reciter(context, 92)} " +
                "surahArt=${AutoArtwork.surah(context, 1)}"
        }
    }

    fun root(): MediaItem = folder(
        mediaId = PlaybackMediaIds.ROOT,
        title = context.getString(R.string.app_name),
        artwork = AutoArtwork.launcher(context),
        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        searchSupported = true,
    )

    fun childrenOf(parentId: String): ImmutableList<MediaItem> {
        return when {
            parentId == PlaybackMediaIds.ROOT -> ImmutableList.of(recitersTab(), surahsTab())
            parentId == PlaybackMediaIds.RECITERS -> reciterItems
            parentId == PlaybackMediaIds.SURAHS -> flatSurahChildren()
            PlaybackMediaIds.parseReciter(parentId) != null -> {
                val reciterId = PlaybackMediaIds.parseReciter(parentId) ?: return ImmutableList.of()
                reciterSurahChildren(reciterId)
            }
            else -> ImmutableList.of()
        }
    }

    fun itemById(mediaId: String): MediaItem? {
        return when {
            mediaId == PlaybackMediaIds.ROOT -> root()
            mediaId == PlaybackMediaIds.RECITERS -> recitersTab()
            mediaId == PlaybackMediaIds.SURAHS -> surahsTab()
            PlaybackMediaIds.parseReciter(mediaId) != null ->
                reciterItems.firstOrNull { it.mediaId == mediaId }
            PlaybackMediaIds.parseBrowseRequest(mediaId) != null ->
                browsePlayable(mediaId)
            PlaybackMediaIds.parseSurah(mediaId) != null ->
                resolvePlayable(MediaItem.Builder().setMediaId(mediaId).build())
            else -> null
        }
    }

    fun search(query: String): ImmutableList<MediaItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchQuery = trimmed
            searchHits = ImmutableList.of()
            return searchHits
        }
        if (searchQuery == trimmed) return searchHits
        val reciter = activeReciter() ?: return ImmutableList.of()
        val moshaf = reciter.preferredMoshaf() ?: return ImmutableList.of()
        val ids = AutoSurahSearch.surahIds(trimmed, surahById.values)
            .filter { moshaf.contains(it) }
        val items = ids.map { number ->
            browseSurah(PlaybackMediaIds.browseFlatSurah(number), reciter, number)
        }
        searchQuery = trimmed
        searchHits = ImmutableList.copyOf(items)
        return searchHits
    }

    fun pageOf(
        items: ImmutableList<MediaItem>,
        page: Int,
        pageSize: Int,
    ): ImmutableList<MediaItem> {
        if (items.isEmpty() || pageSize <= 0 || page < 0) return ImmutableList.of()
        val fromLong = page.toLong() * pageSize.toLong()
        if (fromLong >= items.size) return ImmutableList.of()
        val from = fromLong.toInt()
        val to = (from.toLong() + pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
        if (from == 0 && to == items.size) return items
        return ImmutableList.copyOf(items.subList(from, to))
    }

    fun playbackQueue(mediaId: String?): MediaSession.MediaItemsWithStartPosition? {
        val request = PlaybackMediaIds.parsePlayRequest(mediaId) ?: return null
        val reciter = resolveReciter(request) ?: return null
        val moshaf = reciter.preferredMoshaf() ?: return null
        sessionPolicy.activate(reciter)
        val playlist = moshaf.surahNumbers.sorted().ifEmpty { (1..AppConstants.SURAH_COUNT).toList() }
        val startIndex = playlist.indexOf(request.surah).coerceAtLeast(0)
        val items = playlist.map { number -> playableSurah(moshaf, reciter, number, withUri = true) }
        return MediaSession.MediaItemsWithStartPosition(items, startIndex, 0L)
    }

    fun defaultQueue(progress: PlaybackProgress? = null): MediaSession.MediaItemsWithStartPosition? {
        val reciterId = progress?.reciterId?.toIntOrNull()
        val startSurah = progress?.surah?.takeIf { it in 1..AppConstants.SURAH_COUNT } ?: 1
        val mediaId = when {
            progress?.moshafId != null -> PlaybackMediaIds.surah(progress.moshafId, startSurah)
            reciterId != null -> PlaybackMediaIds.browseReciterSurah(reciterId, startSurah)
            else -> {
                val reciter = reciterCatalog.defaultSurahReciter() ?: return null
                PlaybackMediaIds.browseReciterSurah(reciter.id, startSurah)
            }
        }
        val queue = playbackQueue(mediaId) ?: return null
        val startPosition = progress?.positionMs?.coerceAtLeast(0L) ?: 0L
        return MediaSession.MediaItemsWithStartPosition(
            queue.mediaItems,
            queue.startIndex,
            startPosition,
        )
    }

    fun resolvePlayable(item: MediaItem): MediaItem {
        PlaybackMediaIds.parseBrowseRequest(item.mediaId)?.let { request ->
            val reciter = resolveReciter(request) ?: return item
            val moshaf = reciter.preferredMoshaf() ?: return item
            sessionPolicy.activate(reciter)
            return playableSurah(moshaf, reciter, request.surah, withUri = true)
        }
        PlaybackMediaIds.parseSurah(item.mediaId)?.let { (moshafId, surah) ->
            val moshaf = reciterCatalog.moshaf(moshafId) ?: return item
            val reciter = reciterCatalog.reciterForMoshaf(moshafId) ?: return item
            return playableSurah(moshaf, reciter, surah, withUri = true)
        }
        val existing = item.localConfiguration?.uri
        if (existing != null && existing != Uri.EMPTY) return item
        return item
    }

    private fun recitersTab(): MediaItem = folder(
        mediaId = PlaybackMediaIds.RECITERS,
        title = AutoPresentation.rtlLabel(context.getString(R.string.auto_root_reciters)),
        artwork = AutoArtwork.tabReciters(context),
        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    private fun surahsTab(): MediaItem = folder(
        mediaId = PlaybackMediaIds.SURAHS,
        title = AutoPresentation.rtlLabel(context.getString(R.string.auto_root_surahs)),
        artwork = AutoArtwork.tabSurahs(context),
        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    @Suppress("DEPRECATION")
    private fun reciterFolder(reciter: SurahReciter): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(AutoPresentation.rtlLabel(reciter.name))
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            .setArtworkUri(AutoArtwork.reciter(context, reciter.id))
            .build()
        return MediaItem.Builder()
            .setMediaId(PlaybackMediaIds.reciter(reciter.id))
            .setMediaMetadata(metadata)
            .build()
    }

    private fun reciterSurahChildren(reciterId: Int): ImmutableList<MediaItem> {
        reciterSurahs[reciterId]?.let { return it }
        val reciter = reciterCatalog.surahReciter(reciterId) ?: return ImmutableList.of()
        sessionPolicy.activate(reciter)
        val moshaf = reciter.preferredMoshaf() ?: return ImmutableList.of()
        val items = moshaf.surahNumbers.sorted().map { number ->
            browseSurah(PlaybackMediaIds.browseReciterSurah(reciter.id, number), reciter, number)
        }
        return ImmutableList.copyOf(items).also { reciterSurahs[reciterId] = it }
    }

    private fun flatSurahChildren(): ImmutableList<MediaItem> {
        val reciter = activeReciter() ?: return ImmutableList.of()
        val moshaf = reciter.preferredMoshaf() ?: return ImmutableList.of()
        if (flatSurahsMoshafId == moshaf.id && !flatSurahs.isEmpty()) return flatSurahs
        val items = (1..AppConstants.SURAH_COUNT)
            .filter { moshaf.contains(it) }
            .map { number ->
                browseSurah(PlaybackMediaIds.browseFlatSurah(number), reciter, number)
            }
        flatSurahs = ImmutableList.copyOf(items)
        flatSurahsMoshafId = moshaf.id
        return flatSurahs
    }

    private fun browsePlayable(mediaId: String): MediaItem? {
        val request = PlaybackMediaIds.parseBrowseRequest(mediaId) ?: return null
        val reciter = resolveReciter(request) ?: return null
        return browseSurah(mediaId, reciter, request.surah)
    }

    private fun browseSurah(mediaId: String, reciter: SurahReciter, surah: Int): MediaItem {
        return playableSurah(reciter.preferredMoshaf(), reciter, surah, withUri = false, mediaId = mediaId)
    }

    @Suppress("DEPRECATION")
    private fun playableSurah(
        moshaf: MoshafEdition?,
        reciter: SurahReciter,
        surah: Int,
        withUri: Boolean,
        mediaId: String = PlaybackMediaIds.surah(moshaf?.id ?: reciter.id, surah),
    ): MediaItem {
        val entity = surahById[surah]
        val name = AutoPresentation.surahLabel(surah, entity?.nameArabic)
        val ayahCount = entity?.numberOfAyahs ?: 0
        val revelation = entity?.revelationType.orEmpty()
        val (uri, fromLocal) = if (withUri && moshaf != null) {
            surahAudioStore.playbackUri(moshaf, surah)
        } else {
            Uri.EMPTY to false
        }
        val extras = PlaybackMediaIds.extras(
            domain = PlaybackDomain.SURAH,
            reciterName = reciter.name,
            reciterId = reciter.id.toString(),
            surah = surah,
            surahName = name,
            moshafId = moshaf?.id,
            fromLocal = fromLocal,
            repeatMode = SurahRepeatMode.REMAINING,
        )
        val metadata = MediaMetadata.Builder()
            .setTitle(AutoPresentation.surahTitle(surah, name))
            .setSubtitle(AutoPresentation.surahSubtitle(revelation, ayahCount))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
            .setArtworkUri(AutoArtwork.surah(context, surah))
            .setExtras(extras)
            .apply {
                if (withUri) {
                    setTrackNumber(surah)
                    setArtist(AutoPresentation.rtlLabel(reciter.name))
                }
            }
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
        if (withUri && uri != Uri.EMPTY) {
            builder.setUri(uri)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun folder(
        mediaId: String,
        title: String,
        artwork: Uri,
        browsableStyle: Int,
        playableStyle: Int,
        searchSupported: Boolean = false,
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsableStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playableStyle)
            if (searchSupported) putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            .setArtworkUri(artwork)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun resolveReciter(request: PlaybackMediaIds.SurahPlayRequest): SurahReciter? {
        request.reciterId?.let { id -> reciterCatalog.surahReciter(id)?.let { return it } }
        request.moshafId?.let { id -> reciterCatalog.reciterForMoshaf(id)?.let { return it } }
        return activeReciter()
    }

    private fun activeReciter(): SurahReciter? {
        sessionPolicy.activeReciterId?.let { reciterCatalog.surahReciter(it)?.let { found -> return found } }
        return reciterCatalog.defaultSurahReciter()
    }

    companion object {
        private const val TAG = "QuranAuto"
    }
}

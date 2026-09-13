@file:OptIn(UnstableApi::class)

package com.quransunah.app.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.quransunah.app.MainActivity
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.AppLog
import com.quransunah.app.core.AppUiPresence
import com.quransunah.app.data.audio.SurahAudioStore
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.local.mushaf.MushafDao
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.media.auto.AutoLibrary
import com.quransunah.app.media.auto.AutoRtl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Standalone Media3 library for Android Auto and in-app playback.
 *
 * Ported from `quran-app` [com.quransunah.app.service.PlaybackService]: native
 * ExoPlayer + MediaLibrary browse. Capacitor / WebView is not used.
 *
 * USB and wireless Auto bind this service directly. [MainActivity] is not
 * required to browse or start recitation from the head unit.
 *
 * Wireless Auto briefly unbinds during Wi‑Fi / BT latency spikes. A
 * mediaPlayback foreground session plus Wi‑Fi / CPU wake locks prevent the
 * bind/destroy loop that hides the app on Samsung One UI (S24 Ultra).
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var reciterCatalog: ReciterCatalog

    @Inject
    lateinit var surahAudioStore: SurahAudioStore

    @Inject
    lateinit var sessionPolicy: PlaybackSessionPolicy

    @Inject
    lateinit var mushafDao: MushafDao

    @Inject
    lateinit var mushafRepository: MushafRepository

    @Inject
    lateinit var progressTracker: PlaybackProgressTracker

    private lateinit var player: ExoPlayer
    private lateinit var autoLibrary: AutoLibrary
    private var librarySession: MediaLibrarySession? = null
    private var sessionActivity: PendingIntent? = null
    private var foregroundStarted = false
    private var autoSessionActive = false
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var releaseKeepAliveJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val catalogReady = CompletableDeferred<Unit>()
    private var playerListener: Player.Listener? = null
    /**
     * Set after an explicit Play from the phone UI or Android Auto.
     * Blocks Bluetooth headset / car A2DP auto-resume from starting audio
     * when the user never opened the app or Auto this process.
     */
    private var userAllowedPlayback = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AutoRtl.wrap(newBase))
    }

    override fun onCreate() {
        // Match quran-app: RTL labels ready before any MediaItem titles are built.
        AutoRtl.forceArabicRtl(this)
        super.onCreate()
        AppLog.i(TAG) { "onCreate: standalone MediaLibraryService (USB+wireless)" }
        createPlaybackChannel()
        autoLibrary = AutoLibrary(this, reciterCatalog, surahAudioStore, sessionPolicy)
        // Seed browse structure immediately; titles use bundled SurahArabicNames
        // until the mushaf DB finishes loading. Do NOT mark catalogReady yet —
        // early completion previously let Auto show "001. سورة 1".
        autoLibrary.load(emptyList())
        serviceScope.launch { prepareCatalog() }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(AppConstants.PLAYBACK_NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .build()
            .also { provider -> provider.setSmallIcon(R.drawable.ic_stat_playback) }
        setMediaNotificationProvider(notificationProvider)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableAudioFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 8_000,
                        /* maxBufferMs = */ 24_000,
                        /* bufferForPlaybackMs = */ 250,
                        /* bufferForPlaybackAfterRebufferMs = */ 500,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setPauseAtEndOfMediaItems(false)
            .setSkipSilenceEnabled(false)
            .build()
        // Strict no auto-play: queue may be prepared for Auto browse, but audio
        // starts only after an explicit Play command from the user / head unit.
        player.playWhenReady = false
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                    )
                    .build(),
            )
            .build()
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) ensurePlaybackForeground()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    if (player.playWhenReady) ensurePlaybackForeground()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (sessionPolicy.pauseAtEndOfItems &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    player.pause()
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                        val duration = player.duration
                        if (duration != C.TIME_UNSET && duration > 0L) {
                            player.seekTo(duration)
                        }
                    }
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
        progressTracker.attach(player)

        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(mainActivityPendingIntent())
            .setId("quran-playback")
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(this)))
            // Periodic position ticks make Auto re-render browse and snap to now-playing.
            .setPeriodicPositionUpdateEnabled(false)
            .build()
        // Drop any stale "متصل بـ Android Auto" FGS left from a previous process.
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_AUTO_SESSION -> {
                AppLog.i(TAG) { "onStartCommand: ACTION_AUTO_SESSION" }
                promoteAutoSession(reason = ACTION_AUTO_SESSION, startSelf = false)
                serviceScope.launch {
                    withTimeoutOrNull(8_000) { catalogReady.await() }
                    prepareLastSessionForAuto()
                }
            }
            ACTION_AUTO_DETACH -> {
                AppLog.i(TAG) { "onStartCommand: ACTION_AUTO_DETACH" }
                demoteAutoSession(reason = ACTION_AUTO_DETACH)
            }
            ACTION_KEEP_ALIVE -> {
                if (autoSessionActive || isActivePlayback()) {
                    acquireNetworkLocks()
                    if (isActivePlayback()) ensurePlaybackForeground()
                }
            }
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        AppLog.i(TAG) { "onGetSession: ${describeController(controllerInfo)}" }
        if (isCarOrLegacyBrowser(controllerInfo)) {
            promoteAutoSession(reason = "onGetSession")
        }
        return librarySession
    }

    override fun isPlaybackOngoing(): Boolean {
        return super.isPlaybackOngoing() ||
            isActivePlayback() ||
            autoSessionActive ||
            hasExternalControllers()
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Phone idle / Auto browse must stay silent. Only playing audio (or Media3
        // explicitly requiring FGS for an active player) may post a notification.
        val shouldForeground = isActivePlayback() ||
            (startInForegroundRequired && (::player.isInitialized && player.playWhenReady))
        if (shouldForeground) {
            super.onUpdateNotification(session, true)
            foregroundStarted = true
        } else {
            super.onUpdateNotification(session, false)
            if (foregroundStarted && !isActivePlayback()) {
                clearIdleForeground()
            }
        }
    }

    override fun onDestroy() {
        AppLog.i(TAG) { "onDestroy" }
        releaseKeepAliveJob?.cancel()
        releaseKeepAliveJob = null
        releaseNetworkLocks()
        serviceJob.cancel()
        progressTracker.detach()
        librarySession?.release()
        librarySession = null
        if (::player.isInitialized) {
            playerListener?.let { player.removeListener(it) }
            playerListener = null
            player.release()
        }
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        autoSessionActive = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isActivePlayback() || hasExternalControllers() || autoSessionActive) {
            AppLog.i(TAG) { "onTaskRemoved: keep running (playback or Auto browser)" }
            // Keep process/locks — do not post an Auto "connected" notification.
            releaseKeepAliveJob?.cancel()
            autoSessionActive = autoSessionActive || hasExternalControllers()
            acquireNetworkLocks()
            runCatching {
                startService(Intent(this, PlaybackService::class.java).setAction(ACTION_KEEP_ALIVE))
            }
            return
        }
        if (::player.isInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        stopSelf()
    }

    private suspend fun prepareCatalog() {
        try {
            mushafRepository.warmup()
            val surahs = withContext(Dispatchers.IO) {
                runCatching { mushafDao.getSurahs() }.getOrDefault(emptyList())
            }
            autoLibrary.load(surahs)
            AppLog.i(TAG) { "catalog ready surahs=${surahs.size} reciters=${reciterCatalog.surahReciters().size}" }
            refreshPlayerQueueTitles()
        } catch (t: Throwable) {
            AppLog.w(TAG, "catalog warmup failed; Auto will use bundled Surah names", t)
            autoLibrary.load(emptyList())
        } finally {
            if (!catalogReady.isCompleted) catalogReady.complete(Unit)
        }
    }

    /**
     * Rebuild titles on an already-prepared Auto queue that may have been
     * created before the mushaf DB finished loading.
     */
    private fun refreshPlayerQueueTitles() {
        if (!::player.isInitialized || !::autoLibrary.isInitialized) return
        if (player.mediaItemCount <= 0) return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val refreshed = (0 until player.mediaItemCount).map { i ->
            autoLibrary.resolvePlayable(player.getMediaItemAt(i))
        }
        runCatching {
            player.setMediaItems(refreshed, index, position)
            player.prepare()
            player.playWhenReady = playWhenReady
            AppLog.i(TAG) { "refreshPlayerQueueTitles: items=${refreshed.size}" }
        }.onFailure { t ->
            AppLog.w(TAG, "refreshPlayerQueueTitles failed", t)
        }
    }

    private fun isActivePlayback(): Boolean {
        if (!::player.isInitialized) return false
        return player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }

    private fun hasExternalControllers(): Boolean {
        val session = librarySession ?: return false
        return session.connectedControllers.any { controller ->
            isCarOrLegacyBrowser(controller)
        }
    }

    /**
     * True only for Android Auto / Automotive / DHU media browsers.
     * Deliberately excludes bare GMS and uid&lt;0 probes — those fire on normal
     * phone launch and previously forced a sticky "connected to Auto" FGS.
     */
    private fun isCarOrLegacyBrowser(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName.orEmpty()
        if (pkg.contains("projection.gearhead", ignoreCase = true)) return true
        if (pkg.contains("android.car", ignoreCase = true)) return true
        if (pkg.contains("automotive", ignoreCase = true)) return true
        if (pkg.contains("desktopheadunit", ignoreCase = true)) return true
        if (pkg.contains("car.media", ignoreCase = true)) return true
        if (pkg.contains("carapp", ignoreCase = true)) return true
        return false
    }

    /**
     * Phone UI or Android Auto may start audio. Bare Bluetooth A2DP / headset
     * media-key auto-resume must not start playback in the background.
     */
    private fun mayStartPlayback(controller: MediaSession.ControllerInfo): Boolean {
        if (isCarOrLegacyBrowser(controller)) return true
        if (AppUiPresence.isUiStarted) return true
        if (userAllowedPlayback) return true
        return false
    }

    private fun authorizePlayback(controller: MediaSession.ControllerInfo) {
        if (isCarOrLegacyBrowser(controller) || AppUiPresence.isUiStarted) {
            userAllowedPlayback = true
        }
    }

    private fun promoteAutoSession(reason: String, startSelf: Boolean = true) {
        releaseKeepAliveJob?.cancel()
        releaseKeepAliveJob = null
        autoSessionActive = true
        acquireNetworkLocks()
        if (startSelf) {
            runCatching {
                startService(Intent(this, PlaybackService::class.java).setAction(ACTION_KEEP_ALIVE))
            }
        }
        // Never post "متصل بـ Android Auto" here. Notification only when audio plays.
        if (isActivePlayback() || (::player.isInitialized && player.playWhenReady)) {
            ensurePlaybackForeground()
        } else {
            clearIdleForeground()
        }
        AppLog.i(TAG) { "promoteAutoSession: $reason (no idle Auto notification)" }
    }

    private fun demoteAutoSession(reason: String) {
        AppLog.i(TAG) { "demoteAutoSession: $reason" }
        releaseKeepAliveJob?.cancel()
        releaseKeepAliveJob = null
        autoSessionActive = false
        if (isActivePlayback()) {
            ensurePlaybackForeground()
            return
        }
        releaseNetworkLocks()
        clearIdleForeground()
    }

    private fun scheduleAutoSessionRelease() {
        releaseKeepAliveJob?.cancel()
        releaseKeepAliveJob = serviceScope.launch {
            delay(AUTO_KEEP_ALIVE_GRACE_MS)
            if (isActivePlayback() || hasExternalControllers()) return@launch
            demoteAutoSession(reason = "keep-alive-expired")
        }
    }

    private fun clearIdleForeground() {
        if (!foregroundStarted) return
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        foregroundStarted = false
        // Also cancel any leftover media notification id.
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireNetworkLocks() {
        if (wifiLock?.isHeld != true) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            val lock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
            } else {
                wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
            }
            lock?.setReferenceCounted(false)
            runCatching { lock?.acquire() }
            wifiLock = lock
        }
        if (wakeLock?.isHeld != true) {
            val pm = applicationContext.getSystemService(PowerManager::class.java)
            val lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock?.setReferenceCounted(false)
            runCatching { lock?.acquire(10 * 60 * 1000L) }
            wakeLock = lock
        }
    }

    private fun releaseNetworkLocks() {
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wifiLock = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    /**
     * Media playback FGS with dynamic title/artist — never the Auto-connected copy.
     * Does not mark [autoSessionActive]; Auto promote is separate.
     */
    private fun ensurePlaybackForeground() {
        val session = librarySession ?: return
        if (!::player.isInitialized) return
        createPlaybackChannel()
        val title = player.mediaMetadata.title?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val text = player.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: player.mediaMetadata.subtitle?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notification_channel_playback)
        val notification = NotificationCompat.Builder(this, AppConstants.PLAYBACK_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_playback)
            .setContentIntent(mainActivityPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()
        try {
            ServiceCompat.startForeground(
                this,
                DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
        } catch (t: Throwable) {
            AppLog.e(TAG, "ensurePlaybackForeground: failed", t)
            onUpdateNotification(session, true)
        }
    }

    /**
     * Load the last persisted surah queue into the player without forcing play.
     * Auto / DHU can then resume via [LibraryCallback.onPlaybackResumption].
     */
    private fun prepareLastSessionForAuto() {
        if (!::player.isInitialized || !::autoLibrary.isInitialized) return
        if (player.mediaItemCount > 0) {
            player.playWhenReady = false
            return
        }
        val queue = autoLibrary.defaultQueue(progressTracker.last()) ?: return
        runCatching {
            player.setMediaItems(queue.mediaItems, queue.startIndex, queue.startPositionMs)
            player.prepare()
            // Never auto-start on car attach — wait for explicit Play.
            player.playWhenReady = false
            AppLog.i(TAG) {
                "prepareLastSessionForAuto: items=${queue.mediaItems.size} start=${queue.startIndex} paused"
            }
        }.onFailure { t ->
            AppLog.w(TAG, "prepareLastSessionForAuto failed", t)
        }
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AppConstants.PLAYBACK_NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_playback)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Session / notification tap target only. Auto playback never starts this
     * activity. Use a plain PendingIntent (same as quran-app) — ActivityOptions
     * BAL modes on PendingIntent.create crash on Samsung Android 15/16.
     */
    private fun mainActivityPendingIntent(): PendingIntent {
        sessionActivity?.let { return it }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AppConstants.EXTRA_FROM_ANDROID_AUTO, true)
        }
        val pending = PendingIntent.getActivity(
            this,
            /* requestCode= */ 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        sessionActivity = pending
        return pending
    }

    private fun <T> libraryFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = serviceScope.launch {
            runCatching { block() }
                .onSuccess { value -> if (!future.isDone) future.set(value) }
                .onFailure { error -> if (!future.isDone) future.setException(error) }
        }
        future.addListener(
            { if (future.isCancelled) job.cancel() },
            DirectExecutor,
        )
        return future
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            AppLog.i(TAG) { "onConnect: ${describeController(controller)}" }
            // Browse/connect must never start audio — only an explicit Play command may.
            if (::player.isInitialized) {
                player.playWhenReady = false
            }
            if (isCarOrLegacyBrowser(controller)) {
                promoteAutoSession(reason = "onConnect")
            }
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                )
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            AppLog.i(TAG) { "onDisconnected: ${describeController(controller)}" }
            if (!isActivePlayback() && !hasExternalControllers()) {
                scheduleAutoSessionRelease()
            }
            super.onDisconnected(session, controller)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return libraryFuture {
                if (isCarOrLegacyBrowser(browser)) {
                    promoteAutoSession(reason = "onGetLibraryRoot")
                }
                // Root should answer quickly for wireless Auto; surah titles use
                // bundled names even before the DB completes.
                withTimeoutOrNull(2_500) { catalogReady.await() }
                val extras = Bundle().apply {
                    putBoolean(SEARCH_SUPPORTED, true)
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                    )
                }
                val rootParams = MediaLibraryService.LibraryParams.Builder().setExtras(extras).build()
                LibraryResult.ofItem(autoLibrary.root(), rootParams)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return libraryFuture {
                if (isCarOrLegacyBrowser(browser)) {
                    promoteAutoSession(reason = "onGetChildren")
                }
                withTimeoutOrNull(8_000) { catalogReady.await() }
                val children = autoLibrary.pageOf(autoLibrary.childrenOf(parentId), page, pageSize)
                AppLog.d(TAG) { "onGetChildren parent=$parentId count=${children.size}" }
                LibraryResult.ofItemList(children, /* params= */ null)
            }
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            // Do not notifyChildrenChanged: Auto re-fetches and jumps to now-playing.
            return FuturesVoid
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            return libraryFuture {
                catalogReady.await()
                val hits = autoLibrary.search(query)
                AppLog.i(TAG) { "onSearch q='$query' hits=${hits.size}" }
                session.notifySearchResultChanged(browser, query, hits.size, params)
                LibraryResult.ofVoid()
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return libraryFuture {
                catalogReady.await()
                val pageItems = autoLibrary.pageOf(autoLibrary.search(query), page, pageSize)
                AppLog.i(TAG) { "onGetSearchResult q='$query' page=$page size=${pageItems.size}" }
                LibraryResult.ofItemList(pageItems, /* params= */ null)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return libraryFuture {
                catalogReady.await()
                val item = autoLibrary.itemById(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, /* params= */ null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return libraryFuture {
                catalogReady.await()
                mediaItems.map { autoLibrary.resolvePlayable(it) }.toMutableList()
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return libraryFuture {
                catalogReady.await()
                val safePosition = startPositionMs.coerceAtLeast(0L)
                val fromAppPlaylist = mediaItems.size > 1
                if (!fromAppPlaylist) {
                    val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
                        ?: mediaItems.firstOrNull()?.mediaId
                    val queue = autoLibrary.playbackQueue(requestedId)
                    if (queue != null) {
                        AppLog.i(TAG) {
                            "onSetMediaItems Auto queue size=${queue.mediaItems.size} " +
                                "start=${queue.startIndex} from=${describeController(controller)}"
                        }
                        ensurePlaybackForeground()
                        return@libraryFuture MediaSession.MediaItemsWithStartPosition(
                            queue.mediaItems,
                            queue.startIndex,
                            safePosition,
                        )
                    }
                }
                val resolved = mediaItems.map { autoLibrary.resolvePlayable(it) }
                ensurePlaybackForeground()
                MediaSession.MediaItemsWithStartPosition(resolved, startIndex, safePosition)
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            if (playerCommand == Player.COMMAND_PLAY_PAUSE) {
                val starting = ::player.isInitialized &&
                    !player.playWhenReady &&
                    !player.isPlaying
                if (starting && !mayStartPlayback(controller)) {
                    AppLog.i(TAG) {
                        "reject background auto-play from ${describeController(controller)}"
                    }
                    return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                }
                if (starting) authorizePlayback(controller)
                AppLog.i(TAG) { "onPlay: COMMAND_PLAY_PAUSE from ${describeController(controller)}" }
                ensurePlaybackForeground()
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val event = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                val wouldStart = when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> true
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    -> ::player.isInitialized && !player.playWhenReady && !player.isPlaying
                    else -> false
                }
                if (wouldStart && !mayStartPlayback(controllerInfo)) {
                    AppLog.i(TAG) {
                        "suppress media-button auto-play key=${event.keyCode} " +
                            "from ${describeController(controllerInfo)}"
                    }
                    // Keep the player paused; consume so Media3 does not play.
                    if (::player.isInitialized) player.playWhenReady = false
                    return true
                }
                if (wouldStart) authorizePlayback(controllerInfo)
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return libraryFuture {
                catalogReady.await()
                // Supply the last queue for the head unit, but stay paused until Play.
                player.playWhenReady = false
                if (!mayStartPlayback(controller)) {
                    AppLog.i(TAG) {
                        "onPlaybackResumption: queue only (no auto-play) " +
                            describeController(controller)
                    }
                }
                if (player.mediaItemCount > 0) {
                    val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                    return@libraryFuture MediaSession.MediaItemsWithStartPosition(
                        items,
                        player.currentMediaItemIndex.coerceAtLeast(0),
                        player.currentPosition.coerceAtLeast(0L),
                    )
                }
                autoLibrary.defaultQueue(progressTracker.last())
                    ?: MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            }
        }
    }

    companion object {
        private const val TAG = "QuranAuto"
        private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val ACTION_KEEP_ALIVE = "com.quransunah.app.action.KEEP_ALIVE"
        const val ACTION_AUTO_SESSION = "com.quransunah.app.action.AUTO_SESSION"
        const val ACTION_AUTO_DETACH = "com.quransunah.app.action.AUTO_DETACH"
        private const val WIFI_LOCK_TAG = "quran:auto-wifi"
        private const val WAKE_LOCK_TAG = "quran:auto-wake"
        /** Longer grace for wireless Auto Wi‑Fi / BT rebind gaps on Samsung. */
        private const val AUTO_KEEP_ALIVE_GRACE_MS = 90_000L
        private val DirectExecutor = java.util.concurrent.Executor { it.run() }
        private val FuturesVoid: ListenableFuture<LibraryResult<Void>> =
            com.google.common.util.concurrent.Futures.immediateFuture(LibraryResult.ofVoid())

        fun describeController(controller: MediaSession.ControllerInfo): String {
            return "pkg=${controller.packageName} uid=${controller.uid}"
        }
    }
}

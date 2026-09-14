package com.quransunah.app.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val file: File) : RecordingState
    data class Ready(val file: File) : RecordingState
    data class Playing(val file: File) : RecordingState
}

@Singleton
class MemorizationRecorder @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val directory = File(context.cacheDir, "memorization-recordings").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun start(): Boolean {
        if (_state.value is RecordingState.Recording) return false
        stopPlayback()
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        return runCatching {
            val instance = MediaRecorder()
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setAudioEncodingBitRate(96_000)
            instance.setAudioSamplingRate(44_100)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            recorder = instance
            currentFile = file
            _state.value = RecordingState.Recording(file)
        }.onFailure {
            recorder?.release()
            recorder = null
            file.delete()
        }.isSuccess
    }

    fun stop(): Boolean {
        val active = recorder ?: return false
        val file = currentFile
        return runCatching {
            active.stop()
            active.release()
            recorder = null
            if (file != null && file.exists() && file.length() > 0) {
                _state.value = RecordingState.Ready(file)
            } else {
                file?.delete()
                _state.value = RecordingState.Idle
            }
        }.onFailure {
            active.release()
            recorder = null
            file?.delete()
            _state.value = RecordingState.Idle
        }.isSuccess
    }

    fun play() {
        val file = (state.value as? RecordingState.Ready)?.file ?: return
        stopPlayback()
        runCatching {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { _state.value = RecordingState.Ready(file) }
                prepare()
                start()
            }
            player = mediaPlayer
            _state.value = RecordingState.Playing(file)
        }
    }

    fun stopPlayback() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        val file = currentFile
        if (file != null && file.exists() && recorder == null) _state.value = RecordingState.Ready(file)
    }

    fun delete() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        stopPlayback()
        currentFile?.delete()
        currentFile = null
        _state.value = RecordingState.Idle
    }

    fun release() {
        delete()
    }
}

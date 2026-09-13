package com.quransunah.app.share

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Light feed-forward echo matching the React share-video taps:
 * dry 0.82, delays 240/480/720 ms at 0.17 / 0.10 / 0.055.
 */
@UnstableApi
internal class ShareEchoAudioProcessor : BaseAudioProcessor() {

    private var delay = FloatArray(0)
    private var delayFrames = IntArray(0)
    private var bufferFrames = 0
    private var writeIndex = 0
    private var maxDelayFrames = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        delayFrames = TAPS.map { tap ->
            (tap.delaySeconds * inputAudioFormat.sampleRate).roundToInt().coerceAtLeast(1)
        }.toIntArray()
        maxDelayFrames = delayFrames.maxOrNull() ?: 0
        bufferFrames = maxDelayFrames + 8
        delay = FloatArray(bufferFrames * inputAudioFormat.channelCount)
        writeIndex = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer)
            else -> processPcm16(inputBuffer)
        }
    }

    override fun onQueueEndOfStream() {
        // React's OfflineAudioContext renders exactly the merged PCM length and
        // truncates remaining echo. Flushing a 720ms tail here would stretch
        // audio past the ayah slides and desync the export.
    }

    override fun onFlush() {
        delay.fill(0f)
        writeIndex = 0
    }

    override fun onReset() {
        delay = FloatArray(0)
        delayFrames = IntArray(0)
        bufferFrames = 0
        writeIndex = 0
        maxDelayFrames = 0
    }

    private fun processPcm16(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (channels * 2)
        val output = replaceOutputBuffer(frames * channels * 2)
        val dry = FloatArray(channels)
        repeat(frames) {
            for (ch in 0 until channels) {
                dry[ch] = inputBuffer.short / PCM16_MAX
            }
            writeFrame(output, dry)
        }
        output.flip()
    }

    private fun processFloat(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val frames = inputBuffer.remaining() / (channels * 4)
        val output = replaceOutputBuffer(frames * channels * 4)
        val dry = FloatArray(channels)
        repeat(frames) {
            for (ch in 0 until channels) {
                dry[ch] = inputBuffer.float
            }
            writeFrame(output, dry)
        }
        output.flip()
    }

    private fun writeFrame(output: ByteBuffer, drySamples: FloatArray?) {
        val channels = inputAudioFormat.channelCount
        val floatOut = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        for (ch in 0 until channels) {
            val dry = drySamples?.getOrElse(ch) { 0f } ?: 0f
            var mix = dry * DRY
            for (index in TAPS.indices) {
                mix += delayed(ch, delayFrames[index]) * TAPS[index].gain
            }
            store(ch, dry)
            val clipped = mix.coerceIn(-1f, 1f)
            if (floatOut) {
                output.putFloat(clipped)
            } else {
                output.putShort((clipped * PCM16_MAX).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        writeIndex = (writeIndex + 1) % bufferFrames.coerceAtLeast(1)
    }

    private fun delayed(channel: Int, framesBack: Int): Float {
        if (bufferFrames <= 0 || delay.isEmpty()) return 0f
        val index = (writeIndex - framesBack + bufferFrames) % bufferFrames
        return delay[index * inputAudioFormat.channelCount + channel]
    }

    private fun store(channel: Int, sample: Float) {
        if (delay.isEmpty()) return
        delay[writeIndex * inputAudioFormat.channelCount + channel] = sample
    }

    private data class EchoTap(val delaySeconds: Float, val gain: Float)

    private companion object {
        const val DRY = 0.82f
        const val PCM16_MAX = 32768f
        val TAPS = listOf(
            EchoTap(0.24f, 0.17f),
            EchoTap(0.48f, 0.10f),
            EchoTap(0.72f, 0.055f),
        )
    }
}

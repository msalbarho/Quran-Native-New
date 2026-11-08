package com.quransunah.app.share

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Sample-accurate share-video clips matching the React pipeline:
 * decode to PCM, trim inter-ayah silence, then mux the real duration.
 */
internal data class ShareTimedClip(
    val file: File,
    val durationUs: Long,
)

internal object ShareAudioClip {
    private const val TRIM_THRESHOLD = 0.008f
    private const val TRIM_PAD_SECONDS = 0.02f
    private const val MIN_DURATION_US = 120_000L
    private const val MAX_DURATION_US = 280_000_000L
    private const val DECODE_TIMEOUT_US = 10_000L

    fun prepare(
        source: File,
        dest: File,
        trimStart: Boolean,
        trimEnd: Boolean,
    ): ShareTimedClip {
        if (source.extension.equals("wav", ignoreCase = true)) {
            return ShareTimedClip(source, wavPcmDurationUs(source))
        }
        val pcm = runCatching { decodePcm16(source) }.getOrNull()
        if (pcm == null) {
            return ShareTimedClip(source, probeDurationUs(source))
        }
        val trimmed = trimPcm(pcm, trimStart, trimEnd)
        writeWav(dest, trimmed)
        return ShareTimedClip(dest, framesToDurationUs(trimmed.frameCount, trimmed.sampleRate))
    }

    /** Clean maker-only PCM: exact requested clock crop, 44.1 kHz stereo, neutral resampling.
     * No echo, pitch, tempo, or copyright-evasion processing. Fades are on the export edges only.
     */
    fun prepareMaker(source: File, dest: File, fromMs: Long = 0, toMs: Long? = null,
                     fadeIn: Boolean, fadeOut: Boolean): ShareTimedClip {
        val pcm = decodePcm16(source)
        val first = (fromMs * pcm.sampleRate / 1000).toInt().coerceIn(0, pcm.frameCount - 1)
        val last = (toMs?.let { it * pcm.sampleRate / 1000 } ?: pcm.frameCount.toLong()).toInt()
            .coerceIn(first + 1, pcm.frameCount)
        val outputRate = 44100
        val outputFrames = ((last - first).toLong() * outputRate / pcm.sampleRate).toInt().coerceAtLeast(1)
        val samples = ShortArray(outputFrames * 2)
        fun sourceSample(frame: Int, channel: Int): Float {
            val i = frame.coerceIn(first, last - 1) * pcm.channelCount
            return when (pcm.channelCount) {
                1 -> pcm.samples[i].toFloat()
                2 -> pcm.samples[i + channel].toFloat()
                else -> (0 until pcm.channelCount).sumOf { pcm.samples[i + it].toInt() }.toFloat() / pcm.channelCount
            }
        }
        for (frame in 0 until outputFrames) {
            val sourcePosition = first + frame.toDouble() * pcm.sampleRate / outputRate
            val index = sourcePosition.toInt()
            val fraction = (sourcePosition - index).toFloat()
            val fadeFrames = minOf(outputRate * 3 / 10, outputFrames / 2).coerceAtLeast(1)
            var gain = 1f
            if (fadeIn) gain = minOf(gain, frame.toFloat() / fadeFrames)
            if (fadeOut) gain = minOf(gain, (outputFrames - 1 - frame).toFloat() / fadeFrames)
            for (channel in 0..1) {
                val a = sourceSample(index, channel)
                val b = sourceSample(index + 1, channel)
                samples[frame * 2 + channel] = ((a + (b - a) * fraction) * gain).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        writeWav(dest, Pcm16(outputRate, 2, samples))
        return ShareTimedClip(dest, framesToDurationUs(outputFrames, outputRate))
    }
    /** One continuous encoder input. Never trim a verse at a word-alignment anchor:
     * QUL anchors may be inside a syllable, not at the safe boundary of the audio file.
     * Original ayah files already include their own leading/trailing safety margins.
     */
    fun concatenateMaker(clips: List<ShareTimedClip>, dest: File): ShareTimedClip {
        require(clips.isNotEmpty())
        val dataBytes = clips.sumOf { it.file.length() - 44L }
        require(dataBytes in 4..Int.MAX_VALUE.toLong())
        val totalFrames = dataBytes / 4
        val fadeFrames = minOf(13230L, totalFrames / 2).coerceAtLeast(1)
        dest.outputStream().use { output ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII)); header.putInt(36 + dataBytes.toInt())
            header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); header.putInt(16)
            header.putShort(1); header.putShort(2); header.putInt(44100); header.putInt(176400)
            header.putShort(4); header.putShort(16); header.put("data".toByteArray(Charsets.US_ASCII)); header.putInt(dataBytes.toInt())
            output.write(header.array())
            var sampleIndex = 0L
            val buffer = ByteArray(65536)
            clips.forEach { clip ->
                clip.file.inputStream().use { input ->
                    check(input.skip(44) == 44L)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(count % 2 == 0)
                        var i = 0
                        while (i < count) {
                            val frame = sampleIndex / 2
                            val gain = makerEdgeFadeGain(frame, totalFrames)
                            if (gain < 1f) {
                                val original = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort().toInt()
                                val faded = (original * gain).toInt()
                                buffer[i] = faded.toByte(); buffer[i + 1] = (faded shr 8).toByte()
                            }
                            sampleIndex++; i += 2
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        return ShareTimedClip(dest, totalFrames * 1_000_000L / 44100)
    }
    /** Measure AAC priming against the exact WAV given to the encoder, rather than
     * assuming a device-specific constant or dropping potentially useful AAC packets.
     */
    fun makerEncoderDelayUs(encoded: File, referenceWav: File): Long {
        val decoded = decodePcm16(encoded)
        check(decoded.sampleRate == 44100)
        val reference = referenceWav.inputStream().use { input ->
            check(input.skip(44) == 44L)
            val bytes = ByteArray(minOf(44100 * 3 * 4, (referenceWav.length() - 44).toInt()))
            var count = 0
            while (count < bytes.size) { val n = input.read(bytes, count, bytes.size - count); if (n < 0) break; count += n }
            ShortArray(count / 2).also { ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
        }
        val start = minOf(13230, reference.size / 8)
        val end = minOf(reference.size / 2, decoded.frameCount - 6616)
        if (end - start < 2000) return 0L
        fun score(lag: Int): Double {
            var xy = 0.0; var xx = 0.0; var yy = 0.0
            var frame = start
            while (frame < end) {
                val x = reference[frame * 2].toDouble()
                val y = decoded.samples[(frame + lag) * decoded.channelCount].toDouble()
                xy += x * y; xx += x * x; yy += y * y; frame += 16
            }
            return if (xx > 0 && yy > 0) xy / kotlin.math.sqrt(xx * yy) else 0.0
        }
        var bestLag = 0; var bestScore = -1.0
        for (lag in 0..6615 step 8) { val s = score(lag); if (s > bestScore) { bestLag = lag; bestScore = s } }
        val coarse = bestLag
        for (lag in maxOf(0, coarse - 12)..minOf(6615, coarse + 12)) {
            val s = score(lag); if (s > bestScore) { bestLag = lag; bestScore = s }
        }
        check(bestScore > .65) { "Cannot verify audio/video encoder sync ($bestScore)" }
        return bestLag * 1_000_000L / 44100
    }
    private fun wavPcmDurationUs(file: File): Long {
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(44)
                check(stream.read(header) >= 44)
                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = buffer.getShort(22).toInt().coerceAtLeast(1)
                val sampleRate = buffer.getInt(24).coerceAtLeast(1)
                val dataBytes = buffer.getInt(40).coerceAtLeast(0)
                framesToDurationUs(dataBytes / (channels * 2), sampleRate)
            }
        }.getOrElse { probeDurationUs(file) }
    }

    fun probeDurationUs(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = audioTrackIndex(extractor) ?: return fallbackDurationUs()
            val format = extractor.getTrackFormat(track)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            duration.takeIf { it > 0L } ?: fallbackDurationUs()
        } catch (_: Exception) {
            fallbackDurationUs()
        } finally {
            extractor.release()
        }.coerceIn(MIN_DURATION_US, MAX_DURATION_US)
    }

    private fun fallbackDurationUs(): Long = 8_000_000L

    private fun framesToDurationUs(frameCount: Int, sampleRate: Int): Long {
        if (sampleRate <= 0 || frameCount <= 0) return MIN_DURATION_US
        return ((frameCount * 1_000_000L) / sampleRate).coerceIn(MIN_DURATION_US, MAX_DURATION_US)
    }

    private data class Pcm16(
        val sampleRate: Int,
        val channelCount: Int,
        val samples: ShortArray,
    ) {
        val frameCount: Int get() = if (channelCount <= 0) 0 else samples.size / channelCount
    }

    private fun decodePcm16(file: File): Pcm16 {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = audioTrackIndex(extractor) ?: error("no audio track")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("no mime")
            codec = MediaCodec.createDecoderByType(mime).also { decoder ->
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()
            }
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmFloat = false
            var sawInputEos = false
            var sawOutputEos = false
            var idle = 0
            while (!sawOutputEos) {
                if (idle > 8_000) error("decode timeout")
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = codec.getInputBuffer(inIndex) ?: error("decoder input")
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                        idle = 0
                    } else {
                        idle++
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, DECODE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> idle++
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmFloat = outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                            android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    else -> if (outIndex >= 0) {
                        val output = codec.getOutputBuffer(outIndex)
                        if (output != null &&
                            info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            if (pcmFloat) {
                                appendFloatAsPcm16(output, pcm)
                            } else {
                                val chunk = ByteArray(info.size)
                                output.get(chunk)
                                pcm.write(chunk)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        idle = 0
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
            val bytes = pcm.toByteArray()
            require(bytes.isNotEmpty()) { "empty pcm" }
            val samples = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            return Pcm16(
                sampleRate = sampleRate.coerceAtLeast(8_000),
                channelCount = channelCount.coerceAtLeast(1),
                samples = samples,
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun appendFloatAsPcm16(buffer: ByteBuffer, pcm: ByteArrayOutputStream) {
        val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        val out = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (floats.hasRemaining()) {
            val sample = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            out.putShort(sample)
        }
        pcm.write(out.array())
    }

    private fun trimPcm(pcm: Pcm16, trimStart: Boolean, trimEnd: Boolean): Pcm16 {
        val channels = pcm.channelCount
        val frames = pcm.frameCount
        if (frames <= 0 || (!trimStart && !trimEnd)) return pcm
        val threshold = (TRIM_THRESHOLD * 32768f).toInt().coerceAtLeast(1)
        var start = 0
        var end = frames
        if (trimStart) {
            for (frame in 0 until frames) {
                if (frameAbove(pcm.samples, channels, frame, threshold)) {
                    start = frame
                    break
                }
            }
        }
        if (trimEnd) {
            for (frame in frames - 1 downTo start) {
                if (frameAbove(pcm.samples, channels, frame, threshold)) {
                    end = frame + 1
                    break
                }
            }
        }
        val pad = kotlin.math.ceil(pcm.sampleRate * TRIM_PAD_SECONDS).toInt()
        if (trimStart) start = (start - pad).coerceAtLeast(0)
        if (trimEnd) end = (end + pad).coerceAtMost(frames)
        if (start <= 0 && end >= frames) return pcm
        val keptFrames = (end - start).coerceAtLeast(0)
        val sliced = ShortArray(keptFrames * channels)
        pcm.samples.copyInto(
            destination = sliced,
            startIndex = start * channels,
            endIndex = start * channels + sliced.size,
        )
        return pcm.copy(samples = sliced)
    }

    private fun frameAbove(samples: ShortArray, channels: Int, frame: Int, threshold: Int): Boolean {
        val offset = frame * channels
        for (channel in 0 until channels) {
            if (abs(samples[offset + channel].toInt()) > threshold) return true
        }
        return false
    }

    private fun writeWav(file: File, pcm: Pcm16) {
        val bits = 16
        val dataBytes = pcm.samples.size * 2
        file.outputStream().use { stream ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(pcm.channelCount.toShort())
            header.putInt(pcm.sampleRate)
            header.putInt(pcm.sampleRate * pcm.channelCount * bits / 8)
            header.putShort((pcm.channelCount * bits / 8).toShort())
            header.putShort(bits.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            stream.write(header.array())
            val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcm.samples) body.putShort(sample)
            stream.write(body.array())
        }
    }

    private fun audioTrackIndex(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return null
    }
}

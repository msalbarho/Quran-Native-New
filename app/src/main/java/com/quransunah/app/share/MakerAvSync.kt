package com.quransunah.app.share

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Lossless sample remux: keep every AAC packet and compensate measured priming
 * with video timestamps. No re-encoding, dropped syllables, or audio DSP.
 */
internal object MakerAvSync {
    fun align(encoded: File, output: File, videoDelayUs: Long) {
        val extractor = MediaExtractor()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(encoded.absolutePath)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(encoded.absolutePath)
                val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                muxer.setOrientationHint(rotation)
            } finally { metadata.release() }
            val video = BooleanArray(extractor.trackCount)
            val tracks = IntArray(extractor.trackCount)
            var maxSize = 4 * 1024 * 1024
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                video[track] = format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSize = maxOf(maxSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                tracks[track] = muxer.addTrack(format)
                extractor.selectTrack(track)
            }
            muxer.start()
            val data = ByteBuffer.allocateDirect(maxSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                data.clear()
                val size = extractor.readSampleData(data, 0)
                if (size < 0) break
                val track = extractor.sampleTrackIndex
                info.set(0, size, extractor.sampleTime + if (video[track]) videoDelayUs else 0L, extractor.sampleFlags)
                muxer.writeSampleData(tracks[track], data, info)
                extractor.advance()
            }
            muxer.stop()
        } finally { extractor.release(); muxer.release() }
    }
}

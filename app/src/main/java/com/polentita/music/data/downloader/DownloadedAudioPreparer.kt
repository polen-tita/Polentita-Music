package com.polentita.music.data.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal data class DownloadedMediaTracks(
    val hasAudio: Boolean,
    val hasVideo: Boolean,
)

internal enum class DownloadedMediaPreparation {
    USE_AUDIO_FILE,
    EXTRACT_AUDIO_TRACK,
    REJECT,
}

internal fun downloadedMediaPreparation(
    tracks: DownloadedMediaTracks,
): DownloadedMediaPreparation = when {
    tracks.hasAudio && tracks.hasVideo -> DownloadedMediaPreparation.EXTRACT_AUDIO_TRACK
    tracks.hasAudio -> DownloadedMediaPreparation.USE_AUDIO_FILE
    else -> DownloadedMediaPreparation.REJECT
}

@Singleton
class DownloadedAudioPreparer @Inject constructor() {
    fun prepare(
        sourceFile: File,
        outputDirectory: File,
        isCancelled: () -> Boolean,
    ): File {
        return when (downloadedMediaPreparation(inspectTracks(sourceFile))) {
            DownloadedMediaPreparation.USE_AUDIO_FILE -> sourceFile
            DownloadedMediaPreparation.EXTRACT_AUDIO_TRACK ->
                extractAudioTrack(sourceFile, outputDirectory, isCancelled)
            DownloadedMediaPreparation.REJECT ->
                throw IllegalArgumentException("El resultado de yt-dlp no contiene una pista de audio")
        }
    }

    private fun inspectTracks(file: File): DownloadedMediaTracks {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasAudio = false
            var hasVideo = false
            repeat(extractor.trackCount) { index ->
                when {
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true -> hasAudio = true
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("video/") == true -> hasVideo = true
                }
            }
            DownloadedMediaTracks(hasAudio = hasAudio, hasVideo = hasVideo)
        } finally {
            extractor.release()
        }
    }

    private fun extractAudioTrack(
        sourceFile: File,
        outputDirectory: File,
        isCancelled: () -> Boolean,
    ): File {
        require(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "No se pudo preparar la extracción de audio"
        }
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var completed = false
        var outputFile: File? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("El video no contiene una pista de audio")
            val audioFormat = extractor.getTrackFormat(audioTrack)
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val useWebM = audioMime == "audio/opus" || audioMime == "audio/vorbis"
            val destinationFile = File(
                outputDirectory,
                if (useWebM) "audio-extraido.webm" else "audio-extraido.m4a",
            )
            outputFile = destinationFile
            if (destinationFile.exists() && !destinationFile.delete()) {
                throw IllegalStateException("No se pudo reemplazar el audio temporal")
            }
            val mediaMuxer = MediaMuxer(
                destinationFile.absolutePath,
                if (useWebM) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                },
            )
            muxer = mediaMuxer
            val destinationTrack = mediaMuxer.addTrack(audioFormat)
            extractor.selectTrack(audioTrack)
            mediaMuxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(MAX_AUDIO_SAMPLE_BYTES)
            val bufferInfo = MediaCodec.BufferInfo()
            var wroteSample = false
            while (true) {
                if (isCancelled()) throw CancellationException("La descarga fue cancelada")
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sourceFlags = extractor.sampleFlags
                require(sourceFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "La pista de audio está cifrada y no puede conservarse"
                }
                bufferInfo.set(
                    0,
                    sampleSize,
                    extractor.sampleTime.coerceAtLeast(0),
                    if (sourceFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    },
                )
                mediaMuxer.writeSampleData(destinationTrack, buffer, bufferInfo)
                wroteSample = true
                extractor.advance()
            }
            require(wroteSample) {
                "No se pudo extraer el audio del video"
            }
            mediaMuxer.stop()
            muxerStarted = false
            require(destinationFile.length() > 0) {
                "La pista de audio extraída está vacía"
            }
            completed = true
            return destinationFile
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
            if (!completed) outputFile?.delete()
        }
    }

    private companion object {
        const val MAX_AUDIO_SAMPLE_BYTES = 2 * 1024 * 1024
    }
}

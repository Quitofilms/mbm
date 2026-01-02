package com.example.mbm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

class ExportEngine(private val context: Context) {

    interface ExportListener {
        fun onStart()
        fun onProgress(progress: Int)
        fun onCompleted(outputFile: File)
        fun onError(message: String)
    }

    fun performSmash(files: List<File>, outputFile: File, listener: ExportListener) {
        if (files.isEmpty()) {
            listener.onError("No moments found to smash.")
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())

        // Convert physical files into Media3 EditedMediaItems with explicit durations and audio handling
        val editedMediaItems = files.map { file ->
            val mediaItem = MediaItem.fromUri(file.absolutePath)
            val isImage = file.name.lowercase().endsWith(".jpg") || file.name.lowercase().endsWith(".jpeg")

            val builder = EditedMediaItem.Builder(mediaItem)

            if (isImage) {
                // Set images to display for 1 second (1,000,000 microseconds)
                builder.setDurationUs(1000000L)
                // Images must explicitly remove audio to prevent muxer initialization errors
                builder.setRemoveAudio(true)
            } else {
                // For videos, we keep the audio to maintain the 1-second clip sound
                builder.setRemoveAudio(false)
            }

            builder.build()
        }

        // Create a single sequence of all media items
        val sequence = EditedMediaItemSequence(editedMediaItems)

        // We set the composition to be experimental for image-to-video if needed,
        // but primarily we build the list of sequences.
        val composition = Composition.Builder(listOf(sequence))
            .build()

        // Explicitly set the video and audio mime types to ensure compatibility across segments
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                mainHandler.post { listener.onCompleted(outputFile) }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Log.e("MBM_EXPORT", "Transformer Error: ${exportException.message}")
                mainHandler.post { listener.onError(exportException.message ?: "Muxer/Pipeline Error") }
            }
        })

        try {
            listener.onStart()
            if (outputFile.exists()) {
                outputFile.delete()
            }

            transformer.start(composition, outputFile.absolutePath)

            // Poll for progress tracking
            val progressRunnable = object : Runnable {
                override fun run() {
                    val progressHolder = androidx.media3.transformer.ProgressHolder()
                    val state = transformer.getProgress(progressHolder)
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.post { listener.onProgress(progressHolder.progress) }
                        mainHandler.postDelayed(this, 500)
                    }
                }
            }
            mainHandler.post(progressRunnable)

        } catch (e: Exception) {
            Log.e("MBM_EXPORT", "Start Exception: ${e.message}")
            listener.onError("Failed to start transformer: ${e.message}")
        }
    }
}
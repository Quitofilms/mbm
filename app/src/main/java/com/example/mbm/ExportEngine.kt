package com.example.mbm

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File

class ExportEngine(private val context: Context) {

    interface ExportListener {
        fun onStart()
        fun onProgress(progress: Int)
        fun onCompleted(outputFile: File)
        fun onError(message: String)
    }

    // New Function: Handles the 1-second cut + rotation
    fun performSurgicalCut(uri: Uri, startMs: Long, rotationDegrees: Int, outputFile: File, listener: ExportListener) {
        val mainHandler = Handler(Looper.getMainLooper())

        val videoEffects = mutableListOf<Effect>()
        if (rotationDegrees != 0) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rotationDegrees.toFloat())
                    .build()
            )
        }

        val effects = Effects(
            ImmutableList.of<AudioProcessor>(),
            ImmutableList.copyOf(videoEffects)
        )

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(startMs + 1000)
                .build())
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                mainHandler.post { listener.onCompleted(outputFile) }
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                mainHandler.post { listener.onError(exportException.message ?: "Trim Error") }
            }
        })

        try {
            listener.onStart()
            if (outputFile.exists()) outputFile.delete()
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            listener.onError("Failed to start trim: ${e.message}")
        }
    }

    fun performSmash(files: List<File>, outputFile: File, listener: ExportListener) {
        if (files.isEmpty()) {
            listener.onError("No moments found to smash.")
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())

        val editedMediaItems = files.map { file ->
            val mediaItem = MediaItem.fromUri(file.absolutePath)
            val isImage = file.name.lowercase().endsWith(".jpg") || file.name.lowercase().endsWith(".jpeg")

            val builder = EditedMediaItem.Builder(mediaItem)

            if (isImage) {
                builder.setDurationUs(1000000L)
                builder.setRemoveAudio(true)
            } else {
                builder.setRemoveAudio(false)
            }

            builder.build()
        }

        val sequence = EditedMediaItemSequence(editedMediaItems)

        val composition = Composition.Builder(listOf(sequence))
            .build()

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

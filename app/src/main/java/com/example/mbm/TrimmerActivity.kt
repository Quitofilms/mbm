package com.example.mbm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.slider.Slider

@OptIn(UnstableApi::class)
class TrimmerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var timeSlider: Slider
    private lateinit var tvTimestamp: TextView
    private lateinit var btnConfirm: Button

    private var videoUri: Uri? = null
    private var videoDurationMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trimmer)

        playerView = findViewById(R.id.player_view)
        timeSlider = findViewById(R.id.time_slider)
        tvTimestamp = findViewById(R.id.tv_timestamp)
        btnConfirm = findViewById(R.id.btn_confirm_trim)

        videoUri = intent.getParcelableExtra("VIDEO_URI")

        setupPlayer()

        // Updated listener to prevent "snapping back" to 0
        timeSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                // Seek the player to the exact value of the slider
                player.seekTo(value.toLong())
                // Ensure the UI text reflects the current seek position
                updateTimestampText(value.toLong())
            }
        }

        btnConfirm.setOnClickListener {
            val startMs = timeSlider.value.toLong()
            val resultIntent = Intent()
            resultIntent.putExtra("START_MS", startMs)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        videoUri?.let {
            val mediaItem = MediaItem.fromUri(it)
            player.setMediaItem(mediaItem)
            player.prepare()
            // Confirmed: Autoplay is set to false
            player.playWhenReady = false
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    videoDurationMs = player.duration
                    // Logic Preserved: Ensure the slider can't go past the point where 1s is impossible
                    val maxStart = if (videoDurationMs > 1000) videoDurationMs - 1000 else 0
                    timeSlider.valueFrom = 0f
                    timeSlider.valueTo = maxStart.toFloat()

                    // Initial setup only happens once when the player is ready
                    if (timeSlider.value == 0f) {
                        timeSlider.value = 0f
                        updateTimestampText(0)
                    }
                }
            }
        })
    }

    private fun updateTimestampText(currentMs: Long) {
        val seconds = currentMs / 1000
        val millis = (currentMs % 1000) / 100
        tvTimestamp.text = String.format("Start at: %02d.%d seconds", seconds, millis)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
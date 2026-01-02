package com.example.mbm

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File

class FullscreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)

        val filePath = intent.getStringExtra("FILE_PATH") ?: return
        val dateStr = intent.getStringExtra("DATE_STR") ?: ""
        val file = File(filePath)

        val ivFullscreen = findViewById<ImageView>(R.id.iv_fullscreen)
        val vvFullscreen = findViewById<VideoView>(R.id.vv_fullscreen)
        val tvCaption = findViewById<TextView>(R.id.tv_fullscreen_caption)
        val btnClose = findViewById<ImageButton>(R.id.btn_fullscreen_close)

        btnClose.setOnClickListener { finish() }

        // Load Media
        if (filePath.endsWith(".mp4")) {
            ivFullscreen.visibility = View.GONE
            vvFullscreen.visibility = View.VISIBLE
            vvFullscreen.setVideoPath(filePath)
            vvFullscreen.setOnPreparedListener { it.isLooping = true }
            vvFullscreen.start()
        } else {
            vvFullscreen.visibility = View.GONE
            ivFullscreen.visibility = View.VISIBLE
            Glide.with(this).load(file).into(ivFullscreen)
        }

        // Load Caption from .txt file
        val captionFile = File(file.parent, "MBM_$dateStr.txt")
        if (captionFile.exists()) {
            tvCaption.text = captionFile.readText()
            tvCaption.visibility = View.VISIBLE
        } else {
            tvCaption.visibility = View.GONE
        }
    }
}
package com.example.mbm

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MediaPickerActivity : AppCompatActivity() {

    private lateinit var rvPicker: RecyclerView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_picker)

        rvPicker = findViewById(R.id.rv_media_picker)
        btnBack = findViewById(R.id.btn_picker_back)

        btnBack.setOnClickListener { finish() }

        loadDeviceMedia()
    }

    private fun loadDeviceMedia() {
        val mediaList = mutableListOf<Any>()
        val groupedMedia = LinkedHashMap<String, MutableList<Uri>>()
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )

        val selection = ("(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?) " +
                "AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} NOT LIKE ?")

        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "%Download/MBM%"
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC"

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val type = cursor.getInt(typeColumn)
                val dateTaken = cursor.getLong(dateColumn)
                val contentUri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }

                val dateKey = dateFormat.format(Date(dateTaken))
                if (!groupedMedia.containsKey(dateKey)) {
                    groupedMedia[dateKey] = mutableListOf()
                }
                groupedMedia[dateKey]?.add(contentUri)
            }
        }

        for ((date, uris) in groupedMedia) {
            mediaList.add(date)
            mediaList.addAll(uris)
        }

        setupRecyclerView(mediaList)
    }

    private fun setupRecyclerView(data: List<Any>) {
        // Fix: Explicitly typed lambda parameter (selectedUri: Uri)
        val adapter = MediaPickerAdapter(data) { selectedUri: Uri ->
            val resultIntent = Intent()
            resultIntent.data = selectedUri
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (data[position] is String) 3 else 1
            }
        }

        rvPicker.layoutManager = layoutManager
        rvPicker.adapter = adapter
    }
}
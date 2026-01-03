package com.example.mbm

import android.Manifest
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabBack: FloatingActionButton
    private lateinit var tvTitle: TextView
    private lateinit var btnForceSync: ImageButton

    private val fileDateFormatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
    private val STORAGE_PERMISSION_CODE = 101

    private var pendingDate: Date? = null
    private var selectedVideoUri: Uri? = null
    private var currentActionMode: ActionMode? = null
    private lateinit var calendarAdapter: CalendarAdapter
    private var tempCameraUri: Uri? = null

    private fun getVaultDirectory(): File {
        val folderId = intent.getStringExtra("FOLDER_ID") ?: "0001"
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mbmDir = File(downloads, "MBM/$folderId")
        // FIXED: Corrected reference from bmmDir to mbmDir
        if (!mbmDir.exists()) mbmDir.mkdirs()
        return mbmDir
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let { handleSelectedMedia(it) }
        }
    }

    private val captureMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            tempCameraUri?.let { handleSelectedMedia(it) }
        }
    }

    private val trimmerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val startMs = result.data?.getLongExtra("START_MS", 0L) ?: 0L
            val uri = selectedVideoUri
            val date = pendingDate
            if (uri != null && date != null) {
                trimAndSaveVideo(uri, date, startMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.rv_calendar_grid)
        fabBack = findViewById(R.id.fab_back_to_dashboard)
        tvTitle = findViewById(R.id.tv_journal_title)
        btnForceSync = findViewById(R.id.btn_force_sync)

        val folderId = intent.getStringExtra("FOLDER_ID") ?: "0001"
        val vaultDir = getVaultDirectory()

        // Read Title from name.txt in the vault directory
        val nameFile = File(vaultDir, "name.txt")
        val customName = if (nameFile.exists()) {
            nameFile.readText().trim()
        } else {
            "Journal $folderId"
        }
        tvTitle.text = customName.uppercase()

        fabBack.setOnClickListener { finish() }
        btnForceSync.setOnClickListener { forceVaultSync() }

        setupCalendarGrid()
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            sharedUri?.let { uri -> showDateSelectionForSharedMedia(uri) }
        }
    }

    private fun showDateSelectionForSharedMedia(uri: Uri) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }.time
            pendingDate = selectedDate
            handleSelectedMedia(uri)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        datePickerDialog.setTitle("Select Date for Shared Moment")
        datePickerDialog.show()
    }

    private fun forceVaultSync() {
        val vaultDir = getVaultDirectory()
        val files = vaultDir.listFiles() ?: emptyArray()
        val paths = files.map { it.absolutePath }.toTypedArray()
        Toast.makeText(this, "Refreshing Vault...", Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(this, paths, null) { _, _ ->
            runOnUiThread {
                setupCalendarGrid()
                Toast.makeText(this, "Vault Synced", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCalendarGrid() {
        val masterList = mutableListOf<Any>()
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        var lastMonth = -1
        while (calendar.get(Calendar.YEAR) == 2026) {
            val currentMonth = calendar.get(Calendar.MONTH)
            if (currentMonth != lastMonth) {
                masterList.add(monthFormat.format(calendar.time).uppercase())
                lastMonth = currentMonth
            }
            masterList.add(calendar.time.clone() as Date)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        calendarAdapter = CalendarAdapter(masterList, getVaultDirectory(),
            onDayClick = { date ->
                if (currentActionMode != null) {
                    toggleSelection(date)
                } else {
                    val dateStr = fileDateFormatter.format(date)
                    val mbmDir = getVaultDirectory()
                    val imgFile = File(mbmDir, "MBM_$dateStr.jpg")
                    val vidFile = File(mbmDir, "MBM_$dateStr.mp4")

                    if (imgFile.exists() || vidFile.exists()) {
                        val intent = Intent(this, FullscreenActivity::class.java)
                        intent.putExtra("FILE_PATH", if (vidFile.exists()) vidFile.absolutePath else imgFile.absolutePath)
                        intent.putExtra("DATE_STR", dateStr)
                        startActivity(intent)
                    } else {
                        if (PermissionVault.hasAllPermissions(this)) {
                            showSourceSelectionDialog(date)
                        } else {
                            PermissionVault.runManualCheck(this)
                        }
                    }
                }
            },
            onDayLongClick = { date ->
                if (currentActionMode == null) {
                    currentActionMode = startSupportActionMode(actionModeCallback)
                    toggleSelection(date)
                }
            }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = calendarAdapter
    }

    private fun showSourceSelectionDialog(date: Date) {
        pendingDate = date
        val options = arrayOf("Gallery", "Camera (Video)", "Camera (Photo)")
        AlertDialog.Builder(this)
            .setTitle("Add Moment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchGalleryPicker()
                    1 -> launchCamera(isImage = false)
                    2 -> launchCamera(isImage = true)
                }
            }
            .show()
    }

    private fun launchGalleryPicker() {
        val intent = Intent(this, MediaPickerActivity::class.java)
        pickMediaLauncher.launch(intent)
    }

    private fun launchCamera(isImage: Boolean) {
        val intent = if (isImage) Intent(MediaStore.ACTION_IMAGE_CAPTURE) else Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val tempFile = File(externalCacheDir, "temp_camera_media" + (if (isImage) ".jpg" else ".mp4"))
        tempCameraUri = FileProvider.getUriForFile(this, "${packageName}.provider", tempFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri)
        captureMediaLauncher.launch(intent)
    }

    private fun handleSelectedMedia(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: if (uri.toString().endsWith(".mp4")) "video/mp4" else "image/jpeg"
        if (mimeType.startsWith("video")) {
            selectedVideoUri = uri
            launchTrimmer(uri)
        } else {
            pendingDate?.let { date -> saveImageToVault(uri, date) }
        }
    }

    private fun launchTrimmer(uri: Uri) {
        val intent = Intent(this, TrimmerActivity::class.java)
        intent.putExtra("VIDEO_URI", uri)
        trimmerLauncher.launch(intent)
    }

    private fun trimAndSaveVideo(uri: Uri, date: Date, startMs: Long) {
        val dateStr = fileDateFormatter.format(date)
        val fileName = "MBM_$dateStr.mp4"
        val mbmDir = getVaultDirectory()
        val destFile = File(mbmDir, fileName)
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(startMs + 1000)
                .build())
            .build()
        val transformer = Transformer.Builder(this).build()
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                recyclerView.postDelayed({
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Surgical Cut Saved", Toast.LENGTH_SHORT).show()
                        setupCalendarGrid()
                    }
                }, 500)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, e: ExportException) {
                Log.e("MBM_DEBUG", "Trim failed: ${e.message}")
            }
        })
        try {
            if (destFile.exists()) {
                deleteExistingFromMediaStore(fileName, isVideo = true)
                destFile.delete()
            }
            transformer.start(mediaItem, destFile.absolutePath)
        } catch (e: Exception) { Log.e("MBM_DEBUG", "Start failed: ${e.message}") }
    }

    private fun saveImageToVault(uri: Uri, date: Date) {
        try {
            val fileName = "MBM_${fileDateFormatter.format(date)}.jpg"
            val destFile = File(getVaultDirectory(), fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            runOnUiThread { setupCalendarGrid() }
        } catch (e: Exception) { Log.e("MBM_DEBUG", "Image Error: ${e.message}") }
    }

    private fun deleteExistingFromMediaStore(fileName: String, isVideo: Boolean) {
        val resolver = contentResolver
        val uri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        try {
            resolver.delete(uri, selection, selectionArgs)
        } catch (e: Exception) { Log.e("MBM_DEBUG", "MediaStore cleanup failed: ${e.message}") }
    }

    private fun toggleSelection(date: Date) {
        calendarAdapter.toggleSelection(date)
        val count = calendarAdapter.getSelectedCount()
        if (count == 0) currentActionMode?.finish() else currentActionMode?.title = "$count selected"
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, 1, Menu.NONE, "Delete").setIcon(android.R.drawable.ic_menu_delete)
            menu.add(Menu.NONE, 2, Menu.NONE, "Share").setIcon(android.R.drawable.ic_menu_share)
            menu.add(Menu.NONE, 3, Menu.NONE, "Edit Caption").setIcon(android.R.drawable.ic_menu_edit)
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                1 -> showMultiDeleteConfirmation()
                2 -> shareSelectedMoments()
                3 -> showEditCaptionDialog()
            }
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) {
            calendarAdapter.clearSelection()
            currentActionMode = null
        }
    }

    private fun showEditCaptionDialog() {
        val dates = calendarAdapter.getSelectedDates()
        if (dates.size != 1) {
            Toast.makeText(this, "Select one day to edit caption", Toast.LENGTH_SHORT).show()
            return
        }
        val date = dates[0]
        val dateStr = fileDateFormatter.format(date)
        val captionFile = File(getVaultDirectory(), "MBM_$dateStr.txt")
        val input = EditText(this)
        if (captionFile.exists()) input.setText(captionFile.readText())

        AlertDialog.Builder(this)
            .setTitle("Edit Caption ($dateStr)")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                captionFile.writeText(input.text.toString())
                Toast.makeText(this, "Caption Saved", Toast.LENGTH_SHORT).show()
                currentActionMode?.finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareSelectedMoments() {
        val dates = calendarAdapter.getSelectedDates()
        val mbmDir = getVaultDirectory()
        val uris = arrayListOf<Uri>()
        dates.forEach { date ->
            val ds = fileDateFormatter.format(date)
            val imgFile = File(mbmDir, "MBM_$ds.jpg")
            val vidFile = File(mbmDir, "MBM_$ds.mp4")
            val fileToShare = if (vidFile.exists()) vidFile else if (imgFile.exists()) imgFile else null
            fileToShare?.let {
                uris.add(FileProvider.getUriForFile(this, "${packageName}.provider", it))
            }
        }
        if (uris.isNotEmpty()) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Moments"))
        }
        currentActionMode?.finish()
    }

    private fun showMultiDeleteConfirmation() {
        AlertDialog.Builder(this).setTitle("Delete moments?").setPositiveButton("Delete") { _, _ ->
            val dates = calendarAdapter.getSelectedDates()
            val mbmDir = getVaultDirectory()
            dates.forEach { date ->
                val ds = fileDateFormatter.format(date)
                deleteExistingFromMediaStore("MBM_$ds.jpg", false)
                deleteExistingFromMediaStore("MBM_$ds.mp4", true)
                File(mbmDir, "MBM_$ds.jpg").delete()
                File(mbmDir, "MBM_$ds.mp4").delete()
                File(mbmDir, "MBM_$ds.txt").delete()
            }
            setupCalendarGrid()
            currentActionMode?.finish()
        }.show()
    }
}
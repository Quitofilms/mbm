package com.example.mbm

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddJournal: FloatingActionButton
    private lateinit var btnGrantAccess: Button
    private lateinit var btnImportJournal: Button
    private lateinit var adapter: DashboardAdapter

    private lateinit var btnExportYear: Button
    private lateinit var btnExportMonth: Button
    private lateinit var btnExportCustom: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private var targetFolderForCover: File? = null

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleCoverSelection(it) }
    }

    private val importZipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let { importJournalFromZip(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        recyclerView = findViewById(R.id.rv_journals)
        fabAddJournal = findViewById(R.id.fab_add_journal)
        btnGrantAccess = findViewById(R.id.btn_grant_access)
        btnImportJournal = findViewById(R.id.btn_import_journal)

        btnExportYear = findViewById(R.id.btn_global_export_year)
        btnExportMonth = findViewById(R.id.btn_global_export_month)
        btnExportCustom = findViewById(R.id.btn_global_export_custom)
        progressBar = findViewById(R.id.pb_export_progress)
        tvStatus = findViewById(R.id.tv_export_status)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        fabAddJournal.setOnClickListener {
            createNewJournal()
        }

        btnImportJournal.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/zip"
            importZipLauncher.launch(intent)
        }

        btnGrantAccess.setOnClickListener {
            PermissionVault.runManualCheck(this)
        }

        btnExportYear.setOnClickListener { initiateSmash("YEAR") }
        btnExportMonth.setOnClickListener { initiateSmash("MONTH") }
        btnExportCustom.setOnClickListener {
            Toast.makeText(this, "Select a journal to smash custom range", Toast.LENGTH_SHORT).show()
        }

        setupDashboard()
    }

    private fun initiateSmash(timeframe: String) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mbmDir = File(downloads, "MBM")
        val allMediaFiles = mutableListOf<File>()

        mbmDir.listFiles { file -> file.isDirectory }?.forEach { folder ->
            folder.listFiles { f ->
                (f.name.endsWith(".mp4") || f.name.endsWith(".jpg")) && f.name.startsWith("MBM_")
            }?.let {
                allMediaFiles.addAll(it)
            }
        }

        val sortedFiles = allMediaFiles.sortedBy { it.name }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val movieFile = File(downloads, "MBM_SMASH_$timestamp.mp4")

        val engine = ExportEngine(this)
        engine.performSmash(sortedFiles, movieFile, object : ExportEngine.ExportListener {
            override fun onStart() {
                progressBar.visibility = View.VISIBLE
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "Starting Smash..."
            }

            override fun onProgress(progress: Int) {
                progressBar.progress = progress
                tvStatus.text = "Smashing: $progress%"
            }

            override fun onCompleted(outputFile: File) {
                progressBar.visibility = View.GONE
                tvStatus.text = "Smash Complete!"
                Toast.makeText(this@DashboardActivity, "Movie saved to Downloads", Toast.LENGTH_LONG).show()
                MediaScannerConnection.scanFile(this@DashboardActivity, arrayOf(outputFile.absolutePath), null, null)
            }

            override fun onError(message: String) {
                progressBar.visibility = View.GONE
                tvStatus.text = "Smash Failed: $message"
                Log.e("MBM_DEBUG", "Smash Error: $message")
            }
        })
    }

    private fun setupDashboard() {
        if (!PermissionVault.hasAllPermissions(this)) {
            recyclerView.visibility = View.GONE
            fabAddJournal.visibility = View.GONE
            btnGrantAccess.visibility = View.VISIBLE
            btnImportJournal.visibility = View.GONE
            return
        }

        recyclerView.visibility = View.VISIBLE
        fabAddJournal.visibility = View.VISIBLE
        btnImportJournal.visibility = View.VISIBLE
        btnGrantAccess.visibility = View.GONE

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mbmDir = File(downloads, "MBM")

        if (!mbmDir.exists()) mbmDir.mkdirs()

        try {
            val noMediaFile = File(mbmDir, ".nomedia")
            if (!noMediaFile.exists()) {
                noMediaFile.createNewFile()
            }
        } catch (e: Exception) {
            Log.e("MBM_DEBUG", "nomedia creation failed: ${e.message}")
        }

        val journalFolders = mbmDir.listFiles { file -> file.isDirectory }?.toList()
            ?.sortedBy { it.name } ?: emptyList()

        if (journalFolders.isEmpty()) {
            File(mbmDir, "0001").mkdirs()
            setupDashboard()
            return
        }

        val nameMap = journalFolders.associate { folder ->
            val nameFile = File(folder, "name.txt")
            val prettyName = if (nameFile.exists()) {
                nameFile.readText().trim()
            } else {
                ""
            }
            folder.name to prettyName
        }

        adapter = DashboardAdapter(journalFolders, nameMap,
            onFolderClick = { folder ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("FOLDER_ID", folder.name)
                startActivity(intent)
            },
            onFolderLongClick = { folder ->
                showJournalOptionsDialog(folder)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun showJournalOptionsDialog(folder: File) {
        val options = arrayOf("Rename", "Set Custom Cover", "Export Library (.zip)", "Export & Purge Journal", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Journal ${folder.name} Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(folder)
                    1 -> {
                        targetFolderForCover = folder
                        pickCoverLauncher.launch("image/*")
                    }
                    2 -> exportJournalAsZip(folder, purgeAfter = false)
                    3 -> exportJournalAsZip(folder, purgeAfter = true)
                    4 -> showDeleteConfirmation(folder)
                }
            }
            .show()
    }

    private fun handleCoverSelection(uri: Uri) {
        val folder = targetFolderForCover ?: return
        try {
            val coverFile = File(folder, "MBM_COVER.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(coverFile).use { output ->
                    input.copyTo(output)
                }
            }
            setupDashboard()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to set cover: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showRenameDialog(folder: File) {
        val input = EditText(this)
        val nameFile = File(folder, "name.txt")
        if (nameFile.exists()) {
            input.setText(nameFile.readText().trim())
        }
        input.setHint("Custom Journal Name")

        AlertDialog.Builder(this)
            .setTitle("Rename Journal ${folder.name}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                try {
                    val targetFile = File(folder, "name.txt")
                    FileOutputStream(targetFile).use { output ->
                        output.write(newName.toByteArray())
                    }
                } catch (e: Exception) {
                    Log.e("MBM_DEBUG", "Failed to save name.txt: ${e.message}")
                }
                setupDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportJournalAsZip(folder: File, purgeAfter: Boolean) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val zipFile = File(downloadsDir, "MBM_Backup_${folder.name}.zip")

        try {
            val fos = FileOutputStream(zipFile)
            val zos = ZipOutputStream(fos)
            val files = folder.listFiles() ?: emptyArray()

            for (file in files) {
                if (file.isFile) {
                    val fis = FileInputStream(file)
                    val zipEntry = ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                    fis.close()
                }
            }
            zos.close()
            fos.close()

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (purgeAfter) {
                AlertDialog.Builder(this)
                    .setTitle("Purge Journal?")
                    .setMessage("Zip created. Once you share/save this file, would you like to delete the local journal folder from your phone?")
                    .setPositiveButton("Share & Purge") { _, _ ->
                        startActivity(Intent.createChooser(shareIntent, "Share & Purge Backup"))
                        deleteJournalFolder(folder)
                    }
                    .setNegativeButton("Share Only") { _, _ ->
                        startActivity(Intent.createChooser(shareIntent, "Share Backup Zip"))
                    }
                    .show()
            } else {
                startActivity(Intent.createChooser(shareIntent, "Share Backup Zip"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importJournalFromZip(zipUri: Uri) {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mbmDir = File(downloads, "MBM")

        try {
            val inputStream = contentResolver.openInputStream(zipUri) ?: return
            val zipInputStream = ZipInputStream(inputStream)

            // Check for name.txt to validate MBM journal
            var isValidMbm = false
            var entry = zipInputStream.nextEntry
            val tempEntries = mutableListOf<Pair<String, ByteArray>>()

            while (entry != null) {
                if (entry.name == "name.txt") isValidMbm = true
                if (!entry.isDirectory) {
                    tempEntries.add(entry.name to zipInputStream.readBytes())
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (!isValidMbm) {
                Toast.makeText(this, "Invalid File: No MBM metadata found", Toast.LENGTH_LONG).show()
                return
            }

            // Determine next available folder ID
            val existingFolders = mbmDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()
            val nextId = (existingFolders.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
            val newFolderName = String.format("%04d", nextId)
            val newFolder = File(mbmDir, newFolderName)
            newFolder.mkdirs()

            // Extract entries to new folder
            tempEntries.forEach { (name, data) ->
                val outFile = File(newFolder, name)
                FileOutputStream(outFile).use { it.write(data) }
            }

            Toast.makeText(this, "Journal Imported as $newFolderName", Toast.LENGTH_SHORT).show()
            setupDashboard()

        } catch (e: Exception) {
            Log.e("MBM_DEBUG", "Import failed: ${e.message}")
            Toast.makeText(this, "Import Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteConfirmation(folder: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete Journal?")
            .setMessage("This will permanently delete the folder '${folder.name}' and all videos/images inside it.")
            .setPositiveButton("Delete Everything") { _, _ ->
                deleteJournalFolder(folder)
            }
            .setNegativeButton("Keep Journal", null)
            .show()
    }

    private fun deleteJournalFolder(folder: File) {
        try {
            val resolver = contentResolver
            val folderPath = "Download/MBM/${folder.name}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("$folderPath%")
                resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
                resolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            }

            folder.deleteRecursively()
            setupDashboard()
        } catch (e: Exception) {
            Log.e("MBM_DEBUG", "Error deleting: ${e.message}")
        }
    }

    private fun createNewJournal() {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mbmDir = File(downloads, "MBM")
        val existingFolders = mbmDir.listFiles { file -> file.isDirectory }?.map { it.name } ?: emptyList()
        val nextId = (existingFolders.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val nextFolderName = String.format("%04d", nextId)
        val newFolder = File(mbmDir, nextFolderName)
        if (!newFolder.exists()) {
            newFolder.mkdirs()
            setupDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        setupDashboard()
    }
}
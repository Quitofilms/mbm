package com.example.mbm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionVault {

    private const val PERMISSION_REQUEST_CODE = 1001

    /**
     * Checks if all necessary storage and camera permissions are granted.
     */
    fun hasAllPermissions(activity: Activity): Boolean {
        // Handle Android 11+ (API 30+) All Files Access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) return false
        }

        // Check standard permissions (Camera and Media)
        return getMissingStandardPermissions(activity).isEmpty()
    }

    /**
     * Triggered manually via button click to avoid the system race condition loop.
     */
    fun runManualCheck(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showManageStorageDialog(activity)
        } else {
            val missing = getMissingStandardPermissions(activity)
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        }
    }

    /**
     * Identifies permissions that haven't been granted yet based on API level.
     */
    private fun getMissingStandardPermissions(activity: Activity): List<String> {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses specific media permissions
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            // Android 12 and below use standard storage permissions
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showManageStorageDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Storage Access Required")
            .setMessage("To view and manage your journals, MBM needs 'All Files Access' to the Downloads folder.")
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
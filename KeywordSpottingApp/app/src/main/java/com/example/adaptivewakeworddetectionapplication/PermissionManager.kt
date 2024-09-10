package com.example.adaptivewakeworddetectionapplication

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: AppCompatActivity) {

    fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(vararg permissions: String) {
        ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}
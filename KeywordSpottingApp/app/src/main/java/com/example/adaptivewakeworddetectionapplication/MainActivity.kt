package com.example.adaptivewakeworddetectionapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentTransaction

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager
    private val PERMISSION_REQUEST_CODE = 1

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        permissionManager = PermissionManager(this)

        if (!permissionManager.hasPermissions(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)) {
            permissionManager.requestPermissions(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }

        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)

            if (currentFragment is SettingsFragment) {
                supportFragmentManager.popBackStack()
            } else {
                openSettingsFragment()
            }
        }
    }

    private fun openSettingsFragment() {
        val settingsFragment = SettingsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, settingsFragment)
            .addToBackStack(null)
            .commit()
    }

    fun returnToHomeFragment() {
        supportFragmentManager.popBackStack()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "$permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "$permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }
}
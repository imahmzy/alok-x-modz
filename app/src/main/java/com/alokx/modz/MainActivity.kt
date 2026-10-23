package com.alokx.modz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, grant ->
            status.text = "Shizuku permission: " +
                if (grant == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B12"))
            setPadding(56, 120, 56, 56)
        }

        status = TextView(this).apply {
            text = "Idle."
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        col.addView(status)

        col.addView(btn("1. Grant Shizuku") {
            if (!Shizuku.pingBinder()) {
                toast("Shizuku isn't running - open Shizuku first")
                return@btn
            }
            if (Shizuku.isPreV11()) {
                toast("Shizuku v11+ required")
                return@btn
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                toast("Already granted")
            } else {
                Shizuku.requestPermission(4242)
            }
        })

        col.addView(btn("2. Allow overlay") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                toast("Already allowed")
            }
        })

        col.addView(btn("3. Start mod") {
            startForegroundService(Intent(this, OverlayService::class.java))
            toast("Started - now open Free Fire")
        })

        col.addView(btn("Stop mod") {
            stopService(Intent(this, OverlayService::class.java))
        })

        col.addView(btn("Open Shizuku") {
            packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?.let { startActivity(it) } ?: toast("Shizuku not installed")
        })

        setContentView(col)
        Shizuku.addRequestPermissionResultListener(permListener)

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

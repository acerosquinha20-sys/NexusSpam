package com.nexus.spam

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var toggle: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK); setPadding(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 40), Ui.dp(this@MainActivity, 24), 0)
        }
        val logo = ImageView(this).apply { setImageResource(R.drawable.logo_app) }
        root.addView(logo, LinearLayout.LayoutParams(Ui.dp(this, 220), Ui.dp(this, 220)))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = Ui.neon(this@MainActivity, 24)
            setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18))
        }
        fun add(btn: Button, click: () -> Unit) {
            btn.setOnClickListener { click() }
            card.addView(btn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, Ui.dp(this@MainActivity, 12)) })
        }
        add(Ui.button(this, "1. Permitir sobreposição")) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        add(Ui.button(this, "2. Permitir acesso de uso (detectar TikTok)")) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        toggle = Ui.button(this, "ATIVAR FLUTUANTE")
        add(toggle) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Permita a sobreposição primeiro", Toast.LENGTH_SHORT).show()
            } else if (OverlayService.running) {
                stopService(Intent(this, OverlayService::class.java))
            } else startForegroundService(Intent(this, OverlayService::class.java))
            toggle.postDelayed({ refresh() }, 400)
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() { toggle.text = if (OverlayService.running) "DESATIVAR FLUTUANTE" else "ATIVAR FLUTUANTE" }

    companion object {
        fun hasUsageAccess(c: android.content.Context): Boolean {
            val ops = c.getSystemService(APP_OPS_SERVICE) as AppOpsManager
            return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
        }
    }
}

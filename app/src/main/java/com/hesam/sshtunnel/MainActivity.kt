package com.hesam.sshtunnel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var connectBtn: Button
    private lateinit var grantBtn: Button
    private lateinit var errorView: TextView
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        b.layoutParams = lp
        return b
    }

    private fun text(s: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#121212"))
        root.setPadding(dp(24), dp(56), dp(24), dp(24))
        root.gravity = Gravity.TOP

        root.addView(text("SSH Tunnel", 28f, Color.WHITE))
        statusView = text("Disconnected", 18f, Color.parseColor("#B0B0B0"))
        statusView.setPadding(0, dp(8), 0, dp(16))
        root.addView(statusView)

        connectBtn = button("Connect") { onConnectClick() }
        root.addView(connectBtn)
        root.addView(button("Ping") { Util.ping(this) })
        grantBtn = button("Grant file access") { ensureStorage() }
        root.addView(grantBtn)

        errorView = text("", 12f, Color.parseColor("#FF6B6B"))
        errorView.setPadding(0, dp(16), 0, 0)
        errorView.setTextIsSelectable(true)
        root.addView(errorView)

        val info = text(
            "Config file:\n" + Config.file.path +
                "\n\nEdit it with any text editor, then reconnect.\n\n" +
                "Add the \"SSH Tunnel\" and \"Ping\" tiles from the quick settings edit menu.",
            14f, Color.parseColor("#8A8A8A")
        )
        info.setPadding(0, dp(24), 0, 0)
        root.addView(info)

        val scroll = android.widget.ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#121212"))
        scroll.addView(root)
        setContentView(scroll)
        if (savedInstanceState == null) ensureStorage()
    }

    private fun hasStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun ensureStorage() {
        if (hasStorage()) return
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1
            )
        }
    }

    private fun onConnectClick() {
        if (TunnelVpnService.running) {
            startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_STOP))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 100) else startVpn()
    }

    private fun startVpn() {
        try {
            startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_START))
        } catch (e: Throwable) {
            TunnelVpnService.lastError = "Start failed: " + e
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) startVpn()
    }

    private fun refresh() {
        val st = TunnelVpnService.state
        statusView.text = st
        statusView.setTextColor(
            Color.parseColor(if (st == "Connected") "#4CAF50" else "#B0B0B0")
        )
        connectBtn.text = if (TunnelVpnService.running) "Disconnect" else "Connect"
        val crash = java.io.File(filesDir, "crash.txt")
        val crashText = if (crash.exists()) "Last crash:\n" + crash.readText() else ""
        errorView.text = if (TunnelVpnService.lastError.isNotEmpty()) "Error: " + TunnelVpnService.lastError
            else crashText
        val ok = hasStorage()
        grantBtn.visibility = if (ok) View.GONE else View.VISIBLE
        if (ok) Config.ensureFile()
    }

    override fun onResume() {
        super.onResume()
        h.post(tick)
    }

    override fun onPause() {
        h.removeCallbacks(tick)
        super.onPause()
    }
}

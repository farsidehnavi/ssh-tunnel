package com.hesam.sshtunnel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object Util {
    private val main = Handler(Looper.getMainLooper())

    fun toast(ctx: Context, msg: String) {
        val app = ctx.applicationContext
        main.post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
    }

    fun ping(ctx: Context) {
        Thread {
            val c = TunnelVpnService.client
            val msg = if (!TunnelVpnService.running || c == null || !c.connected) {
                "Not connected"
            } else {
                try {
                    val ms = c.ping()
                    c.cfg.pingHost + ": " + ms + " ms"
                } catch (e: Exception) {
                    "Ping failed: " + (e.message ?: "error")
                }
            }
            toast(ctx, msg)
        }.start()
    }
}

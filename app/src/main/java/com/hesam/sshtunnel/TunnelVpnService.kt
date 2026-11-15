package com.hesam.sshtunnel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import hev.htproxy.TProxyService
import java.io.File

class TunnelVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.hesam.sshtunnel.START"
        const val ACTION_STOP = "com.hesam.sshtunnel.STOP"

        @Volatile var running = false
        @Volatile var state = "Disconnected"
        @Volatile var client: SshClient? = null

        fun updateTiles(ctx: Context) {
            try {
                TileService.requestListeningState(ctx, ComponentName(ctx, ConnectTile::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var socks: SocksServer? = null
    private var worker: Thread? = null
    @Volatile private var alive = false
    private var hevStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            return START_NOT_STICKY
        }
        if (alive) return START_NOT_STICKY
        startFg()
        alive = true
        running = true
        state = "Connecting..."
        updateTiles(this)
        worker = Thread { work() }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vpn", "VPN status", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, TunnelVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "vpn")
            .setSmallIcon(R.drawable.ic_tile_lock)
            .setContentTitle("SSH Tunnel")
            .setContentText("Running")
            .setContentIntent(openPi)
            .addAction(0, "Disconnect", stopPi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun work() {
        try {
            val cfg = Config.load()
            val c = SshClient(cfg)
            c.connect()
            if (!alive) { c.close(); return }
            client = c

            val srv = SocksServer { client }
            srv.start()
            socks = srv

            establishTun()
            state = "Connected"
            updateTiles(this)
            Util.toast(this, "Connected")

            while (alive) {
                Thread.sleep(3000)
                if (!alive) break
                if (client?.connected != true) {
                    state = "Reconnecting..."
                    updateTiles(this)
                    try {
                        client?.close()
                        val n = SshClient(cfg)
                        n.connect()
                        client = n
                        state = "Connected"
                        updateTiles(this)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            if (alive) Util.toast(this, "Failed: " + (e.message ?: e.toString()))
        } finally {
            stopAll()
        }
    }

    private fun establishTun() {
        val b = Builder()
            .setSession("SSH Tunnel")
            .setMtu(1500)
            .addAddress("198.18.0.1", 32)
            .addAddress("fd00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("198.18.0.2")
        try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
        val t = b.establish() ?: throw IllegalStateException("VPN permission not granted")
        tun = t

        val f = File(filesDir, "hev.yaml")
        f.writeText(
            """tunnel:
  mtu: 1500
  ipv4: 198.18.0.1
socks5:
  port: 1080
  address: 127.0.0.1
mapdns:
  address: 198.18.0.2
  port: 53
  network: 100.64.0.0
  netmask: 255.192.0.0
  cache-size: 10000
misc:
  log-level: warn
"""
        )
        TProxyService.TProxyStartService(f.absolutePath, t.fd)
        hevStarted = true
    }

    @Synchronized
    private fun stopAll() {
        alive = false
        running = false
        state = "Disconnected"
        if (hevStarted) {
            try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
            hevStarted = false
        }
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        try { socks?.stop() } catch (_: Exception) {}
        socks = null
        try { client?.close() } catch (_: Exception) {}
        client = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        updateTiles(this)
    }

    override fun onRevoke() {
        stopAll()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}

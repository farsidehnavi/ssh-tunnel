package com.hesam.sshtunnel

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ConnectTile : TileService() {

    override fun onStartListening() {
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (TunnelVpnService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "SSH Tunnel"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile_lock)
        t.updateTile()
    }

    override fun onClick() {
        if (TunnelVpnService.running) {
            startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_STOP))
            return
        }
        if (VpnService.prepare(this) != null) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
            return
        }
        startForegroundService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_START))
    }
}

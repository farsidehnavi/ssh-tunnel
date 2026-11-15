package com.hesam.sshtunnel

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PingTile : TileService() {

    override fun onStartListening() {
        val t = qsTile ?: return
        t.state = Tile.STATE_INACTIVE
        t.label = "Ping"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile_ping)
        t.updateTile()
    }

    override fun onClick() {
        Util.ping(this)
    }
}

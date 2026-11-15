package com.hesam.sshtunnel

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

class SshClient(val cfg: Config) {

    class Conn(val channel: ChannelDirectTCPIP, val input: InputStream, val output: OutputStream) {
        fun close() {
            try { channel.disconnect() } catch (_: Exception) {}
        }
    }

    @Volatile private var session: Session? = null

    val connected: Boolean get() = session?.isConnected == true

    fun connect() {
        val jsch = JSch()
        if (cfg.privateKey.isNotEmpty()) {
            if (cfg.passphrase.isNotEmpty()) jsch.addIdentity(cfg.privateKey, cfg.passphrase)
            else jsch.addIdentity(cfg.privateKey)
        }
        val s = jsch.getSession(cfg.username, cfg.host, cfg.port)
        if (cfg.password.isNotEmpty()) s.setPassword(cfg.password)
        val ciphers = "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com"
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("cipher.c2s", ciphers)
        s.setConfig("cipher.s2c", ciphers)
        s.setConfig("compression.c2s", "none")
        s.setConfig("compression.s2c", "none")
        s.setConfig("PreferredAuthentications", "publickey,password")
        s.setServerAliveInterval(15000)
        s.setServerAliveCountMax(3)
        s.connect(10000)
        session = s
    }

    fun open(host: String, port: Int): Conn {
        val s = session ?: throw IllegalStateException("Not connected")
        val ch = s.openChannel("direct-tcpip") as ChannelDirectTCPIP
        ch.setHost(host)
        ch.setPort(port)
        val i = ch.inputStream
        val o = ch.outputStream
        ch.connect(10000)
        return Conn(ch, i, o)
    }

    /** Time (ms) to open a TCP connection to ping host through the SSH server. */
    fun ping(): Long {
        val t0 = System.nanoTime()
        val c = open(cfg.pingHost, cfg.pingPort)
        val ms = (System.nanoTime() - t0) / 1_000_000
        c.close()
        return ms
    }

    fun close() {
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
    }
}

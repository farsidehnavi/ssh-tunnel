package com.hesam.sshtunnel

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Minimal SOCKS5 server (CONNECT only). Every connection goes through the SSH session. */
class SocksServer(private val clientProvider: () -> SshClient?) {

    companion object {
        const val PORT = 1080
    }

    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 512)
        server = ss
        running = true
        pool.execute {
            while (running) {
                try {
                    val c = ss.accept()
                    pool.execute { handle(c) }
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun reply(out: OutputStream, code: Int) {
        out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun handle(c: Socket) {
        var conn: SshClient.Conn? = null
        try {
            c.tcpNoDelay = true
            val raw = c.getInputStream()
            val inp = DataInputStream(raw)
            val out = c.getOutputStream()

            if (inp.readUnsignedByte() != 5) return
            val n = inp.readUnsignedByte()
            inp.readFully(ByteArray(n))
            out.write(byteArrayOf(5, 0))
            out.flush()

            if (inp.readUnsignedByte() != 5) return
            val cmd = inp.readUnsignedByte()
            inp.readUnsignedByte()
            val atyp = inp.readUnsignedByte()
            val host: String = when (atyp) {
                1 -> {
                    val b = ByteArray(4); inp.readFully(b)
                    InetAddress.getByAddress(b).hostAddress ?: return
                }
                3 -> {
                    val l = inp.readUnsignedByte()
                    val b = ByteArray(l); inp.readFully(b)
                    String(b, Charsets.US_ASCII)
                }
                4 -> {
                    val b = ByteArray(16); inp.readFully(b)
                    InetAddress.getByAddress(b).hostAddress ?: return
                }
                else -> { reply(out, 8); return }
            }
            val port = inp.readUnsignedShort()
            if (cmd != 1) { reply(out, 7); return }

            conn = try { clientProvider()?.open(host, port) } catch (e: Exception) { null }
            if (conn == null) { reply(out, 5); return }
            reply(out, 0)

            val cin = conn.input
            val cout = conn.output
            val up = conn
            pool.execute {
                copy(raw, cout)
                up.close()
                try { c.close() } catch (_: Exception) {}
            }
            copy(cin, out)
        } catch (_: Exception) {
        } finally {
            conn?.close()
            try { c.close() } catch (_: Exception) {}
        }
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val buf = ByteArray(32768)
        try {
            while (true) {
                val n = i.read(buf)
                if (n < 0) break
                o.write(buf, 0, n)
                o.flush()
            }
        } catch (_: Exception) {
        }
    }
}

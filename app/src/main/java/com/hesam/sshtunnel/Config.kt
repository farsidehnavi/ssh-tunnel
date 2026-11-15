package com.hesam.sshtunnel

import android.os.Environment
import org.json.JSONObject
import java.io.File

data class Config(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val privateKey: String,
    val passphrase: String,
    val pingHost: String,
    val pingPort: Int
) {
    companion object {
        val dir = File(Environment.getExternalStorageDirectory(), "SSHTunnel")
        val file = File(dir, "config.json")

        private const val DEFAULT = """{
  "host": "",
  "port": 22,
  "username": "",
  "password": "",
  "private_key_path": "",
  "private_key_passphrase": "",
  "ping_host": "instagram.com",
  "ping_port": 443
}
"""

        fun ensureFile() {
            try {
                if (!file.exists()) {
                    dir.mkdirs()
                    file.writeText(DEFAULT)
                }
            } catch (_: Exception) {
            }
        }

        fun load(): Config {
            if (!file.exists()) {
                throw IllegalStateException("Config not found. Open the app, grant file access, then edit " + file.path)
            }
            val j = JSONObject(file.readText())
            val c = Config(
                host = j.optString("host", "").trim(),
                port = j.optInt("port", 22),
                username = j.optString("username", "").trim(),
                password = j.optString("password", ""),
                privateKey = j.optString("private_key_path", "").trim(),
                passphrase = j.optString("private_key_passphrase", ""),
                pingHost = j.optString("ping_host", "instagram.com").trim().ifEmpty { "instagram.com" },
                pingPort = j.optInt("ping_port", 443)
            )
            if (c.host.isEmpty() || c.username.isEmpty()) {
                throw IllegalStateException("Fill host and username in " + file.path)
            }
            return c
        }
    }
}

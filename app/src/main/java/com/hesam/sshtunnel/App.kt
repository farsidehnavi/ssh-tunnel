package com.hesam.sshtunnel

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.txt").writeText(sw.toString().take(3000))
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}

package com.botcelular.mu

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Guarda el último crash en un archivo propio, legible por MainActivity al
 * reabrir la app. Sin esto no hay forma de saber por qué se cerró — no
 * logramos conectar ADB a esta instancia de LDPlayer para ver logcat (ver
 * memoria del proyecto), así que la app tiene que reportarse sola.
 */
class BotApplication : Application() {

    companion object {
        const val CRASH_LOG_FILE = "last_crash.txt"
    }

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))
                File(filesDir, CRASH_LOG_FILE).writeText(writer.toString())
            } catch (e: Exception) {
                // Si ni siquiera esto funciona, no hay mucho más que hacer acá.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}

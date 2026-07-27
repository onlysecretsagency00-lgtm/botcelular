package com.botcelular.mu

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMPORAL: log de diagnóstico compartido entre MainActivity y
 * BotForegroundService (procesos/clases distintas dentro del mismo
 * proceso de la app) — sin logcat disponible en esta instancia de
 * LDPlayer, esto es lo único que deja ver qué pasa dentro del Service,
 * que no tiene UI propia para mostrar un Toast persistente.
 */
object DebugLog {
    @Volatile
    var text: String = ""
        private set

    @Synchronized
    fun add(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        text = "$time  $msg\n$text"
    }
}

package com.botcelular.mu

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Único responsable de tocar la pantalla — vía dispatchGesture(), el
 * mecanismo que Android permite para que una app inyecte gestos en OTRAS
 * apps (necesita que el usuario lo habilite a mano una vez en Ajustes >
 * Accesibilidad). Sin lógica de decisión acá: BotForegroundService es
 * quien decide QUÉ tocar, esto solo ejecuta el gesto.
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BotAccessibilityService"
        var instance: BotAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service conectado.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos leer contenido de pantalla vía accessibility
        // (canRetrieveWindowContent=false) — la detección la hace
        // BotForegroundService sobre los frames de MediaProjection.
    }

    override fun onInterrupt() {}

    fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

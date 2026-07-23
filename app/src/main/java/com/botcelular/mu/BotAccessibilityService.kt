package com.botcelular.mu

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Único responsable de tocar la pantalla — vía dispatchGesture(), el
 * mecanismo que Android permite para que una app inyecte gestos en OTRAS
 * apps (necesita que el usuario lo habilite a mano una vez en Ajustes >
 * Accesibilidad). Sin lógica de decisión acá: BotForegroundService es
 * quien decide QUÉ tocar, esto solo ejecuta el gesto.
 *
 * También dueña de la burbuja flotante ON/OFF (ver [showOverlay]) — un
 * servicio de accesibilidad puede agregar overlays de tipo
 * TYPE_ACCESSIBILITY_OVERLAY sin pedir el permiso "Mostrar sobre otras
 * apps" aparte, así que se reutiliza este mismo servicio en vez de sumar
 * un permiso nuevo.
 *
 * La burbuja SOLO pausa/reanuda el bot dentro de una sesión ya encendida
 * desde MainActivity — nunca pide el permiso de MediaProjection ella
 * misma (eso requeriría abrir una Activity, algo que resultó frágil e
 * inconsistente al probarlo en vivo). Ver BotForegroundService para el
 * porqué de esta separación isRunning/isPaused.
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BotAccessibilityService"
        private const val DRAG_CLICK_THRESHOLD_PX = 24
        private const val TAP_DEBOUNCE_MS = 800L
        var instance: BotAccessibilityService? = null
            private set
    }

    private var lastTapAtMs = 0L

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service conectado.")
        showOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
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

    // ── Burbuja flotante ON/OFF ──────────────────────────────────────────

    private fun showOverlay() {
        if (overlayView != null) return   // ya está mostrada

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val view = TextView(this).apply {
            text = "OFF"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        applyBubbleBackground(view, active = false)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            sizePx, sizePx, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 300
        }
        overlayParams = params

        view.setOnTouchListener(makeDragTouchListener(params, wm))

        try {
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo agregar la burbuja flotante", e)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error sacando la burbuja flotante", e)
        }
        overlayView = null
        overlayParams = null
    }

    /** Llamado por BotForegroundService cada vez que isPaused cambia de
     * verdad, para que el color/texto de la burbuja siempre refleje el
     * estado real (activo=ON verde, pausado o sesión apagada=OFF gris). */
    fun updateOverlayAppearance(active: Boolean) {
        val view = overlayView ?: return
        view.post {
            view.text = if (active) "ON" else "OFF"
            applyBubbleBackground(view, active)
        }
    }

    private fun applyBubbleBackground(view: TextView, active: Boolean) {
        val color = if (active) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun onBubbleTapped() {
        val now = System.currentTimeMillis()
        if (now - lastTapAtMs < TAP_DEBOUNCE_MS) return
        lastTapAtMs = now

        if (!BotForegroundService.isRunning) {
            // Todavía no se encendió la sesión desde la app — la burbuja no
            // puede pedir el permiso de MediaProjection por su cuenta.
            Toast.makeText(this, "Primero abrí BotCelular y tocá ENCENDER.", Toast.LENGTH_LONG).show()
            return
        }

        val action = if (BotForegroundService.isPaused) {
            BotForegroundService.ACTION_RESUME
        } else {
            BotForegroundService.ACTION_PAUSE
        }
        startService(Intent(this, BotForegroundService::class.java).apply { this.action = action })
    }

    private fun makeDragTouchListener(
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchStartX).toInt()
                    params.y = startY + (event.rawY - touchStartY).toInt()
                    try {
                        wm.updateViewLayout(v, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error moviendo la burbuja flotante", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val movedX = abs(event.rawX - touchStartX)
                    val movedY = abs(event.rawY - touchStartY)
                    if (movedX < DRAG_CLICK_THRESHOLD_PX && movedY < DRAG_CLICK_THRESHOLD_PX) {
                        v.performClick()
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }
    }
}

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
import kotlin.math.abs

/**
 * Único responsable de tocar la pantalla — vía dispatchGesture(), el
 * mecanismo que Android permite para que una app inyecte gestos en OTRAS
 * apps (necesita que el usuario lo habilite a mano una vez en Ajustes >
 * Accesibilidad). Sin lógica de decisión acá: BotForegroundService es
 * quien decide QUÉ tocar, esto solo ejecuta el gesto.
 *
 * También dueña de la burbuja flotante ENCENDER/APAGAR (ver [showOverlay])
 * — un servicio de accesibilidad puede agregar overlays de tipo
 * TYPE_ACCESSIBILITY_OVERLAY sin pedir el permiso "Mostrar sobre otras
 * apps" aparte, así que se reutiliza este mismo servicio en vez de sumar
 * un permiso nuevo.
 */
class BotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BotAccessibilityService"
        private const val DRAG_CLICK_THRESHOLD_PX = 12
        var instance: BotAccessibilityService? = null
            private set
    }

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

    // ── Burbuja flotante ENCENDER/APAGAR ────────────────────────────────

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
        applyBubbleBackground(view, running = false)

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

    /** Llamado por BotForegroundService cuando arranca/frena de verdad, para que el color/texto de la burbuja siempre refleje el estado real. */
    fun updateOverlayAppearance(running: Boolean) {
        val view = overlayView ?: return
        view.post {
            view.text = if (running) "ON" else "OFF"
            applyBubbleBackground(view, running)
        }
    }

    private fun applyBubbleBackground(view: TextView, running: Boolean) {
        val color = if (running) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun onBubbleTapped() {
        if (BotForegroundService.isRunning) {
            startService(Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_STOP
            })
            updateOverlayAppearance(false)
        } else {
            // Encender requiere el diálogo de consentimiento de MediaProjection,
            // que solo una Activity puede disparar — abrimos MainActivity con
            // un flag para que dispare ese flujo sola y vuelva a esconderse
            // (ver MainActivity.EXTRA_AUTO_START), sin que el usuario tenga
            // que tocar nada más ahí.
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_AUTO_START, true)
            })
        }
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

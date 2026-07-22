package com.botcelular.mu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dueño del loop de vida real del bot — equivalente Android de
 * general_bot.py::GeneralBot._loop(). Corre como foreground service
 * (obligatorio en Android para un servicio de larga duración, requiere
 * notificación persistente) mientras el toggle esté en ON.
 *
 * Captura frames vía MediaProjection (API de Android para grabar pantalla
 * dentro de la propia app — pide consentimiento del usuario cada vez que
 * arranca la sesión, no se puede evitar) y actúa tocando la pantalla vía
 * BotAccessibilityService.
 */
class BotForegroundService : Service() {

    companion object {
        private const val TAG = "BotForegroundService"
        private const val CHANNEL_ID = "bot_activo"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.botcelular.mu.action.START"
        const val ACTION_STOP = "com.botcelular.mu.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var loopJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData == null || resultCode == -1) {
                    Log.e(TAG, "Faltan datos de permiso de MediaProjection — no se puede arrancar.")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification())
                startCapture(resultCode, resultData)
            }
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // ── Captura ──────────────────────────────────────────────────────

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection = projection

        val reader = ImageReader.newInstance(screenWidth, screenHeight, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "botcelular-capture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null,
        )

        isRunning = true
        loopJob = serviceScope.launch { runLoop() }
        Log.i(TAG, "Captura iniciada ($screenWidth x $screenHeight).")
    }

    private fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888,
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } finally {
            image.close()
        }
    }

    // ── Loop de decisión (Fase 1 — solo pociones HP/MP) ────────────────

    private suspend fun runLoop() {
        while (isRunning) {
            val frame = captureBitmap()
            if (frame != null) {
                try {
                    decideAndAct(frame)
                } finally {
                    frame.recycle()
                }
            }
            delay(Config.TICK_INTERVAL_MS)
        }
    }

    private fun decideAndAct(frame: Bitmap) {
        val hpPct = HpMpReader.readPercent(
            frame, Config.hpBarX, Config.hpBarY, Config.hpBarWidth, Config.hpBarHeight,
            Config.hpColorR, Config.hpColorG, Config.hpColorB,
        )
        if (hpPct < Config.HP_THRESHOLD) {
            Log.i(TAG, "HP bajo (${(hpPct * 100).toInt()}%) — tap poción HP.")
            BotAccessibilityService.instance?.tap(Config.hpPotionButtonX, Config.hpPotionButtonY)
        }

        val mpPct = HpMpReader.readPercent(
            frame, Config.mpBarX, Config.mpBarY, Config.mpBarWidth, Config.mpBarHeight,
            Config.mpColorR, Config.mpColorG, Config.mpColorB,
        )
        if (mpPct < Config.MP_THRESHOLD) {
            Log.i(TAG, "MP bajo (${(mpPct * 100).toInt()}%) — tap poción MP.")
            BotAccessibilityService.instance?.tap(Config.mpPotionButtonX, Config.mpPotionButtonY)
        }
    }

    // ── Lifecycle / notificación ─────────────────────────────────────

    private fun stopEverything() {
        isRunning = false
        loopJob?.cancel()
        loopJob = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Bot activo")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}

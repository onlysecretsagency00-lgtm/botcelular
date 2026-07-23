package com.botcelular.mu

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activity invisible (tema transparente, ver Theme.Transparent) usada
 * SOLO por la burbuja flotante de BotAccessibilityService para encender
 * el bot sin abrir la UI normal de MainActivity. Dispara el diálogo de
 * consentimiento de MediaProjection (una Activity real es obligatoria
 * para eso, no hay forma de evitarlo) y se cierra sola apenas termina,
 * sin dejar nada propio visible en pantalla.
 */
class AutoStartActivity : ComponentActivity() {

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_START
                putExtra(BotForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(BotForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
        } else {
            Toast.makeText(this, "Permiso de captura de pantalla denegado.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
    }
}

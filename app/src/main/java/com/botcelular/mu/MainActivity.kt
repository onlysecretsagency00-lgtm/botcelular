package com.botcelular.mu

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botcelular.mu.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* si la niega, la notificación del foreground service puede no mostrarse — no bloqueante */ }

    // TEMPORAL: prueba de control para descartar si el problema es específico
    // de MediaProjection o algo más general con los diálogos de permiso en
    // este dispositivo — CAMERA sí tiene diálogo de runtime en API 28.
    private val requestCameraTest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        logDebug("resultado permiso cámara: $granted")
    }

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        logDebug("callback captura: resultCode=${result.resultCode} data=${result.data}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_START
                putExtra(BotForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(BotForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            updateStatus()
        } else {
            logDebug("permiso de captura de pantalla denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.textVersion.text = "v${BuildConfig.VERSION_NAME}"
        binding.buttonToggle.setOnClickListener { onToggleClicked() }
        binding.buttonCheckUpdate.setOnClickListener { checkForUpdate() }
        binding.buttonTestPermission.setOnClickListener {
            Toast.makeText(this, "pidiendo permiso de cámara...", Toast.LENGTH_SHORT).show()
            requestCameraTest.launch(android.Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        showLastCrashIfAny()
    }

    /** Muestra el último crash guardado por BotApplication (si hay uno) y lo
     * borra, para no repetirlo en próximas aperturas. Se suma a DebugLog en
     * vez de pisar el texto directamente, para no perderse con onResume(). */
    private fun showLastCrashIfAny() {
        val file = File(filesDir, BotApplication.CRASH_LOG_FILE)
        if (!file.exists()) return
        DebugLog.add("=== CRASH ===\n${file.readText()}")
        file.delete()
    }

    override fun onResume() {
        super.onResume()
        binding.textCrashLog.text = DebugLog.text
        updateStatus()
    }

    /** TEMPORAL: log de diagnóstico FIJO en pantalla (no como Toast, que se
     * cierra solo antes de que se pueda ver/capturar). Usa DebugLog (objeto
     * compartido) para poder mostrar también lo que loguea
     * BotForegroundService, que no tiene UI propia. */
    private fun logDebug(msg: String) {
        DebugLog.add(msg)
        binding.textCrashLog.text = DebugLog.text
    }

    private fun onToggleClicked() {
        logDebug("onToggleClicked (isRunning=${BotForegroundService.isRunning})")

        if (BotForegroundService.isRunning) {
            startService(Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_STOP
            })
            updateStatus()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            logDebug("falta accesibilidad, abriendo Ajustes")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val captureIntent = projectionManager.createScreenCaptureIntent()
        // TEMPORAL: diagnóstico — ¿el sistema tiene siquiera un componente
        // que resuelva este intent? Si es null, el problema es que el
        // componente de consentimiento de MediaProjection no existe/no
        // resuelve en esta imagen de Android, no algo de nuestro código.
        val resolved = captureIntent.resolveActivity(packageManager)
        logDebug("componente resuelto: $resolved")

        try {
            logDebug("lanzando pedido de captura...")
            requestScreenCapture.launch(captureIntent)
        } catch (e: Exception) {
            logDebug("launch() falló: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, BotAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun updateStatus() {
        val running = BotForegroundService.isRunning
        binding.textStatus.text = if (running) "Sesión: ACTIVA" else "Sesión: APAGADA"
        binding.buttonToggle.text = if (running) "APAGAR" else "ENCENDER"
        binding.textPermissions.text = when {
            !isAccessibilityServiceEnabled() -> "Falta habilitar el servicio de accesibilidad."
            running -> "Listo — usá el círculo flotante para activar/pausar el bot."
            else -> ""
        }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            binding.buttonCheckUpdate.isEnabled = false
            val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
            binding.buttonCheckUpdate.isEnabled = true
            if (update == null) {
                Toast.makeText(this@MainActivity, "Ya tenés la última versión.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (!canInstallUnknownApps()) {
                Toast.makeText(
                    this@MainActivity,
                    "Habilitá 'instalar apps desconocidas' para esta app y volvé a intentar.",
                    Toast.LENGTH_LONG,
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return@launch
            }
            Toast.makeText(this@MainActivity, "Descargando versión ${update.versionName}...", Toast.LENGTH_SHORT).show()
            UpdateChecker.downloadAndInstall(this@MainActivity, update) {}
        }
    }

    private fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }
}

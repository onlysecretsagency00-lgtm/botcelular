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
            updateStatus()
        } else {
            Toast.makeText(this, "Permiso de captura de pantalla denegado.", Toast.LENGTH_LONG).show()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        showLastCrashIfAny()
    }

    /** Muestra el último crash guardado por BotApplication (si hay uno) y lo
     * borra, para no repetirlo en próximas aperturas. */
    private fun showLastCrashIfAny() {
        val file = File(filesDir, BotApplication.CRASH_LOG_FILE)
        if (!file.exists()) return
        binding.textCrashLog.text = "Último error:\n\n${file.readText()}"
        file.delete()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun onToggleClicked() {
        if (BotForegroundService.isRunning) {
            startService(Intent(this, BotForegroundService::class.java).apply {
                action = BotForegroundService.ACTION_STOP
            })
            updateStatus()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "Habilitá 'BotCelular' en Ajustes > Accesibilidad, después volvé a tocar ENCENDER.",
                Toast.LENGTH_LONG,
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
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
        binding.textStatus.text = if (running) "Bot: ENCENDIDO" else "Bot: APAGADO"
        binding.buttonToggle.text = if (running) "APAGAR" else "ENCENDER"
        binding.textPermissions.text = if (!isAccessibilityServiceEnabled()) {
            "Falta habilitar el servicio de accesibilidad."
        } else {
            ""
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

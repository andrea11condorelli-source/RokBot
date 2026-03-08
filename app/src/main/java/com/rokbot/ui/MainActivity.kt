package com.rokbot.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rokbot.model.BotConfig
import com.rokbot.model.ResourceMode
import com.rokbot.model.ResourceType
import com.rokbot.service.BotForegroundService
import com.rokbot.service.RokAccessibilityService
import com.rokbot.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var projManager: MediaProjectionManager

    // Launcher per richiedere il permesso MediaProjection
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startBotService(result.resultCode, result.data!!)
        } else {
            toast("Permesso MediaProjection negato")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    private fun setupUI() {
        binding.logTextView.movementMethod = ScrollingMovementMethod()

        // Pulsante START
        binding.btnStart.setOnClickListener {
            when {
                !RokAccessibilityService.isConnected -> {
                    toast("Abilita AccessibilityService prima!")
                    openAccessibilitySettings()
                }
                BotForegroundService.isRunning -> toast("Bot già in esecuzione")
                else -> requestProjectionPermission()
            }
        }

        // Pulsante STOP
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, BotForegroundService::class.java))
            appendLog("Bot fermato dall'utente")
            updateStatusUI()
        }

        // Pulsante Accessibility Settings
        binding.btnAccessibility.setOnClickListener { openAccessibilitySettings() }

        // Selezione risorsa
        binding.radioGroupResource.setOnCheckedChangeListener { _, _ -> /* salva config */ }
    }

    private fun requestProjectionPermission() {
        projectionLauncher.launch(projManager.createScreenCaptureIntent())
    }

    private fun startBotService(resultCode: Int, data: Intent) {
        val serviceIntent = BotForegroundService.buildIntent(this, resultCode, data)
        startForegroundService(serviceIntent)
        appendLog("Bot avviato")
        updateStatusUI()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateStatusUI() {
        val accOk = RokAccessibilityService.isConnected
        val botRunning = BotForegroundService.isRunning
        binding.tvStatusAccessibility.text = if (accOk) "✅ Accessibility OK" else "❌ Accessibility OFF"
        binding.tvStatusBot.text = if (botRunning) "🟢 Bot ATTIVO" else "🔴 Bot FERMO"
        binding.btnStart.isEnabled = !botRunning
        binding.btnStop.isEnabled = botRunning
    }

    private fun appendLog(msg: String) {
        binding.logTextView.append("\n$msg")
        // Scroll to bottom
        val scrollAmount = binding.logTextView.layout?.getLineTop(binding.logTextView.lineCount) ?: 0
        binding.logTextView.scrollTo(0, scrollAmount)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

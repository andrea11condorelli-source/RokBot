package com.rokbot.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rokbot.cv.TemplateMatcher
import com.rokbot.model.BotConfig
import com.rokbot.ocr.MarchCounter
import com.rokbot.ui.MainActivity
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader

private const val TAG = "BotFgService"
private const val NOTIF_CHANNEL = "rok_bot_channel"
private const val NOTIF_ID = 1001
private const val ACTION_STOP = "com.rokbot.STOP"

/**
 * BotForegroundService
 * Avviato da MainActivity con il risultato di MediaProjection.
 * Gestisce il ciclo di vita del bot e della notifica persistente.
 */
class BotForegroundService : Service() {

    private var botEngine: BotEngine? = null
    private var screenCapture: ScreenCapture? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        var isRunning = false
            private set

        fun buildIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, BotForegroundService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("result_data", data)
            }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Inizializza OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV init fallito!")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBot()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("result_code", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val resultData = intent.getParcelableExtra<Intent>("result_data") ?: return START_NOT_STICKY

        startForeground(NOTIF_ID, buildNotification("Avvio..."))
        isRunning = true

        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection = pm.getMediaProjection(resultCode, resultData)

        val capture = ScreenCapture(this, projection).also {
            it.init()
            screenCapture = it
        }

        val config = BotConfig() // default; verrà passata da UI
        val engine = BotEngine(
            config = config,
            screenCapture = capture,
            templateMatcher = TemplateMatcher(this),
            marchCounter = MarchCounter(),
            onLog = { msg -> updateNotification(msg) }
        ).also { botEngine = it }

        serviceScope.launch { engine.start() }
        return START_NOT_STICKY
    }

    private fun stopBot() {
        botEngine?.stop()
        screenCapture?.release()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBot()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notifica ──────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "ROK Bot", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "ROK Auto Gather Bot" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BotForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ROK Bot attivo")
            .setContentText(status)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(status.take(60)))
    }
}

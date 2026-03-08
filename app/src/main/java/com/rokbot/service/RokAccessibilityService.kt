package com.rokbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.sin
import kotlin.random.Random

/**
 * RokAccessibilityService
 * Espone metodi per eseguire click e gesture con percorso Bezier (anti-ban).
 * Singleton accessibile da BotForegroundService via companion object.
 */
class RokAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RokAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── Click singolo con offset casuale ──────────────────────────────────────

    suspend fun click(x: Int, y: Int): Boolean {
        val jitterX = x + Random.nextInt(-4, 5)
        val jitterY = y + Random.nextInt(-4, 5)
        val path = Path().apply { moveTo(jitterX.toFloat(), jitterY.toFloat()) }
        return dispatchGesture(path, duration = Random.nextLong(80, 140))
    }

    // ── Gesture curva Bezier (anti-ban) ───────────────────────────────────────

    /**
     * Genera un percorso curvo da (startX,startY) a (endX,endY)
     * simulando un movimento umano con leggera ondulazione.
     */
    suspend fun swipeBezier(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        durationMs: Long = 400
    ): Boolean {
        val path = buildBezierPath(startX, startY, endX, endY)
        return dispatchGesture(path, durationMs)
    }

    private fun buildBezierPath(x0: Int, y0: Int, x1: Int, y1: Int): Path {
        val steps = 20
        val cpX = ((x0 + x1) / 2f) + Random.nextFloat() * 80 - 40
        val cpY = ((y0 + y1) / 2f) + Random.nextFloat() * 80 - 40
        val path = Path()
        path.moveTo(x0.toFloat(), y0.toFloat())
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val px = (1 - t).pow2() * x0 + 2 * (1 - t) * t * cpX + t.pow2() * x1
            val py = (1 - t).pow2() * y0 + 2 * (1 - t) * t * cpY + t.pow2() * y1
            // Piccola ondulazione sinusoidale
            val wave = sin(t * Math.PI * 3).toFloat() * 3f
            path.lineTo(px + wave, py + wave)
        }
        return path
    }

    private fun Float.pow2() = this * this

    // ── Dispatcher interno ────────────────────────────────────────────────────

    private suspend fun dispatchGesture(path: Path, duration: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
}

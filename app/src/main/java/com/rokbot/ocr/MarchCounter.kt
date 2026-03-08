package com.rokbot.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MarchCounter
 * Legge il contatore marce (es. "4/5") dal lato destro dello schermo.
 * Ritaglia la ROI prima di passare a ML Kit per velocità e accuratezza.
 */
class MarchCounter {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Analizza lo screenshot, ritaglia la ROI in alto a destra dove appare "X/Y",
     * ritorna (current, max) o null se non trovato.
     */
    suspend fun readMarches(screenshot: Bitmap): Pair<Int, Int>? {
        // ROI: angolo in alto a destra ~15% larghezza, ~8% altezza
        val roiRect = getRoiRect(screenshot)
        val roi = Bitmap.createBitmap(screenshot, roiRect.left, roiRect.top, roiRect.width(), roiRect.height())

        val text = runOcr(roi)
        roi.recycle()

        return parseMarches(text)
    }

    private fun getRoiRect(bmp: Bitmap): Rect {
        val w = bmp.width
        val h = bmp.height
        // Il contatore marce in ROK è circa: destra 12%, top 4-12%
        return Rect(
            (w * 0.78).toInt(),
            (h * 0.03).toInt(),
            (w * 0.98).toInt(),
            (h * 0.14).toInt()
        )
    }

    private suspend fun runOcr(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    /**
     * Estrae "X/Y" dal testo OCR.
     * Gestisce artefatti comuni: "4 /5", "4|5", "4-5"
     */
    internal fun parseMarches(text: String): Pair<Int, Int>? {
        val clean = text.replace(" ", "").replace("|", "/").replace("-", "/")
        val regex = Regex("""(\d+)/(\d+)""")
        val match = regex.find(clean) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val max = match.groupValues[2].toIntOrNull() ?: return null
        return Pair(current, max)
    }

    fun close() = recognizer.close()
}

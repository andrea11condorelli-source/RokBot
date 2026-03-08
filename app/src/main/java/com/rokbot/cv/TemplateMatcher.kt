package com.rokbot.cv

import android.content.Context
import android.graphics.Bitmap
import com.rokbot.model.BotConfig
import com.rokbot.model.MatchResult
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/**
 * TemplateMatcher
 * Usa OpenCV matchTemplate per trovare i pulsanti sullo screenshot.
 * Supporta multi-scale matching per gestire differenze di risoluzione.
 *
 * Template sizes (dal dispositivo sorgente):
 *   Grandi  : cerca(137x53), raccogli(212x81), nuove_truppe(193x66), marcia(252x31)
 *   Medi    : icone risorse (~120x130), lente_blu(75x83)
 *   Piccoli : livello_meno(28x29), livello_piu(30x29), help(49x44)
 *   Banner  : non_disponibile_*(515-582 x 68-80)
 */
class TemplateMatcher(private val context: Context) {

    private val templateCache = mutableMapOf<String, Mat>()

    // Template che richiedono threshold ridotto per le dimensioni piccole
    private val smallTemplates = setOf("livello_meno", "livello_piu", "help")
    private val iconTemplates = setOf("icona_grano", "icona_legna", "icona_pietra", "icona_oro", "lente_blu")

    fun getThresholdFor(name: String, config: BotConfig): Double = when {
        smallTemplates.any { name.contains(it) } -> config.templateMatchThresholdSmall
        iconTemplates.any { name.contains(it) } -> config.templateMatchThresholdIcons
        else -> config.templateMatchThreshold
    }

    private fun loadTemplate(name: String): Mat? {
        templateCache[name]?.let { return it }
        return try {
            val stream = context.assets.open("templates/$name.png")
            val bytes = stream.readBytes()
            stream.close()
            val data = MatOfByte(*bytes)
            val mat = Imgcodecs.imdecode(data, Imgcodecs.IMREAD_COLOR)
            if (mat.empty()) null else mat.also { templateCache[name] = it }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cerca [templateName] dentro [screenshot].
     * Se useMultiScale=true, prova scale 0.8x–1.2x per gestire DPI diversi.
     */
    fun find(screenshot: Bitmap, templateName: String, threshold: Double = 0.80,
             useMultiScale: Boolean = false): MatchResult {
        val template = loadTemplate(templateName) ?: return MatchResult(false)

        val srcMat = Mat()
        Utils.bitmapToMat(screenshot, srcMat)
        val srcBgr = Mat()
        Imgproc.cvtColor(srcMat, srcBgr, Imgproc.COLOR_RGBA2BGR)
        srcMat.release()

        if (!useMultiScale) {
            return runMatch(srcBgr, template, threshold).also { srcBgr.release() }
        }

        // Multi-scale: prova 5 scale tra 0.8x e 1.2x
        var best = MatchResult(false)
        val scales = listOf(0.80, 0.90, 1.00, 1.10, 1.20)
        for (scale in scales) {
            val scaledTemplate = Mat()
            val newW = (template.cols() * scale).toInt()
            val newH = (template.rows() * scale).toInt()
            if (newW < 5 || newH < 5) continue
            Imgproc.resize(template, scaledTemplate, Size(newW.toDouble(), newH.toDouble()))
            val r = runMatch(srcBgr, scaledTemplate, threshold)
            scaledTemplate.release()
            if (r.found && r.confidence > best.confidence) best = r
        }
        srcBgr.release()
        return best
    }

    private fun runMatch(src: Mat, tmpl: Mat, threshold: Double): MatchResult {
        if (src.cols() < tmpl.cols() || src.rows() < tmpl.rows()) return MatchResult(false)
        val result = Mat()
        Imgproc.matchTemplate(src, tmpl, result, Imgproc.TM_CCOEFF_NORMED)
        val mmr = Core.minMaxLoc(result)
        result.release()
        return if (mmr.maxVal >= threshold) {
            MatchResult(true, mmr.maxLoc.x.toInt(), mmr.maxLoc.y.toInt(),
                tmpl.cols(), tmpl.rows(), mmr.maxVal)
        } else MatchResult(false, confidence = mmr.maxVal)
    }

    fun findFirst(screenshot: Bitmap, vararg templateNames: String,
                  threshold: Double = 0.80): Pair<String?, MatchResult> {
        for (name in templateNames) {
            val r = find(screenshot, name, threshold)
            if (r.found) return Pair(name, r)
        }
        return Pair(null, MatchResult(false))
    }

    fun clearCache() {
        templateCache.values.forEach { it.release() }
        templateCache.clear()
    }
}

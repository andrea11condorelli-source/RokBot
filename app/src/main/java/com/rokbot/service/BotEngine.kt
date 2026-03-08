package com.rokbot.service

import android.graphics.Bitmap
import android.util.Log
import com.rokbot.cv.TemplateMatcher
import com.rokbot.model.BotConfig
import com.rokbot.model.MatchResult
import com.rokbot.model.ResourceMode
import com.rokbot.model.ResourceType
import com.rokbot.ocr.MarchCounter
import kotlinx.coroutines.*
import kotlin.random.Random

private const val TAG = "BotEngine"

/**
 * BotEngine
 * Contiene tutta la logica del bot:
 * - Loop principale (marce + sequenza gather)
 * - Loop secondario HELP (ogni 3s)
 * - Anti-ban delays e click randomizzati
 */
class BotEngine(
    private val config: BotConfig,
    private val screenCapture: ScreenCapture,
    private val templateMatcher: TemplateMatcher,
    private val marchCounter: MarchCounter,
    private val onLog: (String) -> Unit = {}
) {
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var resourceIndex = 0
    private val resourceCycle = ResourceType.values()

    // ── Avvio / Stop ──────────────────────────────────────────────────────────

    fun start() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch { helpLoop() }
        scope.launch { mainLoop() }
        log("Bot avviato")
    }

    fun stop() {
        scope.cancel()
        log("Bot fermato")
    }

    val isRunning: Boolean get() = scope.isActive

    // ── Loop principale ───────────────────────────────────────────────────────

    private suspend fun mainLoop() {
        while (isActive) {
            val screenshot = screenCapture.capture() ?: run {
                delay(1_000); return@run null
            } ?: continue

            val marchResult = marchCounter.readMarches(screenshot)
            if (marchResult == null) {
                log("Contatore marce non leggibile, riprovo...")
                delay(randomMarchInterval())
                continue
            }

            val (current, max) = marchResult
            log("Marce: $current/$max")

            val freeMarches = max - current
            if (freeMarches > 0) {
                log("$freeMarches marce libere → avvio gather")
                repeat(freeMarches) {
                    if (!isActive) return
                    val resource = nextResource()
                    runGatherSequence(resource)
                    // Delay anti-ban tra sequenze consecutive
                    antiBanDelay()
                }
            } else {
                log("Nessuna marcia libera, attendo...")
            }

            delay(randomMarchInterval())
        }
    }

    // ── Sequenza gather per una risorsa ──────────────────────────────────────

    private suspend fun runGatherSequence(resource: ResourceType) {
        log("Sequenza gather: ${resource.displayName}")
        val scr = screenshot() ?: return

        // 1. Lente blu (apri ricerca risorse)
        if (!clickTemplate(scr, "lente_blu")) { log("lente_blu non trovata"); return }
        antiBanDelay()

        // 2. Icona risorsa
        val resourceScr = screenshot() ?: return
        if (!clickTemplate(resourceScr, resource.templatePrefix)) {
            log("${resource.templatePrefix} non trovata"); return
        }
        antiBanDelay()

        // 3. Pulsante CERCA con gestione livello slider
        if (!clickCercaWithLevelAdjust(resource)) { log("CERCA fallito"); return }
        antiBanDelay()

        // 4. RACCOGLI
        val rcScr = screenshot() ?: return
        if (!clickTemplate(rcScr, "raccogli")) { log("raccogli non trovato"); return }
        antiBanDelay()

        // 5. NUOVE TRUPPE
        val ntScr = screenshot() ?: return
        if (!clickTemplate(ntScr, "nuove_truppe")) { log("nuove_truppe non trovato"); return }
        antiBanDelay()

        // 6. MARCIA
        val mScr = screenshot() ?: return
        if (!clickTemplate(mScr, "marcia")) { log("marcia non trovato") }

        log("Sequenza ${resource.displayName} completata")
    }

    /**
     * Clicca CERCA abbassando il livello se appare "non disponibile".
     * Livello iniziale: config.startLevel → abbassa fino a 1.
     */
    private suspend fun clickCercaWithLevelAdjust(resource: ResourceType): Boolean {
        var level = config.startLevel

        repeat(config.startLevel) { attempt ->
            val scr = screenshot() ?: return false

            // Controlla se "non disponibile" è visibile
            val ndResult = templateMatcher.find(scr, resource.noAvailableTemplate(), config.templateMatchThreshold)
            if (ndResult.found) {
                log("Livello $level non disponibile, abbasso")
                val ndScr = screenshot() ?: return false
                if (!clickTemplate(ndScr, "livello_meno")) return false
                level--
                delay(800)
                return@repeat
            }

            // Clicca CERCA
            val cercaResult = templateMatcher.find(scr, "cerca", config.templateMatchThreshold)
            if (cercaResult.found) {
                performClick(cercaResult)
                return true
            }
        }
        return false
    }

    // ── Loop HELP (ogni 3s, parallelo) ───────────────────────────────────────

    private suspend fun helpLoop() {
        while (isActive) {
            delay(config.helpCheckIntervalMs)
            val scr = screenshot() ?: continue
            val helpResult = templateMatcher.find(scr, "help", config.templateMatchThreshold)
            if (helpResult.found) {
                log("HELP trovato, click")
                performClick(helpResult)
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private suspend fun screenshot(): Bitmap? {
        val bmp = screenCapture.capture()
        if (bmp == null) log("Screenshot fallito")
        return bmp
    }

    private suspend fun clickTemplate(screenshot: Bitmap, templateName: String): Boolean {
        val result = templateMatcher.find(screenshot, templateName, config.templateMatchThreshold)
        if (!result.found) {
            log("Template '$templateName' non trovato (threshold: ${config.templateMatchThreshold})")
            return false
        }
        performClick(result)
        return true
    }

    private suspend fun performClick(match: MatchResult) {
        val (x, y) = match.randomPoint()
        val accessibility = RokAccessibilityService.instance ?: run {
            log("ERRORE: AccessibilityService non connesso!")
            return
        }
        // Pausa pre-click (0.6–1.6s) anti-ban
        delay(Random.nextLong(600, 1_600))
        accessibility.click(x, y)
        log("Click su ($x, $y) conf=${String.format("%.2f", match.confidence)}")
    }

    private suspend fun antiBanDelay() {
        val delay = Random.nextLong(config.minClickDelayMs, config.maxClickDelayMs)
        log("Anti-ban delay: ${delay}ms")
        delay(delay)
    }

    private fun randomMarchInterval() =
        Random.nextLong(config.marchCheckIntervalMinMs, config.marchCheckIntervalMaxMs)

    private fun nextResource(): ResourceType {
        return if (config.resourceMode == ResourceMode.SINGLE) {
            config.singleResource
        } else {
            val r = resourceCycle[resourceIndex % resourceCycle.size]
            resourceIndex++
            r
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog(msg)
    }
}

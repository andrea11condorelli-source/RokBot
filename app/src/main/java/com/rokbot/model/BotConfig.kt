package com.rokbot.model

enum class ResourceType(val templatePrefix: String, val displayName: String) {
    GRAIN("icona_grano", "Grano"),
    WOOD("icona_legna", "Legna"),
    STONE("icona_pietra", "Pietra"),
    GOLD("icona_oro", "Oro");

    fun noAvailableTemplate() = "non_disponibile_${templatePrefix.removePrefix("icona_")}"
}

data class BotConfig(
    val resourceMode: ResourceMode = ResourceMode.MIXED,
    val singleResource: ResourceType = ResourceType.GRAIN,
    val minClickDelayMs: Long = 7_000,
    val maxClickDelayMs: Long = 21_000,
    val marchCheckIntervalMinMs: Long = 30_000,
    val marchCheckIntervalMaxMs: Long = 75_000,
    val helpCheckIntervalMs: Long = 3_000,

    // Threshold differenziati:
    // - Pulsanti grandi (cerca, raccogli, marcia, nuove_truppe, non_disponibile_*): 0.80
    // - Pulsanti medi (icone risorse, lente_blu): 0.78
    // - Pulsanti piccoli (livello_meno 28x29, livello_piu 30x29, help 49x44): 0.72
    val templateMatchThreshold: Double = 0.80,
    val templateMatchThresholdSmall: Double = 0.72,  // livello_meno, livello_piu, help
    val templateMatchThresholdIcons: Double = 0.78,  // icone risorse, lente_blu

    val startLevel: Int = 7,

    // Risoluzione dei template (px del dispositivo da cui sono stati ritagliati).
    // Se il telefono target ha risoluzione diversa, OpenCV scalerà automaticamente.
    // Impostare a true per abilitare multi-scale matching (più lento ma più robusto).
    val useMultiScaleMatching: Boolean = false
)

enum class ResourceMode { MIXED, SINGLE }

data class MatchResult(
    val found: Boolean,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val confidence: Double = 0.0
) {
    /** Punto casuale dentro il bounding box (anti-ban) */
    fun randomPoint(): Pair<Int, Int> {
        if (!found) return Pair(0, 0)
        val rx = x + (Math.random() * width * 0.6 + width * 0.2).toInt()
        val ry = y + (Math.random() * height * 0.6 + height * 0.2).toInt()
        return Pair(rx, ry)
    }
}

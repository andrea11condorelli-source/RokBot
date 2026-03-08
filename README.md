# ROK Auto Gather Bot — Android

Bot Kotlin per automatizzare la raccolta risorse in Rise of Kingdoms.
Non richiede root, gira in background.

---

## Stack tecnico

| Componente | Libreria |
|---|---|
| Click senza root | `AccessibilityService` |
| Screenshot background | `MediaProjection API` |
| OCR contatore marce | `ML Kit Text Recognition` |
| Trovare pulsanti | `OpenCV matchTemplate` |
| Esecuzione background | `ForegroundService` |

---

## Setup iniziale

### 1. Template PNG
Metti i tuoi ritagli in:
```
app/src/main/assets/templates/
```
File richiesti (`.png`, su sfondo trasparente o solido):
```
lente_blu.png
icona_grano.png
icona_legna.png
icona_pietra.png
icona_oro.png
cerca.png
livello_meno.png
livello_piu.png
raccogli.png
nuove_truppe.png
marcia.png
help.png
non_disponibile_grano.png
non_disponibile_legna.png
non_disponibile_pietra.png
non_disponibile_oro.png
```

**Consiglio:** Ritaglia i template con bordi stretti (~5px padding) per il miglior matching.
Risoluzione: prendi screenshot al 100% dal telefono target.

### 2. Prima build
```bash
./gradlew assembleDebug
```

### 3. Primo avvio
1. Installa l'APK
2. Apri l'app → tocca **Impostazioni Accessibilità**
3. Trova "ROK Bot Accessibility" → abilita
4. Torna all'app → premi **▶ AVVIA BOT**
5. Concedi il permesso di screen capture
6. Apri Rise of Kingdoms → il bot gira in background

---

## Architettura

```
MainActivity
  └─ chiede permesso MediaProjection
  └─ avvia BotForegroundService

BotForegroundService (foreground, tipo: mediaProjection)
  └─ inizializza ScreenCapture (MediaProjection)
  └─ crea BotEngine

BotEngine
  ├─ mainLoop() — controlla marce ogni 30-75s, esegue sequenza gather
  └─ helpLoop() — cerca e preme HELP ogni 3s (coroutine parallela)

BotEngine → TemplateMatcher (OpenCV) → trova pulsanti
BotEngine → MarchCounter (ML Kit OCR) → legge "X/Y"
BotEngine → RokAccessibilityService → esegue click
```

---

## Anti-ban implementato

- ✅ Delay random 7–21s tra click
- ✅ Click in posizione casuale dentro il template (non sempre al centro)
- ✅ Pausa random 0.6–1.6s prima di ogni click
- ✅ Gesture con percorso Bezier curvo (non retta)
- ✅ Jitter ±4px su ogni click
- ✅ Intervallo marce random 30–75s

---

## Tuning template matching

Se un pulsante non viene trovato, abbassa `templateMatchThreshold` in `BotConfig`:

```kotlin
val config = BotConfig(templateMatchThreshold = 0.75)  // default 0.80
```

Per debug: il log nella notifica mostra confidence di ogni match.

---

## Personalizzazione BotConfig

```kotlin
BotConfig(
    resourceMode = ResourceMode.SINGLE,
    singleResource = ResourceType.GRAIN,
    minClickDelayMs = 10_000,   // delay minimo tra click
    maxClickDelayMs = 25_000,   // delay massimo
    marchCheckIntervalMinMs = 30_000,
    marchCheckIntervalMaxMs = 75_000,
    helpCheckIntervalMs = 3_000,
    templateMatchThreshold = 0.80,
    startLevel = 7
)
```

---

## Troubleshooting

| Problema | Soluzione |
|---|---|
| Click non funzionano | Verifica AccessibilityService abilitato |
| Screenshot nero | Riavvia il service; alcuni launcher bloccano la projection |
| Template non trovato | Abbassa threshold o ritaglia template più preciso |
| OCR marce sbagliato | Ingrandisci la ROI in `MarchCounter.getRoiRect()` |
| Bot si ferma in background | Disabilita ottimizzazione batteria per l'app |

---

## File principali

```
app/src/main/java/com/rokbot/
├── model/
│   └── BotConfig.kt          ← Configurazione e tipi
├── cv/
│   └── TemplateMatcher.kt    ← OpenCV template matching
├── ocr/
│   └── MarchCounter.kt       ← ML Kit OCR marce
├── service/
│   ├── RokAccessibilityService.kt  ← Click senza root
│   ├── ScreenCapture.kt            ← Screenshot MediaProjection
│   ├── BotEngine.kt                ← Loop principale bot
│   └── BotForegroundService.kt     ← Service background
└── ui/
    └── MainActivity.kt       ← UI controllo
```

package com.botcelular.mu

/**
 * Constantes editables — equivalente Android de config.py en el proyecto
 * PC (miproyecto). Los valores de región/color/posición de acá son
 * PLACEHOLDERS: hay que calibrarlos con una captura real del juego
 * corriendo en el celular del usuario (mismo método que snapshot.py en
 * el proyecto PC: capturar un frame real, medir la región/color a mano
 * o con una herramienta, confirmar antes de confiar en el valor).
 */
object Config {
    // ── Update checker (GitHub Releases) ────────────────────────────
    // TODO: reemplazar por el owner/repo real de GitHub una vez creado.
    const val GITHUB_REPO_OWNER = "TU_USUARIO"
    const val GITHUB_REPO_NAME = "TU_REPO"

    // ── Loop del bot ─────────────────────────────────────────────────
    const val TICK_INTERVAL_MS = 2000L

    // ── Barra de HP — PLACEHOLDER, calibrar con captura real ─────────
    // Región en píxeles, relativa al frame capturado (mismo sistema que
    // HP_BAR en config.py del proyecto PC).
    var hpBarX = 0
    var hpBarY = 0
    var hpBarWidth = 100
    var hpBarHeight = 10
    // Color BGR/RGB de referencia de la barra llena — placeholder.
    var hpColorR = 200
    var hpColorG = 30
    var hpColorB = 30
    const val HP_THRESHOLD = 0.5f

    // Igual para MP — sin calibrar todavía (mismo estado que MP_BAR en
    // config.py del proyecto PC: "no confíes en esto todavía").
    var mpBarX = 0
    var mpBarY = 0
    var mpBarWidth = 100
    var mpBarHeight = 10
    var mpColorR = 30
    var mpColorG = 30
    var mpColorB = 200
    const val MP_THRESHOLD = 0.3f

    // ── Posición del botón de poción en pantalla — PLACEHOLDER ───────
    // A diferencia de la versión PC (que usa una tecla mapeada por
    // LDPlayer), acá se toca directo el botón real del juego en la
    // pantalla del celular. Coordenadas relativas al frame capturado.
    var hpPotionButtonX = 0
    var hpPotionButtonY = 0
    var mpPotionButtonX = 0
    var mpPotionButtonY = 0
}

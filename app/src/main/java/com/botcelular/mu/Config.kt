package com.botcelular.mu

/**
 * Constantes editables — equivalente Android de config.py en el proyecto
 * PC (miproyecto).
 *
 * Los valores de región/color/posición de HP/MP y botones de poción de
 * abajo fueron calibrados el 2026-07-22 contra una captura del EMULADOR
 * LDPlayer (1640x935, landscape) corriendo MU: Dark Awakening — NO contra
 * el celular real del usuario todavía. Un teléfono real casi seguro tiene
 * otra resolución/aspecto (portrait, no 1640x935), así que estos números
 * hay que volver a calibrarlos con una captura real del celular antes de
 * confiar en ellos ahí (mismo método: capturar un frame real, medir
 * región/color a mano o con una herramienta, confirmar antes de usar).
 * Esta calibración solo sirve para validar la lógica de lectura de barras
 * end-to-end en el emulador mientras tanto.
 */
object Config {
    // ── Update checker (GitHub Releases) ────────────────────────────
    const val GITHUB_REPO_OWNER = "onlysecretsagency00-lgtm"
    const val GITHUB_REPO_NAME = "botcelular"

    // ── Loop del bot ─────────────────────────────────────────────────
    const val TICK_INTERVAL_MS = 2000L

    // ── Barra de HP — calibrada contra el EMULADOR (ver comentario arriba) ──
    // Región en píxeles, relativa al frame capturado (mismo sistema que
    // HP_BAR en config.py del proyecto PC). Medida con un grid superpuesto
    // sobre una captura real (snapshot.py) mientras el personaje estaba a
    // full HP (899685/899685) — el color es el de la barra roja llena.
    var hpBarX = 124
    var hpBarY = 81
    var hpBarWidth = 232
    var hpBarHeight = 15
    var hpColorR = 162
    var hpColorG = 7
    var hpColorB = 0
    const val HP_THRESHOLD = 0.5f

    // Barra de MP — en este juego (MU: Dark Awakening) el HUD SÍ tiene un
    // indicador de MP visible (a diferencia de lo que se había concluido en
    // el proyecto PC): una barra AMARILLA justo debajo de la de HP, misma
    // posición horizontal. Medida igual que HP arriba, personaje a full MP
    // (3000/3000).
    var mpBarX = 124
    var mpBarY = 102
    var mpBarWidth = 232
    var mpBarHeight = 11
    var mpColorR = 235
    var mpColorG = 252
    var mpColorB = 5
    const val MP_THRESHOLD = 0.3f

    // ── Posición del botón de poción en pantalla — calibrada contra el
    // EMULADOR (ver comentario arriba). A diferencia de la versión PC (que
    // usa una tecla mapeada por LDPlayer), acá se toca directo el botón
    // real del juego en la pantalla. Son los dos frascos (rojo=HP,
    // azul=MP) en la franja inferior de la HUD.
    var hpPotionButtonX = 508
    var hpPotionButtonY = 850
    var mpPotionButtonX = 1095
    var mpPotionButtonY = 850
}

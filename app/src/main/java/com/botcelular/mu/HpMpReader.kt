package com.botcelular.mu

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Puerto directo de screen.py::BarReader.read_percent (proyecto PC) — lee
 * qué fracción de una barra de color fijo (HP/MP) sigue "llena" contando
 * columnas cuyo color cae dentro de una tolerancia del color de
 * referencia, en vez de comparar contra un ancho de barra dinámico.
 */
object HpMpReader {

    private const val TOLERANCE = 40

    fun readPercent(
        frame: Bitmap,
        x: Int, y: Int, width: Int, height: Int,
        refR: Int, refG: Int, refB: Int,
    ): Float {
        if (width <= 0 || height <= 0) return 1f
        if (x < 0 || y < 0 || x + width > frame.width || y + height > frame.height) return 1f

        var filledCols = 0
        for (col in x until x + width) {
            var colHasMatch = false
            for (row in y until y + height) {
                val pixel = frame.getPixel(col, row)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (abs(r - refR) <= TOLERANCE && abs(g - refG) <= TOLERANCE && abs(b - refB) <= TOLERANCE) {
                    colHasMatch = true
                    break
                }
            }
            if (colHasMatch) filledCols++
        }
        return filledCols.toFloat() / width
    }
}

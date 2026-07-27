package com.botcelular.mu

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Un bloque de texto reconocido en pantalla, con su posición real (no
 * relativa) para poder tocarlo. */
data class OcrLabel(val text: String, val bounds: Rect) {
    val centerX get() = bounds.centerX()
    val centerY get() = bounds.centerY()
}

/**
 * OCR en el dispositivo vía ML Kit — equivalente Android de
 * ocr_reader.py::read_all_labels (pytesseract) del proyecto PC. Se apoya en
 * "líneas" que ML Kit ya agrupa (Text.TextBlock.Line), en vez de reagrupar
 * palabras sueltas a mano como hace la versión Python con LABEL_MERGE_RADIUS
 * — ML Kit hace ese trabajo internamente.
 */
object OcrReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Todas las líneas de texto detectadas en el frame completo (o en
     * [roi] si se pasa uno — recorte previo, más rápido si ya sabemos dónde
     * mirar). Las coordenadas devueltas son siempre relativas al frame
     * ORIGINAL completo, aunque se haya pasado un roi. */
    suspend fun readAllLabels(frame: Bitmap, roi: Rect? = null): List<OcrLabel> {
        val (bitmap, offsetX, offsetY) = if (roi != null) {
            Triple(
                Bitmap.createBitmap(frame, roi.left, roi.top, roi.width(), roi.height()),
                roi.left, roi.top,
            )
        } else {
            Triple(frame, 0, 0)
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val labels = mutableListOf<OcrLabel>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty()) continue
                val realBounds = Rect(
                    box.left + offsetX, box.top + offsetY,
                    box.right + offsetX, box.bottom + offsetY,
                )
                labels.add(OcrLabel(text, realBounds))
            }
        }
        return labels
    }
}

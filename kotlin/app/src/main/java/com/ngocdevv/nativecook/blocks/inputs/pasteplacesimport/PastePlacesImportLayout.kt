package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import kotlin.math.round

/** Screen geometry shared by the renderer and the paper: reference px -> pixels, plus measured text boxes. */
internal data class Layout(
    val width: Float,
    val height: Float,
    val topInset: Float,
    val u: Float,
    val k: Float,
    val y0: Float,
    val pageShader: LinearGradient,
    val bandShader: RadialGradient,
    val cornerShader: RadialGradient,
    val wordBoxes: List<WordBox>,
    val chips: List<ChipBox>,
) {
    lateinit var paper: PastePlacesImportPaper

    fun x(reference: Float): Float = reference * u

    fun y(reference: Float): Float = reference * u + y0

    fun px(reference: Float): Float = reference * u

    fun lineBox(size: Float, baseline: Float): LineBox {
        val fontSize = size * k
        val height = round(fontSize * 1.4f)
        val baseIn = (height - (PastePlacesImportTheme.ASCENT + PastePlacesImportTheme.DESCENT) * fontSize) / 2f + PastePlacesImportTheme.ASCENT * fontSize
        return LineBox(y(baseline) - baseIn, height, baseIn)
    }
}

internal data class LineBox(val top: Float, val height: Float, val baseIn: Float)

internal data class WordBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val centerY: Float
        get() = top + height / 2f
}

/** A chip's own layout, pixels, chip-local: size, the pin's left and centre x, the label's left x and baseline. */
internal data class ChipBox(
    val text: String,
    val width: Float,
    val height: Float,
    val pinLeft: Float,
    val pinCenterX: Float,
    val labelLeft: Float,
    val labelBaseline: Float,
)

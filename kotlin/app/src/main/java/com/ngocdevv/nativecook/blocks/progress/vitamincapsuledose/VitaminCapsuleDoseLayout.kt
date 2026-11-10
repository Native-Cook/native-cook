package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

/** RN makeLayout: horizontal device points and independently fitted safe-area content height. */
internal data class VitaminCapsuleDoseLayout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
    private val safeTop = maxOf(top, 44.0)
    private val safeBottom = maxOf(bottom, 20.0)
    val sx = w / 402.0
    val k = (h - safeTop - safeBottom) / 778.0
    fun y(v: Double) = if (v < 62.0) v * safeTop / 62.0 else safeTop + (v - 62.0) * k
    val chipH = 66 * maxOf(k, 0.9)
    val chipTop = y(410.0) + (66 * k - chipH) / 2
    val chipPad = (chipH - 66) / 2
    val cx = 101.0 * sx
    val cy = y(221.0)
    val unit = 76.0 * sx
    val floor = y(298.0)
    val checkX = 351.0 * sx
    val rowsY = listOf(y(568.0), y(606.5), y(645.0))
    val logY = y(805.0)
}

package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

internal data class SmokedClocheRevealLayout(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val sx = w / 402f
    private val safeTop = maxOf(top, 44f)
    private val safeBottom = maxOf(bottom, 20f)
    val k = (h - safeTop - safeBottom) / (874f - 62f - 34f)
    private val scale = minOf(sx, k)
    fun y(value: Float) = if (value < 62f) value * safeTop / 62f else safeTop + (value - 62f) * k
    val cx = 200f * sx
    val cy = y(304f)
    val half = 206f * scale / 0.92f
    val radius = 188f * scale
    val ctaX = 276f * sx
    val ctaY = y(807f)
    fun inDome(x: Float, y: Float) = x >= cx - radius && x <= cx + radius && y >= cy - radius && y <= cy + radius
    fun inCta(x: Float, py: Float) = x >= 236f * sx && x <= 426f * sx && py >= y(772f) && py <= y(772f) + 70f * k
    fun inIcon(x: Float, y: Float, cx: Float, cy: Float, r: Float = 22f) = x >= cx - r && x <= cx + r && y >= cy - r && y <= cy + r
}

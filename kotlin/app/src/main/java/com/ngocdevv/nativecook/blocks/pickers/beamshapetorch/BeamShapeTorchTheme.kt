package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Every colour, size and copy token of the RN block (`bst-theme.ts`), measured on the pin and rectified onto the 402 x 874 pt
 * canvas. Only this file holds literal colours and sizes; the drawing, the shader and the motion read them from here.
 */
internal object BeamShapeTorchTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val wallTop = rgb(0x6E1F4E)
    val wallMid = rgb(0xB8538B)
    val wallLow = rgb(0x982168)
    val wallLight = floatArrayOf(0xC9 / 255f, 0xC4 / 255f, 0xD6 / 255f)
    val glyphTop = rgb(0xFDFCFE)
    val glyphLow = rgb(0xE9E5F6)
    val glyphOff = rgb(0x5D5A68)
    val shadow = rgb(0x1E0815)
    val rim = rgb(0xFFFFFF)
    val spill = rgb(0xFFFFFF)
    val ceiling = rgb(0xF6C9E6)
    val arc = rgb(0xB4B2BE)
    val coreHex = 0xFDFDFB
    val capMidHex = 0x09080B

    // the capsule: the Dynamic Island grown into a panel
    const val CAP_X = 82.0
    const val CAP_Y = 11.0
    const val CAP_W = 240.0
    const val CAP_H = 430.0
    const val CAP_R = 56.0
    const val ISLAND_W = 126.0
    const val ISLAND_H = 37.0
    const val ISLAND_Y = 11.0

    // inside the capsule, from its top-left corner
    const val LENS_X = 119.0
    const val LENS_Y = 276.0
    const val GLYPH_X = 105.0
    const val GLYPH_Y = 294.0
    const val GLYPH_W = 28.0
    const val GLYPH_H = 79.0
    const val ARC_STROKE = 1.5
    const val DOTS_SIZE = 1.7
    const val DOTS_PITCH = 5.5
    const val SUN_ALONG = 19.0
    const val SUN_RING = 3.8
    const val SUN_RAY_IN = 6.6
    const val SUN_RAY_OUT = 7.6
    const val SUN_STROKE = 1.1
    const val SUN_RAYS = 8

    // shadows of the floating capsule on the wallpaper
    const val CONTACT_Y = 2.0
    const val CONTACT_BLUR = 4.0
    const val CONTACT_ALPHA = 0.32
    const val AMBIENT_Y = 18.0
    const val AMBIENT_BLUR = 40.0

    const val LABEL = "Beam control"
    const val HINT_OPEN =
        "Drag up or down to set the brightness, toward or away from the beam to focus it, or drag the sun. Tap to switch it on or off."
    const val HINT_CLOSED = "Opens the beam control"
    const val NARROW = "Focus the beam"
    const val WIDEN = "Widen the beam"
    const val CLOSE = "Close the beam control"

    /** What a host can change (the `theme` prop); every field falls back to the pin's value. */
    class Theme(
        /** the glass's colour at the middle of its gradient (the pin: #09080B) */
        val glass: Int? = null,
        /** the light's colour at the lens (the pin: #FDFDFB) */
        val light: Int? = null,
        /** the arc, its dots and the sun (the pin: #B4B2BE) */
        val arc: Int? = null,
        /** dispersion at the cone's edge, 0 none .. 1 the pin .. 1.5 */
        val fringe: Double = 1.0,
        /** the page's share of the light, 0 none .. 1 the pin's glow */
        val spill: Double = 1.0,
    )

    class Resolved(val glass: FloatArray, val light: FloatArray, val arc: Int, val fringe: Float, val spill: Double)

    private fun rgbf(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    fun resolve(t: Theme?): Resolved = Resolved(
        glass = rgbf(t?.glass ?: rgb(capMidHex)),
        light = rgbf(t?.light ?: rgb(coreHex)),
        arc = t?.arc ?: arc,
        fringe = min(max(t?.fringe ?: 1.0, 0.0), 1.5).toFloat(),
        spill = min(max(t?.spill ?: 1.0, 0.0), 1.0),
    )

    /** Where the open capsule sits: under the Dynamic Island (the default), or in a rectangle of the host's choosing (dp). */
    sealed interface Anchor {
        data object Island : Anchor
        data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) : Anchor
    }

    class Box(val x: Double, val y: Double, val w: Double, val h: Double, val r: Double)

    class Layout(val w: Double, val h: Double, val s: Double, val cap: Box, val isl: Box, private val ox: Double, private val oy: Double) {
        /** a point inside the open capsule (design pt from its top-left) -> dp */
        fun ix(x: Double) = cap.x + ox + x * s
        fun iy(y: Double) = cap.y + oy + y * s
    }

    /**
     * The capsule hangs from the island, so it is anchored to the top of the screen, not to the safe area: on a phone with a
     * Dynamic Island (top inset >= 47) it starts at the island's top (11 pt); without one it starts 6 pt under the status bar.
     * Its size and everything inside scale with the width (S = W / 402). Given a rectangle instead, the glass fills it and the
     * content (240 x 430 pt at S = 1) is centred in it at the scale that fits.
     */
    fun layout(w: Double, h: Double, insetTop: Double, anchor: Anchor = Anchor.Island): Layout {
        if (anchor is Anchor.Rect) {
            val s = max(min(anchor.width / CAP_W, anchor.height / CAP_H), 0.05)
            val ox = (anchor.width - CAP_W * s) / 2
            val oy = (anchor.height - CAP_H * s) / 2
            val cap = Box(anchor.x, anchor.y, anchor.width, anchor.height, CAP_R * s)
            val iw = ISLAND_W * s
            val ih = ISLAND_H * s
            val isl = Box(anchor.x + (anchor.width - iw) / 2, anchor.y, iw, ih, ih / 2)
            return Layout(w, h, s, cap, isl, ox, oy)
        }
        val s = w / REF_W
        val hasIsland = insetTop >= 47
        val top = if (hasIsland) ISLAND_Y else insetTop + 6
        val cw = CAP_W * s
        val ch = min(CAP_H * s, h - top - 120 * s)
        val cap = Box((w - cw) / 2, top, cw, ch, CAP_R * s)
        val isl = Box((w - ISLAND_W) / 2, top, ISLAND_W, ISLAND_H, ISLAND_H / 2)
        return Layout(w, h, s, cap, isl, 0.0, 0.0)
    }
}

package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.graphics.Color
import kotlin.math.max

/**
 * Every colour, size and copy of the RN block (`rgr-theme.ts`), measured on the pin. Points on the 402 x 874 reference screen; the pin's card
 * (402 x 681) sits at y 120 on it.
 */
internal object ReededGlassRevealTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_BOTTOM = 34.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val wall = rgb(0xE5E4EC) // the page under the image while it loads
    val ink = rgb(0xF7F7F9)
    val mute = rgb(0x959598)
    val panel = rgb(0x161619)
    val well = rgb(0x222224)
    val plusWell = rgb(0x242328)
    val swap = rgb(0xFFFFFD)
    val swapGlyph = rgb(0x202124)
    val thumbA = rgb(0xC9C9BC)
    val thumbB = rgb(0x8E927E)
    val thumbC = rgb(0x5B7052)
    val thumbInk = rgb(0xF4F5EF)
    val thumbMute = rgb(0xDADDD2)
    val netA = rgb(0xE9473A)
    val netB = rgb(0xF2B33D)
    val vidly = rgb(0xEE2A2F)
    val loopDisc = rgb(0x0B0B0D)
    val loopCyan = rgb(0x2CF4EF)
    val loopPink = rgb(0xFE2C55)

    // the capsule glass (sRGB; Android needs no Display-P3 conversion)
    val capsuleTop = rgb(0x1F1D20)
    val capsuleLow = rgb(0x121013)
    val capsuleEnd = rgb(0xACACB4)
    val capsuleShadow = rgb(0x050407)

    /** the ground the capsule and the panel stand on: the portrait's black sweater */
    val ground = rgb(0x0C0C10)
    val groundClear = Color.argb(0, 12, 12, 16)

    // type: Urbanist (OFL), size in dp at 402 wide and tracking in em
    const val TITLE_SIZE = 17.0
    const val TITLE_TRACK = -0.01
    const val NUM_SIZE = 15.0
    const val ROW_SIZE = 14.5
    const val THUMB_BRAND = 4.6
    const val THUMB_NUM = 5.4
    const val THUMB_SMALL = 3.5

    /** Urbanist Light advances in em (hmtx), for laying the number out glyph by glyph */
    val ADV = mapOf(
        '0' to 0.593, '1' to 0.245, '2' to 0.5365, '3' to 0.4855, '4' to 0.5355, '5' to 0.547, '6' to 0.535, '7' to 0.53, '8' to 0.5205, '9' to 0.52,
        '*' to 0.3495, ' ' to 0.24,
    )
    const val ASCENT = 0.95 // Urbanist hhea
    const val DESCENT = 0.25

    // the glass: 22 flutes, each a wide-angle cylinder lens (the shader's GLASS pass)
    const val RIBS = 22
    const val GLASS_SMEAR = 2.6 // dp: cached defocus of the photo before the lens (x)
    const val GLASS_SMEAR_Y = 3.2 // (y)
    const val GLASS_KAPPA = -2.3
    const val GLASS_TAU = 1.15
    const val GLASS_SLICE = 0.0
    const val GLASS_SPREAD = 0.4
    const val GLASS_SAG = 0.12
    const val GLASS_EDGE_K = 0.9
    const val GLASS_BLUR = 1.4
    const val GLASS_MAG = 0.015
    const val GLASS_CHROMA = 1.6
    const val GLASS_GLINT_W = 0.75
    const val GLASS_SEAM = 0.7
    const val CAPSULE_HAZE = 7.0 // dp: blur of the portrait the capsule's thick ends refract

    class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    val CAPSULE = Box(16.0, 472.0, 370.0, 99.0)
    const val CAPSULE_R = 28.0
    val THUMB = Box(36.0, 491.3, 84.2, 59.2)
    const val THUMB_R = 4.0
    const val TITLE1_X = 144.5
    const val TITLE1_BASE = 510.8
    const val STARS_X = 143.4
    const val STARS_DROP = 3.0
    const val NUM_X = 143.4
    const val NUM_BASE = 543.3
    const val NUM_REST_X2644 = 171.9
    const val SWAP_CX = 334.5
    const val SWAP_CY = 520.9
    const val SWAP_D = 60.0
    const val SWAP_GLYPH_W = 18.0
    const val SWAP_GLYPH_H = 12.0
    const val SWAP_STROKE = 1.4
    const val PANEL_X = 16.0
    const val PANEL_Y = 584.5
    const val PANEL_W = 370.0
    const val PANEL_R = 32.0
    const val TITLE2_X = 37.6
    const val TITLE2_BASE = 639.7
    const val PLUS_CX = 334.5
    const val PLUS_CY = 633.9
    const val PLUS_D = 60.0
    const val PLUS_GLYPH = 12.3
    const val PLUS_STROKE = 1.4
    class RowGeo(val cy: Double, val base: Double)
    val ROWS_GEO = listOf(RowGeo(718.2, 723.2), RowGeo(787.2, 792.2))
    const val ROW_WELL_CX = 66.9
    const val ROW_WELL_D = 60.0
    const val ROW_NAME_X = 122.1
    const val ROW_FOLLOWERS_X = 200.5
    const val ROW_VALUE_RIGHT = 356.5
    const val MARK1_W = 23.0
    const val MARK1_H = 16.0
    const val MARK1_R = 4.5
    const val MARK2_D = 22.0
    const val SHADOW_DY = 10.0
    const val SHADOW_BLUR = 22.0
    const val SHADOW_ALPHA = 0.45

    const val TITLE1 = "Bank Account"
    const val STARS = "****"
    const val LAST4 = "2644"
    val FULL = listOf("4829", "1730", "2644")
    const val TITLE2 = "Authenticated platform"

    class Row(val name: String, val followers: String, val value: String)

    val ROWS = listOf(Row("Vidly", "122k  followers", "+ 120"), Row("Loop", "834k  followers", "+ 220"))
    const val THUMB_BRAND_TEXT = "Sable"
    const val THUMB_GROUPS = "****  ****" // three groups, like the revealed 4829 1730 2644
    const val THUMB_LAST4 = "2644"
    const val THUMB_EXP_LABEL = "Expiry date"
    const val THUMB_EXP = "10/28"

    const val A11Y_GLASS = "Portrait behind reeded glass. Drag across it to clear the glass and show the account number."
    const val A11Y_HIDDEN = "Bank Account, number hidden, ending 2644"
    const val A11Y_SHOWN = "Bank Account, number 4829 1730 2644"
    const val A11Y_REVEAL = "Show account number"
    const val A11Y_HIDE = "Hide account number"
    const val A11Y_ADD = "Add a platform"

    /** seeded generator, so every stack and every run cuts the same glass (mulberry32) */
    private fun mulberry32(seed: Long): () -> Double {
        var a = seed and 0xFFFFFFFFL
        return {
            a = (a + 0x6D2B79F5L) and 0xFFFFFFFFL
            var t = a.toInt()
            t = imul(t xor (t ushr 15), t or 1)
            t = t xor (t + imul(t xor (t ushr 7), t or 61))
            ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    private fun imul(a: Int, b: Int): Int = a * b // 32-bit wrap, like Math.imul

    /** the 22 flutes are not a ruler's width: 15.5 to 21 dp on the 402 dp screen, summing to the screen */
    private val WIDTHS: DoubleArray = run {
        val rnd = mulberry32(462604)
        val raw = DoubleArray(RIBS) { 0.87 + 0.25 * rnd() }
        val sum = raw.sum()
        DoubleArray(RIBS) { raw[it] / sum }
    }

    /** per-flute random in 0..1 (phase of its glints, size of its lens), passed to the shader */
    val RIB_SEEDS: DoubleArray = run {
        val rnd = mulberry32(2644)
        DoubleArray(RIBS) { 0.04 + 0.92 * rnd() }
    }

    /** the three flutes a soft glint runs down at rest */
    val RUN_RIBS = doubleArrayOf(4.0, 11.0, 17.0)

    /** flute edges for a screen width: 23 numbers, first 0 and last W */
    fun ribEdges(w: Double): DoubleArray {
        val edges = DoubleArray(RIBS + 1)
        var acc = 0.0
        for (i in 0 until RIBS) {
            acc += WIDTHS[i]
            edges[i + 1] = if (i == RIBS - 1) w else acc * w
        }
        return edges
    }

    /** the layout of the screen at a size (RN `makeLayout` + `glassLayout` for the account screen) */
    class Layout(val w: Double, val h: Double, insetBottom: Double) {
        val s = w / REF_W
        private val bottom = max(insetBottom, 0.0)
        fun x(v: Double) = v * s

        /** y anchored to the bottom inset (capsule, panel and everything in them) */
        fun yb(v: Double) = h - bottom - (REF_H - SAFE_BOTTOM - v) * s
        val capTop = yb(CAPSULE.y)

        /** the portrait image's rect, anchored to the capsule */
        val imgX = 0.0
        val imgY = capTop - CAPSULE.y * s
        val imgW = REF_W * s
        val imgH = REF_H * s
        val ribW = w / RIBS
        val edges = ribEdges(w)
        val centres = DoubleArray(RIBS) { (edges[it] + edges[it + 1]) / 2 }

        /** front positions (dp) for progress 0 and 1: one rib width outside each edge */
        val front0 = -1.2 * ribW
        val front1 = w + 1.2 * ribW

        /** the lens' centre: the point the glass magnifies about */
        val magX = w / 2
        val magY = imgY + 300 * s
    }
}

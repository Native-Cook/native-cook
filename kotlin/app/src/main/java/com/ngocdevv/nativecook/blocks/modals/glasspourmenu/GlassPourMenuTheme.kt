package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import kotlin.math.max
import kotlin.math.min

/**
 * Every colour, size and copy of the RN block (`gpm-theme.ts`), measured on the pin (Dribbble 27773244). The pin is a desktop
 * composition laid on the phone at 1.14 pt per pin px with the flower's centre O at (201, 392) on the 402 x 874 reference screen.
 * Numbers are dp relative to O unless said otherwise.
 */
internal object GlassPourMenuTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0
    const val ORIGIN_Y = 392.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val cream = rgb(0xF4E1DE)
    val petalCore = rgb(0xEF77BA)
    val petalLilac = rgb(0xF5A7D1)
    val petalRose = rgb(0xF491A9)
    val petalCoral = rgb(0xF5ABA7)
    val petalPeach = rgb(0xF6BEBC)
    val foldLight = rgb(0xF6A4CA)
    val foldDeep = rgb(0xF46789)
    val foldEnd = rgb(0xF27B81)
    val edge = rgb(0x8E3A30)
    val fold = rgb(0xC2306A)
    val dropTint = rgb(0xF276CD)
    val dropDeep = rgb(0xB9579D)
    val halo = rgb(0xC25CA3)
    val caustic = rgb(0xFFB8F6)
    val panelTint = rgb(0xFFA6D2)
    val panelLow = rgb(0xFFC0E7)
    val hot = rgb(0xFFF3FA)
    val shadow = rgb(0x7A2A5C)
    val rim = rgb(0xFFFFFF)
    val ink = rgb(0x3A0F2C)
    val glyph = rgb(0x3A0A30)
    val danger = rgb(0x921E40)
    val rule = rgb(0xFFFFFF)

    /** Five discs whose circles all pass through O: x, y (dp from O), as in the RN `ref.discs`. */
    val DISCS = arrayOf(
        doubleArrayOf(-316.7, -268.5), doubleArrayOf(98.3, -360.4), doubleArrayOf(352.2, 6.4), doubleArrayOf(119.2, 402.2), doubleArrayOf(-262.1, 210.2),
    )

    const val ROW_SIZE = 17.0
    const val CAP_RATIO = 0.727

    // component boxes (dp from O)
    const val BUTTON_R = 39.0
    const val BEAD_Y = -107.76
    const val BEAD_R = 38.22
    const val PANEL_TOP = -54.75
    const val PANEL_BOTTOM = 161.25
    const val PANEL_HALF = 125.0
    const val PANEL_R = 21.3
    const val ROW_PITCH = 48.0
    const val RULE_Y = 92.25
    const val RULE_INSET = 15.0
    const val LABEL_X = 20.0
    const val ICON_X = 27.5
    const val ICON = 17.0
    const val STROKE = 1.4
    const val GLYPH_W = 23.8
    const val GLYPH_GAP = 6.9
    const val GLYPH_STROKE = 2.25
    const val HIT_SLOP = 10.0

    /** The menu's geometry for `count` rows: real dp, narrow phones do not shrink the 48 dp bands. */
    class Geometry(
        val buttonR: Double,
        val beadY: Double,
        val beadR: Double,
        val panelTop: Double,
        val panelBottom: Double,
        val panelHalf: Double,
        val panelR: Double,
        val rows: DoubleArray,
        val rowPitch: Double,
    )

    fun menuGeometry(count: Int = 4, drop: Double? = null, width: Double? = null, rowHeight: Double? = null, fontSize: Double = ROW_SIZE): Geometry {
        val radius = max(22.0, drop ?: 78.0) / 2
        val half = max(160.0, width ?: 250.0) / 2
        val rowPitch = max(max(48.0, rowHeight ?: 48.0), fontSize * 1.35 + 16)
        val top = PANEL_TOP * radius / 39
        val rows = DoubleArray(count) { top + 27 + it * rowPitch }
        return Geometry(
            buttonR = radius,
            beadY = BEAD_Y * radius / 39,
            beadR = radius * 0.98,
            panelTop = top,
            panelBottom = rows[count - 1] + rowPitch / 2 + 21,
            panelHalf = half,
            panelR = PANEL_R,
            rows = rows,
            rowPitch = rowPitch,
        )
    }

    /** The default geometry (the RN `comp`), with the four default rows. */
    val DEFAULT: Geometry = Geometry(
        BUTTON_R, BEAD_Y, BEAD_R, PANEL_TOP, PANEL_BOTTOM, PANEL_HALF, PANEL_R, doubleArrayOf(-27.75, 20.25, 68.25, 116.25), ROW_PITCH,
    )

    const val BUTTON = "More actions"
    const val CLOSE = "Close menu"

    val DEFAULT_ITEMS = listOf(
        GlassPourMenuItem("share", "Share", "share"),
        GlassPourMenuItem("duplicate", "Duplicate", "duplicate"),
        GlassPourMenuItem("rename", "Rename", "rename"),
        GlassPourMenuItem("delete", "Delete", "delete", destructive = true),
    )

    // the row glyphs, a 24 x 24 grid (stroked, round caps and joins)
    val ICONS = mapOf(
        "share" to "M12 3.5V14.5 M8.5 7L12 3.5L15.5 7 M8.5 10.5H7C5.9 10.5 5 11.4 5 12.5V18.5C5 19.6 5.9 20.5 7 20.5H17C18.1 20.5 19 19.6 19 18.5V12.5C19 11.4 18.1 10.5 17 10.5H15.5",
        "duplicate" to "M15.5 8.5V6.5C15.5 5.4 14.6 4.5 13.5 4.5H6.5C5.4 4.5 4.5 5.4 4.5 6.5V13.5C4.5 14.6 5.4 15.5 6.5 15.5H8.5 M10.5 8.5H17.5C18.6 8.5 19.5 9.4 19.5 10.5V17.5C19.5 18.6 18.6 19.5 17.5 19.5H10.5C9.4 19.5 8.5 18.6 8.5 17.5V10.5C8.5 9.4 9.4 8.5 10.5 8.5Z",
        "rename" to "M14.5 6.5L17.5 9.5 M5 19L5.8 15.2L15.8 5.2C16.6 4.4 17.9 4.4 18.8 5.2C19.6 6.1 19.6 7.4 18.8 8.2L8.8 18.2L5 19Z",
        "delete" to "M4.5 7H19.5 M9.5 7V5.5C9.5 4.9 9.9 4.5 10.5 4.5H13.5C14.1 4.5 14.5 4.9 14.5 5.5V7 M6.5 7L7.3 18.6C7.4 19.7 8.3 20.5 9.3 20.5H14.7C15.7 20.5 16.6 19.7 16.7 18.6L17.5 7 M10.5 10.5V16.5 M13.5 10.5V16.5",
    )

    /** O keeps the pin's optical centre (y 392 of 874); the menu is in real dp, so S = 1. */
    class Layout(val w: Double, val h: Double, insetTop: Double, insetBottom: Double) {
        val ox = w / 2
        val oy: Double

        init {
            val usable = h - insetTop - insetBottom
            oy = max(insetTop + (ORIGIN_Y - SAFE_TOP) * (usable / (REF_H - SAFE_TOP - SAFE_BOTTOM)), insetTop + 130)
        }
    }

    fun clamp(x: Double, lo: Double, hi: Double) = min(hi, max(lo, x))
}

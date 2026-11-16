package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import kotlin.math.max
import kotlin.math.min

internal class ThermalGlassTilesRect(val x: Double, val y: Double, val w: Double, val h: Double, val r: Double)

/** One text cell of the tiles: reference position, size and fitting width. */
internal class ThermalGlassTilesTextItem(
    val id: String, val x: Double, val y: Double, val size: Double, val light: Boolean = false, val align: Int = 0, val width: Double,
)

/** Colours, type, layout numbers and copy of the RN block (`tgt-theme.ts`): three fixed tiles at 5.2 s of the reference, in pt. */
internal object ThermalGlassTilesTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0

    const val PAGE = 0xFFBEB1B1.toInt()
    const val INK = 0xFFF8F5EB.toInt()
    const val INK_DARK = 0xFF172326.toInt()
    const val MARKER = 0xFFF4EBC1.toInt()
    const val ROUTE_COLOR = 0xFFE6E9DF.toInt()

    const val NOTE_SIZE = 11.0
    const val DISPLAY_SIZE = 40.0

    // material
    const val FROST = 0.3
    const val REFRACTION = 14.0
    const val DISPERSION = 1.5
    const val LENS = 0.06
    const val GRAIN = 0.04
    const val PEAK_WAKE = 60.0
    const val FIGURE_X = 201.0
    const val FIGURE_Y = 250.0
    const val FIGURE_K = 1.173

    // components
    val LARGE = ThermalGlassTilesRect(51.0, 196.0, 300.0, 300.0, 72.0)
    val CONTACT = ThermalGlassTilesRect(51.0, 512.0, 142.0, 142.0, 71.0)
    val VERTICAL = ThermalGlassTilesRect(209.0, 512.0, 142.0, 142.0, 40.0)
    const val ROUTE_STROKE = 0.8
    const val ROUTE_GRAB = 28.0
    val LABEL_LARGE = doubleArrayOf(51.0, 350.0, 300.0, 180.0)
    val LABEL_CONTACT = doubleArrayOf(73.0, 526.0, 98.0, 80.0)
    val LABEL_VERTICAL = doubleArrayOf(222.0, 525.0, 116.0, 114.0)
    const val CONTACT_Y = 629.0
    const val VERTICAL_X = 280.0
    const val VERTICAL_Y = 625.0
    const val VERTICAL_R = 54.0
    const val DOT = 1.7

    const val RUN = "Easy Run"
    const val CONTACT_LABEL = "Contact time"
    const val VERTICAL_LABEL = "Vertical oscillation"
    const val KM = "KM"
    const val MI = "MI"
    const val MS = "MS"
    const val CM = "CM"

    class Layout(val w: Double, val h: Double, val s: Double, val ox: Double, val oy: Double)

    /** The three-tile cluster sits on the frame's optical centre (y 447 of 874); safe area constrains it on short frames. */
    fun makeLayout(w: Double, h: Double, top: Double, bottom: Double): Layout {
        val s = min(w / REF_W, max(0.1, (h - top - bottom - 24) / 458))
        val ox = (w - REF_W * s) / 2
        val ideal = h * (447 / REF_H) - 425 * s
        val oy = max(top + 12 - LARGE.y * s, min(ideal, h - bottom - 12 - 654 * s))
        return Layout(w, h, s, ox, oy)
    }

    // align: 0 left, 1 right, 2 center
    val TEXT_ITEMS = listOf(
        ThermalGlassTilesTextItem("run", 79.0, 408.0, 13.0, width = 130.0),
        ThermalGlassTilesTextItem("distance", 79.0, 453.0, 40.0, light = true, width = 142.0),
        ThermalGlassTilesTextItem("unit", 194.0, 453.0, 11.0, width = 24.0),
        ThermalGlassTilesTextItem("elapsed", 323.0, 404.0, 11.0, align = 1, width = 75.0),
        ThermalGlassTilesTextItem("bpm", 323.0, 427.0, 11.0, align = 1, width = 75.0),
        ThermalGlassTilesTextItem("pace", 323.0, 450.0, 11.0, align = 1, width = 75.0),
        ThermalGlassTilesTextItem("contactLabel", 122.0, 541.0, 11.0, align = 2, width = 120.0),
        ThermalGlassTilesTextItem("contact", 89.0, 599.0, 28.0, light = true, width = 89.0),
        ThermalGlassTilesTextItem("ms", 160.0, 598.0, 11.0, width = 21.0),
        ThermalGlassTilesTextItem("verticalLabel", 280.0, 541.0, 11.0, align = 2, width = 122.0),
        ThermalGlassTilesTextItem("oscillation", 230.0, 632.0, 28.0, light = true, width = 95.0),
        ThermalGlassTilesTextItem("cm", 315.0, 632.0, 11.0, width = 20.0),
    )

    fun textItemsAt(fontScale: Double): List<ThermalGlassTilesTextItem> = TEXT_ITEMS.map {
        if (it.id == "unit" && fontScale > 1.2) ThermalGlassTilesTextItem(it.id, 79.0, 478.0, it.size, it.light, it.align, it.width) else it
    }
}


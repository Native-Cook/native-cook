package com.ngocdevv.nativecook.blocks.forms.addresslookupmap

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 Address Lookup Map — the data: the addresses the lookup knows and the invented town it draws
 (a port of addressLookupData.ts). Everything is made up: street names, districts, postcodes. The
 town is laid out in "world units" (a 1200 × 1200 square); AddressLookupMapStreet.kt draws it and
 the camera looks at it with a centre and a scale.
*/

data class AddressLookupMapAddress(
    /** House number and street, as typed and shown in the field. */
    val street: String,
    /** Second line of a suggestion. */
    val area: String,
    val unit: String,
    val postcode: String,
    val city: String,
    /** Front door in world units. */
    val x: Float,
    val y: Float,
)

internal object AddressLookupMapData {
    val ADDRESSES = listOf(
        AddressLookupMapAddress("48 Larkspur Lane", "Fernhill, Ashby", "Flat 3B", "2041", "Ashby", 606f, 541f),
        AddressLookupMapAddress("48 Larkfield Mews", "Ashby North", "Unit 2", "2043", "Ashby", 541f, 742f),
        AddressLookupMapAddress("148 Larkin Street", "Old Quay, Ashby", "Apt 12", "2038", "Ashby", 428f, 821f),
        AddressLookupMapAddress("48 Lark Rise", "Wendle, Bramley", "House", "2050", "Bramley", 806f, 872f),
        AddressLookupMapAddress("4 Linton Parade", "Fernhill, Ashby", "Shop 4", "2041", "Ashby", 214f, 681f),
        AddressLookupMapAddress("248 Lowther Road", "Bramley", "Apt 7", "2046", "Bramley", 978f, 961f),
        AddressLookupMapAddress("40 Larch Close", "Fernhill, Ashby", "Flat 1", "2041", "Ashby", 921f, 446f),
        AddressLookupMapAddress("84 Lantern Row", "Old Quay, Ashby", "Loft 5", "2038", "Ashby", 161f, 604f),
    )

    const val MAX_ROWS = 4

    private fun houseNumber(street: String): Int =
        street.takeWhile { it.isDigit() }.toIntOrNull() ?: 1_000_000

    /**
     The suggestions for a query: addresses whose street starts with it first (by house number),
     then those that contain it, at most MAX_ROWS. Indices into [list].
     */
    fun rank(query: String, list: List<AddressLookupMapAddress> = ADDRESSES): List<Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val starts = mutableListOf<Int>()
        val contains = mutableListOf<Int>()
        list.forEachIndexed { i, a ->
            val s = a.street.lowercase()
            if (s.startsWith(q)) starts.add(i)
            else if (s.contains(q) || a.area.lowercase().contains(q)) contains.add(i)
        }
        starts.sortWith(compareBy<Int>({ houseNumber(list[it].street) }, { it }))
        return (starts + contains).take(MAX_ROWS)
    }

    /** The stretch of [text] that matches the query, for the bold letters of a suggestion. */
    fun matchRange(text: String, query: String): IntRange? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val at = text.lowercase().indexOf(q)
        return if (at < 0) null else at until at + q.length
    }

    // ---- The town ------------------------------------------------------------------------------

    const val WORLD = 1200f

    class Road(val name: String, val points: List<FloatArray>, val label: FloatArray, val angle: Float)

    private fun p(x: Number, y: Number) = floatArrayOf(x.toFloat(), y.toFloat())

    /** Residential streets, 12 units wide. Labels sit on the road, rotated along it (degrees). */
    val STREETS = listOf(
        Road("Quay Road", listOf(p(40, 300), p(1160, 300)), p(430, 300), 0f),
        Road("Ferry Street", listOf(p(60, 380), p(1140, 380)), p(430, 380), 0f),
        Road("Larkspur Lane", listOf(p(60, 520), p(1140, 520)), p(430, 520), 0f),
        Road("Linton Parade", listOf(p(60, 660), p(1140, 660)), p(430, 660), 0f),
        Road("Larkin Street", listOf(p(60, 800), p(1140, 800)), p(620, 800), 0f),
        Road("Lowther Road", listOf(p(60, 940), p(1140, 940)), p(620, 940), 0f),
        Road("Mill Row", listOf(p(60, 1080), p(1140, 1080)), p(620, 1080), 0f),
        Road("Lantern Row", listOf(p(140, 300), p(140, 1160)), p(140, 590), -90f),
        Road("Wendle Way", listOf(p(330, 300), p(330, 1160)), p(330, 590), -90f),
        Road("Larkfield Mews", listOf(p(520, 380), p(520, 1160)), p(520, 730), -90f),
        Road("Orchard Walk", listOf(p(710, 300), p(710, 1160)), p(710, 450), -90f),
        Road("Larch Close", listOf(p(900, 380), p(900, 520)), p(900, 450), -90f),
        Road("Bell Street", listOf(p(1080, 300), p(1080, 1160)), p(1080, 590), -90f),
    )

    /** The crescent of Lark Rise, a quadratic curve inside its block. */
    val CRESCENT_FROM = p(730, 830)
    val CRESCENT_CTRL = p(805, 905)
    val CRESCENT_TO = p(880, 830)

    /** The one main road, wider and yellow, crossing the grid. */
    val MAIN_ROAD = Road("Ashby Road", listOf(p(0, 1150), p(1200, 470)), p(880, 652), -29.5f)

    class Park(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val name: String)

    /** Blocks between streets that are parks (between street centres). */
    val PARKS = listOf(
        Park(710f, 520f, 900f, 660f, "Fernhill Green"),
        Park(140f, 940f, 330f, 1080f, "Mill Fields"),
    )

    class District(val name: String, val x: Float, val y: Float)

    val DISTRICTS = listOf(
        District("Old Quay", 600f, 112f),
        District("Fernhill", 420f, 600f),
        District("Ashby", 880f, 1010f),
        District("Bramley", 236f, 880f),
    )

    /** River banks: a band of water across the north of the town. */
    val RIVER_NORTH = listOf(p(-20, 196), p(300, 236), p(620, 168), p(900, 214), p(1220, 184))
    val RIVER_SOUTH = listOf(p(-20, 262), p(300, 290), p(620, 236), p(900, 270), p(1220, 246))
    val RIVER_LABEL = p(760, 228)

    /** Seeded hash, 0 ..1 (the same sine hash as the React Native version, in doubles). */
    fun hash(n: Int): Double {
        val s = sin(n * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    private val XS = intArrayOf(140, 330, 520, 710, 900, 1080)
    private val YS = intArrayOf(300, 380, 520, 660, 800, 940, 1080)

    /**
     House footprints: rows of small rectangles along the edges of every block, seeded so the town
     is the same each time. Returns [x, y, w, h] in world units.
     */
    fun houses(): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        val edge = 13.0
        val depth = 26.0
        var seed = 1
        fun along(x0: Double, y0: Double, len: Double, horizontal: Boolean, inward: Int) {
            var pp = 0.0
            while (pp < len - 16) {
                val w = min(18 + hash(seed++) * 22, len - pp)
                val d = depth - hash(seed++) * 7
                if (hash(seed++) > 0.1) {
                    if (horizontal) out.add(floatArrayOf((x0 + pp).toFloat(), (if (inward > 0) y0 else y0 - d).toFloat(), w.toFloat(), d.toFloat()))
                    else out.add(floatArrayOf((if (inward > 0) x0 else x0 - d).toFloat(), (y0 + pp).toFloat(), d.toFloat(), w.toFloat()))
                }
                pp += w + 3 + hash(seed++) * 3
            }
        }
        val blockCols = (0 until XS.size - 1).map { intArrayOf(XS[it], XS[it + 1]) } + listOf(intArrayOf(40, 140), intArrayOf(1080, 1160))
        for (col in blockCols) {
            val xa = col[0]
            val xb = col[1]
            for (j in 0 until YS.size - 1) {
                val ya = YS[j]
                val yb = YS[j + 1]
                if (xa == 900 && ya == 380) continue // Larch Close splits this block; its houses are below.
                if (PARKS.any { it.x0.toInt() == xa && it.y0.toInt() == ya }) continue
                if (j == 0 && (xa == 40 || xa == 1080)) continue
                val x0 = xa + edge
                val x1 = xb - edge
                val y0 = ya + edge
                val y1 = yb - edge
                if (x1 - x0 < 40 || y1 - y0 < 40) continue
                val crescent = xa == 710 && ya == 800 // Lark Rise curves through this block's north half.
                if (!crescent) along(x0, y0, x1 - x0, true, 1)
                along(x0, y1, x1 - x0, true, -1)
                if (!crescent && y1 - y0 > depth * 2 + 20) {
                    along(x0, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, 1)
                    along(x1, y0 + depth + 4, y1 - y0 - depth * 2 - 8, false, -1)
                }
            }
        }
        // Larch Close: two short rows either side of the cul-de-sac.
        along(913.0, 393.0, 154.0, true, 1)
        along(913.0, 507.0, 154.0, true, -1)
        // Lark Rise: houses facing the crescent from its outer side.
        for (k in 0 until 6) {
            val u = (k + 0.5) / 6
            val a = (1 - u) * (1 - u)
            val b = 2 * u * (1 - u)
            val c = u * u
            val x = a * CRESCENT_FROM[0] + b * CRESCENT_CTRL[0] + c * CRESCENT_TO[0]
            val y = a * CRESCENT_FROM[1] + b * CRESCENT_CTRL[1] + c * CRESCENT_TO[1]
            out.add(floatArrayOf((x - 10).toFloat(), (y + 12).toFloat(), 20f, (22 - hash(k + 300) * 5).toFloat()))
        }
        // Old Quay: warehouses north of the river.
        for (i in 0 until 9) {
            val x = 30 + i * 130 + hash(i + 400) * 20
            out.add(floatArrayOf(x.toFloat(), (40 + hash(i + 410) * 30).toFloat(), (80 + hash(i + 420) * 30).toFloat(), (60 + hash(i + 430) * 40).toFloat()))
        }
        return out
    }
}

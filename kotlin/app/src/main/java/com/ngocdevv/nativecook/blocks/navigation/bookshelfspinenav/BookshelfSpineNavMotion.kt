package com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class BookshelfBinding { Cloth, Leather, Linen }

internal class BookshelfRow(val title: String, val author: String, val progress: Float, val tone: Pair<Long, Long>)

/** One book per section, each a different binding. Every title and name is invented. */
internal class BookshelfBook(
    val section: String,
    /** Spine width and height in dp. */
    val w: Float,
    val h: Float,
    val color: Long,
    val foil: Long,
    val binding: BookshelfBinding,
    val count: Int,
    val blurb: String,
    val rows: List<BookshelfRow>,
)

/** The shelf's data, the timeline and the poses (bookshelfData.ts, bookshelfMotion.ts, bookshelfColor.ts). */
internal object BookshelfSpineNavMotion {
    /** Loop period in seconds. */
    const val LOOP = 9f
    /** Length of the pull-and-open sequence, in seconds of open time τ. */
    const val OPEN = 2.6f

    const val DESIGN_W = 393f
    const val DESIGN_H = 759f
    /** Top of the shelf board: every book stands on it. */
    const val BOARD_Y = 700f
    private const val GAP = 2f

    const val HOLD_X = DESIGN_W / 2f
    const val HOLD_Y = 282f
    const val HOLD_SCALE = 1.48f
    const val PAGE_X = 16f
    const val PAGE_Y = 102f
    const val PAGE_W = DESIGN_W - 32f
    const val PAGE_H = 364f

    /** The book the self-playing demo pulls. */
    const val DEMO_BOOK = 1

    private fun row(title: String, author: String, progress: Float, a: Long, b: Long) = BookshelfRow(title, author, progress, a to b)

    val BOOKS = listOf(
        BookshelfBook(
            "Novels", 40f, 196f, 0xFF2F5243, 0xFFD9B663, BookshelfBinding.Cloth, 41, "41 books · 2 in progress",
            listOf(
                row("The Salt Orchard", "Ines Calloway", 0.64f, 0xFFC96F4A, 0xFF5B2A2E),
                row("Harbour Lights", "Teo Marchetti", 0.12f, 0xFF3F6E8C, 0xFF1C2B3E),
                row("A Year of Small Rain", "Juno Vale", 1f, 0xFF8DA36A, 0xFF2F4030),
            ),
        ),
        BookshelfBook(
            "Essays", 40f, 210f, 0xFF6E2430, 0xFFE2C072, BookshelfBinding.Leather, 12, "12 books · 3 in progress",
            listOf(
                row("On Walking Slowly", "Mira Okonjo", 0.38f, 0xFFD9A24E, 0xFF6B3A1F),
                row("The Kitchen Window", "Paul Arden-Hale", 0.81f, 0xFF6F8FA6, 0xFF26364A),
                row("Notes from a Ferry", "Sana Iqbal", 0.07f, 0xFFB85C6C, 0xFF45202C),
            ),
        ),
        BookshelfBook(
            "Poetry", 32f, 178f, 0xFF26365A, 0xFFC9D1DE, BookshelfBinding.Cloth, 19, "19 books · 1 in progress",
            listOf(
                row("Lantern Hours", "Elsa Brandt", 0.5f, 0xFFE0B64F, 0xFF5A4414),
                row("Field Recordings", "Kofi Mensah-Lowe", 1f, 0xFF7CA08B, 0xFF24392F),
                row("Blue Hour Psalms", "Noor Haddad", 0.22f, 0xFF5966A8, 0xFF1E2448),
            ),
        ),
        BookshelfBook(
            "Notes", 42f, 168f, 0xFFB8862F, 0xFF3A2510, BookshelfBinding.Linen, 86, "86 highlights · 9 books",
            listOf(
                row("“Rivers keep their own time.”", "The Salt Orchard, p. 112", 1f, 0xFFC96F4A, 0xFF5B2A2E),
                row("“Begin where your feet are.”", "On Walking Slowly, p. 9", 1f, 0xFFD9A24E, 0xFF6B3A1F),
                row("“Light is a kind of weather.”", "Lantern Hours, p. 31", 1f, 0xFFE0B64F, 0xFF5A4414),
            ),
        ),
        BookshelfBook(
            "Saved", 36f, 202f, 0xFF4E2F52, 0xFFE0BE6E, BookshelfBinding.Cloth, 27, "27 books saved for later",
            listOf(
                row("The Copper Bell", "Owen Fisk", 0f, 0xFFB9784A, 0xFF4A2A18),
                row("North of the River", "Lise Gordon", 0f, 0xFF6F9AA0, 0xFF203A3E),
                row("Wild Plum", "Hana Grove", 0f, 0xFF9C5A7E, 0xFF3A1D30),
            ),
        ),
        BookshelfBook(
            "History", 40f, 186f, 0xFF8A5A35, 0xFFF0CF83, BookshelfBinding.Leather, 15, "15 books · 1 in progress",
            listOf(
                row("The Canal Builders", "R. T. Ashdown", 0.44f, 0xFF8E7A5A, 0xFF2F271A),
                row("Tea, Silk and Salt", "Wen Huang", 0f, 0xFFB44A3C, 0xFF3E1914),
                row("Map of a Vanished Sea", "Ilse Varga", 1f, 0xFF4E7E9E, 0xFF1A2E3E),
            ),
        ),
        BookshelfBook(
            "Lent", 30f, 160f, 0xFF2E6466, 0xFFD3DCD8, BookshelfBinding.Cloth, 4, "4 books with friends",
            listOf(
                row("The Long Quiet", "with Dara · due 3 Nov", 0f, 0xFF5E7F6E, 0xFF1E2E26),
                row("Small Hours", "with Felix · due 9 Nov", 0f, 0xFFC78B5A, 0xFF4A2C18),
                row("Paper Lanterns", "with Ama · due 21 Nov", 0f, 0xFFD0A452, 0xFF4E3A12),
            ),
        ),
        BookshelfBook(
            "Audio", 38f, 192f, 0xFF34353B, 0xFFD69A6A, BookshelfBinding.Leather, 8, "8 audiobooks · 6 h 20 min left",
            listOf(
                row("The Weather Clock", "read by June Ellery · 4 h", 0.71f, 0xFF6E7FA8, 0xFF232A44),
                row("Lamplight", "read by Theo Rask · 7 h", 0.18f, 0xFFD49A4C, 0xFF4A3014),
                row("Saltmarsh Hours", "read by Odile Fenn · 5 h", 1f, 0xFF7FA38F, 0xFF263A30),
            ),
        ),
        BookshelfBook(
            "Wishlist", 38f, 174f, 0xFF9A4E5A, 0xFFF2E2C4, BookshelfBinding.Linen, 33, "33 books · 4 on sale",
            listOf(
                row("The Ninth Lighthouse", "Colm Frost", 0f, 0xFF4F7A9A, 0xFF1A2A3A),
                row("A Room of Blue Tiles", "Ottilie Glenn", 0f, 0xFF3F6FB0, 0xFF16284A),
                row("Rooms Above the Bakery", "Margo Flint", 0f, 0xFFC9925A, 0xFF4C301A),
            ),
        ),
    )

    private val ROW_W = BOOKS.sumOf { it.w.toDouble() }.toFloat() + GAP * (BOOKS.size - 1)

    /** Left edge of each spine on the shelf. */
    val BOOK_X: FloatArray = FloatArray(BOOKS.size).also { xs ->
        for (i in BOOKS.indices) xs[i] = if (i == 0) (DESIGN_W - ROW_W) / 2f else xs[i - 1] + BOOKS[i - 1].w + GAP
    }

    /** Front cover width of each book (a share of its height). */
    val COVER_W: FloatArray = FloatArray(BOOKS.size) { (BOOKS[it].h * 0.64f).roundToInt().toFloat() }

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
    fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    fun easeOutBack(x: Float): Float {
        val c = 1.4f
        return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
    }

    /** Open time τ at loop time t: rest; pull and open 1.2–3.8 s; hold; close 5.4–7.2 s; settle; rest. */
    fun autoTau(t: Float): Float = when {
        t < 1.2f -> 0f
        t < 3.8f -> t - 1.2f
        t < 5.4f -> OPEN
        else -> OPEN * (1f - seg(t, 5.4f, 7.2f))
    }

    /** The settling wobble of the neighbours once the book is back, 7.2–8.6 s. */
    fun autoWobble(t: Float): Float {
        if (t < 7.2f || t > 8.6f) return 0f
        val u = t - 7.2f
        return exp(-u * 4.2f) * sin(u * 15f) * (1f - seg(t, 8.0f, 8.6f))
    }

    class Pose(
        val tilt: Float,
        val turn: Float,
        val open: Float,
        val dx: Float,
        val dy: Float,
        val scale: Float,
        val alpha: Float,
        val grow: Float,
        val text: Float,
        val gap: Float,
    )

    /** The pulled book at open time τ (0 … OPEN). */
    fun bookPose(tau: Float, index: Int): Pose {
        val b = BOOKS[index]
        val tip = easeOutBack(seg(tau, 0f, 0.5f))
        val lift = easeInOutCubic(seg(tau, 0.4f, 1.6f))
        val turn = 90f * easeInOutCubic(seg(tau, 1.0f, 1.6f))
        val open = easeInOutCubic(seg(tau, 1.6f, 2.25f))
        val tilt = -16f * tip * (1f - easeInOutCubic(seg(tau, 0.6f, 1.2f)))
        val scale = 1f + (HOLD_SCALE - 1f) * lift
        val cw = COVER_W[index]
        val ox = BOOK_X[index] + b.w / 2f
        val oy = BOARD_Y
        val dx = (HOLD_X - (ox + HOLD_SCALE * (b.w / 2f + cw / 2f))) * lift
        val dy = (HOLD_Y - (oy - HOLD_SCALE * (b.h / 2f))) * lift - 10f * tip * (1f - lift)
        val grow = easeInOutCubic(seg(tau, 1.9f, 2.6f))
        val text = seg(tau, 2.25f, 2.6f)
        val alpha = 1f - seg(tau, 2.3f, 2.55f)
        val gap = easeOutCubic(seg(tau, 0.35f, 1.1f))
        return Pose(tilt, turn, open, dx, dy, scale, alpha, grow, text, gap)
    }

    /** How a book on the shelf leans when the book [active] is out: toward the gap, about its bottom corner. */
    fun neighbourLean(index: Int, active: Int, gap: Float, wobble: Float): Float {
        val d = index - active
        if (d == 0) return 0f
        val reach = if (abs(d) == 1) 1f else if (abs(d) == 2) 0.28f else 0f
        val dir = if (d < 0) 1f else -1f
        return dir * reach * (6.5f * gap + 2.6f * wobble)
    }

    /** Where the page starts, on the open book's cover: x, y, w, h. */
    fun coverRect(index: Int): FloatArray {
        val b = BOOKS[index]
        val cw = COVER_W[index] * HOLD_SCALE
        val ch = b.h * HOLD_SCALE
        return floatArrayOf(HOLD_X - cw / 2f, HOLD_Y - ch / 2f, cw, ch)
    }

    /** Lightens (amount > 0, toward a warm white) or darkens (amount < 0, toward a warm black) a colour. */
    fun shade(hex: Long, amount: Float): Color {
        val r = ((hex shr 16) and 0xFF).toInt()
        val g = ((hex shr 8) and 0xFF).toInt()
        val b = (hex and 0xFF).toInt()
        val to = if (amount > 0f) intArrayOf(255, 244, 226) else intArrayOf(14, 9, 6)
        val a = min(1f, abs(amount))
        fun mix(v: Int, t: Int) = (v + (t - v) * a).roundToInt()
        return Color(mix(r, to[0]), mix(g, to[1]), mix(b, to[2]))
    }
}

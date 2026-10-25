package com.ngocdevv.nativecook.blocks.lists.cardcataloglist

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One index card of the drawer: fiction from F to G, every name and title invented. */
internal class CardCatalogCard(
    val surname: String,
    val given: String,
    val title: String,
    /** Call number: the class letter, then the author's first three letters. */
    val callTop: String,
    val callBottom: String,
    val publisher: String,
    val year: Int,
    val pages: Int,
    /** Where the tab sits along the top edge: 0 left, 1 middle, 2 right. */
    val tab: Int,
    /** 0 on shelf, 1 on loan, 2 reading room. */
    val status: Int,
)

/** The cards, the timeline and the card poses of the Card Catalog List (cardCatalogData.ts, cardCatalogMotion.ts). */
internal object CardCatalogListMotion {
    /** Loop period in seconds. */
    const val LOOP = 9f

    const val DESIGN_W = 393f
    const val DESIGN_H = 759f
    const val CARD_W = 329f
    const val CARD_H = 232f
    const val CARD_X = (DESIGN_W - CARD_W) / 2f
    /** The reading line: the bottom edge every card turns about. */
    const val PIVOT_Y = 424f
    const val DRAWER_X = 14f
    const val DRAWER_Y = 104f
    const val DRAWER_W = DESIGN_W - 28f
    const val DRAWER_BOTTOM = PIVOT_Y + 66f
    const val FRONT_X = 6f
    const val FRONT_Y = 476f
    const val FRONT_W = DESIGN_W - 12f
    const val FRONT_H = 98f

    /** The card the demo thumbs to (Gaskell). */
    const val TARGET = 14
    const val BACK_LEAN = 15f
    const val FORWARD_LEAN = 104f
    const val STEP_UP = 13f
    const val VISIBLE_BACK = 7
    const val VISIBLE_FRONT = 4
    const val PT_PER_CARD = 58f

    private val PUBLISHERS = listOf("Harrow & Finch", "Mill Lane Press", "Copperleaf Books", "Weir & Daughters", "North Quay")
    private val RAW = listOf(
        Triple("Fairley", "Ines", "The Orchard Ledger"),
        Triple("Falk", "Tomas", "Winter Ferry"),
        Triple("Farrow", "June", "A House of Small Weather"),
        Triple("Faulk", "Daniel", "The Glass Cartographer"),
        Triple("Fenn", "Odile", "Saltmarsh Hours"),
        Triple("Fern", "Ruth", "The Night Telegraph"),
        Triple("Finch", "Ada", "Paper Lanterns"),
        Triple("Fisk", "Owen", "The Copper Bell"),
        Triple("Flint", "Margo", "Rooms Above the Bakery"),
        Triple("Ford", "Elias", "The Long Quiet"),
        Triple("Frey", "Nadia", "A Field Guide to Leaving"),
        Triple("Frost", "Colm", "The Ninth Lighthouse"),
        Triple("Gale", "Simone", "The Weather Clock"),
        Triple("Garnet", "Theo", "Small Hours"),
        Triple("Gaskell", "Mara", "The Lantern Weir"),
        Triple("Gilroy", "Pen", "Under the Plane Trees"),
        Triple("Glass", "Henry", "The Cartwright Letters"),
        Triple("Glenn", "Ottilie", "A Room of Blue Tiles"),
        Triple("Goode", "Ravi", "The Tidewater Year"),
        Triple("Gordon", "Lise", "North of the River"),
        Triple("Grace", "Wim", "The Clockmaker’s Niece"),
        Triple("Gray", "Edda", "Lamplight"),
        Triple("Greer", "Basil", "The Quiet Engine"),
        Triple("Grove", "Hana", "Wild Plum"),
    )

    val CARDS: List<CardCatalogCard> = RAW.mapIndexed { i, (surname, given, title) ->
        CardCatalogCard(
            surname = surname,
            given = given,
            title = title,
            callTop = "F",
            callBottom = surname.take(3).uppercase(),
            publisher = PUBLISHERS[(i * 3) % PUBLISHERS.size],
            year = 1948 + (i * 17) % 61,
            pages = 168 + (i * 53) % 260,
            tab = i % 3,
            status = if (i % 5 == 3) 1 else if (i % 7 == 5) 2 else 0,
        )
    }

    val STATUS_LABEL = listOf("On shelf", "On loan", "Reading room")

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    /**
     * Where the drawer stands at loop time t, in cards (0 = first card at the front). Rest 0–1.4 s;
     * thumb forward 1.4–4.6 s onto Gaskell with a small overshoot; hold; thumb back 5.8–7.6 s; rest.
     */
    fun autoPosition(t: Float): Float {
        val forward = easeInOutCubic(seg(t, 1.4f, 4.35f))
        val settle = sin(PI.toFloat() * seg(t, 4.1f, 4.8f)) * 0.24f * (1f - seg(t, 4.1f, 4.8f))
        val back = easeInOutCubic(seg(t, 5.8f, 7.6f))
        return TARGET * forward * (1f - back) + settle
    }

    /** A card's pose d cards behind the reading line: rotation (degrees), lift, scale, shade, opacity, back showing. */
    class Pose(val rot: Float, val ty: Float, val scale: Float, val shade: Float, val opacity: Float, val back: Float)

    fun cardPose(d: Float, lean: Float): Pose {
        if (d >= 0f) {
            val first = clamp01(d)
            val rot = (BACK_LEAN + lean) * first + 1.2f * min(max(d - 1f, 0f), 6f)
            val ty = -STEP_UP * d
            val scale = 1f - 0.012f * min(d, VISIBLE_BACK + 1f)
            val shade = min(0.07f * d, 0.45f)
            val opacity = 1f - clamp01(d - (VISIBLE_BACK - 1))
            return Pose(rot, ty, scale, shade, opacity, 0f)
        }
        val e = clamp01(-d)
        val rot = -FORWARD_LEAN * easeInOutCubic(e) - 4f * min(if (-d - 1f < 0f) 0f else -d - 1f, 3f)
        val opacity = 1f - clamp01(-d - (VISIBLE_FRONT - 1))
        val back = if (rot < -90f) 1f else 0f
        return Pose(rot, 0f, 1f, 0.12f * e, opacity, back)
    }

    /** Soft limits: past either end the drawer gives way slowly. */
    fun rubberBand(s: Float, max: Float): Float {
        if (s < 0f) return -(-s / (1f + -s / 0.9f)) * 0.55f
        if (s > max) {
            val over = s - max
            return max + over / (1f + over / 0.9f) * 0.55f
        }
        return s
    }
}

package com.ngocdevv.nativecook.blocks.navigation.claycategorytabs

import androidx.compose.ui.graphics.Color

internal object ClayCategoryTabsTheme {
    const val SEARCH_H = 56f
    const val STAGE_H = 244f
    const val ICON_Y = 108f
    const val ICON_S = 76f
    const val GHOST_S = 24f
    const val LABEL_Y = 204f
    const val HOP = 30f
    val page = Color(0xFF111015)
    val card = Color(0xFF1B1A21)
    val line = Color(0xFF2A2932)
    val ink = Color(0xFFF4F3F7)
    val mute = Color(0xFF8E8C99)
    val accent = Color(0xFFFF5A4F)
    val star = Color(0xFFFFC24A)
    data class Listing(val title: String, val meta: String, val time: String, val price: String, val unit: String, val rating: String) {
        val label get() = "$title, $meta, $time, $price per $unit, rated $rating"
    }
    data class Category(val name: String, val heading: String, val listings: List<Listing>)
    val categories = listOf(
        Category("Homes", "Stays near Lisbon", listOf(
            Listing("Cabin above the tide", "Ericeira · 2 beds", "Nov 12 – 17", "€142", "night", "4.92"),
            Listing("Whitewashed loft in Alfama", "Lisbon · 1 bed", "Nov 3 – 6", "€118", "night", "4.88"),
            Listing("Cork-oak farmhouse", "Alentejo · 4 beds", "Dec 1 – 5", "€205", "night", "4.97"),
        )),
        Category("Experiences", "Things to do nearby", listOf(
            Listing("Sunrise balloon over the plains", "Évora · 3 hours", "Sat, 6:10 AM", "€190", "guest", "4.99"),
            Listing("Paint your own azulejo", "Lisbon · 2 hours", "Every day", "€45", "guest", "4.91"),
            Listing("Surf the north swell", "Ericeira · 4 hours", "Sun, 9:00 AM", "€60", "guest", "4.86"),
        )),
        Category("Services", "Brought to your stay", listOf(
            Listing("Private seafood dinner", "Chef at home · up to 6", "Any evening", "€85", "guest", "4.95"),
            Listing("Morning yoga on your terrace", "60 minutes", "7 – 10 AM", "€40", "guest", "4.90"),
            Listing("Luggage drop and city transfer", "Door to door", "Any day", "€28", "trip", "4.84"),
        )),
    )
    data class Layout(val w: Float, val top: Float) {
        val searchTop get() = top + 8f
        val stageTop get() = searchTop + SEARCH_H + 8f
        val pagerTop get() = stageTop + STAGE_H
        val slot get() = minOf(114f, (w - 64f)/3f)
        val xs get() = listOf(w/2 - slot, w/2, w/2 + slot)
    }
}

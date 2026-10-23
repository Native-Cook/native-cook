package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.ui.graphics.Color
import kotlin.math.min
import kotlin.math.roundToInt

// What the screen says about a ball (ballEditorial.ts, chooseBallOrder.ts): how its name is set,
// the ground colour the page takes from it, and the three chips printed under it.

/** The carousel's order: the featured balls, then the rest in catalog order. */
internal fun chooseBallOrder(): List<BallId> =
    FEATURED_BALLS + BallId.entries.filter { it !in FEATURED_BALLS }

/** The name split for setting: everything before the last word, and the last word ("BALL"). */
internal fun wordmark(label: String): Pair<String, String> {
    val parts = label.trim().split(Regex("\\s+"))
    if (parts.size < 2) return label.uppercase() to ""
    return parts.dropLast(1).joinToString(" ").uppercase() to parts.last().uppercase()
}

/** Every hero word the wordmark shows, so its size is fitted to the widest of them once. */
internal val WORDMARK_HEROES: List<String> = BallId.entries.map { wordmark(it.label).first }

private fun tierLabel(tier: Int): String = when (tier) {
    1 -> "STANDARD"
    2 -> "SITUATIONAL"
    3 -> "RARE"
    else -> "CHERISH"
}

/**
 * The ground colour a ball gives the page: its aura pulled most of the way to the stage ink
 * (#06080C). The reach is 0.3, scaled down by the aura's own luminance so a bright aura does not
 * wash the page.
 */
internal fun groundFromBall(aura: Int): Color {
    val r = (aura shr 16) and 255
    val g = (aura shr 8) and 255
    val b = aura and 255
    val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    val reach = 0.3 * (1 - luma * 0.55)
    fun mix(channel: Int, ink: Int): Int = min(255.0, ink + (channel - ink) * reach).roundToInt()
    return Color(mix(r, 6), mix(g, 8), mix(b, 12))
}

/** The three chips under the ball, in reading order. */
internal fun ballFacts(ball: BallId): List<String> =
    listOf("×${formatCatchMod(ball.catchMod)} CATCH", tierLabel(ball.tier), keyword(ball.blurb))

private fun formatCatchMod(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private val KEYWORDS = listOf(
    "night", "cave", "water", "surfing", "fishing", "turn", "first", "friend", "gift", "speed",
    "heavy", "light", "fast", "slow", "moon", "sun", "bug", "dark",
)

/** One word lifted out of the blurb for the third chip, or FIELD when it has none of them. */
private fun keyword(blurb: String): String {
    val lower = blurb.lowercase()
    return (KEYWORDS.firstOrNull { lower.contains(it) } ?: "field").uppercase()
}

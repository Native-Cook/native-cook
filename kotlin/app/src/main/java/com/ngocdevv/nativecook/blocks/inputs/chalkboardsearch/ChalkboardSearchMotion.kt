package com.ngocdevv.nativecook.blocks.inputs.chalkboardsearch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/*
 Chalkboard Search — the loop script and the menu (a port of chalkboardSearchMotion.ts).

 LOOP = 9 s
   0.0 – 1.2   rest: the board, a faint "search the menu…" in chalk on the search line.
   1.0 – 1.2   the placeholder rubs away.
   1.2 – 3.3   "lemon tart" is chalked letter by letter (0.21 s a glyph), dust falling from
               under every stroke (0.9 s).
   3.4 – 4.45  three menu lines are chalked in, 0.3 s apart, 0.55 s each.
   4.45 – 6.0  hold.
   6.0 – 7.2   a felt eraser sweeps the board in four passes, leaving a haze where it went.
   7.2 – 8.6   the haze clears; 7.6 – 8.4 the placeholder comes back.
   8.6 – 9.0   rest.
*/

const val LOOP = 9f
internal const val CHALK_QUERY = "lemon tart"
internal const val CHALK_Q_AT = 1.2f
internal const val CHALK_Q_STEP = 0.21f
private const val SUG_AT = 3.4f
private const val SUG_STEP = 0.3f
internal const val CHALK_SUG_LEN = 0.55f
private const val ERASE_AT = 6.0f
internal const val CHALK_ERASE_END = 7.2f
private const val HAZE_END = 8.6f
internal const val CHALK_DUST_LIFE = 0.9f
internal const val CHALK_DUST_PER_GLYPH = 8

private class MenuItem(val name: String, val price: Int)

private val MENU = listOf(
    MenuItem("Lemon tart", 6), MenuItem("Lemon curd scone", 4), MenuItem("Tarte au citron", 7),
    MenuItem("Flat white", 4), MenuItem("Cardamom bun", 5), MenuItem("Oat milk latte", 5),
    MenuItem("Sourdough toast", 8), MenuItem("Almond croissant", 5), MenuItem("Rhubarb crumble", 6),
    MenuItem("Fresh mint tea", 3), MenuItem("Mushroom tartine", 11), MenuItem("Spiced chai", 4),
    MenuItem("Pear & ginger cake", 6), MenuItem("Iced lemonade", 4), MenuItem("Shakshuka", 12),
    MenuItem("Butter scone", 3),
)

private fun itemLine(i: MenuItem) = "${i.name} · \$${i.price}"

/** Up to three menu lines for a query: items sharing a word stem, best matches first. */
internal fun chalkSuggest(query: String): List<String> {
    val words = query.lowercase().split(Regex("[^a-zà-ÿ]+")).filter { it.length >= 2 }
    if (words.isEmpty()) return emptyList()
    val scored = MENU.map { item ->
        val name = item.name.lowercase()
        var score = 0
        for (w in words) {
            if (name.startsWith(w)) score += 3
            else if (name.split(Regex("\\s+")).any { it.startsWith(w.take(maxOf(3, w.length - 1))) }) score += 2
            else if (name.contains(w)) score += 1
        }
        item to score
    }.filter { it.second > 0 }
    return scored.sortedByDescending { it.second }.take(3).map { itemLine(it.first) }
}

internal val ChalkAutoSuggestions = listOf(itemLine(MENU[0]), itemLine(MENU[1]), itemLine(MENU[2]))

internal fun chalkClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun chalkSeg(t: Float, a: Float, b: Float) = chalkClamp01((t - a) / (b - a))
internal fun chalkEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
internal fun chalkEaseInOutSine(x: Float) = 0.5f - 0.5f * cos(PI.toFloat() * x)

/** When glyph i of the loop's query starts to be chalked. */
internal fun chalkGlyphAt(i: Int) = CHALK_Q_AT + i * CHALK_Q_STEP

/** The pen's x on the query line at loop time t, from the glyph edges. */
private fun autoPen(t: Float, lefts: FloatArray, rights: FloatArray): Float {
    if (lefts.isEmpty() || t < CHALK_Q_AT) return if (lefts.isNotEmpty()) lefts[0] else 0f
    val i = min(lefts.size - 1, floor((t - CHALK_Q_AT) / CHALK_Q_STEP).toInt())
    val u = chalkEaseInOutSine(chalkClamp01((t - chalkGlyphAt(i)) / CHALK_Q_STEP))
    return lefts[i] + (rights[i] - lefts[i]) * u
}

internal class ChalkBoard {
    var pen = 0f
    val sug = FloatArray(3)
    var trail = 0f
    var eraserOn = 0f
    var haze = 0f
    var placeholder = 1f
}

internal fun chalkAutoBoard(t: Float, lefts: FloatArray, rights: FloatArray, b: ChalkBoard = ChalkBoard()): ChalkBoard {
    val erasing = t >= ERASE_AT
    for (j in 0..2) b.sug[j] = if (t >= CHALK_ERASE_END) 0f else chalkEaseInOutSine(chalkSeg(t, SUG_AT + j * SUG_STEP, SUG_AT + j * SUG_STEP + CHALK_SUG_LEN))
    b.pen = if (t >= CHALK_ERASE_END) (if (lefts.isNotEmpty()) lefts[0] else 0f) else autoPen(t, lefts, rights)
    b.trail = if (erasing && t < HAZE_END) chalkEaseInOutCubic(chalkSeg(t, ERASE_AT, CHALK_ERASE_END)) else 0f
    b.eraserOn = if (erasing) chalkClamp01((t - ERASE_AT) / 0.15f) * (1 - chalkSeg(t, CHALK_ERASE_END - 0.15f, CHALK_ERASE_END)) else 0f
    b.haze = if (erasing) chalkClamp01((t - ERASE_AT) / 0.2f) * (1 - chalkEaseInOutSine(chalkSeg(t, CHALK_ERASE_END, HAZE_END))) else 0f
    b.placeholder = maxOf(1 - chalkSeg(t, 1.0f, 1.2f), chalkEaseInOutSine(chalkSeg(t, 7.6f, 8.4f)))
    return b
}

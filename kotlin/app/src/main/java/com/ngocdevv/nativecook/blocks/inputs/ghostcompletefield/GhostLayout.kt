package com.ngocdevv.nativecook.blocks.inputs.ghostcompletefield

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/*
 Ghost Complete Field: measuring and wrapping.

 The field draws its own text, so it needs to know where every glyph goes. `makeGhostLayout` wraps
 the typed text and the suggestion after it as ONE paragraph: accepting the suggestion then moves
 nothing, the grey glyphs just take ink where they already are.
*/

/** The drawn text's font: sizes in dp, advances measured at the screen's real pixel size. */
internal class GhostFont(val size: Float, val density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.DEFAULT
        textSize = size * density
        // One glyph per character: the layout places each by itself.
        fontFeatureSettings = "'liga' 0, 'clig' 0"
    }
    val ascent: Float
    val descent: Float

    init {
        val m = paint.fontMetrics
        ascent = m.ascent / density
        descent = m.descent / density
    }

    fun advances(text: String): FloatArray {
        val out = FloatArray(text.length)
        if (text.isNotEmpty()) paint.getTextWidths(text, out)
        for (i in out.indices) out[i] /= density
        return out
    }

    fun width(text: String): Float = advances(text).sum()

    /** The first baseline below a line's top, for this line height. */
    fun firstBaseline(lineHeight: Float) = (lineHeight - (descent - ascent)) / 2f - ascent
}

/** One word of a suggestion, on one line. */
internal class GhostWord(val a: Int, val b: Int, val x0: Float, val off: Float)

/** One stretch of suggested text inside the paragraph. */
internal class GhostRun(
    val start: Int,
    val end: Int,
    /** Length of the run along the reading direction, dp (the ink sweep travels this far). */
    val length: Float,
    val words: List<GhostWord>,
    /** Where the keycap chip sits: left edge and the top of its line. */
    val chipX: Float,
    val chipY: Float,
)

internal class GhostLayout(
    val text: String,
    val xs: FloatArray,
    val ys: FloatArray,
    /** Where the caret stands after k glyphs (k = 0 .. text.length): x and the top of its line. */
    val cx: FloatArray,
    val cy: FloatArray,
    val runs: List<GhostRun>,
    val lines: Int,
)

internal val EMPTY_LAYOUT = GhostLayout("", FloatArray(0), FloatArray(0), floatArrayOf(0f), floatArrayOf(0f), emptyList(), 1)

/** Most words a suggestion may have. */
internal const val MAX_WORDS = 8
/** Width kept free after a suggestion for the keycap chip. */
internal const val KEYCAP_W = 70f
private const val KEYCAP_GAP = 10f

/** What the field can draw: one glyph per UTF-16 unit, no line breaks unless asked. */
internal fun cleanText(text: String, multiline: Boolean): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        val c = ch.code
        if (c in 0xD800..0xDFFF) continue
        if (c == 10 && !multiline) continue
        if (c == 13 || c == 9) continue
        out.append(ch)
    }
    return out.toString()
}

/**
 Wraps `text` into `width` and describes the suggestion ranges in it.
 `ghosts` are [start, end) character ranges, in order and not overlapping.
*/
internal fun makeGhostLayout(
    font: GhostFont,
    text: String,
    ghosts: List<Pair<Int, Int>>,
    width: Float,
    lineHeight: Float,
    keycap: Boolean = true,
): GhostLayout {
    val widths = font.advances(text)
    val n = text.length
    val base = font.firstBaseline(lineHeight)
    val xs = FloatArray(n)
    val lineOf = IntArray(n)

    var x = 0f
    var line = 0
    var i = 0
    while (i < n) {
        val ch = text[i]
        if (ch == '\n') {
            xs[i] = x
            lineOf[i] = line
            widths[i] = 0f
            line += 1
            x = 0f
            i += 1
        } else if (ch == ' ') {
            xs[i] = x
            lineOf[i] = line
            x += widths[i]
            i += 1
        } else {
            var j = i
            var word = 0f
            while (j < n && text[j] != ' ' && text[j] != '\n') {
                word += widths[j]
                j += 1
            }
            if (x > 0f && x + word > width) {
                line += 1
                x = 0f
            }
            for (k in i until j) {
                if (x > 0f && x + widths[k] > width) {
                    line += 1
                    x = 0f
                }
                xs[k] = x
                lineOf[k] = line
                x += widths[k]
            }
            i = j
        }
    }

    val cx = FloatArray(n + 1)
    val cy = FloatArray(n + 1)
    for (k in 1..n) {
        if (text[k - 1] == '\n') {
            cx[k] = 0f
            cy[k] = (lineOf[k - 1] + 1) * lineHeight
        } else {
            cx[k] = min(width, xs[k - 1] + widths[k - 1])
            cy[k] = lineOf[k - 1] * lineHeight
        }
    }

    var lines = if (n > 0) lineOf[n - 1] + (if (text[n - 1] == '\n') 2 else 1) else 1
    val runs = ArrayList<GhostRun>(ghosts.size)
    for ((g0, g1) in ghosts) {
        val start = max(0, min(n, g0))
        val end = max(start, min(n, g1))
        val words = ArrayList<GhostWord>()
        var flow = 0f
        var lineStartX = 0f
        var lineStartFlow = 0f
        var current = -1
        var a = start
        for (k in start until end) {
            val newLine = lineOf[k] != current
            // A word ends where the next one starts (after its trailing spaces) or where the line does.
            val newWord = k > start && text[k] != ' ' && text[k - 1] == ' '
            if (k > a && (newLine || newWord) && words.size < MAX_WORDS - 1) {
                words.add(GhostWord(a, k, lineStartX, lineStartFlow))
                a = k
            }
            if (newLine) {
                current = lineOf[k]
                lineStartX = xs[k]
                lineStartFlow = flow
            }
            flow += widths[k]
        }
        if (end > a) words.add(GhostWord(a, end, lineStartX, lineStartFlow))

        var chipX = 0f
        var chipY = 0f
        if (end > start) {
            val last = end - 1
            val after = xs[last] + widths[last]
            if (after + KEYCAP_GAP + KEYCAP_W <= width) {
                chipX = after + KEYCAP_GAP
                chipY = lineOf[last] * lineHeight
            } else {
                chipY = (lineOf[last] + 1) * lineHeight
                if (keycap) lines = max(lines, lineOf[last] + 2)
            }
        }
        runs.add(GhostRun(start, end, flow, words, chipX, chipY))
    }

    val ys = FloatArray(n) { lineOf[it] * lineHeight + base }
    return GhostLayout(text, xs, ys, cx, cy, runs, lines)
}

// ---- Suggestions -------------------------------------------------------------------------------------

/** Phrases the demo's mail app completes. A real app would ask its model instead. */
private val PHRASES = listOf(
    "Hi Mara, thanks for the notes — ",
    "can we walk through them Thursday?",
    "thanks for getting back to me so quickly.",
    "let me know what works for you.",
    "sounds good to me.",
    "see you on Thursday.",
    "happy to take a look this week.",
    "I will send the updated draft tonight.",
    "looking forward to it.",
    "Best, Ines",
)
/** Letters that must be typed before a phrase is offered. */
private const val MIN_MATCH = 3

/**
 The rest of a known phrase that the end of `text` has started, or null. The match starts at a
 word boundary and ignores case; the longest start wins.
*/
internal fun suggestPhrase(text: String): String? {
    if (text.length < MIN_MATCH || text.endsWith('\n')) return null
    val from = max(0, text.length - 40)
    for (s in from..text.length - MIN_MATCH) {
        if (s > 0 && text[s - 1] != ' ' && text[s - 1] != '\n') continue
        val tail = text.substring(s).lowercase()
        if (tail.trim().length < MIN_MATCH) continue
        for (phrase in PHRASES) {
            if (phrase.length > tail.length && phrase.lowercase().startsWith(tail)) return phrase.substring(tail.length)
        }
    }
    return null
}

/** Mail domains offered after the @ of an address. Invented. */
private val DOMAINS = listOf("postbox.co", "northmail.com", "fernhill.org", "studio-okafor.com")

/** Up to two domains that continue what follows the @, or none when there is no @ yet. */
internal fun suggestDomains(address: String): List<String> {
    val at = address.indexOf('@')
    if (at < 1 || address.indexOf('@', at + 1) >= 0 || address.contains(' ')) return emptyList()
    val started = address.substring(at + 1).lowercase()
    return DOMAINS.filter { it.startsWith(started) && it != started }.take(2)
}

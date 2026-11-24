package com.ngocdevv.nativecook.blocks.text.glyphreflowedit

import android.graphics.Paint
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.ARC_MAX
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.ARC_MIN
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.ARC_PER_POINT
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.LETTER_LAG
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Pairing and measurement, ported from the React Native block's `match.ts` and `layout.ts`.
 Nothing here runs per frame. All positions are in dp; glyphs are measured on a px paint and divided by the density.
 */

/** Which letters of the old text become which letters of the new one: (from index, to index). */
internal object GlyphMatch {
    private class Word(val start: Int, val end: Int, val key: String)

    private fun wordsOf(text: String): List<Word> {
        val out = ArrayList<Word>()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i += 1
                continue
            }
            var end = i
            while (end < text.length && !text[end].isWhitespace()) end += 1
            out.add(Word(i, end, text.substring(i, end).lowercase().filter { it.isLetterOrDigit() }))
            i = end
        }
        return out
    }

    /** Longest common subsequence, as index pairs in increasing order. */
    private fun <T> commonRun(a: List<T>, b: List<T>): List<IntArray> {
        val row = b.size + 1
        val table = IntArray((a.size + 1) * row)
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                table[i * row + j] = if (a[i] == b[j]) table[(i + 1) * row + j + 1] + 1
                else max(table[(i + 1) * row + j], table[i * row + j + 1])
            }
        }
        val out = ArrayList<IntArray>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j]) {
                out.add(intArrayOf(i, j))
                i += 1
                j += 1
            } else if (table[(i + 1) * row + j] >= table[i * row + j + 1]) {
                i += 1
            } else {
                j += 1
            }
        }
        return out
    }

    private fun lettersIn(text: String, from: Int, to: Int): List<Int> {
        val at = ArrayList<Int>()
        for (i in from until to) if (!text[i].isWhitespace()) at.add(i)
        return at
    }

    private fun pairLetters(a: String, b: String, aFrom: Int, aTo: Int, bFrom: Int, bTo: Int, out: MutableList<IntArray>) {
        val aAt = lettersIn(a, aFrom, aTo)
        val bAt = lettersIn(b, bFrom, bTo)
        val run = commonRun(aAt.map { a[it] }, bAt.map { b[it] })
        for (pair in run) out.add(intArrayOf(aAt[pair[0]], bAt[pair[1]]))
    }

    /**
     * Whole words that survive the edit are anchors, so they travel together. In the gaps between anchors, the
     * letters the two versions share are paired in reading order. Everything unpaired fades.
     */
    fun match(a: String, b: String): List<IntArray> {
        val aWords = wordsOf(a)
        val bWords = wordsOf(b)
        val anchors = commonRun(aWords.map { it.key }, bWords.map { it.key })
        val out = ArrayList<IntArray>()
        var aCursor = 0
        var bCursor = 0
        for (pair in anchors) {
            val wa = aWords[pair[0]]
            val wb = bWords[pair[1]]
            pairLetters(a, b, aCursor, wa.start, bCursor, wb.start, out)
            pairLetters(a, b, wa.start, wa.end, wb.start, wb.end, out)
            aCursor = wa.end
            bCursor = wb.end
        }
        pairLetters(a, b, aCursor, a.length, bCursor, b.length, out)
        return out
    }
}

/** One version laid out: a pen position per character (dp, baseline y). */
internal class GlyphLaid(val x: FloatArray, val y: FloatArray, val line: IntArray, val lines: Int, val height: Float)

/** The tables one rewrite's morph draws from. */
internal class GlyphMorph(
    // Glyphs both versions share.
    val chars: CharArray,
    val ax: FloatArray,
    val ay: FloatArray,
    val bx: FloatArray,
    val by: FloatArray,
    val arc: FloatArray,
    val delay: FloatArray,
    // Glyphs only the original has.
    val outChars: CharArray,
    val outX: FloatArray,
    val outY: FloatArray,
    val outDelay: FloatArray,
    // Glyphs only the rewrite has.
    val inChars: CharArray,
    val inX: FloatArray,
    val inY: FloatArray,
    val inDelay: FloatArray,
)

internal object GlyphLayout {
    /**
     * Greedy word wrap on real glyph advances. [paint] is set to the px size; [density] turns its advances into dp.
     * [lineHeight] and [width] are in dp.
     */
    fun layOut(paint: Paint, density: Float, text: String, width: Float, lineHeight: Float): GlyphLaid {
        val advances = FloatArray(text.length)
        paint.getTextWidths(text, advances)
        for (k in advances.indices) advances[k] /= density
        val metrics = paint.fontMetrics
        val ascent = metrics.ascent / density
        val descent = metrics.descent / density
        val baseline = (lineHeight - (descent - ascent)) / 2 - ascent
        val x = FloatArray(text.length)
        val y = FloatArray(text.length)
        val line = IntArray(text.length)
        var row = 0
        var pen = 0f
        var i = 0
        while (i < text.length) {
            var end = i
            while (end < text.length && text[end] != ' ') end += 1
            var wordWidth = 0f
            for (k in i until end) wordWidth += advances[k]
            if (pen > 0 && pen + wordWidth > width) {
                row += 1
                pen = 0f
            }
            for (k in i until end) {
                x[k] = pen
                y[k] = row * lineHeight + baseline
                line[k] = row
                pen += advances[k]
            }
            if (end < text.length) {
                x[end] = pen
                y[end] = row * lineHeight + baseline
                line[end] = row
                pen += advances[end]
            }
            i = end + 1
        }
        return GlyphLaid(x, y, line, row + 1, (row + 1) * lineHeight)
    }

    private fun hash(i: Int): Double {
        val v = sin(i * 12.9898) * 43758.5453
        return v - floor(v)
    }

    private fun orderOf(laid: GlyphLaid, i: Int, width: Float): Float {
        val rows = max(laid.lines - 1, 1)
        return min(laid.line[i].toFloat() / rows * 0.62f + laid.x[i] / width * 0.38f, 1f)
    }

    private class Flight(val arc: Float, val delay: Float, var seen: Int)

    fun buildMorph(fromText: String, toText: String, from: GlyphLaid, to: GlyphLaid, width: Float): GlyphMorph {
        val pairs = GlyphMatch.match(fromText, toText)
        val usedFrom = BooleanArray(fromText.length)
        val usedTo = BooleanArray(toText.length)
        // Letters landing in one word share its arc and its start, so words stay readable in flight.
        val wordOf = IntArray(toText.length)
        for (i in 1 until toText.length) {
            wordOf[i] = wordOf[i - 1] + if (toText[i] != ' ' && toText[i - 1] == ' ') 1 else 0
        }
        val flights = HashMap<Int, Flight>()
        val n = pairs.size
        val chars = CharArray(n)
        val ax = FloatArray(n)
        val ay = FloatArray(n)
        val bx = FloatArray(n)
        val by = FloatArray(n)
        val arc = FloatArray(n)
        val delay = FloatArray(n)
        pairs.forEachIndexed { k, pair ->
            val a = pair[0]
            val b = pair[1]
            usedFrom[a] = true
            usedTo[b] = true
            val distance = hypot(to.x[b] - from.x[a], to.y[b] - from.y[a])
            val flight = flights.getOrPut(wordOf[b]) {
                val height = min(ARC_MIN + distance * ARC_PER_POINT, ARC_MAX) * (0.8f + hash(wordOf[b]).toFloat() * 0.2f)
                Flight(-height, orderOf(to, b, width), 0)
            }
            chars[k] = fromText[a]
            ax[k] = from.x[a]
            ay[k] = from.y[a]
            bx[k] = to.x[b]
            by[k] = to.y[b]
            arc[k] = if (distance < 0.5f) 0f else flight.arc
            delay[k] = min(flight.delay + flight.seen * LETTER_LAG, 1f)
            flight.seen += 1
        }
        val outIdx = fromText.indices.filter { !usedFrom[it] && fromText[it] != ' ' }
        val inIdx = toText.indices.filter { !usedTo[it] && toText[it] != ' ' }
        return GlyphMorph(
            chars, ax, ay, bx, by, arc, delay,
            CharArray(outIdx.size) { fromText[outIdx[it]] },
            FloatArray(outIdx.size) { from.x[outIdx[it]] },
            FloatArray(outIdx.size) { from.y[outIdx[it]] },
            FloatArray(outIdx.size) { orderOf(from, outIdx[it], width) },
            CharArray(inIdx.size) { toText[inIdx[it]] },
            FloatArray(inIdx.size) { to.x[inIdx[it]] },
            FloatArray(inIdx.size) { to.y[inIdx[it]] },
            FloatArray(inIdx.size) { orderOf(to, inIdx[it], width) },
        )
    }

    class Stats(val words: String, val read: String, val change: String)

    fun statsOf(original: String, text: String): Stats {
        val words = text.trim().split(Regex("\\s+")).size
        val base = original.trim().split(Regex("\\s+")).size
        // JS Math.round: halves round up.
        val change = floor((words - base).toDouble() / base * 100 + 0.5).toInt()
        val read = max(floor(words / 3.6 + 0.5).toInt(), 1)
        return Stats(
            words.toString(),
            "$read s",
            if (change == 0) "0%" else "${if (change > 0) "+" else "−"}${abs(change)}%",
        )
    }

    /** Line height for a type size: `round(size * 1.38)`. */
    fun leadingOf(size: Int, leading: Float) = (size * leading).roundToInt().toFloat()
}

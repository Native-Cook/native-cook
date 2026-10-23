package com.ngocdevv.nativecook.blocks.text.karaokelyricshighlight

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 Karaoke Lyrics Highlight — the lyrics, their timing, the layout and the pure timeline. A port of
 KaraokeLyricsHighlightModel.swift; the numbers are the same.

 The chorus has four lines and repeats, so the lyric list is endless: absolute line i shows
 chorus line i mod 4, and the list scrolls down it forever. Every line gets a 2.5 s slot: its
 words fill between 0.1 and 2.05 s (times below), and at 1.95 s the list starts scrolling to the
 next line, reaching it exactly at 2.5 s along an ease-out-back curve (a small overshoot, like a
 soft spring). Four slots make the 10 s loop, ending one chorus further down, which looks exactly
 like its start.
 */

internal data class KaraokeWord(
    val text: String,
    /** Seconds into the line's slot when the fill starts and ends. */
    val start: Double,
    val end: Double,
    /** A held note: its letters wave while it fills. */
    val held: Boolean = false,
)

internal object KaraokeSong {
    const val TITLE = "Paper Satellites"
    const val ARTIST = "Lumen Fair"
    /** The chorus starts 1:12 into a 3:43 song. */
    const val CHORUS_AT = 72.0
    const val LENGTH = 223.0

    val chorus = listOf(
        listOf(
            KaraokeWord("We", 0.15, 0.40), KaraokeWord("fold", 0.40, 0.70), KaraokeWord("the", 0.70, 0.88),
            KaraokeWord("night", 0.88, 1.25), KaraokeWord("into", 1.25, 1.52), KaraokeWord("paper", 1.52, 2.0),
        ),
        listOf(
            KaraokeWord("and", 0.15, 0.33), KaraokeWord("throw", 0.33, 0.68), KaraokeWord("it", 0.68, 0.82),
            KaraokeWord("past", 0.82, 1.10), KaraokeWord("the", 1.10, 1.25), KaraokeWord("streetlights", 1.25, 2.0),
        ),
        listOf(
            KaraokeWord("Ohhh,", 0.10, 1.05, held = true), KaraokeWord("we're", 1.05, 1.30),
            KaraokeWord("paper", 1.30, 1.62), KaraokeWord("satellites", 1.62, 2.05),
        ),
        listOf(
            KaraokeWord("still", 0.15, 0.42), KaraokeWord("circling,", 0.42, 0.98), KaraokeWord("still", 1.00, 1.30),
            KaraokeWord("bright", 1.30, 2.0),
        ),
    )
}

internal object KaraokeTimeline {
    const val SLOT = 2.5
    const val LINES = 4
    const val LOOP = 10.0
    const val SCROLL_START = 1.95
    /** The playhead glides from the end of the chorus back to its start over the last 0.5 s. */
    const val REWIND = 0.5

    /** Where the list is, in lines from the loop's first line, [e] seconds into the loop (0 ≤ e < 10). */
    fun scroll(e: Double): Double {
        val k = min((LINES - 1).toDouble(), floor(e / SLOT))
        val within = e - k * SLOT
        return k + easeOutBack(max(0.0, (within - SCROLL_START) / (SLOT - SCROLL_START)))
    }

    /** The line being sung (0…3) and how far into its slot. */
    fun singingLine(e: Double): Int = min(LINES - 1, (e / SLOT).toInt())

    fun fill(w: KaraokeWord, tau: Double) = ((tau - w.start) / (w.end - w.start)).coerceIn(0.0, 1.0)

    /** How much a word is lifted (0…1): in over 0.12 s as it starts, out over 0.2 s as it ends. */
    fun lift(w: KaraokeWord, tau: Double) = smoothstep(w.start, w.start + 0.12, tau) * (1 - smoothstep(w.end - 0.05, w.end + 0.15, tau))

    /** The song time in seconds shown by the scrubber. */
    fun playhead(e: Double): Double {
        val end = KaraokeSong.CHORUS_AT + LOOP - REWIND
        if (e < LOOP - REWIND) return KaraokeSong.CHORUS_AT + e
        return end + (KaraokeSong.CHORUS_AT - end) * easeInOut((e - (LOOP - REWIND)) / REWIND)
    }

    fun easeOutBack(t: Double): Double {
        val c1 = 1.2
        val c3 = c1 + 1
        val u = t.coerceIn(0.0, 1.0) - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }

    fun easeInOut(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Each line's look by its distance from the focus, d = line − scroll (negative: already sung). */
    fun sungAlpha(d: Double) = if (d >= 0) 1.0 else 1 - 0.75 * min(1.0, -d)
    fun unsungAlpha(d: Double) = if (d <= 0) 0.35 else 0.35 - 0.1 * min(d, 2.0)
    fun blur(d: Double) = if (d < 0) 2.5 * min(1.0, -d) else 1.5 * min(d, 2.0)
}

/** The chorus laid out for a width (dp): lines wrapped into rows by measured advances. */
internal class KaraokeLayout(width: Float, typeface: Typeface) {
    class PlacedWord(val word: KaraokeWord, val x: Double, val row: Int, val width: Double, val letters: List<Pair<Char, Double>>)
    class Line(val words: List<PlacedWord>, val height: Double)

    val lines: List<Line>
    /** Top of each chorus line within one chorus, and the height of one whole chorus. */
    val prefix: List<Double>
    val cycle: Double

    init {
        val paint = Paint().apply { this.typeface = typeface; textSize = 1000f }
        fun adv(ch: Char) = paint.measureText(ch.toString()) / 1000.0 * SIZE
        val maxWidth = width - 2 * MARGIN
        val space = adv(' ')
        val built = KaraokeSong.chorus.map { words ->
            val placed = mutableListOf<PlacedWord>()
            var x = 0.0
            var row = 0
            for (w in words) {
                val advances = w.text.map { adv(it) }
                val ww = advances.sum()
                if (x > 0 && x + ww > maxWidth) {
                    row += 1
                    x = 0.0
                }
                var lx = 0.0
                val letters = w.text.mapIndexed { i, ch -> (ch to lx).also { lx += advances[i] } }
                placed += PlacedWord(w, MARGIN + x, row, ww, letters)
                x += ww + space
            }
            Line(placed, (placed.maxOf { it.row } + 1) * ROW_HEIGHT)
        }
        lines = built
        val p = mutableListOf<Double>()
        var y = 0.0
        for (l in built) {
            p += y
            y += l.height + GAP
        }
        prefix = p
        cycle = y
    }

    fun line(i: Int): Line = lines[((i % lines.size) + lines.size) % lines.size]

    /** Top of absolute line i, from the top of line 0. */
    fun top(i: Int): Double {
        val n = lines.size
        val c = floor(i.toDouble() / n).toInt()
        return c * cycle + prefix[i - c * n]
    }

    /** The scroll offset in dp for a scroll position in lines. */
    fun offset(s: Double): Double {
        val i = floor(s).toInt()
        return top(i) + (s - i) * (line(i).height + GAP)
    }

    /** The scroll position in lines for an offset in dp. */
    fun position(y: Double): Double {
        val n = lines.size
        val c = floor(y / cycle)
        var r = y - c * cycle
        for (k in 0 until n) {
            val step = lines[k].height + GAP
            if (r < step || k == n - 1) return c * n + k + min(1.0, r / step)
            r -= step
        }
        return c * n
    }

    companion object {
        const val SIZE = 32.0
        const val ROW_HEIGHT = 38.0
        const val GAP = 22.0
        const val MARGIN = 24.0
    }
}

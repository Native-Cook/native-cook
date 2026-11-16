package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs


/** Round-dot numerals (port of bvl-glyphs.ts). A dot's x is in half steps, -4..4; a row is a 9-bit mask (bit x2 + 4). */
internal object BreathVolumeLungsGlyphs {
    private val SHAPES: Map<Char, Array<IntArray>> = mapOf(
        '0' to arrayOf(intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(-4, 4), intArrayOf(-4, 4), intArrayOf(-4, 4), intArrayOf(-3, 3), intArrayOf(-1, 1)),
        '1' to arrayOf(intArrayOf(1), intArrayOf(-1, 1), intArrayOf(-3, 1), intArrayOf(1), intArrayOf(1), intArrayOf(1), intArrayOf(1)),
        '2' to arrayOf(intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(3), intArrayOf(1), intArrayOf(-1), intArrayOf(-3), intArrayOf(-4, -2, 0, 2, 4)),
        '3' to arrayOf(intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(3), intArrayOf(-1, 1), intArrayOf(3), intArrayOf(-3, 3), intArrayOf(-1, 1)),
        '4' to arrayOf(intArrayOf(2), intArrayOf(0, 2), intArrayOf(-2, 2), intArrayOf(-4, 2), intArrayOf(-4, -2, 0, 2, 4), intArrayOf(2), intArrayOf(2)),
        '5' to arrayOf(intArrayOf(-2, 0, 2, 4), intArrayOf(-4), intArrayOf(-4), intArrayOf(-3, -1, 1), intArrayOf(4), intArrayOf(-3, 3), intArrayOf(-1, 1)),
        '6' to arrayOf(intArrayOf(0, 2), intArrayOf(-2), intArrayOf(-4), intArrayOf(-4, -2, 0, 2), intArrayOf(-4, 4), intArrayOf(-3, 3), intArrayOf(-1, 1)),
        '7' to arrayOf(intArrayOf(-4, -2, 0, 2, 4), intArrayOf(4), intArrayOf(2), intArrayOf(1), intArrayOf(-1), intArrayOf(-1), intArrayOf(-1)),
        '8' to arrayOf(intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(-3, 3), intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(-3, 3), intArrayOf(-1, 1)),
        '9' to arrayOf(intArrayOf(-1, 1), intArrayOf(-3, 3), intArrayOf(-4, 4), intArrayOf(-2, 0, 2, 4), intArrayOf(4), intArrayOf(2), intArrayOf(-2, 0)),
        '.' to arrayOf(intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(-1, 1), intArrayOf(-1, 1)),
        ' ' to arrayOf(intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf()),
    )
    private const val ORDER = "0123456789. "

    private fun maskOf(row: IntArray): Int {
        var m = 0
        for (x2 in row) m += 1 shl (x2 + 4)
        return m
    }

    /** Glyph g (index into ORDER) row r at `MASKS[g * 7 + r]`. */
    val MASKS: IntArray = ORDER.flatMap { ch -> SHAPES.getValue(ch).map(::maskOf) }.toIntArray()
    const val ADVANCE_DIGIT = 5.4
    const val ADVANCE_POINT = 3.2
    const val SLOTS = 4

    fun glyphIndex(code: Int): Int = if (code in 48..57) code - 48 else if (code == 46) 10 else 11

    /** Pack the readout: per slot 8 floats (7 row codes leaving*512+arriving), then x (4) and kind (4). */
    fun packReadout(from: String, to: String): FloatArray {
        val out = FloatArray(40)
        val n = to.length
        val m = from.length
        var width = 0.0
        val adv = DoubleArray(n)
        for (i in 0 until n) {
            adv[i] = if (to[i].code == 46) ADVANCE_POINT else ADVANCE_DIGIT
            width += adv[i]
        }
        var acc = 0.0
        val cx = DoubleArray(n)
        for (i in 0 until n) { cx[i] = acc + adv[i] / 2; acc += adv[i] }
        val mid = width / 2
        for (s in 0 until SLOTS) {
            val ti = s - (SLOTS - n)
            val fi = s - (SLOTS - m)
            val nowIdx = if (ti >= 0) glyphIndex(to[ti].code) else 11
            val oldIdx = if (fi >= 0) glyphIndex(from[fi].code) else 11
            for (r in 0 until 7) {
                out[s * 8 + r] = (MASKS[oldIdx * 7 + r] * 512 + MASKS[nowIdx * 7 + r]).toFloat()
            }
            out[32 + s] = if (ti >= 0) (cx[ti] - mid).toFloat() else 0f
            out[36 + s] = if (ti >= 0 && to[ti].code == 46) 1f else 0f
        }
        return out
    }
}

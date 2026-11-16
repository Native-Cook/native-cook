package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import kotlin.math.max
import kotlin.math.min

/** One page of the story: a glyph, two field colours, the face colour, a direction and the copy. */
internal class ExtrusionRecapStoryPage(
    val glyph: String, val fieldA: Int, val fieldB: Int, val face: Int, val direction: DoubleArray,
    val headline: String, val caption: String?, val stat: Int? = null,
)

/** Colours, type, layout numbers, glyphs and the three pages of the RN block (`ers-theme.ts`), in design points of a 402 x 874 screen. */
internal object ExtrusionRecapStoryTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    const val INK = 0xFFF4F83C.toInt()
    const val LIGHT_INK = 0xFFFFF3A0.toInt()
    const val PAGE = 0xFF080407.toInt()
    const val CAPTION = 0xFFF7F1B2.toInt()
    const val PEEK_INK = 0xFF14070F.toInt()
    const val PEEK_MUTE = 0x9E14070F.toInt() // rgba(20,7,15,0.62)

    const val DISPLAY_SIZE = 54.0
    const val DISPLAY_LINE = 51.0
    const val DISPLAY_TRACKING = -2.1
    const val CAPTION_SIZE = 15.0
    const val BRAND_SIZE = 22.0
    const val COUNT_SIZE = 14.0

    const val CARD_RADIUS = 28.0
    const val EDGE = 20.0
    const val PEEK = 48.0
    const val GAP = 8.0
    const val HERO_X = 198.0
    const val HERO_Y = 495.0

    const val TITLE_Y = 136.0

    const val BRAND = "Lilt"
    const val NEXT = "Next page"
    const val PREVIOUS = "Previous page"
    const val CLOSE = "Close recap"
    const val UP_NEXT = "Up next"
    const val REPLAY = "Replay"

    const val NOTE = "M530 455 L486 627 C469 689 419 715 382 690 L336 641 L308 715 C330 770 290 842 232 873 C152 924 65 901 70 800 C39 731 74 682 131 674 C167 650 218 645 254 661 L277 698 L143 577 L155 510 L335 539 C364 523 368 499 356 480 Z"
    const val HEART = "M128 236 C110 218 12 150 12 83 C12 18 90 0 128 57 C166 0 244 18 244 83 C244 150 146 218 128 236Z"
    const val BOLT = "M138 9 L236 9 L161 96 L241 96 L60 247 L99 143 L24 143 Z"

    val RECAP = listOf(
        ExtrusionRecapStoryPage(NOTE, 0xFF080205.toInt(), 0xFFF26AB4.toInt(), 0xFFD8004A.toInt(), doubleArrayOf(0.43, 0.9), "That very\nmusic", "Tracks that captured your year like nothing else"),
        ExtrusionRecapStoryPage(HEART, 0xFFD51B39.toInt(), 0xFFF34455.toInt(), 0xFFD8183B.toInt(), doubleArrayOf(-0.16, 0.98), "new discoveries", "Each time your heart said yes, a track found its way in.", stat = 264),
        ExtrusionRecapStoryPage(BOLT, 0xFF020B05.toInt(), 0xFF3DF03C.toInt(), 0xFF32DF31.toInt(), doubleArrayOf(0.52, 0.85), "Remember\nthat moment?", "You found new music. And a little more of yourself."),
    )

    fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)

    class Layout(val s: Double, val titleY: Double, val headerY: Double, val cardBottom: Double, val heroY: Double, val captionY: Double, val peekRow: Double)

    fun makeLayout(w: Double, h: Double, top: Double, bottom: Double): Layout {
        val s = w / REF_W
        val cardBottom = h - max(bottom, SAFE_BOTTOM * s) - PEEK * s
        val titleY = top + (TITLE_Y - SAFE_TOP) * s
        return Layout(
            s, titleY, top, cardBottom, min(h * (HERO_Y / REF_H), cardBottom - 145 * s), titleY + 122 * s,
            max(h - max(bottom, SAFE_BOTTOM * s) - cardBottom - GAP * s, 32 * s),
        )
    }
}

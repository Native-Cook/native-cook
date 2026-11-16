package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** One track of the queue: a title, an artist line and a procedural cover variant (0, 1, 2). */
class WovenSpeakerPlayerTrack(val title: String, val subtitle: String? = null, val cover: Int? = null)

/**
 * Every colour, size and copy token of the RN block (`wsp-theme.ts`), measured on the shot. Everything is design pt on the 402 x 874 canvas, scaled at
 * run time by S = min(W / 402, 1) x size / 330 (the dome stays 330 pt on a 440 pt phone).
 */
internal object WovenSpeakerPlayerTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val page = rgb(0x131313)
    val glassTop = rgb(0x101020)
    val glassBottom = rgb(0x0C0916)
    val bezelDark = rgb(0x050506)
    val bezelHi = rgb(0x9A9CA6)
    val bezelLo = rgb(0x1B1C1F)
    val accent = rgb(0xFF3B30)
    val accentTail = rgb(0xFF7A45)
    val bloom = rgb(0xB3181F)
    val coverTop = rgb(0xC6303A)
    val coverBottom = rgb(0x5C0E14)
    val sun = rgb(0xFF7452)
    val sunGlow = rgb(0xFF8A5C)
    val figure = rgb(0x24060A)
    val figureLo = rgb(0x120204)
    val rim = rgb(0xFFB088)
    val amber = rgb(0xFFB020)
    val ink = rgb(0xFFFFFF)
    val mute = rgb(0x8E8E93)
    val pill = rgb(0xFFFFFF)
    val time = rgb(0xCFCDD8)
    val glyph = rgb(0xE6E3F2)
    val glyphLo = rgb(0xB3AFC6)
    val discFill = rgb(0x8C78C8)
    val avatar = rgb(0xF2B5AE)
    val avatarInk = rgb(0x6A2B30)
    val displayMute = rgb(0xA9A5B5)

    // the dome and its display
    const val DOME_R = 165.0
    const val DISPLAY_R = 102.8
    const val BEZEL_OUTER = 105.4
    const val BEZEL_RING = 104.0
    const val BEZEL_W = 1.3
    const val ARC_R = 96.5
    const val ARC_W = 1.7

    const val TIME_Y = -80.0
    const val TIME_SIZE = 12.0
    const val TIME_BIG = 17.0
    const val ROW_Y = -57.0
    const val ROW_DX = 30.0
    const val ROW_CAST = 13.0
    const val ROW_AVATAR = 16.0
    const val ROW_AVATAR_INK = 9.0
    const val ROW_WAVE = 14.0
    const val COVER_SIZE = 72.0
    const val COVER_R = 20.0
    const val DISC_DX = 67.0
    const val DISC_SIZE = 26.0
    const val DISC_GLYPH = 9.0
    const val TRACK_Y = 54.0
    const val TRACK_SIZE = 15.0
    const val ARTIST_Y = 73.0
    const val ARTIST_SIZE = 8.5
    const val ARTIST_TRACK = 0.12

    const val TITLE_Y = 590.0
    const val TITLE_SIZE = 38.0
    const val LINE_Y = 632.0
    const val LINE_SIZE = 17.0
    const val PILL_Y = 672.0
    const val PILL_W = 124.0
    const val PILL_H = 40.0
    const val PILL_STROKE = 1.0
    const val PILL_SIZE = 15.0

    const val WASH_R = 300.0
    const val WASH_BASE = 0.05
    const val WASH_BEAT = 0.08
    const val WASH_TITLE = 0.1
    const val BLOOM_R0 = 60.0
    const val BLOOM_R1 = 84.0
    const val BLOOM_DX = 24.0
    const val BLOOM_DY = -6.0

    const val HEADLINE = "Press play."
    const val SUBTITLE = "Introducing Aria OS"
    const val PILL_TEXT = "Learn more"
    const val SUBTITLE_PLAYING = "Aria OS · Living room"
    const val PILL_PLAYING = "Open queue"
    const val POSITION = "Track position"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val PREVIOUS = "Previous track"
    const val NEXT = "Next track"
    const val HINT_DOME = "Turn the dome to scrub the track. Swipe up or down to seek five seconds."
    const val HINT_TITLE = "Swipe left or right to change track"

    /** the demo queue: invented tracks, no real artist, label or song */
    val DEMO_QUEUE = listOf(
        WovenSpeakerPlayerTrack("Lowlight", "NORTH OF EIGHT", 0),
        WovenSpeakerPlayerTrack("Paper Moons", "JUNO VALE", 1),
        WovenSpeakerPlayerTrack("Static Bloom", "HALVES", 2),
    )

    const val DEFAULT_SIZE = 360.0
    private const val BOTTOM_GAP = 40.0

    fun rgbOf(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    /** The text block stands on the bottom safe area, 40 pt up; the dome is centred in the space above it. */
    class Layout(val w: Double, val h: Double, size: Double, insetTop: Double, insetBottom: Double) {
        val s = min(w / REF_W, 1.0) * (size / 330)
        private val blockBottom = PILL_Y + PILL_H
        private val pillBottom = h - max(insetBottom, 16.0) - BOTTOM_GAP * s
        fun y(dy: Double) = pillBottom + (dy - blockBottom) * s
        fun x(dx: Double) = w / 2 + dx * s
        private val titleTop = y(TITLE_Y - TITLE_SIZE * 0.5)
        val cx = w / 2
        val cy = (max(insetTop, 20.0) + titleTop) / 2

        /** the dome's centre in design pt, for the canvas group that draws in design pt */
        val domeY = (cy - y(0.0)) / s
    }
}

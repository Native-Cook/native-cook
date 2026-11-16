package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import android.graphics.Color
import kotlin.math.max

/** Every colour, size and copy token of the RN block (`prv-theme.ts`), measured on the pin, on the 402 x 874 pt canvas. */
internal object ParticleRibbonVoiceTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val page = rgb(0x030304)
    val ink = rgb(0xFDFDFE)
    val greeting = rgb(0x95959A)
    val gold = rgb(0xE7B94A)
    val green = rgb(0x6EDC80)
    val cyan = rgb(0x27D3E6)

    const val GREETING_SIZE = 35.0
    const val GREETING_LINE = 44.0
    const val GREETING_TRACK = -0.024
    const val BODY_SIZE = 35.0
    const val BODY_LINE = 44.0
    const val BODY_TRACK = -0.024
    const val TEXT_BOTTOM = 376.0

    const val Y0 = 476.0
    const val AMP = 24.0
    const val WAVELENGTH = 402.0
    const val WAVE_P0 = -0.07
    const val WD_IDLE = 26.0
    const val WD_LEVEL = 20.0
    const val WD_THREAD = 6.0
    const val WD_SPEAK = 7.0
    const val AMP_LEVEL = 4.5
    const val STRIP_TOP = 330.0
    const val STRIP_BOTTOM = 600.0

    const val TWIST_AMP = 0.55
    const val TWIST_LENGTH = 300.0
    const val TWIST_PHASE = 1.0
    const val TWIST_SLOPE = 0.4
    const val TAIL_LEFT = -1.4
    const val TAIL_RIGHT = 0.0
    const val TAIL_WIDTH = 55.0

    const val ASK_Y = 598.0
    const val ASK_SIZE = 19.0
    const val ASK_LINE = 25.0
    const val ASK_TRACK = -0.01
    const val CONTROL_D = 60.0
    const val CONTROL_RING = 0.22
    const val CONTROL_GAP = 14.0
    const val CONTROL_HINT = 15.0
    const val CONTROL_BOTTOM = 14.0

    const val GREETING = "Hey Mara,"
    val IDLE = "How can I help you?".replace(Regex(" (\\S+)$"), " $1") // the last two words stay together: no widow at large text sizes
    const val LISTENING = "I’m listening"
    val REPLY = listOf("Booked", "for", "8", "pm.")
    val ASK = listOf("Book", "a", "table", "for", "two", "at", "eight")
    const val HOLD = "Hold to talk"
    const val RELEASE = "Release to send"
    const val LABEL = "Voice assistant"
    const val HINT = "Press and hold to talk. Drag along the ribbon to part it."
    val STATES = listOf("Ready", "Listening", "Thinking", "Answering")
    const val START = "Start listening"
    const val STOP = "Stop listening"

    fun rgbOf(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    /** The ribbon and its two lines are one group, anchored by its centre (y 414 of 874); insets keep it clear of the island. */
    class Layout(val w: Double, val h: Double, insetTop: Double, centerY: Double = Y0) {
        val s = w / REF_W
        private val cy = 414.0
        private val shiftC = (centerY - Y0) * s
        private var shift = 0.0
        private fun base(y: Double) = h * (cy / REF_H) + (y - cy) * s + shiftC

        init {
            // a short phone: never let the greeting climb under the status bar
            val minTop = insetTop + 16
            val textTop = base(TEXT_BOTTOM) - 2 * BODY_LINE * s
            if (textTop < minTop) shift = minTop - textTop
        }

        fun yc(y: Double) = base(y) + shift
        val stripTop = yc(STRIP_TOP)
        val stripH = (STRIP_BOTTOM - STRIP_TOP) * s
        val textBottom = yc(TEXT_BOTTOM)
    }

    fun max2(a: Double, b: Double) = max(a, b)
}

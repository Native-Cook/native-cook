package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.graphics.Color

/** Exact geometry, colour and timing values from the approved React Native block. */
internal object DragMultiSelectTokens {
    object Colors {
        const val page = 0xFF0B0B0C.toInt()
        const val card = 0xFF161618.toInt()
        const val line = 0x14FFFFFF
        const val ink = 0xFFF4F2EE.toInt()
        const val mute = 0xFF8F8C87.toInt()
        const val dim = 0xFF5E5C58.toInt()
        const val dot = 0x33FFFFFF
        const val orange = 0xFFFF8A1F.toInt()
        const val violet = 0xFFA57BFF.toInt()
        const val violetInk = 0xFF170B30.toInt()
        const val violetTrack = 0xFF2D2340.toInt()
        const val green = 0xFF3DDC84.toInt()
        const val greenSoft = 0x243DDC84
        const val buttonOff = 0xFF1E1E21.toInt()
        const val glyph = 0x5CFFFFFF
        const val ring = 0x40A57BFF
        const val scrim = 0x99000000.toInt()
        const val pass = 0xFF050506.toInt()
        const val passEdge = 0x12FFFFFF
        const val passMute = 0x8FFFFFFF.toInt()
        const val passText = 0xC7FFFFFF.toInt()
        const val chip = 0xF0FFFFFF.toInt()
        const val chipInk = 0xFF111113.toInt()
        const val finger = 0x29FFFFFF
        const val fingerEdge = 0x52FFFFFF
    }

    object Layout {
        const val edge = 16f
        const val nav = 44f
        const val cardGap = 4f
        const val cardHeight = 148f
        const val cardRadius = 24f
        const val cardPad = 20f
        const val sectionGap = 20f
        const val sectionHeight = 16f
        const val sectionAfter = 8f
        const val row = 64f
        const val numberX = 16f
        const val numberW = 52f
        const val railX = 76f
        const val rail = 4f
        const val textX = 96f
        const val trailRight = 20f
        const val glyph = 22f
        const val cells = 72f
        const val cellsGap = 12f
        const val button = 52f
        const val buttonRadius = 16f
        const val panelHeight = 136f
        const val panelSide = 16f
        const val edgeZone = 48f
        const val finger = 44f
    }

    object Route {
        const val x0 = 120f
        const val x1 = 250f
        const val y = 56f
        const val lift = 18f
    }

    object Type {
        const val nav = 17f
        const val cardMeta = 12f
        const val code = 44f
        const val city = 12f
        const val time = 17f
        const val section = 12f
        const val sectionMeta = 13f
        const val rowTime = 13f
        const val rowAp = 11f
        const val title = 19f
        const val meta = 12f
        const val land = 12f
        const val figure = 26f
        const val figureLabel = 12f
        const val button = 16f
        const val passKey = 10f
        const val passValue = 13f
        const val passCode = 84f
        const val passCity = 11f
        const val passShow = 12f
        const val passNote = 12f
        const val chip = 12f
    }

    data class Spring(val durationMs: Float, val dampingRatio: Float)

    object Springs {
        val join = Spring(460f, .72f)
        val leave = Spring(260f, 1f)
        val lift = Spring(520f, .85f)
        val mode = Spring(360f, 1f)
        val reduced = Spring(200f, 1f)
    }

    object Select {
        const val longPressMs = 300L
        const val dragSlop = 8f
        const val autoScrollMax = 720f
        const val coastDecay = 3.5f
        const val coastStop = 20f
        const val restLift = .35f
    }

    object Download {
        const val rowSeconds = .9f
        const val staggerSeconds = .11f
        const val settleSeconds = .4f
        const val markSeconds = .24f
    }

    object Demo {
        const val holdRow = 0
        const val holdDown = .6f
        const val dragStart = 1.05f
        const val dragEnd = 2.3f
        const val dragTo = 4
        const val release = 2.52f
        const val tapRow = 5
        const val tapAt = 3.6f
        const val downloadAt = 4.8f
        const val resetAt = 9.1f
        const val tidySeconds = .6f
    }

    const val sheetFadeSeconds = .24f
    const val fingerFadeSeconds = .15f
    const val pressSeconds = .12f
    const val passRiseSeconds = .52f
    const val passOutSeconds = .22f
    const val passFrom = 120f
    const val passScale = .96f
    const val resumeMs = 2_000L
    const val loopMs = 10_000L

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}

const val LOOP: Long = DragMultiSelectTokens.loopMs

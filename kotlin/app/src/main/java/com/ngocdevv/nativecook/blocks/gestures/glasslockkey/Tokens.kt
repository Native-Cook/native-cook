package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import android.graphics.Color
import kotlin.math.PI

/** Values copied from the approved React Native brass-vault design. */
internal object GlassLockKeyTokens {
    const val STAGE_WIDTH = 402.0
    const val LOOP = 10_000
    const val STEP = 1.0 / 120.0
    const val MAX_STEPS = 30
    const val RESUME_MS = 2_000.0

    object Colors {
        val bgTop = Color.rgb(0x10, 0x16, 0x14)
        val bgBottom = Color.rgb(0x0A, 0x0D, 0x0C)
        val pool = Color.argb(26, 206, 226, 214)
        val shadow = Color.argb(179, 0, 0, 0)
        val brassHigh = Color.rgb(0xF3, 0xD5, 0x8C)
        val brass = Color.rgb(0xC9, 0x97, 0x3E)
        val brassDeep = Color.rgb(0x8A, 0x5E, 0x1F)
        val brassSide = Color.rgb(0x9A, 0x6A, 0x26)
        val brassSideDeep = Color.rgb(0x5E, 0x3E, 0x12)
        val brassEdge = Color.argb(191, 255, 236, 190)
        val engrave = Color.argb(140, 70, 44, 10)
        val engraveLight = Color.argb(115, 255, 238, 196)
        val steelHigh = Color.rgb(0xF4, 0xF6, 0xF8)
        val steel = Color.rgb(0xA9, 0xB0, 0xB8)
        val steelDeep = Color.rgb(0x59, 0x60, 0x69)
        val steelEdge = Color.argb(217, 255, 255, 255)
        val plug = Color.rgb(0xB9, 0x8A, 0x36)
        val slot = Color.rgb(0x1A, 0x12, 0x08)
        val glow = Color.argb(140, 255, 214, 140)
        val glint = Color.argb(204, 255, 250, 230)
        val ink = Color.rgb(0xEC, 0xF1, 0xEE)
        val mute = Color.argb(143, 236, 241, 238)
        val faint = Color.argb(87, 236, 241, 238)
        val error = Color.rgb(0xF0, 0x8A, 0x7A)
        val sheet = Color.rgb(0x14, 0x1A, 0x18)
        val sheetEdge = Color.argb(20, 255, 255, 255)
        val sheetLine = Color.argb(18, 255, 255, 255)
        val rowIcon = Color.argb(31, 233, 205, 140)
        val rowIconInk = Color.rgb(0xE9, 0xCD, 0x8C)
        val button = ink
        val buttonInk = bgTop
    }

    object Lock {
        const val topRatio = 0.36
        const val face = 150.0
        const val height = 180.0
        const val side = 22.0
        const val radius = 18.0
        const val shackle = 30.0
        const val opening = 68.0
        const val shackleRise = 100.0
        const val shortLeg = 8.0
        const val longLeg = 60.0
        const val openLift = 58.0
        const val openTilt = 0.12
        const val plugWidth = 28.0
        const val plugHeight = 9.0
        const val slot = 3.0
        const val glowRadius = 34.0
    }

    object Key {
        const val restRatio = 0.70
        const val ring = 64.0
        const val rim = 11.0
        const val shaft = 11.0
        const val blade = 100.0
        const val bitWidth = 18.0
        const val bitHeight = 30.0
        const val bitFromTip = 8.0
        const val collar = 18.0
        const val hit = 88.0
    }

    object Type {
        const val side = 32.0
        const val top = 24.0
        const val eyebrowSize = 11.0
        const val eyebrowTracking = 1.8
        const val titleSize = 44.0
        const val titleLine = 50.0
        const val hintSize = 15.0
        const val hintLine = 20.0
        const val gap = 8.0
        const val bottom = 32.0
    }

    object Sheet {
        const val heightRatio = 0.5
        const val radius = 28.0
        const val pad = 24.0
        const val row = 64.0
        const val icon = 40.0
        const val iconRadius = 12.0
        const val button = 52.0
        const val rise = 0.075
        const val shrink = 0.62
    }

    object Copy {
        const val eyebrow = "PERSONAL VAULT · 3 ITEMS"
        const val locked = "Locked"
        const val unlocked = "Unlocked"
        const val hint = "Slide the key in, then turn it right"
        const val wrongWay = "The other way"
        const val meta = "3 items · end-to-end encrypted"
        const val head = "Opened just now"
        const val lock = "Lock vault"
        const val unlock = "Unlock vault"
        const val keyHint = "Swipe up to insert and turn the key"

        data class Row(val title: String, val subtitle: String, val icon: Icon)

        enum class Icon { codes, document, wifi }

        val rows = listOf(
            Row("Recovery codes", "12 codes · added 3 days ago", Icon.codes),
            Row("Passport scan", "PDF · 2.4 MB · expires 2031", Icon.document),
            Row("Home Wi-Fi", "Password · shared with 2", Icon.wifi),
        )
    }

    data class Spring(val stiffness: Double, val damping: Double)

    object Carry {
        const val lift = 1.04
        const val magnet = 72.0
        const val magnetPull = 0.55
        const val seat = 12.0
        val home = Spring(220.0, 22.0)
        const val bob = 3.0
        const val bobPeriod = 2.5
        const val pullOut = 34.0
    }

    object Insert {
        const val duration = 0.22
        const val outDuration = 0.26
    }

    object Turn {
        const val radius = 60.0
        const val open = PI / 2.0
        val detents = doubleArrayOf(PI / 6.0, PI / 3.0, PI / 2.0)
        const val wrong = 0.21
        const val visual = 0.83
        val back = Spring(260.0, 24.0)
        val close = Spring(180.0, 22.0)
    }

    object Shackle {
        const val stiffness = 320.0
        const val damping = 14.0
        const val closeStiffness = 260.0
        const val closeDamping = 26.0
    }

    object Bounce {
        const val stiffness = 420.0
        const val damping = 16.0
        const val detentKick = -40.0
        const val openKick = -120.0
    }

    object Shake {
        const val duration = 0.36
        const val amplitude = 6.0
        const val cycles = 3.0
    }

    object SheetMotion {
        const val delay = 0.3
        const val open = 0.5
        const val close = 0.38
        const val stagger = 0.04
        const val reducedFade = 0.2
        const val shackleAfter = 0.3
    }

    object Script {
        val carry = doubleArrayOf(0.8, 1.6)
        val carryTo = doubleArrayOf(0.0, -100.0)
        val turn = doubleArrayOf(1.95, 3.0)
        const val turnBy = 100.0
        const val lockAt = 6.6
        const val resumeOpen = 6.2
    }

    object Haptic {
        const val grab = 1
        const val seat = 2
        const val detent = 3
        const val open = 4
        const val wrong = 5
    }

    const val glintDuration = 0.7

    object Assets {
        const val instrumentSerif = "glasslockkey/glasslockkey_InstrumentSerif-Regular.ttf"
        const val interRegular = "glasslockkey/glasslockkey_Inter-Regular.ttf"
        const val interMedium = "glasslockkey/glasslockkey_Inter-Medium.ttf"
        const val interSemibold = "glasslockkey/glasslockkey_Inter-SemiBold.ttf"
    }
}

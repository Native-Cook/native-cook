package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import androidx.compose.ui.graphics.Color

internal fun slideToAnswerCordRgba(
    red: Int,
    green: Int,
    blue: Int,
    alpha: Double,
): Color = Color(
    red = red / 255f,
    green = green / 255f,
    blue = blue / 255f,
    alpha = alpha.toFloat(),
)

/** Exact values mirrored from the approved React Native theme.ts and motion.ts. */
internal object Tokens {
    const val STAGE_WIDTH = 402.0

    object COLORS {
        val sky = listOf(
            Color(0xFF050409),
            Color(0xFF050409),
            Color(0xFF111226),
            Color(0xFF1C1C3E),
            Color(0xFF2A2B6C),
            Color(0xFF262767),
        )
        val skyStops = listOf(0.0, 0.39, 0.47, 0.55, 0.78, 1.0)
        val skyTop = Color(0xFF050409)
        val skyMid = Color(0xFF050409)
        val horizon = Color(0xFF1C1D42)
        val ground = Color(0xFF2A2B6C)
        val groundConnected = Color(0xFF0E3D3A)
        val groundEnded = Color(0xFF3A1328)
        const val grain = 0.07
        val ring = Color(0xFFFF1F8E)
        val ringDeep = Color(0xFFC40F6A)
        val connected = Color(0xFF2BE36A)
        val connectedDeep = Color(0xFF12A548)
        val off = Color(0xFF5B2348)
        val offDeep = Color(0xFF3A1630)
        val shell = slideToAnswerCordRgba(92, 52, 178, 0.78)
        val shellEdge = slideToAnswerCordRgba(164, 128, 255, 0.55)
        val grille = slideToAnswerCordRgba(60, 0, 40, 0.55)
        val bloom = Color.White
        val cordBack = Color(0xFF2C2178)
        val cordFront = Color(0xFF7448E6)
        val cordEdge = Color(0xFFFF4FAE)
        val replicaCord = Color(0xFF302A88)
        val replicaRim = Color(0xFFA5237D)
        val replicaRimLeft = Color(0xFF4C40B4)
        val ink = Color(0xFFF4F2FF)
        val mute = slideToAnswerCordRgba(244, 242, 255, 0.58)
        val decline = Color(0xFFFF1450)
        val accept = Color(0xFF22E04A)
        val glyph = Color.White
        val label = slideToAnswerCordRgba(244, 242, 255, 0.62)
    }

    object HANDSET {
        const val imageX = -69.6
        const val imageY = -6.96
        const val imageW = 167.04
        const val imageH = 208.8
        const val earX = 12.0
        const val earY = 23.0
        const val mouthX = 12.0
        const val mouthY = 172.0
        const val comX = 2.0
        const val comY = 98.0
        const val bloom = 20.0
        const val bloomX = 14.0
        const val bloomY = -14.0
        const val hit = 190.0
        const val restTilt = -0.33
        const val offOpacity = 0.42
    }

    object CORD {
        const val anchorY = -12.0
        const val replicaAnchorDx = -26.0
        const val replicaTurns = 9.7
        const val replicaWave = 7.0
        const val replicaPhase = -3.98
        val replicaRoll = listOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(1.0, -0.39),
            doubleArrayOf(1.875, -0.22),
            doubleArrayOf(2.9, -0.39),
            doubleArrayOf(4.0, 0.0),
        )
        const val replicaWidth = 7.0
        const val replicaRimDx = 2.7
        const val restRatio = 0.335
        const val links = 14
        const val turns = 20
        const val wire = 90.0
        const val width = 2.6
        const val edge = 1.1
        const val maxRadius = 9.0
        const val samples = 8
    }

    object SPACE {
        const val xs = 4.0
        const val sm = 8.0
        const val md = 12.0
        const val lg = 16.0
        const val xl = 24.0
        const val xxl = 32.0
        const val xxxl = 40.0
    }

    object LAYOUT {
        const val side = 32.0
        const val nameTopRatio = 0.655
        const val copyTopRatio = 0.645
        const val nameSize = 34.0
        const val nameLine = 40.0
        const val statusSize = 17.0
        const val statusLine = 22.0
        const val gap = 8.0
        const val buttonsBottom = 40.0
        const val button = 64.0
        const val buttonGap = 96.0
        const val labelSize = 13.0
        const val labelGap = 8.0
        const val glyph = 26.0
    }

    data class Control(
        val name: String,
        val label: String,
        val symbol: String,
    )

    object COPY {
        const val meta = "mobile · Stockholm"
        val controls = listOf(
            Control("mute", "Mute", "mic.slash.fill"),
            Control("keypad", "Keypad", "circle.grid.3x3.fill"),
            Control("speaker", "Speaker", "speaker.wave.2.fill"),
        )
        const val name = "Mara Lindqvist"
        const val ringing = "Incoming call · lift to answer"
        const val declined = "Call declined"
        const val ended = "Call ended"
        const val decline = "Decline"
        const val accept = "Accept"
        const val end = "End"
        const val a11yHandset = "Handset"
        const val a11yHint = "Swipe up to answer, swipe down to decline"
    }

    const val REPLICA = false

    object GLASS {
        const val blur = 18.0
        val decline = slideToAnswerCordRgba(255, 20, 80, 0.55)
        val accept = slideToAnswerCordRgba(34, 224, 74, 0.5)
        val neutral = slideToAnswerCordRgba(255, 255, 255, 0.12)
        val edgeTop = slideToAnswerCordRgba(255, 255, 255, 0.75)
        val edgeBottom = slideToAnswerCordRgba(255, 255, 255, 0.08)
        const val pillY = -96.0
        const val pillH = 64.0
        const val field = 300.0
        const val fieldAlpha = 0.35
    }

    const val STEP = 1.0 / 120.0
    const val MAX_STEPS = 30

    object CHAIN {
        const val gravity = 900.0
        const val hangStretch = 150.0
        const val linkMass = 0.05
        const val handsetMass = 1.0
        const val damping = 0.35
        const val handsetDamping = 0.6
        const val idleDrive = 75.0
        const val drivePeriod = 4.0
        const val straighten = 160.0
        const val lookEase = 0.2
        const val toSource = 0.3
    }

    object TILT {
        const val stiffness = 42.0
        const val damping = 5.5
    }

    object DRAG {
        const val answer = 64.0
        const val decline = 72.0
        const val reach = 110.0
        const val releaseSpeed = 1600.0
    }

    object STATE {
        const val pickUp = 72.0
        const val drop = 24.0
        const val lengthEase = 7.0
        const val colorEase = 14.0
        const val resetAfter = 2.0
    }

    object RING {
        const val every = 2.0
        const val offset = 1.2
        val buzz = doubleArrayOf(0.0, 0.2)
        const val buzzLength = 0.09
        const val buzzAngle = 0.05
        const val buzzHz = 38.0
        const val pulse = 1.3
        const val pulseFrom = 44.0
        const val pulseTo = 170.0
    }

    object HAPTIC {
        const val grab = 1
        const val answer = 2
        const val decline = 3
        const val tick = 4
    }

    const val LOOP_MS = 12_000
    const val LOOP = LOOP_MS / 1000.0
    const val RESUME_MS = 3_500
    const val TOUCH_RESUME_MS = 2_000
    const val PREROLL_LOOPS = 3
    val SCRIPT = listOf(
        doubleArrayOf(2.6, 3.0, 0.0, 0.0, 0.0, -10.0, 1.0, 0.0),
        doubleArrayOf(3.0, 3.55, 0.0, -10.0, 6.0, -84.0, 0.0, 0.0),
        doubleArrayOf(3.55, 3.7, 6.0, -84.0, 6.0, -84.0, 0.0, 1.0),
        doubleArrayOf(7.4, 7.75, 0.0, 0.0, 0.0, 14.0, 1.0, 0.0),
        doubleArrayOf(7.75, 8.25, 0.0, 14.0, -4.0, 92.0, 0.0, 0.0),
        doubleArrayOf(8.25, 8.4, -4.0, 92.0, -4.0, 92.0, 0.0, 1.0),
    )

    object REPLICA_SWING {
        const val period = 4.0
        val x = doubleArrayOf(578.19, -256.06, 44.02, -0.52, 0.09, 5.88, -3.23, 0.0, -0.12)
        val y = doubleArrayOf(1262.69, 8.17, -1.38, -19.1, 6.54, -0.36, -0.25, 1.13, -0.5)
        val lean = doubleArrayOf(9.82, -11.66, 1.96, 0.1, -0.05, 0.22, -0.16, 0.03, -0.01)
        val centroid = doubleArrayOf(16.5, 251.3)
        const val leanPerRad = -0.9426
        const val leanAt0 = -2.078
        val attach = doubleArrayOf(3.0, -22.0)
    }

    val REPLICA_CORD_Y = doubleArrayOf(300.0, 360.0, 420.0, 480.0, 540.0, 600.0, 660.0, 720.0, 780.0)
    val REPLICA_CORD_X = arrayOf(
        doubleArrayOf(449.9, 442.3, 430.5, 415.6, 399.0, 382.0, 365.8, 351.7, 341.0),
        doubleArrayOf(464.8, 453.7, 441.3, 427.9, 414.0, 400.1, 386.6, 373.9, 362.4),
        doubleArrayOf(488.3, 473.8, 461.4, 450.6, 440.9, 431.7, 422.6, 412.8, 402.0),
        doubleArrayOf(514.2, 504.5, 496.5, 489.9, 484.3, 479.5, 475.0, 470.6, 465.7),
        doubleArrayOf(537.1, 532.4, 529.3, 527.5, 526.6, 526.4, 526.7, 527.1, 527.4),
        doubleArrayOf(554.3, 561.4, 565.8, 568.5, 570.6, 573.0, 576.8, 582.9, 592.4),
        doubleArrayOf(561.5, 579.9, 590.5, 596.0, 598.8, 601.6, 607.1, 617.8, 636.4),
        doubleArrayOf(566.2, 590.6, 604.7, 611.8, 615.5, 619.0, 625.9, 639.5, 663.3),
        doubleArrayOf(566.1, 591.2, 605.6, 612.8, 616.4, 619.9, 626.8, 640.6, 664.7),
        doubleArrayOf(562.4, 581.9, 593.2, 598.9, 601.8, 604.7, 610.3, 621.5, 641.0),
        doubleArrayOf(555.4, 564.5, 570.0, 573.1, 575.3, 577.7, 581.8, 588.6, 599.6),
        doubleArrayOf(540.1, 536.2, 533.8, 532.7, 532.5, 533.1, 534.0, 535.1, 536.0),
        doubleArrayOf(517.0, 508.2, 501.0, 495.1, 490.3, 486.1, 482.3, 478.5, 474.5),
        doubleArrayOf(491.5, 477.2, 465.2, 454.9, 445.7, 437.1, 428.5, 419.4, 409.1),
        doubleArrayOf(467.4, 456.2, 443.8, 430.6, 417.1, 403.5, 390.4, 378.1, 367.2),
        doubleArrayOf(450.9, 443.0, 431.1, 416.4, 400.0, 383.2, 367.2, 353.2, 342.3),
    )

    const val REPLICA_HOLD = -1.0
    const val QA_SPEED = 1.0

    object FLICK {
        const val speed = 900.0
        const val min = 24.0
    }

    const val TICK = 22.0

    object WASH {
        const val duration = 0.7
    }

    object MORPH {
        const val duration = 0.5
    }

    object PRESS {
        const val scale = 0.95
        const val inMs = 120
        const val outMs = 160
    }

    object CHEVRON {
        const val period = 1.5
        const val rise = 18.0
    }

    const val RESUME_EASE = 0.5
}

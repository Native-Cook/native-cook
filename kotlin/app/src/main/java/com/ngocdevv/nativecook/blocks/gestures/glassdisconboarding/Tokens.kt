package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

/** React Native theme.ts and motion.ts values, kept as the port's single source of truth. */
internal object GlassDiscOnboardingTokens {
    object COLOR {
        const val bgTL = "#EAC5BE"
        const val bgTR = "#E0B8B3"
        const val bgBL = "#E9CDBE"
        const val bgBR = "#F5D2C2"
        const val ink = "#2A1A1C"
        const val inkSoft = "#2E1E20"
        const val hint = "#BE9C8F"
        const val lensInk = "#2A2226"
        const val shadow = "#5A3A36"
        const val buttonLight = "#F9E1D5"
        const val buttonLightEdge = "rgba(90,58,54,0.10)"
        const val buttonLightShadow = "#8A5A52"
        const val buttonLightInk = "#3A3236"
        const val buttonDark = "#1E0A0E"
        const val buttonDarkInk = "#FFFFFF"
        const val finger = "rgba(255,255,255,0.45)"
        const val fingerRing = "rgba(35,29,32,0.30)"
        const val ringInner = "#7FE0FF"
        const val ringCore = "#1E3BFF"
        const val ringOuter = "#B05CFF"
        const val ringBlush = "#FF8FCB"
        const val pulse = "#FFFFFF"
        const val fieldA = "rgba(70,90,255,0.16)"
        const val fieldB = "rgba(176,92,255,0.10)"
    }

    const val FONT = "System"

    object TYPE {
        const val introFontSize = 31f
        const val introLineHeight = 37f
        const val introFontWeight = 400
        const val introLetterSpacing = -0.3f
        const val introTextAlign = "center"
        const val introColor = COLOR.ink
        const val hintFontSize = 14.5f
        const val hintFontWeight = 500
        const val hintLetterSpacing = -0.2f
        const val hintTextAlign = "center"
        const val hintColor = COLOR.hint
        const val headline = 37f
        const val headlineLine = 43.5f
        const val headlineTracking = -0.5f
        const val buttonFontSize = 15f
        const val buttonLetterSpacing = -0.2f
        const val lens = 23.5f
    }

    object SPACE {
        const val xs = 4f
        const val sm = 8f
        const val md = 12f
        const val lg = 16f
        const val xl = 24f
        const val xxl = 32f
    }

    object LAYOUT {
        const val domeRadius = 0.6f
        const val domeTop = 0.72f
        val pullT = floatArrayOf(0f, 0.4f, 0.6f, 0.75f, 1f)
        val pullR = floatArrayOf(0.6f, 0.53f, 0.43f, 0.358f, 0.29f)
        val pullY = floatArrayOf(0.996f, 0.884f, 0.79f, 0.721f, 0.655f)
        val pullX = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)

        object lens {
            const val fold = 0.73f
            const val foldBack = 6.04f
            const val foldShape = 2f
            const val kY = 0.556f
            const val kX = 0.75f
            const val squeezeX = 1.091f
            const val tx = 0.017f
            const val ty = -0.113f
            const val spread = 0.5f
            const val fringeFrom = 0.35f
            const val fade = 3.19f
            const val mirrorFloor = 0.3f
        }

        const val rimLight = 0.04f
        const val rimWidth = 7f
        const val shadowAt = 4f
        const val shadowWidth = 6f
        const val shadowAlpha = 0.07f

        object bead {
            const val from = 90f
            const val span = 45f
            const val frost = 0.18f
        }

        const val domeFrost = 0.12f
        const val ringFrom = 0.5f
        const val ringTo = 0.9f
        const val hintY = 0.889f
        const val introBaseline = 0.4842f
        const val headlineBaseline = 0.5099f
        const val beadY = 0.397f
        const val beadRadius = 46f
        const val beadRestY = 0.3715f
        const val beadRestRadius = 39.5f
        const val backRadius = 0.37f
        const val buttonsBottom = 32f
        const val buttonHeight = 52f
        const val buttonHeightDark = 54f
        const val buttonGap = 14f
        const val gutter = 34f
        const val buttonIcon = 19f
        const val buttonIconInset = 22f
        const val fingerRadius = 22f
    }

    val CLOUD = arrayOf(
        floatArrayOf(375f, 38f, 25f, 9f),
        floatArrayOf(120f, 25f, 18f, 3f),
        floatArrayOf(84f, 42f, 35f, 12f),
        floatArrayOf(250f, 52f, 27f, 4f),
        floatArrayOf(203f, 88f, 35f, 14f),
        floatArrayOf(145f, 95f, 22f, 13f),
        floatArrayOf(94f, 84f, 33f, 2f),
        floatArrayOf(350f, 100f, 25f, 15f),
        floatArrayOf(292f, 148f, 20f, 7f),
        floatArrayOf(322f, 132f, 32f, 10f),
        floatArrayOf(67f, 127f, 13f, 20f),
        floatArrayOf(140f, 143f, 28f, 0f),
        floatArrayOf(261f, 158f, 25f, 23f),
        floatArrayOf(200f, 165f, 26f, 5f),
        floatArrayOf(207f, 207f, 19f, 18f),
        floatArrayOf(147f, 229f, 17f, 21f),
        floatArrayOf(172f, 240f, 29f, 8f),
        floatArrayOf(195f, 272f, 22f, 16f),
        floatArrayOf(239f, 264f, 11f, 1f),
        floatArrayOf(247f, 226f, 8f, 17f),
        floatArrayOf(279f, 224f, 8f, 11f),
        floatArrayOf(243f, 240f, 4f, 19f),
        floatArrayOf(225f, 241f, 4f, 22f),
        floatArrayOf(270f, 263f, 2.5f, 6f),
        floatArrayOf(60f, 195f, 1.8f, 15f),
        floatArrayOf(80f, 220f, 1.8f, 7f),
    )

    object CLOUD_FRAME {
        const val width = 402f
        const val height = 874f
    }

    object ATLAS {
        const val cell = 192f
        const val columns = 6
        const val rows = 4
    }

    object COPY {
        val intro = listOf("A new era of", "software is here.")
        const val hint = "Swipe up to enter"
        const val lens = "Meet Wavo."
        const val headline = "Meet Wavo."
        val sub = listOf("The first personal", "software platform.")
        const val email = "Continue with email"
        const val phone = "Continue with phone"
        const val back = "Start over"
        const val enter = "Enter"
    }

    object VIEW {
        const val ascent = 0.94f
        const val textureMaxScale = 2f
        const val buttonLensWidth = 120f
        const val buttonLensLightAlpha = 0.85f
        const val buttonLensDarkAlpha = 0.16f
        const val buttonLightWeight = 400
        const val buttonDarkWeight = 600
        const val textureButtonWeight = 500
        const val lensWeight = 500
        const val buttonShadowOpacity = 0.16f
        const val buttonShadowRadius = 6f
        const val buttonShadowOffsetY = 2f
        const val buttonTopRimAlpha = 0.9f
        const val buttonTopRimHairlines = 2f
        const val buttonLabelBaselineFactor = 0.35f
        const val fieldRadius = 0.8f
        const val fieldMiddle = 0.45f
        const val tiltMultiplier = 1.5f
        const val fingerTiltThreshold = 0.5f
        const val beadTouchPadding = 22f
        const val domeTouchPadding = 24f
        const val pressureStrokeStart = 2.2f
        const val pressureStrokeEnd = 0.4f
        const val pressureAlpha = 0.7f
        const val fingerFillAlpha = 0.45f
        const val fingerRingAlpha = 0.35f
        const val fingerStrokeWidth = 1.5f
        const val minimumBubbleRadius = 0.5f
        const val minimumWobble = 0.005f
        const val autoplaySweepStartX = 0.1f
        const val autoplaySweepWidth = 0.8f
        const val autoplaySweepY = 0.2f
        const val autoplaySweepArc = 0.06f
        const val autoplayBackOffsetX = 6f
    }

    object PORT {
        const val maximumFrameSeconds = 0.1f
        const val resumeAfterTouchMillis = 2000f
    }

    object PULL {
        const val threshold = 260f
        const val rubber = 120f
        const val commitAt = 0.85f
        const val flickVelocity = -900f
        const val flickMin = 0.4f
        const val ringPeakAt = 0.42f
        const val ringOutAt = 0.62f
        const val revealFrom = 0.52f
        const val revealTo = 0.72f
        const val introOutBy = 0.62f
        const val hintOutBy = 0.2f
        const val tickEvery = 0.125f
    }

    object BACK {
        const val growBy = 300f
        const val commitBy = 200f
        const val flickVelocity = 900f
        const val flickMin = 90f
    }

    object RISE {
        const val ms = 1600
    }

    object SPRING {
        const val backDuration = 400
        const val backDamping = 0.8f
        const val beadDuration = 400
        const val beadDamping = 0.8f
        const val domeDuration = 400
        const val domeDamping = 0.8f
    }

    val EASE_OUT = floatArrayOf(0.23f, 1f, 0.32f, 1f)

    object FADE {
        const val settle = 260
        const val headlineMs = 260
        const val restDelay = 250
        const val restMs = 750
        const val outMs = 200
        const val introBack = 280
        const val reducedMs = 200
    }

    object BURST {
        const val delayMs = 650
        const val staggerMs = 26
        const val startScale = 0.12f
        const val growOver = 150f
        const val growMs = 520
        const val launchSpeed = 320f
    }

    object STICKERS {
        const val step = 1f / 120f
        const val stiffness = 14f
        const val damping = 5.2f
        const val separation = 0.5f
        const val overlap = 0.62f
        const val push = 56f
        const val float = 2.2f
        const val floatPeriodMs = 4200
        const val exitLift = 1.1f
        const val exitStiffness = 5f
        const val exitDamping = 3.5f
        const val exitStaggerMs = 30
        const val maxFrameMs = 250
    }

    const val LOOP_MS = 12500
    const val DEBUG_FREEZE_MS = -1

    object AUTOPLAY {
        const val resumeDelayMs = 3500
        const val startX = 0.66f
        const val startY = 0.84f
        const val pullStart = 600
        const val pullEnd = 3400
        const val pullBy = 236f
        const val sweepStart = 6700
        const val sweepEnd = 7600
        const val backStart = 8200
        const val backEnd = 9500
        const val backBy = 250f
    }

    object UPGRADE {
        const val nudge = SPACE.xs
        const val nudgeDuration = 300
        const val nudgeDamping = 0.8f
        const val stretchPerSpeed = 1f / 9000f
        const val stretchMax = 0.14f
        const val stretchFollow = 0.25f
        const val dispersionPerSpeed = 1f / 1400f
        const val dispersionMax = 1.6f
        const val glintEveryMs = 6000
        const val glintMs = 1300
        const val glintFrom = -2.7f
        const val glintTo = -0.45f
        const val glow = 0.35f
        val pulses = intArrayOf(0, 230, 460)
        const val pulseMs = 760
        const val pulseGrow = 96f
        const val pulseSquash = 0.06f
        const val wobblePerSpeed = 1f / 2600f
        const val wobbleMax = 0.16f
        const val fingerCarry = 0.35f
        const val rise = SPACE.sm
        const val lineStagger = 60
        const val fieldDrift = SPACE.sm
        const val fieldPeriodMs = 8000
        const val hintBase = 0.82f
        const val pressIn = 100
        const val pressOut = 220
        const val pressScale = 0.97f
    }
}

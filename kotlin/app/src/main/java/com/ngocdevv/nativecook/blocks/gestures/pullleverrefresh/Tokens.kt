package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

/** Mechanical port of every design and motion token in the approved React Native block. */
internal object PullLeverRefreshTokens {
    internal object Color {
        const val background = "#1C1C1C"
        const val track = "#2C2C2C"
        const val trackDot = "#232323"
        const val fill = "#FFFFFF"
        const val fillDot = "#1C1C1C"
        const val pivot = "#1C1C1C"
        const val ink = "#FFFFFF"
        const val mute = "#8C8C8C"
        const val finger = "rgba(82,82,82,0.92)"
        const val dimFill = "#2C2C2C"
        const val arcOff = "#333333"
        const val shadow = "#0B0B0B"
        const val shaftLightStart = "rgba(255,255,255,0)"
        const val shaftLightEnd = "rgba(255,255,255,0.9)"
        const val specular = "rgba(255,255,255,0.22)"
        const val knobLit = "#BDBDBD"
    }

    internal object Artboard {
        const val height = 301f
        const val leverLeft = 115f
        const val leverCentreY = 150f
    }

    internal object Lever {
        const val length = 171f
        const val thickness = 17f
        const val pivotDot = 7f
        const val dot = 2.2f
        const val pitch = 16.75f
        const val dots = 9
        const val knob = 33f
    }

    internal object Space {
        const val xs = 4f
        const val sm = 8f
        const val md = 12f
        const val lg = 16f
        const val xl = 24f
        const val xxl = 32f
    }

    internal object Layout {
        const val captionGap = Space.sm
        const val arcRadius = Lever.thickness * 2f
    }

    internal object Type {
        const val captionFontSize = 12f
        const val captionLineHeight = 16f
        const val captionFontWeight = 500
        const val captionLetterSpacing = 0.1f
        const val captionTravel = 25f
    }

    internal object Gesture {
        const val activationDistance = 6f
        const val scriptedVelocityWindow = 0.08f
        const val scriptedVelocityBoost = 600f
    }

    internal object Rendering {
        const val quantizationEpsilon = 0.001f
        const val arcActiveScale = 0.5f
        const val haloScaleBase = 0.7f
        const val haloScaleGain = 0.3f
        const val haloBreathScale = 0.12f
        const val rippleOpacity = 0.7f
        const val rippleStroke = 1.5f
        const val highlightShadowOpacity = 0.6f
        const val highlightShadowBlur = 4f
        const val trailShadowOpacity = 0.8f
        const val trailShadowBlur = 6f
    }

    const val REPLICA_ONLY = false

    internal object Pull {
        const val restAngle = 45f
        const val maxAngle = 42f
        const val leverPull = 90f
        const val overAngle = 22f
        const val overSoftness = 70f
        const val overTick = 6f
        const val degPerPt = 0.335f
        const val throwBoost = 0.6f
        const val maxThrow = 900f
    }

    internal data class Spring(
        val damping: Float,
        val stiffness: Float,
        val mass: Float,
    )

    internal object SpringValues {
        val home = Spring(damping = 18f, stiffness = 300f, mass = 1f)
        val throwUp = Spring(damping = 16f, stiffness = 240f, mass = 1f)
        val fall = Spring(damping = 12f, stiffness = 260f, mass = 1f)
        val bloom = Spring(damping = 9f, stiffness = 160f, mass = 0.8f)
    }

    internal object Light {
        const val direction = -135f
        const val highlightOffset = 0.18f
        const val highlightSize = 0.55f
        const val highlightRamp = 8f
        const val restAngle = 45f
        const val knobSoft = 1.5f
        const val haloSize = 34f
        const val haloBlur = 18f
        const val haloMin = 0.25f
        const val haloMax = 0.6f
        const val haloBreath = 0.9f
        const val cometHead = 12f
        const val cometBlur = 14f
        const val cometTrail = 7
        const val cometTrailPt = 52f
        const val shaftLight = 0.55f
        const val ripple = 160f
        const val rippleMs = 620L
        const val arcDots = 7

        internal data class Shadow(
            val x: Float,
            val y: Float,
            val blur: Float,
            val opacity: Float,
        )

        val shadow = Shadow(
            x = 7f,
            y = 11f,
            blur = 10f,
            opacity = 0.55f,
        )

        internal data class Arc(
            val radius: Float,
            val dots: Int,
            val from: Float,
            val to: Float,
            val size: Float,
        )

        val arc = Arc(
            radius = Layout.arcRadius,
            dots = arcDots,
            from = 100f,
            to = 168f,
            size = 4f,
        )
    }

    internal object Timing {
        const val dotMs = 250L
        const val upAngle = -44f
        const val relaxAngle = -30f
        const val relaxMs = 300L
        const val dimMs = 800L
        const val lightMs = 160L
        const val fillReturnDivisor = 3f
        const val swayAmplitude = 2.5f
        const val swayHalfPeriod = 550L
        const val swayFade = 240L
        const val bloomScale = 1.7f
        const val bloomRise = 90L
        const val glowRise = 1_800L
        const val pulse = 640L
        const val easeInOutX1 = 0.77f
        const val easeInOutY1 = 0f
        const val easeInOutX2 = 0.175f
        const val easeInOutY2 = 1f
        const val easeOutX1 = 0.23f
        const val easeOutY1 = 1f
        const val easeOutX2 = 0.32f
        const val easeOutY2 = 1f
        const val knobFlash = 140L
        const val knobFade = 520L
        val shake = floatArrayOf(5f, -4f, 2.6f, -1.3f, 0f)
        const val shakeStep = 70L
        const val charIn = 220L
        const val charOut = 160L
        const val charStagger = 18L
        const val idleAmplitude = 1.2f
        const val idlePeriod = 2.75f
        const val caption = 180L
        const val doneLabel = 1_600L
        const val reduced = 180L
        const val idleResponse = 4f
        const val maxFrameDelta = 0.1f
    }

    const val FETCH_MS = 2_300L
    const val LOOP_MS = 11_000L
    const val RESUME_MS = 3_500L

    internal data class DemoPull(
        val down: Float,
        val moveEnd: Float,
        val up: Float,
        val depth: Float,
    )

    internal object Demo {
        val full = DemoPull(
            down = 0.6f,
            moveEnd = 1.75f,
            up = 2.1f,
            depth = 1.55f,
        )
        const val resolve = 4.4f
        val partial = DemoPull(
            down = 6.4f,
            moveEnd = 7f,
            up = 7.2f,
            depth = 0.5f,
        )
    }

    const val CAPTION_REFRESHING = "Checking\u2026"
    const val CAPTION_UPDATED = "Updated just now"
}

package com.ngocdevv.nativecook.blocks.gestures.holddragresize

/** Exact visual and motion tokens mirrored from the approved React Native block. */
object Tokens {
    object Color {
        const val background = "#000000"
        const val chrome = "#1C1C1E"
        const val chromeHi = "#2C2C2E"
        const val ink = "#FFFFFF"
        const val mute = "#8E8E93"
        const val divider = "#2A2A2C"
        const val toolDim = "#5C5C60"
        const val faint = "#5A5A5E"
        const val blue = "#1E8FFF"
        const val strip = "#151517"
        const val playhead = "#FFFFFF"
        const val scrim = "#B8000000"
        const val touch = "#59FFFFFF"
        const val outline = "#E6FFFFFF"
        const val caption = "#FFFFFF"
        const val captionShadow = "#99000000"
        const val finger = "#59FFFFFF"
        const val fingerEdge = "#B3FFFFFF"
        const val sticker = "#3FA34D"
        const val stickerRing = "#B8E986"
        const val stickerInk = "#FFFFFF"
        const val overlayButton = "#B81C1C1E"
        const val badge = "#8C141416"
        const val badgeDot = "#59FFFFFF"
        const val badgeBorder = "#2EFFFFFF"
        const val rim = "#FFFFFF"
    }

    const val REFERENCE_WIDTH = 402.0
    const val REFERENCE_HEIGHT = 874.0
    const val ASPECT = REFERENCE_WIDTH / REFERENCE_HEIGHT

    object Layout {
        const val gutter = 20.0
        const val barY = 14.0
        const val barButton = 32.0
        const val previewTop = 6.0
        const val previewRadius = 14.0
        const val controlsGap = 29.0
        const val control = 32.0
        const val dividerGap = 29.0
        const val rulerGap = 46.0
        const val rulerPitch = 85.0
        const val rulerLabelW = 30.0
        const val ruler = 12.0
        const val stripGap = 24.0
        const val strip = 60.0
        const val track = 34.0
        const val trackFirstGap = 4.0
        const val trackGap = 8.0
        const val trackIndent = 83.0
        const val trimHandleX = 73.0
        const val trimHandleWidth = 10.0
        const val trimHandleHeight = 30.0
        const val stickerX = 125.0
        const val stickerSize = 26.0
        const val hintGap = 51.0
        const val hint = 16.0
        const val thumb = 340.0 / 12.0
        const val hintBottom = 118.0
        const val tools = 78.0
        const val tool = 52.0
        const val toolH = 41.0
        const val toolGap = 6.8
        const val toolInset = 11.0
        const val toolRadius = 10.0
        const val finger = 44.0
        const val pairGap = 8.0
        const val trackContentGap = 6.0
        const val toolTop = 10.0
        const val toolContentGap = 4.0
        const val fullIcon = 40.0
        const val fullIconsBottom = 72.0
        const val pillsBottom = 16.0
        const val pillHeight = 44.0
        const val pillPadding = 18.0
        const val pillGap = 6.0
        const val fullIconRise = 8.0
    }

    object Space {
        const val xxs = 2.0
        const val xs = 4.0
        const val sm = 8.0
        const val md = 12.0
        const val lg = 16.0
        const val xl = 20.0
        const val xxl = 24.0
    }

    object Type {
        const val timeSize = 13.0
        const val timeLine = 16.0
        const val timeWeight = 500
        const val rulerSize = 9.0
        const val rulerLine = 12.0
        const val rulerWeight = 600
        const val trackSize = 13.0
        const val trackLine = 16.0
        const val trackWeight = 500
        const val hintSize = 12.0
        const val hintLine = 16.0
        const val hintWeight = 400
        const val toolSize = 10.5
        const val toolLine = 13.0
        const val toolWeight = 600
        const val captionSize = 20.0
        const val captionLine = 26.0
        const val captionWeight = 800
        const val coachSize = 22.0
        const val coachLine = 27.0
        const val coachWeight = 700
        const val coachButtonSize = 13.0
        const val coachButtonLine = 16.0
        const val coachButtonWeight = 600
        const val pillSize = 15.0
        const val pillLine = 20.0
        const val pillWeight = 600
        const val toolAaSize = 19.0
    }

    object Squeeze {
        const val controls = -2.0
        const val ruler = -3.0
        const val below = -14.0
        const val hint = -22.0
        const val tools = -23.0
    }

    object Badge {
        const val height = 26.0
        const val bottom = Space.md
        const val dot = 5.0
        const val padX = Space.md
        const val gap = Space.sm
        const val blur = 30.0
        const val initialScale = 0.96
    }

    object Coach {
        const val handX = 169.0
        const val handY = 267.0
        const val handWidth = 44.0
        const val handHeight = 70.0
        const val touchX = 10.0
        const val touchY = -7.0
        const val touchSize = 24.0
        const val textTop = 489.0
        const val buttonX = 160.0
        const val buttonY = 571.0
        const val buttonWidth = 79.0
        const val buttonHeight = 30.0
        const val handStroke = 3.2
    }

    val sizes = doubleArrayOf(401.0, 476.0, 874.0)
    val frameWidths = doubleArrayOf(227.0, 268.0, 402.0)
    val frameTops = doubleArrayOf(61.0, 61.0, 0.0)

    object Resize {
        const val holdMs = 260L
        const val gain = 1.15
        const val rubber = 0.3
        const val rubberMax = 60.0
        const val project = 0.18
        const val lift = 0.97
    }

    object Spring {
        const val liftDamping = 18.0
        const val liftStiffness = 340.0
        const val liftMass = 0.7
        const val dropDamping = 22.0
        const val dropStiffness = 460.0
        const val dropMass = 0.6
        const val snapDamping = 24.0
        const val snapStiffness = 210.0
        const val snapMass = 1.0
        const val coachDamping = 20.0
        const val coachStiffness = 200.0
        const val coachMass = 1.0
    }

    object Timing {
        const val fadeChrome = 160L
        const val coachFade = 240L
        const val reduced = 200L
        const val clip = 3000L
        const val crossfade = 400L
        const val kenBurns = 0.06
        const val stripSpeed = 340.0 / 12.0
        const val frameDeltaMax = 0.1
        const val badgeLabelIn = 160L
        const val badgeLabelOut = 120L
        const val ghostRelease = 120L
    }

    const val LOOP_MS = 12_000L
    const val LOOP = LOOP_MS
    const val RESUME_MS = 2_000L

    object Demo {
        const val fingerFade = 0.15
        const val coachDown = 0.4
        const val coachBottom = 1.1
        const val coachUp = 1.9
        const val coachTravel = 36.0
        const val coachGrow = 44.0
        const val gotItShow = 2.15
        const val gotItTap = 2.45
        const val growDown = 3.0
        const val growLifted = 3.26
        const val growMid = 3.9
        const val growHold = 4.5
        const val growEnd = 5.0
        const val growUp = 5.15
        const val shrinkDown = 7.1
        const val shrinkLifted = 7.36
        const val shrinkEnd = 8.5
        const val shrinkUp = 8.6
        const val coachBack = 11.6
        const val tidy = 0.6
    }

    object Upgrade {
        const val buttonPress = 0.96
        const val press = 0.99
        const val pressInMs = 100L
        const val pressOutMs = 160L
        const val magnet = 18.0
        const val magnetPull = 0.45
        const val follow = 16.0
        const val liftShadowOpacity = 0.55
        const val liftShadowRadius = 28.0
        const val liftShadowY = 14.0
        const val rim = 0.22
        const val badgeInMs = 140L
        const val badgeOutMs = 220L
        const val badgeRise = 6.0
        const val stagger = 0.025
    }

    object Timeline {
        const val period = 340.0
        const val repeat = 4
        const val rulerOffset = 139.0
        const val thumbsPerClip = 3
    }

    object Control {
        const val gotIt = 1
        const val topLeft = 2
        const val topRight = 3
        const val timelinePlay = 4
        const val firstTool = 10
        const val editVideo = 30
        const val next = 31
    }

    object Copy {
        val sizeNames = arrayOf("Compact", "Large", "Full screen")
        val accessibilitySizeNames = arrayOf("compact", "standard", "full screen")
        val captions = arrayOf("", "and a lot of", "small tight spaces,", "casual drinking den.")
        val tools = arrayOf("Text", "Sticker", "Audio", "Add clips", "Overlay", "Edit", "Voiceover")
        val toolDim = booleanArrayOf(false, false, false, true, false, true, false)
        val fullTools = arrayOf("Camera", "Captions", "Sticker", "Audio", "Overlay", "Adjust")
        const val coach = "Hold and drag to change\npreview size."
        const val gotIt = "Got it"
        const val audioTrack = "Tap to add audio"
        const val textTrack = "Tap to add text"
        const val hint = "Tap on a track to trim. Pinch to zoom."
        const val editVideo = "Edit video"
        const val next = "Next"
    }

    const val QA_SLOWMO = 1.0
    const val QA_STATE = -1
    val QA_TIMES = doubleArrayOf(9.5, 0.0, 4.5)
    val QA_T = if (QA_STATE >= 0) QA_TIMES[QA_STATE] else 0.0
}

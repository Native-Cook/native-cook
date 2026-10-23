package com.ngocdevv.nativecook.blocks.gestures.giftslotdrop

/** Token values mirrored from the approved React Native Gift Slot Drop block. */
object Tokens {
    const val DESIGN_WIDTH = 402f
    const val MAX_FRAME_SECONDS = 0.1f

    object COLORS {
        const val stage = "#605133"
        const val stageEdge = "#302816"
        const val floor = "#332A13"
        const val floorLow = "#2D2511"
        const val floorLine = "rgba(255,240,190,0.08)"
        const val pocket = "#251902"
        const val pocketFade = "rgba(37,25,2,0)"
        const val pocketLow = "#4F3803"
        const val ink = "#FFFFFF"
        const val mute = "#B9B3A0"
        const val hint = "#CFCABB"
        const val hueTint = 0.05f
        const val stageWarm = 0.3f
        const val stageWarmInk = "#E07A2A"
        const val pocketLight = "rgba(255,224,60,1)"
        const val glowCore = "#F7B812"
        const val cardLift = 0.2f
        const val offerWarm = "rgba(214,200,140,0.28)"
        val cardShade = floatArrayOf(0.2f, 0.25f)
        const val cardShadeInk = "#8A4C00"
        const val yellow = "#FFD23F"
        const val yellowDeep = "#F2A900"
        const val outline = "rgba(255,255,255,0.42)"
        const val button = "#FCD43C"
        const val buttonInk = "#2A2112"
        const val step = "#D8D3C4"
    }

    object SPACE {
        const val xs = 4f
        const val sm = 8f
        const val md = 12f
        const val lg = 16f
        const val xl = 20f
        const val xxl = 24f
        const val gutter = 28f
    }

    object LAYOUT {
        const val headerTop = 63f
        const val offerHeaderTop = 25f
        const val headerLeft = 14f
        const val headerRight = 50f
        const val giftY = 144f
        const val giftDx = -2.5f
        const val giftSize = 47f
        const val titleY = 214f
        const val titleSize = 25.5f
        const val titleLine = 34f
        const val cardY = 427f
        const val cardW = 158f
        const val cardH = 206f
        const val cardR = 12f
        const val spacing = 172f
        const val turnDeg = 42f
        const val sideScale = 1f
        const val chevronY = 605f
        const val pocketW = 180f
        const val pocketTop = 634f
        const val floorY = 669f
        const val slotY = 678f
        const val slotW = 170f
        const val slotH = 24f
        const val glowDrop = 21f
        const val glowTop = -14f
        const val glowH = 92f
        const val pocketDepth = 28f
        const val hintY = 764.5f
        const val offerY = 366f
        const val offerW = 292f
        const val offerH = 505f
        const val howTop = 669f
        const val stepTop = 716f
        const val buttonTop = 780f
        const val buttonH = 60f
        const val buttonW = 357f
        const val headerH = 44f
        const val headerHitSlop = 12f
        const val cardHitSlop = 12f
        const val offerRadius = 14f
        const val detailsRight = 24f
        const val detailRise = 12f
        const val closeRise = 24f
        const val closeScale = 0.06f
    }

    object TYPE {
        const val header = 14f
        const val hint = 17.8f
        const val cardMark = 12f
        const val cardHeadline = 30f
        const val cardLine = 15f
        const val offerHeadline = 52f
        const val offerLine = 28f
        const val offerSmall = 15f
        const val offerMark = 20f
        const val howTitle = 17.3f
        const val stepNumber = 40f
        const val stepText = 18.4f
        const val stepLine = 24f
        const val button = 16f
    }

    object DRAW {
        const val perspective = 700f
        const val frameOuterInset = 7f
        const val frameInnerInset = 6f
        const val frameOuterStroke = 6f
        const val frameInnerStroke = 3f
        const val frameOuterAlpha = 0.55f
        const val frameFadePull = 60f
        const val stageGlowWidth = 400f
        const val stageGlowHeight = 440f
        const val stageGlowTop = 207f
        const val slotHaloInsetX = 24f
        const val slotHaloInsetY = 10f
        const val slotHaloExtraH = 26f
        const val slotHaloAlpha = 0.2f
        const val slotGlowAlpha = 0.72f
        const val chevronHalfW = 11.5f
        const val chevronTop = -7.5f
        const val chevronMid = 1f
        const val chevronSecondTop = 2.5f
        const val chevronBottom = 11f
        const val chevronStroke = 2f
        const val placeholderStroke = 1.2f
        const val cardWordmarkY = 37f
        const val cardHeadlineY = 94f
        const val cardLineY = 111f
        const val cardCapY = 129f
        const val cardBottomLineY = 34f
        const val cardBottomCapY = 15f
        const val offerWordmarkY = 54f
        const val offerPercentY = -44f
        const val offerLineY = -2f
        const val offerUseFirstY = 62f
        const val offerUseSecondY = 82f
        const val offerExpiryInset = 46f
        const val offerWordmarkScale = 1.4f
        const val pocketRadius = 16f
        const val confettiHalfW = 5f
        const val confettiHalfH = 2.6f
        const val confettiMinHalfH = 0.5f
    }

    data class Gift(val id: String, val name: String, val wordmark: String, val headline: String, val line: String, val cap: String, val use: String, val expires: String, val ink: String, val glow: String)

    val GIFTS = listOf(
        Gift("white", "Loomwell", "loomwell", "50%", "cashback", "up to \$20", "Use AERO Pay at Loomwell", "Expires on 27 Nov", "#22201C", "#E9E4DA"),
        Gift("parcels", "Nimbus", "nimbus", "50%", "cashback", "up to \$25", "Use AERO Pay to shop on Nimbus", "Expires on 27 Nov", "#1A2433", "#9CCADC"),
        Gift("burger", "Bunbox", "BUNBOX", "50%", "cashback", "up to \$10", "Order on Bunbox and pay with AERO Pay", "Expires on 27 Nov", "#FFFFFF", "#E8612A"),
        Gift("red", "Volta", "volta", "50%", "cashback on", "mobile recharge", "Recharge your Volta number with AERO Pay", "Expires on 27 Nov", "#FFFFFF", "#D9141C"),
    )
    const val START_INDEX = 2

    object DRAG {
        const val decide = 8f
        const val commit = 170f
        const val full = 250f
        const val minScale = 0.92f
        const val leanDeg = 64f
        const val lift = 1.03f
        const val upResist = 0.2f
        const val swipeVelocity = 600f
    }
    data class Spring(val stiffness: Float, val damping: Float, val mass: Float)
    val SPRINGS = mapOf(
        "snap" to Spring(260f, 28f, 1f),
        "back" to Spring(320f, 26f, 1f),
        "centre" to Spring(420f, 30f, 0.8f),
        "rise" to Spring(170f, 20f, 1f),
        "lift" to Spring(500f, 30f, 0.6f),
    )

    const val insertCentreMs = 160L
    const val insertSinkMs = 300L
    const val insertSinkEaseX1 = 0.77f
    const val insertSinkEaseY1 = 0f
    const val insertSinkEaseX2 = 0.175f
    const val insertSinkEaseY2 = 1f
    const val insertFlashMs = 420L
    const val activateMs = 1150L
    const val activateDotMs = 280L
    const val printFeeds = 3
    const val printFeedShare = 0.22f
    const val printFeedMs = 260L
    const val printFeedGap = 90L
    const val printFeedEaseBack = 1.6f
    const val printStartScale = 0.44f
    const val printRollMs = 900L
    const val printDetailsDelay = 280L
    const val printStagger = 60L
    const val printConfetti = 72
    const val printConfettiLife = 1.6f
    const val printGravity = 820f
    const val ambientFloatPt = 3f
    const val ambientFloatMs = 3000L
    const val ambientRollPerPt = 0.08f
    const val ambientRollMax = 7f
    const val ambientBand = 0.4f
    const val ambientBandMs = 6000L
    const val ambientSlotBreathMs = 2400L
    const val chevronBobPt = 4f
    const val chevronBobMs = 1400L
    const val chevronGap = 18f
    const val closeMs = 260L
    const val closeEaseX1 = 0.23f
    const val closeEaseY1 = 1f
    const val closeEaseX2 = 0.32f
    const val closeEaseY2 = 1f
    const val closeReturnMs = 420L
    const val buttonScale = 0.97f
    const val buttonInMs = 100L
    const val buttonOutMs = 160L
    const val reducedMs = 200L
    const val LOOP_MS = 15000L
    const val resumeAfterMs = 3500L
    const val touchResumeAfterMs = 2000L
    data class Swipe(val at: Long, val ms: Long, val dx: Float)
    val scriptSwipes = listOf(Swipe(1000L, 450L, 200f), Swipe(12700L, 450L, -200f))
    const val scriptPullDown = 2400L
    const val scriptPullUp = 3450L
    const val scriptPullBy = 252f
    const val scriptDone = 11000L
    val QA_HOLD_AT: Long? = null
}

package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

/** Faithful Kotlin token mirror of the approved React Native reference. */
object Tokens {
    data class Hue(
        val light: String,
        val inner: String,
        val rim: String,
        val core: String,
        val halo: String,
        val icon: String,
    )

    data class Habit(
        val id: String,
        val name: String,
        val icon: String,
        val hue: String,
        val r: Double,
        val x: Double,
        val y: Double,
    )

    data class Point(
        val x: Double,
        val y: Double,
    )

    object COLOR {
        const val bg = "#141313"
        const val dust = "rgba(255,255,255,0.05)"
        const val ink = "#FFFFFF"
        const val inkSoft = "rgba(255,255,255,0.55)"
        const val inkFaint = "rgba(255,255,255,0.35)"
        const val control = "#1E1E21"
        const val controlSelected = "#FFFFFF"
        const val controlInkSelected = "#0A0A0B"
        const val disc = "#FFFFFF"
        const val discDim = "rgba(255,255,255,0.1)"
        const val personalTop = "#363637"
        const val personalBottom = "#252526"
        const val card = "#141415"
        const val hairline = "rgba(255,255,255,0.14)"
        const val good = "#5FBF5A"
        const val discInk = "#0A0A0B"
        const val quiet = "#E0565B"
        const val quietCore = "#2A1012"
        const val badge = "#3B6CF6"
        const val spark = "#FFFFFF"
        const val finger = "rgba(255,255,255,0.32)"
        const val fingerRing = "rgba(255,255,255,0.7)"
    }

    val HUE = mapOf(
        "purple" to Hue("#C9A2EE", "#7B2BD6", "#8A52C4", "#1A0F2B", "rgba(138,82,196,0.45)", "#A94FE0"),
        "green" to Hue("#A6EDBF", "#3FCB72", "#5BE38E", "#0C1F14", "rgba(80,220,140,0.36)", "#5CD487"),
        "yellow" to Hue("#F6DE93", "#E8B530", "#F7C948", "#211A08", "rgba(245,195,70,0.36)", "#EFC04A"),
        "orange" to Hue("#F6BC8E", "#EE7A2A", "#F58A3C", "#22130A", "rgba(245,130,60,0.36)", "#EE9049"),
        "red" to Hue("#E0888E", "#A92A33", "#C83A44", "#220C0E", "rgba(232,82,90,0.34)", "#E25860"),
        "glass" to Hue("#A8A8B0", "#6C6C74", "#7C7C86", "#141416", "rgba(160,160,175,0.14)", "rgba(180,180,190,0.3)"),
    )

    object QUIET_GLASS {
        const val body = 0.22
        const val wedge = 0.08
    }

    object GLASS {
        const val interiorSmall = 0.46
        const val interiorLarge = 0.15
        const val interiorFrom = 45.0
        const val interiorSlope = 0.009
        const val bigRadius = 60.0
        const val bandLarge = 0.95
        const val bandSmall = 0.72
        const val rimLight = 0.8
        const val lightX = 0.06
        const val lightY = -0.08
        const val lightR = 1.02
        const val edge = 0.9
        const val halo = 0.45
        const val chargeLift = 0.3
        const val highlight = 0.06
        const val spriteEdge = 0.45
        const val spriteEdgeGlass = 0.12
    }

    const val clusterX = 190.0
    const val clusterY = 380.0
    private fun at(x: Double, y: Double) = Point(x - clusterX, y - clusterY)
    private fun habit(id: String, name: String, icon: String, hue: String, r: Double, x: Double, y: Double): Habit {
        val point = at(x, y)
        return Habit(id, name, icon, hue, r, point.x, point.y)
    }

    val SOCIAL = listOf(
        habit("wave", "Say hi to a neighbour", "hand-left-outline", "purple", 78.0, 150.0, 225.0),
        habit("star", "Thank someone", "star-outline", "purple", 78.0, 309.0, 329.0),
        habit("gift", "Send a small gift", "basket-outline", "red", 28.0, 241.0, 157.0),
        habit("ball", "Team sport", "basketball-outline", "orange", 33.0, 268.0, 218.0),
        habit("friend", "Call a friend", "person-outline", "orange", 34.0, 50.0, 280.0),
        habit("art", "Draw together", "color-palette-outline", "green", 53.0, 83.0, 365.0),
        habit("host", "Host friends", "bed-outline", "yellow", 43.0, 183.0, 347.0),
        habit("magic", "Plan a surprise", "sparkles-outline", "yellow", 43.0, 86.0, 467.0),
        habit("home", "Family dinner", "home-outline", "green", 50.0, 300.0, 465.0),
        habit("tea", "Tea with mum", "cafe-outline", "red", 28.0, 60.0, 536.0),
        habit("heart", "Date night", "heart-outline", "green", 53.0, 143.0, 552.0),
        habit("wallet", "Split the bill", "wallet-outline", "orange", 33.0, 234.0, 535.0),
        habit("ride", "Ride to work", "bicycle-outline", "yellow", 43.0, 311.0, 568.0),
        habit("note", "Write a note", "mail-outline", "glass", 25.0, 255.0, 384.0),
    )
    val PERSONAL = listOf(habit("journal", "Journal", "create-outline", "glass", 22.0, 247.0, 476.0))
    val QUIET = mapOf("r" to 55.0, "x" to 0.0, "y" to 67.0, "title" to "QUIET HOURS", "hours" to "22:00–07:00")
    val CATEGORY = mapOf("home" to "Personal Life", "heart" to "Personal Life", "art" to "Creativity", "wave" to "Neighbours", "star" to "Gratitude", "host" to "Friends", "magic" to "Friends", "ride" to "Health", "friend" to "Friends", "ball" to "Health", "gift" to "Friends", "tea" to "Family Life", "wallet" to "Money", "note" to "Friends", "journal" to "Personal Life")

    object SPACE {
        const val hair = 2.0
        const val xs = 3.0
        const val s = 4.0
        const val m = 6.0
        const val l = 12.0
        const val xl = 16.0
        const val x2 = 18.0
        const val x3 = 20.0
        const val cardTop = 27.0
    }

    object LAYOUT {
        const val gutter = 17.0
        const val headerTop = 1.0
        const val control = 40.0
        const val segmentWidth = 185.0
        const val iconButton = 40.0
        const val clusterTop = 0.0
        const val dateY = 640.0
        const val discY = 792.0
        const val disc = 74.0
        const val dot = 22.0
        const val dotOffset = 60.0
        const val corner = 52.0
        const val cornerInset = 16.0
        const val cornerY = 830.0
        const val gap = 3.0
        const val iconScale = 0.3
        const val ring = 3.0
        const val ringGap = 7.0
        const val fingerRadius = 22.0
        const val sparks = 12.0
        const val personalDateY = 0.654
        const val personalDotX = 0.53
        const val personalDotY = 0.84
        const val detailBalanceY = 0.513
        const val detailCardY = 0.6
        const val rowHeight = 60.0
        const val iconGlow = 10.0
    }
    object TYPE {
        const val segmentSize = 15.0
        const val segmentWeight = "700"
        const val dateSize = 24.0
        const val dateLine = 30.0
        const val dateWeight = "500"
        const val dateColor = COLOR.ink
        const val dateAlignment = "center"
        const val nowSize = 14.0
        const val nowLine = 18.0
        const val nowWeight = "500"
        const val nowColor = COLOR.inkSoft
        const val nowAlignment = "center"
        const val countSize = 17.0
        const val countWeight = "800"
        const val countColor = COLOR.discInk
        const val countFontVariant = "tabular-nums"
        const val categorySize = 12.0
        const val categoryWeight = "500"
        const val categoryColor = COLOR.inkSoft
        const val categoryAlignment = "center"
        const val categoryTitleSize = 20.0
        const val categoryTitleWeight = "700"
        const val categoryTitleColor = COLOR.ink
        const val categoryTitleAlignment = "center"
        const val balanceSize = 17.0
        const val balanceWeight = "500"
        const val balanceColor = COLOR.inkSoft
        const val goodSize = 14.0
        const val goodWeight = "700"
        const val goodColor = COLOR.ink
        const val sectionTitleSize = 24.0
        const val sectionTitleLine = 30.0
        const val sectionTitleWeight = "600"
        const val sectionTitleColor = COLOR.ink
        const val bodySize = 17.0
        const val bodyLine = 25.0
        const val bodyWeight = "400"
        const val bodyColor = COLOR.inkSoft
        const val personalNowSize = 20.0
        const val personalNowLine = 26.0
        const val personalNowWeight = "500"
        const val personalNowColor = COLOR.inkSoft
        const val personalNowAlignment = "center"
        const val personalDateSize = 36.0
        const val personalDateLine = 42.0
        const val personalDateWeight = "500"
        const val personalDateColor = COLOR.ink
        const val personalDateAlignment = "center"
        const val quiet = 9.0
    }
    object COPY {
        val tabs = listOf("Social", "Personal")
        const val date = "14 October"
        const val now = "Now - 08:12"
        const val personalDate = "26 October"
        const val category = "Category"
        const val balance = "Balance"
        const val good = "Good"
        const val description = "Description"
        val body = listOf(
            "Small rituals with the people you love keep a week from slipping by. This habit counts the evenings you sit down together, phones away, and asks nothing else of you.",
            "We look at when you usually manage it and nudge you on the days that fit, never on the busy ones.",
        )
        const val avatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80"
    }

    object PHYSICS {
        const val step = 1.0 / 120.0
        const val stiffness = 120.0
        const val damping = 14.0
        const val radiusStiffness = 260.0
        const val radiusDamping = 18.0
        const val separation = 0.5
        const val relaxIterations = 240
        const val relaxGravity = 0.04
        const val maxFling = 2400.0
        const val maxFrameMs = 250.0
    }

    object HOLD {
        const val delayMs = 120L
        const val chargeMs = 900L
        const val drainMs = 220L
        const val moveTolerance = 10.0
        const val squeeze = 0.94
        val ticks = listOf(1.0 / 3.0, 2.0 / 3.0)
        const val doneScale = 0.62
        const val tapMs = 220L
    }

    object BURST {
        const val flashMs = 240L
        const val sparkMs = 620L
        const val sparkFrom = 6.0
        const val sparkNear = 28.0
        const val sparkFar = 44.0
    }

    object IDLE {
        const val breathe = 0.015
        const val cyclesMin = 2
    }

    object HINT {
        const val inMs = 180L
        const val holdMs = 1400L
        const val outMs = 260L
    }

    object LIQUID {
        const val period1 = 140.0
        const val period2 = 230.0
        const val amp1 = 3.0
        const val amp2 = 1.6
        const val top = 0.75
        const val bottom = 0.5
    }

    object WAVE {
        const val speed = 520.0
        const val reach = 120.0
    }

    object STAGGER {
        const val stepMs = 30L
        const val per = 60.0
    }

    object SCATTER {
        const val distance = 220.0
    }

    object DETAIL {
        const val y = 0.333
        const val radius = 70.0
        const val fadeMs = 240L
        const val fadeOutMs = 160L
        val ease = listOf(0.23, 1.0, 0.32, 1.0)
    }

    object SWITCH {
        const val fadeMs = 260L
        const val stagger = 22L
        const val reducedMs = 200L
    }
    const val LOOP_MS = 12000L
    val QA_HOLD: String? = null
    val QA_HOLD_AT = mapOf("rest" to 0L, "a" to 8300L, "b" to 4600L)
    object AUTOPLAY {
        const val resumeDelayMs = 3500L
        const val holdId = "star"
        const val holdStart = 600L
        const val holdEnd = 1900L
        const val openId = "home"
        const val openAt = 3200L
        const val backAt = 5400L
        const val personalAt = 6800L
        const val socialAt = 8800L
        const val tapAt = 10400L
        const val tapMs = 120L
    }

    object ATLAS {
        const val size = 360
        const val columns = 5
        const val reach = 1.45
        val order = listOf("wave", "star", "gift", "ball", "friend", "art", "host", "magic", "home", "tea", "heart", "wallet", "ride", "note", "journal")
        val alias = mapOf("note" to "journal", "quiet" to "journal")
    }

    const val iconBox = 64.0
    const val maxWallDeltaSeconds = 0.1
    const val interactionResumeDelayMs = 2000L
}

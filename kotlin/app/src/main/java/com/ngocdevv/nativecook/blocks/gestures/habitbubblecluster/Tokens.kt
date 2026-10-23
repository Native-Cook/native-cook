package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

internal object HabitBubbleClusterTokens {
    data class Hue(
        val tint: FloatArray,
        val top: String,
        val bottom: String,
        val ink: String,
        val icon: String,
    )

    data class Habit(
        val id: String,
        val name: String,
        val icon: String,
        val hue: String,
        val radius: Double,
        val x: Double,
        val y: Double,
    )

    data class Entity(
        val habit: Habit,
        val set: Int,
        val quiet: Boolean = false,
    )

    data class Point(val x: Double, val y: Double)

    const val Background = "#0E1013"
    const val Ink = "#F3F4F2"
    const val InkSoft = "#99F3F4F2"
    const val InkFaint = "#5CF3F4F2"
    const val Hairline = "#1AFFFFFF"
    const val SelectedInk = "#111316"
    const val Finger = "#24FFFFFF"
    const val FingerRing = "#6BFFFFFF"

    val Hues = mapOf(
        "purple" to Hue(floatArrayOf(0.66f, 0.73f, 0.86f), "#B9C6DF", "#8193B5", "#1C2333", "#E4EAF5"),
        "green" to Hue(floatArrayOf(0.62f, 0.78f, 0.66f), "#B7D3BD", "#7FA38A", "#17261C", "#E3F0E6"),
        "yellow" to Hue(floatArrayOf(0.88f, 0.79f, 0.60f), "#E8D5AE", "#BFA372", "#2B2213", "#F6EEDD"),
        "orange" to Hue(floatArrayOf(0.86f, 0.66f, 0.54f), "#E3BBA4", "#B98468", "#2D1C13", "#F6E6DC"),
        "red" to Hue(floatArrayOf(0.82f, 0.62f, 0.62f), "#DDB2B0", "#AE7C7B", "#2C1717", "#F5E3E2"),
        "glass" to Hue(floatArrayOf(0.80f, 0.82f, 0.85f), "#D3D7DD", "#9CA3AD", "#1C1F24", "#EEF0F3"),
    )

    const val ClusterX = 190.0
    const val ClusterY = 380.0

    private fun at(x: Double, y: Double) = Point(x - ClusterX, y - ClusterY)

    private fun habit(
        id: String,
        name: String,
        icon: String,
        hue: String,
        radius: Double,
        x: Double,
        y: Double,
    ): Habit {
        val point = at(x, y)
        return Habit(id, name, icon, hue, radius, point.x, point.y)
    }

    private val Social = listOf(
        habit("wave", "Say hi to a neighbour", "wave", "purple", 78.0, 150.0, 225.0),
        habit("star", "Thank someone", "star", "purple", 78.0, 309.0, 329.0),
        habit("gift", "Send a small gift", "gift", "red", 28.0, 241.0, 157.0),
        habit("ball", "Team sport", "ball", "orange", 33.0, 268.0, 218.0),
        habit("friend", "Call a friend", "friend", "orange", 34.0, 50.0, 280.0),
        habit("art", "Draw together", "art", "green", 53.0, 83.0, 365.0),
        habit("host", "Host friends", "host", "yellow", 43.0, 183.0, 347.0),
        habit("magic", "Plan a surprise", "magic", "yellow", 43.0, 86.0, 467.0),
        habit("home", "Family dinner", "home", "green", 50.0, 300.0, 465.0),
        habit("tea", "Tea with mum", "tea", "red", 28.0, 60.0, 536.0),
        habit("heart", "Date night", "heart", "green", 53.0, 143.0, 552.0),
        habit("wallet", "Split the bill", "wallet", "orange", 33.0, 234.0, 535.0),
        habit("ride", "Ride to work", "ride", "yellow", 43.0, 311.0, 568.0),
        habit("note", "Write a note", "note", "glass", 25.0, 255.0, 384.0),
    )

    private val Personal = listOf(
        habit("read", "Read 20 pages", "read", "purple", 70.0, 176.0, 250.0),
        habit("sleep", "In bed by 23:00", "sleep", "purple", 60.0, 289.0, 322.0),
        habit("meditate", "Meditate", "meditate", "green", 53.0, 86.0, 340.0),
        habit("run", "Morning run", "run", "green", 50.0, 187.0, 373.0),
        habit("cook", "Cook at home", "cook", "green", 46.0, 270.0, 431.0),
        habit("spanish", "Spanish lesson", "spanish", "yellow", 43.0, 288.0, 215.0),
        habit("water", "Two litres of water", "water", "yellow", 40.0, 114.0, 432.0),
        habit("guitar", "Practise guitar", "guitar", "yellow", 38.0, 189.0, 465.0),
        habit("stretch", "Stretch", "stretch", "orange", 34.0, 68.0, 250.0),
        habit("phone", "No phone in bed", "phone", "orange", 31.0, 356.0, 254.0),
        habit("vitamins", "Vitamins", "vitamins", "red", 28.0, 247.0, 505.0),
        habit("plants", "Water the plants", "plants", "red", 26.0, 47.0, 413.0),
        habit("journal", "Journal", "journal", "glass", 24.0, 132.0, 498.0),
    )

    val QuietHabit = habit("quiet", "QUIET HOURS", "quiet", "glass", 55.0, 190.0, 447.0)
    val Entities = buildList {
        Social.forEach { add(Entity(it, set = 0)) }
        add(Entity(QuietHabit, set = 0, quiet = true))
        Personal.forEach { add(Entity(it, set = 1)) }
    }
    val QuietIndex = Social.size

    object Layout {
        const val gutter = 16.0
        const val control = 44.0
        const val segmentWidth = 196.0
        const val dateDrop = 252.0
        const val pill = 44.0
        const val gap = 3.0
        const val iconScale = 0.30
        const val ring = 2.0
        const val ringGap = 6.0
        const val fingerRadius = 22.0
        const val labelHeight = 52.0
        const val labelGap = 10.0
    }

    object Physics {
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

    object Hold {
        const val delayMs = 120.0
        const val chargeMs = 900.0
        const val drainMs = 220.0
        const val moveTolerance = 10.0
        const val squeeze = 0.94
        val ticks = doubleArrayOf(1.0 / 3.0, 2.0 / 3.0)
        const val doneScale = 0.62
        const val tapMs = 220.0
    }

    object Idle {
        const val breathe = 0.015
        const val cyclesMin = 2
    }

    object Liquid {
        const val period1 = 140.0
        const val period2 = 230.0
        const val amplitude1 = 3.0
        const val amplitude2 = 1.6
    }

    object Wave {
        const val speed = 520.0
        const val reach = 120.0
    }

    object Stagger {
        const val stepMs = 30.0
        const val per = 60.0
    }

    object Label {
        const val enterMs = 180.0
        const val holdMs = 1600.0
        const val exitMs = 220.0
        const val rise = 6.0
    }

    object Ripple {
        const val durationMs = 700.0
        const val reach = 26.0
    }

    const val LoopMs = 10_000L

    object Autoplay {
        const val resumeDelayMs = 2_000.0
        const val holdId = "star"
        const val holdStart = 400.0
        const val holdEnd = 1_700.0
        const val nameId = "home"
        const val nameAt = 2_700.0
        const val personalAt = 4_200.0
        const val socialAt = 6_400.0
        const val tapAt = 8_000.0
        const val tapMs = 120.0
    }

    const val Date = "Tuesday, 14 October"
    const val Now = "Hold a habit to mark it done"
    const val Monogram = "AM"
    val Tabs = listOf("Social", "Personal")
}

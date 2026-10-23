package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

/** Exact value snapshot of the approved React Native theme.ts, motion.ts and component styles. */
internal object MoodOrbJarTokens {
    const val STAGE_WIDTH = 402.0
    const val REPLICA = false

    object Colors {
        const val bg = "#F4FDFE"
        const val bgBottom = "#E3F0FB"
        const val card = "#F7FCFF"
        const val label = "#3A3A3F"
        const val secondary = "#8F9298"
        const val cardShadow = "rgba(60, 100, 160, 0.08)"
        const val ink = "#16233A"
        const val mute = "rgba(22, 35, 58, 0.55)"
        const val faint = "rgba(22, 35, 58, 0.35)"
        const val glass = "rgba(255, 255, 255, 0.38)"
        const val glassBack = "rgba(214, 230, 246, 0.55)"
        const val glassEdge = "rgba(255, 255, 255, 0.95)"
        const val glassShade = "rgba(110, 150, 200, 0.28)"
        const val floorShadow = "rgba(70, 110, 170, 0.18)"
        val lid = arrayOf("#8E949C", "#D8DDE3", "#F7F8FA", "#B4BAC2", "#7C828A")
        val lidStops = doubleArrayOf(0.0, 0.2, 0.42, 0.75, 1.0)
        const val lidRidge = "rgba(60, 66, 74, 0.25)"
        const val print = "#0E0E10"
        const val danger = "#F4545E"
        const val dangerInk = "#FFFFFF"
        const val dotsBg = "#EEF1F5"
        const val sheetLine = "rgba(22, 35, 58, 0.1)"
        const val dotsInk = "#5B616B"
        const val aim = "rgba(22, 35, 58, 0.18)"
    }

    data class Mood(
        val name: String,
        val base: String,
        val light: String,
        val deep: String,
        val tags: String,
    )

    val MOODS = arrayOf(
        Mood("Awful", "#A24CE6", "#E0B0FF", "#6A22A8", "Angry, Overwhelmed"),
        Mood("Bad", "#5E7DF6", "#BCCBFF", "#2F4CC2", "Disgusted, Friends"),
        Mood("Neutral", "#3FC6E6", "#B4F0FF", "#1689A8", "Work, Tired"),
        Mood("Good", "#57D27A", "#C2F5CF", "#22964A", "Calm, Partner"),
        Mood("Great", "#F4545E", "#FFC2B8", "#B8243A", "Family, Sunshine"),
    )

    object Space {
        const val xs = 4.0
        const val sm = 8.0
        const val cardPad = 12.0
        const val lg = 16.0
        const val cardGap = 18.0
        const val avatarTop = 25.0
    }

    object Layout {
        const val side = 16.0
        const val header = 44.0
        const val icon = 44.0
        const val lidWidth = 192.0
        const val lidHeight = 36.7
        const val lidOverlap = 10.0
        const val jarTop = 47.0
        const val jarImageW = 280.0 * 1.01
        const val jarImageH = 326.7
        const val jarImageMouthY = 10.0
        const val jarWidth = 276.0 * 1.01
        const val jarHeight = 300.0
        const val base = 2.0
        const val wall = 6.0
        const val shoulder = 70.0
        const val corner = 46.0
        const val mouthHalf = 84.0 * 1.01
        const val printSize = 30.0
        const val printStroke = 5.0
        const val printY = 0.724
        const val labelY = 0.12
        const val pickerTop = 400.0
        const val pickerHeight = 96.0
        const val pickerOrb = 20.2
        const val pickerOrbY = 41.0
        const val slotGap = 67.33
        const val slotDx = 0.8
        const val slotLabelH = 63.0
        const val labelSize = 13.0
        const val cardRadius = 16.0
        const val entriesTop = 512.0
        const val entryHeight = 106.0
        const val sectionH = 26.0
        const val rowHeight = 68.0
        const val hairlineInset = 70.0
        const val entryGap = 18.0
        const val entryOrb = 22.0
        const val orb = 25.0
    }

    object Style {
        const val headerInset = 8.0
        const val chevronSize = 12.0
        const val chevronStroke = 2.2
        const val chevronInset = 4.0
        const val calendarSize = 22.0
        const val calendarRadius = 5.0
        const val calendarStroke = 2.0
        const val calendarTextSize = 10.0
        const val calendarTextTop = 1.0
        const val moreTextSize = 13.0
        const val moreTracking = 1.0
        const val slotHitHalfWidth = 30.0
        const val slotHitTop = 24.0
        const val monthSize = 11.0
        const val monthTracking = 2.2
        const val countBoxHeight = 30.0
        const val countSize = 24.0
        const val countLineHeight = 30.0
        const val undoRight = 24.0
        const val undoSize = 15.0
        const val avatarTextGap = 16.3
        const val entryTextTop = 27.4
        const val entryTitleSize = 16.0
        const val entryTitleLineHeight = 20.0
        const val entryTitleTracking = -0.2
        const val entryLineSize = 14.0
        const val entryLineHeight = 18.0
        const val entryLineGap = 4.1
        const val dotsRight = 11.3
        const val dotsTop = 14.3
        const val dotsSize = 27.0
        const val dotsRadius = 8.0
        const val dotsStroke = 2.0
        const val dotsGap = 3.0
        const val dotSize = 3.0
        const val cardShadowY = 4.0
        const val cardShadowBlur = 10.0
        const val orbImageScale = 144.0 / 132.0
        const val underFull = 60.0
        const val entryRise = 25.0
        const val entryEnterMs = 260
        const val entryExitMs = 160
        const val entryMoveMs = 220
        const val countEnterMs = 260
        const val countExitMs = 200
        const val aimStartRadius = 2.2
        const val aimRadiusStep = 0.04
        const val rippleStroke = 3.0
        const val rippleAlpha = 0.8
    }

    val PILE_MOODS = intArrayOf(3, 0, 1, 2, 0, 1, 2, 4, 0, 1, 2, 0, 3, 2, 0, 3)

    data class Entry(
        val mood: Int,
        val tags: String,
        val time: String,
    )

    object Copy {
        const val today = "Today"
        const val entriesWord = "entries"
        const val month = "OCTOBER"
        const val print = "US?"
        const val back = "Back"
        const val calendar = "Calendar"
        const val more = "More"
        const val undo = "Undo"
        const val now = "Just now"
        val replicaEntry = Entry(0, "Ashamed, Partner", "4:21 PM")
        val entries = arrayOf(
            Entry(1, "Disgusted, Friends", "4:20 PM"),
            Entry(2, "Work, Tired", "1:05 PM"),
        )

        fun add(mood: String) = "Add $mood"
    }

    data class ReplicaPile(
        val x: Int,
        val y: Int,
        val mood: Int,
        val turn: Int,
    )

    const val REPLICA_PLATE_Y = 490.0
    const val REPLICA_PLATE_HEIGHT = 950.0
    const val REPLICA_CUT = 2.0
    val REPLICA_PILE = arrayOf(
        ReplicaPile(484, 899, 3, 0),
        ReplicaPile(634, 885, 1, -4),
        ReplicaPile(942, 884, 2, 128),
        ReplicaPile(311, 919, 0, -84),
        ReplicaPile(554, 1019, 4, 0),
        ReplicaPile(717, 1009, 2, 116),
        ReplicaPile(867, 1015, 0, -90),
        ReplicaPile(272, 1061, 0, -16),
        ReplicaPile(418, 1024, 0, 44),
        ReplicaPile(632, 1154, 0, -40),
        ReplicaPile(295, 1205, 3, 12),
        ReplicaPile(489, 1145, 1, -4),
        ReplicaPile(785, 1140, 1, 100),
        ReplicaPile(937, 1146, 1, -45),
        ReplicaPile(415, 1279, 0, -75),
        ReplicaPile(566, 1276, 1, -90),
        ReplicaPile(711, 1281, 0, 0),
        ReplicaPile(852, 1270, 2, -8),
    )

    const val STEP = 1.0 / 120.0
    const val MAX_STEPS = 30
    const val MAX_FRAME_DELTA = 0.1

    object Pile {
        const val count = 16
        const val seed = 11
        const val settleSteps = 900
        const val home = 60.0
        const val damping = 4.0
        const val passes = 3
        const val restSpeed = 18.0
        const val restTime = 0.35
        const val collisionScale = 0.9
    }

    object Throw {
        const val gravity = 2200.0
        const val apex = 70.0
        const val solved = 0.75
        const val minFlick = 380.0
        const val lift = 1.15
        const val spin = 6.0
        const val regrow = 0.3
        const val maxSpeed = 2400.0
    }

    object Undo {
        const val threshold = 96.0
        const val rise = 0.35
        const val fly = 0.55
        const val rowSpringStiffness = 300.0
        const val rowSpringDamping = 28.0
    }

    object Lid {
        const val open = 1.0
        const val lift = 20.0
        const val tilt = 0.09
        const val stiffness = 120.0
        const val damping = 9.0
        const val hold = 2.4
    }

    object Haptic {
        const val grab = 1
        const val throwValue = 2
        const val land = 3
        const val undo = 4
        const val landSoft = 5
        const val landHard = 6
    }

    object Soft {
        const val stiffness = 420.0
        const val damping = 14.0
        const val perSpeed = 1.0 / 2600.0
        const val max = 0.28
        const val landing = 0.15
        const val trigger = 140.0
        const val hard = 900.0
        const val soft = 350.0
    }

    object Tilt {
        const val slosh = 14.0
        const val glass = 6.0
    }

    object Ghost {
        const val duration = 0.85
        const val lift = 0.3
        const val rise = 46.0
        const val arc = 60.0
        const val grow = 0.36
    }

    object Ripple {
        const val duration = 0.6
        const val radius = 46.0
    }

    object JarKnock {
        const val amp = 1.5
        const val decay = 14.0
        const val hz = 9.0
    }

    object Hop {
        const val every = 2.0
        const val phase = 1.0
        const val speed = 70.0
    }

    object Aim {
        const val points = 14
        const val step = 0.045
    }

    object Reduced {
        const val fade = 0.2
    }

    const val LOOP_MS = 12_000.0
    const val RESUME_MS = 2_000.0

    object Script {
        const val mood = 4
        val pick = doubleArrayOf(1.0, 1.35)
        val pickBy = doubleArrayOf(-30.0, -80.0)
        val release = doubleArrayOf(-500.0, -1500.0)
        val swipe = doubleArrayOf(7.0, 7.55)
        const val swipeBy = -150.0
    }

    const val REPLICA_HOLD = -1.0
    const val QA_SPEED = 1.0

    object Trail {
        const val copies = 4
        const val gap = 0.028
        val alpha = floatArrayOf(0.32f, 0.2f, 0.12f, 0.06f)
    }
}

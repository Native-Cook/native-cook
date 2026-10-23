package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

/**
 * The complete value surface of the React Native Glass Lock Key block.
 *
 * Dimensions are density-independent pixels (the RN points), colours stay as the
 * source strings so the renderer can choose its platform colour parser, and all
 * numeric arrays use doubles to keep the simulation equations lossless.
 */
internal object GlassLockKeyTokens {
    const val stageWidth: Double = 402.0

    object COLORS {
        const val bgTop: String = "#EEEEF0"
        const val bgBottom: String = "#F6F6F7"
        const val shadow: String = "rgba(40, 46, 120, 0.16)"
        const val bodyTop: String = "#4A54EC"
        const val bodyBottom: String = "#2F37C9"
        const val bodyLight: String = "rgba(150, 165, 255, 0.85)"
        const val bodyLightFar: String = "rgba(150, 165, 255, 0)"
        const val side: String = "#7F8BFF"
        const val sideDeep: String = "#5862F0"
        const val rim: String = "rgba(255, 255, 255, 0.85)"
        const val rimSoft: String = "rgba(255, 255, 255, 0.35)"
        const val glass: String = "rgba(132, 146, 255, 0.85)"
        const val glassDeep: String = "rgba(84, 98, 238, 0.9)"
        const val glassEdge: String = "rgba(255, 255, 255, 0.9)"
        const val plug: String = "#2A31B0"
        const val slot: String = "#151A6E"
        const val glow: String = "rgba(130, 150, 255, 0.9)"
        const val glint: String = "rgba(255, 255, 255, 0.75)"
        const val ink: String = "#16182E"
        const val mute: String = "rgba(22, 24, 46, 0.55)"
        const val error: String = "#D6304A"
        const val sheet: String = "#FFFFFF"
        const val sheetLine: String = "rgba(22, 24, 46, 0.08)"
        const val rowIcon: String = "#E9EBFF"
        const val rowIconInk: String = "#3D46E0"
        const val button: String = "#16182E"
        const val buttonInk: String = "#FFFFFF"
    }

    object LOCK {
        const val topRatio: Double = 0.36
        const val face: Double = 150.0
        const val height: Double = 180.0
        const val side: Double = 22.0
        const val radius: Double = 26.0
        const val shackle: Double = 30.0
        const val opening: Double = 68.0
        const val shackleRise: Double = 100.0
        const val shortLeg: Double = 8.0
        const val longLeg: Double = 60.0
        const val openLift: Double = 58.0
        const val openTilt: Double = 0.12
        const val plugWidth: Double = 28.0
        const val plugHeight: Double = 9.0
        const val slot: Double = 3.0
        const val glowRadius: Double = 34.0
    }

    object KEY {
        const val restRatio: Double = 0.70
        const val ring: Double = 64.0
        const val rim: Double = 11.0
        const val shaft: Double = 11.0
        const val blade: Double = 100.0
        const val bitWidth: Double = 18.0
        const val bitHeight: Double = 30.0
        const val bitFromTip: Double = 8.0
        const val collar: Double = 18.0
        const val hit: Double = 88.0
    }

    object TYPE {
        const val side: Double = 32.0
        const val top: Double = 24.0
        const val eyebrowSize: Double = 12.0
        const val eyebrowTracking: Double = 1.8
        const val titleSize: Double = 40.0
        const val titleLine: Double = 46.0
        const val titleFamily: String = "Georgia"
        const val hintSize: Double = 15.0
        const val hintLine: Double = 20.0
        const val gap: Double = 8.0
        const val bottom: Double = 32.0
    }

    object SHEET {
        const val heightRatio: Double = 0.5
        const val radius: Double = 28.0
        const val pad: Double = 24.0
        const val row: Double = 64.0
        const val icon: Double = 40.0
        const val iconRadius: Double = 12.0
        const val titleSize: Double = 17.0
        const val subSize: Double = 13.0
        const val headSize: Double = 22.0
        const val button: Double = 52.0
        const val rise: Double = 0.075
        const val shrink: Double = 0.62
    }

    object SPACE {
        const val xs: Double = 4.0
        const val sm: Double = 8.0
        const val md: Double = 12.0
        const val lg: Double = 16.0
        const val xl: Double = 24.0
        const val xxl: Double = 32.0
    }

    object COPY {
        const val eyebrow: String = "VAULT"
        const val locked: String = "Locked"
        const val unlocked: String = "Unlocked"
        const val hint: String = "Slide the key in, then turn it right"
        const val wrongWay: String = "The other way"
        const val meta: String = "3 items · end-to-end encrypted"
        const val head: String = "Personal vault"
        const val sheetMeta: String = "Opened just now"

        data class Row(
            val title: String,
            val sub: String,
            val icon: String,
            val symbol: String,
        )

        val rows: List<Row> = listOf(
            Row("Recovery codes", "12 codes · added 3 days ago", "#", "number"),
            Row("Passport scan", "PDF · 2.4 MB", "P", "doc.text"),
            Row("Home Wi-Fi", "Password · shared with 2", "W", "wifi"),
        )

        const val lock: String = "Lock vault"
        const val unlock: String = "Unlock vault"
        const val keyLabel: String = "Key"
        const val keyHint: String = "Swipe up to insert and turn the key"
    }

    const val replica: Boolean = false
    const val replicaBg: String = "#EFEFEF"

    data class Point(val x: Double, val y: Double)
    data class Circle(val x: Double, val y: Double, val r: Double)
    data class Glow(val r: Double, val color: String)

    object UPG {
        val keyFront = Point(200.7, 436.3)
        val keyRest = Point(200.7, 436.3)
        val plug = Point(145.3, 461.0)
        val ring = Point(107.0, 443.0)
        const val insertAngle: Double = -1.19
        val lockCentre = Point(201.0, 445.0)
        val darkCentre = Circle(220.0, 470.0, 120.0)
        const val darkColor: String = "rgba(20, 24, 120, 0.55)"
        val glow = Glow(260.0, "rgba(80, 96, 255, 0.35)")
        val bg: List<String> = listOf("#F2F2F6", "#E6E8F3")
    }

    object REPLICA_FIT {
        val lockCentre = doubleArrayOf(660.0, 1416.0)
        val keyCentre = doubleArrayOf(436.0, 1383.0)
        val frontCentre = doubleArrayOf(602.0, 1309.0)
        val lock = doubleArrayOf(1.01, 1.01, 0.0, 0.0, 0.0)
        val shackleClosed = doubleArrayOf(3.0, 0.0)
        val shackleOpen = doubleArrayOf(-4.0, 1.0)
        val keyClosed = doubleArrayOf(1.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        val keyOpen = doubleArrayOf(0.95, 0.885, 0.0, 39.0, 25.5, -0.075)
        val front = doubleArrayOf(1.01, 0.99, 0.0, 0.0, 2.0)
    }

    /** Values authored inline by the shipped React Native GlassUpgrade view. */
    object VIEW {
        const val keyTargetHalfWidth: Double = 44.0
        const val keyTargetTop: Double = 70.0
        const val keyTargetWidth: Double = 88.0
        const val keyTargetHeight: Double = 140.0
        const val sheetOffscreenExtra: Double = 40.0
        const val headerSheetFade: Double = 0.4
        const val titleBoxHeight: Double = 44.0
        const val titleSize: Double = 36.0
        const val titleLineHeight: Double = 44.0
        const val titleTracking: Double = -0.8
        const val titleTransitionStart: Double = 0.15
        const val titleTransitionSpan: Double = 0.7
        const val titleTravel: Double = 8.0
        const val metaSize: Double = 13.0
        const val hintSize: Double = 14.0
        const val hintSheetFade: Double = 3.0
        const val sheetBlurIntensity: Double = 60.0
        const val androidSheetBlurRadius: Double = 20.0
        const val sheetTintAlpha: Double = 0.45
        const val sheetEdgeAlpha: Double = 0.95
        const val sheetEdgeHeight: Double = 1.0
        const val sheetTitleSize: Double = 20.0
        const val sheetTitleTracking: Double = -0.4
        const val sheetMetaSize: Double = 12.0
        const val groupAlpha: Double = 0.7
        const val rowStart: Double = 0.1
        const val rowHeight: Double = 60.0
        const val rowIconSize: Double = 32.0
        const val rowIconInkSize: Double = 17.0
        const val rowTitleSize: Double = 15.0
        const val rowTitleTracking: Double = -0.2
        const val rowSubSize: Double = 12.0
        const val rowSubGap: Double = 2.0
        const val chevronSize: Double = 12.0
        const val hairlineInset: Double = 60.0
        const val buttonHeight: Double = 48.0
        const val buttonRadius: Double = 24.0
        const val buttonIconSize: Double = 15.0
        const val buttonTextSize: Double = 16.0
        const val pressScale: Double = 0.97
        const val pressInMs: Int = 120
        const val pressOutMs: Int = 160
        const val pressBezierX1: Double = 0.23
        const val pressBezierY1: Double = 1.0
        const val pressBezierX2: Double = 0.32
        const val pressBezierY2: Double = 1.0
        const val backgroundArgb: Long = 0xFFEFEFEF
        const val inkArgb: Long = 0xFF16182E
        const val whiteArgb: Long = 0xFFFFFFFF
        const val secondary50Argb: Long = 0x8016182E
        const val secondary55Argb: Long = 0x8C16182E
        const val secondary30Argb: Long = 0x4D16182E
        const val divider12Argb: Long = 0x1F16182E
        const val rowIconBgArgb: Long = 0x1A3D46E0
        const val rowIconInkArgb: Long = 0xFF3D46E0
    }

    /** Values authored inline by the shipped React Native Skia layer tree. */
    object RENDER {
        const val sourceWidth: Double = 1206.0
        const val glowBaseOpacity: Double = 0.6
        const val glowSheetOpacity: Double = 0.4
        const val shackleTravelX: Double = 23.0
        const val shackleTravelY: Double = 90.0
        const val seatedBlendStart: Double = 0.6
        const val seatedBlendSpan: Double = 0.4
        const val keyTurnMinimum: Double = -0.15
        const val looseLiftScale: Double = 0.05
        const val restingLiftThreshold: Double = 0.01
        const val spinMinimumScale: Double = 0.06
        const val tickRadius: Double = 40.0
        const val glintStartOffset: Double = -120.0
        const val glintTravel: Double = 240.0
        const val glintTopOffset: Double = -140.0
        const val glintWidth: Double = 36.0
        const val glintHeight: Double = 280.0
        const val glintSkew: Double = -0.35
        const val glintOpacity: Double = 0.8
        const val glintCenterAlpha: Double = 0.75
    }

    /** Inline state-machine thresholds from glass-sim.ts plus the native clock contract. */
    object SIM {
        const val maxFrameDelta: Double = 0.1
        const val interactionResumeMs: Double = 2_000.0
        const val flickDistanceMultiplier: Double = 2.2
        const val wrongReleaseThreshold: Double = -0.05
        const val autoFinishDelay: Double = 0.1
        const val liftRate: Double = 14.0
        const val magnetExponent: Double = 0.6
        const val pullOutAngle: Double = 0.05
        const val closeSheetThreshold: Double = 0.02
        const val closeShackleThreshold: Double = 0.05
        const val closeAngleThreshold: Double = 0.02
        const val detentEpsilon: Double = 0.0001
        const val showThreshold: Double = 0.01
        const val carryTargetOffsetX: Double = 6.0
        const val carryTargetOffsetY: Double = 10.0
        const val restPositionEpsilon: Double = 0.1
        const val restVelocityEpsilon: Double = 0.5
        const val restAngleEpsilon: Double = 0.005
        const val restVisualEpsilon: Double = 0.01
    }

    const val step: Double = 1.0 / 120.0
    const val maxSteps: Double = 30.0

    data class StiffnessDamping(val stiffness: Double, val damping: Double)

    object CARRY {
        const val lift: Double = 1.04
        const val magnet: Double = 72.0
        const val magnetPull: Double = 0.55
        const val seat: Double = 12.0
        val home = StiffnessDamping(220.0, 22.0)
        const val bob: Double = 3.0
        const val bobPeriod: Double = 3.0
        const val pullOut: Double = 34.0
    }

    object INSERT {
        const val duration: Double = 0.22
        const val outDuration: Double = 0.26
    }

    object TURN {
        const val radius: Double = 60.0
        const val open: Double = Math.PI / 2.0
        val detents = doubleArrayOf(Math.PI / 6.0, Math.PI / 3.0, Math.PI / 2.0)
        const val wrong: Double = 0.21
        const val visual: Double = 0.83
        val back = StiffnessDamping(260.0, 24.0)
        val close = StiffnessDamping(180.0, 22.0)
    }

    object SHACKLE {
        const val stiffness: Double = 320.0
        const val damping: Double = 14.0
        const val closeStiffness: Double = 260.0
        const val closeDamping: Double = 26.0
    }

    object BOUNCE {
        const val stiffness: Double = 420.0
        const val damping: Double = 16.0
        const val detentKick: Double = -40.0
        const val openKick: Double = -120.0
    }

    object SHAKE {
        const val duration: Double = 0.36
        const val amp: Double = 6.0
        const val cycles: Double = 3.0
    }

    object SHEET_MOTION {
        const val delay: Double = 0.3
        const val open: Double = 0.5
        const val close: Double = 0.38
        const val stagger: Double = 0.04
        const val reducedFade: Double = 0.2
        const val shackleAfter: Double = 0.3
    }

    object GLINT {
        const val duration: Double = 0.7
    }

    object HAPTIC {
        const val grab: Double = 1.0
        const val seat: Double = 2.0
        const val detent: Double = 3.0
        const val open: Double = 4.0
        const val wrong: Double = 5.0
    }

    val script = Script(
        carry = doubleArrayOf(0.8, 1.6),
        carryTo = doubleArrayOf(0.0, -100.0),
        turn = doubleArrayOf(1.95, 3.0),
        turnBy = 100.0,
        lockAt = 8.4,
    )

    data class Script(
        val carry: DoubleArray,
        val carryTo: DoubleArray,
        val turn: DoubleArray,
        val turnBy: Double,
        val lockAt: Double,
    )

    val replicaT = ReplicaT(
        keySpin = doubleArrayOf(0.0, 1.0),
        toLock = doubleArrayOf(1.0, 1.35),
        open = doubleArrayOf(1.9, 2.15),
        close = doubleArrayOf(4.2, 4.45),
        toKey = doubleArrayOf(5.4, 5.75),
    )

    data class ReplicaT(
        val keySpin: DoubleArray,
        val toLock: DoubleArray,
        val open: DoubleArray,
        val close: DoubleArray,
        val toKey: DoubleArray,
    )

    const val replicaLoopMs: Int = 6_000
    const val replicaHold: Double = -1.0

    data class KcSpring(val k: Double, val c: Double)
    data class KcCloseSpring(val k: Double, val c: Double, val kClose: Double, val cClose: Double)
    data class Tick(val duration: Double, val alpha: Double)
    data class Recoil(val amp: Double, val decay: Double, val hz: Double)
    data class Rows(val stagger: Double, val rise: Double, val span: Double)
    data class Spin(val every: Double, val turn: Double)
    data class Show(val inRate: Double, val outRate: Double, val hold: Double)

    object GM {
        const val magnet: Double = 72.0
        const val magnetPull: Double = 0.55
        const val seat: Double = 14.0
        const val flick: Double = 900.0
        const val keyHitW: Double = 50.0
        const val keyHitH: Double = 90.0
        const val ringHit: Double = 64.0
        const val grabBelow: Double = 40.0
        const val insert: Double = 0.28
        const val turnPx: Double = 110.0
        const val wrong: Double = 0.12
        const val pullOut: Double = 34.0
        const val outPull: Double = 40.0
        const val outSpeed: Double = 300.0
        val detents = doubleArrayOf(1.0 / 3.0, 2.0 / 3.0, 1.0)
        val back = KcSpring(260.0, 24.0)
        val home = KcSpring(220.0, 22.0)
        val shackle = KcCloseSpring(320.0, 14.0, 260.0, 26.0)
        const val shackleKick: Double = 4.0
        const val shackleAfter: Double = 0.3
        const val sheetDelay: Double = 0.3
        const val sheetIn: Double = 9.0
        const val sheetOut: Double = 12.0
        const val glint: Double = 0.7
        val tick = Tick(0.16, 0.35)
        val recoil = Recoil(3.0, 12.0, 7.0)
        const val appear: Double = 0.96
        val rows = Rows(0.08, 8.0, 0.5)
        val shake = SHAKE
        val spin = Spin(4.0, 1.0)
        val show = Show(10.0, 4.0, 1.5)
        const val rise: Double = 150.0
        const val shrink: Double = 0.72
    }

    const val gQaSpeed: Double = 1.0

    object G_HAPTIC {
        const val grab: Double = 1.0
        const val seat: Double = 2.0
        const val detent: Double = 3.0
        const val open: Double = 4.0
        const val wrong: Double = 5.0
    }

    const val gLoopMs: Int = 12_000
    const val resumeMs: Int = 3_500

    val gScript = GScript(
        carry = doubleArrayOf(0.8, 1.6),
        turn = doubleArrayOf(2.0, 3.0),
        turnBy = 120.0,
        lockAt = 8.4,
        resumeOpen = 5.0,
    )

    data class GScript(
        val carry: DoubleArray,
        val turn: DoubleArray,
        val turnBy: Double,
        val lockAt: Double,
        val resumeOpen: Double,
    )

    // The shipped upgrade loop is the React Native G_LOOP_MS value.
    const val LOOP: Int = gLoopMs
    const val loopMs: Int = LOOP

    // Source-name aliases keep direct RN-to-Kotlin comparisons and call-site diffs obvious.
    const val STAGE_WIDTH: Double = stageWidth
    const val REPLICA: Boolean = replica
    const val REPLICA_BG: String = replicaBg
    const val STEP: Double = step
    const val MAX_STEPS: Double = maxSteps
    const val LOOP_MS: Int = loopMs
    const val RESUME_MS: Int = resumeMs
    const val REPLICA_LOOP_MS: Int = replicaLoopMs
    const val REPLICA_HOLD: Double = replicaHold
    const val G_QA_SPEED: Double = gQaSpeed
    const val G_LOOP_MS: Int = gLoopMs
    val SCRIPT: Script = script
    val REPLICA_T: ReplicaT = replicaT
    val G_SCRIPT: GScript = gScript
}

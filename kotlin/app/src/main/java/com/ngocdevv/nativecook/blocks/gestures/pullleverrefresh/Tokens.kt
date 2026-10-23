package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal const val LOOP: Long = 12_000L

internal object PullLeverRefreshTokens {
    internal object Color {
        val Panel = 0xFF0B0A09.toInt()
        val Ink = 0xFFF4F1EC.toInt()
        val Mute = 0xFF948C83.toInt()
        val Eyebrow = 0xFFC08A5A.toInt()
        val DialInk = 0xFFE9E4DC.toInt()
        val DialMute = 0xFF8D867E.toInt()
        val Etch = 0xFFA49C93.toInt()
        val Digit = 0xFFF1ECE4.toInt()
    }

    internal object Type {
        const val Eyebrow = 12f
        const val EyebrowLine = 16f
        const val EyebrowTracking = 1.8f
        const val Title = 30f
        const val TitleLine = 36f
        const val TitleTracking = -0.6f
        const val Sub = 15f
        const val SubLine = 20f
        const val SubTracking = -0.1f
    }

    internal object Layout {
        const val Gutter = 24f
        const val Edge = 16f
        const val CopyTop = 24f
        const val CopyGap = 8f
        const val SubGap = 4f
        const val MachineGap = 16f
        const val BottomGap = 16f
        const val MaxScale = 1.3f
        const val CopyHeight = 84f
    }

    internal object Lever {
        const val Boss = 15f
        const val Hub = 8.5f
        const val Half0 = 6.2f
        const val Half1 = 4.4f
        const val Bevel = 2.2f
        const val Fillet = 10f
        const val FerruleFrom = 90f
        const val FerruleTo = 100f
        const val FerruleHalf = 6.4f
        const val HandleFrom = 99f
        const val HandleTo = 160f
        const val HandleR0 = 6.2f
        const val HandleR1 = 10.6f
        const val HandleSwell = 0.6f
        const val YokeHalf = 9f
        const val YokeTop = -4f
        const val YokeBottom = 18f
    }

    internal object Machine {
        const val GroupCapTop = 14f
        const val GroupCapBottom = 24f
        const val GroupCapRadius = 22f
        const val GroupTop = 22f
        const val GroupBottom = 116f
        const val GroupRadius = 30f
        const val GroupGroove = 46f
        const val FlangeTop = 110f
        const val FlangeBottom = 128f
        const val FlangeRadius = 37f
        const val NeckFrom = -58f
        const val NeckTo = -28f
        const val NeckY = 56f
        const val NeckRadius = 5.2f
        const val NutFrom = -49f
        const val NutTo = -39f
        const val NutRadius = 8f
        const val BowlTop = 127f
        const val BowlBottom = 147f
        const val BowlRadius = 30f
        const val StemTop = 145f
        const val StemBottom = 154f
        const val StemRadius = 7.5f
        const val SpoutX = 8.5f
        const val SpoutSpread = 3.5f
        const val SpoutTip = 166f
        const val SpoutRadius = 2.9f
        const val SpoutBore = 1.9f
        const val PortafilterFrom = 26f
        const val PortafilterTo = 62f
        const val PortafilterY = 134f
        const val PortafilterHalf = 4.4f
        const val WoodFrom = 60f
        const val WoodTo = 140f
        const val WoodR0 = 6.4f
        const val WoodR1 = 8.4f
        const val PortafilterTilt = 6f
        const val CupRim = 230f
        const val CupBase = 280f
        const val CupRadius = 37f
        const val CupBelly = 36f
        const val CupBottom = 26f
        const val CupFoot = 20f
        const val CupWall = 2.8f
        const val CupFull = 2.5f
        const val CupEarX = 44f
        const val CupEarY = 246f
        const val CupEarRx = 13f
        const val CupEarRy = 12f
        const val TrayLeft = -72f
        const val TrayRight = 100f
        const val TrayTop = 274f
        const val TrayFace = 286f
        const val TrayBottom = 310f
        const val TrayRadius = 3f
        const val TrayPitch = 4f
        const val TrayBar = 2.4f
        const val BodyLeft = -152f
        const val BodyRight = -58f
        const val BodyTop = -124f
        const val BodyBottom = 310f
        const val BodyRadius = 14f
        const val Floor = 316f
        const val GaugeX = -105f
        const val GaugeY = -44f
        const val GaugeBezel = 36.6f
        const val GaugeDial = 34f
        const val GaugeSweep = 270f
        const val GaugeMax = 12f
        const val GaugeTicks = 30
        const val TimerX = -105f
        const val TimerY = 252f
        const val TimerWidth = 58f
        const val TimerHeight = 40f
        const val TimerRadius = 9f
        const val TimerDrum = 21f
        const val TimerGap = 2f
        const val TimerRow = 10f
        const val TimerInset = 4f
        const val TimerLamp = 3f
        const val FingerAt = 136f
    }

    internal object Pump {
        const val Strokes = 3
        const val BarPerStroke = 3f
        const val Rest = -82f
        const val Bottom = -6f
        const val StrokeAt = -16f
        const val RearmAt = -58f
        const val OverDown = 6f
        const val OverUp = 5f
        const val OverSoftness = 40f
        const val GrabFrom = 0.3f
        const val HomeDamping = 26f
        const val HomeStiffness = 300f
        const val NeedleDamping = 12f
        const val NeedleStiffness = 190f
    }

    internal object Brew {
        const val Pour = 4_600L
        const val Seconds = 27f
        const val Grams = 36f
        const val Bar = 9f
        const val EndBar = 1.4f
        const val Drip = 0.05f
        const val Flowing = 0.17f
        const val Thinning = 0.76f
        const val Dry = 0.95f
    }

    internal object Timing {
        const val Zone = 360L
        const val Ready = 700L
        const val ReadyOut = 420L
        const val Settle = 900L
        const val CupOut = 380L
        const val CupIn = 460L
        const val CupTravel = 46f
        const val TimerReset = 160L
        const val Puff = 700L
        const val SubRoll = 160L
        const val Reduced = 180L
        const val Resume = 2_000L
    }

    internal object Demo {
        val Strokes = arrayOf(
            floatArrayOf(0.70f, 1.16f, 1.26f),
            floatArrayOf(1.75f, 2.21f, 2.31f),
            floatArrayOf(2.80f, 3.26f, 4.01f),
        )
        const val Swap = 10.60f
    }
}

internal data class PullLeverRefreshMachineLayout(
    val pivotX: Float,
    val pivotY: Float,
    val scale: Float,
)

internal fun pullLeverRefreshLayout(
    width: Float,
    height: Float,
    insetTop: Float,
    insetBottom: Float,
): PullLeverRefreshMachineLayout {
    val layout = PullLeverRefreshTokens.Layout
    val machine = PullLeverRefreshTokens.Machine
    val left = layout.Edge
    val right = width - layout.Edge
    val top = insetTop + layout.CopyTop + layout.CopyHeight + layout.MachineGap
    val bottom = height - insetBottom - layout.BottomGap
    val half = max((right - left) * 0.5f, 1f)
    val vertical = max(bottom - top, 1f)
    val reach = pullLeverRefreshReach()
    val silhouetteLeft = -machine.BodyLeft
    val silhouetteRight = max(machine.TrayRight, machine.WoodTo)
    val centreX = (silhouetteRight - silhouetteLeft) * 0.5f
    val centreY = (machine.Floor - reach.up) * 0.5f
    val scale = min(
        layout.MaxScale,
        min(
            min(half / max(reach.left + centreX, 1f), half / max(reach.right - centreX, 1f)),
            vertical / max(reach.up + reach.down, 1f),
        ),
    )
    val midX = (left + right) * 0.5f - 0.5f
    val rawY = (top + bottom) * 0.5f - scale * centreY
    val pivotY = rawY.coerceIn(top - scale * reach.up, bottom - scale * reach.down)
    return PullLeverRefreshMachineLayout(
        pivotX = (midX - scale * centreX).roundToInt().toFloat(),
        pivotY = pivotY.roundToInt().toFloat(),
        scale = scale,
    )
}

private data class PullLeverRefreshReach(val left: Float, val right: Float, val up: Float, val down: Float)

private fun pullLeverRefreshReach(): PullLeverRefreshReach {
    val machine = PullLeverRefreshTokens.Machine
    val pump = PullLeverRefreshTokens.Pump
    val lever = PullLeverRefreshTokens.Lever
    var minX = machine.BodyLeft
    var maxX = max(machine.TrayRight, machine.WoodTo)
    var minY = machine.BodyTop
    val angles = listOf(pump.Rest - pump.OverUp, pump.Bottom + pump.OverDown)
    angles.forEach { degrees ->
        val radians = Math.toRadians(degrees.toDouble())
        val x = cos(radians).toFloat() * lever.HandleTo
        val y = sin(radians).toFloat() * lever.HandleTo
        minX = min(minX, x - lever.HandleR1)
        maxX = max(maxX, x + lever.HandleR1)
        minY = min(minY, y - lever.HandleR1)
    }
    return PullLeverRefreshReach(-minX, maxX, -minY, machine.Floor)
}

internal fun pullLeverRefreshAngleAt(x: Float, y: Float, layout: PullLeverRefreshMachineLayout): Float =
    Math.toDegrees(atan2((y - layout.pivotY).toDouble(), (x - layout.pivotX).toDouble())).toFloat()

internal fun pullLeverRefreshLeverDistance(x: Float, y: Float, layout: PullLeverRefreshMachineLayout): Float =
    sqrt((x - layout.pivotX) * (x - layout.pivotX) + (y - layout.pivotY) * (y - layout.pivotY))

internal fun pullLeverRefreshGaugeAngle(bar: Float): Float =
    135f + bar.coerceIn(0f, PullLeverRefreshTokens.Machine.GaugeMax) /
        PullLeverRefreshTokens.Machine.GaugeMax * PullLeverRefreshTokens.Machine.GaugeSweep

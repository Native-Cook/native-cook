package com.ngocdevv.nativecook.blocks.gestures.holddragresize

internal const val LOOP = 10_000L

internal object HoldDragResizeTokens {
    object Color {
        const val Page = 0xFF06080D.toInt()
        const val Ink = 0xFFF5F6F8.toInt()
        const val Mute = 0x8FEBEEF5.toInt()
        const val Faint = 0x57EBEEF5.toInt()
        const val Focus = 0xFFFFB547.toInt()
        const val FocusInk = 0xFFFFD08A.toInt()
        const val Task = 0xFF7CC4FF.toInt()
        const val Meeting = 0xFFD9DEE8.toInt()
        const val Now = 0xFFFF5A4E.toInt()
        const val OnAccent = 0xFF1A1206.toInt()
        const val Finger = 0x24FFFFFF
        const val FingerEdge = 0x6BFFFFFF
    }

    object Tint {
        val Meeting = floatArrayOf(0.86f, 0.89f, 0.95f, 0.07f)
        val Task = floatArrayOf(0.49f, 0.77f, 1f, 0.10f)
        val Focus = floatArrayOf(1f, 0.71f, 0.28f, 0.20f)
        val Chrome = floatArrayOf(0.90f, 0.92f, 0.97f, 0.06f)
        val Selected = floatArrayOf(1f, 1f, 1f, 0.16f)
    }

    object Type {
        const val Title = 30f
        const val Date = 15f
        const val ChipLabel = 11f
        const val Total = 17f
        const val TotalUnit = 13f
        const val Weekday = 11f
        const val WeekDate = 17f
        const val Hour = 11f
        const val Pill = 11f
        const val Delta = 12f
        const val Event = 14f
        const val EventSub = 11f
        const val EventNote = 12f
        const val Bar = 15f
    }

    object Layout {
        const val Edge = 16.0
        const val Line1 = 38.0
        const val Line2 = 60.0
        const val ChipY = 10.0
        const val ChipH = 48.0
        const val ChipW = 116.0
        const val WeekY = 76.0
        const val WeekH = 60.0
        const val WeekRadius = 30.0
        const val WeekCell = 44.0
        const val GridTop = 160.0
        const val LabelRight = 50.0
        const val RailX = 63.0
        const val Capsule = 6.0
        const val CardX = 76.0
        const val CardInset = 2.0
        const val Radius = 12.0
        const val PadX = 12.0
        const val PadY = 8.0
        const val Check = 13.0
        const val PillH = 22.0
        const val PillPad = 7.0
        const val GripW = 28.0
        const val GripH = 4.0
        const val GripInset = 6.0
        const val BarH = 52.0
        const val BarGap = 8.0
        const val BarPlus = 52.0
        const val Finger = 44.0
    }

    object Day {
        const val Start = 9 * 60
        const val End = 18 * 60
        const val Now = 9 * 60 + 41
        const val Quarter = 15
    }

    object Resize {
        const val HoldMs = 300L
        const val Quarter = 15.0
        const val MinLength = 15.0
        const val RippleStep = 70.0
        const val Rubber = 0.35
        const val RubberMax = 15.0
        const val SquashBulge = 14.0
        const val BulgeMax = 3.0
    }

    object Elevation {
        const val Rest = 1.0
        const val Pressed = 0.35
        const val Held = 12.0
    }

    object Lift {
        const val Scale = 0.012
        const val Rise = 2.0
    }

    data class Spring(val durationMs: Double, val dampingRatio: Double)

    object Springs {
        val Detent = Spring(200.0, 0.8)
        val Ripple = Spring(400.0, 0.8)
        val Lift = Spring(400.0, 1.0)
        val Drop = Spring(300.0, 1.0)
        val Roll = Spring(200.0, 1.0)
        val Reduced = Spring(200.0, 1.0)
    }

    object Timing {
        const val LockIn = 0.20
        const val LockOut = 0.16
        const val FadeIn = 0.20
        const val FadeOut = 0.16
        const val WasOut = 0.0
        const val FingerIn = 0.15
        const val FingerOut = 0.12
        const val PressOut = 0.15
    }

    const val ResumeMs = 2_000L

    object Demo {
        const val GrowDown = 0.5
        const val GrowLifted = 0.8
        const val GrowGrab = 645.0
        val GrowPath = arrayOf(
            doubleArrayOf(0.9, 0.0),
            doubleArrayOf(2.1, 60.0),
            doubleArrayOf(2.5, 92.0),
            doubleArrayOf(2.75, 92.0),
            doubleArrayOf(3.0, 62.0),
        )
        const val GrowUp = 3.2
        const val ShrinkDown = 5.0
        const val ShrinkLifted = 5.3
        const val ShrinkGrab = 705.0
        val ShrinkPath = arrayOf(doubleArrayOf(5.4, 0.0), doubleArrayOf(6.8, -60.0))
        const val ShrinkUp = 7.1
        const val FingerX = 0.62
        const val Tidy = 0.8
    }
}

internal data class HoldDragResizeLayout(
    val width: Double,
    val height: Double,
    val top: Double,
    val bottom: Double,
) {
    val gridTop = top + HoldDragResizeTokens.Layout.GridTop
    val barTop = height - maxOf(bottom, HoldDragResizeTokens.Layout.Edge) -
        HoldDragResizeTokens.Layout.BarGap - HoldDragResizeTokens.Layout.BarH
    val ppm = minOf(1.2, (barTop - 16.0 - gridTop) / (HoldDragResizeTokens.Day.End - HoldDragResizeTokens.Day.Start))
    val cardX = HoldDragResizeTokens.Layout.CardX
    val cardW = width - HoldDragResizeTokens.Layout.CardX - HoldDragResizeTokens.Layout.Edge
    val right = width - HoldDragResizeTokens.Layout.Edge

    fun yOf(minutes: Double): Double = gridTop + (minutes - HoldDragResizeTokens.Day.Start) * ppm
}

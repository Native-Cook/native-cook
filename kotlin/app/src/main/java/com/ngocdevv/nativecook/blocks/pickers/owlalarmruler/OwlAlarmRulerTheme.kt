package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

/**
 * Owl Alarm Ruler — every colour, size and position of the screen, in pt.
 *
 * The values are kept as doubles to mirror the React Native reference. Colours are packed
 * Android ARGB ints so callers can pass them directly to Compose/Canvas adapters.
 */
object OwlAlarmRulerTheme {
    /** The pin's top safe area (pt). */
    const val PIN_INSET: Double = 62.0

    /** Spacing tokens measured from the pin. */
    object space {
        const val screenInset: Double = 26.0
        const val backInset: Double = 24.0
        const val headerTop: Double = 10.0
        const val ctaPadStart: Double = 19.0
        const val ctaPadEnd: Double = 22.3
        const val ctaBottom: Double = 20.0
        const val hit: Double = 44.0
    }

    /** One family of fully rounded ends plus the button's corner. */
    object radius {
        const val cta: Double = 18.0
        const val pill: Double = 999.0
    }

    object colors {
        val page: Int = 0xFFEDEDED.toInt()
        val band: Int = 0xFFE3E3E3.toInt()
        val shadow: Int = 0xFFC4C4C5.toInt()
        val backFill: Int = 0xFFDADADA.toInt()
        val ink: Int = 0xFF000000.toInt()
        val track: Int = 0xFFCCCCCC.toInt()
        val caption: Int = 0xFF5F5F5F.toInt()
        val cta: Int = 0xFFBC4A20.toInt()
        val white: Int = 0xFFFFFFFF.toInt()
        val needle: Int = 0xFFCC3F20.toInt()
        val tickDark: Int = 0xFF2C2926.toInt()
        val tickLight: Int = 0xFFC2C1C0.toInt()
        val lid: Int = 0xFFF9F8F6.toInt()
        val lash: Int = 0xFF3A3A3A.toInt()
    }

    object copy {
        const val line1: String = "You chose your goal."
        const val line2: String = "Now set the alarm."
        const val caption: String = "We’ll begin with one alarm. No backups."
        const val cta: String = "Set alarm for"
        const val a11yRuler: String = "Alarm time"
        const val a11yRulerHint: String = "Swipe up or down to change by five minutes."
        const val a11yBack: String = "Back"
    }

    /** SF Pro / SF Pro Rounded baseline under the top of a Text with no lineHeight. */
    const val ASCENT: Double = 0.953

    data class TextStyleSpec(
        val size: Double,
        val weight: String,
        val color: Int,
        val ls: Double,
    )

    object type {
        val headline = TextStyleSpec(30.0, "600", colors.ink, -1.25)
        val time = TextStyleSpec(78.0, "800", colors.ink, 0.0)
        val meridiem = TextStyleSpec(25.0, "600", colors.ink, 0.0)
        val caption = TextStyleSpec(14.2, "400", colors.caption, 0.0)
        val cta = TextStyleSpec(20.0, "500", colors.white, 0.36)
    }

    data class Diagonal(val y0: Double, val slope: Double)
    data class SundialPivot(val dx: Double, val y: Double)
    data class BackArrow(
        val tipX: Double,
        val armX: Double,
        val armDy: Double,
        val endX: Double,
        val stroke: Double,
    )
    data class Back(val d: Double, val arrow: BackArrow)
    data class Track(val w: Double, val h: Double, val fill: Double)
    data class Mascot(val w: Double, val h: Double, val dx: Double, val y: Double, val pivotY: Double)
    data class Eye(val cx: Double, val cy: Double, val r: Double)
    data class PupilRest(val x: Double, val y: Double)
    data class Pupil(val d: Double, val rest: List<PupilRest>)
    data class GroundShadow(val dx: Double, val cy: Double, val rx: Double, val ry: Double)
    data class Baseline(val base: Double)
    data class Time(val base: Double, val dx: Double, val colonGap: Double, val tight: Double, val colonRise: Double)
    data class Meridiem(val base: Double, val dx: Double, val w: Double)
    data class Needle(val w: Double, val h: Double, val cy: Double)
    data class RulerBand(val top: Double, val h: Double)
    data class RulerTouch(val top: Double, val h: Double)
    data class Ruler(
        val spacing: Double,
        val base: Double,
        val tickW: Double,
        val fade: Double,
        val needle: Needle,
        val band: RulerBand,
        val touch: RulerTouch,
    )
    data class Cta(val h: Double, val base: Double)
    data class CtaArrow(val shaft: Double, val stroke: Double, val headW: Double, val headH: Double, val headR: Double)

    private fun p(px: Double): Double = px / 3.0

    private fun y(px: Double): Double = px / 3.0 - PIN_INSET

    object layout {
        val diagonal = Diagonal(y(584.0), 400.0 / 1206.0)
        val sundialPivot = SundialPivot(0.0, y(584.0 + 603.0 * (400.0 / 1206.0)))

        val back = Back(
            d = p(126.0),
            arrow = BackArrow(
                tipX = p(110.5 - 72.0),
                armX = p(129.5 - 72.0),
                armDy = p(19.5),
                endX = p(158.9 - 72.0),
                stroke = p(6.3),
            ),
        )
        val track = Track(w = 180.0, h = p(33.0), fill = p(196.0))

        val mascot = Mascot(
            w = p(660.0),
            h = p(576.0),
            dx = p(275.0) + p(660.0) / 2.0 - 201.0,
            y = y(414.0),
            pivotY = 563.0 / 576.0,
        )
        val eyes = listOf(
            Eye(cx = p(224.0), cy = p(246.0), r = p(70.5)),
            Eye(cx = p(454.0), cy = p(248.0), r = p(72.5)),
        )
        val pupil = Pupil(
            d = p(60.0),
            rest = listOf(PupilRest(x = p(22.0), y = p(24.0)), PupilRest(x = p(-22.0), y = p(24.0))),
        )
        val lash: Double = 12.0 / 3.0
        val groundShadow = GroundShadow(dx = p(588.0) - 201.0, cy = y(947.0), rx = p(307.0), ry = p(50.0))

        val headline = listOf(Baseline(base = y(1155.0)), Baseline(base = y(1253.0)))
        val time = Time(base = y(1564.0), dx = p(17.5), colonGap = 1.3, tight = -5.4, colonRise = p(21.0))
        val meridiem = Meridiem(base = y(1692.0), dx = -0.5, w = 60.0)

        val ruler = Ruler(
            spacing = p(34.7),
            base = y(1849.0),
            tickW = 3.0,
            fade = 0.6,
            needle = Needle(w = 7.0, h = p(93.0), cy = y(1816.0)),
            band = RulerBand(top = y(1730.0), h = p(170.0)),
            touch = RulerTouch(top = y(1700.0), h = p(230.0)),
        )

        val caption = Baseline(base = y(2046.0))
        val cta = Cta(h = p(196.0), base = p(2384.0 - 2265.0))
        val ctaArrow = CtaArrow(
            shaft = p(1036.0 - 997.5),
            stroke = p(7.5),
            headW = p(30.5),
            headH = p(37.5),
            headR = 1.0,
        )
    }

    /** The time the pin shows: 1:05 PM, in minutes after midnight. */
    const val REST_MINUTES: Double = 785.0
    const val STEP_MINUTES: Double = 5.0
    const val TICKS_PER_DAY: Double = 288.0
}

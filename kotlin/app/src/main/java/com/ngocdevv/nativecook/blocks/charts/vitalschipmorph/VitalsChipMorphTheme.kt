package com.ngocdevv.nativecook.blocks.charts.vitalschipmorph

import androidx.compose.ui.graphics.Color

/**
 * Vitals Chip Morph — every colour, size, and position of the three screens.
 *
 * The values are kept as doubles to mirror the React Native reference. Positions are ref px from
 * the 1206 × 2622 pin; the renderer scales them by screen width / [REF_W].
 */
internal object VitalsChipMorphTheme {
    const val REF_W: Double = 1206.0
    /** The pin's 62 pt top safe area (186 ref px). */
    const val REF_INSET: Double = 186.0
    /** Font scale reference: 1206 ref px = 402 pt. */
    const val REF_PT: Double = 402.0

    object colors {
        val page: Color = Color(0xFFF2F1F7)
        val chip: Color = Color(0xFFF4F3F8)
        val chipBorder: Color = Color(0xFFD2D1D8)
        val chipActive: Color = Color(0xFFFFFFFF)
        val ink: Color = Color(0xFF000000)
        val muted: Color = Color(0xFF88878D)
        val faint: Color = Color(0xFFB9B8C0)
        val hairline: Color = Color(0xFFD9D8DF)

        // Readiness.
        val track: Color = Color(0xFFDFDFE9)
        val segment: Color = Color(0xFFB2D3F2)
        val blue: Color = Color(0xFF1592EA)
        val blueGlow: Color = Color(0xFFD4E9FB)
        val link: Color = Color(0xFF1E88E5)
        val check: Color = Color(0xFF1E8DE6)
        val iconGrey: Color = Color(0xFF6E6E73)
        val band: Color = Color(0xFFCFE3F8)
        val bandLine: Color = Color(0xFFD7D7DD)

        // Fitness rings: start → end of each progress gradient and the ring's own track.
        val moveStart: Color = Color(0xFFFA0232)
        val moveEnd: Color = Color(0xFFFD0172)
        val moveTrack: Color = Color(0xFFF6C0CE)
        val exerciseStart: Color = Color(0xFF05DD04)
        val exerciseEnd: Color = Color(0xFF78E402)
        val exerciseTrack: Color = Color(0xFFC4EDC4)
        val standStart: Color = Color(0xFF02C1DB)
        val standEnd: Color = Color(0xFF02D4B9)
        val standTrack: Color = Color(0xFFB5E5F1)
        val glyph: Color = Color(0xFF14060B)
        val movePink: Color = Color(0xFFE4365F)
        val standCyan: Color = Color(0xFF14B4CF)
        val exerciseText: Color = Color(0xFF4BD12D)

        // Heart.
        val heart: Color = Color(0xFFF42E59)
        val heartFlat: Color = Color(0xFFFB4F78)
        val heartHalo: Color = Color(0xFFF7C3CF)
        val heartDark: Color = Color(0xFFE52650)
        val chartPink: Color = Color(0xFFF09CAF)
        val bpmUnit: Color = Color(0xFF55343D)
        val supportValue: Color = Color(0xFF55545A)

        // Chip icons.
        val sleepTeal: Color = Color(0xFF1FCFB0)
        val sleepCoral: Color = Color(0xFFF47A5C)
        val sleepBlue: Color = Color(0xFF3D63F0)
        val vitalsPink: Color = Color(0xFFDF61D2)
        val vitalsBlue: Color = Color(0xFF378DDD)
        val summaryHeartTop: Color = Color(0xFFFF5C95)
        val summaryHeartBottom: Color = Color(0xFFF0246A)

        // Tab bar.
        val tabGlass: Color = Color(red = 248f / 255f, green = 248f / 255f, blue = 252f / 255f, alpha = 0.78f)
        /** The Liquid Glass tab bar's tint in the accepted RN version. */
        val tabGlassTint: Color = Color(red = 250f / 255f, green = 250f / 255f, blue = 253f / 255f, alpha = 0.6f)
        val tabRim: Color = Color(red = 255f / 255f, green = 255f / 255f, blue = 255f / 255f, alpha = 0.9f)
        val tabActive: Color = Color(red = 118f / 255f, green = 118f / 255f, blue = 128f / 255f, alpha = 0.14f)
        val tabBlue: Color = Color(0xFF1F8BF0)
    }

    /** Text styles: size in pt at 402 pt, weight, colour, and letter spacing in pt. */
    data class TextSpec(
        val size: Double,
        val weight: String,
        val color: Color,
        val ls: Double,
        val rounded: Boolean = false,
    )

    object type {
        val chipTitle = TextSpec(15.2, "400", colors.muted, 0.0)
        val chipValue = TextSpec(15.0, "600", colors.ink, 0.0)
        val readiness = TextSpec(77.0, "600", colors.blue, 0.0, rounded = true)
        val bpm = TextSpec(76.0, "600", colors.ink, -2.2)
        val bpmUnit = TextSpec(16.0, "500", colors.bpmUnit, 0.0)
        val heading = TextSpec(30.8, "700", colors.ink, 0.0)
        val body = TextSpec(21.8, "400", colors.muted, 0.0)
        val section = TextSpec(21.1, "700", colors.ink, 0.0)
        val link = TextSpec(15.2, "600", colors.link, 0.0)
        val value = TextSpec(18.4, "700", colors.ink, 0.0)
        val stat = TextSpec(15.5, "600", colors.movePink, 0.0)
        val metricLink = TextSpec(14.5, "600", colors.heartDark, 0.0)
        val metricValue = TextSpec(20.5, "600", colors.ink, -0.2)
        val metricSub = TextSpec(14.0, "400", colors.faint, 0.0)
        val axis = TextSpec(11.0, "400", colors.faint, 0.0)
        val chartTitle = TextSpec(15.0, "600", colors.ink, 0.0)
        val tab = TextSpec(11.2, "500", colors.ink, 0.0)
    }

    object space {
        /** Left edge of every text column (32 pt). */
        const val screenInset: Double = 96.0
        /** Gap between two chips (7.5 pt, measured; not on the grid). */
        const val chipGap: Double = 22.5
        /** Chip padding before the icon centre (28 pt) and before the label (54.7 pt). */
        const val chipIconCentre: Double = 84.0
        const val chipLabel: Double = 161.0
        /** Cap tops of a chip's title and value from the chip's top. */
        const val chipTitleCap: Double = 39.0
        const val chipValueCap: Double = 97.0
        /** A link row: the check's left edge to the label. */
        const val linkLabel: Double = 50.0
        /** Body line height (26 pt) and heading line height (36 pt). */
        const val bodyLine: Double = 78.0
        const val headingLine: Double = 108.0
        /** A metric block: label cap top → value cap top → sub-label cap top. */
        const val metricValue: Double = 69.0
        const val metricSub: Double = 146.0
        /** Distance a page slides while it hands over to the next one (24 pt). */
        const val pageSlide: Double = 72.0
    }

    object radius {
        const val chip: Double = 66.0
        const val tabBar: Double = 93.0
        const val tabPill: Double = 81.0
    }

    /** A chip in the metric row, in row coordinates before the row's scroll offset. */
    data class ChipSpec(
        val key: String,
        val title: String,
        val value: String,
        val x: Double,
        val w: Double,
    )

    val CHIP_W = doubleArrayOf(175.0, 315.5, 420.0, 446.0, 355.0, 339.0)
    val CHIP_X = DoubleArray(CHIP_W.size).also { values ->
        var x = 51.0
        CHIP_W.forEachIndexed { index, width ->
            values[index] = x
            x += width + space.chipGap
        }
    }

    val chips: List<ChipSpec> = listOf(
        ChipSpec("summary", "Summary", "", CHIP_X[0], CHIP_W[0]),
        ChipSpec("sleep", "Sleep", "High", CHIP_X[1], CHIP_W[1]),
        ChipSpec("readiness", "Readiness", "Ready", CHIP_X[2], CHIP_W[2]),
        ChipSpec("fitness", "Fitness", "5,253 steps", CHIP_X[3], CHIP_W[3]),
        ChipSpec("heart", "Heart", "62 BPM", CHIP_X[4], CHIP_W[4]),
        ChipSpec("vitals", "Vitals", "Typical", CHIP_X[5], CHIP_W[5]),
    )

    /** Chip index of each screen (Readiness, Fitness, Heart). */
    val SCREEN_CHIP: IntArray = intArrayOf(2, 3, 4)

    /** The screen each chip opens: Summary and Sleep show Readiness, Vitals shows Heart. */
    val CHIP_SCREEN: IntArray = intArrayOf(0, 0, 0, 1, 2, 2)
    val SCREEN_NAMES: Array<String> = arrayOf("Readiness", "Fitness", "Heart")

    data class Gauge(
        val cx: Double,
        val cy: Double,
        val r: Double,
        val stroke: Double,
        val from: Double,
        val to: Double,
    )

    data class Segment(val t0: Double, val t1: Double, val corner: Double)
    data class Dot(val lag: Double, val d: Double, val glow: Double)
    data class Seven(val cx: Double, val capTop: Double)
    data class Rings(
        val cx: Double,
        val cy: Double,
        val stroke: Double,
        val r: DoubleArray,
        val progress: DoubleArray,
    )
    data class Heart(val x: Double, val y: Double, val w: Double, val h: Double, val cx: Double, val cy: Double)
    data class Bpm(val capTop: Double, val unitCapTop: Double)
    data class CheckGroup(val icon: Double, val check: Double)
    data class Checks(
        val cy: Double,
        val groups: Array<CheckGroup>,
        val dividers: DoubleArray,
        val checkD: Double,
        val iconD: Double,
        val dividerH: Double,
    )
    data class MiniChart(
        val x: Double,
        val w: Double,
        val lineTop: Double,
        val bandTop: Double,
        val bandH: Double,
        val lineBottom: Double,
        val iconsCy: Double,
        val dots: Array<DoubleArray>,
        val dotD: Double,
    )
    data class SleepRing(val cx: Double, val cy: Double, val r: Double, val stroke: Double)
    data class FitChart(
        val x: Double,
        val w: Double,
        val rowsCy: DoubleArray,
        val labelsTop: Double,
        val base: Double,
        val barX: Double,
        val barPitch: Double,
        val barW: Double,
        val dotX: Double,
        val dotPitch: Double,
        val dotR: Double,
        val labelPitch: Double,
        val gridW: Double,
    )
    data class FitStats(val x: Double, val capTops: DoubleArray)
    data class HeartChart(val titleTop: Double, val axisCapTop: Double, val axisRightX: Double)
    data class TabPill(val x: Double, val y: Double, val w: Double, val h: Double)
    data class TabBar(
        val x: Double,
        val y: Double,
        val w: Double,
        val h: Double,
        val pill: TabPill,
        val iconCy: Double,
        val labelCapTop: Double,
        val centres: DoubleArray,
    )

    object layout {
        const val chipTop: Double = 186.0
        const val chipH: Double = 174.0
        /** Row scroll offset per screen; the active chip sits where the pin shows it. */
        val rowOffset: DoubleArray = doubleArrayOf(
            0.0,
            377.0 - CHIP_X[3],
            427.0 - CHIP_X[4],
        )

        /** The hero canvas spans the full width from y 400 to 1420 ref px. */
        const val heroTop: Double = 400.0
        const val heroH: Double = 1020.0
        /** The hero's touch area stops above the headline. */
        const val heroTouchH: Double = 800.0

        // Readiness gauge (screen coordinates).
        val gauge = Gauge(cx = 603.0, cy = 1072.0, r = 455.0, stroke = 130.0, from = 167.5, to = 12.5)
        /** The light segment's butt ends and the dot's lag behind t1. */
        val segment = Segment(t0 = 0.4510, t1 = 0.7303, corner = 40.0)
        val dot = Dot(lag = 0.0335, d = 76.0, glow = 150.0)
        val seven = Seven(cx = 601.5, capTop = 861.0)

        // Fitness rings (screen coordinates); progress starts at 12 o'clock and runs clockwise.
        val rings = Rings(
            cx = 608.0,
            cy = 851.5,
            stroke = 88.0,
            r = doubleArrayOf(359.0, 262.0, 165.0),
            progress = doubleArrayOf(0.75, 0.633, 0.333),
        )

        // Heart: the rendered artwork's canvas, registered to the pin.
        val heart = Heart(x = 213.0, y = 488.0, w = 780.0, h = 750.0, cx = 601.5, cy = 863.0)
        val bpm = Bpm(capTop = 718.0, unitCapTop = 963.0)

        // Readiness page.
        val checks = Checks(
            cy = 1206.0,
            groups = arrayOf(
                CheckGroup(icon = 280.0, check = 370.0),
                CheckGroup(icon = 562.0, check = 642.0),
                CheckGroup(icon = 836.0, check = 915.0),
            ),
            dividers = doubleArrayOf(465.0, 742.0),
            checkD = 50.0,
            iconD = 56.0,
            dividerH = 60.0,
        )
        const val pageHeadingCapTop: Double = 1366.0
        const val readyBodyTop: Double = 1480.0
        const val raisingCapTop: Double = 1891.0
        const val vitalsLinkCapTop: Double = 2021.0
        const val vitalsChevronX: Double = 520.0
        const val sleepChevronX: Double = 412.0
        const val typicalCapTop: Double = 2085.0
        val miniChart = MiniChart(
            x = 668.0,
            w = 444.0,
            lineTop = 2006.0,
            bandTop = 2023.0,
            bandH = 106.0,
            lineBottom = 2137.0,
            iconsCy = 2186.0,
            dots = arrayOf(
                doubleArrayOf(720.0, 2099.0),
                doubleArrayOf(802.0, 2059.0),
                doubleArrayOf(887.0, 2097.0),
                doubleArrayOf(972.0, 2087.0),
                doubleArrayOf(1057.0, 2074.0),
            ),
            dotD = 26.0,
        )
        const val sleepLinkCapTop: Double = 2284.0
        const val highCapTop: Double = 2342.0
        val sleepRing = SleepRing(cx = 1003.0, cy = 2372.0, r = 80.0, stroke = 30.0)

        // Fitness page.
        const val fitBodyTop: Double = 1590.0
        /** Fitness mini chart (ref px, from the chart's left edge). */
        val fitChart = FitChart(
            x = 96.0,
            w = 450.0,
            rowsCy = doubleArrayOf(1852.0, 1940.0, 2006.0),
            labelsTop = 2052.0,
            base = 28.0,
            barX = 130.0,
            barPitch = 10.0,
            barW = 4.2,
            dotX = 146.0,
            dotPitch = 20.0,
            dotR = 7.0,
            labelPitch = 135.0,
            gridW = 0.88,
        )
        val fitStats = FitStats(x = 620.0, capTops = doubleArrayOf(1852.0, 1919.0, 1987.0))
        const val fitMetricsCapTop: Double = 2160.0

        // Heart page (copied by value from pulse-heart-card).
        const val heartBodyTop: Double = 1578.0
        val heartChart = HeartChart(titleTop = 1907.0, axisCapTop = 2169.0, axisRightX = 1040.0)
        /** Right-hand metric column on the Heart screen (pin 05). */
        const val heartRightColumn: Double = 636.0
        const val heartMetricsTop: Double = 2247.0

        /** Content under the tab bar's glass is soft (Gaussian σ in ref px). */
        const val underGlassBlur: Double = 7.0
        val tabBar = TabBar(
            x = 169.0,
            y = 2369.0,
            w = 867.0,
            h = 189.0,
            pill = TabPill(x = 177.0, y = 2386.0, w = 295.0, h = 164.0),
            iconCy = 2441.0,
            labelCapTop = 2499.0,
            centres = doubleArrayOf(326.0, 600.0, 876.0),
        )
    }

    /** Liquid material values, in the same reference-pixel coordinate space as the RN shader. */
    object liquidLook {
        val skyTop: IntArray = intArrayOf(255, 255, 255)
        val skyLow: IntArray = intArrayOf(216, 220, 232)
        val horizon: IntArray = intArrayOf(118, 122, 140)
        val floorNear: IntArray = intArrayOf(198, 201, 214)
        val floorFar: IntArray = intArrayOf(240, 241, 246)
        const val softSigma: Double = 15.0
        val gooCentre: DoubleArray = doubleArrayOf(605.0, 1195.0)
        const val gooRadius: Double = 260.0
        const val sampleRadius: Double = 64.0
        const val slopeStep: Double = 3.0
        const val slope: Double = 70.0
        const val refractPx: Double = 12.0
        const val wobblePx: Double = 7.0
        const val ripSpeed: Double = 820.0
        const val ripWidth: Double = 70.0
        const val ripDecay: Double = 0.5
        const val ripBirth: Double = 60.0
        const val ripK: Double = 0.0982
        const val ripPx: Double = 3.5
        const val ripSlope: Double = 0.95
        const val ripLight: Double = 1.0
    }

    /** The travelling chip selection's glass-lens values. */
    object lens {
        const val liftScale: Double = 1.06
        val tint: Color = Color.White.copy(alpha = 0.28f)
        const val onFrom: Double = 0.02
        const val offAt: Double = 0.97
        const val fade: Double = 0.22
    }
}

package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal const val GlassPeakDesignWidth = 402f
internal const val GlassPeakDesignHeight = 874f
internal const val GlassPeakLoop = 13f
internal const val GlassPeakSamples = 96
internal const val GlassPeakSplashCount = 8
internal const val GlassPeakPeakNode = 2

internal object GlassPeakCard {
    const val x = 28f
    const val y = 176f
    const val width = 346f
    const val height = 480f
}

internal object GlassPeakPlot {
    const val left = 28f
    const val right = 374f
    const val top = 294f
    const val zero = 476f
}

internal data class GlassPeakPeriod(
    val label: String,
    val sub: String,
    val value: Float,
    val level: Float,
    val knots: Array<FloatArray>,
    val nodes: FloatArray,
)

internal val GlassPeakPeriods = arrayOf(
    GlassPeakPeriod(
        "Monthly",
        "increase vs last month",
        300f,
        1f,
        arrayOf(
            floatArrayOf(0f, .074f), floatArrayOf(.103f, .153f), floatArrayOf(.21f, .09f),
            floatArrayOf(.318f, .049f), floatArrayOf(.38f, .14f), floatArrayOf(.418f, .47f),
            floatArrayOf(.45f, .87f), floatArrayOf(.488f, .985f), floatArrayOf(.526f, 1f),
            floatArrayOf(.571f, .73f), floatArrayOf(.626f, .4f), floatArrayOf(.692f, .206f),
            floatArrayOf(.733f, .153f), floatArrayOf(.828f, .115f), floatArrayOf(1f, .13f),
        ),
        floatArrayOf(.103f, .318f, .526f, .733f),
    ),
    GlassPeakPeriod(
        "Weekly",
        "increase vs last week",
        48f,
        .42f,
        arrayOf(
            floatArrayOf(0f, .13f), floatArrayOf(.14f, .23f), floatArrayOf(.3f, .1f),
            floatArrayOf(.44f, .2f), floatArrayOf(.56f, .4f), floatArrayOf(.62f, .455f),
            floatArrayOf(.68f, .47f), floatArrayOf(.74f, .44f), floatArrayOf(.8f, .33f),
            floatArrayOf(.88f, .2f), floatArrayOf(1f, .27f),
        ),
        floatArrayOf(.14f, .3f, .68f, .88f),
    ),
    GlassPeakPeriod(
        "Yearly",
        "increase vs last year",
        124f,
        .68f,
        arrayOf(
            floatArrayOf(0f, .05f), floatArrayOf(.16f, .1f), floatArrayOf(.28f, .07f),
            floatArrayOf(.42f, .22f), floatArrayOf(.54f, .3f), floatArrayOf(.64f, .34f),
            floatArrayOf(.72f, .56f), floatArrayOf(.77f, .69f), floatArrayOf(.8f, .71f),
            floatArrayOf(.84f, .67f), floatArrayOf(.94f, .46f), floatArrayOf(1f, .5f),
        ),
        floatArrayOf(.16f, .42f, .8f, .94f),
    ),
)

internal fun glassPeakBuildYs(knots: Array<FloatArray>): FloatArray {
    val count = knots.size
    val slopes = FloatArray(count - 1)
    val tangents = FloatArray(count)
    for (index in 0 until count - 1) {
        slopes[index] = (knots[index + 1][1] - knots[index][1]) / (knots[index + 1][0] - knots[index][0])
    }
    tangents[0] = slopes[0]
    for (index in 1 until count - 1) {
        tangents[index] = if (slopes[index - 1] * slopes[index] <= 0f) 0f else (slopes[index - 1] + slopes[index]) / 2f
    }
    tangents[count - 1] = slopes[count - 2]
    for (index in 0 until count - 1) {
        if (slopes[index] == 0f) {
            tangents[index] = 0f
            tangents[index + 1] = 0f
        } else {
            val a = tangents[index] / slopes[index]
            val b = tangents[index + 1] / slopes[index]
            val sum = a * a + b * b
            if (sum > 9f) {
                val scale = 3f / sqrt(sum)
                tangents[index] = scale * a * slopes[index]
                tangents[index + 1] = scale * b * slopes[index]
            }
        }
    }
    val values = FloatArray(GlassPeakSamples)
    var knot = 0
    for (sample in 0 until GlassPeakSamples) {
        val u = sample.toFloat() / (GlassPeakSamples - 1)
        while (knot < count - 2 && u > knots[knot + 1][0]) knot += 1
        val width = knots[knot + 1][0] - knots[knot][0]
        val t = (u - knots[knot][0]) / width
        val t2 = t * t
        val t3 = t2 * t
        values[sample] =
            (2f * t3 - 3f * t2 + 1f) * knots[knot][1] +
                (t3 - 2f * t2 + t) * width * tangents[knot] +
                (-2f * t3 + 3f * t2) * knots[knot + 1][1] +
                (t3 - t2) * width * tangents[knot + 1]
    }
    repeat(4) {
        var previous = values[0]
        for (sample in 1 until GlassPeakSamples - 1) {
            val here = values[sample]
            values[sample] = (previous + 2f * here + values[sample + 1]) / 4f
            previous = here
        }
    }
    return values
}

internal val GlassPeakCurves = GlassPeakPeriods.map { glassPeakBuildYs(it.knots) }.toTypedArray()

internal fun glassPeakHeightAt(values: FloatArray, u: Float): Float {
    val position = u.coerceIn(0f, 1f) * (GlassPeakSamples - 1)
    val index = min(GlassPeakSamples - 2, floor(position).toInt())
    return values[index] + (values[index + 1] - values[index]) * (position - index)
}

internal fun glassPeakX(u: Float) = GlassPeakPlot.left + u * (GlassPeakPlot.right - GlassPeakPlot.left)

internal fun glassPeakY(value: Float) = GlassPeakPlot.zero - value * (GlassPeakPlot.zero - GlassPeakPlot.top)

internal fun glassPeakDropBottom(x: Float, peak: Boolean): Float {
    val offset = kotlin.math.abs(x - 201f)
    return when {
        offset < 64f -> 492f
        peak -> 516f
        offset < 100f -> 516f
        else -> 556f
    }
}

internal fun glassPeakPoolFront(level: Float, hump: Float, humpX: Float, slope: Float, x: Float): Float {
    val dx = x - humpX
    return GlassPeakCard.y + GlassPeakCard.height - (10f + 140f * level) -
        hump * 84f * kotlin.math.exp(-(dx * dx) / (2f * 58f.pow(2))) - slope * (x - 201f) * .4f
}

internal data class GlassPeakFrame(
    var time: Float = 0f,
    var presented: Float = 1f,
    var ys: FloatArray = GlassPeakCurves[0].copyOf(),
    var lit: Float = 1f,
    var tip: Float = 0f,
    var tube: Float = 1f,
    var orbX: Float = 0f,
    var orbY: Float = 0f,
    var orbRadius: Float = 11f,
    var orbGain: Float = 1f,
    var nodeX: FloatArray = FloatArray(4),
    var nodeY: FloatArray = FloatArray(4),
    var nodeLit: FloatArray = FloatArray(4) { 1f },
    var nodeScale: FloatArray = FloatArray(4) { 1f },
    var drops: FloatArray = FloatArray(4) { 1f },
    var nodeBottom: FloatArray = FloatArray(4),
    var poolLevel: Float = 1f,
    var poolHumpX: Float = 0f,
    var poolHump: Float = .35f,
    var poolSlope: Float = 0f,
    var poolGain: Float = 1f,
    var poolStir: Float = 0f,
    var splashX: FloatArray = FloatArray(GlassPeakSplashCount),
    var splashY: FloatArray = FloatArray(GlassPeakSplashCount),
    var splashRadius: FloatArray = FloatArray(GlassPeakSplashCount),
    var beam: Float = .22f,
    var beamReach: Float = 1f,
    var ringX: Float = 0f,
    var ringY: Float = 0f,
    var ringRadius: Float = 0f,
    var ringAlpha: Float = 0f,
    var flash: Float = 0f,
    var rimGlint: Float = 0f,
    var tiltX: Float = 0f,
    var tiltY: Float = 0f,
    var cardScale: Float = 1f,
    var value: Float = 300f,
    var numberAlpha: Float = 1f,
    var numberScale: Float = 1f,
    var numberGlow: Float = 0f,
    var period: Int = 0,
    var periodPrevious: Int = 0,
    var periodMix: Float = 1f,
    var status: Int = 0,
    var statusPrevious: Int = 0,
    var statusMix: Float = 1f,
    var spin: Float = 0f,
    var pillPress: Float = 0f,
    var refreshPress: Float = 0f,
    var chevron: Float = 0f,
    var touchX: Float = 201f,
    var touchY: Float = 416f,
    var touchAlpha: Float = 0f,
)

internal fun glassPeakRestFrame(periodIndex: Int): GlassPeakFrame {
    val period = GlassPeakPeriods[periodIndex]
    val frame = GlassPeakFrame(
        ys = GlassPeakCurves[periodIndex].copyOf(),
        poolLevel = period.level,
        value = period.value,
        period = periodIndex,
        periodPrevious = periodIndex,
    )
    for (index in 0 until 4) {
        frame.nodeX[index] = glassPeakX(period.nodes[index])
        frame.nodeY[index] = glassPeakY(glassPeakHeightAt(frame.ys, period.nodes[index]))
        frame.nodeBottom[index] = glassPeakDropBottom(frame.nodeX[index], index == GlassPeakPeakNode)
    }
    frame.orbX = frame.nodeX[GlassPeakPeakNode]
    frame.orbY = frame.nodeY[GlassPeakPeakNode]
    frame.poolHumpX = frame.orbX
    frame.ringX = frame.orbX
    frame.ringY = frame.orbY
    return frame
}

internal fun glassPeakEaseOut(value: Float): Float = glassPeakBezier(value, .23f, 1f, .32f, 1f)

internal fun glassPeakEaseMove(value: Float): Float = glassPeakBezier(value, .77f, 0f, .175f, 1f)

internal fun glassPeakBezier(value: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (value <= 0f) return 0f
    if (value >= 1f) return 1f
    var low = 0f
    var high = 1f
    var u = value
    repeat(18) {
        val inverse = 1f - u
        val x = 3f * inverse * inverse * u * x1 + 3f * inverse * u * u * x2 + u * u * u
        if (x < value) low = u else high = u
        u = (low + high) * .5f
    }
    val inverse = 1f - u
    return 3f * inverse * inverse * u * y1 + 3f * inverse * u * u * y2 + u * u * u
}

internal fun glassPeakSmoothRange(value: Float, low: Float, high: Float): Float {
    val amount = ((value - low) / (high - low)).coerceIn(0f, 1f)
    return amount * amount * (3f - 2f * amount)
}

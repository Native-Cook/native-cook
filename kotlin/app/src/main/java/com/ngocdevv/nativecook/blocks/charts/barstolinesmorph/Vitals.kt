package com.ngocdevv.nativecook.blocks.charts.barstolinesmorph

import androidx.compose.ui.graphics.Color

sealed interface VitalChart {
    data class Bars(val low: List<Float>, val high: List<Float>) : VitalChart
    data class Lines(val first: List<Float>, val second: List<Float>? = null) : VitalChart
}

data class Vital(
    val key: String,
    val name: String,
    val icon: String,
    val iconColor: Color,
    val value: String,
    val unit: String,
    val time: String,
    val warning: Boolean = false,
    val chartUnit: String,
    val ticks: List<Float>,
    val above: Float? = null,
    val below: Float? = null,
    val colors: List<Color>,
    val chart: VitalChart,
)

private val Gold = Color(0xFFFFC83D)
private val Teal = Color(0xFF29D3C0)
private val Rose = Color(0xFFF2748A)
private const val NONE = 1e6f

internal class Plot(vital: Vital) {
    val bars = vital.chart is VitalChart.Bars
    val lines = when (val chart = vital.chart) {
        is VitalChart.Bars -> 0
        is VitalChart.Lines -> if (chart.second == null) 1 else 2
    }
    val a = when (val chart = vital.chart) {
        is VitalChart.Bars -> chart.low
        is VitalChart.Lines -> chart.first
    }
    val b = when (val chart = vital.chart) {
        is VitalChart.Bars -> chart.high
        is VitalChart.Lines -> chart.second ?: chart.first
    }
    private val pad = (vital.ticks[2] - vital.ticks[0]) / 8f
    val min = vital.ticks[0] - pad
    val max = vital.ticks[2] + pad
    val above = vital.above ?: NONE
    val below = vital.below ?: -NONE
    val colors = vital.colors
    private val tops = if (bars) b else a
    private val bottoms = if (bars) a else b
    val peak = tops.indexOf(tops.max())
    val trough = bottoms.indexOf(bottoms.min())
    val peakAbove = vital.above?.let { tops[peak] > it } ?: false
    val troughBelow = vital.below?.let { bottoms[trough] < it } ?: false

    fun extremes(unit: String) = listOf(number(tops[peak]) + " " + unit, number(bottoms[trough]) + " " + unit)
}

internal fun number(value: Float) = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

val SampleVitals = listOf(
    Vital(
        key = "heart",
        name = "Heart Rate",
        icon = "heart-pulse",
        iconColor = Gold,
        value = "146",
        unit = "BPM",
        time = "9:32 AM",
        warning = true,
        chartUnit = "BPM",
        ticks = listOf(60f, 120f, 180f),
        above = 140f,
        colors = listOf(Gold, Gold),
        chart = VitalChart.Bars(
            low = listOf(68f, 62f, 64f, 72f, 72f, 82f, 64f, 72f, 78f, 66f, 62f, 70f, 86f, 52f, 64f, 74f, 82f, 60f, 70f, 66f, 74f, 92f, 98f, 86f),
            high = listOf(92f, 128f, 116f, 112f, 124f, 128f, 116f, 110f, 150f, 122f, 104f, 128f, 120f, 96f, 116f, 128f, 172f, 92f, 112f, 126f, 136f, 122f, 146f, 152f),
        ),
    ),
    Vital(
        key = "spo2",
        name = "SpO2",
        icon = "water-percent",
        iconColor = Color(0xFF3D8BFF),
        value = "93",
        unit = "%",
        time = "9:34 AM",
        chartUnit = "%",
        ticks = listOf(88f, 94f, 100f),
        below = 90f,
        colors = listOf(Color(0xFF3D8BFF), Color(0xFF3D8BFF)),
        chart = VitalChart.Lines(
            first = listOf(97f, 98f, 97f, 96f, 95f, 93f, 91f, 89f, 90f, 94f, 96f, 97f, 98f, 97f, 96f, 97f, 98f, 97f, 95f, 94f, 96f, 97f, 95f, 93f),
        ),
    ),
    Vital(
        key = "fever",
        name = "Fever",
        icon = "thermometer",
        iconColor = Color(0xFFFF7A45),
        value = "97.8",
        unit = "°F",
        time = "9:40 AM",
        chartUnit = "°F",
        ticks = listOf(97f, 99f, 101f),
        above = 99.5f,
        colors = listOf(Color(0xFFFF9A5C), Color(0xFFFF9A5C)),
        chart = VitalChart.Lines(
            first = listOf(97.6f, 97.5f, 97.4f, 97.3f, 97.4f, 97.6f, 97.8f, 98f, 98.3f, 98.6f, 98.9f, 99.2f, 99.6f, 99.8f, 99.5f, 99.1f, 98.8f, 98.5f, 98.3f, 98.1f, 97.9f, 97.8f, 97.8f, 97.8f),
        ),
    ),
    Vital(
        key = "pressure",
        name = "Blood Pressure",
        icon = "gauge",
        iconColor = Teal,
        value = "145/95",
        unit = "mmHg",
        time = "9:46 AM",
        chartUnit = "mmHg",
        ticks = listOf(60f, 110f, 160f),
        above = 140f,
        below = 60f,
        colors = listOf(Teal, Rose),
        chart = VitalChart.Lines(
            first = listOf(124f, 128f, 122f, 131f, 126f, 119f, 134f, 129f, 138f, 127f, 121f, 133f, 142f, 148f, 136f, 129f, 124f, 131f, 137f, 128f, 122f, 126f, 133f, 145f),
            second = listOf(84f, 86f, 79f, 88f, 83f, 76f, 62f, 58f, 55f, 66f, 78f, 85f, 89f, 92f, 86f, 80f, 74f, 81f, 87f, 83f, 78f, 80f, 85f, 95f),
        ),
    ),
    Vital(
        key = "glucose",
        name = "Blood Glucose",
        icon = "water",
        iconColor = Color(0xFF34C759),
        value = "5.5",
        unit = "mmol/L",
        time = "9:41 AM",
        chartUnit = "mmol/L",
        ticks = listOf(4f, 7f, 10f),
        above = 7.8f,
        colors = listOf(Color(0xFF34C759), Color(0xFF34C759)),
        chart = VitalChart.Bars(
            low = listOf(4.6f, 4.4f, 4.3f, 4.2f, 4.4f, 4.8f, 5.2f, 5f, 4.8f, 5.4f, 5f, 4.7f, 5.6f, 5.2f, 4.9f, 4.8f, 5f, 5.8f, 5.4f, 5f, 4.8f, 4.6f, 4.5f, 4.9f),
            high = listOf(5.2f, 4.9f, 4.8f, 4.7f, 5.1f, 6.2f, 7.4f, 6.8f, 5.9f, 8.2f, 6.6f, 5.6f, 8.6f, 7f, 5.8f, 5.6f, 6.4f, 8f, 7.2f, 6f, 5.6f, 5.2f, 5f, 5.5f),
        ),
    ),
    Vital(
        key = "breath",
        name = "Respiratory Rate",
        icon = "lungs",
        iconColor = Color(0xFF9B6BFF),
        value = "18",
        unit = "br/min",
        time = "9:48 AM",
        chartUnit = "br/min",
        ticks = listOf(12f, 18f, 24f),
        above = 22f,
        colors = listOf(Color(0xFF9B6BFF), Color(0xFF9B6BFF)),
        chart = VitalChart.Lines(
            first = listOf(15f, 14f, 14f, 15f, 14f, 15f, 16f, 17f, 18f, 17f, 16f, 18f, 19f, 20f, 23f, 22f, 19f, 18f, 17f, 17f, 16f, 16f, 15f, 18f),
        ),
    ),
)

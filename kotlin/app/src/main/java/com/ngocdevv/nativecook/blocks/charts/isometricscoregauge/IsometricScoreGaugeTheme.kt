package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

internal object IsometricScoreGaugeTheme {
    const val REF_W = 402f
    const val REF_H = 874f
    object C {
        const val bg = 0x050506
        const val disc = 0x111114
        const val track = 0x1D1D22
        const val ink = 0xF0F0F2
        const val grey = 0x5E5E66
        const val caption = 0xCFCFD4
        const val card = 0x17161B
        const val cardEdge = 0x2A2930
        const val cardText = 0xD9D9DE
        const val glow = 0x3B2A5E
        const val up = 0x3CCB6E
        const val down = 0xF0663A
    }
    object Gauge {
        const val cx = 200f
        const val cy = 278f
        const val radius = 121f
        const val width = 6f
        const val start = 156f
        const val sweep = 228f
        const val low = 300.0
        const val high = 900.0
        const val score = 765.0
        const val numberY = 262f
        const val numberSize = 74f
        const val labelY = 335f
        val labelXs = floatArrayOf(68.8f, 327.5f)
    }
    object UI {
        const val backX = 45.6f
        const val backY = 93.8f
        const val logoX = 201f
        const val logoY = 93.8f
        const val logoSize = 27f
        const val captionY = 392.5f
        val rows = floatArrayOf(425f, 623f)
        const val cardW = 135f
        const val cardH = 183f
        const val pitch = 151.2
        const val x0 = 0.7
    }
    data class Scenario(val lines: List<String>, val icon: Int, val delta: Int)
    val columns = listOf(
        listOf(Scenario(listOf("pay off my", "credit card", "outstanding"), 4, 26), Scenario(listOf("close oldest", "credit card"), 5, -18)),
        listOf(Scenario(listOf("take a new", "credit card"), 0, -12), Scenario(listOf("take a new", "home loan"), 2, -22)),
        listOf(Scenario(listOf("take a new car", "loan"), 1, -37), Scenario(listOf("take a new", "loan"), 3, -29)),
        listOf(Scenario(listOf("raise my", "credit limit"), 0, 9), Scenario(listOf("save up a", "cushion"), 3, 14)),
    )
    const val LOGO = "outlook"
    const val CAPTION = "MAKE A CHOICE. SEE WHERE IT TAKES YOU"
}

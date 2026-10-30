package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

internal object SunUvBuddyTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    object C {
        const val page = 0xF4F4F6
        const val map = 0xC9CDD2
        const val cardTop = 0xFFF8D9
        const val cardBottom = 0xFFE9A6
        const val ink = 0x1E1F24
        const val grey = 0x8E9099
        const val chip = 0xFFFFFF
        const val chipOn = 0x24252B
        const val sun = 0xFFD800
        const val sunDeep = 0xFFC400
        const val barLow = 0xEDEDF2
        const val barMid = 0xFFE14D
        const val barHigh = 0xFFB21E
        const val green = 0x3CC665
        const val yellow = 0xF7CF39
    }
    object Copy {
        const val title = "UV index"
        const val place = "Riverside village"
        val days = arrayOf("Today", "Tomorrow", "Thu, 2", "Fri, 3", "Sat, 4")
        const val learn = "What the UV index means"
    }
    val hours = arrayOf("09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19")
    val days = arrayOf(
        doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 5.0, 4.0, 3.0, 1.0, 0.0, 0.0),
        doubleArrayOf(1.0, 1.0, 2.0, 3.0, 4.0, 4.0, 3.0, 2.0, 1.0, 0.0, 0.0),
        doubleArrayOf(1.0, 1.0, 2.0, 3.0, 3.0, 3.0, 2.0, 1.0, 1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 1.0, 2.0, 3.0, 3.0, 2.0, 1.0, 1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 1.0, 1.0, 2.0, 2.0, 1.0, 1.0, 0.0, 0.0, 0.0),
    )
    data class Words(val level: String, val lines: List<String>)
    fun words(uv: Int): Words = when {
        uv <= 0 -> Words("no UV at all", listOf("The sun is going to bed.", "Nothing to worry about now"))
        uv <= 2 -> Words("low", listOf("Barely any sun. Enjoy the day", "without a second thought"))
        uv <= 4 -> Words("moderate", listOf("The sun is calm. But sunscreen", "and a cap won't hurt"))
        else -> Words("high", listOf("Strong sun. Sunscreen, a cap", "and some shade, please"))
    }
    object UI {
        const val sheetY = 61.3
        const val sheetR = 34.0
        const val sheetBottom = 613.0
        const val sheetBottomR = 30.0
        const val titleY = 84.6
        const val placeY = 105.0
        const val closeX = 370.0
        const val closeY = 95.2
        const val closeR = 20.0
        const val chipsY = 148.0
        const val chipsH = 35.0
        val chipsX = doubleArrayOf(-6.0, 74.0, 178.0, 262.0, 336.0)
        val chipsW = doubleArrayOf(74.0, 98.0, 78.0, 68.0, 72.0)
        const val sunX = 198.8
        const val sunY = 327.8
        const val sunR = 84.0
        const val headlineY = 446.0
        const val badgeX = 200.9
        const val badgeY = 476.6
        const val badgeR = 12.7
        val lineY = doubleArrayOf(512.5, 534.4)
        const val iconsY = 569.0
        const val barsX = 20.4
        const val barsW = 29.6
        const val barsGap = 3.5
        const val barsBottom = 765.6
        val barsH = doubleArrayOf(5.0, 26.8, 37.4, 48.0, 58.0, 68.0)
        const val barsLabelY = 787.5
        const val stemX = 135.4
        const val learnY = 860.0
    }
}

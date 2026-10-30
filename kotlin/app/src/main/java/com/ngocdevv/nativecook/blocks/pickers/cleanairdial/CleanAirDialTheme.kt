package com.ngocdevv.nativecook.blocks.pickers.cleanairdial
internal object CleanAirDialTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = 402.0 / 670.0
    const val X0 = 265.0
    const val Y0 = 220.0
    const val DUST = 300
    const val BASE_DUST = 260
    object C {
        const val bg = 0x121212
        const val tick = 0x4A4A4A
        const val green = 0x29AB65
        const val diskTop = 0x070707
        const val diskBot = 0x101010
        const val rim = 0x1A1A1A
        const val divider = 0x202020
        const val label = 0x868686
        const val white = 0xF4F4F4
        const val unit = 0x9A9A9A
        const val sub = 0x6B6B6B
        const val chip = 0x1D1D1D
        const val blue = 0x3F6AF5
        const val purple = 0x5E55F2
        const val power = 0xF1F1F1
        const val dust = 0xD8C6A8
        const val capText = 0x0B2E14
    }
    val qualityMax = doubleArrayOf(6.5, 15.5, 999.0)
    val qualityWord = arrayOf("Good", "Moderate", "Poor")
    val qualityColor = intArrayOf(0x4FD6C8, 0x18B063, 0xF2994A)
    val levelNames = arrayOf("Low", "Medium", "High", "Turbo")
    val ticks = doubleArrayOf(6.0, 12.0, 21.0, 30.0)
    val omega = doubleArrayOf(0.35, 0.7, 1.6, 2.5)
    val target = doubleArrayOf(-0.5, 0.0, 0.62, 0.88)
    object UI {
        const val headerY = -200.0
        const val headerSub = -160.0
        const val dialX = 600.0
        const val dialY = 282.0
        const val r0 = 247.0
        const val r1 = 270.0
        const val tickCount = 60
        const val disk = 230.0
        const val tickW = 4.6
        const val pmY = 140.0
        const val pmValue = 212.0
        const val divider = 283.0
        val statX = doubleArrayOf(470.0, 600.0, 728.0)
        const val statV = 335.0
        const val statIcon = 388.0
        val statDiv = doubleArrayOf(537.0, 665.0)
        val statDivY = doubleArrayOf(313.0, 407.0)
        const val capCY = 622.0
        const val capR = 185.0
        const val capText = 474.0
        val chipX = doubleArrayOf(400.0, 600.0, 800.0)
        const val chipY = 648.0
        const val chipW = 142.0
        const val chipH = 78.0
        const val chipLabel = 716.0
        const val chipSub = 748.0
        const val bottomY = 845.0
    }
    object COPY {
        const val title = "Air Purifier"
        const val room = "Living room"
        const val pm = "PM 2.5"
        val statV = arrayOf("7", "64", "12")
        val statU = arrayOf("°C", "%", "%")
        val chips = arrayOf("Speed", "Timer", "Scenes")
        const val timerOn = "30 mins · On"
        const val timerOff = "30 mins · Off"
    }
}

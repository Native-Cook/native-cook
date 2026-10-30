package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

internal object MorphObjectOnboardingTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    private const val S = REF_W / 291.0
    fun x(px: Double) = (px - 101) * S
    fun y(px: Double) = (px - 156) * S
    val titles = arrayOf(arrayOf("We really value", "your time"), arrayOf("You will", "learn online"), arrayOf("You will learn", "correctly"))
    val subtitles = arrayOf(arrayOf("The classes last for 60 minutes,", "you can schedule for any day"), arrayOf("You won't need to spend money", "and time going to school."), arrayOf("Our teachers hold a special", "international certificate"))
    val glows = intArrayOf(0xFFB300, 0x7A5CFF, 0xFF6A3D)
    const val BG = 0x18171F
    const val INK = 0x111014
    const val SUB = 0x8B8A92
    const val DOT = 0x4A4954
    const val SPARKLE = 0xFF8FC0
    val dotsX = x(122.0)
    const val TOP_Y = 68.0
    val skipX = x(369.0)
    val cardY = y(530.0)
    const val CARD_R = 40.0
    val titleY = doubleArrayOf(y(577.0), y(604.0))
    val subY = doubleArrayOf(y(642.0), y(661.0))
    val buttonX = x(162.0)
    val buttonY = y(715.0)
    val buttonW = x(330.0) - x(162.0)
    val buttonH = y(757.0) - y(715.0)
    val sparkleX = x(183.0)
    val sparkleY = y(268.0)
    const val OBJECT_X = 28.0
    const val OBJECT_Y = 136.0
    const val OBJECT_SIZE = 305.0
    const val FRAMES = 33
    const val COLS = 6
    const val FRAME_SIZE = 256
    const val PER_PAGE = 16
}

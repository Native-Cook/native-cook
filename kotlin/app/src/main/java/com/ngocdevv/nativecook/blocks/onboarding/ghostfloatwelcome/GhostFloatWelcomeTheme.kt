package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome
internal object GhostFloatWelcomeTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = REF_W / 610

    fun x(px: Double) = (px - 294) * S

    fun y(px: Double) = (px - 90) * S

    data class Body(val kind: String, val x: Double, val y: Double, val r: Double, val depth: Double)

    data class Star(val x: Double, val y: Double, val s: Double, val depth: Double)

    data class Glint(val body: Int, val dx: Double, val dy: Double, val s: Double)
    object C {
        const val bg = 0x1C1C1C
        const val white = 0xFFFFFF
        const val sub = 0xE8E8E8
        const val grey = 0x9A9A9A
        const val lavender = 0xAB9FF2
        const val pill = 0xAD9FF6
        const val dot = 0x55516A
        const val star = 0x392D5B
        const val ring = 0x8A8A8A
        const val btnOff = 0x67608C
        const val btnOffInk = 0x17122C
        const val btnOn = 0xAB9FF2
        const val btnOnInk = 0x1C1C1C
        const val have = 0x8E8E8E
        const val ghost = 0xFFFEFA
        const val eye = 0x1D1C1A
    }
    object UI {
        val helpX = x(862.0)
        val helpY = y(196.0)
        const val helpR = 9.2
        val titleY = y(888.0)
        val sub1Y = y(946.0)
        val sub2Y = y(981.0)
        val dotsY = y(1043.0)
        const val pillW = 15.8
        const val dotH = 5.3
        const val dotGap = 9.9
        const val dotN = 6
        val checkX = x(379.0)
        val checkY = y(1132.0)
        const val checkR = 12.5
        val agreeX = x(418.0)
        val buttonX = x(321.0)
        val buttonY = y(1191.0)
        val buttonW = x(878.0) - buttonX
        val buttonH = y(1268.0) - buttonY
        val haveY = y(1321.0)
    }
    val bodies = arrayOf(
        Body("ghost", x(600.0), y(520.0), 41.0, 1.0),
        Body("lumen", x(600.0), y(355.0), x(656.0) - x(600.0), 0.8),
        Body("dino", x(350.0), y(462.0), x(401.0) - x(350.0), 0.6),
        Body("halo", x(852.0), y(504.0), x(894.0) - x(852.0), 0.5),
        Body("ember", x(342.0), y(662.0), x(390.0) - x(342.0), 0.7),
        Body("penguin", x(832.0), y(670.0), x(874.0) - x(832.0), 0.55),
        Body("fern", x(595.0), y(725.0), x(655.0) - x(595.0), 0.9),
    )
    val tokens = mapOf("lumen" to (0xEAEAEA to 0x151515), "ember" to (0xF2662B to 0xFFF2E7), "fern" to (0x29C28C to 0xF4FFF9), "halo" to (0xADA2F0 to 0xFFFFFF))
    val glints = arrayOf(
        Glint(1, x(624.0) - x(600.0), y(318.0) - y(355.0), 12.0),
        Glint(3, x(876.0) - x(852.0), y(477.0) - y(504.0), 9.5),
        Glint(4, x(370.0) - x(342.0), y(628.0) - y(662.0), 9.5),
        Glint(6, x(552.0) - x(595.0), y(688.0) - y(725.0), 10.5),
    )
    val stars = arrayOf(
        Star(x(418.0), y(272.0), 17.0, 0.25), Star(x(810.0), y(325.0), 25.0, 0.35),
        Star(x(478.0), y(588.0), 15.0, 0.2), Star(x(752.0), y(605.0), 12.0, 0.15),
        Star(x(385.0), y(758.0), 20.0, 0.3), Star(x(787.0), y(800.0), 16.0, 0.25),
    )
    val fieldBottom = y(840.0)
}


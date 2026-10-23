package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

// Invented names and mascots. A portrait's flat background leads the light; its two aura
// colors add a neighboring hue and a deeper accent chosen for that friend.
internal object ReferralFriends {
    val ids = arrayOf("mika", "juno", "otto", "nori", "pim", "zuzu", "rafa")
    val names = arrayOf("Mika", "Juno", "Otto", "Nori", "Pim", "Zuzu", "Rafa")
    val colors =
        intArrayOf(
            0xFFFF8A3D.toInt(),
            0xFF7BE0B0.toInt(),
            0xFF8B9CFF.toInt(),
            0xFFFF8FC7.toInt(),
            0xFF6EC6FF.toInt(),
            0xFFFFD966.toInt(),
            0xFFC7A6FF.toInt(),
        )

    // Dimming yellow turns it olive, so Zuzu's lamp uses amber to keep its warmth.
    val lamps = colors.copyOf().apply { this[5] = 0xFFFFAE2B.toInt() }
    val second =
        intArrayOf(
            0xFFFF2E7E.toInt(),
            0xFF19B8FF.toInt(),
            0xFFB44DFF.toInt(),
            0xFFFF6A3D.toInt(),
            0xFF5B5BFF.toInt(),
            0xFFFF7A1F.toInt(),
            0xFFFF5CC8.toInt(),
        )
    val deep =
        intArrayOf(
            0xFF5B2EFF.toInt(),
            0xFF2E5BFF.toInt(),
            0xFF1E9BFF.toInt(),
            0xFF8A3DFF.toInt(),
            0xFF19D9C0.toInt(),
            0xFFFF2E7E.toInt(),
            0xFF3D6BFF.toInt(),
        )

    fun blend(
        colors: IntArray,
        scroll: Double,
    ): Int {
        val at = ReferralMath.wrap(scroll)
        val index = at.toInt()
        val fraction = at - index
        val from = colors[index]
        val to = colors[(index + 1) % 7]
        var color = 0xFF000000.toInt()
        for (channel in 0..2) {
            val shift = channel * 8
            val x = (from ushr shift) and 255
            val y = (to ushr shift) and 255
            color = color or ((x + (y - x) * fraction).toInt() shl shift)
        }
        return color
    }

    fun rgb(
        colors: IntArray,
        scroll: Double,
        output: FloatArray,
    ) {
        val at = ReferralMath.wrap(scroll)
        val index = at.toInt()
        val fraction = at - index
        val from = colors[index]
        val to = colors[(index + 1) % 7]
        for (channelIndex in 0..2) {
            val channel = (2 - channelIndex) * 8
            val x = (from ushr channel) and 255
            val y = (to ushr channel) and 255
            output[channelIndex] = ((x + (y - x) * fraction) / 255).toFloat()
        }
    }
}

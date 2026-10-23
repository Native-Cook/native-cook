package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// The two cubic Bezier curves used by the wallet’s timing animations.
internal object AuraEasing {
    // Invert the cubic's x coordinate, then evaluate y. Same curves as ui.tsx.
    fun bezier(
        t: Double,
        inOut: Boolean = false,
    ): Double {
        val x = t.coerceIn(0.0, 1.0)
        if (x == 0.0 || x == 1.0) return x
        val x1 = if (inOut) 0.77 else 0.23
        val x2 = if (inOut) 0.175 else 0.32
        var lower = 0.0
        var upper = 1.0
        var u = x
        repeat(26) {
            val m = 1 - u
            val v = 3 * m * m * u * x1 + 3 * m * u * u * x2 + u * u * u
            if (v < x) lower = u else upper = u
            u = (lower + upper) / 2
        }
        val m = 1 - u
        return if (inOut) 3 * m * u * u + u * u * u else 3 * m * m * u + 3 * m * u * u + u * u * u
    }

}

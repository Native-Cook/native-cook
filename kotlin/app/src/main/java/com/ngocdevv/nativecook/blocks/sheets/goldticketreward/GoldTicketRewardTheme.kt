package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

internal object GoldTicketRewardTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = REF_W / 274.0

    fun x(v: Double) = v * S

    fun yt(v: Double) = v * S

    fun yb(v: Double) = REF_H - (581 - v) * S

    data class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    data class Point(val x: Double, val y: Double)

    data class Coin(val x: Double, val y: Double, val r: Double)

    data class Sparkle(
        val x: Double,
        val y: Double,
        val s: Double,
        val k: Double,
        val ph: Double,
        val gold: Boolean = false,
    )

    fun box(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        bottom: Boolean = true,
    ) = Box(
        x(x0),
        if (bottom) yb(y0) else yt(y0),
        x(x1 - x0),
        x(y1 - y0),
    )

    fun point(px: Double, py: Double) = Point(x(px), yb(py))

    object A {
        val glowTop = yb(168.0)
        val stageTop = yb(276.0)
        val stageEdge = yb(294.0)
        val stageInset = x(37.0)
        val cars = box(37.0, 240.0, 240.0, 291.0)
        val thanks = point(137.0, 308.0)
        val callback = point(136.0, 327.0)
        val ticket = box(21.5, 385.5, 252.5, 466.5)
        val notchR = x(5.2)
        val coin = Coin(x(136.5), yb(384.5), x(21.0))
        val halo = x(26.5)
        val win = point(137.0, 419.5)
        val prize = point(136.5, 445.0)
        val band = box(0.0, 479.0, 274.0, 500.0)
        val benefits = listOf(point(54.5, 489.5), point(139.5, 489.5), point(222.0, 489.5))
        val button = box(10.0, 522.0, 264.0, 558.5)
        val radius = x(6.0)
        val sparkles = listOf(
            Sparkle(x(79.0), yb(212.0), x(7.5), 7.0, 0.1),
            Sparkle(x(38.0), yb(251.0), x(3.5), 9.0, 0.55),
            Sparkle(x(219.0), yb(244.0), x(4.0), 8.0, 0.3)
        )
    }

    object B {
        val ticket = box(40.0, 380.0, 232.5, 456.5)
        val notchR = x(5.4)
        val coin = Coin(x(136.0), yb(370.5), x(24.0))
        val win = point(136.5, 411.5)
        val prize = point(136.5, 431.5)
        val benefits = listOf(point(85.0, 488.0), point(199.5, 488.0))
        val button = box(12.0, 524.5, 261.5, 559.5)
        val close = point(249.3, 310.7)
        val fadeTop = yb(352.0)
        val full = yb(468.0)
        val sparkles = listOf(
            Sparkle(x(27.0), yb(377.5), x(9.0), 6.0, 0.0, true),
            Sparkle(x(247.0), yb(436.0), x(9.0), 7.0, 0.4, true),
            Sparkle(x(246.0), yb(337.5), x(5.0), 9.0, 0.7, true),
            Sparkle(x(197.0), yb(345.0), x(6.5), 8.0, 0.2),
            Sparkle(x(174.5), yb(361.0), x(2.6), 11.0, 0.85),
            Sparkle(x(24.5), yb(451.5), x(6.5), 5.0, 0.6),
            Sparkle(x(83.5), yb(343.5), x(2.6), 10.0, 0.15)
        )
    }
}

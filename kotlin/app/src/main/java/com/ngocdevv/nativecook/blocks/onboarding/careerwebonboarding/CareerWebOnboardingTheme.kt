package com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding

/** Layout, colors and copy copied from cwo-theme.ts in the reference space. */
internal object CareerWebOnboardingTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SCALE = REF_W / 251.0

    fun x(px: Double): Double = (px - 56.0) * SCALE
    fun y(px: Double): Double = (px - 143.0) * SCALE

    object Copy {
        val lines = arrayOf("Build Your", "Future, Build", "Your ")
        const val dream = "Dream"
        const val cta = "Get Started !"
    }

    object C {
        const val bg = 0xFF101011.toInt()
        const val white = 0xFFFFFFFF.toInt()
        const val ink = 0xFF121214.toInt()
        const val highlight = 0xFFC8BAFF.toInt()
        const val rope = 0xFFFFFFFF.toInt()
        const val ring = 0xFFC8BAFF.toInt()
        const val grid = 0x09FFFFFF
    }

    data class Title(val x: Double, val lines: DoubleArray, val width0: Double)
    val title = Title(
        x = x(68.0),
        lines = doubleArrayOf(y(214.0), y(252.0), y(290.0)),
        width0 = x(213.0) - x(68.0),
    )

    data class Marker(val x0: Double, val x1: Double, val top0: Double, val top1: Double, val bot0: Double, val bot1: Double)
    val marker = Marker(x(140.0), x(247.0), y(271.0), y(266.0), y(308.0), y(312.0))

    data class Arrow(val ax: Double, val ay: Double, val cx: Double, val cy: Double, val bx: Double, val by: Double, val head: Double)
    val arrow = Arrow(x(272.0), y(316.0), x(284.0), y(350.0), x(250.0), y(378.0), 13.0)

    data class Pop(val x0: Double, val y0: Double, val x1: Double, val y1: Double)
    val pops = arrayOf(
        Pop(x(99.0), y(342.0), x(104.0), y(354.0)),
        Pop(x(114.0), y(338.0), x(111.0), y(351.0)),
        Pop(x(89.0), y(358.0), x(99.0), y(363.0)),
    )

    enum class NodeKind { PERSON, ROLE }
    data class Node(
        val kind: NodeKind,
        val x: Double,
        val y: Double,
        val r: Double,
        val label: Array<String>? = null,
        val image: Int = -1,
        val background: Int = C.white,
    )

    private const val NODE_R = 51.0
    val nodes = arrayOf(
        Node(NodeKind.PERSON, x(126.0), y(398.0), NODE_R, image = 0, background = 0xFFDFF5E1.toInt()),
        Node(NodeKind.PERSON, x(186.0), y(483.0), NODE_R, image = 1, background = 0xFFF7D5B5.toInt()),
        Node(NodeKind.PERSON, x(238.0), y(560.0), NODE_R, image = 2, background = 0xFFCEC2F7.toInt()),
        Node(NodeKind.ROLE, x(207.0), y(415.0), NODE_R, label = arrayOf("UX/UI", "Designer")),
        Node(NodeKind.ROLE, x(104.0), y(483.0), NODE_R, label = arrayOf("Product", "Designer")),
        Node(NodeKind.ROLE, x(258.0), y(488.0), NODE_R, label = arrayOf("Software", "Engineer")),
        Node(NodeKind.ROLE, x(155.0), y(555.0), NODE_R, label = arrayOf("Flutter", "Developer")),
    )

    val bars = arrayOf(intArrayOf(3, 4), intArrayOf(5, 6))
    val fits = arrayOf(intArrayOf(3, 4), intArrayOf(4, 5), intArrayOf(5, 6))
    const val ROPE_W = 12.8

    data class Button(val x: Double, val y: Double, val w: Double, val h: Double, val dotX: Double, val dotY: Double, val dotR: Double)
    val button = Button(x(82.0), y(625.0), x(288.0) - x(82.0), y(663.0) - y(625.0), x(266.0), y(644.0), 27.0)

    const val ROLE_SIZE = 15.5
    const val CTA_SIZE = 17.0
}

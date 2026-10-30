package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import kotlin.math.*

/** The reference's 402 × 874 coordinates. Page anchors hang from the top
sheet from bottom. */
internal object QrJoyPostcardTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val S = REF_W / 1179.0

    fun x(px: Double) = px * S

    fun y(px: Double) = (px + 78) * S + 1.25
    data class Box(
        val x: Double,
        val y: Double,
        val w: Double,
        val h: Double,
        val r: Double = 0.0
    )

    fun box(x0: Double, y0: Double, x1: Double, y1: Double, r: Double = 0.0) = Box(QrJoyPostcardTheme.x(x0), QrJoyPostcardTheme.y(y0), QrJoyPostcardTheme.x(x1)-QrJoyPostcardTheme.x(x0), QrJoyPostcardTheme.y(y1)-QrJoyPostcardTheme.y(y0), r)
    object C {
        const val page = 0xF6F6FA
        const val card = 0xFFFFFF
        const val ink = 0x252527
        const val grey = 0x8E8E93
        const val violet = 0xC252FF
        const val promo = 0xFEE6FF
        const val chip = 0xF2F2F7
        const val heroTop = 0xFFC2F7
        const val heroMid = 0xFFC9F9
        const val heroLight = 0xFFE0FB
        const val closeX = 0xFFAEF2
        const val cta = 0xB62DFF
        const val ctaPress = 0xA01FEA
        const val stepChip = 0xF6F5FA
        const val qrInk = 0x252429
        const val postTop = 0xFFE6F7
        const val postBottom = 0xFFC4EC
        const val dotA = 0xC21FD6
        const val dotB = 0x5B21D9
        const val finder = 0xD61FC4
        const val finderCore = 0x5B1A8E
        const val petal = 0xFF6FD8
        const val heart = 0xF0219F
        const val bubbleInk = 0x7A1FA8
        const val sparkle = 0xFFFFFF
    }
    object Page {
        val headerY = QrJoyPostcardTheme.y(220.0)
        val backX = QrJoyPostcardTheme.x(75.0)
        val backY = QrJoyPostcardTheme.y(150.0)
        val ordersY = QrJoyPostcardTheme.y(152.0)
        val row = box(48.0, 255.0, 1130.0, 430.0, 18.0)
        val bagX = QrJoyPostcardTheme.x(130.0)
        val bagY = QrJoyPostcardTheme.y(338.0)
        val purchasesX = QrJoyPostcardTheme.x(205.0)
        val chevronX = QrJoyPostcardTheme.x(1048.0)
        val chevronY = QrJoyPostcardTheme.y(342.0)
        val promo = box(0.0, 465.0, 1179.0, 640.0, 26.0)
        val tulips = box(292.0, 418.0, 472.0, 548.0)
        val promoX = QrJoyPostcardTheme.x(468.0)
        val promoY = QrJoyPostcardTheme.y(505.0)
        val promoChevronX = QrJoyPostcardTheme.x(868.0)
        val order = box(0.0, 552.0, 1179.0, 1000.0, 26.0)
        val pick1Y = QrJoyPostcardTheme.y(630.0)
        val pick2Y = QrJoyPostcardTheme.y(705.0)
        val qrButton = box(975.0, 590.0, 1130.0, 745.0, 18.0)
        val pointY = QrJoyPostcardTheme.y(797.0)
        val grabber = box(537.0, 845.0, 643.0, 857.0)
    }
    object Sheet {
        val top = QrJoyPostcardTheme.y(876.0)
        const val r = 24.0
        val heroBottom = QrJoyPostcardTheme.y(1506.0)
        val closeX = QrJoyPostcardTheme.x(1095.0)
        val closeY = QrJoyPostcardTheme.y(952.0)
        val closeR = QrJoyPostcardTheme.x(33.0)
        val titleX = QrJoyPostcardTheme.x(50.0)
        val titleY = QrJoyPostcardTheme.y(1587.0)
        val subY = QrJoyPostcardTheme.y(1688.0)
        val chipX = QrJoyPostcardTheme.x(88.0)
        val chipR = QrJoyPostcardTheme.x(37.0)
        val stepTextX = QrJoyPostcardTheme.x(152.0)
        val linesY = arrayOf(doubleArrayOf(QrJoyPostcardTheme.y(1781.0), QrJoyPostcardTheme.y(1841.0), QrJoyPostcardTheme.y(1901.0)), doubleArrayOf(QrJoyPostcardTheme.y(2005.0), QrJoyPostcardTheme.y(2065.0), QrJoyPostcardTheme.y(2125.0)))
        val chipY = doubleArrayOf(QrJoyPostcardTheme.y(1787.0), QrJoyPostcardTheme.y(2010.0))
        val cta = box(24.0, 2204.0, 1155.0, 2350.0, 17.0)
    }
    object Card {
        const val angle = -9.6 * PI / 180
        const val w = 190.0
        const val h = 232.0
        const val r = 19.0
        val cx = QrJoyPostcardTheme.x(195.0) + cos(angle)*w/2 - sin(angle)*h/2
        val cy = QrJoyPostcardTheme.y(1255.0) + sin(angle)*w/2 + cos(angle)*h/2
    }

    fun toCard(x: Double, y: Double, lift: Double = 0.0): Pair<Double, Double> {
        val angle = QrJoyPostcardMotion.mix(Card.angle, QrJoyPostcardMotion.TURN_LIFTED, lift.coerceIn(0.0, 1.0))
        val dx = x-Card.cx
        val dy = y-(Card.cy-QrJoyPostcardMotion.LIFT*lift)
        return Pair(dx*cos(-angle)-dy*sin(-angle)+Card.w/2, dx*sin(-angle)+dy*cos(-angle)+Card.h/2)
    }
    object QR {
        const val n = 29
        const val x = 27.0
        const val y = 23.0
        const val size = 136.0
        const val m = size/n
    }
    object Bear {
        private val base: Pair<Double, Double> = toCard(QrJoyPostcardTheme.x(450.0), QrJoyPostcardTheme.y(1302.0))
        val x: Double = base.first
        val y: Double = base.second
        const val w = 141.0
        const val h = w * 345 / 360
        const val rest = 4
        const val frames = 9
        const val cols = 3
        const val fw = 360
        const val fh = 345
    }
    object Gift {
        val x = QrJoyPostcardTheme.x(838.0)
        val y = QrJoyPostcardTheme.y(1238.0)
        const val w = 128.0
        const val h = w*242/192
        const val rot = -7.0
        const val blur = 4.2
    }
    object Sticker {
        const val x = 196.0
        const val y = 64.0
        const val w = 58.0
        const val h = w*500/420
        const val rot = 13.0
        const val border = 4.5
    }
    object Bubble {
        val x = QrJoyPostcardTheme.x(700.0)
        val y = QrJoyPostcardTheme.y(968.0)
        const val w = 92.0
        const val h = 40.0
        const val r = 20.0
        val tailX = QrJoyPostcardTheme.x(585.0)
        val tailY = QrJoyPostcardTheme.y(1030.0)
    }
    val sparkles = arrayOf(
    doubleArrayOf(QrJoyPostcardTheme.x(268.0), QrJoyPostcardTheme.y(1000.0), 26.0, 0.0), doubleArrayOf(QrJoyPostcardTheme.x(772.0), QrJoyPostcardTheme.y(918.0), 13.0, 0.35),
    doubleArrayOf(QrJoyPostcardTheme.x(655.0), QrJoyPostcardTheme.y(962.0), 6.0, 0.7), doubleArrayOf(QrJoyPostcardTheme.x(58.0), QrJoyPostcardTheme.y(1262.0), 8.0, 0.15),
    doubleArrayOf(QrJoyPostcardTheme.x(1125.0), QrJoyPostcardTheme.y(1238.0), 7.0, 0.55), doubleArrayOf(QrJoyPostcardTheme.x(150.0), QrJoyPostcardTheme.y(1445.0), 6.0, 0.85))
    val steps = arrayOf(arrayOf("Decorate a plain QR code: add a", "backdrop and a few warm words to", "turn it into a lovely postcard."), arrayOf("Send it to whoever picks it up: even", "the most ordinary parcel becomes a", "little nicer to collect."))
    private val rows = arrayOf(
    "11111110010110000111001111111",
    "10000010111010011000101000001",
    "10111010001101011111101011101",
    "10111010011110110001001011101",
    "10111010010111011010001011101",
    "10000010111000001011001000001",
    "11111110101010101010101111111",
    "00000000111011001111100000000",
    "00001111011001010010001100010",
    "11011101101101001101111010011",
    "10111011110110011000100001101",
    "01001101100010001010011101001",
    "01011011000101111011110000010",
    "01111100101000110010001111001",
    "11110010011110000100111100001",
    "00101101000010111101010101010",
    "10011011111001110101110111001",
    "10001101001011000011000111001",
    "00001010101000011011001011001",
    "00101100100100000101101000011",
    "11001111100001101010111111010",
    "00000000101111111001100011101",
    "11111110111110101011101011001",
    "10000010111110011101100011000",
    "10111010100000101111111111011",
    "10111010011010000001000100010",
    "10111010011001001011110001111",
    "10000010000110000010010000011",
    "11111110001101111101111100010",
    )
    data class Module(
        val r: Int,
        val c: Int,
        val d: Double,
        val h: Double,
        val heart: Boolean
    )
    val finders = arrayOf(0 to 0, 0 to 22, 22 to 0)
    val modules = buildList {
        val mid = 14.0
        val maxD = hypot(mid, mid)
        for (r in 0 until QR.n) for (c in 0 until QR.n) {
            val finder = (r<7 && c<7) || (r<7 && c >= 22) || (r >= 22 && c<7)
            if (rows[r][c] != '1' || finder) continue
            val seed = sin((r*29+c)*12.9898)*43758.5453
            add(Module(r, c, hypot(r-mid, c-mid)/maxD, seed-floor(seed), abs(r-mid) <= 3 && abs(c-mid) <= 3))
        }
    }
}

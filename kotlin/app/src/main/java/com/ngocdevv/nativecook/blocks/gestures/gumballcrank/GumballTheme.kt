package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import androidx.compose.ui.graphics.Color

private fun gumballRgba(red: Int, green: Int, blue: Int, alpha: Double): Color =
    Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = alpha.toFloat())

/** All visual tokens from the React Native reference, kept value-for-value for the Kotlin port. */
internal object GumballTheme {
    const val STAGE_WIDTH: Double = 402.0

    object COLORS {
        val wallTop = Color(0xFFD9CCDD)
        val wallBottom = Color(0xFFD2C1D7)
        val beam = gumballRgba(255, 255, 255, 0.28)
        val beamFar = gumballRgba(255, 255, 255, 0.0)
        val tableTop = Color(0xFFC9AEDC)
        val tableFront = Color(0xFFB897CF)
        val tableEdge = gumballRgba(255, 255, 255, 0.45)
        val shadow = gumballRgba(80, 40, 110, 0.22)
        val body = Color(0xFFB57FDB)
        val bodyLight = Color(0xFFCFA4F0)
        val bodyDeep = Color(0xFF9A63C6)
        val wave = gumballRgba(255, 255, 255, 0.22)
        val plate = Color(0xFFC799EC)
        val door = Color(0xFF9C64CB)
        val doorDeep = Color(0xFF7E49AE)
        val knob = Color(0xFFF6B04A)
        val knobLight = Color(0xFFFFD58A)
        val knobDeep = Color(0xFFD98A26)
        val slot = Color(0xFFD07F1E)
        val glassRim = gumballRgba(255, 255, 255, 0.85)
        val glassTint = gumballRgba(255, 255, 255, 0.12)
        val glassShade = gumballRgba(120, 70, 150, 0.16)
        val reflect = gumballRgba(255, 255, 255, 0.55)
        val ink = Color(0xFF3B2350)
        val mute = gumballRgba(59, 35, 80, 0.6)
        val capsuleTop = gumballRgba(255, 255, 255, 0.78)
        val capsuleEdge = gumballRgba(255, 255, 255, 0.95)
        val capsule = Color(0xFFF0507E)
        val capsuleDeep = Color(0xFFC73460)
        val card = Color(0xFFFFFFFF)
        val cardAccent = Color(0xFFF0507E)
        val button = Color(0xFF3B2350)
        val buttonInk = Color(0xFFFFFFFF)
        val scrim = gumballRgba(214, 196, 222, 0.55)
        val bag = Color(0xFF9A63C6)
        val bagLight = Color(0xFFCFA4F0)
    }

    val BALLS = listOf(
        Color(0xFFFF9E7A),
        Color(0xFFE8547A),
        Color(0xFFFFD66B),
        Color(0xFF7FD8B5),
        Color(0xFF8B6CE0),
        Color(0xFF6FB7FF),
        Color(0xFFF7A6C8),
        Color(0xFF5B3B6E),
    )

    object MACHINE {
        const val lidTop: Double = 196.0
        const val lidHeight: Double = 58.0
        const val lidWidth: Double = 188.0
        const val lidKnob: Double = 14.0
        const val globeY: Double = 348.0
        const val globeR: Double = 130.0
        const val glass: Double = 6.0
        const val collarY: Double = 436.0
        const val collarHeight: Double = 16.0
        const val collarWidth: Double = 204.0
        const val baseTop: Double = 452.0
        const val baseBottom: Double = 645.0
        const val baseTopWidth: Double = 158.0
        const val baseBottomWidth: Double = 224.0
        const val baseRadius: Double = 26.0
        const val plateY: Double = 514.0
        const val plateSize: Double = 72.0
        const val plateRadius: Double = 18.0
        const val knobR: Double = 22.0
        const val slotWidth: Double = 30.0
        const val slotHeight: Double = 7.0
        const val doorTop: Double = 560.0
        const val doorWidth: Double = 84.0
        const val doorHeight: Double = 78.0
        const val doorRadius: Double = 28.0
        const val tableY: Double = 600.0
        const val tableFrontY: Double = 812.0
        const val extraY: Double = 0.5
    }

    object BALL {
        const val count: Int = 15
        const val rMin: Double = 18.0
        const val rMax: Double = 24.0
        const val seed: Double = 7.0
    }

    object CAPSULE {
        const val r: Double = 20.0
        const val rollTo: Double = 726.0
        const val rollScale: Double = 1.7
    }

    object CARD {
        const val width: Double = 288.0
        const val height: Double = 236.0
        const val radius: Double = 28.0
        const val y: Double = 560.0
        const val pad: Double = 24.0
    }

    object BAG {
        const val size: Double = 44.0
        const val right: Double = 24.0
    }

    object TYPE {
        const val side: Double = 24.0
        const val top: Double = 16.0
        const val titleSize: Double = 28.0
        const val titleFamily: String = "Georgia"
        const val subSize: Double = 14.0
        const val hintSize: Double = 15.0
        const val bottom: Double = 28.0
        const val amountSize: Double = 56.0
    }

    object COPY {
        const val cardMeta: String = "Day 7 streak · added Oct 15"
        const val coinsWord: String = "coins"
        const val title: String = "Daily treat"
        const val sub: String = "Day 7 of your streak"
        const val hint: String = "Turn the knob a full circle"
        const val hintBusy: String = "Here it comes"
        const val amount: String = "+50"
        const val unit: String = "coins"
        const val prize: String = "Day 7 reward"
        const val collect: String = "Collect"
        const val knob: String = "Turn the knob"
        const val knobHint: String = "Dispenses today’s capsule"
        const val bag: String = "Rewards bag"
    }

    const val REPLICA: Boolean = false

    object REPLICA_ART {
        val wallFill = Color(0xFFD6C8DA)

        object globe {
            const val x: Double = 198.0
            const val y: Double = 341.0
            const val r: Double = 160.0
            const val floor: Double = 462.0
        }

        object knob {
            const val x: Double = 500.5 / 3.0
            const val y: Double = 1553.5 / 3.0
            const val size: Double = 169.0 / 3.0
            const val tilt: Double = -0.35
        }

        val knobMatrix = doubleArrayOf(
            0.87, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.97, 0.0, 0.0, 0.0,
            0.0, 0.0, 1.19, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0, 0.0,
        )

        object door {
            const val x: Double = 443.0 / 3.0
            const val y: Double = 1786.0 / 3.0
            const val w: Double = 295.0 / 3.0
            const val h: Double = 290.0 / 3.0
            const val r: Double = 16.0
            val top = Color(0xFFC27CE0)
            val bottom = Color(0xFFC583E1)
            val shade = gumballRgba(110, 50, 150, 0.35)
        }

        const val fruitBlur: Double = 1.2
        val pushGrade = doubleArrayOf(
            0.97, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.93, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.98, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0, 0.0,
        )
        const val defocus: Double = 1.2
        const val knobBlur: Double = 1.6
        const val fillBlur: Double = 0.0
        const val fruitPack: Double = 0.72
        val haze = gumballRgba(255, 255, 255, 0.10)

        object glare {
            const val r: Double = 138.0
            const val from: Double = 196.0
            const val sweep: Double = 62.0
            const val width: Double = 14.0
            const val blur: Double = 6.0
            val color = gumballRgba(255, 255, 255, 0.0)
        }

        object rim {
            const val width: Double = 3.0
            val color = gumballRgba(255, 255, 255, 0.0)
        }

        object jostle {
            const val amp: Double = 5.0
            const val freq: Double = 9.0
            const val decay: Double = 3.2
        }

        object pot {
            const val cx: Double = 201.0
            const val cy: Double = 420.0
            const val w: Double = 402.0
            const val h: Double = 1000.0 / 3.0
        }

        object potA {
            const val cx: Double = 201.0
            const val cy: Double = 450.0
            const val scale: Double = 1.0
            const val rot: Double = 0.0
        }

        object exit {
            const val x: Double = 199.0
            const val y: Double = 644.0
        }

        data class Fruit(val sprite: String, val radius: Double, val size: Double)

        val fruit = listOf(
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach-b", 40.0, 112.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach-b", 40.0, 112.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach-b", 40.0, 112.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach-b", 40.0, 112.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
            Fruit("peach-b", 40.0, 112.0),
            Fruit("peach", 38.0, 86.0),
            Fruit("passion", 24.0, 54.0),
        )
    }

    object UP {
        object panel {
            const val x: Double = 119.3
            const val y: Double = 315.7
            const val w: Double = 188.0
            const val h: Double = 218.0
            const val r: Double = 14.0
        }

        object card {
            const val x: Double = 41.0
            const val y: Double = 452.0
            const val w: Double = 320.0
            const val h: Double = 183.0
            const val r: Double = 10.0
        }

        object couponText {
            const val x: Double = 0.36
            const val y: Double = 0.2
            const val w: Double = 0.3
        }

        object stub {
            const val x: Double = 0.83
            const val y: Double = 0.5
        }

        const val blur: Double = 14.0
        const val tubBack: Double = 0.9
        const val peel: Double = -0.14
        val navy = Color(0xFF293267)
        const val scrim: Double = 0.35
        val scrimColor = Color(0xFF2A1840)
        val shadow = gumballRgba(70, 30, 100, 0.55)
        const val shadowY: Double = 640.0
        val sweep = gumballRgba(255, 255, 255, 0.45)
        const val sweepW: Double = 34.0
        const val ring: Double = 34.0
        val ringColor = gumballRgba(255, 240, 220, 0.9)
        const val confettiKinds: Int = 3

        object counter {
            const val top: Double = 12.0
            const val left: Double = 16.0
        }
    }
}

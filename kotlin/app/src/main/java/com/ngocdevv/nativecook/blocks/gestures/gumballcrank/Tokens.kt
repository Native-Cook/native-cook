package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import androidx.compose.ui.graphics.Color
import kotlin.math.PI

private fun tokenRgba(red: Int, green: Int, blue: Int, alpha: Double): Color {
    return Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
        alpha = alpha.toFloat(),
    )
}

/** Canonical values used by the shipped React Native upgrade selected by index.tsx. */
internal object Tokens {
    const val STAGE_WIDTH = 402.0
    const val REPLICA = false

    object COPY {
        const val cardMeta = "Day 7 streak · added Oct 15"
        const val coinsWord = "coins"
        const val amount = "+50"
        const val prize = "Day 7 reward"
        const val collect = "Collect"
        const val knob = "Turn the knob"
        const val knobHint = "Dispenses today’s capsule"
    }

    object SPACE {
        const val xs = 4.0
        const val sm = 8.0
        const val md = 12.0
        const val lg = 16.0
        const val xl = 24.0
        const val xxl = 32.0
    }

    object REPLICA_ART {
        val wallFill = Color(0xFFD6C8DA)

        object globe {
            const val x = 198.0
            const val y = 341.0
            const val r = 160.0
            const val floor = 462.0
        }

        object knob {
            const val x = 500.5 / 3.0
            const val y = 1553.5 / 3.0
            const val size = 169.0 / 3.0
            const val tilt = -0.35
        }

        val knobMatrix = doubleArrayOf(
            0.87, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.97, 0.0, 0.0, 0.0,
            0.0, 0.0, 1.19, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0, 0.0,
        )

        object door {
            const val x = 443.0 / 3.0
            const val y = 1786.0 / 3.0
            const val w = 295.0 / 3.0
            const val h = 290.0 / 3.0
            const val r = 16.0
            val top = Color(0xFFC27CE0)
            val bottom = Color(0xFFC583E1)
            val shade = tokenRgba(110, 50, 150, 0.35)
        }

        const val knobBlur = 1.6
        val haze = tokenRgba(255, 255, 255, 0.10)

        object glare {
            const val r = 138.0
            const val from = 196.0
            const val sweep = 62.0
            const val width = 14.0
            const val blur = 6.0
            val color = Color.Transparent
        }

        object rim {
            const val width = 3.0
            val color = Color.Transparent
        }

        object pot {
            const val cx = 201.0
            const val cy = 420.0
            const val w = 402.0
            const val h = 1000.0 / 3.0
        }

        object exit {
            const val x = 199.0
            const val y = 644.0
        }
    }

    object UP {
        object panel {
            const val x = 119.3
            const val y = 315.7
            const val w = 188.0
            const val h = 218.0
            const val r = 14.0
        }

        object card {
            const val x = 41.0
            const val y = 452.0
            const val w = 320.0
            const val h = 183.0
            const val r = 10.0
        }

        object couponText {
            const val x = 0.36
            const val y = 0.2
            const val w = 0.3
        }

        object stub {
            const val x = 0.83
            const val y = 0.5
        }

        const val blur = 14.0
        const val tubBack = 0.9
        const val peel = -0.14
        val navy = Color(0xFF293267)
        const val scrim = 0.35
        val scrimColor = Color(0xFF2A1840)
        val shadow = tokenRgba(70, 30, 100, 0.55)
        const val shadowY = 640.0
        val sweep = tokenRgba(255, 255, 255, 0.45)
        const val sweepW = 34.0
        const val ring = 34.0
        val ringColor = tokenRgba(255, 240, 220, 0.9)
        const val confettiKinds = 3

        object counter {
            const val top = SPACE.md
            const val left = SPACE.lg
        }
    }

    object UI {
        const val knobTarget = 72.0
        const val counterHeight = 36.0
        const val counterRadius = 18.0
        const val counterBackgroundAlpha = 0.72
        const val counterCoin = 20.0
        const val counterDigitBoxHeight = 22.0
        const val counterDigitSize = 16.0
        const val counterDigitLineHeight = 22.0
        const val counterDigitTracking = -0.2
        const val counterSlide = 25.0
        const val counterBump = 0.18
        const val counterEnterMillis = 140
        const val counterExitMillis = 120
        const val counterWordSize = 12.0
        const val counterWordLineHeight = 16.0
        const val counterWordAlpha = 0.55
        const val eyebrowSize = 10.0
        const val eyebrowLineHeight = 12.0
        const val eyebrowTracking = 1.6
        const val amountSize = 44.0
        const val amountLineHeight = 48.0
        const val amountTracking = -1.2
        const val metaSize = 11.0
        const val metaLineHeight = 14.0
        const val metaAlpha = 0.6
        const val buttonHeight = 44.0
        const val buttonRadius = 22.0
        const val buttonCoin = 18.0
        const val buttonTextSize = 16.0
        const val buttonTextLineHeight = 20.0
        const val buttonPressedScale = 0.97
        const val buttonPressMillis = 100
        const val buttonReleaseMillis = 160
        const val buttonCurveX1 = 0.23f
        const val buttonCurveY1 = 1f
        const val buttonCurveX2 = 0.32f
        const val buttonCurveY2 = 1f
        val ink = Color(0xFF3B2350)
        val eyebrow = Color(0xFFF6C27A)
        val button = Color(0xFFF2B26A)
        val navy = Color(0xFF293267)
    }

    object PORT {
        const val maxFrameDelta = 0.1
        const val touchResetDelay = 2.0
        const val returnDuration = 0.45
        const val loopTolerance = 1e-9
        const val hiddenX = -999.0
    }

    object RENDER {
        const val cameraBasisWidth = 1206f
        const val machineBase = 645f
        const val squashWidthFactor = 0.5f
        const val doorStroke = 6f
        const val doorInnerBlur = 5f
        const val globeClipInset = 4.0
        const val fillJostle = 0.05f
        const val sweepVisibleThreshold = -998.0
        const val sweepSkew = -0.4f
        const val sweepBlur = 10f
        const val ringStroke = 2f
        const val ringBlur = 2f
        const val shadowOpacity = 0.25f
        const val shadowStartScale = 0.3f
        const val shadowEndScale = 0.7f
        const val shadowBlur = 14f
        const val shadowHalfWidth = 120f
        const val shadowHalfHeight = 14f
        const val potFadeThreshold = 0.2
        const val stubCoinSize = 40f
        const val confettiSizeMultiplier = 7
        const val confettiSizeModulo = 9
        const val confettiSizeDivisions = 8.0
        const val confettiScale = 1.6f
        const val flyingCoinCounterX = 22.0
        const val flyingCoinCounterY = 18.0
        const val flyingCoinArcX = 40.0
        const val flyingCoinArcXStep = 6.0
        const val flyingCoinArcY = 120.0
        const val flyingCoinArcYStep = 8.0
        const val flyingCoinDiameter = 26f
        const val flyingCoinShrink = 0.45f
    }

    object Motion {
        const val STEP = 1.0 / 120.0
        const val MAX_STEPS = 30
        const val LOOP = 12_000L
        const val LOOP_MS = 12_000L
        const val RESUME_MS = 3_500L

        object CRANK {
            val detent = PI / 2.0
            val turn = PI * 2.0

            object overshoot {
                const val stiffness = 300.0
                const val damping = 14.0
                const val kick = 1.6
            }
        }

        object REDUCED {
            const val fade = 0.2
        }

        object REPLICA_CAMERA {
            val b = doubleArrayOf(1.117, -49.3, -18.1)
        }

        object KNOB {
            const val grab = 80.0
            const val friction = 2.2
            const val catchSpin = 0.8
            const val commitSpin = 3.0
            const val finish = 0.35
            const val maxSpin = 9.0
            const val press = 1.06
            const val backMax = 0.3
        }

        object UP_T {
            const val squash = 0.18
            val out = doubleArrayOf(0.18, 1.18)
            const val confetti = 0.35
            val morph = doubleArrayOf(1.7, 2.25)
            const val done = 2.4
            const val collect = 0.8
            const val coinFly = 0.45
            const val coinStagger = 0.04
            const val coins = 8
            val couponOut = doubleArrayOf(0.45, 0.8)
            const val back = 0.5
            const val sweep = 0.9
            const val idleSweep = 4.0
        }

        object CONFETTI {
            const val count = 24
            const val speed = 560.0
            const val spread = 1.2
            const val gravity = 1100.0
            const val life = 1.6
            val size = doubleArrayOf(8.0, 16.0)
            const val spin = 7.0
        }

        object COINS {
            const val from = 1290.0
            const val add = 50.0
            const val countSteps = 5
            const val countStep = 70.0
        }

        const val UP_LOOP_MS = 12_000L

        object UP_SCRIPT {
            val turn = doubleArrayOf(1.0, 2.5)
            val angle = PI * 2.0 + 0.2
            const val radius = 48.0
            const val collectAt = 7.8
        }

        const val QA_SPEED = 1.0
    }
}

package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Fixed-step bubble physics shared by autoplay and direct manipulation. */
internal class GlassDiscOnboardingSimulation(
    widthPx: Float,
    heightPx: Float,
    density: Float,
) {
    private var widthPx = widthPx
    private var heightPx = heightPx
    private var density = density
    private val count = GlassDiscOnboardingTheme.CLOUD.size
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val velocityX = FloatArray(count)
    private val velocityY = FloatArray(count)
    private val homeX = FloatArray(count)
    private val homeY = FloatArray(count)
    private val radius = FloatArray(count)
    private val scale = FloatArray(count)
    private val atlasCell = IntArray(count)
    private val order = IntArray(count)
    private var burstX = 0f
    private var burstY = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var fingerVelocityX = 0f
    private var fingerVelocityY = 0f
    private var fingerDown = false
    private var clockMillis = 0f
    private var remainderMillis = 0f

    var phase: Int = Hidden
        private set

    init {
        rebuildHomes()
    }

    fun resize(widthPx: Float, heightPx: Float, density: Float) {
        this.widthPx = widthPx
        this.heightPx = heightPx
        this.density = density
        rebuildHomes()
        hide()
    }

    fun launch(burstX: Float, burstY: Float, reducedMotion: Boolean) {
        phase = Out
        clockMillis = 0f
        remainderMillis = 0f
        this.burstX = burstX
        this.burstY = burstY
        for (index in 0 until count) {
            x[index] = if (reducedMotion) homeX[index] else burstX
            y[index] = if (reducedMotion) homeY[index] else burstY
            velocityX[index] = 0f
            velocityY[index] = 0f
            scale[index] = 0f
        }
    }

    fun showFinal() {
        phase = Out
        clockMillis = 0f
        remainderMillis = 0f
        fingerDown = false
        for (index in 0 until count) {
            x[index] = homeX[index]
            y[index] = homeY[index]
            velocityX[index] = 0f
            velocityY[index] = 0f
            scale[index] = 1f
        }
    }

    fun leave() {
        if (phase != Out) {
            return
        }
        phase = Leaving
        clockMillis = 0f
        fingerDown = false
    }

    fun hide() {
        phase = Hidden
        clockMillis = 0f
        remainderMillis = 0f
        fingerDown = false
        for (index in 0 until count) {
            scale[index] = 0f
            velocityX[index] = 0f
            velocityY[index] = 0f
        }
    }

    fun beginFinger(x: Float, y: Float) {
        fingerDown = true
        fingerX = x
        fingerY = y
        fingerVelocityX = 0f
        fingerVelocityY = 0f
    }

    fun moveFinger(x: Float, y: Float, velocityX: Float, velocityY: Float) {
        fingerX = x
        fingerY = y
        fingerVelocityX = velocityX
        fingerVelocityY = velocityY
    }

    fun endFinger() {
        fingerDown = false
    }

    /** Returns true when every leaving bubble has cleared the top edge. */
    fun step(elapsedMillis: Float, reducedMotion: Boolean): Boolean {
        if (phase == Hidden) {
            return false
        }
        remainderMillis += min(elapsedMillis, GlassDiscOnboardingMotion.STICKERS.maxFrameMs.toFloat())
        val dt = GlassDiscOnboardingMotion.STICKERS.step
        val stepMillis = dt * MillisPerSecond
        while (remainderMillis >= stepMillis - StepEpsilon) {
            remainderMillis -= stepMillis
            clockMillis += stepMillis
            if (phase == Leaving) {
                if (stepLeaving(stepMillis, dt, reducedMotion)) {
                    hide()
                    return true
                }
                continue
            }
            stepOut(stepMillis, dt, reducedMotion)
        }
        return false
    }

    fun snapshot(): List<GlassDiscOnboardingBubbleFrame> {
        if (phase == Hidden) {
            return emptyList()
        }
        return List(count) { index ->
            val speed = hypot(velocityX[index], velocityY[index])
            GlassDiscOnboardingBubbleFrame(
                index = index,
                x = x[index],
                y = y[index],
                radius = radius[index],
                scale = scale[index],
                atlasCell = atlasCell[index],
                velocityX = velocityX[index],
                velocityY = velocityY[index],
                wobble = min(
                    GlassDiscOnboardingMotion.UPGRADE.wobbleMax,
                    speed * GlassDiscOnboardingMotion.UPGRADE.wobblePerSpeed / density,
                ),
                rotationRad = atan2(velocityY[index], velocityX[index]),
                opacity = if (scale[index] > 0f) 1f else 0f,
            )
        }
    }

    private fun rebuildHomes() {
        for (index in 0 until count) {
            val source = GlassDiscOnboardingTheme.CLOUD[index]
            homeX[index] = source[0] / GlassDiscOnboardingTheme.CLOUD_FRAME.width * widthPx
            homeY[index] = source[1] / GlassDiscOnboardingTheme.CLOUD_FRAME.height * heightPx
            radius[index] = source[2] * density
            atlasCell[index] = source[3].toInt()
        }
        for (index in 0 until count) {
            var rank = 0
            for (other in 0 until count) {
                if (homeY[other] > homeY[index] || homeY[other] == homeY[index] && other < index) {
                    rank += 1
                }
            }
            order[index] = rank
        }
    }

    private fun stepLeaving(stepMillis: Float, dt: Float, reducedMotion: Boolean): Boolean {
        var visible = 0
        for (index in 0 until count) {
            if (scale[index] == 0f) {
                continue
            }
            if (reducedMotion) {
                scale[index] = max(0f, scale[index] - stepMillis / ReducedFadeMillis)
                if (scale[index] > 0f) {
                    visible += 1
                }
                continue
            }
            val start = (count - 1 - order[index]) * GlassDiscOnboardingMotion.STICKERS.exitStaggerMs
            if (clockMillis > start) {
                val goal = homeY[index] - heightPx * GlassDiscOnboardingMotion.STICKERS.exitLift
                velocityY[index] += (
                    GlassDiscOnboardingMotion.STICKERS.exitStiffness * (goal - y[index]) -
                        GlassDiscOnboardingMotion.STICKERS.exitDamping * velocityY[index]
                    ) * dt
                y[index] += velocityY[index] * dt
            }
            if (y[index] + radius[index] * scale[index] > -2f * density) {
                visible += 1
            }
        }
        return visible == 0
    }

    private fun stepOut(stepMillis: Float, dt: Float, reducedMotion: Boolean) {
        fingerVelocityX *= FingerVelocityDecay
        fingerVelocityY *= FingerVelocityDecay
        for (index in 0 until count) {
            if (reducedMotion) {
                scale[index] = min(1f, scale[index] + stepMillis / ReducedFadeMillis)
                continue
            }
            val start = GlassDiscOnboardingMotion.BURST.delayMs + order[index] * GlassDiscOnboardingMotion.BURST.staggerMs
            if (clockMillis < start) {
                continue
            }
            if (scale[index] == 0f) {
                launchBubble(index)
            }
            val travelled = hypot(x[index] - burstX, y[index] - burstY)
            val aged = (clockMillis - start) / GlassDiscOnboardingMotion.BURST.growMs
            val distanceGrowth = travelled / (GlassDiscOnboardingMotion.BURST.growOver * density)
            scale[index] = max(
                scale[index],
                min(1f, GlassDiscOnboardingMotion.BURST.startScale + max(distanceGrowth, aged)),
            )
            val floatOffset = sin(
                clockMillis / GlassDiscOnboardingMotion.STICKERS.floatPeriodMs * (PI * 2.0) + index * FloatPhaseStep,
            ).toFloat() * GlassDiscOnboardingMotion.STICKERS.float * density
            velocityX[index] += (
                GlassDiscOnboardingMotion.STICKERS.stiffness * (homeX[index] - x[index]) -
                    GlassDiscOnboardingMotion.STICKERS.damping * velocityX[index]
                ) * dt
            velocityY[index] += (
                GlassDiscOnboardingMotion.STICKERS.stiffness * (homeY[index] + floatOffset - y[index]) -
                    GlassDiscOnboardingMotion.STICKERS.damping * velocityY[index]
                ) * dt
            if (fingerDown) {
                pushFromFinger(index, dt)
            }
            x[index] += velocityX[index] * dt
            y[index] += velocityY[index] * dt
        }
        separateBubbles()
    }

    private fun launchBubble(index: Int) {
        val dx = homeX[index] - burstX
        val dy = homeY[index] - burstY
        val distance = max(1f, hypot(dx, dy))
        val launchSpeed = GlassDiscOnboardingMotion.BURST.launchSpeed * density
        velocityX[index] = dx / distance * launchSpeed
        velocityY[index] = dy / distance * launchSpeed
        scale[index] = GlassDiscOnboardingMotion.BURST.startScale
    }

    private fun pushFromFinger(index: Int, dt: Float) {
        val dx = x[index] - fingerX
        val dy = y[index] - fingerY
        val distance = max(MinimumDistance, hypot(dx, dy))
        val reach = GlassDiscOnboardingMotion.STICKERS.push * density + radius[index]
        if (distance >= reach) {
            return
        }
        val shove = (reach - distance) * FingerShove
        x[index] += dx / distance * shove
        y[index] += dy / distance * shove
        velocityX[index] += dx / distance * shove / dt * FingerImpulse
        velocityY[index] += dy / distance * shove / dt * FingerImpulse
        val proximity = 1f - distance / reach
        velocityX[index] += fingerVelocityX * GlassDiscOnboardingMotion.UPGRADE.fingerCarry * proximity * dt * FingerCarryRate
        velocityY[index] += fingerVelocityY * GlassDiscOnboardingMotion.UPGRADE.fingerCarry * proximity * dt * FingerCarryRate
    }

    private fun separateBubbles() {
        for (first in 0 until count) {
            if (scale[first] == 0f) {
                continue
            }
            for (second in first + 1 until count) {
                if (scale[second] == 0f) {
                    continue
                }
                val dx = x[second] - x[first]
                val dy = y[second] - y[first]
                val distance = max(MinimumDistance, hypot(dx, dy))
                val minimum = (
                    radius[first] * scale[first] + radius[second] * scale[second]
                    ) * GlassDiscOnboardingMotion.STICKERS.overlap
                if (distance >= minimum) {
                    continue
                }
                val push = (minimum - distance) / distance * GlassDiscOnboardingMotion.STICKERS.separation * 0.5f
                x[first] -= dx * push
                y[first] -= dy * push
                x[second] += dx * push
                y[second] += dy * push
            }
        }
    }

    companion object {
        const val Hidden = 0
        const val Out = 1
        const val Leaving = 2
        private const val MillisPerSecond = 1000f
        private const val StepEpsilon = 0.000001f
        private const val ReducedFadeMillis = 200f
        private const val FingerVelocityDecay = 0.9f
        private const val FloatPhaseStep = 1.7
        private const val MinimumDistance = 0.001f
        private const val FingerShove = 0.35f
        private const val FingerImpulse = 0.2f
        private const val FingerCarryRate = 8f
    }
}

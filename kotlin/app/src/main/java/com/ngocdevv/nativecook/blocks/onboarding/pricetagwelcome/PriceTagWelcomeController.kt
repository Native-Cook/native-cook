package com.ngocdevv.nativecook.blocks.onboarding.pricetagwelcome

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class PriceTagWelcomeController(
    now: Double,
    autoPlay: Boolean,
    reducedMotion: Boolean,
) {
    private companion object {
        const val AUTO = 0
        const val LIVE = 1
        const val RETURN = 2
    }

    private class Engine(
        var mode: Int,
        var autoPlay: Boolean,
        var still: Boolean,
        var autoStart: Double,
        var last: Double,
        var lastTouch: Double,
        var x: Double,
        var velocity: Double,
        var acceleration: Double,
        var dragging: Boolean,
        var dragX0: Double,
        var dragTranslation: Double,
        var theta: DoubleArray,
        var swingVelocity: DoubleArray,
        var frozeAt: DoubleArray,
        var thawAt: DoubleArray,
        var press: DoubleArray,
        var pressTarget: DoubleArray,
        var homeAt: Double,
        var homeFrom: Double,
        var homeTo: Double,
        var homeDuration: Double,
    )

    private var engine = createEngine(now, autoPlay, reducedMotion)
    private val mutableFrame = mutableStateOf(PriceTagWelcomeMotion.REST_FRAME, neverEqualPolicy())
    private val mutableFrozenMask = mutableIntStateOf(0)
    private val mutableOnScreenMask = mutableIntStateOf(PriceTagWelcomeMotion.visibleMask(0.0))
    val frame: State<PriceTagWelcomeMotion.Frame> = mutableFrame
    val frozenMask: State<Int> = mutableFrozenMask

    /** Bit i: item i is on screen, so its frost layers are composed. Changes only when an item crosses the edge. */
    val onScreenMask: State<Int> = mutableOnScreenMask
    val wake = Channel<Unit>(Channel.CONFLATED)
    var shouldTick = true
        private set

    init {
        wake.trySend(Unit)
    }

    private fun createEngine(now: Double, autoPlay: Boolean, still: Boolean): Engine {
        return Engine(
            mode = if (autoPlay && !still) AUTO else LIVE,
            autoPlay = autoPlay,
            still = still,
            autoStart = now,
            last = now,
            lastTouch = -1e9,
            x = 0.0,
            velocity = 0.0,
            acceleration = 0.0,
            dragging = false,
            dragX0 = 0.0,
            dragTranslation = 0.0,
            theta = DoubleArray(PriceTagWelcomeMotion.N),
            swingVelocity = DoubleArray(PriceTagWelcomeMotion.N),
            frozeAt = DoubleArray(PriceTagWelcomeMotion.N) { -1.0 },
            thawAt = DoubleArray(PriceTagWelcomeMotion.N) { -1.0 },
            press = DoubleArray(PriceTagWelcomeMotion.N),
            pressTarget = DoubleArray(PriceTagWelcomeMotion.N),
            homeAt = -1.0,
            homeFrom = 0.0,
            homeTo = 0.0,
            homeDuration = 0.0,
        )
    }

    fun setAutoPlay(autoPlay: Boolean, reducedMotion: Boolean, now: Double) {
        val current = engine
        if (reducedMotion) {
            val frozen = DoubleArray(PriceTagWelcomeMotion.N) { -1.0 }
            for (index in 0 until PriceTagWelcomeMotion.N) {
                if (isFrozen(current, index)) {
                    frozen[index] = now - 1.0
                }
            }
            val fresh = createEngine(now, autoPlay, true)
            fresh.mode = LIVE
            fresh.frozeAt = frozen
            engine = fresh
        } else if (autoPlay && current.mode == LIVE && !current.dragging) {
            current.autoPlay = true
            current.still = false
            current.lastTouch = now
        } else if (!autoPlay && current.mode == AUTO) {
            takeOver(now)
            engine.autoPlay = false
            engine.still = false
        } else {
            current.autoPlay = autoPlay
            current.still = false
        }
        publish(now)
        requestTick()
    }

    fun restartAutoPlay(now: Double) {
        val current = engine
        current.autoStart = now
        current.last = now
        current.dragging = false
        if (current.mode == AUTO) {
            publishFrame(PriceTagWelcomeMotion.frameAt(0.0))
        }
        requestTick()
    }

    fun tick(now: Double): Boolean {
        val moving = publish(now)
        shouldTick = moving
        return moving
    }

    fun touchDown(now: Double, hit: Int) {
        takeOver(now)
        engine.velocity = 0.0
        engine.pressTarget.fill(0.0)
        if (hit >= 0) {
            engine.pressTarget[hit] = 1.0
        }
        publish(now)
        requestTick()
    }

    fun dragBegin(now: Double) {
        takeOver(now)
        engine.dragging = true
        engine.dragX0 = engine.x
        engine.dragTranslation = 0.0
        engine.velocity = 0.0
        engine.pressTarget.fill(0.0)
        publish(now)
        requestTick()
    }

    fun dragMove(now: Double, translation: Double) {
        engine.dragTranslation = translation
        engine.lastTouch = now
        requestTick()
    }

    fun dragEnd(now: Double, velocity: Double) {
        engine.dragging = false
        engine.velocity = velocity
        engine.lastTouch = now
        engine.pressTarget.fill(0.0)
        requestTick()
    }

    fun tapItem(now: Double, index: Int): Boolean {
        takeOver(now)
        val current = engine
        if (isFrozen(current, index)) {
            current.thawAt[index] = now
        } else if (current.thawAt[index] >= 0.0 && !current.still) {
            val melt = PriceTagWelcomeMotion.clamp01(
                (now - current.thawAt[index]) / PriceTagWelcomeMotion.Freeze.melt,
            )
            val remaining = PriceTagWelcomeMotion.clamp01(1.0 - (melt - 0.16) / 0.62)
            val rewind = (1.0 - Math.cbrt(1.0 - remaining)) * PriceTagWelcomeMotion.Freeze.grow
            current.frozeAt[index] = now - rewind
            current.thawAt[index] = -1.0
        } else {
            current.frozeAt[index] = now
            current.thawAt[index] = -1.0
            if (!current.still) {
                current.swingVelocity[index] += PriceTagWelcomeMotion.Freeze.kick
            }
        }
        current.pressTarget.fill(0.0)
        publish(now)
        requestTick()
        return isFrozen(current, index)
    }

    fun touchCancel(now: Double) {
        engine.pressTarget.fill(0.0)
        engine.lastTouch = now
        requestTick()
    }

    fun hitItem(x: Double, y: Double): Int {
        for (index in 0 until PriceTagWelcomeMotion.N) {
            val item = PriceTagWelcomeTheme.items[index]
            val originX = PriceTagWelcomeMotion.wrapX(item.x, engine.x)
            val tag = item.tag
            val inSticker = x >= originX + 12.0 &&
                x <= originX + item.w - 12.0 &&
                y >= item.y + 12.0 &&
                y <= item.y + item.h - 12.0
            val tagX = tag.cx - item.x + originX
            val inTag = abs(x - tagX) <= tag.w / 2.0 + 12.0 &&
                abs(y - tag.cy) <= tag.h / 2.0 + 12.0
            if (inSticker || inTag) {
                return index
            }
        }
        return -1
    }

    private fun requestTick() {
        shouldTick = true
        wake.trySend(Unit)
    }

    private fun isFrozen(current: Engine, index: Int): Boolean {
        return current.frozeAt[index] >= 0.0 && current.thawAt[index] < 0.0
    }

    private fun takeOver(now: Double) {
        val current = engine
        if (current.mode == LIVE) {
            current.lastTouch = now
            return
        }
        if (current.mode == RETURN) {
            current.mode = LIVE
            current.homeAt = -1.0
            current.lastTouch = now
            return
        }

        val time = PriceTagWelcomeMotion.mod(now - current.autoStart, PriceTagWelcomeMotion.LOOP)
        for (index in 0 until PriceTagWelcomeMotion.N) {
            val swing = PriceTagWelcomeMotion.scriptSwing(index, time)
            current.theta[index] = swing.theta
            current.swingVelocity[index] = swing.velocity
            val frost = PriceTagWelcomeMotion.scriptFrost(index, time)
            current.frozeAt[index] = if (frost.freezeTime < 0.0) -1.0 else now - frost.freezeTime
            current.thawAt[index] = if (frost.meltTime < 0.0) -1.0 else now - frost.meltTime
            current.press[index] = PriceTagWelcomeMotion.scriptPress(index, time)
        }
        current.mode = LIVE
        current.lastTouch = now
        current.last = now
        current.x = 0.0
        current.velocity = 0.0
        current.acceleration = 0.0
        current.pressTarget.fill(0.0)
    }

    private fun publish(now: Double): Boolean {
        val current = engine
        if (current.mode == AUTO) {
            val time = PriceTagWelcomeMotion.mod(now - current.autoStart, PriceTagWelcomeMotion.LOOP)
            current.last = now
            publishFrame(PriceTagWelcomeMotion.frameAt(time))
            return true
        }

        val delta = min(max(now - current.last, 0.0), 1.0 / 20.0)
        current.last = now
        var heavy = false
        for (index in 0 until PriceTagWelcomeMotion.N) {
            val thawDuration = if (current.still) {
                PriceTagWelcomeMotion.STILL_FADE
            } else {
                PriceTagWelcomeMotion.Freeze.melt
            }
            if (current.thawAt[index] >= 0.0 && now - current.thawAt[index] >= thawDuration) {
                current.frozeAt[index] = -1.0
                current.thawAt[index] = -1.0
            }
            if (isFrozen(current, index)) {
                heavy = true
            }
        }

        val oldVelocity = current.velocity
        if (current.dragging) {
            val x = current.dragX0 + current.dragTranslation
            val measuredVelocity = if (delta > 0.0) {
                (x - current.x) / delta
            } else {
                current.velocity
            }
            current.velocity = PriceTagWelcomeMotion.lerp(
                current.velocity,
                measuredVelocity,
                if (delta > 0.0) 1.0 - exp(-delta / 0.03) else 0.0,
            )
            current.x = x
        } else if (current.mode == RETURN) {
            val progress = PriceTagWelcomeMotion.segment(
                now,
                current.homeAt,
                current.homeAt + current.homeDuration,
            )
            val x = PriceTagWelcomeMotion.lerp(
                current.homeFrom,
                current.homeTo,
                PriceTagWelcomeMotion.easeInOut(progress),
            )
            current.velocity = if (delta > 0.0) (x - current.x) / delta else 0.0
            current.x = x
        } else if (current.velocity != 0.0) {
            val decay = if (heavy) {
                PriceTagWelcomeMotion.DECAY_TAU * PriceTagWelcomeMotion.Freeze.drag
            } else {
                PriceTagWelcomeMotion.DECAY_TAU
            }
            current.velocity = if (abs(current.velocity) < 4.0) {
                0.0
            } else {
                current.velocity * exp(-delta / decay)
            }
            current.x += current.velocity * delta
        }
        val rawAcceleration = if (delta > 0.0) {
            (current.velocity - oldVelocity) / delta
        } else {
            0.0
        }
        current.acceleration = PriceTagWelcomeMotion.lerp(
            current.acceleration,
            rawAcceleration,
            if (delta > 0.0) 1.0 - exp(-delta / 0.025) else 0.0,
        )

        val substeps = PriceTagWelcomeMotion.substepCount(delta)
        val substep = delta / substeps
        for (index in 0 until PriceTagWelcomeMotion.N) {
            val freezeTime = if (current.frozeAt[index] < 0.0) {
                -1.0
            } else {
                now - current.frozeAt[index]
            }
            val meltTime = if (current.thawAt[index] < 0.0) {
                -1.0
            } else {
                now - current.thawAt[index]
            }
            if (current.still) {
                current.theta[index] = 0.0
                current.swingVelocity[index] = 0.0
            } else if (PriceTagWelcomeMotion.lockedBy(freezeTime, meltTime)) {
                current.swingVelocity[index] = 0.0
            } else {
                repeat(substeps) {
                    val swing = PriceTagWelcomeMotion.swingStep(
                        current.theta[index],
                        current.swingVelocity[index],
                        index,
                        current.acceleration,
                        substep,
                    )
                    current.theta[index] = swing.theta
                    current.swingVelocity[index] = swing.velocity
                }
            }
            val target = current.pressTarget[index]
            val duration = if (target > current.press[index]) {
                PriceTagWelcomeMotion.Press.pressIn
            } else {
                PriceTagWelcomeMotion.Press.pressOut
            }
            current.press[index] = PriceTagWelcomeMotion.lerp(
                current.press[index],
                target,
                1.0 - exp(-delta * 3.0 / duration),
            )
        }

        if (current.autoPlay &&
            !current.still &&
            current.mode == LIVE &&
            !current.dragging &&
            now - current.lastTouch > PriceTagWelcomeMotion.RESUME_AFTER &&
            abs(current.velocity) < 40.0
        ) {
            // JavaScript Math.round resolves exact halves toward positive infinity.
            val periods = floor(current.x / PriceTagWelcomeTheme.PERIOD + 0.5)
            val home = periods * PriceTagWelcomeTheme.PERIOD
            current.mode = RETURN
            current.homeAt = now
            current.homeFrom = current.x
            current.homeTo = home
            current.homeDuration = min(
                PriceTagWelcomeMotion.Home.max,
                PriceTagWelcomeMotion.Home.base +
                    PriceTagWelcomeMotion.Home.perPx * abs(current.x - home),
            )
            current.velocity = 0.0
        }
        if (current.mode == RETURN) {
            for (index in 0 until PriceTagWelcomeMotion.N) {
                if (isFrozen(current, index) &&
                    now >= current.homeAt + PriceTagWelcomeMotion.Home.thawStagger * index
                ) {
                    current.thawAt[index] = now
                }
            }
        }
        if (current.mode == RETURN && now >= current.homeAt + current.homeDuration) {
            // A whole-period offset draws the rest frame while secondary motion settles.
            current.x = current.homeTo
            current.velocity = 0.0
            var settled = true
            for (index in 0 until PriceTagWelcomeMotion.N) {
                if (abs(current.theta[index]) > 0.002 ||
                    abs(current.swingVelocity[index]) > 0.02 ||
                    current.frozeAt[index] >= 0.0
                ) {
                    settled = false
                }
            }
            if (settled) {
                engine = createEngine(now, true, false)
                publishFrame(PriceTagWelcomeMotion.frameAt(0.0))
                return true
            }
        }

        val theta = DoubleArray(PriceTagWelcomeMotion.N)
        val freezeTime = DoubleArray(PriceTagWelcomeMotion.N)
        val meltTime = DoubleArray(PriceTagWelcomeMotion.N)
        for (index in 0 until PriceTagWelcomeMotion.N) {
            theta[index] = PriceTagWelcomeMotion.softDegrees(current.theta[index])
            freezeTime[index] = if (current.frozeAt[index] < 0.0) {
                -1.0
            } else {
                now - current.frozeAt[index]
            }
            meltTime[index] = if (current.thawAt[index] < 0.0) {
                -1.0
            } else {
                now - current.thawAt[index]
            }
        }
        val glint = PriceTagWelcomeMotion.glintOf(current.x, current.velocity)
        publishFrame(
            PriceTagWelcomeMotion.Frame(
                x = current.x,
                velocity = current.velocity,
                thetaDegrees = theta,
                freezeTime = freezeTime,
                meltTime = meltTime,
                press = current.press.copyOf(),
                clock = if (current.still) 0.0 else PriceTagWelcomeMotion.mod(now, 1000.0),
                glint = glint.first,
                glintAlpha = glint.second,
            ),
        )
        return current.mode != LIVE || anyMoving(current) || current.autoPlay && !current.still
    }

    private fun publishFrame(next: PriceTagWelcomeMotion.Frame) {
        mutableFrame.value = next
        var mask = 0
        for (index in next.freezeTime.indices) {
            if (next.freezeTime[index] >= 0.0) {
                mask = mask or (1 shl index)
            }
        }
        if (mutableFrozenMask.intValue != mask) {
            mutableFrozenMask.intValue = mask
        }
        val visible = PriceTagWelcomeMotion.visibleMask(next.x)
        if (mutableOnScreenMask.intValue != visible) {
            mutableOnScreenMask.intValue = visible
        }
    }

    private fun anyMoving(current: Engine): Boolean {
        if (current.dragging || abs(current.velocity) > 2.0) {
            return true
        }
        for (index in 0 until PriceTagWelcomeMotion.N) {
            if (abs(current.theta[index]) > 0.0015 ||
                abs(current.swingVelocity[index]) > 0.01
            ) {
                return true
            }
            if (abs(current.press[index] - current.pressTarget[index]) > 0.002) {
                return true
            }
            if (current.frozeAt[index] >= 0.0) {
                return true
            }
        }
        return false
    }
}

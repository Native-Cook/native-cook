package com.ngocdevv.nativecook.blocks.playground.jellygooglymascot

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos

internal const val LOOP = 8.0
private val jellyGooglyMascotPokes = doubleArrayOf(1.6, 4.3, 7.0)

internal data class JellyGooglyMascotTarget(
    val x: Double,
    val y: Double,
    val px: Double,
    val py: Double,
)

internal data class JellyGooglyMascotPoke(
    val squash: Double,
    val blink: Double,
    val flavour: Double,
)

private fun jellyGooglyMascotSmooth(value: Double): Double {
    val u = value.coerceIn(0.0, 1.0)
    return u * u * (3.0 - 2.0 * u)
}

internal fun jellyGooglyMascotGhostAt(t: Double): Pair<Double, Double> {
    val a = t / LOOP * PI * 2.0
    return Pair(
        150.0 * sin(a) + 30.0 * sin(3.0 * a),
        -40.0 + 150.0 * sin(2.0 * a) * 0.8 + 40.0 * cos(a),
    )
}

internal fun jellyGooglyMascotPokeAt(t: Double): JellyGooglyMascotPoke {
    var squash = 0.0
    var blink = 0.0
    var flavour = 0.0
    for (poke in jellyGooglyMascotPokes) {
        val x = t - poke
        if (x > 0.0 && x < 1.6) squash += 0.16 * exp(-4.0 * x) * sin(13.0 * x)
        if (x > 0.0 && x < 0.18) blink = max(blink, sin(PI * x / 0.18))
        flavour += jellyGooglyMascotSmooth(x / 0.3)
    }
    val wrap = t + LOOP - jellyGooglyMascotPokes[2]
    if (wrap > 0.0 && wrap < 1.6) squash += 0.16 * exp(-4.0 * wrap) * sin(13.0 * wrap)
    return JellyGooglyMascotPoke(squash, blink, flavour % 3.0)
}

/**
 * Eight-second closed ghost path; pokes at 1.6, 4.3 and 7.0 seconds squash for 1.6 seconds,
 * blink for 0.18 seconds and crossfade flavour for 0.3 seconds. Manual input holds for two
 * seconds, then rejoins the ghost over 0.6 seconds, matching the React Native reference.
 */
internal class JellyGooglyMascotMotion {
    var elapsed = 0.0
        private set
    var clock = 0.0
        private set
    var manual = false
        private set
    var touching = false
        private set
    var target = JellyGooglyMascotTarget(0.0, -40.0, 0.0, -40.0)
        private set
    var poke = JellyGooglyMascotPoke(0.0, 0.0, 0.0)
        private set

    private var origin = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var lastTouch = -10.0
    private var userPoke = -10.0
    private var userFlavour = 0.0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val now = elapsed
        val t = if (autoPlay) ((now - origin) % LOOP + LOOP) % LOOP else 0.0
        clock = t
        val ghost = if (autoPlay) jellyGooglyMascotGhostAt(t) else Pair(0.0, -40.0)
        val pupilGhost = if (autoPlay) jellyGooglyMascotGhostAt((t - 0.12 + LOOP) % LOOP) else ghost
        val auto = jellyGooglyMascotPokeAt(t)
        if (manual) {
            val since = now - lastTouch
            val back = if (touching) 0.0 else jellyGooglyMascotSmooth((since - 2.0) / 0.6)
            val k = 1.0 - exp(-14.0 * dt)
            val px = target.px + (fingerX - target.px) * k
            val py = target.py + (fingerY - target.py) * k
            target = JellyGooglyMascotTarget(
                fingerX + (ghost.first - fingerX) * back,
                fingerY + (ghost.second - fingerY) * back,
                px + (pupilGhost.first - px) * back,
                py + (pupilGhost.second - py) * back,
            )
            val x = now - userPoke
            val squash = if (x > 0.0 && x < 1.6) 0.16 * exp(-4.0 * x) * sin(13.0 * x) else 0.0
            val blink = if (x > 0.0 && x < 0.18) sin(PI * x / 0.18) else 0.0
            val flavour = (userFlavour + jellyGooglyMascotSmooth(x / 0.3)) % 3.0
            poke = JellyGooglyMascotPoke(squash, blink, if (back >= 1.0) auto.flavour else flavour)
            if (back >= 1.0) {
                manual = false
                val shown = floor(flavour + 0.5).toInt() % 3
                val starts = doubleArrayOf(0.0, jellyGooglyMascotPokes[0] + 0.4, jellyGooglyMascotPokes[1] + 0.4)
                origin = now - starts[shown]
            }
            return
        }
        target = JellyGooglyMascotTarget(ghost.first, ghost.second, pupilGhost.first, pupilGhost.second)
        poke = if (autoPlay) auto else JellyGooglyMascotPoke(0.0, 0.0, 0.0)
    }

    private fun takeOver() {
        if (!manual) {
            manual = true
            userFlavour = floor(poke.flavour + 0.5) % 3.0
            userPoke = -10.0
        }
        lastTouch = elapsed
    }

    fun begin(x: Double, y: Double) {
        takeOver()
        touching = true
        fingerX = x
        fingerY = y
    }

    fun move(x: Double, y: Double) {
        fingerX = x
        fingerY = y
        lastTouch = elapsed
    }

    fun end() {
        touching = false
        lastTouch = elapsed
    }

    fun tap(x: Double, y: Double) {
        takeOver()
        fingerX = x
        fingerY = y
        val sincePoke = elapsed - userPoke
        userFlavour = (userFlavour + if (sincePoke > 0.0 && sincePoke < 10.0) 1.0 else 0.0) % 3.0
        userPoke = elapsed
    }
}

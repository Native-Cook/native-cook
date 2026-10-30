package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.frameAt
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.RESUME
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.follow
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.liveHome
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.TAU
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.wobble
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.seg
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.easeInOut

/** The seven foreground bodies and six stars share the exact two-substep touch simulation. */
internal class GhostFloatWelcomeDriver(initialTime: Double = 0.0) {
    var t = initialTime
    var mode = 0
    var last = -10.0
    var amb = initialTime
    val ox = DoubleArray(13)
    val oy = DoubleArray(13)
    val vx = DoubleArray(13)
    val vy = DoubleArray(13)
    val or = DoubleArray(13)
    val vr = DoubleArray(13)
    var down = 0
    var fx = 0.0
    var fy = 0.0
    var fvx = 0.0
    var fvy = 0.0
    var grab = -1
    var gdx = 0.0
    var gdy = 0.0
    var booAt = -1.0
    var rollAt = -1.0
    var agree = -1.0
    var agreeAim = 0.0
    var btnPress = 0.0
    var btnAim = 0.0
    var noAt = -1.0
    var lookX = 0.0
    var lookY = 0.0

    fun touched(now: Double) {
        last = now
        mode = 2
    }

    fun toggleAgree() {
        val cur = if (agree >= 0) agree else frameAt(t).tick
        agree = cur
        agreeAim = if (cur > 0.5) 0.0 else 1.0
    }

    fun step(dt: Double, now: Double, auto: Boolean) {
        if (mode == 2) {
            if (auto && down == 0 && now - last >= RESUME) {
                mode = 0
                agree = -1.0
            }
        } else if (auto) t = (t + dt) % LOOP
        amb = (amb + dt) % LOOP
        if (agree >= 0) agree = follow(agree, agreeAim, 16.0, dt)
        btnPress = follow(btnPress, btnAim, if (btnAim > 0) 40.0 else 16.0, dt)
        val spring = 38.0
        val damp = 2 * 0.5 * sqrt(spring)
        val h = dt / 2
        repeat(2) {
            for (j in 0 until 13) {
                val home = liveHome(j, t, amb)
                val x = home.x + ox[j]
                val y = home.y + oy[j]
                if (j == grab) {
                    val tx = fx - gdx - home.x
                    val ty = fy - gdy - home.y
                    vx[j] = (tx - ox[j]) / max(h, 0.004) * 0.35
                    vy[j] = (ty - oy[j]) / max(h, 0.004) * 0.35
                    ox[j] += (tx - ox[j]) * 0.35
                    oy[j] += (ty - oy[j]) * 0.35
                    vr[j] += (fvx * 0.08 - vr[j]) * 0.2
                    or[j] += vr[j] * h
                    continue
                }
                var ax = -spring * ox[j] - damp * vx[j]
                var ay = -spring * oy[j] - damp * vy[j]
                if (down > 0 && grab < 0) {
                    val dx = x - fx
                    val dy = y - fy
                    val dist = hypot(dx, dy).let {
                        if (it == 0.0) 1.0 else it
                    }
                    if (dist < 150) {
                        val w = (1 - dist / 150).pow(2)
                        val speed = min(1600.0, hypot(fvx, fvy))
                        val tx = -dy / dist
                        val ty = dx / dist
                        val side = if (fvx * tx + fvy * ty >= 0) 1 else -1
                        val gain = 5.5 / home.mass
                        ax += w * gain * (fvx * 0.9 + tx * speed * 0.7 * side)
                        ay += w * gain * (fvy * 0.9 + ty * speed * 0.7 * side)
                        val near = home.r + 26
                        if (dist < near) {
                            ax += dx / dist * (near - dist) * 260
                            ay += dy / dist * (near - dist) * 260
                        }
                        vr[j] += side * w * speed * 0.04 * h * 60
                    }
                }
                vx[j] += ax * h
                vy[j] += ay * h
                val speed = hypot(vx[j], vy[j])
                if (speed > 2400) {
                    vx[j] *= 2400 / speed
                    vy[j] *= 2400 / speed
                }
                ox[j] += vx[j] * h
                oy[j] += vy[j] * h
                vr[j] += (-30 * or[j] - 2 * 0.45 * sqrt(30.0) * vr[j]) * h
                or[j] += vr[j] * h
                val px = home.x + ox[j]
                val py = home.y + oy[j]
                val radius = home.r
                if (px < radius * 0.4 && vx[j] < 0) vx[j] *= -0.6
                if (px > 402 - radius * 0.4 && vx[j] > 0) vx[j] *= -0.6
                if (py < 60 + radius && vy[j] < 0) vy[j] *= -0.6
                if (py > GhostFloatWelcomeTheme.fieldBottom - radius && vy[j] > 0) vy[j] *= -0.6
            }
            for (a in 0 until 7) for (b in a + 1 until 7) {
                val ha = liveHome(a, t, amb)
                val hb = liveHome(b, t, amb)
                val dx = hb.x + ox[b] - (ha.x + ox[a])
                val dy = hb.y + oy[b] - (ha.y + oy[a])
                val dist = hypot(dx, dy).let {
                    if (it == 0.0) 1.0 else it
                }
                val minimum = ha.r + hb.r + 3
                if (dist >= minimum) continue
                val force = min(9000.0, (minimum - dist) * 420) * h
                val ux = dx / dist
                val uy = dy / dist
                if (a != grab) {
                    vx[a] -= ux * force
                    vy[a] -= uy * force
                }
                if (b != grab) {
                    vx[b] += ux * force
                    vy[b] += uy * force
                }
            }
        }
        var lx = 0.35 * sin(TAU * amb / LOOP)
        var ly = 0.25 * sin(TAU * 2 * amb / LOOP + 1)
        val ghost = liveHome(0, t, amb)
        val gx = ghost.x + ox[0]
        val gy = ghost.y + oy[0]
        if (down > 0 && grab != 0) {
            lx = (fx - gx) / 120
            ly = (fy - gy) / 120
        } else {
            var best = 260.0
            for (j in 1 until 7) {
                val speed = hypot(vx[j], vy[j])
                if (speed > best) {
                    best = speed
                    val home = liveHome(j, t, amb)
                    lx = (home.x + ox[j] - gx) / 120
                    ly = (home.y + oy[j] - gy) / 120
                }
            }
        }
        val length = hypot(lx, ly)
        if (length > 1) {
            lx /= length
            ly /= length
        }
        lookX = follow(lookX, lx, 9.0, dt)
        lookY = follow(lookY, ly, 9.0, dt)
    }

    fun boo(now: Double) {
        booAt = now
        val ghost = liveHome(0, t, amb)
        val gx = ghost.x + ox[0]
        val gy = ghost.y + oy[0]
        for (j in 1 until 13) {
            val home = liveHome(j, t, amb)
            val dx = home.x + ox[j] - gx
            val dy = home.y + oy[j] - gy
            val dist = hypot(dx, dy).let {
                if (it == 0.0) 1.0 else it
            }
            val strength = max(160.0, 900 - dist * 1.6) / (if (j < 7) 1.0 else 0.6)
            vx[j] += dx / dist * strength
            vy[j] += dy / dist * strength
            vr[j] += (if (j % 2 == 0) 1 else -1) * 260
        }
    }

    data class Extra(val puff: Double = 0.0, val ring: Double = -1.0, val wide: Double = 0.0, val roll: Double = 0.0, val hop: Double = 0.0, val no: Double = 0.0, val noP: Double = -1.0)

    fun extra(now: Double): Extra {
        var puff = 0.0
        var ring = -1.0
        var wide = 0.0
        var roll = 0.0
        var hop = 0.0
        var no = 0.0
        if (booAt >= 0) {
            val p = now - booAt
            if (p > 1.6) booAt = -1.0 else {
                puff = wobble(p, 0.34, 0.24)
                ring = if (p < 0.65) p / 0.65 else -1.0
                wide = 1 - seg(p, 0.3, 0.9)
            }
        }
        if (rollAt >= 0) {
            val p = (now - rollAt) / 0.75
            if (p >= 1) rollAt = -1.0 else {
                roll = 360 * easeInOut(p)
                hop = 22 * sin(PI * p)
            }
        }
        if (noAt >= 0) {
            val p = now - noAt
            if (p > 0.6) noAt = -1.0 else no = exp(-p / 0.16) * sin(TAU * p / 0.12)
        }
        return Extra(puff, ring, wide, roll, hop, no, if (noAt >= 0) (now - noAt) / 0.6 else -1.0)
    }
}


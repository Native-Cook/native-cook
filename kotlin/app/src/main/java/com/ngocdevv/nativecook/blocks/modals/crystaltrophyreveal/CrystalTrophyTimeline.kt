package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import kotlin.math.*

internal object CrystalTrophyTimeline {
    const val TAU = PI * 2
    data class Spring(val x: Double, val v: Double)

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double {
        return max(low, min(high, value))
    }

    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val p = clamp(x)
        if (p == 0.0 || p == 1.0) return p

        var lo = 0.0
        var hi = 1.0
        var t = p
        for (i in 0 until 14) {
            t = (lo + hi) * 0.5
            val u = 1.0 - t
            val bx = 3.0 * u * u * t * x1 + 3.0 * u * t * t * x2 + t * t * t
            if (bx < p) lo = t
            else hi = t
        }
        return 3.0 * (1.0 - t) * (1.0 - t) * t * y1 + 3.0 * (1.0 - t) * t * t * y2 + t * t * t
    }

    fun enter(x: Double): Double {
        return bezier(x, 0.23, 1.0, 0.32, 1.0)
    }

    fun travel(x: Double): Double {
        return bezier(x, 0.77, 0.0, 0.175, 1.0)
    }

    fun spring(x: Double, v: Double, target: Double, stiffness: Double, damping: Double, dt: Double): Spring {
        val a = damping * 0.5
        val w = sqrt(max(0.0001, stiffness - a * a))
        val decay = exp(-a * dt)
        val c = cos(w * dt)
        val sn = sin(w * dt)
        val y = x - target
        val b = (v + a * y) / w
        return Spring(x = target + decay * (y * c + b * sn), v = decay * (v * c - (a * b + w * y) * sn))
    }

    fun pulse(age: Double, tail: Double = 0.52): Double {
        if (age < 0.0 || age > 0.06 + tail) return 0.0

        return (if (age < 0.06) enter(age / 0.06) else 1.0 - enter((age - 0.06) / tail))
    }

    fun interrupt(s: CrystalTrophyState, down: Boolean): Unit {
        s.paused = s.auto
        s.recovering = false
        s.realDown = down
        s.lastInput = s.clock
        if (down) {
            s.touchFrom = s.touch
            s.releaseAge = 0.0
            s.down = false
            s.shareDown = false
            s.closeDown = false
            s.spin = false
        }
    }

    fun reveal(s: CrystalTrophyState): Unit {
        if (s.phase != "closed") return

        s.phase = "revealing"
        s.phaseTime = 0.0
        s.startOpen = s.open
        s.started = true
        s.seated = false
        s.cameraY = 0.0
        s.cameraV = 0.0
        s.titleTime = -1.0
        s.ui = 0.0
        s.hint = 0.0
        s.events = s.events or 1
    }

    fun close(s: CrystalTrophyState): Unit {
        if (s.phase != "open" && s.phase != "revealing") return

        s.startCamera = s.cameraScale
        s.phase = "closing"
        s.phaseTime = 0.0
        s.startOpen = s.open
        s.startRise = s.rise
        s.startLamp = s.lamp
        s.startYaw = atan2(sin(s.yaw), cos(s.yaw))
        s.startUi = s.ui
        s.down = false
        s.spin = false
        s.yawV = 0.0
        s.shareDown = false
    }

    fun touchDown(s: CrystalTrophyState, x: Double, y: Double): Unit {
        if (s.phase != "open" && s.phase != "closed") return

        s.touchFrom = s.touch
        s.down = true
        s.downAt = s.clock
        s.downX = x
        s.downY = y
        s.dragBase = s.yaw
        s.restTarget = floor(s.yaw / TAU + 0.5) * TAU
        s.maxDrag = 0.0
        s.crossed = abs(s.yaw - s.restTarget) > 0.52
        s.spin = false
        s.yawV = 0.0
        s.touchX = clamp(x, -0.86, 0.86)
        s.touchY = clamp(y, 0.0, 2.9)
    }

    fun dragTo(s: CrystalTrophyState, dx: Double, dy: Double, x: Double, y: Double, width: Double): Unit {
        if (!s.down) return

        s.maxDrag = max(s.maxDrag, sqrt(dx * dx + dy * dy))
        s.touchX = clamp(x, -0.86, 0.86)
        s.touchY = clamp(y, 0.0, 2.9)
        if (s.phase == "closed") {
            if (s.reduced) return

            val pull = abs(dx) / (width * 0.5)
            s.open = (if (pull < 0.22) pull else min(0.32, 0.22 + (pull - 0.22) / (1.0 + (pull - 0.22) * 10.0)))
        } else if (s.phase == "open") {
            val base = s.dragBase - s.restTarget
            val raw = base + dx * 0.0062
            val extra = max(0.0, abs(raw) - 0.52)
            val baseExtra = max(0.0, abs(base) - 0.52)
            val baseBand = sign(base) * (min(abs(base), 0.52) + baseExtra / (1.0 + baseExtra * 3.2))
            val band = sign(raw) * (min(abs(raw), 0.52) + extra / (1.0 + extra * 3.2))
            s.yaw = s.dragBase + band - baseBand
            if (extra > 0.0 && !s.crossed) {
                s.crossed = true
                s.events = s.events or 16
            }
        }
    }

    fun tapAt(s: CrystalTrophyState, x: Double, y: Double): Unit {
        if (s.phase != "open") return

        s.touchX = clamp(x, -0.86, 0.86)
        s.touchY = clamp(y, 0.0, 2.9)
        s.flashAge = 0.0
        s.yawV += (if (s.reduced) 0.0 else ((if (x < 0.0) -0.6 else 0.6)))
        s.events = s.events or 4
    }

    fun release(s: CrystalTrophyState, velocity: Double, cancelled: Boolean = false): Unit {
        if (!s.down) return

        s.touchFrom = s.touch
        s.down = false
        s.lastInput = s.clock
        s.releaseAge = 0.0
        s.releaseYaw = s.yaw
        if (s.phase == "closed") {
            if (!cancelled && (s.maxDrag < 8.0 || s.open > 0.12 || abs(velocity) > 600.0)) reveal(s)

            return
        }
        if (s.phase != "open") return

        if (!cancelled && s.clock - s.downAt <= 0.25 && s.maxDrag < 8.0) {
            tapAt(s, s.touchX, s.touchY)
            return
        }
        s.yawV = (if (cancelled) 0.0 else velocity * 0.0062)
        if (!s.reduced && !cancelled && abs(velocity) > 900.0) {
            val sign = sign(velocity)
            s.spin = true
            s.caught = false
            s.frontReported = false
            s.spinStart = s.yaw
            s.spinTarget = (floor(s.yaw / TAU + 0.5) + sign) * TAU
            s.yawV = sign * max(10.8, min(16.0, abs(s.yawV)))
        }
    }

    fun pressShare(s: CrystalTrophyState, down: Boolean, activate: Boolean = false, x: Double = 0.5, y: Double = 0.5): Unit {
        if (down && !s.shareDown) s.sharePressAge = 0.0

        if (down) {
            s.shareX = clamp(x)
            s.shareY = clamp(y)
        }
        s.shareDown = down && s.phase == "open"
        if (activate && s.phase == "open") {
            s.shareAge = 0.0
            s.events = s.events or (4 or 64)
        }
    }

    fun pressClose(s: CrystalTrophyState, down: Boolean, activate: Boolean = false, x: Double = 0.5, y: Double = 0.5): Unit {
        if (down && !s.closeDown) s.closePressAge = 0.0

        if (down) {
            s.closeX = clamp(x)
            s.closeY = clamp(y)
        }
        s.closeDown = down
        if (activate) close(s)
    }

    fun uiProgress(s: CrystalTrophyState, start: Double, duration: Double = 0.32): Double {
        return (if (s.reduced) s.ui else enter((s.titleTime - start) / duration) * s.ui)
    }

    fun letterProgress(s: CrystalTrophyState, index: Double): Double {
        return (if (s.reduced) s.ui else enter((s.titleTime - 2.66 - index * 0.038) / 0.42) * s.ui)
    }

    fun stepSimulation(s: CrystalTrophyState, dt: Double): Unit {
        val previous = s.phaseTime
        val previousRise = s.rise
        s.clock += dt
        s.phaseTime += dt
        s.flashAge += dt
        s.releaseAge += dt
        s.shareAge += dt
        val t = s.phaseTime
        s.flash = (if (s.reduced) ((if (s.flashAge < 0.3) sin(PI * s.flashAge / 0.3) * 0.35 else 0.0)) else pulse(s.flashAge))
        s.touch = (if (s.down && s.phase == "open") s.touchFrom + (1.0 - s.touchFrom) * enter((s.clock - s.downAt) / 0.12) else s.touchFrom * (1.0 - enter(s.releaseAge / 0.22)))
        s.sharePressAge += dt
        s.closePressAge += dt
        val sp = spring(s.sharePress, s.sharePressV, (if (s.shareDown) 1.0 else 0.0), 500.0, 32.0, dt)
        s.sharePress = (if (s.shareDown) enter(s.sharePressAge / 0.12) else sp.x)
        s.sharePressV = (if (s.shareDown) 0.0 else sp.v)
        val cp = spring(s.closePress, s.closePressV, (if (s.closeDown) 1.0 else 0.0), 500.0, 32.0, dt)
        s.closePress = (if (s.closeDown) enter(s.closePressAge / 0.12) else cp.x)
        s.closePressV = (if (s.closeDown) 0.0 else cp.v)
        s.copied = enter(s.shareAge / 0.22) * (1.0 - enter((s.shareAge - 1.43) / 0.22))
        s.check = clamp(s.shareAge / 0.22) * s.copied
        s.sheen = (if (s.shareAge < 0.65) s.shareAge / 0.65 else -1.0)
        if (s.shareAge > 2.0) {
            s.copied = 0.0
            s.check = 0.0
        }
        if (s.reduced && (s.phase == "revealing" || s.phase == "closing")) {
            val opening = s.phase == "revealing"
            s.veil = (if (t < 0.18) enter(t / 0.18) else 1.0 - enter((t - 0.18) / 0.32))
            val shown = (if (t < 0.18) !opening else opening)
            s.open = (if (shown) 1.0 else 0.0)
            s.hem = s.open
            s.hemRaw = s.open
            s.rise = s.open
            s.etch = s.open
            s.lamp = s.open
            s.beam = s.open
            s.under = 0.0
            s.shiver = 0.0
            s.ui = (if (opening) enter((t - 0.30) / 0.32) else 1.0 - enter(t / 0.18))
            s.titleTime = (if (opening) 10.0 else -1.0)
            s.yaw = 0.0
            if (t >= 0.62) {
                s.phase = (if (opening) "open" else "closed")
                s.phaseTime = 0.0
                s.veil = 0.0
                if (!opening) s.events = s.events or (1 or 32)
            }
        } else if (s.phase == "revealing") {
            s.open = s.startOpen + (1.0 - s.startOpen) * travel((t - 0.3) / 0.7)
            s.shiver = clamp(t / 0.32)
            s.seam = (0.37 + 0.63 * enter(t / 0.32)) * (1.0 - clamp((t - 0.32) / 0.68))
            val l = t - 0.86
            s.lamp = (if (l < 0.0) 0.0 else (if (l < 0.06) 0.55 * l / 0.06 else (if (l < 0.15) 0.55 - 0.43 * (l - 0.06) / 0.09 else 0.12 + 0.88 * enter((l - 0.15) / 0.24))))
            s.beam = travel((t - 0.92) / 0.66)
            s.under = enter((t - 1.44) / 0.2) * (1.0 - enter((t - 2.0) / 0.6))
            val rising = spring(0.0, 0.0, 1.0, 110.0, 11.5, max(0.0, t - 1.56)).x
            s.rise = (if (rising <= 1.0) rising else 1.0 + 0.065 * tanh((rising - 1.0) / 0.065))
            if (previous < 1.56 && t >= 1.56) s.events = s.events or 2

            if (previous < 2.3 && t >= 2.3) {
                s.flashAge = 0.0
                s.touchX = 0.05
                s.touchY = 2.85
                s.events = s.events or 4
            }
            s.lamp += 0.2 * s.flash
            s.etch = bezier((t - 2.36) / 1.15, 0.4, 0.0, 0.2, 1.0)
            if (previous < 3.51 && t >= 3.51) {
                s.flashAge = 0.0
                s.touchX = 0.02
                s.touchY = 2.0
                s.events = s.events or 8
            }
            s.titleTime = t
            s.ui = 1.0
            if (t >= 4.0) {
                s.phase = "open"
                s.phaseTime = 0.0
                s.rise = 1.0
                s.hem = 1.0
                s.hemRaw = 1.0
                s.hemV = 0.0
                s.shiver = 0.0
            }
        } else if (s.phase == "closing") {
            s.ui = s.startUi * (1.0 - enter(t / 0.2))
            s.lamp = s.startLamp * (1.0 - enter(t / 0.32))
            s.rise = s.startRise * (1.0 - travel((t - 0.08) / 0.48))
            if (s.rise < 0.001) s.etch = 0.0

            s.yaw = s.startYaw * (1.0 - enter(t / 0.3))
            s.open = s.startOpen * (1.0 - travel((t - 0.42) / 0.62))
            s.under = 0.0
            s.flash = 0.0
            s.seam = 0.37 * enter((t - 1.02) / 0.3)
            if (previous < 1.04 && t >= 1.04) s.events = s.events or 1

            if (t >= 1.32) {
                s.phase = "closed"
                s.phaseTime = 0.0
                s.events = s.events or 32
                s.titleTime = -1.0
            }
        } else if (s.phase == "closed") {
            if (!s.down) {
                val rail = spring(s.open, s.yawV, 0.0, 180.0, 22.0, dt)
                s.open = rail.x
                s.yawV = rail.v
            }
            val seamClock = s.clock % 3.2
            val demoSwell = (if (s.demoTime >= 2.1 && s.demoTime < 2.8) sin(PI * (s.demoTime - 2.1) / 0.7) else 0.0)
            s.seam = 0.37 + ((if (s.reduced) 0.0 else (if (s.auto && !s.paused) 0.45 * demoSwell else 0.09 * sin(TAU * seamClock / 3.2)))) + max(0.0, s.open)
            s.hint = (if (s.auto) 0.0 else enter((t - 1.4) / 0.4) * ((if (s.reduced) 1.0 else 0.8 + 0.2 * sin(TAU * (((if (s.auto && !s.paused) s.demoTime else s.clock)) % 3.2) / 3.2))))
            if (!s.started && t >= 0.5 && !s.down) reveal(s)
        } else {
            s.titleTime = min(10.0, s.titleTime + dt)
            s.ui = 1.0
            s.lamp = 1.0 + 0.22 * s.flash
            s.shiver = 0.0
            if (!s.down) {
                if (s.reduced) {
                    s.yaw = s.releaseYaw * (1.0 - enter(s.releaseAge / 0.24))
                    s.yawV = 0.0
                } else if (s.spin && !s.caught) {
                    val old = s.yaw
                    s.yaw += s.yawV * (1.0 - exp(-1.45 * dt)) / 1.45
                    s.yawV *= exp(-1.45 * dt)
                    if (abs(s.yaw - s.spinStart) >= PI || abs(s.yawV) < 2.0) s.caught = true

                    if ((old - s.spinTarget) * (s.yaw - s.spinTarget) <= 0.0) s.caught = true
                } else {
                    val idleAge = max(0.0, s.clock - s.lastInput - 1.2)
                    val target = (if (s.spin) s.spinTarget else s.restTarget + ((if (s.auto) 0.0 else 0.07 * sin(TAU * (s.clock % 7.0) / 7.0) * enter(idleAge / 0.8))))
                    val turn = spring(s.yaw, s.yawV, target, 120.0, 14.0, dt)
                    if (s.spin && !s.frontReported && (s.yaw - target) * (turn.x - target) <= 0.0) {
                        s.events = s.events or 2
                        s.frontReported = true
                    }
                    s.yaw = turn.x
                    s.yawV = turn.v
                    if (s.spin && abs(s.yaw - target) < 0.002 && abs(s.yawV) < 0.02) {
                        s.yaw = 0.0
                        s.yawV = 0.0
                        s.restTarget = 0.0
                        s.spin = false
                        s.caught = false
                    } else if (!s.spin && s.restTarget != 0.0 && abs(s.yaw - s.restTarget) < 0.002 && abs(s.yawV) < 0.02) {
                        s.yaw -= s.restTarget
                        s.restTarget = 0.0
                    } else if (!s.spin && s.auto && abs(s.yaw) < 0.0001 && abs(s.yawV) < 0.001) {
                        s.yaw = 0.0
                        s.yawV = 0.0
                    }
                }
            }
        }

        if (s.reduced) {
            s.cameraScale = 1.0
            s.cameraY = 0.0
            s.cameraV = 0.0
        } else {
            val push = (if (s.phase == "revealing") ((if (t < 0.3) enter(t / 0.3) else 1.0 - enter((t - 0.3) / (4.0 - 0.3)))) * 0.07 else (if (s.phase == "closing") (s.startCamera - 1.0) * (1.0 - enter(t / 0.32)) else 0.0))
            s.cameraScale = (1.0 + push) * (1.0 + 0.012 * s.flash)
            if (s.phase == "revealing" && !s.seated && previousRise < 1.0 && s.rise >= 1.0) {
                s.seated = true
                s.cameraY = 2.0
                s.cameraV = 0.0
            } else {
                val seat = spring(s.cameraY, s.cameraV, 0.0, 420.0, 26.0, dt)
                s.cameraY = seat.x
                s.cameraV = seat.v
                if (abs(s.cameraY) < 0.0001 && abs(s.cameraV) < 0.001) {
                    s.cameraY = 0.0
                    s.cameraV = 0.0
                }
            }
        }
        if (!s.reduced) {
            val hem = spring(s.hemRaw, s.hemV, s.open, 70.0, 9.0, dt)
            s.hemRaw = hem.x
            s.hemV = hem.v
            s.hem = (if (hem.x < 0.0) 0.06 * tanh(hem.x / 0.06) else (if (hem.x > 1.0) 1.0 + 0.06 * tanh((hem.x - 1.0) / 0.06) else hem.x))
        }
    }
}

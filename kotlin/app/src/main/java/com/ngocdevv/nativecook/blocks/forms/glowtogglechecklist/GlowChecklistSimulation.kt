package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Native translation of checklist-sim.ts; the demo and real gestures use the same input path. */
internal object GlowChecklistSimulation {
    const val FIXED_DT = 1.0 / 240.0
    const val LOOP_PERIOD = 10.4
    const val RESUME_DELAY = 3.5

    fun unit(n: Double) = n.coerceIn(0.0, 1.0)

    fun bezier(t: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (t <= 0) return 0.0
        if (t >= 1) return 1.0
        var lo = 0.0
        var hi = 1.0
        var u = t
        repeat(18) {
            u = (lo + hi) / 2
            val v = 1 - u
            val x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if (x < t) lo = u else hi = u
        }
        val v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }

    fun demoTouch(t: Double, still: Boolean): GlowChecklistTouch {
        var control = -2
        if ((t >= 0.9 && t < 1.06) || (t >= 7.5 && t < 7.64)) control = -1
        if (t >= 2 && t < 2.12) control = 0
        if (t >= 2.8 && t < 2.92) control = 1
        if (t >= 3.6 && t < 3.72) control = 2
        if (t >= 5.5 && t < 5.62) control = 3
        if (!still && t >= 4.4 && t < 4.98) {
            val p = min(1.0, (t - 4.4) / 0.42)
            return GlowChecklistTouch(3, true, 0.46 * (1 - (1 - p).pow(3)) * 20,
                if (p < 1) 0.46 * 3 * (1 - p).pow(2) / 0.42 * 20 else 0.0)
        }
        return GlowChecklistTouch(control, control != -2)
    }

    private fun fireGlare(s: GlowChecklistState) {
        if (!s.still) s.glareAge = 0.0
    }

    private fun fade(s: GlowChecklistState, kind: Int, target: Double) {
        s.fadeAge = 0.0
        s.fadeFrom = s.opacity
        s.fadeKind = kind
        s.fadeTarget = target
    }

    private fun intro(s: GlowChecklistState) {
        s.clock = 0.0
        s.introAge = 0.0
        s.outroAge = -1.0
        s.glareDelay = 0.1
        if (s.still) fade(s, 2, 1.0) else s.present = 0.08
    }

    private fun outro(s: GlowChecklistState) {
        s.outroAge = 0.0
        s.introAge = -1.0
        if (s.still) fade(s, 2, 0.0)
    }

    private fun resetHidden(s: GlowChecklistState) {
        for (i in 0..3) s.knobs[i] = GlowChecklistState.Knob()
        s.sum = 0.0
        s.fill = 0.0
        s.fillV = 0.0
        s.hue = 0.0
        s.hueV = 0.0
        s.tip = 0.0
        s.tipV = 0.0
        s.surge = 0.0
        s.surgeAge = -1.0
        s.surgeDelay = -1.0
        s.flash = 0.0
        s.press = 0.0
        s.glare = -0.3
        s.glareAge = -1.0
        s.glareDelay = -1.0
        s.open = 0.0
        s.openTarget = 0.0
        s.openFrom = 0.0
        s.openAge = 1.0
        s.present = 0.0
        s.introAge = -1.0
        s.outroAge = -1.0
        s.finger = -2
        s.events.changed = true
        s.events.reset = true
    }

    private fun commit(s: GlowChecklistState, i: Int, on: Boolean, real: Boolean) {
        val n = s.knobs[i]
        n.linearFrom = n.lead
        n.linearAge = 0.0
        if (n.on == on) return
        n.on = on
        val count = s.knobs.count { it.on }
        s.events.changed = true
        if (on && count == 4) {
            s.surgeDelay = 0.06
            if (real) s.events.success = true
        } else if (on) {
            s.flash = max(s.flash, 0.35)
            if (real) s.events.onMask = s.events.onMask or (1 shl i)
        } else {
            if (count == 3) {
                s.flash = 0.25
                s.surgeDelay = -1.0
            }
            if (real) s.events.offMask = s.events.offMask or (1 shl i)
        }
    }

    private fun toggleCap(s: GlowChecklistState, real: Boolean) {
        s.openTarget = 1 - s.openTarget
        s.openFrom = s.open
        s.openAge = 0.0
        if (s.still) fade(s, 1, s.openTarget)
        s.events.changed = true
        if (real) s.events.cap = true
    }

    private fun rubber(n: Double): Double {
        if (n < 0) return -0.08 * (1 - exp(n / 0.08))
        if (n > 1) return 1 + 0.08 * (1 - exp(-(n - 1) / 0.08))
        return n
    }

    private fun cancelFinger(s: GlowChecklistState) {
        if (s.finger >= 0) {
            val n = s.knobs[s.finger]
            n.linearFrom = n.lead
            n.linearAge = 0.0
        }
        s.finger = -2
        s.fingerVelocity = 0.0
    }

    private fun input(s: GlowChecklistState, t: GlowChecklistTouch, real: Boolean) {
        if (real) {
            if (!s.paused && s.autoPlay) {
                cancelFinger(s)
                s.paused = true
                s.events.paused = true
            }
            s.realDown = t.down
            s.idle = 0.0
        }
        if (t.cancel) {
            cancelFinger(s)
            return
        }
        if (t.activate) {
            if (s.present < 1) return
            if (t.control == -1) toggleCap(s, real)
            else if (t.control >= 0 && s.open >= 0.98) commit(s, t.control, !s.knobs[t.control].on, real)
            return
        }
        if (t.down && s.finger == -2) {
            if (s.present < 1 || (t.control >= 0 && s.open < 0.98)) return
            s.finger = t.control
            s.fingerReal = real
            s.start = if (t.control >= 0) s.knobs[t.control].lead else 0.0
            s.moved = 0.0
            s.drag = false
            s.lastDx = 0.0
        }
        if (s.finger == -2 || real != s.fingerReal) return
        s.moved = max(s.moved, abs(t.dx))
        if (s.finger >= 0) {
            val n = s.knobs[s.finger]
            s.drag = s.drag || s.moved >= 6
            if (s.moved > 0) {
                val pos = s.start + t.dx / (20 * t.k)
                n.lead = if (s.still) unit(pos) else rubber(pos)
                n.leadV = t.velocity / (20 * t.k)
                if (s.still) {
                    n.trail = n.lead
                    n.trailV = n.leadV
                }
            }
            if (!t.down) {
                commit(s, s.finger, if (s.drag) n.lead + 0.1 * n.leadV > 0.5 else !n.on, real)
                s.finger = -2
            }
        } else if (!t.down) {
            toggleCap(s, real)
            s.finger = -2
        }
        s.lastDx = t.dx
        s.fingerVelocity = t.velocity
    }

    private fun advance(s: GlowChecklistState, h: Double) {
        if (s.present > 0) s.clock += h
        if (s.glareDelay >= 0) {
            s.glareDelay -= h
            if (s.glareDelay <= 1e-9) {
                s.glareDelay = -1.0
                fireGlare(s)
            }
        }
        if (s.surgeDelay >= 0) {
            s.surgeDelay -= h
            if (s.surgeDelay <= 1e-9) {
                s.surgeDelay = -1.0
                s.flash = 1.0
                if (!s.still) {
                    s.surgeAge = 0.0
                    s.glareDelay = 0.08
                }
            }
        }
        if (s.surgeAge >= 0) {
            s.surgeAge += h
            s.surge = s.surgeAge / 0.95
            if (s.surgeAge >= 0.95) {
                s.surgeAge = -1.0
                s.surge = 0.0
            }
        }
        s.flash *= exp(-h / 0.28)
        if (s.glareAge >= 0) {
            s.glareAge += h
            s.glare = -0.3 + 1.6 * bezier(s.glareAge / 0.7, 0.77, 0.0, 0.175, 1.0)
            if (s.glareAge >= 0.7) {
                s.glareAge = -1.0
                s.glare = -0.3
            }
        }
        if (!s.still) {
            if (s.introAge >= 0) {
                s.introAge += h
                s.present = 0.08 + 0.92 * bezier(s.introAge / 0.52, 0.23, 1.0, 0.32, 1.0)
                for (i in 0..6) s.letters[i] = bezier((s.introAge - 0.055 * i) / 0.22, 0.23, 1.0, 0.32, 1.0)
                if (s.introAge >= 0.55) s.introAge = -1.0
            }
            if (s.outroAge >= 0) {
                s.outroAge += h
                s.present = 1 - bezier(s.outroAge / 0.38, 0.77, 0.0, 0.175, 1.0)
                if (s.outroAge >= 0.38) s.outroAge = -1.0
            }
            if (s.open != s.openTarget) {
                s.openAge += h
                val duration = if (s.openTarget != 0.0) 0.56 else 0.44
                val p = if (s.openTarget != 0.0) bezier(s.openAge / duration, 0.23, 1.0, 0.32, 1.0)
                    else bezier(s.openAge / duration, 0.32, 0.72, 0.0, 1.0)
                s.open = s.openFrom + (s.openTarget - s.openFrom) * p
                if (s.openAge >= duration) {
                    s.open = s.openTarget
                    if (s.open == 0.0) fireGlare(s)
                }
            }
            s.press += ((if (s.finger == -1) 1 else 0) - s.press) * (1 - exp(-h / 0.055))
        } else if (s.fadeAge >= 0) {
            val before = s.fadeAge
            s.fadeAge += h
            if (s.fadeAge < 0.12) s.opacity = s.fadeFrom * (1 - s.fadeAge / 0.12)
            else {
                if (before < 0.12) {
                    if (s.fadeKind == 1) s.open = s.fadeTarget
                    else {
                        s.present = s.fadeTarget
                        s.letters.fill(1.0)
                    }
                }
                s.opacity = unit((s.fadeAge - 0.12) / 0.18)
                if (s.fadeAge >= 0.3) s.fadeAge = -1.0
            }
        }
        s.sum = 0.0
        for (i in 0..3) {
            val n = s.knobs[i]
            val held = s.finger == i
            n.hold += ((if (held) 1 else 0) - n.hold) * (1 - exp(-h / (if (held) 0.07 else 0.12)))
            if (s.still) {
                if (!(held && s.moved > 0)) {
                    n.linearAge = min(0.12, n.linearAge + h)
                    n.lead = n.linearFrom + ((if (n.on) 1 else 0) - n.linearFrom) * n.linearAge / 0.12
                    n.trail = n.lead
                    n.leadV = 0.0
                    n.trailV = 0.0
                }
            } else {
                if (!(held && s.moved > 0)) {
                    n.leadV += (900 * ((if (n.on) 1 else 0) - n.lead) - 2 * 0.82 * 30 * n.leadV) * h
                    n.lead += n.leadV * h
                }
                val w = if (held && s.moved > 0) 22 else 17
                val z = if (held && s.moved > 0) 0.8 else 0.72
                val target = if (held && s.moved > 0) n.lead else if (n.on) 1.0 else 0.0
                n.trailV += (w * w * (target - n.trail) - 2 * z * w * n.trailV) * h
                n.trail += n.trailV * h
                n.lead = n.lead.coerceIn(-0.08, 1.08)
                n.trail = n.trail.coerceIn(-0.08, 1.08)
            }
            s.sum += unit((n.lead + n.trail) / 2)
        }
        s.fillV += (256 * (s.sum - s.fill) - 32 * s.fillV) * h
        s.fill += s.fillV * h
        s.hueV += (49 * (s.sum - s.hue) - 14 * s.hueV) * h
        s.hue += s.hueV * h
        val p = unit((s.sum - 3.1) / 0.85)
        val target = p * p * (3 - 2 * p)
        s.tipV += (121 * (target - s.tip) - 22 * (if (s.still) 1.0 else 0.38) * s.tipV) * h
        s.tip += s.tipV * h
    }

    fun step(s: GlowChecklistState, touch: GlowChecklistTouch?, dt: Double) {
        if (!s.started) {
            s.started = true
            if (!s.autoPlay) intro(s)
        }
        if (touch != null) input(s, touch, true)
        s.remainder += dt.coerceIn(0.0, 1.0 / 30)
        while (s.remainder + 1e-10 >= FIXED_DT) {
            s.remainder = max(0.0, s.remainder - FIXED_DT)
            if (s.autoPlay && s.paused) {
                if (!s.realDown) s.idle += FIXED_DT
                if (s.idle + 1e-9 >= RESUME_DELAY) {
                    s.paused = false
                    s.idle = 0.0
                    s.events.resumed = true
                    s.loopTick = ((if (s.openTarget != 0.0 || s.open > 0) 7.5 else 8.7) / FIXED_DT).roundToInt()
                }
            }
            if (s.autoPlay && !s.paused) {
                s.loopTime = s.loopTick * FIXED_DT
                if (s.loopTick == 36) fireGlare(s)
                if (s.loopTick == 2088) outro(s)
                if (s.loopTick == 2208) resetHidden(s)
                if (s.loopTick == 2232) intro(s)
                val f = demoTouch(s.loopTime, s.still)
                if (f.down || s.finger != -2) {
                    input(s, if (f.down) f else GlowChecklistTouch(s.finger, false, s.lastDx), false)
                }
                s.loopTick = (s.loopTick + 1) % 2496
                s.loopTime = s.loopTick * FIXED_DT
            }
            advance(s, FIXED_DT)
        }
    }
}

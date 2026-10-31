/** Live gesture and Undo state integration matching the React Native motion model. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.BACK
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.DRAG
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.DRAG_FULL
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.GONE
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.Geo
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.LOOP
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.Pose
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.REST
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.UNDO
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.easeInOut
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.easeOut
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.script
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.seg
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.smooth
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.springStep
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.springTo
import com.ngocdevv.nativecook.blocks.gestures.crumpletodelete.CrumpleToDeleteMotion.tossPos
import kotlin.math.*

/** UI-thread state mirrors the RN shared-value spring integrator and interruption rules. */
internal class CrumpleToDeleteDriver(g: Geo) {
    var auto = false
    var mode = REST
    var t = 0.0
    var now = 0.0
    var touchAt = -10.0
    var x = g.hx
    var y = g.hy
    var vx = 0.0
    var vy = 0.0
    var c = 0.0
    var vc = 0.0
    var spin = 0.0
    var sc = 1.0
    var sink = 0.0
    var wobble = 0.0
    var toast = 0.0
    var lift = 0.0
    var relAt = -10.0
    var rx = 0.0
    var ry = 0.0
    var rc = 0.0
    var rspin = 0.0
    var undoAt = -10.0
    var sx = 0.0
    var sy = 0.0
    var lastStep = 0
    var gone = false

    fun pose() = Pose(x, y, c, spin, sc, sink, wobble, toast, lift)

    fun apply(p: Pose) {
        x = p.x
        y = p.y
        c = p.c
        spin = p.spin
        sc = p.sc
        sink = p.sink
        wobble = p.wobble
        toast = p.toast
        lift = p.lift
    }

    /** Advances one live frame before its immutable pose is published to the Canvas. */
    fun step(
        dt: Double,
        g: Geo,
        autoPlay: Boolean,
        reduced: Boolean,
        cue: (String) -> Unit,
        buzz: (Int) -> Unit,
    ) {
        now += dt
        if (autoPlay && !reduced && !auto && mode == REST && now - touchAt > 2 && c < 0.001) {
            auto = true
            t = 0.0
        }
        if (auto) {
            t = (t + dt) % LOOP
            val p = script(t, g)
            vx = (p.x - x) / dt
            vy = (p.y - y) / dt
            apply(p)
            gone = t >= 3.45 && t < 6.3
            return
        }
        when (mode) {
            DRAG -> {
                val n = max(1, ceil(dt / (1.0 / 240)).toInt())
                repeat(n) {
                    val a = springTo(x, vx, sx, 0.12, 0.95, dt / n)
                    x = a.first
                    vx = a.second
                    val b = springTo(y, vy, sy, 0.12, 0.95, dt / n)
                    y = b.first
                    vy = b.second
                }
                val target = ((y - g.hy) / DRAG_FULL).coerceIn(0.0, 0.92)
                c += (target - c) * (1 - exp(-dt * 14))
                spin = (x - g.hx) * 0.002
                lift += (1 - lift) * (1 - exp(-dt * 16))
                val step = floor(c * 8).toInt()
                if (step != lastStep) {
                    lastStep = step
                    buzz(0)
                    cue("rustle")
                }
            }
            BACK -> {
                val n = max(1, ceil(dt / (1.0 / 240)).toInt())
                repeat(n) {
                    val a = springTo(x, vx, g.hx, 0.5, 0.75, dt / n)
                    x = a.first
                    vx = a.second
                    val b = springTo(y, vy, g.hy, 0.5, 0.75, dt / n)
                    y = b.first
                    vy = b.second
                    val d = springTo(c, vc, 0.0, 0.6, 0.6, dt / n)
                    c = max(0.0, d.first)
                    vc = d.second
                }
                spin *= 1 - min(1.0, dt * 8)
                lift *= 1 - min(1.0, dt * 10)
                if (abs(x - g.hx) + abs(y - g.hy) < 0.4 && c < 0.002) {
                    mode = REST
                    c = 0.0
                    x = g.hx
                    y = g.hy
                }
            }
            GONE -> {
                val tr = now - relAt
                val q = easeInOut(seg(tr, 0.28, 0.9))
                val p = tossPos(q, rx, ry, g.bx, g.by)
                x = p.first
                y = p.second
                c = if (reduced) 1.0 else rc + (1 - rc) * springStep(tr, 0.42, 0.78)
                spin = rspin + 7 * q
                sc = 1 - 0.18 * q
                sink = easeInOut(seg(tr, 0.9, 1.25))
                val land = tr - 0.9
                wobble = if (land > 0) exp(-land * 5) * sin(land * 26) else 0.0
                toast = smooth(1.0, 1.3, tr)
                lift = 1 - smooth(0.0, 0.3, tr)
                gone = tr >= 0.9
                if (tr - dt < 0.28 && tr >= 0.28) cue("toss")
                if (tr - dt < 0.9 && tr >= 0.9) {
                    buzz(1)
                    cue("bin")
                }
                if (tr > 7) {
                    mode = UNDO
                    undoAt = now
                }
            }
            UNDO -> {
                val tu = now - undoAt
                val q = easeInOut(seg(tu, 0.18, 0.72))
                val p = tossPos(q, g.bx, g.by, g.hx, g.hy)
                val open = springStep(tu - 0.62, 0.8, 0.62)
                x = p.first
                y = p.second
                sink = 1 - easeOut(seg(tu, 0.0, 0.22))
                c =
                    if (reduced) {
                        if (tu > 0.5) 0.0 else 1.0
                    } else max(0.0, 1 - open)
                spin = (rspin + 7) * (1 - q) * (1 - smooth(0.0, 1.0, open))
                sc = 0.82 + 0.18 * q
                toast = 1 - smooth(0.0, 0.25, tu)
                wobble = 0.0
                gone = tu < 0.3
                if (tu - dt < 0.62 && tu >= 0.62) cue("open")
                if (tu > 1.82) {
                    mode = REST
                    c = 0.0
                    spin = 0.0
                    sc = 1.0
                    x = g.hx
                    y = g.hy
                }
            }
        }
    }

    fun interrupt(g: Geo) {
        touchAt = now
        if (!auto) return
        auto = false
        if (t >= 6 && t < 8) {
            mode = UNDO
            undoAt = now - (t - 6)
            rspin = 0.12
        } else if (t >= 2.55 && t < 8) {
            mode = GONE
            relAt = now - (t - 2.55)
            rx = g.hx + 18
            ry = g.hy + 210
            rc = 210 / DRAG_FULL
            rspin = 0.12
        } else if (t >= 1 && t < 8) mode = BACK else mode = REST
    }

    fun beginDrag() {
        if (mode == REST || mode == BACK) {
            mode = DRAG
            sx = x
            sy = y
            lastStep = 0
        }
    }

    fun drag(dx: Double, dy: Double, g: Geo) {
        touchAt = now
        if (mode == DRAG) {
            sx = g.hx + dx
            sy = g.hy + max(-40.0, dy)
        }
    }

    fun release(velocityY: Double) {
        touchAt = now
        if (mode != DRAG) return
        if (c > 0.33 || velocityY > 900) delete() else mode = BACK
    }

    fun delete() {
        mode = GONE
        relAt = now
        rx = x
        ry = y
        rc = c
        rspin = spin
    }

    fun undo(g: Geo) {
        interrupt(g)
        if (mode == GONE) {
            mode = UNDO
            undoAt = now
        }
    }
}

package com.ngocdevv.nativecook.blocks.lists.snackstopvending

import kotlin.math.*

/** Pure absolute-time geometry; the touch model plays the same dispense beats as the demo. */
internal object SnackStopVendingMotion {
    const val LOOP = 8.0
    val keys = listOf("A", "B", "C", "D", "1", "2", "3", "4", "5", "6", "0", "✕")
    val letters = listOf("A", "B", "C", "D")
    val drinks = listOf(
        listOf("Lime Fizz", "Sparkling · 330 ml", "\$1.50"),
        listOf("Pink Pop", "Sparkling · 330 ml", "\$1.75"),
        listOf("Violet Dusk", "Still · 330 ml", "\$1.75"),
        listOf("Tangerine Twist", "Sparkling · 330 ml", "\$1.50"),
        listOf("Sky Splash", "Still · 330 ml", "\$1.25"),
    )
    fun color(s: Int) = (2 * (s / 5) + s % 5) % 5
    fun code(s: Int) = letters[s / 5] + (s % 5 + 1)
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun io(u: Double) = if (u < .5) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2
    fun out(u: Double) = 1 - (1 - u).pow(3)
    fun phase(t: Double) = (t % LOOP + LOOP) % LOOP
    data class Can(
        var kind: Int = 0, var fall: Double = 0.0, var drop: Double = 0.0,
        var bounce: Double = 0.0, var rot: Double = 0.0, var scale: Double = 1.0,
        var alpha: Double = 1.0, var taken: Double = 0.0, var clip: Boolean = false, var veil: Boolean = false,
    )
    data class Frame(
        var slot: Int = -1, var can: Can = Can(), var glint: Double = -1.0, var trayAlpha: Double = 0.0,
        var flashA: Int = -1, var flashAU: Double = -1.0, var flashB: Int = -1, var flashBU: Double = -1.0,
        var fingerKey: Int = 0, var finger: Double = 0.0, var lcd: String = "", var lcdShake: Double = 0.0,
        var card: Boolean = false, var pill: Int = 0, var cardSlot: Int = -1,
    )
    fun canAt(t: Double): Can {
        val c = Can()
        if (t < 2 || t >= 7) return c
        if (t >= 6.4) {
            val r = out(seg(t, 6.4, 7.0)); c.kind = 2; c.scale = .85 + .15 * r; c.alpha = r
            return c
        }
        c.kind = 1; c.scale = 1 + .12 * io(seg(t, 2.0, 2.8))
        if (t >= 2.8) { c.rot = .35 * seg(t, 2.8, 3.15).pow(2); c.scale = 1.12 }
        if (t >= 3.15) {
            c.fall = seg(t, 3.15, 3.6); c.drop = c.fall * c.fall
            c.rot = .35 + (PI / 2 - .35) * c.fall
        }
        if (t >= 3.6) {
            val b = seg(t, 3.6, 3.95); c.bounce = abs(exp(-4 * b) * sin(3 * PI * b) * 8)
            c.rot = PI / 2; c.fall = 1.0; c.drop = 1.0
        }
        if (t >= 5.8) { val k = seg(t, 5.8, 6.3); c.taken = 40 * io(k); c.alpha = 1 - k }
        c.clip = t >= 3.45; c.veil = c.clip
        return c
    }
    fun glint(t: Double): Double { val u = seg(t, 2.0, 2.8); return if (u > 0 && u < 1) u else -1.0 }
    fun flash(t: Double, start: Double): Double { val u = seg(t, start, start + .35); return if (u > 0 && u < 1) u else -1.0 }
    fun loopFrame(time: Double): Frame {
        val t = phase(time); val f = Frame(slot = 8, can = canAt(t), glint = glint(t))
        f.flashA = 1; f.flashAU = flash(t, 1.0); f.flashB = 7; f.flashBU = flash(t, 1.6)
        val a = seg(t, .85, .97) * (1 - seg(t, 1.3, 1.42))
        val b = seg(t, 1.45, 1.57) * (1 - seg(t, 1.9, 2.02))
        f.fingerKey = if (a > 0) 1 else 7; f.finger = if (a > 0) a else b
        f.lcd = if (t >= 6 || t < 1) "" else if (t < 1.6) "B_" else "B4"
        f.card = t >= 1.6 && t < 6; f.pill = if (!f.card) 0 else if (t >= 3.6) 2 else 1; f.cardSlot = 8
        return f
    }
    class Touch {
        var manual = false; var loopStart = 0.0; var lastTouch = -100.0; var letter = ""
        var slot = -1; var dispAt = -1.0; var takeAt = -1.0
        var keyA = -1; var keyAAt = -100.0; var keyB = -1; var keyBAt = -100.0; var shakeAt = -100.0
        fun beat(c: Double, reduced: Boolean): Double {
            if (slot < 0) return -1.0
            if (reduced) {
                if (takeAt >= 0) return if (c - takeAt >= .2) 6.0 else 5.8
                return if (c - dispAt >= .4) 3.6 else 2.0
            }
            return if (takeAt >= 0) min(5.8 + c - takeAt, 7.0) else min(2 + c - dispAt, 5.79)
        }
        fun busy(c: Double, reduced: Boolean) = slot >= 0 && beat(c, reduced) < 7 && !(takeAt >= 0 && (if (reduced) c - takeAt >= .4 else beat(c, false) >= 7))
        fun inTray(c: Double, reduced: Boolean) = slot >= 0 && takeAt < 0 && (if (reduced) c - dispAt >= .4 else beat(c, false) >= 3.95)
        fun over(c: Double, reduced: Boolean) {
            lastTouch = c
            if (manual) return
            manual = true
            if (reduced) return
            val t = phase(c - loopStart)
            if (t >= 1 && t < 1.6) letter = "B"
            if (t >= 1.6 && t < 7) { slot = 8; dispAt = c - (t - 2); if (t >= 5.8) takeAt = c - (t - 5.8) }
            if (t >= 1 && t < 1.35) { keyA = 1; keyAAt = c - (t - 1) }
            if (t >= 1.6 && t < 1.95) { keyB = 7; keyBAt = c - (t - 1.6) }
        }
        fun shake(c: Double) { letter = ""; shakeAt = c }
        fun choose(s: Int, c: Double, reduced: Boolean) {
            if (busy(c, reduced)) { shake(c); return }
            letter = ""; slot = s; dispAt = c; takeAt = -1.0
        }
        fun key(key: Int, c: Double, reduced: Boolean) {
            keyB = keyA; keyBAt = keyAAt; keyA = key; keyAAt = c
            if (busy(c, reduced)) { shake(c); return }
            slot = -1
            val v = keys[key]
            if (v == "✕") { letter = ""; return }
            if (letters.contains(v)) { letter = v; return }
            val row = letters.indexOf(letter); val col = v.toIntOrNull() ?: 0
            if (row < 0 || col !in 1..5) { shake(c); return }
            choose(row * 5 + col - 1, c, reduced)
        }
        fun take(c: Double, reduced: Boolean) { if (inTray(c, reduced)) takeAt = c }
        fun step(c: Double, autoplay: Boolean, reduced: Boolean) {
            if (!manual) return
            if (slot >= 0 && takeAt >= 0 && (if (reduced) c - takeAt >= .4 else beat(c, false) >= 7)) { slot = -1; dispAt = -1.0; takeAt = -1.0 }
            val landed = if (slot >= 0 && takeAt < 0) dispAt + (if (reduced) .4 else 1.95) else -1e9
            if (!autoplay || reduced || c - max(lastTouch, landed) < 2) return
            if (inTray(c, reduced)) { takeAt = c; return }
            if (slot >= 0 || c - shakeAt < .3 || c - keyAAt < .35) return
            manual = false; loopStart = c; letter = ""; keyA = -1; keyB = -1
            keyAAt = -100.0; keyBAt = -100.0; shakeAt = -100.0
        }
        fun frame(c: Double, reduced: Boolean): Frame {
            if (!manual && !reduced) return loopFrame(c - loopStart)
            val t = beat(c, reduced)
            val f = Frame(slot = slot, can = canAt(max(0.0, t)), glint = if (reduced || t < 0) -1.0 else glint(t))
            f.flashA = keyA; f.flashAU = flash(c, keyAAt); f.flashB = keyB; f.flashBU = flash(c, keyBAt)
            f.lcd = if (letter.isNotEmpty()) letter + "_" else ""
            if (slot >= 0 && t < 6) f.lcd = code(slot)
            val u = seg(c, shakeAt, shakeAt + .3); f.lcdShake = if (u > 0 && u < 1) 4 * sin(u * PI * 6) else 0.0
            f.card = slot >= 0 && t < 6; f.pill = if (!f.card) 0 else if (t >= 3.6) 2 else 1; f.cardSlot = slot
            if (reduced && slot >= 0) {
                f.can = Can()
                if (takeAt >= 0) {
                    val v = c - takeAt; f.can.kind = if (v >= .2) 2 else 3
                    f.can.alpha = if (v >= .2) seg(v, .2, .4) else 0.0
                    f.trayAlpha = if (v >= .2) 0.0 else 1 - seg(v, 0.0, .2)
                } else {
                    val v = c - dispAt; f.can.kind = if (v < .2) 2 else 3
                    f.can.alpha = if (v < .2) 1 - seg(v, 0.0, .2) else 0.0
                    f.trayAlpha = if (v < .2) 0.0 else seg(v, .2, .4)
                }
            }
            return f
        }
        fun moving(c: Double, autoplay: Boolean, reduced: Boolean): Boolean {
            if (autoplay && !reduced) return true
            if (c - keyAAt < .35 || c - shakeAt < .3) return true
            if (slot < 0) return false
            return if (takeAt >= 0) c - takeAt < (if (reduced) .4 else 1.2) else c - dispAt < (if (reduced) .4 else 1.95)
        }
    }
}

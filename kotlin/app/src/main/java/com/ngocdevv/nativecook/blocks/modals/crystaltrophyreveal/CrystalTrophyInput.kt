package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline as Timeline

internal class CrystalTrophyInput {
    private var mode = "background"
    private var startX = 0.0
    private var startY = 0.0
    private var x = 0.0
    private var y = 0.0
    private var active = false

    fun begin(s: CrystalTrophyState, f: CrystalTrophyFrame, px: Double, py: Double) {
        if (active) return
        Timeline.interrupt(s, true)
        x = px
        y = py
        startX = x
        startY = y
        active = true
        mode = when {
            s.phase == "closed" -> "curtain"
            s.phase != "open" -> "background"
            inShare(f) -> "share"
            inClose(f) -> "close"
            x >= f.originX - f.unit * 1.3 - 24 && x <= f.originX + f.unit * 1.3 + 24 &&
                y >= f.originY - f.unit * 3.05 - 24 && y <= f.originY + f.unit * 0.6 + 24 -> "trophy"
            else -> "background"
        }
        if (mode == "trophy" || mode == "curtain") {
            val point = f.stagePoint(s, x, y)
            Timeline.touchDown(s, (point[0] - f.originX) / f.unit, (f.originY - point[1]) / f.unit)
        }
        press(s, f)
    }

    fun move(s: CrystalTrophyState, f: CrystalTrophyFrame, px: Double, py: Double) {
        if (!active) return
        x = px
        y = py
        if (mode == "trophy" || mode == "curtain") {
            val point = f.stagePoint(s, x, y)
            Timeline.dragTo(s, x - startX, y - startY, (point[0] - f.originX) / f.unit, (f.originY - point[1]) / f.unit, f.width)
        }
        press(s, f)
    }

    fun held(s: CrystalTrophyState, f: CrystalTrophyFrame) {
        if (active && mode == "trophy") move(s, f, x, y)
    }

    fun end(s: CrystalTrophyState, f: CrystalTrophyFrame, velocity: Double, cancelled: Boolean) {
        if (!active) return
        Timeline.interrupt(s, false)
        active = false
        when (mode) {
            "trophy", "curtain" -> Timeline.release(s, velocity, cancelled)
            "share" -> Timeline.pressShare(s, false, !cancelled && inShare(f))
            "close" -> Timeline.pressClose(s, false, !cancelled && inClose(f))
        }
    }

    fun access(s: CrystalTrophyState, f: CrystalTrophyFrame, target: String, delta: Double = 0.0) {
        Timeline.interrupt(s, false)
        when (target) {
            "curtain" -> Timeline.reveal(s)
            "share" -> Timeline.pressShare(s, false, true)
            "close" -> Timeline.close(s)
            "trophy" -> if (s.phase == "open") {
                if (delta == 0.0) Timeline.tapAt(s, 0.0, 1.8)
                else {
                    Timeline.touchDown(s, 0.0, 1.8)
                    Timeline.dragTo(s, delta / 0.0062, 0.0, 0.0, 1.8, f.width)
                    s.maxDrag = 20.0
                    Timeline.release(s, 0.0)
                }
            }
        }
    }

    private fun press(s: CrystalTrophyState, f: CrystalTrophyFrame) {
        if (mode == "share") Timeline.pressShare(s, true, false, (x - f.shareX) / f.shareWidth, (y - f.shareY) / 56)
        if (mode == "close") Timeline.pressClose(s, true, false, (x - f.closeX - 2) / 36, (y - f.closeY - 2) / 36)
    }

    private fun inShare(f: CrystalTrophyFrame): Boolean {
        return x >= f.shareX && x <= f.shareX + f.shareWidth && y >= f.shareY && y <= f.shareY + 56
    }

    private fun inClose(f: CrystalTrophyFrame): Boolean {
        return x >= f.closeX - 2 && x <= f.closeX + 42 && y >= f.closeY - 2 && y <= f.closeY + 42
    }
}

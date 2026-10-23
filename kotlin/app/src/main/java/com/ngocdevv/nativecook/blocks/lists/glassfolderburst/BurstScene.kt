package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import kotlin.math.*

/** A single UI-thread owner for the native translation of burst-timeline.ts. */
internal class BurstScene(val n: Int, val reduced: Boolean, val auto: Boolean) {
    enum class Input { FolderDown, FolderUp, Toggle, RowDown, RowUp, Select, TouchStart, TouchEnd }
    var sim = BurstSimulation(n, reduced)
    var time = 0.0
    var accumulator = 0.0
    var loop = 0.0
    var idleTime = 0.0
    var idleGain = if (auto) 0.0 else 1.0
    var paused = false
    var resumeAt = 0.0
    var recovering = false
    var held = false
    var touching = false
    var serial = 0
    var expanded = false
    var age = 0.0
    var rowsStarted = false
    var launched = false
    var returning = false
    var home = true
    var dock = 0.0
    var dockFrom = 0.0
    var dockTarget = 0.0
    var dockAge = 1.0
    val springs = DoubleArray(14)
    var pressFrom = 0.0
    var pressAge = 1.0
    var rowFrom = 0.0
    var rowAge = 1.0
    var pressed = -1
    var selected = -1
    var previous = -1
    var selectionAge = 1.0
    val selectedMix = DoubleArray(n)
    var landed = 0
    var events = 0
    var sounded = false
    var shutSounded = true
    var fade = 1.0
    var reducedFrom = 1.0
    var reducedSnapped = true

    fun input(kind: Input, index: Int = -1) {
        if (auto && !paused) held = false
        serial++
        paused = true
        recovering = false
        resumeAt = time + 3.5
        when (kind) {
            Input.TouchStart -> touching = true
            Input.TouchEnd -> touching = false
            Input.FolderDown -> {
                press(true)
                events = events or LIGHT
            }
            Input.FolderUp -> press(false)
            Input.Toggle -> toggle()
            Input.RowDown -> press(true, index)
            Input.RowUp -> press(false, index)
            Input.Select -> if (index in 0 until n && expanded && (landed and (1 shl index)) != 0) {
                select(if (selected == index) -1 else index)
                events = events or SELECTION
            }
        }
    }
    private fun select(index: Int) {
        previous = selected
        selected = index
        selectionAge = 0.0
        events = events or CHANGED
    }
    private fun dockTo(goal: Double) {
        dockFrom = dock
        dockTarget = goal
        dockAge = 0.0
    }
    private fun toggle() {
        expanded = !expanded
        age = 0.0
        rowsStarted = false
        sounded = false
        returning = false
        home = false
        shutSounded = false
        if (!expanded) {
            select(-1)
            landed = 0
        }
        if (reduced) {
            reducedFrom = fade
            reducedSnapped = false
        } else if (expanded) {
            dockTo(1.0)
            if (springs[DISC] > .001) launched = true
        }
        events = events or CHANGED
    }
    private fun press(down: Boolean, row: Int = -1) {
        held = down
        if (row < 0) {
            pressed = -1
            pressFrom = springs[FOLDER_PRESS]
            pressAge = 0.0
        } else {
            pressed = row
            rowFrom = springs[ROW_PRESS]
            rowAge = 0.0
        }
    }
    private fun automatic(before: Double) {
        if ((before < .86 && loop >= .86) || (before < 6.2 - .14 && loop >= 6.2 - .14)) press(true)
        if ((before < 1 && loop >= 1) || (before < 6.2 && loop >= 6.2)) {
            press(false)
            toggle()
        }
        if (before < 4.3 && loop >= 4.3) press(true, 2)
        if (before < 4.3 + .14 && loop >= 4.3 + .14) {
            press(false, 2)
            select(2)
        }
    }
    private fun follow(at: Int, target: Double, k: Double, c: Double, h: Double) {
        springs[at + 1] += (k * (target - springs[at]) - c * springs[at + 1]) * h
        springs[at] += springs[at + 1] * h
        if (abs(springs[at] - target) < .0001 && abs(springs[at + 1]) < .001) {
            springs[at] = target
            springs[at + 1] = 0.0
        }
    }
    private fun resetLoop() {
        sim = BurstSimulation(n, reduced)
        loop = 0.0
        idleTime = 0.0
        idleGain = 0.0
        paused = false
        resumeAt = 0.0
        recovering = false
        held = false
        touching = false
        expanded = false
        age = 0.0
        rowsStarted = false
        launched = false
        returning = false
        home = true
        dock = 0.0
        dockFrom = 0.0
        dockTarget = 0.0
        dockAge = 1.0
        springs.fill(0.0)
        pressFrom = 0.0
        pressAge = 1.0
        rowFrom = 0.0
        rowAge = 1.0
        pressed = -1
        selected = -1
        previous = -1
        selectionAge = 1.0
        selectedMix.fill(0.0)
        landed = 0
        events = CHANGED
        sounded = false
        shutSounded = true
        fade = 1.0
        reducedFrom = 1.0
        reducedSnapped = true
    }
    fun step(dt: Double, layout: BurstLayout) {
        accumulator += dt.coerceIn(0.0, .05)
        val h = 1.0 / 240
        while (accumulator + 1e-10 >= h) {
            accumulator = max(0.0, accumulator - h)
            time += h
            age += h
            if (auto && !reduced) {
                if (paused && !held && !touching && time >= resumeAt) {
                    recovering = true
                    if (expanded) toggle()
                    if (home && dock == 0.0) resetLoop()
                }
                if (!paused) {
                    val before = loop
                    loop += h
                    automatic(before)
                    if (loop >= 10 - 1e-9) resetLoop()
                }
            }
            if (auto && !paused) {
                idleTime = loop
                idleGain = BurstPose.smooth(loop / .6) * (1 - BurstPose.smooth((loop - 9.4) / .6))
            } else {
                idleTime += h
                idleGain += ((if (recovering) 0.0 else 1.0) - idleGain) * (1 - exp(-8 * h))
            }
            selectionAge += h
            pressAge += h
            rowAge += h
            for (i in 0 until n) {
                val goal = if (selected == i) 1.0 else 0.0
                selectedMix[i] += (goal - selectedMix[i]).coerceIn(-h / .16, h / .16)
            }
            if (held && pressed < 0) springs[FOLDER_PRESS] = pressFrom + (1 - pressFrom) * BurstPose.bezier(pressAge / .12, .23, 1.0, .32, 1.0)
            else follow(FOLDER_PRESS, 0.0, 300.0, 20.0, h)
            if (held && pressed >= 0) springs[ROW_PRESS] = rowFrom + (1 - rowFrom) * BurstPose.bezier(rowAge / .12, .23, 1.0, .32, 1.0)
            else {
                follow(ROW_PRESS, 0.0, 300.0, 20.0, h)
                if (abs(springs[ROW_PRESS]) < .0001) pressed = -1
            }
            if (reduced) {
                if (!reducedSnapped && age >= .12) {
                    reducedSnapped = true
                    dock = if (expanded) 1.0 else 0.0
                    springs[LEAN] = if (expanded) .72 else 0.0
                    springs[DISC] = dock
                    sim = BurstSimulation(n, true)
                    if (expanded) sim.command(true)
                    home = !expanded
                }
                fade = if (reducedSnapped) BurstPose.clamp((age - .12) / .22) else reducedFrom * (1 - BurstPose.clamp(age / .12))
                if (home && (serial == 0 || age > .34)) fade = 1.0
            } else {
                if (expanded) {
                    if (age >= .56 && !launched) {
                        launched = true
                        springs[RECOIL] += .6
                    }
                    if (age >= .76 && !rowsStarted) {
                        sim.command(true)
                        rowsStarted = true
                    }
                } else if (!home) {
                    if (age >= .09 && !rowsStarted) {
                        sim.command(false)
                        rowsStarted = true
                    }
                    if (age >= .09 + (n - 1) * .050) {
                        returning = true
                        launched = false
                    }
                    if (returning && springs[DISC] < .04 && sim.completed && sim.target == 0) {
                        home = true
                        dockTo(0.0)
                        events = events or CHANGED
                    }
                }
                val lean = if (home) 0.0 else if (expanded) {
                    if (age < .12) springs[LEAN] else if (rowsStarted && sim.completed) .72 else 1.0
                } else 1.0
                follow(LEAN, lean, if (home) 220.0 else if (lean == .72) 120.0 else 170.0,
                    if (home) 22.0 else if (lean == .72) 18.0 else 16.0, h)
                if (!shutSounded && home && springs[LEAN] < .1) {
                    shutSounded = true
                    events = events or SOFT
                }
                follow(DISC, if (launched) 1.0 else 0.0, if (launched) 90.0 else 25.0, if (launched) 14.0 else 9.0, h)
                follow(RISE, if (expanded) 24.0 else 0.0, 70.0, 16.0, h)
                follow(RECOIL, 0.0, 260.0, 14.0, h)
                follow(KICK, 0.0, 260.0, 14.0, h)
                dockAge += h
                dock = if (dockAge >= .56) dockTarget else dockFrom + (dockTarget - dockFrom) * BurstPose.bezier(dockAge / .56, .77, 0.0, .175, 1.0)
            }
            val rowEvents = sim.step(h, layout, BurstPose.folder(this, layout))
            if (!reduced) {
                if ((rowEvents and BurstSimulation.EJECT) != 0) {
                    springs[RECOIL] += .35
                    springs[KICK] += 1.5
                    if (!sounded) {
                        events = events or MEDIUM
                        sounded = true
                    }
                }
                if ((rowEvents and BurstSimulation.GULP) != 0) {
                    springs[RECOIL] -= .3
                    springs[KICK] += 1
                }
            }
            var landedNow = 0
            if (expanded) sim.rows.forEachIndexed { i, r ->
                if (r.p == 1.0 && r.v == 0.0 && (!reduced || r.fade == 1.0)) landedNow = landedNow or (1 shl i)
            }
            if (landedNow != landed) {
                landed = landedNow
                events = events or CHANGED
            }
        }
    }
    companion object {
        const val LEAN = 0
        const val DISC = 2
        const val RECOIL = 4
        const val KICK = 6
        const val FOLDER_PRESS = 8
        const val ROW_PRESS = 10
        const val RISE = 12
        const val CHANGED = 1
        const val LIGHT = 2
        const val MEDIUM = 4
        const val SELECTION = 8
        const val SOFT = 16
    }
}

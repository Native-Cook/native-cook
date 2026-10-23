package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal enum class DragMultiSelectPhase { Idle, Selecting, Downloading, Done, Ready }
internal enum class DragMultiSelectHaptic { Enter, Tick, Cover, Commit, Success }

private enum class DragMultiSelectPointer { None, Pending, ColumnPending, Drag, Scroll, Control }

internal data class DragMultiSelectEpisode(
    val number: Int,
    val title: String,
    val minutes: Int,
    val guest: String,
)

internal val DragMultiSelectEpisodes = listOf(
    DragMultiSelectEpisode(48, "The chapter that wouldn’t end", 64, "Mara Okafor"),
    DragMultiSelectEpisode(47, "Writing in a second language", 52, "Tomasz Wilk"),
    DragMultiSelectEpisode(46, "Notebooks, not outlines", 71, "Inés Calderón"),
    DragMultiSelectEpisode(45, "The red pen, live in Brooklyn", 38, "Live"),
    DragMultiSelectEpisode(44, "Starting over at sixty", 58, "Ruth Abernathy"),
    DragMultiSelectEpisode(43, "How to translate a joke", 47, "Kenji Sato"),
    DragMultiSelectEpisode(42, "Q&A: when to quit a draft", 41, "Listener questions"),
    DragMultiSelectEpisode(41, "Down the research hole", 66, "Priya Natarajan"),
    DragMultiSelectEpisode(40, "Short stories as sketches", 44, "Leo Brandt"),
    DragMultiSelectEpisode(39, "Writing the city you left", 55, "Amara Diallo"),
    DragMultiSelectEpisode(38, "Endings", 49, "Sofia Marchetti"),
    DragMultiSelectEpisode(37, "What the first reader said", 53, "Jonah Reyes"),
)

internal class DragMultiSelectEngine(
    val autoPlay: Boolean,
    val reducedMotion: Boolean,
    private val onHaptic: (DragMultiSelectHaptic) -> Unit,
) {
    private val n = DragMultiSelectEpisodes.size
    val selected = BooleanArray(n)
    val presence = FloatArray(n)
    private val presenceVelocity = FloatArray(n)
    val shown = FloatArray(n)
    val progress = FloatArray(n)
    val downloaded = BooleanArray(n)
    val marks = FloatArray(n)
    private val fadeSelection = BooleanArray(n)

    var phase = DragMultiSelectPhase.Idle
        private set
    var selecting = false
        private set
    var modeAmount = 0f
        private set
    private var modeVelocity = 0f
    var lift = 0f
        private set
    private var liftVelocity = 0f
    var sheetAlpha = 1f
        private set
    var scroll = 0f
        private set
    var passAmount = 0f
        private set
    var ghostX = 0f
        private set
    var ghostY = 0f
        private set
    var ghostAlpha = 0f
        private set
    var ghostPressed = 0f
        private set

    var width = 393f
        private set
    var height = 852f
        private set
    var safeTop = 0f
        private set
    var safeBottom = 0f
        private set
    var cardTop = 48f
        private set
    var sectionTop = 216f
        private set
    var listTop = 240f
        private set
    var panelTop = 700f
        private set
    var panelBottom = 16f
        private set

    private var sheetFading = false
    private var dragging = false
    private var anchor = 0
    private var current = -1
    private var dragTarget = true
    private val dragBase = BooleanArray(n)
    private var downloadTime = 0f
    private var doneTime = 0f
    private var downloadRows = IntArray(0)

    private var pointer = DragMultiSelectPointer.None
    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var fingerY = 0f
    private var downRow = 0
    private var downSeconds = 0.0
    private var coastVelocity = 0f
    private var edgeVelocity = 0f

    private var silent = false
    private var paused = !autoPlay
    private var quietSeconds = 0f
    private var tidying = false
    private var tidyTime = 0f
    private var tidyFrom = 0f
    private var scriptTime = 0f
    private var scriptDown = false
    private var passClosed = false
    private var ghostTargetAlpha = 0f
    private var ghostTargetPressed = 0f
    private var activeAnimation = true

    val selectedCount: Int get() = selected.count { it }
    val downloadedCount: Int get() = downloaded.count { it }
    val selectedMinutes: Int get() = DragMultiSelectEpisodes.indices.sumOf { if (selected[it]) DragMultiSelectEpisodes[it].minutes else 0 }
    val offlineMinutes: Int get() = DragMultiSelectEpisodes.indices.sumOf {
        if (selected[it] || downloaded[it]) DragMultiSelectEpisodes[it].minutes else 0
    }
    val isPassOpen: Boolean get() = phase == DragMultiSelectPhase.Ready && !passClosed
    val needsFrame: Boolean get() = (autoPlay && !reducedMotion) || touching || activeAnimation || coastVelocity != 0f || tidying

    fun setSize(width: Float, height: Float, safeTop: Float, safeBottom: Float) {
        this.width = width
        this.height = height
        this.safeTop = safeTop
        this.safeBottom = safeBottom
        cardTop = safeTop + DragMultiSelectTokens.Layout.nav + DragMultiSelectTokens.Layout.cardGap
        sectionTop = cardTop + DragMultiSelectTokens.Layout.cardHeight + DragMultiSelectTokens.Layout.sectionGap
        listTop = sectionTop + DragMultiSelectTokens.Layout.sectionHeight + DragMultiSelectTokens.Layout.sectionAfter
        panelBottom = max(safeBottom, DragMultiSelectTokens.Layout.edge)
        panelTop = height - panelBottom - DragMultiSelectTokens.Layout.panelHeight
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun step(dtRaw: Float, nowSeconds: Double) {
        val dt = min(dtRaw.coerceAtLeast(0f), .05f)
        activeAnimation = false

        if (touching && (pointer == DragMultiSelectPointer.Pending || pointer == DragMultiSelectPointer.ColumnPending) &&
            (nowSeconds - downSeconds) * 1_000.0 >= DragMultiSelectTokens.Select.longPressMs
        ) {
            enter(downRow)
            dragBegin(downRow, true)
            pointer = DragMultiSelectPointer.Drag
        }

        if (dragging && touching && !scriptDown) autoScroll(dt)
        if (!dragging && coastVelocity != 0f) {
            val before = scroll
            scroll = (scroll + coastVelocity * dt).coerceIn(0f, maxScroll())
            coastVelocity *= exp(-dt * DragMultiSelectTokens.Select.coastDecay)
            if (abs(coastVelocity) < DragMultiSelectTokens.Select.coastStop || scroll == before) coastVelocity = 0f
            activeAnimation = activeAnimation || coastVelocity != 0f
        }

        if (autoPlay && !reducedMotion) {
            if (paused) {
                if (!touching) quietSeconds += dt
                val busy = dragging || phase == DragMultiSelectPhase.Downloading || phase == DragMultiSelectPhase.Done
                if (!tidying && quietSeconds >= DragMultiSelectTokens.resumeMs / 1000f && !busy) {
                    tidying = true
                    tidyTime = 0f
                    tidyFrom = scroll
                    silent = true
                    resetDone()
                }
                if (tidying) {
                    tidyTime += dt
                    scroll = tidyFrom * (1f - easeInOut(tidyTime / DragMultiSelectTokens.Demo.tidySeconds))
                    if (tidyTime >= DragMultiSelectTokens.Demo.tidySeconds) {
                        tidying = false
                        scriptTime = 0f
                        paused = false
                    }
                    activeAnimation = true
                }
            } else {
                silent = true
                val before = scriptTime
                var after = before + dt
                if (after >= LOOP / 1000f) {
                    runScript(before, LOOP / 1000f)
                    resetDone()
                    after -= LOOP / 1000f
                    scriptTime = 0f
                    runScript(0f, after)
                } else {
                    runScript(before, after)
                }
                scriptTime = after
                activeAnimation = true
            }
        }

        stepSprings(dt)
        stepDownload(dt)
        stepMarks(dt)
        stepGhost(dt)
        val passTarget = if (isPassOpen) 1f else 0f
        val passRate = dt / if (passTarget > passAmount) DragMultiSelectTokens.passRiseSeconds else DragMultiSelectTokens.passOutSeconds
        val oldPass = passAmount
        passAmount = if (passTarget > passAmount) min(passTarget, passAmount + passRate) else max(passTarget, passAmount - passRate)
        activeAnimation = activeAnimation || oldPass != passAmount
    }

    fun touchDown(x: Float, y: Float, nowSeconds: Double) {
        touching = true
        quietSeconds = 0f
        tidying = false
        if (!paused) {
            paused = true
            scriptDown = false
            dragEnd()
        }
        silent = false
        ghostTargetAlpha = 0f
        ghostTargetPressed = 0f
        downX = x
        downY = y
        lastY = y
        fingerY = y
        downSeconds = nowSeconds
        coastVelocity = 0f

        if (isPassOpen) {
            passClosed = true
            pointer = DragMultiSelectPointer.Control
            return
        }
        if (y in safeTop..(safeTop + DragMultiSelectTokens.Layout.nav) && x > width - 92f) {
            if (phase != DragMultiSelectPhase.Downloading && phase != DragMultiSelectPhase.Done) {
                if (selecting) exitSelection() else enter(-1)
            }
            pointer = DragMultiSelectPointer.Control
            return
        }
        if (y in (panelTop + DragMultiSelectTokens.Layout.cells + DragMultiSelectTokens.Layout.cellsGap)..
            (panelTop + DragMultiSelectTokens.Layout.panelHeight)
        ) {
            startDownload()
            pointer = DragMultiSelectPointer.Control
            return
        }
        if (y in listTop..panelTop) {
            downRow = rowAt(listY(y))
            pointer = if (selecting && x <= DragMultiSelectTokens.Layout.textX + 4f) {
                DragMultiSelectPointer.ColumnPending
            } else {
                DragMultiSelectPointer.Pending
            }
        } else {
            pointer = DragMultiSelectPointer.Scroll
        }
    }

    fun touchMove(x: Float, y: Float, velocityY: Float) {
        fingerY = y
        val distance = abs(y - downY)
        when (pointer) {
            DragMultiSelectPointer.ColumnPending -> if (distance > DragMultiSelectTokens.Select.dragSlop) {
                dragBegin(downRow, null)
                pointer = DragMultiSelectPointer.Drag
            }
            DragMultiSelectPointer.Pending -> if (distance > DragMultiSelectTokens.Select.dragSlop) {
                pointer = DragMultiSelectPointer.Scroll
            }
            DragMultiSelectPointer.Drag -> dragMove(listY(y))
            DragMultiSelectPointer.Scroll -> {
                scroll = (scroll - (y - lastY)).coerceIn(0f, maxScroll())
                coastVelocity = -velocityY
            }
            else -> Unit
        }
        lastY = y
        @Suppress("UNUSED_VARIABLE") val keepSignatureParity = x
    }

    fun touchUp(velocityY: Float) {
        when (pointer) {
            DragMultiSelectPointer.Pending, DragMultiSelectPointer.ColumnPending -> if (selecting) toggle(downRow)
            DragMultiSelectPointer.Drag -> {
                if (edgeVelocity != 0f && !reducedMotion) coastVelocity = edgeVelocity
                dragEnd()
            }
            DragMultiSelectPointer.Scroll -> coastVelocity = (-velocityY).coerceIn(-1_800f, 1_800f)
            else -> Unit
        }
        pointer = DragMultiSelectPointer.None
        touching = false
        edgeVelocity = 0f
        quietSeconds = 0f
        activeAnimation = true
    }

    fun accessibilityActivate(row: Int) {
        if (!selecting) enter(row) else toggle(row)
    }

    private fun enter(row: Int) {
        if (selecting || phase == DragMultiSelectPhase.Downloading) return
        selecting = true
        phase = DragMultiSelectPhase.Selecting
        sheetFading = false
        sheetAlpha = 1f
        fadeSelection.fill(false)
        progress.fill(0f)
        val next = BooleanArray(n)
        if (row in 0 until n && !downloaded[row]) next[row] = true
        publish(next, DragMultiSelectHaptic.Enter)
    }

    private fun exitSelection() {
        if (!selecting) return
        selecting = false
        dragging = false
        phase = if (phase == DragMultiSelectPhase.Done) DragMultiSelectPhase.Ready else DragMultiSelectPhase.Idle
        var any = false
        for (i in 0 until n) {
            fadeSelection[i] = presence[i] > .05f
            any = any || fadeSelection[i]
            selected[i] = false
        }
        sheetFading = any
        sheetAlpha = 1f
        activeAnimation = true
    }

    private fun toggle(row: Int) {
        if (!selecting || phase != DragMultiSelectPhase.Selecting || downloaded[row]) return
        val next = selected.copyOf()
        next[row] = !next[row]
        publish(next, DragMultiSelectHaptic.Tick)
    }

    private fun publish(next: BooleanArray, haptic: DragMultiSelectHaptic) {
        val before = selectedMinutes
        next.copyInto(selected)
        val after = selectedMinutes
        if (!silent) onHaptic(if (haptic == DragMultiSelectHaptic.Tick && before < FLIGHT_MINUTES && after >= FLIGHT_MINUTES) DragMultiSelectHaptic.Cover else haptic)
        activeAnimation = true
    }

    private fun dragBegin(row: Int, force: Boolean?) {
        if (!selecting || phase != DragMultiSelectPhase.Selecting) return
        dragging = true
        anchor = row
        current = row
        selected.copyInto(dragBase)
        dragTarget = force ?: !selected[row]
        if (!downloaded[row] && selected[row] != dragTarget) {
            val next = selected.copyOf()
            next[row] = dragTarget
            publish(next, DragMultiSelectHaptic.Tick)
        }
    }

    private fun dragMove(y: Float) {
        if (!dragging) return
        val row = rowAt(y)
        if (row == current) return
        current = row
        val first = min(anchor, row)
        val last = max(anchor, row)
        val next = dragBase.copyOf()
        for (i in first..last) if (!downloaded[i]) next[i] = dragTarget
        if (!next.contentEquals(selected)) publish(next, DragMultiSelectHaptic.Tick)
    }

    private fun dragEnd() {
        dragging = false
        activeAnimation = true
    }

    private fun startDownload() {
        if (phase != DragMultiSelectPhase.Selecting || selectedCount == 0) return
        dragging = false
        phase = DragMultiSelectPhase.Downloading
        downloadRows = selected.indices.filter { selected[it] }.toIntArray()
        downloadTime = 0f
        if (!silent) onHaptic(DragMultiSelectHaptic.Commit)
        activeAnimation = true
    }

    private fun resetDone() {
        if (selecting) exitSelection()
        downloaded.fill(false)
        progress.fill(0f)
        marks.fill(0f)
        if (phase == DragMultiSelectPhase.Ready || phase == DragMultiSelectPhase.Done) phase = DragMultiSelectPhase.Idle
        passClosed = false
        activeAnimation = true
    }

    private fun stepSprings(dt: Float) {
        val modeSpring = if (reducedMotion) DragMultiSelectTokens.Springs.reduced else DragMultiSelectTokens.Springs.mode
        spring(modeAmount, modeVelocity, if (selecting) 1f else 0f, dt, modeSpring).also {
            activeAnimation = activeAnimation || it.first != modeAmount || it.second != modeVelocity
            modeAmount = it.first
            modeVelocity = it.second
        }
        for (i in 0 until n) {
            val target = if (fadeSelection[i]) 1f else if (selected[i]) 1f else 0f
            val choice = if (reducedMotion) DragMultiSelectTokens.Springs.reduced else if (target >= presence[i]) DragMultiSelectTokens.Springs.join else DragMultiSelectTokens.Springs.leave
            val next = spring(presence[i], presenceVelocity[i], target, dt, choice)
            activeAnimation = activeAnimation || next.first != presence[i] || next.second != presenceVelocity[i]
            presence[i] = next.first
            presenceVelocity[i] = next.second
        }
        if (sheetFading) {
            sheetAlpha = max(0f, sheetAlpha - dt / DragMultiSelectTokens.sheetFadeSeconds)
            activeAnimation = true
            if (sheetAlpha == 0f) {
                for (i in 0 until n) if (fadeSelection[i]) {
                    presence[i] = 0f
                    presenceVelocity[i] = 0f
                    fadeSelection[i] = false
                }
                sheetFading = false
                sheetAlpha = 1f
            }
        }
        shape()
        val liftTarget = if (selecting) if (dragging && !reducedMotion) 1f else DragMultiSelectTokens.Select.restLift else 0f
        val liftSpring = if (reducedMotion) DragMultiSelectTokens.Springs.reduced else DragMultiSelectTokens.Springs.lift
        spring(lift, liftVelocity, liftTarget, dt, liftSpring).also {
            activeAnimation = activeAnimation || it.first != lift || it.second != liftVelocity
            lift = it.first
            liftVelocity = it.second
        }
    }

    private fun stepDownload(dt: Float) {
        if (phase == DragMultiSelectPhase.Downloading) {
            downloadTime += dt
            for (k in downloadRows.indices) {
                val row = downloadRows[k]
                val p = easeInOut((downloadTime - k * DragMultiSelectTokens.Download.staggerSeconds) / DragMultiSelectTokens.Download.rowSeconds)
                progress[row] = p
                if (p >= .97f) downloaded[row] = true
            }
            val finish = (downloadRows.size - 1) * DragMultiSelectTokens.Download.staggerSeconds + DragMultiSelectTokens.Download.rowSeconds
            if (downloadTime >= finish) {
                downloadRows.forEach { downloaded[it] = true }
                phase = DragMultiSelectPhase.Done
                doneTime = 0f
                if (!silent) onHaptic(DragMultiSelectHaptic.Success)
            }
            activeAnimation = true
        } else if (phase == DragMultiSelectPhase.Done) {
            doneTime += dt
            if (doneTime >= DragMultiSelectTokens.Download.settleSeconds) exitSelection()
            activeAnimation = true
        }
    }

    private fun stepMarks(dt: Float) {
        val amount = dt / DragMultiSelectTokens.Download.markSeconds
        for (i in 0 until n) {
            val target = if (downloaded[i]) 1f else 0f
            val old = marks[i]
            marks[i] = if (target > old) min(target, old + amount) else max(target, old - amount)
            activeAnimation = activeAnimation || old != marks[i]
        }
    }

    private fun stepGhost(dt: Float) {
        fun move(value: Float, target: Float, rate: Float): Float =
            if (target > value) min(target, value + rate) else max(target, value - rate)
        val oldA = ghostAlpha
        val oldP = ghostPressed
        ghostAlpha = move(ghostAlpha, ghostTargetAlpha, dt / DragMultiSelectTokens.fingerFadeSeconds)
        ghostPressed = move(ghostPressed, ghostTargetPressed, dt / DragMultiSelectTokens.pressSeconds)
        activeAnimation = activeAnimation || oldA != ghostAlpha || oldP != ghostPressed
    }

    private fun runScript(t0: Float, t1: Float) {
        fun cross(at: Float) = t0 < at && t1 >= at
        fun screenY(row: Int) = listTop - scroll + rowCenter(row)
        val demo = DragMultiSelectTokens.Demo
        val longPress = DragMultiSelectTokens.Select.longPressMs / 1000f
        if (cross(demo.holdDown)) {
            ghostX = width * .5f
            ghostY = screenY(demo.holdRow)
            ghostTargetAlpha = 1f
            ghostTargetPressed = 1f
        }
        if (cross(demo.holdDown + longPress)) {
            enter(demo.holdRow)
            dragBegin(demo.holdRow, true)
            scriptDown = true
        }
        if (scriptDown && t1 >= demo.dragStart && t1 < demo.release) {
            val u = easeInOut((t1 - demo.dragStart) / (demo.dragEnd - demo.dragStart))
            val y = rowCenter(0) + (rowCenter(demo.dragTo) - rowCenter(0)) * u
            dragMove(y)
            ghostY = listTop - scroll + y
        }
        if (cross(demo.release)) {
            scriptDown = false
            dragEnd()
            ghostTargetPressed = 0f
            ghostTargetAlpha = 0f
        }
        if (cross(demo.tapAt - .3f)) {
            ghostX = DragMultiSelectTokens.Layout.numberX + DragMultiSelectTokens.Layout.numberW / 2f + 6f
            ghostY = screenY(demo.tapRow)
            ghostTargetAlpha = 1f
            ghostTargetPressed = 0f
        }
        if (cross(demo.tapAt - .08f)) ghostTargetPressed = 1f
        if (cross(demo.tapAt)) {
            toggle(demo.tapRow)
            ghostTargetPressed = 0f
            ghostTargetAlpha = 0f
        }
        if (cross(demo.downloadAt - .3f)) {
            ghostX = width * .5f
            ghostY = panelTop + DragMultiSelectTokens.Layout.panelHeight - DragMultiSelectTokens.Layout.button / 2f
            ghostTargetAlpha = 1f
            ghostTargetPressed = 0f
        }
        if (cross(demo.downloadAt - .08f)) ghostTargetPressed = 1f
        if (cross(demo.downloadAt)) {
            startDownload()
            ghostTargetPressed = 0f
            ghostTargetAlpha = 0f
        }
        if (cross(demo.resetAt)) resetDone()
    }

    private fun autoScroll(dt: Float) {
        val zone = DragMultiSelectTokens.Layout.edgeZone
        val bottom = panelTop - DragMultiSelectTokens.Layout.edge / 2f
        val speed = when {
            fingerY > bottom - zone -> min((fingerY - (bottom - zone)) / zone, 1f)
            fingerY < listTop + zone -> -min((listTop + zone - fingerY) / zone, 1f)
            else -> 0f
        }
        edgeVelocity = speed * DragMultiSelectTokens.Select.autoScrollMax
        if (speed == 0f) return
        val before = scroll
        scroll = (scroll + edgeVelocity * dt).coerceIn(0f, maxScroll())
        if (scroll != before) dragMove(listY(fingerY))
    }

    private fun shape() {
        val full = .98f
        val gone = .004f
        var i = 0
        while (i < n) {
            if (presence[i] < gone) {
                shown[i] = 0f
                i++
                continue
            }
            val start = i
            while (i < n && presence[i] >= gone) i++
            val end = i - 1
            var first = -1
            var last = -1
            for (k in start..end) if (presence[k] >= full) {
                if (first < 0) first = k
                last = k
            }
            for (k in start..end) shown[k] = presence[k]
            if (first < 0) {
                var total = 0f
                for (k in start..end) total += presence[k]
                val upward = dragging && current < anchor
                for (j in 0..(end - start)) shown[if (upward) end - j else start + j] = clamp01(total - j)
                continue
            }
            var tail = 0f
            for (k in (last + 1)..end) if (k in 0 until n) tail += presence[k]
            for (k in (last + 1)..end) if (k in 0 until n) shown[k] = clamp01(tail - (k - last - 1))
            var head = 0f
            for (k in start until first) head += presence[k]
            for (k in first - 1 downTo start) shown[k] = clamp01(head - (first - 1 - k))
        }
    }

    fun share(row: Int): Float = clamp01(max(shown[row] * easedSheetAlpha(), marks[row]))

    fun coverage(): Float = clamp01(DragMultiSelectEpisodes.indices.sumOf { (DragMultiSelectEpisodes[it].minutes * share(it)).toDouble() }.toFloat() / FLIGHT_MINUTES)

    fun planeProgress(): Float {
        if (phase == DragMultiSelectPhase.Downloading) {
            var got = 0f
            var all = 0f
            for (row in downloadRows) {
                got += DragMultiSelectEpisodes[row].minutes * progress[row]
                all += DragMultiSelectEpisodes[row].minutes
            }
            return if (all > 0f) got / all else 0f
        }
        return if (phase == DragMultiSelectPhase.Done || phase == DragMultiSelectPhase.Ready) 1f else 0f
    }

    private fun easedSheetAlpha() = if (sheetFading) 1f - easeOut(1f - sheetAlpha) else 1f
    private fun listY(screenY: Float) = screenY + scroll - listTop
    private fun rowAt(y: Float) = floor(y / DragMultiSelectTokens.Layout.row).toInt().coerceIn(0, n - 1)
    private fun rowCenter(row: Int) = (row + .5f) * DragMultiSelectTokens.Layout.row
    private fun maxScroll() = max(0f, n * DragMultiSelectTokens.Layout.row - (panelTop - listTop))

    private fun spring(
        x0: Float,
        v0: Float,
        target: Float,
        dt: Float,
        spec: DragMultiSelectTokens.Spring,
    ): Pair<Float, Float> {
        val omega = (2f * PI.toFloat()) / (spec.durationMs / 1000f)
        val stiffness = omega * omega
        val damping = 2f * spec.dampingRatio * omega
        val slices = max(1, ceil(dt / .004f).toInt())
        val h = dt / slices
        var x = x0
        var velocity = v0
        repeat(slices) {
            velocity += (-stiffness * (x - target) - damping * velocity) * h
            x += velocity * h
        }
        return if (abs(x - target) < .0005f && abs(velocity) < .002f) target to 0f else x to velocity
    }

    private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, input: Float): Float {
        val u = clamp01(input)
        val cx = 3f * x1
        val bx = 3f * (x2 - x1) - cx
        val ax = 1f - cx - bx
        val cy = 3f * y1
        val by = 3f * (y2 - y1) - cy
        val ay = 1f - cy - by
        var t = u
        repeat(6) {
            val x = ((ax * t + bx) * t + cx) * t - u
            val dx = (3f * ax * t + 2f * bx) * t + cx
            if (abs(x) >= 1e-5f && abs(dx) >= 1e-6f) t -= x / dx
        }
        if (t !in 0f..1f) {
            var low = 0f
            var high = 1f
            t = u
            repeat(20) {
                val x = ((ax * t + bx) * t + cx) * t
                if (x < u) low = t else high = t
                t = (low + high) / 2f
            }
        }
        return ((ay * t + by) * t + cy) * t
    }

    private fun easeOut(x: Float) = cubicBezier(.23f, 1f, .32f, 1f, x)
    private fun easeInOut(x: Float) = cubicBezier(.77f, 0f, .175f, 1f, x)
    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    companion object {
        const val FLIGHT_MINUTES = 325
    }
}

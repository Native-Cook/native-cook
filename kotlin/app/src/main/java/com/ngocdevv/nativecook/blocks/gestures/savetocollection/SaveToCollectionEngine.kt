package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class SaveToCollectionEngine(
    val layout: SaveToCollectionLayout,
    private val autoPlay: Boolean,
    val reducedMotion: Boolean,
    private val onHaptic: (SaveToCollectionHaptic) -> Unit,
) {
    private enum class PointerTarget { None, Tile, Save, Toast, Close }

    class AnimatedValue(initial: Float) {
        private enum class Kind { Timing, Spring }

        var value: Float = initial
            private set
        var running: Boolean = false
            private set
        private var from = initial
        private var to = initial
        private var startedAt = 0.0
        private var duration = 0.0
        private var damping = 1f
        private var ease = SaveToCollectionEase.Linear
        private var kind = Kind.Timing

        fun snap(next: Float) {
            value = next
            running = false
        }

        fun timing(next: Float, seconds: Double, easing: SaveToCollectionEase, now: Double) {
            from = value
            to = next
            startedAt = now
            duration = max(seconds, 0.0001)
            ease = easing
            kind = Kind.Timing
            running = true
        }

        fun spring(next: Float, seconds: Double, ratio: Float, now: Double) {
            from = value
            to = next
            startedAt = now
            duration = max(seconds, 0.0001)
            damping = ratio
            kind = Kind.Spring
            running = true
        }

        fun update(now: Double) {
            if (!running) return
            val raw = ((now - startedAt) / duration).toFloat().coerceIn(0f, 1f)
            if (raw >= 1f) {
                value = to
                running = false
                return
            }
            val progress = when (kind) {
                Kind.Timing -> saveToCollectionEase(raw, ease)
                Kind.Spring -> saveToCollectionSpring(raw, damping)
            }
            value = from + (to - from) * progress
        }
    }

    val lift = AnimatedValue(0f)
    val centerX = AnimatedValue(0f)
    val centerY = AnimatedValue(0f)
    val lean = AnimatedValue(0f)
    val pull = AnimatedValue(0f)
    val drop = AnimatedValue(0f)
    val gulp = AnimatedValue(1f)
    val roll = AnimatedValue(1f)
    val mark = AnimatedValue(0f)
    val toast = AnimatedValue(0f)
    val sheet = AnimatedValue(0f)
    val arrive = AnimatedValue(1f)
    val press = AnimatedValue(0f)
    val ghostOpacity = AnimatedValue(0f)
    val ghostPress = AnimatedValue(0f)

    var carriedIndex: Int = -1
        private set
    var markedIndex: Int = -1
        private set
    var count: Int = SaveToCollectionTokens.CountBefore
        private set
    var sheetOpen: Boolean = false
        private set
    var ghostX: Float = 0f
        private set
    var ghostY: Float = 0f
        private set

    private var now = 0.0
    private var lastFrameNanos = 0L
    private var demoClock = 0.0
    private var paused = !autoPlay || reducedMotion
    private var silent = autoPlay
    private var touching = false
    private var quietStartedAt = -1.0
    private var resumeAt = -1.0
    private var arriveAt = -1.0
    private var returning = false
    private var landing = false
    private var closingSheet = false
    private var gulpStage = 0
    private var pointerTarget = PointerTarget.None
    private var pointerTile = -1
    private var pointerDownX = 0f
    private var pointerDownY = 0f
    private var pointerX = 0f
    private var pointerY = 0f
    private var pointerDownAt = 0.0
    private var carryOffsetX = 0f
    private var carryOffsetY = 0f
    private var magnetNear = false
    private var demoPreviousFingerX = 0f

    val needsFrames: Boolean
        get() = autoPlay || touching || hasAnimations() || arriveAt >= 0.0 || returning || landing || gulpStage != 0

    fun frame(frameNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameNanos
            return
        }
        val delta = min((frameNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0, 0.05)
        lastFrameNanos = frameNanos
        now += delta

        updateAnimations()
        updateCompletion()
        updateHeldPhoto()

        if (autoPlay && !reducedMotion) {
            if (paused) updateResume()
            else updateDemo(delta)
        }
    }

    fun pointerDown(x: Float, y: Float) {
        touching = true
        quietStartedAt = -1.0
        resumeAt = -1.0
        pauseDemo()
        pointerDownX = x
        pointerDownY = y
        pointerX = x
        pointerY = y
        pointerDownAt = now
        pointerTile = -1
        pointerTarget = when {
            sheet.value > 0.01f && isSheetClose(x, y) -> PointerTarget.Close
            sheet.value > 0.01f && y < currentSheetTop() -> PointerTarget.Close
            sheet.value > 0.01f -> PointerTarget.None
            toast.value > 0.1f && isToast(x, y) -> PointerTarget.Toast
            distanceToSave(x, y) <= SaveToCollectionTokens.SaveSize / 2f + 8f -> {
                press.timing(1f, 0.100, SaveToCollectionEase.EaseOut, now)
                PointerTarget.Save
            }
            else -> {
                pointerTile = tileAt(x, y)
                if (pointerTile >= 0) PointerTarget.Tile else PointerTarget.None
            }
        }
    }

    fun pointerMove(x: Float, y: Float, velocityX: Float) {
        pointerX = x
        pointerY = y
        if (carriedIndex >= 0 && !landing) {
            moveCarried(x, y, velocityX)
            return
        }
        if (pointerTarget == PointerTarget.Tile && hypot(x - pointerDownX, y - pointerDownY) > 12f) {
            pointerTile = -1
            pointerTarget = PointerTarget.None
        }
        if (pointerTarget == PointerTarget.Save && hypot(x - pointerDownX, y - pointerDownY) > 12f) {
            press.timing(0f, SaveToCollectionTokens.PressOutDuration, SaveToCollectionEase.EaseOut, now)
            pointerTarget = PointerTarget.None
        }
    }

    fun pointerUp(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        val target = pointerTarget
        pointerTarget = PointerTarget.None
        pointerTile = -1
        touching = false
        press.timing(0f, SaveToCollectionTokens.PressOutDuration, SaveToCollectionEase.EaseOut, now)
        if (carriedIndex >= 0 && !landing) {
            releaseCarried()
        } else if (hypot(x - pointerDownX, y - pointerDownY) <= 16f) {
            when (target) {
                PointerTarget.Save, PointerTarget.Toast -> toggleSheet()
                PointerTarget.Close -> closeSheet()
                else -> Unit
            }
        }
        if (carriedIndex < 0) quietStartedAt = now
    }

    fun cancelPointer() {
        pointerTarget = PointerTarget.None
        pointerTile = -1
        touching = false
        press.timing(0f, SaveToCollectionTokens.PressOutDuration, SaveToCollectionEase.EaseOut, now)
        if (carriedIndex >= 0 && !landing) releaseCarried()
        if (carriedIndex < 0) quietStartedAt = now
    }

    fun accessibilitySaveLake(): Boolean {
        pauseDemo()
        if (count > SaveToCollectionTokens.CountBefore || carriedIndex >= 0) return false
        val tile = layout.tiles[SaveToCollectionTokens.DemoTile]
        pickUp(SaveToCollectionTokens.DemoTile, tile.centerX, tile.centerY)
        centerX.snap(layout.saveX)
        centerY.snap(layout.saveY)
        pull.snap(1f)
        releaseCarried()
        return true
    }

    fun accessibilityOpenCollection(): Boolean {
        pauseDemo()
        toggleSheet()
        return true
    }

    private fun updateAnimations() {
        listOf(
            lift, centerX, centerY, lean, pull, drop, gulp, roll, mark, toast,
            sheet, arrive, press, ghostOpacity, ghostPress,
        ).forEach { value -> value.update(now) }
    }

    private fun updateCompletion() {
        if (landing && !drop.running) landPhoto()
        if (returning && !lift.running && !centerX.running && !centerY.running) {
            returning = false
            carriedIndex = -1
            pull.snap(0f)
            drop.snap(0f)
            if (!touching) quietStartedAt = now
        }
        if (closingSheet && !sheet.running) {
            closingSheet = false
            sheetOpen = false
        }
        if (arriveAt >= 0.0 && now >= arriveAt) {
            arriveAt = -1.0
            arrive.timing(
                1f,
                if (reducedMotion) 0.160 else SaveToCollectionTokens.ArriveDuration,
                SaveToCollectionEase.EaseOut,
                now,
            )
        }
        if (gulpStage == 1 && !gulp.running) {
            gulpStage = 2
            gulp.spring(1f, SaveToCollectionTokens.GulpDuration, SaveToCollectionTokens.GulpDamping, now)
        } else if (gulpStage == 2 && !gulp.running) {
            gulpStage = 0
        }
        if (markedIndex >= 0 && count == SaveToCollectionTokens.CountBefore && !mark.running && mark.value <= 0f) {
            markedIndex = -1
        }
    }

    private fun updateHeldPhoto() {
        if (!touching || pointerTarget != PointerTarget.Tile || pointerTile < 0 || carriedIndex >= 0) return
        if (now - pointerDownAt >= SaveToCollectionTokens.HoldSeconds) {
            pickUp(pointerTile, pointerDownX, pointerDownY)
            moveCarried(pointerX, pointerY, 0f)
        }
    }

    private fun pauseDemo() {
        silent = false
        if (!autoPlay || paused) return
        paused = true
        ghostOpacity.timing(0f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
        ghostPress.snap(0f)
        if (carriedIndex >= 0 && !landing) releaseCarried()
    }

    private fun updateResume() {
        if (touching || carriedIndex >= 0) return
        if (resumeAt >= 0.0) {
            if (now >= resumeAt) {
                resumeAt = -1.0
                demoClock = 0.0
                paused = false
            }
            return
        }
        if (quietStartedAt >= 0.0 && now - quietStartedAt >= SaveToCollectionTokens.ResumeSeconds) {
            silent = true
            resetAll()
            quietStartedAt = -1.0
            resumeAt = now + SaveToCollectionTokens.TidySeconds
        }
    }

    private fun updateDemo(delta: Double) {
        val previous = demoClock
        var next = previous + delta
        if (crossed(previous, next, SaveToCollectionTokens.DemoFingerIn)) {
            val tile = layout.tiles[SaveToCollectionTokens.DemoTile]
            ghostX = tile.centerX
            ghostY = tile.y + tile.height * 0.42f
            demoPreviousFingerX = ghostX
            ghostOpacity.timing(1f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoDown)) {
            ghostPress.timing(1f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoLift)) {
            pickUp(SaveToCollectionTokens.DemoTile, ghostX, ghostY)
        }
        if (carriedIndex >= 0 && !landing && next >= SaveToCollectionTokens.DemoCarryStart &&
            next <= SaveToCollectionTokens.DemoCarryEnd + 0.12
        ) {
            val tile = layout.tiles[SaveToCollectionTokens.DemoTile]
            val startX = tile.centerX
            val startY = tile.y + tile.height * 0.42f
            val raw = ((next - SaveToCollectionTokens.DemoCarryStart) /
                (SaveToCollectionTokens.DemoCarryEnd - SaveToCollectionTokens.DemoCarryStart)).toFloat().coerceIn(0f, 1f)
            val eased = (1f - cos(PI.toFloat() * raw)) / 2f
            val targetX = layout.saveX - carryOffsetX
            val targetY = layout.saveY - carryOffsetY
            val nextX = startX + (targetX - startX) * eased - sin(eased * PI.toFloat()) * 28f
            val nextY = startY + (targetY - startY) * eased
            val velocityX = (nextX - demoPreviousFingerX) / max(delta.toFloat(), 0.008f)
            demoPreviousFingerX = nextX
            ghostX = nextX
            ghostY = nextY
            moveCarried(nextX, nextY, velocityX)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoRelease)) {
            releaseCarried()
            ghostPress.timing(0f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
            ghostOpacity.timing(0f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoTapIn)) {
            ghostX = layout.saveX
            ghostY = layout.saveY
            ghostOpacity.timing(1f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoTapDown)) {
            ghostPress.timing(1f, 0.100, SaveToCollectionEase.Linear, now)
            press.timing(1f, 0.100, SaveToCollectionEase.EaseOut, now)
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoTap)) {
            ghostPress.timing(0f, SaveToCollectionTokens.PressOutDuration, SaveToCollectionEase.Linear, now)
            ghostOpacity.timing(0f, SaveToCollectionTokens.FingerDuration, SaveToCollectionEase.Linear, now)
            press.timing(0f, SaveToCollectionTokens.PressOutDuration, SaveToCollectionEase.EaseOut, now)
            openSheet()
        }
        if (crossed(previous, next, SaveToCollectionTokens.DemoClose)) closeSheet()
        if (crossed(previous, next, SaveToCollectionTokens.DemoReset)) resetAll()
        if (next >= SaveToCollectionTokens.LoopSeconds) next -= SaveToCollectionTokens.LoopSeconds
        demoClock = next
    }

    private fun pickUp(index: Int, x: Float, y: Float) {
        if (index !in layout.tiles.indices || carriedIndex >= 0 || sheet.value > 0.01f) return
        val tile = layout.tiles[index]
        carriedIndex = index
        centerX.snap(tile.centerX)
        centerY.snap(tile.centerY)
        carryOffsetX = tile.centerX - x
        carryOffsetY = tile.centerY - y
        drop.snap(0f)
        pull.snap(0f)
        magnetNear = false
        if (reducedMotion) lift.snap(1f)
        else lift.spring(1f, SaveToCollectionTokens.LiftDuration, SaveToCollectionTokens.LiftDamping, now)
        emit(SaveToCollectionHaptic.Lift)
    }

    private fun moveCarried(x: Float, y: Float, velocityX: Float) {
        if (carriedIndex < 0 || landing) return
        centerX.snap(x + carryOffsetX)
        centerY.snap(y + carryOffsetY)
        if (!reducedMotion) {
            val target = (velocityX * SaveToCollectionTokens.LeanPerVelocity)
                .coerceIn(-SaveToCollectionTokens.LeanMaximum, SaveToCollectionTokens.LeanMaximum)
            lean.spring(target, SaveToCollectionTokens.HomeDuration, SaveToCollectionTokens.HomeDamping, now)
        }
        val nextPull = smoothStep(SaveToCollectionTokens.MagnetFar, SaveToCollectionTokens.MagnetNear, distanceToSave(centerX.value, centerY.value))
        pull.snap(nextPull)
        val nextNear = nextPull > 0.5f
        if (nextNear && !magnetNear) emit(SaveToCollectionHaptic.Magnet)
        magnetNear = nextNear
    }

    private fun releaseCarried() {
        val index = carriedIndex
        if (index < 0 || landing) return
        if (distanceToSave(centerX.value, centerY.value) < SaveToCollectionTokens.DropRadius || pull.value > 0.5f) {
            landing = true
            drop.timing(
                1f,
                if (reducedMotion) 0.120 else SaveToCollectionTokens.DropDuration,
                SaveToCollectionEase.EaseInOut,
                now,
            )
            return
        }
        val tile = layout.tiles[index]
        returning = true
        pull.timing(0f, 0.200, SaveToCollectionEase.EaseOut, now)
        lean.spring(0f, SaveToCollectionTokens.HomeDuration, SaveToCollectionTokens.HomeDamping, now)
        val duration = if (reducedMotion) SaveToCollectionTokens.ReducedDuration else SaveToCollectionTokens.HomeDuration
        val damping = if (reducedMotion) 1f else SaveToCollectionTokens.HomeDamping
        centerX.spring(tile.centerX, duration, damping, now)
        centerY.spring(tile.centerY, duration, damping, now)
        lift.spring(0f, duration, damping, now)
    }

    private fun landPhoto() {
        val index = carriedIndex
        landing = false
        carriedIndex = -1
        lift.snap(0f)
        lean.snap(0f)
        pull.snap(0f)
        drop.snap(0f)
        gulpStage = if (reducedMotion) 0 else 1
        if (reducedMotion) gulp.snap(1f)
        else gulp.timing(0.86f, 0.070, SaveToCollectionEase.EaseOut, now)
        roll.snap(0f)
        roll.timing(1f, if (reducedMotion) 0.001 else SaveToCollectionTokens.RollDuration, SaveToCollectionEase.EaseOut, now)
        count = SaveToCollectionTokens.CountBefore + 1
        markedIndex = index
        mark.timing(1f, SaveToCollectionTokens.MarkDuration, SaveToCollectionEase.EaseOut, now)
        toast.timing(1f, SaveToCollectionTokens.ToastInDuration, SaveToCollectionEase.EaseOut, now)
        emit(SaveToCollectionHaptic.Success)
        if (!touching) quietStartedAt = now
    }

    private fun toggleSheet() {
        if (sheet.value > 0.5f) closeSheet() else openSheet()
    }

    private fun openSheet() {
        if (carriedIndex >= 0) return
        sheetOpen = true
        closingSheet = false
        toast.timing(0f, SaveToCollectionTokens.ToastOutDuration, SaveToCollectionEase.EaseOut, now)
        if (reducedMotion) sheet.timing(1f, 0.200, SaveToCollectionEase.Linear, now)
        else sheet.spring(1f, SaveToCollectionTokens.SheetDuration, SaveToCollectionTokens.SheetDamping, now)
        val fresh = markedIndex >= 0
        arrive.snap(if (fresh) 0f else 1f)
        arriveAt = if (fresh) now + SaveToCollectionTokens.ArriveDelay else -1.0
        emit(SaveToCollectionHaptic.Select)
    }

    private fun closeSheet() {
        if (sheet.value <= 0f && !sheetOpen) return
        closingSheet = true
        sheet.timing(0f, SaveToCollectionTokens.SheetOutDuration, SaveToCollectionEase.EaseOut, now)
    }

    private fun resetAll() {
        if (sheet.value > 0f || sheetOpen) closeSheet()
        toast.timing(0f, SaveToCollectionTokens.ToastOutDuration, SaveToCollectionEase.EaseOut, now)
        mark.timing(0f, SaveToCollectionTokens.MarkDuration, SaveToCollectionEase.EaseOut, now)
        if (markedIndex >= 0) {
            roll.snap(0f)
            roll.timing(1f, if (reducedMotion) 0.001 else SaveToCollectionTokens.RollDuration, SaveToCollectionEase.EaseOut, now)
        }
        count = SaveToCollectionTokens.CountBefore
    }

    private fun tileAt(x: Float, y: Float): Int {
        return layout.tiles.indexOfFirst { tile -> tile.contains(x, y) && y < layout.barY - 8f }
    }

    private fun currentSheetTop(): Float {
        return layout.sheetY + (1f - sheet.value) * (layout.height - layout.sheetY)
    }

    private fun isSheetClose(x: Float, y: Float): Boolean {
        val top = currentSheetTop()
        return x >= layout.width - 60f && y in (top + 16f)..(top + 72f)
    }

    private fun isToast(x: Float, y: Float): Boolean {
        val top = layout.barY - 56f
        return x in 64f..(layout.width - 64f) && y in top..(top + 44f)
    }

    private fun distanceToSave(x: Float, y: Float): Float {
        return hypot(x - layout.saveX, y - layout.saveY)
    }

    private fun emit(kind: SaveToCollectionHaptic) {
        if (!silent) onHaptic(kind)
    }

    private fun hasAnimations(): Boolean {
        return listOf(
            lift, centerX, centerY, lean, pull, drop, gulp, roll, mark, toast,
            sheet, arrive, press, ghostOpacity, ghostPress,
        ).any { value -> value.running }
    }

    private fun crossed(previous: Double, next: Double, event: Double): Boolean {
        return previous < event && next >= event
    }
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val progress = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

private fun saveToCollectionEase(progress: Float, ease: SaveToCollectionEase): Float {
    return when (ease) {
        SaveToCollectionEase.Linear -> progress
        SaveToCollectionEase.EaseOut -> cubicBezier(progress, 0.23f, 1f, 0.32f, 1f)
        SaveToCollectionEase.EaseInOut -> cubicBezier(progress, 0.77f, 0f, 0.175f, 1f)
    }
}

private fun cubicBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    var parameter = progress.coerceIn(0f, 1f)
    repeat(6) {
        val inverse = 1f - parameter
        val x = 3f * inverse * inverse * parameter * x1 +
            3f * inverse * parameter * parameter * x2 + parameter * parameter * parameter
        val derivative = 3f * inverse * inverse * x1 +
            6f * inverse * parameter * (x2 - x1) + 3f * parameter * parameter * (1f - x2)
        if (derivative > 0.0001f) parameter = (parameter - (x - progress) / derivative).coerceIn(0f, 1f)
    }
    val inverse = 1f - parameter
    return 3f * inverse * inverse * parameter * y1 +
        3f * inverse * parameter * parameter * y2 + parameter * parameter * parameter
}

private fun saveToCollectionSpring(progress: Float, damping: Float): Float {
    val ratio = damping.coerceIn(0.05f, 1f)
    val omega = 9f
    fun response(time: Float): Float {
        if (ratio >= 0.999f) return 1f - exp(-omega * time) * (1f + omega * time)
        val root = sqrt(1f - ratio * ratio)
        val damped = omega * root
        return 1f - exp(-ratio * omega * time) *
            (cos(damped * time) + ratio / root * sin(damped * time))
    }
    val end = response(1f)
    return if (end == 0f) progress else response(progress) / end
}

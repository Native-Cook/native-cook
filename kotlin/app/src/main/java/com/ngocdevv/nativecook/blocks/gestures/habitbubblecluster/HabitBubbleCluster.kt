package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

const val LOOP: Long = Tokens.LOOP_MS

private class HabitBubbleScript {
    var timeMs = 0.0
    var paused = false
    var realDown = false
    var quietMs = 0.0
    var segment = 0
    var scriptedDown = false
    var fingerX = 0.0
    var fingerY = 0.0
    var fingerAlpha = 0.0
}

private class HabitBubbleDetailTransition {
    var value = 0.0
    var start = 0.0
    var target = 0.0
    var elapsedMs = 0.0

    fun setVisible(visible: Boolean) {
        val next = if (visible) 1.0 else 0.0
        if (target == next) {
            return
        }
        start = value
        target = next
        elapsedMs = 0.0
    }

    fun step(frameMs: Double) {
        if (value == target) {
            return
        }
        elapsedMs += frameMs
        val duration = if (target > start) Tokens.DETAIL.fadeMs.toDouble() else Tokens.DETAIL.fadeOutMs.toDouble()
        val progress = min(1.0, elapsedMs / duration)
        val eased = cubicBezier(progress, Tokens.DETAIL.ease[0], Tokens.DETAIL.ease[1], Tokens.DETAIL.ease[2], Tokens.DETAIL.ease[3])
        value = start + (target - start) * eased
        if (progress >= 1.0) {
            value = target
        }
    }
}

/**
 * A faithful Compose port of the approved React Native habit cluster.
 * Hold to fill, drag and fling, tap for detail, or switch between the two sets.
 */
@Composable
fun HabitBubbleCluster(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val view = LocalView.current
    val simulation = remember { HabitBubbleClusterSimulation() }
    val script = remember { HabitBubbleScript() }
    val detailTransition = remember { HabitBubbleDetailTransition() }
    val atlas = ImageBitmap.imageResource(R.drawable.habitbubblecluster_glass_atlas)
    val speckle = ImageBitmap.imageResource(R.drawable.habitbubblecluster_speckle)
    val paints = remember { HabitBubblePaints() }
    var tick by remember { mutableLongStateOf(0L) }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    var stageWidth by remember { mutableDoubleStateOf(0.0) }
    var stageHeight by remember { mutableDoubleStateOf(0.0) }
    var originX by remember { mutableDoubleStateOf(0.0) }
    val originY = Tokens.clusterY

    fun haptic(event: Int) {
        when (event) {
            HabitBubbleEvent.TICK,
            HabitBubbleEvent.GRAB,
            -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            HabitBubbleEvent.DONE -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
            )
            HabitBubbleEvent.UNDO -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun flush(scripted: Boolean) {
        if (!scripted) {
            simulation.events.forEach(::haptic)
        }
        simulation.events.clear()
    }

    fun open(index: Int) {
        simulation.openFocus(
            index = index,
            targetX = stageWidth / 2.0 - originX,
            targetY = stageHeight * Tokens.DETAIL.y - originY,
        )
        detailTransition.setVisible(true)
    }

    fun release(velocityX: Double, velocityY: Double, scripted: Boolean) {
        val tapped = simulation.touchUp(velocityX, velocityY, reducedMotion)
        flush(scripted)
        if (tapped >= 0) {
            open(tapped)
        }
    }

    fun pauseForTouch(down: Boolean) {
        if (down && !script.paused && script.scriptedDown) {
            release(0.0, 0.0, true)
        }
        if (down) {
            script.fingerAlpha = 0.0
        }
        script.paused = true
        script.realDown = down
        script.quietMs = 0.0
        script.segment = 0
        script.scriptedDown = false
    }

    fun switchTab(tab: Int, scripted: Boolean) {
        simulation.setTab(tab)
        flush(scripted)
    }

    fun closeDetail() {
        simulation.closeFocus()
        detailTransition.setVisible(false)
    }

    fun runScript(frameMs: Double): Boolean {
        val time = script.timeMs
        fun at(start: Long): Boolean {
            return time >= start && time < start + Tokens.AUTOPLAY.tapMs
        }
        val holding = time >= Tokens.AUTOPLAY.holdStart && time < Tokens.AUTOPLAY.holdEnd
        val segment = when {
            holding -> 5
            at(Tokens.AUTOPLAY.tapAt) -> 6
            at(Tokens.AUTOPLAY.openAt) -> 1
            at(Tokens.AUTOPLAY.backAt) -> 2
            at(Tokens.AUTOPLAY.personalAt) -> 3
            at(Tokens.AUTOPLAY.socialAt) -> 4
            else -> 0
        }
        var down = script.scriptedDown
        if (segment != script.segment) {
            if (down) {
                release(0.0, 0.0, true)
                down = false
            }
            when (segment) {
                5,
                6,
                -> {
                    val index = simulation.indexOf(Tokens.AUTOPLAY.holdId)
                    script.fingerX = simulation.x[index]
                    script.fingerY = simulation.y[index]
                    script.fingerAlpha = 1.0
                    simulation.touchDown(script.fingerX, script.fingerY)
                    flush(true)
                    down = true
                }
                1 -> {
                    val index = simulation.indexOf(Tokens.AUTOPLAY.openId)
                    script.fingerX = simulation.x[index]
                    script.fingerY = simulation.y[index]
                    script.fingerAlpha = 1.0
                    simulation.touchDown(script.fingerX, script.fingerY)
                    flush(true)
                    down = true
                }
                2 -> {
                    script.fingerX = Tokens.LAYOUT.gutter + Tokens.LAYOUT.iconButton / 2.0 - originX
                    script.fingerY = safeTop + Tokens.LAYOUT.headerTop + Tokens.LAYOUT.control / 2.0 - originY
                    script.fingerAlpha = 1.0
                    closeDetail()
                }
                3 -> {
                    script.fingerX = Tokens.LAYOUT.gutter + Tokens.LAYOUT.segmentWidth * 0.75 - originX
                    script.fingerY = safeTop + Tokens.LAYOUT.headerTop + Tokens.LAYOUT.control / 2.0 - originY
                    script.fingerAlpha = 1.0
                    switchTab(1, true)
                }
                4 -> {
                    script.fingerX = Tokens.LAYOUT.gutter + Tokens.LAYOUT.segmentWidth * 0.25 - originX
                    script.fingerY = safeTop + Tokens.LAYOUT.headerTop + Tokens.LAYOUT.control / 2.0 - originY
                    script.fingerAlpha = 1.0
                    switchTab(0, true)
                }
                else -> script.fingerAlpha = 0.0
            }
        }
        var next = time + frameMs
        var resetAtLoop = false
        Tokens.QA_HOLD?.let { hold ->
            if (time >= Tokens.QA_HOLD_AT.getValue(hold)) {
                next = time
            }
        }
        if (next >= LOOP) {
            // A hard clock boundary guarantees the recorded final frame is the initial frame.
            simulation.reset()
            detailTransition.value = 0.0
            detailTransition.start = 0.0
            detailTransition.target = 0.0
            detailTransition.elapsedMs = 0.0
            next = 0.0
            script.segment = 0
            down = false
            script.fingerAlpha = 0.0
            resetAtLoop = true
        } else {
            script.segment = segment
        }
        script.timeMs = next
        script.scriptedDown = down
        return resetAtLoop
    }

    LaunchedEffect(autoPlay) {
        script.paused = !autoPlay
        if (autoPlay) {
            script.timeMs = 0.0
            script.quietMs = 0.0
            script.segment = 0
        }
    }

    LaunchedEffect(simulation, autoPlay, reducedMotion) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val rawSeconds = (nowNanos - previousNanos).coerceAtLeast(0L) / 1_000_000_000.0
            val frameSeconds = min(Tokens.maxWallDeltaSeconds, rawSeconds)
            val frameMs = frameSeconds * 1000.0
            previousNanos = nowNanos
            var resetAtLoop = false
            if (autoPlay) {
                if (script.paused) {
                    script.quietMs = if (script.realDown) 0.0 else script.quietMs + frameMs
                    if (script.quietMs >= Tokens.interactionResumeDelayMs) {
                        if (simulation.focus >= 0) {
                            closeDetail()
                        }
                        if (simulation.tab != 0) {
                            simulation.setTab(0)
                        }
                        simulation.entities.indices.forEach { index ->
                            if (simulation.doneTarget[index] > 0.5) {
                                simulation.setDone(index, false, reducedMotion)
                            }
                        }
                        flush(true)
                        script.paused = false
                        script.timeMs = 0.0
                        script.quietMs = 0.0
                        script.segment = 0
                        script.scriptedDown = false
                    }
                } else {
                    resetAtLoop = runScript(frameMs)
                }
            }
            if (!resetAtLoop) {
                simulation.step(frameMs, reducedMotion)
                detailTransition.step(frameMs)
            }
            flush(autoPlay && !script.paused)
            tick = nowNanos
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(parseColor(Tokens.COLOR.bg)),
    ) {
        val measuredWidth = maxWidth.value.toDouble()
        val measuredHeight = maxHeight.value.toDouble()
        SideEffect {
            stageWidth = measuredWidth
            stageHeight = measuredHeight
            originX = measuredWidth / 2.0 + Tokens.clusterX - 201.0
        }
        val stageDensity = density.density

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(simulation, stageWidth, stageHeight, reducedMotion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        val downX = down.position.x / stageDensity
                        val downY = down.position.y / stageDensity
                        pauseForTouch(true)
                        var released = false
                        var handledControl = false
                        val headerY = safeTop + Tokens.LAYOUT.headerTop
                        if (downY in headerY..headerY + Tokens.LAYOUT.control) {
                            when {
                                simulation.focus >= 0 && downX <= Tokens.LAYOUT.gutter + Tokens.LAYOUT.iconButton -> {
                                    closeDetail()
                                    handledControl = true
                                }
                                simulation.focus < 0 && downX in Tokens.LAYOUT.gutter..Tokens.LAYOUT.gutter + Tokens.LAYOUT.segmentWidth -> {
                                    val selected = if (downX < Tokens.LAYOUT.gutter + Tokens.LAYOUT.segmentWidth / 2.0) 0 else 1
                                    switchTab(selected, false)
                                    handledControl = true
                                }
                            }
                        }
                        if (!handledControl) {
                            simulation.touchDown(downX - originX, downY - originY)
                            flush(false)
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                if (!change.pressed) {
                                    if (!handledControl) {
                                        val velocity = tracker.calculateVelocity()
                                        release(
                                            velocityX = (velocity.x / stageDensity).toDouble(),
                                            velocityY = (velocity.y / stageDensity).toDouble(),
                                            scripted = false,
                                        )
                                    }
                                    released = true
                                    break
                                }
                                change.consume()
                                if (!handledControl) {
                                    simulation.touchMove(
                                        touchX = change.position.x / stageDensity - originX,
                                        touchY = change.position.y / stageDensity - originY,
                                    )
                                }
                            }
                        } finally {
                            if (!released && !handledControl) {
                                release(0.0, 0.0, false)
                            }
                            pauseForTouch(false)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                tick
                drawContext.canvas.nativeCanvas.run {
                    save()
                    scale(stageDensity, stageDensity)
                    drawHabitBubbleScreen(
                        canvas = this,
                        width = stageWidth.toFloat(),
                        height = stageHeight.toFloat(),
                        safeTop = safeTop,
                        originX = originX.toFloat(),
                        originY = originY.toFloat(),
                        simulation = simulation,
                        script = script,
                        detailProgress = detailTransition.value.toFloat(),
                        atlas = atlas.asAndroidBitmap(),
                        speckle = speckle.asAndroidBitmap(),
                        paints = paints,
                    )
                    restore()
                }
            }

            simulation.entities.forEachIndexed { index, entity ->
                if (index != simulation.quietIndex) {
                    val vector = iconFor(entity.habit.id)
                    val hue = Tokens.HUE.getValue(entity.habit.hue)
                    Icon(
                        imageVector = vector,
                        contentDescription = entity.habit.name,
                        tint = parseColor(hue.icon),
                        modifier = Modifier
                            .graphicsLayer {
                                tick
                                val scale = entity.habit.r * 2.0 * Tokens.LAYOUT.iconScale / Tokens.iconBox * (simulation.radius[index] / entity.habit.r)
                                translationX = ((originX + simulation.x[index] - Tokens.iconBox / 2.0) * stageDensity).toFloat()
                                translationY = ((originY + simulation.y[index] - Tokens.iconBox / 2.0) * stageDensity).toFloat()
                                scaleX = scale.toFloat()
                                scaleY = scale.toFloat()
                                alpha = (min(1.0, simulation.radius[index] / 8.0) * (1.0 - simulation.done[index])).toFloat()
                            }
                            .size(Tokens.iconBox.dp),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = parseColor(hue.core),
                        modifier = Modifier
                            .graphicsLayer {
                                tick
                                val radiusScale = simulation.radius[index] / (entity.habit.r * Tokens.HOLD.doneScale)
                                val base = entity.habit.r * 2.0 * Tokens.LAYOUT.iconScale / Tokens.iconBox
                                val scale = base * min(1.1, radiusScale) * (0.6 + 0.4 * simulation.done[index])
                                translationX = ((originX + simulation.x[index] - Tokens.iconBox / 2.0) * stageDensity).toFloat()
                                translationY = ((originY + simulation.y[index] - Tokens.iconBox / 2.0) * stageDensity).toFloat()
                                scaleX = scale.toFloat()
                                scaleY = scale.toFloat()
                                alpha = simulation.done[index].toFloat()
                            }
                            .size(Tokens.iconBox.dp),
                    )
                }
            }
        }
    }
}

private fun iconFor(id: String): ImageVector {
    return when (id) {
        "wave" -> Icons.Outlined.WavingHand
        "star" -> Icons.Outlined.StarBorder
        "gift" -> Icons.Outlined.ShoppingBasket
        "ball" -> Icons.Outlined.SportsBasketball
        "friend" -> Icons.Outlined.PersonOutline
        "art" -> Icons.Outlined.Palette
        "host" -> Icons.Outlined.Bed
        "magic" -> Icons.Outlined.AutoAwesome
        "home" -> Icons.Outlined.Home
        "tea" -> Icons.Outlined.LocalCafe
        "heart" -> Icons.Outlined.FavoriteBorder
        "wallet" -> Icons.Outlined.AccountBalanceWallet
        "ride" -> Icons.Outlined.DirectionsBike
        "note" -> Icons.Outlined.MailOutline
        else -> Icons.Outlined.Edit
    }
}

private fun cubicBezier(progress: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    fun sample(t: Double, first: Double, second: Double): Double {
        val inverse = 1.0 - t
        return 3.0 * inverse * inverse * t * first + 3.0 * inverse * t * t * second + t * t * t
    }
    fun slope(t: Double, first: Double, second: Double): Double {
        return 3.0 * (1.0 - t).pow(2.0) * first + 6.0 * (1.0 - t) * t * (second - first) + 3.0 * t * t * (1.0 - second)
    }
    var t = progress
    repeat(5) {
        val difference = sample(t, x1, x2) - progress
        val derivative = slope(t, x1, x2)
        if (kotlin.math.abs(derivative) > 0.00001) {
            t = (t - difference / derivative).coerceIn(0.0, 1.0)
        }
    }
    return sample(t, y1, y2)
}

private fun parseColor(value: String): Color {
    return Color(parseArgb(value))
}

private fun parseArgb(value: String): Int {
    if (value.startsWith("#")) {
        val raw = value.removePrefix("#")
        return when (raw.length) {
            6 -> (0xFF000000L or raw.toLong(16)).toInt()
            8 -> raw.toLong(16).toInt()
            else -> 0
        }
    }
    val values = value.substringAfter("rgba(").substringBefore(')').split(',')
    val alpha = (values[3].trim().toDouble() * 255.0).toInt().coerceIn(0, 255)
    return alpha shl 24 or (values[0].trim().toInt() shl 16) or (values[1].trim().toInt() shl 8) or values[2].trim().toInt()
}

private class HabitBubblePaints {
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = BlendMode.LIGHTEN
        } else {
            @Suppress("DEPRECATION")
            xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        }
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
}

private fun drawHabitBubbleScreen(
    canvas: AndroidCanvas,
    width: Float,
    height: Float,
    safeTop: Float,
    originX: Float,
    originY: Float,
    simulation: HabitBubbleClusterSimulation,
    script: HabitBubbleScript,
    detailProgress: Float,
    atlas: Bitmap,
    speckle: Bitmap,
    paints: HabitBubblePaints,
) {
    canvas.drawBitmap(speckle, Rect(0, 0, speckle.width, speckle.height), RectF(0f, 0f, width, height), paints.fill)
    if (simulation.tab == 1) {
        paints.fill.shader = LinearGradient(0f, 0f, 0f, height, parseArgb(Tokens.COLOR.personalTop), parseArgb(Tokens.COLOR.personalBottom), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paints.fill)
        paints.fill.shader = null
    }
    canvas.save()
    canvas.translate(originX, originY)
    drawBubbles(canvas, simulation, atlas, paints)
    if (script.fingerAlpha > 0.01) {
        paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.finger), script.fingerAlpha.toFloat())
        canvas.drawCircle(script.fingerX.toFloat(), script.fingerY.toFloat(), Tokens.LAYOUT.fingerRadius.toFloat(), paints.fill)
        paints.stroke.color = withAlpha(parseArgb(Tokens.COLOR.fingerRing), script.fingerAlpha.toFloat())
        paints.stroke.strokeWidth = 1.5f
        canvas.drawCircle(script.fingerX.toFloat(), script.fingerY.toFloat(), Tokens.LAYOUT.fingerRadius.toFloat(), paints.stroke)
    }
    canvas.restore()
    drawHomeChrome(canvas, width, height, safeTop, simulation, 1f - detailProgress, paints)
    drawDetailChrome(canvas, width, height, safeTop, simulation, detailProgress, paints)
}

private fun drawBubbles(
    canvas: AndroidCanvas,
    simulation: HabitBubbleClusterSimulation,
    atlas: Bitmap,
    paints: HabitBubblePaints,
) {
    simulation.entities.indices.forEach { index ->
        val bubbleRadius = simulation.radius[index].toFloat()
        if (bubbleRadius < 1f || index == simulation.quietIndex) {
            return@forEach
        }
        val entity = simulation.entities[index]
        val sourceIndex = atlasIndex(entity.habit.id)
        val half = bubbleRadius * Tokens.ATLAS.reach.toFloat()
        val left = sourceIndex % Tokens.ATLAS.columns * Tokens.ATLAS.size
        val top = floor(sourceIndex.toDouble() / Tokens.ATLAS.columns).toInt() * Tokens.ATLAS.size
        canvas.drawBitmap(
            atlas,
            Rect(left, top, left + Tokens.ATLAS.size, top + Tokens.ATLAS.size),
            RectF(
                simulation.x[index].toFloat() - half,
                simulation.y[index].toFloat() - half,
                simulation.x[index].toFloat() + half,
                simulation.y[index].toFloat() + half,
            ),
            paints.bitmap,
        )
    }
    drawQuiet(canvas, simulation, paints)
    simulation.entities.indices.forEach { index ->
        val bubbleRadius = simulation.radius[index].toFloat()
        if (bubbleRadius < 1f || index == simulation.quietIndex) {
            return@forEach
        }
        val entity = simulation.entities[index]
        val hue = Tokens.HUE.getValue(entity.habit.hue)
        val centerX = simulation.x[index].toFloat()
        val centerY = simulation.y[index].toFloat()
        val charge = if (simulation.ring == index) simulation.charge.toFloat() else 0f
        if (charge > 0f) {
            val level = centerY + bubbleRadius - 2f * bubbleRadius * charge
            val liquidPath = Path()
            val steps = 24
            repeat(steps + 1) { step ->
                val pointX = centerX - bubbleRadius + 2f * bubbleRadius * step / steps
                val wave = sin(simulation.clockMs / Tokens.LIQUID.period1 + step * 0.55) * Tokens.LIQUID.amp1 + sin(simulation.clockMs / Tokens.LIQUID.period2 - step * 0.9) * Tokens.LIQUID.amp2
                val pointY = level + wave.toFloat() * sin(PI * charge).toFloat()
                if (step == 0) {
                    liquidPath.moveTo(pointX, pointY)
                } else {
                    liquidPath.lineTo(pointX, pointY)
                }
            }
            liquidPath.lineTo(centerX + bubbleRadius, centerY + bubbleRadius)
            liquidPath.lineTo(centerX - bubbleRadius, centerY + bubbleRadius)
            liquidPath.close()
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(centerX, centerY, bubbleRadius - 1f, Path.Direction.CW) })
            paints.fill.shader = LinearGradient(
                centerX,
                level - Tokens.LIQUID.amp1.toFloat(),
                centerX,
                centerY + bubbleRadius,
                intArrayOf(withAlpha(parseArgb(hue.light), Tokens.LIQUID.top.toFloat()), withAlpha(parseArgb(hue.rim), Tokens.LIQUID.bottom.toFloat())),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(liquidPath, paints.fill)
            paints.fill.shader = null
            canvas.restore()
        }
        val completed = simulation.done[index].toFloat()
        if (completed > 0.01f) {
            paints.fill.shader = RadialGradient(
                centerX - bubbleRadius * 0.2f,
                centerY - bubbleRadius * 0.25f,
                bubbleRadius * 1.2f,
                intArrayOf(withAlpha(0xFFFFFFFF.toInt(), completed * 0.95f), withAlpha(parseArgb(hue.rim), completed * 0.95f), withAlpha(parseArgb(hue.rim), completed * 0.95f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(centerX, centerY, bubbleRadius, paints.fill)
            paints.fill.shader = null
        }
        paints.stroke.color = withAlpha(parseArgb(hue.light), if (entity.habit.hue == "glass") Tokens.GLASS.spriteEdgeGlass.toFloat() else Tokens.GLASS.spriteEdge.toFloat())
        paints.stroke.strokeWidth = 1.5f
        paints.stroke.maskFilter = null
        canvas.drawCircle(centerX, centerY, bubbleRadius - 0.75f, paints.stroke)
        val flash = simulation.flash[index]
        if (flash >= 0.0) {
            val progress = (flash / Tokens.BURST.flashMs).toFloat()
            paints.stroke.color = withAlpha(0xFFFFFFFF.toInt(), 1f - progress)
            paints.stroke.strokeWidth = 3f * (1f - progress) + 0.5f
            canvas.drawCircle(centerX, centerY, bubbleRadius + 4f * progress, paints.stroke)
        }
    }
    if (simulation.ring >= 0 && simulation.charge > 0.001) {
        val index = simulation.ring
        val hue = Tokens.HUE.getValue(simulation.entities[index].habit.hue)
        val ringRadius = simulation.radius[index].toFloat() + Tokens.LAYOUT.ringGap.toFloat()
        val bounds = RectF(
            simulation.x[index].toFloat() - ringRadius,
            simulation.y[index].toFloat() - ringRadius,
            simulation.x[index].toFloat() + ringRadius,
            simulation.y[index].toFloat() + ringRadius,
        )
        paints.stroke.strokeCap = Paint.Cap.ROUND
        paints.stroke.color = withAlpha(parseArgb(hue.rim), 0.8f)
        paints.stroke.strokeWidth = Tokens.LAYOUT.ring.toFloat() + 4f
        paints.stroke.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawArc(bounds, -90f, (360.0 * simulation.charge).toFloat(), false, paints.stroke)
        paints.stroke.color = withAlpha(0xFFFFFFFF.toInt(), 0.95f)
        paints.stroke.strokeWidth = Tokens.LAYOUT.ring.toFloat()
        paints.stroke.maskFilter = null
        canvas.drawArc(bounds, -90f, (360.0 * simulation.charge).toFloat(), false, paints.stroke)
        paints.stroke.strokeCap = Paint.Cap.BUTT
    }
    if (simulation.burst >= 0) {
        val index = simulation.burst
        val progress = (simulation.burstMs / Tokens.BURST.sparkMs).toFloat()
        val eased = 1f - (1f - progress).pow(3)
        val hue = Tokens.HUE.getValue(simulation.entities[index].habit.hue)
        repeat(Tokens.LAYOUT.sparks.toInt()) { spark ->
            val angle = spark / Tokens.LAYOUT.sparks * PI * 2.0 + index
            val far = if (spark % 2 == 0) Tokens.BURST.sparkFar else Tokens.BURST.sparkNear
            val distance = simulation.radius[index] + Tokens.BURST.sparkFrom + far * eased
            paints.fill.color = withAlpha(parseArgb(if (spark % 3 == 0) Tokens.COLOR.spark else hue.rim), 1f - progress)
            val sparkRadius = (if (spark % 2 == 0) 2.6f else 1.8f) * (1f - progress * 0.6f)
            canvas.drawCircle(
                (simulation.x[index] + cos(angle) * distance).toFloat(),
                (simulation.y[index] + sin(angle) * distance).toFloat(),
                sparkRadius,
                paints.fill,
            )
        }
    }
}

private fun drawQuiet(canvas: AndroidCanvas, simulation: HabitBubbleClusterSimulation, paints: HabitBubblePaints) {
    val index = simulation.quietIndex
    val radius = simulation.radius[index].toFloat()
    if (radius < 1f) {
        return
    }
    val centerX = simulation.x[index].toFloat()
    val centerY = simulation.y[index].toFloat()
    val scale = radius / (Tokens.QUIET.getValue("r") as Double).toFloat()
    paints.fill.shader = RadialGradient(
        centerX,
        centerY,
        radius * 1.25f,
        intArrayOf(withAlpha(parseArgb(Tokens.COLOR.quiet), 0.12f), withAlpha(parseArgb(Tokens.COLOR.quiet), 0.12f), withAlpha(parseArgb(Tokens.COLOR.bg), 0.12f)),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(centerX, centerY, radius * 1.25f, paints.fill)
    paints.fill.shader = RadialGradient(
        centerX,
        centerY,
        radius,
        intArrayOf(withAlpha(parseArgb(Tokens.COLOR.quietCore), Tokens.QUIET_GLASS.body.toFloat()), withAlpha(parseArgb(Tokens.COLOR.quietCore), Tokens.QUIET_GLASS.body.toFloat()), withAlpha(parseArgb(Tokens.COLOR.quiet), Tokens.QUIET_GLASS.body.toFloat())),
        floatArrayOf(0f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(centerX, centerY, radius, paints.fill)
    paints.fill.shader = null
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.quiet), Tokens.QUIET_GLASS.wedge.toFloat())
    val wedge = Path().apply {
        moveTo(centerX, centerY)
        arcTo(RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius), 150f, 140f)
        close()
    }
    canvas.drawPath(wedge, paints.fill)
    paints.fill.color = parseArgb(Tokens.COLOR.ink)
    canvas.drawCircle(centerX, centerY, 3.5f * scale, paints.fill)
    paints.stroke.color = withAlpha(parseArgb(Tokens.COLOR.ink), 0.25f)
    paints.stroke.strokeWidth = 1f
    canvas.drawCircle(centerX, centerY, 11f * scale, paints.stroke)
    drawArcText(canvas, Tokens.QUIET.getValue("title") as String, centerX, centerY, radius * 0.68f, (-PI / 2.0 + 0.25).toFloat(), true, paints)
    drawArcText(canvas, Tokens.QUIET.getValue("hours") as String, centerX, centerY, radius * 0.68f + 7f * scale, (PI / 2.0 - 0.35).toFloat(), false, paints)
}

private fun drawArcText(canvas: AndroidCanvas, text: String, centerX: Float, centerY: Float, radius: Float, middle: Float, inward: Boolean, paints: HabitBubblePaints) {
    paints.text.textSize = Tokens.TYPE.quiet.toFloat()
    paints.text.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paints.text.color = withAlpha(parseArgb(Tokens.COLOR.ink), 0.85f)
    paints.text.textAlign = Paint.Align.CENTER
    val widths = text.map { paints.text.measureText(it.toString()) + 1.2f }
    val direction = if (inward) 1f else -1f
    var angle = middle - direction * widths.sum() / radius / 2f
    text.forEachIndexed { index, character ->
        val step = widths[index] / radius
        val at = angle + direction * step / 2f
        canvas.save()
        canvas.translate(centerX + cos(at) * radius, centerY + sin(at) * radius)
        canvas.rotate(Math.toDegrees((at + if (inward) PI / 2.0 else -PI / 2.0).toDouble()).toFloat())
        canvas.drawText(character.toString(), 0f, if (inward) 0f else paints.text.textSize * 0.7f, paints.text)
        canvas.restore()
        angle += direction * step
    }
}

private fun drawHomeChrome(canvas: AndroidCanvas, width: Float, height: Float, safeTop: Float, simulation: HabitBubbleClusterSimulation, alpha: Float, paints: HabitBubblePaints) {
    if (alpha <= 0.001f) {
        return
    }
    val headerTop = safeTop + Tokens.LAYOUT.headerTop.toFloat()
    drawRoundRect(canvas, Tokens.LAYOUT.gutter.toFloat(), headerTop, Tokens.LAYOUT.segmentWidth.toFloat(), Tokens.LAYOUT.control.toFloat(), Tokens.LAYOUT.control.toFloat() / 2f, Tokens.COLOR.control, alpha, paints)
    val halfWidth = Tokens.LAYOUT.segmentWidth.toFloat() / 2f
    val selectedLeft = Tokens.LAYOUT.gutter.toFloat() + if (simulation.tab == 0) Tokens.SPACE.xs.toFloat() else halfWidth
    drawRoundRect(canvas, selectedLeft, headerTop + Tokens.SPACE.xs.toFloat(), halfWidth - Tokens.SPACE.xs.toFloat(), Tokens.LAYOUT.control.toFloat() - Tokens.SPACE.m.toFloat(), (Tokens.LAYOUT.control - Tokens.SPACE.m).toFloat() / 2f, Tokens.COLOR.controlSelected, alpha, paints)
    drawCenteredText(canvas, Tokens.COPY.tabs[0], Tokens.LAYOUT.gutter.toFloat() + halfWidth / 2f, headerTop + 25f, Tokens.TYPE.segmentSize.toFloat(), if (simulation.tab == 0) Tokens.COLOR.controlInkSelected else Tokens.COLOR.inkSoft, 700, alpha, paints)
    drawCenteredText(canvas, Tokens.COPY.tabs[1], Tokens.LAYOUT.gutter.toFloat() + halfWidth * 1.5f, headerTop + 25f, Tokens.TYPE.segmentSize.toFloat(), if (simulation.tab == 1) Tokens.COLOR.controlInkSelected else Tokens.COLOR.inkSoft, 700, alpha, paints)
    val profileCenter = width - Tokens.LAYOUT.gutter.toFloat() - Tokens.LAYOUT.iconButton.toFloat() / 2f + 3f
    val locationCenter = profileCenter - Tokens.LAYOUT.iconButton.toFloat() - Tokens.SPACE.x2.toFloat()
    drawCircleControl(canvas, locationCenter, headerTop + 20f, Tokens.LAYOUT.iconButton.toFloat() / 2f, alpha, paints)
    drawNavigationGlyph(canvas, locationCenter, headerTop + 20f, alpha, paints)
    drawAvatarPlaceholder(canvas, profileCenter, headerTop + 20f, alpha, paints)
    val personal = simulation.tab == 1
    val dateTop = if (personal) height * Tokens.LAYOUT.personalDateY.toFloat() else Tokens.LAYOUT.dateY.toFloat()
    if (personal) {
        drawCenteredText(canvas, Tokens.COPY.personalDate, width / 2f, dateTop + 33f, Tokens.TYPE.personalDateSize.toFloat(), Tokens.COLOR.ink, 500, alpha, paints)
        drawCenteredText(canvas, Tokens.COPY.now, width / 2f, dateTop + 65f, Tokens.TYPE.personalNowSize.toFloat(), Tokens.COLOR.inkSoft, 500, alpha, paints)
    } else {
        drawCenteredText(canvas, Tokens.COPY.date, width / 2f, dateTop + 23f, Tokens.TYPE.dateSize.toFloat(), Tokens.COLOR.ink, 500, alpha, paints)
        drawCenteredText(canvas, Tokens.COPY.now, width / 2f, dateTop + 48f, Tokens.TYPE.nowSize.toFloat(), Tokens.COLOR.inkSoft, 500, alpha, paints)
    }
    paints.fill.color = withAlpha(parseArgb(if (personal) Tokens.COLOR.discDim else Tokens.COLOR.disc), alpha)
    canvas.drawCircle(width / 2f, Tokens.LAYOUT.discY.toFloat(), Tokens.LAYOUT.disc.toFloat() / 2f, paints.fill)
    val dotX = if (personal) width * Tokens.LAYOUT.personalDotX.toFloat() else width / 2f + Tokens.LAYOUT.dotOffset.toFloat()
    val dotY = if (personal) height * Tokens.LAYOUT.personalDotY.toFloat() else Tokens.LAYOUT.discY.toFloat()
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.disc), alpha)
    canvas.drawCircle(dotX, dotY, Tokens.LAYOUT.dot.toFloat() / 2f, paints.fill)
    val cornerY = Tokens.LAYOUT.cornerY.toFloat()
    drawCircleControl(canvas, Tokens.LAYOUT.cornerInset.toFloat() + Tokens.LAYOUT.corner.toFloat() / 2f, cornerY, Tokens.LAYOUT.corner.toFloat() / 2f, alpha, paints)
    drawCenteredText(canvas, "•••", Tokens.LAYOUT.cornerInset.toFloat() + Tokens.LAYOUT.corner.toFloat() / 2f, cornerY + 5f, 20f, Tokens.COLOR.ink, 700, alpha, paints)
    drawCircleControl(canvas, width - Tokens.LAYOUT.cornerInset.toFloat() - Tokens.LAYOUT.corner.toFloat() / 2f, cornerY, Tokens.LAYOUT.corner.toFloat() / 2f, alpha, paints)
    drawCenteredText(canvas, "♫", width - Tokens.LAYOUT.cornerInset.toFloat() - Tokens.LAYOUT.corner.toFloat() / 2f, cornerY + 7f, 20f, Tokens.COLOR.ink, 500, alpha, paints)
}

private fun drawDetailChrome(canvas: AndroidCanvas, width: Float, height: Float, safeTop: Float, simulation: HabitBubbleClusterSimulation, alpha: Float, paints: HabitBubblePaints) {
    if (alpha <= 0.001f) {
        return
    }
    canvas.save()
    canvas.translate(0f, (1f - alpha) * 24f)
    val headerY = safeTop + Tokens.LAYOUT.headerTop.toFloat() + Tokens.LAYOUT.iconButton.toFloat() / 2f
    val leftX = Tokens.LAYOUT.gutter.toFloat() + Tokens.LAYOUT.iconButton.toFloat() / 2f
    val rightX = width - Tokens.LAYOUT.gutter.toFloat() - Tokens.LAYOUT.iconButton.toFloat() / 2f + 3f
    drawCircleControl(canvas, leftX, headerY, Tokens.LAYOUT.iconButton.toFloat() / 2f, alpha, paints)
    drawBackGlyph(canvas, leftX, headerY, alpha, paints)
    drawCircleControl(canvas, rightX, headerY, Tokens.LAYOUT.iconButton.toFloat() / 2f, alpha, paints)
    drawShareGlyph(canvas, rightX, headerY, alpha, paints)
    val open = simulation.entities.getOrNull(simulation.focus)
    drawCenteredText(canvas, Tokens.COPY.category, width / 2f, headerY - 4f, Tokens.TYPE.categorySize.toFloat(), Tokens.COLOR.inkSoft, 500, alpha, paints)
    val category = open?.let { Tokens.CATEGORY[it.habit.id] ?: it.habit.name }.orEmpty()
    drawCenteredText(canvas, category, width / 2f, headerY + 19f, Tokens.TYPE.categoryTitleSize.toFloat(), Tokens.COLOR.ink, 700, alpha, paints)
    val balanceTop = height * Tokens.LAYOUT.detailBalanceY.toFloat()
    drawRoundRect(canvas, Tokens.LAYOUT.cornerInset.toFloat(), balanceTop, width - Tokens.LAYOUT.cornerInset.toFloat() * 2f, Tokens.LAYOUT.rowHeight.toFloat(), 14f, Tokens.COLOR.card, alpha, paints)
    drawLeftText(canvas, Tokens.COPY.balance, Tokens.LAYOUT.cornerInset.toFloat() + Tokens.SPACE.xl.toFloat(), balanceTop + 37f, Tokens.TYPE.balanceSize.toFloat(), Tokens.COLOR.inkSoft, 500, alpha, paints)
    val goodWidth = 58f
    val goodLeft = width - Tokens.LAYOUT.cornerInset.toFloat() - Tokens.SPACE.xl.toFloat() - goodWidth
    drawRoundRect(canvas, goodLeft, balanceTop + 17f, goodWidth, 26f, 13f, Tokens.COLOR.good, alpha, paints)
    drawCenteredText(canvas, Tokens.COPY.good, goodLeft + goodWidth / 2f, balanceTop + 35f, Tokens.TYPE.goodSize.toFloat(), Tokens.COLOR.ink, 700, alpha, paints)
    val cardTop = height * Tokens.LAYOUT.detailCardY.toFloat()
    val cardPath = Path().apply {
        addRoundRect(RectF(Tokens.LAYOUT.cornerInset.toFloat(), cardTop, width - Tokens.LAYOUT.cornerInset.toFloat(), height + 20f), floatArrayOf(18f, 18f, 18f, 18f, 0f, 0f, 0f, 0f), Path.Direction.CW)
    }
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.card), alpha)
    canvas.drawPath(cardPath, paints.fill)
    val contentLeft = Tokens.LAYOUT.cornerInset.toFloat() + Tokens.SPACE.x3.toFloat()
    drawLeftText(canvas, Tokens.COPY.description, contentLeft, cardTop + Tokens.SPACE.cardTop.toFloat() + 23f, Tokens.TYPE.sectionTitleSize.toFloat(), Tokens.COLOR.ink, 600, alpha, paints)
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.hairline), alpha)
    canvas.drawRect(contentLeft, cardTop + 70f, width - contentLeft, cardTop + 71f, paints.fill)
    var bodyY = cardTop + 107f
    Tokens.COPY.body.forEach { paragraph ->
        bodyY = drawWrappedText(canvas, paragraph, contentLeft, bodyY, width - contentLeft * 2f, Tokens.TYPE.bodySize.toFloat(), Tokens.TYPE.bodyLine.toFloat(), Tokens.COLOR.inkSoft, alpha, paints)
        bodyY += Tokens.SPACE.x3.toFloat()
    }
    canvas.restore()
}

private fun drawWrappedText(canvas: AndroidCanvas, text: String, x: Float, startY: Float, width: Float, size: Float, lineHeight: Float, color: String, alpha: Float, paints: HabitBubblePaints): Float {
    configureText(paints.text, size, color, 400, alpha, Paint.Align.LEFT)
    var remaining = text
    var y = startY
    while (remaining.isNotBlank()) {
        var count = paints.text.breakText(remaining, true, width, null)
        if (count < remaining.length) {
            val whitespace = remaining.substring(0, count).lastIndexOf(' ')
            if (whitespace > 0) {
                count = whitespace
            }
        }
        val line = remaining.substring(0, count).trim()
        canvas.drawText(line, x, y, paints.text)
        y += lineHeight
        remaining = remaining.substring(count).trimStart()
    }
    return y
}

private fun drawCircleControl(canvas: AndroidCanvas, centerX: Float, centerY: Float, radius: Float, alpha: Float, paints: HabitBubblePaints) {
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.control), alpha)
    canvas.drawCircle(centerX, centerY, radius, paints.fill)
}

private fun drawNavigationGlyph(canvas: AndroidCanvas, centerX: Float, centerY: Float, alpha: Float, paints: HabitBubblePaints) {
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.ink), alpha)
    val path = Path().apply {
        moveTo(centerX - 6f, centerY - 7f)
        lineTo(centerX + 7f, centerY - 2f)
        lineTo(centerX + 1f, centerY + 1f)
        lineTo(centerX - 2f, centerY + 8f)
        close()
    }
    canvas.drawPath(path, paints.fill)
}

private fun drawAvatarPlaceholder(canvas: AndroidCanvas, centerX: Float, centerY: Float, alpha: Float, paints: HabitBubblePaints) {
    paints.fill.shader = LinearGradient(centerX - 20f, centerY - 20f, centerX + 20f, centerY + 20f, withAlpha(parseArgb("#6F5148"), alpha), withAlpha(parseArgb("#D29B7B"), alpha), Shader.TileMode.CLAMP)
    canvas.drawCircle(centerX, centerY, 20f, paints.fill)
    paints.fill.shader = null
    paints.fill.color = withAlpha(parseArgb("#342924"), alpha)
    canvas.drawCircle(centerX, centerY - 4f, 6f, paints.fill)
    canvas.drawOval(RectF(centerX - 11f, centerY + 3f, centerX + 11f, centerY + 20f), paints.fill)
    paints.fill.color = withAlpha(parseArgb(Tokens.COLOR.badge), alpha)
    canvas.drawCircle(centerX + 17f, centerY + 17f, 9f, paints.fill)
    paints.stroke.color = withAlpha(parseArgb(Tokens.COLOR.bg), alpha)
    paints.stroke.strokeWidth = Tokens.SPACE.hair.toFloat()
    canvas.drawCircle(centerX + 17f, centerY + 17f, 9f, paints.stroke)
    drawCenteredText(canvas, "★", centerX + 17f, centerY + 20f, 9f, Tokens.COLOR.ink, 700, alpha, paints)
}

private fun drawBackGlyph(canvas: AndroidCanvas, centerX: Float, centerY: Float, alpha: Float, paints: HabitBubblePaints) {
    paints.stroke.color = withAlpha(parseArgb(Tokens.COLOR.ink), alpha)
    paints.stroke.strokeWidth = 2f
    paints.stroke.strokeCap = Paint.Cap.ROUND
    val path = Path().apply {
        moveTo(centerX + 3f, centerY - 7f)
        lineTo(centerX - 4f, centerY)
        lineTo(centerX + 3f, centerY + 7f)
    }
    canvas.drawPath(path, paints.stroke)
    paints.stroke.strokeCap = Paint.Cap.BUTT
}

private fun drawShareGlyph(canvas: AndroidCanvas, centerX: Float, centerY: Float, alpha: Float, paints: HabitBubblePaints) {
    paints.stroke.color = withAlpha(parseArgb(Tokens.COLOR.ink), alpha)
    paints.stroke.strokeWidth = 1.5f
    canvas.drawCircle(centerX - 6f, centerY, 2.5f, paints.stroke)
    canvas.drawCircle(centerX + 5f, centerY - 6f, 2.5f, paints.stroke)
    canvas.drawCircle(centerX + 5f, centerY + 6f, 2.5f, paints.stroke)
    canvas.drawLine(centerX - 3.5f, centerY - 1f, centerX + 2.5f, centerY - 5f, paints.stroke)
    canvas.drawLine(centerX - 3.5f, centerY + 1f, centerX + 2.5f, centerY + 5f, paints.stroke)
}

private fun drawRoundRect(canvas: AndroidCanvas, x: Float, y: Float, width: Float, height: Float, radius: Float, color: String, alpha: Float, paints: HabitBubblePaints) {
    paints.fill.color = withAlpha(parseArgb(color), alpha)
    canvas.drawRoundRect(RectF(x, y, x + width, y + height), radius, radius, paints.fill)
}

private fun drawCenteredText(canvas: AndroidCanvas, text: String, x: Float, baseline: Float, size: Float, color: String, weight: Int, alpha: Float, paints: HabitBubblePaints) {
    configureText(paints.text, size, color, weight, alpha, Paint.Align.CENTER)
    canvas.drawText(text, x, baseline, paints.text)
}

private fun drawLeftText(canvas: AndroidCanvas, text: String, x: Float, baseline: Float, size: Float, color: String, weight: Int, alpha: Float, paints: HabitBubblePaints) {
    configureText(paints.text, size, color, weight, alpha, Paint.Align.LEFT)
    canvas.drawText(text, x, baseline, paints.text)
}

private fun configureText(paint: Paint, size: Float, color: String, weight: Int, alpha: Float, align: Paint.Align) {
    paint.textSize = size
    paint.color = withAlpha(parseArgb(color), alpha)
    paint.textAlign = align
    paint.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
    } else {
        Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}

private fun atlasIndex(id: String): Int {
    val resolved = Tokens.ATLAS.alias[id] ?: id
    val index = Tokens.ATLAS.order.indexOf(resolved)
    return if (index >= 0) index else Tokens.ATLAS.order.indexOf("journal")
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val sourceAlpha = color ushr 24 and 0xFF
    val resultAlpha = (sourceAlpha * alpha.coerceIn(0f, 1f)).toInt()
    return color and 0x00FFFFFF or (resultAlpha shl 24)
}

package com.ngocdevv.nativecook.blocks.gestures.habitbubblecluster

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min

const val LOOP: Long = HabitBubbleClusterTokens.LoopMs

private class HabitBubbleClusterScript {
    var timeMs = 0.0
    var paused = false
    var realDown = false
    var quietMs = 0.0
    var segment = 0
    var scriptedDown = false
}

/** A faithful Compose port of the approved React Native habit cluster. */
@Composable
fun HabitBubbleCluster(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val tokens = HabitBubbleClusterTokens
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    val simulation = remember { HabitBubbleClusterSimulation() }
    val script = remember { HabitBubbleClusterScript() }
    val finger = remember { HabitBubbleClusterFinger() }
    val label = remember { HabitBubbleClusterLabel() }
    val renderer = remember(context) { HabitBubbleClusterRenderer(context) }
    var tick by remember { mutableLongStateOf(0L) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var doneRevision by remember { mutableIntStateOf(0) }
    var stageWidth by remember { mutableDoubleStateOf(0.0) }
    var stageHeight by remember { mutableDoubleStateOf(0.0) }
    var originX by remember { mutableDoubleStateOf(0.0) }
    var originY by remember { mutableDoubleStateOf(tokens.ClusterY) }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    fun haptic(event: Int) {
        when (event) {
            HabitBubbleClusterEvent.Tick,
            HabitBubbleClusterEvent.Grab,
            -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            HabitBubbleClusterEvent.Done -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                },
            )

            HabitBubbleClusterEvent.Undo -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun flush(scripted: Boolean) {
        if (simulation.events.any { it == HabitBubbleClusterEvent.Count }) {
            selectedTab = simulation.tab
            doneRevision += 1
        }
        if (!scripted) simulation.events.forEach(::haptic)
        simulation.events.clear()
    }

    fun release(velocityX: Double, velocityY: Double, scripted: Boolean) {
        val tapped = simulation.touchUp(velocityX, velocityY, reducedMotion)
        flush(scripted)
        if (tapped >= 0) {
            label.index = tapped
            label.atMs = simulation.clockMs
        }
    }

    fun pauseForTouch(down: Boolean) {
        if (down && !script.paused && script.scriptedDown) release(0.0, 0.0, true)
        if (down) finger.alpha = 0.0
        script.paused = true
        script.realDown = down
        script.quietMs = 0.0
        script.segment = 0
        script.scriptedDown = false
    }

    fun switchTab(tab: Int, scripted: Boolean) {
        simulation.setTab(tab)
        selectedTab = simulation.tab
        label.index = -1
        flush(scripted)
    }

    fun runScript(frameMs: Double): Boolean {
        val time = script.timeMs
        fun at(start: Double) = time >= start && time < start + HabitBubbleClusterTokens.Autoplay.tapMs

        val holding = time >= HabitBubbleClusterTokens.Autoplay.holdStart && time < HabitBubbleClusterTokens.Autoplay.holdEnd
        val segment = when {
            holding -> 1
            at(HabitBubbleClusterTokens.Autoplay.nameAt) -> 2
            at(HabitBubbleClusterTokens.Autoplay.personalAt) -> 3
            at(HabitBubbleClusterTokens.Autoplay.socialAt) -> 4
            at(HabitBubbleClusterTokens.Autoplay.tapAt) -> 5
            else -> 0
        }
        var down = script.scriptedDown
        if (segment != script.segment) {
            if (down) {
                release(0.0, 0.0, true)
                down = false
            }
            when (segment) {
                1, 5 -> {
                    val index = simulation.indexOf(HabitBubbleClusterTokens.Autoplay.holdId)
                    finger.x = simulation.x[index]
                    finger.y = simulation.y[index]
                    finger.alpha = 1.0
                    simulation.touchDown(finger.x, finger.y)
                    flush(true)
                    down = true
                }

                2 -> {
                    val index = simulation.indexOf(HabitBubbleClusterTokens.Autoplay.nameId)
                    finger.x = simulation.x[index]
                    finger.y = simulation.y[index]
                    finger.alpha = 1.0
                    simulation.touchDown(finger.x, finger.y)
                    flush(true)
                    down = true
                }

                3 -> {
                    finger.x = HabitBubbleClusterTokens.Layout.gutter + HabitBubbleClusterTokens.Layout.segmentWidth * 0.75 - originX
                    finger.y = safeTop + 4.0 + HabitBubbleClusterTokens.Layout.control / 2.0 - originY
                    finger.alpha = 1.0
                    switchTab(1, true)
                }

                4 -> {
                    finger.x = HabitBubbleClusterTokens.Layout.gutter + HabitBubbleClusterTokens.Layout.segmentWidth * 0.25 - originX
                    finger.y = safeTop + 4.0 + HabitBubbleClusterTokens.Layout.control / 2.0 - originY
                    finger.alpha = 1.0
                    switchTab(0, true)
                }

                else -> finger.alpha = 0.0
            }
        }

        val next = time + frameMs
        if (next >= LOOP.toDouble()) {
            simulation.reset()
            selectedTab = 0
            label.index = -1
            finger.alpha = 0.0
            script.timeMs = 0.0
            script.segment = 0
            script.scriptedDown = false
            return true
        }
        script.timeMs = next
        script.segment = segment
        script.scriptedDown = down
        return false
    }

    LaunchedEffect(autoPlay, reducedMotion) {
        script.paused = !autoPlay || reducedMotion
        script.timeMs = 0.0
        script.quietMs = 0.0
        script.segment = 0
        script.scriptedDown = false
        finger.alpha = 0.0
        if (reducedMotion) simulation.reset()
    }

    LaunchedEffect(simulation, autoPlay, reducedMotion, stageWidth, stageHeight) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val frameMs = min(
                HabitBubbleClusterTokens.Physics.maxFrameMs,
                (nowNanos - previousNanos).coerceAtLeast(0L) / 1_000_000.0,
            )
            previousNanos = nowNanos
            var resetAtLoop = false
            if (autoPlay && !reducedMotion) {
                if (script.paused) {
                    script.quietMs = if (script.realDown) 0.0 else script.quietMs + frameMs
                    if (script.quietMs >= HabitBubbleClusterTokens.Autoplay.resumeDelayMs) {
                        simulation.reset()
                        selectedTab = 0
                        flush(true)
                        label.index = -1
                        finger.alpha = 0.0
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
            if (!resetAtLoop) simulation.step(frameMs, reducedMotion)
            flush(autoPlay && !script.paused)
            tick = nowNanos
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(android.graphics.Color.parseColor(tokens.Background))),
    ) {
        val measuredWidth = maxWidth.value.toDouble()
        val measuredHeight = maxHeight.value.toDouble()
        SideEffect {
            stageWidth = measuredWidth
            stageHeight = measuredHeight
            originX = measuredWidth / 2.0 + tokens.ClusterX - 201.0
            originY = tokens.ClusterY + safeTop - 62.0
        }
        val stageDensity = density.density

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(simulation, originX, originY, reducedMotion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        val downX = down.position.x / stageDensity
                        val downY = down.position.y / stageDensity
                        pauseForTouch(true)
                        var released = false
                        var handledControl = false
                        val headerY = safeTop + 4.0
                        if (
                            downY in headerY..headerY + HabitBubbleClusterTokens.Layout.control &&
                            downX in HabitBubbleClusterTokens.Layout.gutter..HabitBubbleClusterTokens.Layout.gutter + HabitBubbleClusterTokens.Layout.segmentWidth
                        ) {
                            val tab = if (downX < HabitBubbleClusterTokens.Layout.gutter + HabitBubbleClusterTokens.Layout.segmentWidth / 2.0) 0 else 1
                            switchTab(tab, false)
                            handledControl = true
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
                                            velocity.x / stageDensity.toDouble(),
                                            velocity.y / stageDensity.toDouble(),
                                            false,
                                        )
                                    }
                                    released = true
                                    break
                                }
                                change.consume()
                                if (!handledControl) {
                                    simulation.touchMove(
                                        change.position.x / stageDensity - originX,
                                        change.position.y / stageDensity - originY,
                                    )
                                }
                            }
                        } finally {
                            if (!released && !handledControl) release(0.0, 0.0, false)
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
                    renderer.draw(
                        canvas = this,
                        width = measuredWidth.toFloat(),
                        height = measuredHeight.toFloat(),
                        safeTop = safeTop,
                        safeBottom = safeBottom,
                        originX = originX.toFloat(),
                        originY = originY.toFloat(),
                        simulation = simulation,
                        finger = finger,
                        label = label,
                        reducedMotion = reducedMotion,
                    )
                    restore()
                }
            }

            simulation.entities.forEachIndexed { index, entity ->
                if (index == simulation.quietIndex) return@forEachIndexed
                val hue = tokens.Hues.getValue(entity.habit.hue)
                Icon(
                    imageVector = habitBubbleClusterIcon(entity.habit.icon),
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(hue.icon)),
                    modifier = Modifier
                        .graphicsLayer {
                            tick
                            val sizeDp = 64.0
                            val scale = entity.habit.radius * 2.0 * HabitBubbleClusterTokens.Layout.iconScale / sizeDp *
                                (simulation.radius[index] / entity.habit.radius)
                            translationX = ((originX + simulation.x[index] - sizeDp / 2.0) * stageDensity).toFloat()
                            translationY = ((originY + simulation.y[index] - sizeDp / 2.0) * stageDensity).toFloat()
                            scaleX = scale.toFloat()
                            scaleY = scale.toFloat()
                            alpha = (
                                min(1.0, simulation.radius[index] / 8.0) *
                                    (1.0 - simulation.done[index]) *
                                    if (simulation.ring == index) 1.0 - 0.6 * simulation.charge else 1.0
                                ).toFloat()
                        }
                        .size(64.dp),
                )
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(hue.ink)),
                    modifier = Modifier
                        .graphicsLayer {
                            tick
                            val sizeDp = 64.0
                            val base = entity.habit.radius * 2.0 * HabitBubbleClusterTokens.Layout.iconScale / sizeDp
                            translationX = ((originX + simulation.x[index] - sizeDp / 2.0) * stageDensity).toFloat()
                            translationY = ((originY + simulation.y[index] - sizeDp / 2.0) * stageDensity).toFloat()
                            val scale = base * (0.6 + 0.4 * simulation.done[index])
                            scaleX = scale.toFloat()
                            scaleY = scale.toFloat()
                            alpha = simulation.done[index].toFloat()
                        }
                        .size(64.dp),
                )
            }

            tokens.Tabs.forEachIndexed { index, name ->
                Box(
                    Modifier
                        .offset(
                            x = (HabitBubbleClusterTokens.Layout.gutter + HabitBubbleClusterTokens.Layout.segmentWidth * index / 2.0).dp,
                            y = (safeTop + 4.0).dp,
                        )
                        .size((HabitBubbleClusterTokens.Layout.segmentWidth / 2.0).dp, HabitBubbleClusterTokens.Layout.control.dp)
                        .clickable(role = Role.Tab) {
                            pauseForTouch(false)
                            switchTab(index, false)
                        }
                        .semantics {
                            contentDescription = "$name habits"
                            selected = selectedTab == index
                        },
                )
            }

            // Screen-reader path: the visible bubbles remain one gesture surface, while each habit
            // is also exposed as a one-point button that can complete or reopen it directly.
            Column(Modifier.size(1.dp)) {
                doneRevision
                simulation.entities.forEachIndexed { index, entity ->
                    if (entity.set != selectedTab || entity.quiet) return@forEachIndexed
                    val completed = simulation.doneTarget[index] > 0.5
                    Box(
                        Modifier
                            .size(1.dp)
                            .clickable(role = Role.Button) {
                                pauseForTouch(false)
                                simulation.setDone(index, !completed, reducedMotion)
                                flush(false)
                            }
                            .semantics {
                                contentDescription = entity.habit.name
                                stateDescription = if (completed) "Done today; double tap to reopen" else "Double tap to mark done"
                                role = Role.Button
                            },
                    )
                }
            }
        }
    }
}

private fun habitBubbleClusterIcon(id: String): ImageVector {
    return when (id) {
        "wave" -> Icons.Outlined.WavingHand
        "star" -> Icons.Outlined.StarBorder
        "gift" -> Icons.Outlined.ShoppingBasket
        "ball" -> Icons.Outlined.SportsBasketball
        "friend", "stretch" -> Icons.Outlined.PersonOutline
        "art" -> Icons.Outlined.Palette
        "host" -> Icons.Outlined.Bed
        "magic" -> Icons.Outlined.AutoAwesome
        "home" -> Icons.Outlined.Home
        "tea" -> Icons.Outlined.LocalCafe
        "heart" -> Icons.Outlined.FavoriteBorder
        "wallet" -> Icons.Outlined.AccountBalanceWallet
        "ride" -> Icons.Outlined.DirectionsBike
        "note" -> Icons.Outlined.MailOutline
        "read" -> Icons.Outlined.Book
        "sleep" -> Icons.Outlined.DarkMode
        "meditate" -> Icons.Outlined.Spa
        "run" -> Icons.Outlined.DirectionsRun
        "cook" -> Icons.Outlined.Restaurant
        "spanish" -> Icons.Outlined.Language
        "water" -> Icons.Outlined.WaterDrop
        "guitar" -> Icons.Outlined.MusicNote
        "stretch" -> Icons.Outlined.AccessibilityNew
        "phone" -> Icons.Outlined.PhoneAndroid
        "vitamins" -> Icons.Outlined.MedicalServices
        "plants" -> Icons.Outlined.LocalFlorist
        else -> Icons.Outlined.Edit
    }
}

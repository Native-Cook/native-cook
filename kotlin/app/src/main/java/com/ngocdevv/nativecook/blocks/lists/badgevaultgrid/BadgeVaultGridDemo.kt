package com.ngocdevv.nativecook.blocks.lists.badgevaultgrid

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun BadgeVaultGridDemo(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val density = LocalDensity.current
    val initialReducedMotion = rememberReducedMotion()
    var reducedMotion by remember { mutableStateOf(initialReducedMotion) }
    val engine = remember(autoPlay) { BadgeVaultEngine(autoPlay) }
    val renderer = remember { BadgeVaultRenderer(context.resources) }
    var rendererReady by remember { mutableStateOf(false) }
    var frameSignal by remember { mutableLongStateOf(0L) }
    var semanticsSignal by remember { mutableLongStateOf(0L) }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(renderer) {
        withContext(Dispatchers.Default) { renderer.prepareGpu() }
        rendererReady = true
    }

    LaunchedEffect(context) {
        while (true) {
            reducedMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            delay(250)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = constraints.maxWidth / 402f
        val sizeInDp = scale / density.density
        val width = constraints.maxWidth / scale
        val height = constraints.maxHeight / scale
        // The reference phone has 62/30 design-unit safe margins. Preserve at least
        // those margins while allowing a device with larger cutouts to expand them.
        val safeTop = max(62f, WindowInsets.safeDrawing.getTop(density) / scale)
        val safeBottom = max(30f, WindowInsets.safeDrawing.getBottom(density) / scale)
        val layout = remember(width, height, safeTop, safeBottom) {
            BadgeVaultLayout(width, height, safeTop, safeBottom)
        }
        val share: (BadgeVaultBadge) -> Unit = { badge ->
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${badge.name} · Badge Vault")
                    putExtra(Intent.EXTRA_TEXT, "${badge.name}\n${badge.blurb}\nEarned ${badge.earned ?: BadgeVaultUnlockDate} · Badge Vault")
                },
                null,
            )
            context.startActivity(chooser)
        }

        LaunchedEffect(layout, reducedMotion) {
            var previous = withFrameNanos { it }
            var lastSemantics = previous
            var lastSelected = engine.selected
            var lastCount = engine.earned.count { it }
            while (true) {
                val next = withFrameNanos { it }
                val deltaMs = ((next - previous) / 1_000_000f).coerceAtLeast(0f)
                if (rendererReady) engine.update(deltaMs, layout, reducedMotion)
                while (engine.haptics.isNotEmpty()) {
                    val constant = when (engine.haptics.removeFirst()) {
                        BadgeVaultEvent.Selection -> HapticFeedbackConstants.KEYBOARD_TAP
                        BadgeVaultEvent.Warning -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                        BadgeVaultEvent.Light -> HapticFeedbackConstants.CLOCK_TICK
                        BadgeVaultEvent.Medium -> HapticFeedbackConstants.VIRTUAL_KEY
                        BadgeVaultEvent.Success -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                        BadgeVaultEvent.Progress -> null
                    }
                    if (constant != null) view.performHapticFeedback(constant)
                }
                frameSignal = next
                val count = engine.earned.count { it }
                if (next - lastSemantics >= 100_000_000L || engine.selected != lastSelected || count != lastCount) {
                    semanticsSignal = next
                    lastSemantics = next
                    lastSelected = engine.selected
                    lastCount = count
                }
                previous = next
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(reducedMotion, scale, layout) {
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: return@awaitEachGesture
                    val start = down.position / scale
                    var last = start
                    var lastTime = down.uptimeMillis
                    var velocityX = 0f
                    var velocityY = 0f
                    var total = Offset.Zero
                    val pressedIndex = if (engine.selected < 0) badgeAt(start, layout, engine.scrollY) else -1
                    val footerY = layout.footerY - engine.scrollY
                    val pressedPill = engine.selected < 0 && start.x in (layout.width - 156f)..(layout.width - 24f) && start.y in (footerY + 24f)..(footerY + 72f)
                    engine.touched()
                    if (pressedIndex >= 0) {
                        val x = halfPoint(layout.columnLeft(pressedIndex % 3) + layout.columnWidth(pressedIndex % 3) / 2f)
                        val y = layout.headerHeight + pressedIndex / 3 * layout.pitch - engine.scrollY + layout.badgeY
                        engine.press(pressedIndex, (start.x - x) / layout.radius, (start.y - y) / layout.radius)
                    }
                    if (pressedPill && engine.earned.any { !it }) {
                        engine.changePillDown(true)
                    }
                    down.consume()
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        val point = change.position / scale
                        total = point - start
                        val elapsed = (change.uptimeMillis - lastTime).coerceAtLeast(1L) / 1000f
                        if (change.pressed && point != last) {
                            velocityX = (point.x - last.x) / elapsed
                            velocityY = (point.y - last.y) / elapsed
                        }
                        if (engine.selected >= 0 && !reducedMotion) {
                            if (engine.gestureAxis == 0 && hypot(total.x, total.y) > 10f) {
                                engine.gestureAxis = if (abs(total.x) > abs(total.y)) 1 else 2
                            }
                            if (engine.gestureAxis == 1) engine.horizontalDrag(total.x, layout.heldR)
                            if (engine.gestureAxis == 2) engine.verticalDrag(total.y)
                        } else if (engine.selected < 0 && abs(total.y) > 3f) {
                            engine.scrollY = (engine.scrollY - (point.y - last.y)).coerceIn(0f, layout.scrollEnd)
                            layout.scrollY = engine.scrollY
                        }
                        last = point
                        lastTime = change.uptimeMillis
                        change.consume()
                        released = !change.pressed
                    }
                    if (pressedIndex >= 0) engine.release(pressedIndex)
                    if (pressedPill) engine.changePillDown(false)
                    if (engine.selected >= 0 && engine.gestureAxis != 0) {
                        engine.finishDrag(layout, velocityX, velocityY)
                    } else if (hypot(total.x, total.y) < 12f) {
                        handleTap(start, layout, engine, reducedMotion, share)
                    }
                    engine.touchEnded()
                }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val redraw = frameSignal
                val hero = engine.hero(layout, reducedMotion)
                drawIntoCanvas { composeCanvas ->
                    val native = composeCanvas.nativeCanvas
                    native.save()
                    native.scale(scale, scale)
                    renderer.draw(native, layout, engine, hero, reducedMotion)
                    native.restore()
                }
            }
            @Suppress("UNUSED_VARIABLE") val semanticsTick = semanticsSignal
            if (engine.selected < 0) {
                BadgeVaultBadges.forEachIndexed { index, badge ->
                    val top = layout.headerHeight + index / 3 * layout.pitch - engine.scrollY
                    if (top + layout.pitch >= 0f && top <= layout.height) {
                        val goal = badge.goal
                        val earned = engine.earned[index]
                        val label = if (earned) "${badge.name}, earned ${badge.earned ?: BadgeVaultUnlockDate}"
                            else "${badge.name}, locked, ${engine.have[index]} of ${goal?.need} ${goal?.unit}"
                        Box(
                            Modifier.offset { IntOffset((layout.columnLeft(index % 3) * scale).roundToInt(), (top * scale).roundToInt()) }
                                .size((layout.columnWidth(index % 3) * sizeInDp).dp, (layout.pitch * sizeInDp).dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = label
                                    stateDescription = if (earned) "Earned" else "Locked"
                                    onClick(label = if (earned) "Hold badge up" else "Not earned yet") {
                                        engine.touched()
                                        engine.open(index, layout)
                                        engine.touchEnded()
                                        true
                                    }
                                },
                        )
                    }
                }
                val footerTop = layout.footerY - engine.scrollY
                if (footerTop < layout.height && footerTop + layout.footerHeight > 0f) {
                    val count = engine.earned.count { it }
                    Box(Modifier.offset { IntOffset((24f * scale).roundToInt(), ((footerTop + 24f) * scale).roundToInt()) }
                        .size((142f * sizeInDp).dp, (48f * sizeInDp).dp).semantics { contentDescription = "$count of 18 collected" })
                    Box(Modifier.offset { IntOffset(((layout.width - 156f) * scale).roundToInt(), ((footerTop + 24f) * scale).roundToInt()) }
                        .size((132f * sizeInDp).dp, (48f * sizeInDp).dp).semantics {
                            role = Role.Button
                            contentDescription = if (count < 18) "Log a day" else "All collected"
                            if (count == 18) disabled()
                            else onClick(label = "Advance badge progress") {
                                engine.touched()
                                engine.logDay()
                                engine.touchEnded()
                                true
                            }
                        })
                }
            } else {
                val badge = BadgeVaultBadges[engine.selected]
                Box(Modifier.offset { IntOffset((16f * scale).roundToInt(), ((layout.safeTop + 4f) * scale).roundToInt()) }
                    .size((36f * sizeInDp).dp, (36f * sizeInDp).dp).semantics {
                        role = Role.Button
                        contentDescription = "Close ${badge.name} details"
                        onClick {
                            engine.touched()
                            engine.close(layout)
                            engine.touchEnded()
                            true
                        }
                    })
                Box(Modifier.offset { IntOffset((24f * scale).roundToInt(), (layout.shareY * scale).roundToInt()) }
                    .size(((layout.width - 48f) * sizeInDp).dp, (56f * sizeInDp).dp).semantics {
                        role = Role.Button
                        contentDescription = "Share ${badge.name} badge"
                        onClick {
                            engine.touched()
                            share(badge)
                            engine.touchEnded()
                            true
                        }
                    })
            }
        }
    }
}

private fun badgeAt(point: Offset, layout: BadgeVaultLayout, scrollY: Float): Int {
    val contentY = point.y + scrollY - layout.headerHeight
    if (contentY < 0f) return -1
    val row = floor(contentY / layout.pitch).toInt()
    val column = floor((point.x - 16f) / ((layout.width - 32f) / 3f)).toInt()
    val index = row * 3 + column
    return if (column in 0..2 && index in BadgeVaultBadges.indices) index else -1
}

private fun handleTap(point: Offset, layout: BadgeVaultLayout, engine: BadgeVaultEngine, reduced: Boolean, share: (BadgeVaultBadge) -> Unit) {
    if (engine.selected >= 0) {
        val hero = engine.hero(layout, reduced)
        val close = point.x in 12f..56f && point.y in layout.safeTop..(layout.safeTop + 44f)
        val shareButton = point.x in 20f..(layout.width - 20f) && point.y in (layout.shareY - 4f)..(layout.shareY + 60f)
        val onBadge = hypot(point.x - hero.pose.cx, point.y - hero.pose.cy) < hero.pose.r * 1.22f
        when {
            close -> engine.close(layout)
            shareButton -> share(BadgeVaultBadges[engine.selected])
            !onBadge -> engine.close(layout)
        }
        return
    }
    val footerY = layout.footerY - engine.scrollY
    if (point.x >= layout.width - 164f && point.y in (footerY + 16f)..(footerY + 80f)) {
        if (engine.earned.any { !it }) engine.logDay()
        return
    }
    val index = badgeAt(point, layout, engine.scrollY)
    if (index >= 0) engine.open(index, layout)
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun BadgeVaultGridDemoPreview() {
    BadgeVaultGridDemo()
}

package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve.ObjectSigninDissolveTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * Object Sign-In Dissolve: a real pear on a sign-in screen. One drag turns it (x, 1:1) and holds a plane across it (y): above the
 * finger the fruit is a magenta shell of scan lines, below it real skin, a 3 pt seam between. The theme toggle wipes from the
 * bottom-left corner as a disc; the pear is lit for the page on each side of the arc.
 *
 * LOOP = 8 s: touch .6, drag .7-1.8 (200 deg, plane to the waist), release 2.3, wipe to cream 3.0-3.9, a hard flick at 4.0, wipe back
 * 5.6-6.5, rest to 8. A touch interrupts it; two seconds after the last touch it eases to rest and the loop restarts. Reduce Motion:
 * no loop, no coast, and the theme cross-fades instead of wiping. `previewTime` pins the loop at that second (checks, previews).
 */
@Composable
fun ObjectSigninDissolve(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    defaultLight: Boolean = false,
    onContinue: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val haptics = remember(context) { ObjectSigninDissolveHaptics.create(context) }
    val renderer = remember(context.assets) { ObjectSigninDissolveRenderer(context.assets) }
    var barDark by remember { mutableStateOf(defaultLight) }
    var isLight by remember { mutableStateOf(defaultLight) }
    val lightRef = remember { booleanArrayOf(defaultLight) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    val engine = remember(autoPlay, reduced) {
        ObjectSigninDissolveEngine(autoPlay, reduced, defaultLight).also {
            it.haptic = { beat -> haptics.emit(beat) }
            it.onBarDark = { dark -> barDark = dark }
            it.onTheme = { light ->
                lightRef[0] = light
                isLight = light
            }
        }
    }
    BlockSystemBars(darkIcons = barDark)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engine, active, previewTime) {
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                previous = nanos
                if (previewTime != null) engine.frameAt(previewTime, dt) else engine.frame(dt)
                // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                tick.intValue++
            }
        }
    }

    // first-open hint for a screen that is not on autoPlay: the pear turns 18 deg and back while a scan line dips
    LaunchedEffect(engine) {
        if (!autoPlay && !reduced) {
            delay(1200)
            engine.showHint()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(if (isLight) Theme.ground[1] else Theme.ground[0])),
    ) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val l = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, insetTop, insetBottom) }
        val cy = l.yc(Theme.PEAR_CY)
        remember(engine, l) {
            engine.layout(w, h, insetTop, cy, l.s)
            l
        }
        val insetKey = "$insetTop|$insetBottom"

        Canvas(
            Modifier.fillMaxSize().pointerInput(engine) {
                awaitEachGesture {
                    val d = densityOwner.density
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    engine.pointerDown(down.position.x / d.toDouble(), down.position.y / d.toDouble(), down.uptimeMillis / 1000.0)
                    down.consume()
                    var velocityX = 0.0
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                velocityX = tracker.calculateVelocity().x / d.toDouble()
                                change.consume()
                                engine.pointerUp(velocityX)
                                break
                            }
                            engine.pointerMove(
                                change.position.x / d.toDouble(), change.position.y / d.toDouble(), change.uptimeMillis / 1000.0,
                            )
                            change.consume()
                        }
                    } finally {
                        engine.pointerFinalize()
                    }
                }
            },
        ) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, l, insetKey, scale, engine, engine.pressed)
            c.restore()
        }

        // the controls: real nodes over the drawing, so they are focusable and labelled
        Box(
            Modifier
                .offset((l.w / 2 - 120 * l.s).dp, (cy - 220 * l.s).dp)
                .size((240 * l.s).dp, (400 * l.s).dp)
                .semantics {
                    contentDescription = "Pear. Swipe up or down to turn it."
                    stateDescription = "Turn the pear"
                    customActions = listOf(
                        CustomAccessibilityAction("Turn forward") { engine.a11yBump(1); true },
                        CustomAccessibilityAction("Turn back") { engine.a11yBump(-1); true },
                    )
                },
        )
        Box(
            Modifier
                .offset((l.x(Theme.THEME_CX) - 22).dp, (l.yt(Theme.THEME_CY) - 22).dp)
                .size(44.dp)
                .semantics {
                    role = Role.Switch
                    contentDescription = if (isLight) Theme.TO_DARK else Theme.TO_LIGHT
                    stateDescription = if (isLight) "On" else "Off"
                    onClick {
                        haptics.emit(ObjectSigninDissolveBeat.TOGGLE)
                        engine.setTheme(!lightRef[0])
                        true
                    }
                }
                .pointerInput(engine) {
                    detectTapGestures {
                        haptics.emit(ObjectSigninDissolveBeat.TOGGLE)
                        engine.setTheme(!lightRef[0])
                    }
                },
        )
        val pillX = l.x(Theme.PILL_X)
        val pillW = Theme.PILL_W * l.s
        val hw = Theme.HALF_W * l.s
        ObjectSigninDissolveButton(engine, haptics, 0, "email", Theme.CONTINUE, pillX, l.yb(Theme.PILL_Y), pillW, Theme.PILL_H * l.s, onContinue)
        ObjectSigninDissolveButton(engine, haptics, 1, "passkey", Theme.PASSKEY, pillX, l.yb(Theme.HALF_Y), hw, Theme.HALF_H * l.s, onContinue)
        ObjectSigninDissolveButton(engine, haptics, 2, "phone", Theme.PHONE, pillX + pillW - hw, l.yb(Theme.HALF_Y), hw, Theme.HALF_H * l.s, onContinue)
    }
}

@Composable
private fun ObjectSigninDissolveButton(
    engine: ObjectSigninDissolveEngine,
    haptics: ObjectSigninDissolveHaptics,
    index: Int,
    id: String,
    label: String,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    onContinue: ((String) -> Unit)?,
) {
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    haptics.emit(ObjectSigninDissolveBeat.PRESS)
                    onContinue?.invoke(id)
                    true
                }
            }
            .pointerInput(engine, id) {
                detectTapGestures(
                    onPress = {
                        engine.pressed = index
                        try {
                            tryAwaitRelease()
                        } finally {
                            engine.pressed = -1
                        }
                    },
                    onTap = {
                        haptics.emit(ObjectSigninDissolveBeat.PRESS)
                        onContinue?.invoke(id)
                    },
                )
            },
    )
}

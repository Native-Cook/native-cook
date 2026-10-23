package com.ngocdevv.nativecook.blocks.inputs.pasteplacesimport

import android.app.Activity
import android.content.ClipboardManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A value-for-value Compose port of the accepted React Native import screen. All visible motion is
 * evaluated from one loop time; the draw layer invalidates without recomposing the screen each frame.
 */
@Composable
fun PastePlacesImport(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = rememberReducedMotion()
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
    val assets = remember(context) { PastePlacesImportAssets(context.applicationContext) }
    val renderer = remember(assets) { PastePlacesImportRenderer(assets) }
    val controller = remember { PastePlacesImportController(autoPlay, reducedMotion) }
    val focusManager = LocalFocusManager.current
    var revision by remember { mutableIntStateOf(0) }
    var wakeGeneration by remember { mutableIntStateOf(0) }
    /** The text area's own text (typed, or pasted from the clipboard); the scripted note never goes in here. */
    var draft by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    /** The text area takes input only while the screen is at rest (no scripted note on it). */
    var textLive by remember { mutableStateOf(true) }
    val draftFill = remember { Animatable(0f) }
    var screenReader by remember { mutableStateOf(false) }

    fun clipboardText(): String? {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val clip = manager?.primaryClip
        if (clip == null || clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    /** Every state change the loop must notice: redraw, re-time the wake-up, and re-check the text area. */
    fun bump() {
        revision++
        wakeGeneration++
        textLive = controller.time < PastePlacesImportMotion.Presses.paste.start || controller.time >= PastePlacesImportMotion.REST_AGAIN
    }

    DisposableEffect(controller) {
        controller.onPaste = {
            val text = clipboardText()
            if (text != null) draft = (draft + text).take(PastePlacesImportTheme.Input.max)
            text != null
        }
        controller.onStartOwn = {
            draft = ""
            focusManager.clearFocus()
        }
        onDispose {
            controller.onPaste = { false }
            controller.onStartOwn = {}
        }
    }

    DisposableEffect(context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        screenReader = manager?.isTouchExplorationEnabled == true
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { screenReader = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }

    LaunchedEffect(draft.isNotEmpty(), focused, controller) {
        controller.hasDraft = draft.isNotEmpty()
        controller.focused = focused
        bump()
    }

    LaunchedEffect(draft.isNotEmpty(), reducedMotion) {
        val to = if (draft.isNotEmpty()) 1f else 0f
        if (reducedMotion) {
            draftFill.snapTo(to)
        } else {
            val duration = PastePlacesImportMotion.fillIn.end - PastePlacesImportMotion.fillIn.start
            draftFill.animateTo(to, tween((duration * 1000.0).roundToInt(), easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)))
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val systemBars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = systemBars?.isAppearanceLightStatusBars
        val previousNavigation = systemBars?.isAppearanceLightNavigationBars
        systemBars?.isAppearanceLightStatusBars = true
        systemBars?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) systemBars.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) systemBars.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        controller.setLifecycleActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        wakeGeneration++
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.setLifecycleActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.setLifecycleActive(false)
                else -> return@LifecycleEventObserver
            }
            revision++
            wakeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The loop is a demo: it stops for a screen reader as for Reduce Motion.
    LaunchedEffect(autoPlay, screenReader, controller) {
        controller.setAutoPlay(autoPlay && !screenReader, System.nanoTime())
        bump()
    }

    LaunchedEffect(reducedMotion, controller) {
        controller.setReducedMotion(reducedMotion, System.nanoTime())
        bump()
    }

    LaunchedEffect(controller) {
        var observedWake = wakeGeneration
        while (true) {
            val now = System.nanoTime()
            if (controller.shouldTick(now)) {
                withFrameNanos { frameTime ->
                    val result = controller.tick(frameTime)
                    if (result.changed) {
                        revision++
                        textLive = controller.time < PastePlacesImportMotion.Presses.paste.start || controller.time >= PastePlacesImportMotion.REST_AGAIN
                    }
                    performHaptic(view, result.haptic)
                }
                continue
            }

            val wakeDelay = controller.nextWakeDelayNanos(now)
            val beforeWait = observedWake
            val timedOut = if (wakeDelay != null) {
                withTimeoutOrNull((wakeDelay + 999_999L) / 1_000_000L) {
                    snapshotFlow { wakeGeneration }.first { it != beforeWait }
                    false
                } == null
            } else {
                snapshotFlow { wakeGeneration }.first { it != beforeWait }
                false
            }
            observedWake = wakeGeneration
            if (timedOut) {
                val result = controller.tick(System.nanoTime())
                if (result.changed) {
                    revision++
                    textLive = controller.time < PastePlacesImportMotion.Presses.paste.start || controller.time >= PastePlacesImportMotion.REST_AGAIN
                }
                performHaptic(view, result.haptic)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFFEDEDED))) {
        val widthPx = with(density) { maxWidth.toPx() }
        val unit = widthPx / PastePlacesImportTheme.REF_W
        val y0 = topInset - PastePlacesImportTheme.REF_INSET * unit

        fun targetAt(position: Offset): Int {
            return pastePlacesTarget(position.x, position.y, unit, y0)
        }

        fun accessibilityAction(target: Int) {
            val now = System.nanoTime()
            controller.touchDown(now, target)
            val haptic = controller.touchUp(now, target)
            bump()
            performHaptic(view, haptic)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller, widthPx, topInset) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTarget = targetAt(down.position)
                        // A tap anywhere but the text area and paste dismisses the keyboard.
                        if (focused && downTarget != 1 && downTarget != 4) focusManager.clearFocus()
                        controller.touchDown(System.nanoTime(), downTarget)
                        bump()
                        val up = waitForUpOrCancellation()
                        if (up == null) {
                            controller.touchCancel(System.nanoTime())
                        } else {
                            val haptic = controller.touchUp(System.nanoTime(), targetAt(up.position))
                            performHaptic(view, haptic)
                        }
                        bump()
                    }
                },
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (revision >= 0) {
                    drawIntoCanvas { composeCanvas ->
                        renderer.draw(
                            canvas = composeCanvas.nativeCanvas,
                            width = size.width,
                            height = size.height,
                            topInset = topInset,
                            controller = controller,
                            frame = PastePlacesImportRenderer.Frame(draft.length, draftFill.value),
                        )
                    }
                }
            }

            TextArea(
                value = draft,
                onValueChange = { if (it.length <= PastePlacesImportTheme.Input.max) draft = it },
                live = textLive,
                onFocusChanged = { focused = it },
                assets = assets,
                unit = unit,
                y0 = y0,
                fontScale = widthPx / PastePlacesImportTheme.REF_PT,
            )
            AccessibilityButton(
                label = "Close",
                centerX = 1098f,
                centerY = 224.5f,
                radius = 66f,
                unit = unit,
                y0 = y0,
                onActivate = { accessibilityAction(3) },
            )
            AccessibilityButton(
                label = "Paste",
                centerX = 603f,
                centerY = 2031f,
                radius = 66f,
                unit = unit,
                y0 = y0,
                onActivate = { accessibilityAction(1) },
            )
            AccessibilityButton(
                label = "Start import",
                centerX = 603f,
                centerY = 2431.5f,
                radius = 90f,
                unit = unit,
                y0 = y0,
                onActivate = { accessibilityAction(2) },
            )
        }
    }
}

@Composable
private fun AccessibilityButton(
    label: String,
    centerX: Float,
    centerY: Float,
    radius: Float,
    unit: Float,
    y0: Float,
    onActivate: () -> Unit,
) {
    val density = LocalDensity.current
    val leftPx = (centerX - radius) * unit
    val topPx = (centerY - radius) * unit + y0
    val sizePx = radius * 2f * unit
    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(with(density) { sizePx.toDp() })
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(label) {
                    onActivate()
                    true
                }
            },
    )
}

private fun pastePlacesTarget(xPx: Float, yPx: Float, unit: Float, y0: Float): Int {
    val x = xPx / unit
    val y = (yPx - y0) / unit
    if (x in 470f..740f && y in 1960f..2100f) return 1
    val button = PastePlacesImportTheme.Box.button
    if (x in button.x..(button.x + button.w) && y in button.y..(button.y + button.h)) return 2
    if (abs(x - 1098f) <= 75f && abs(y - 225f) <= 75f) return 3
    val card = PastePlacesImportTheme.Box.card
    if (x in card.x..(card.x + card.w) && y in card.y..(card.y + card.h)) return 4
    return 0
}

/**
 * The text area itself: a real multiline text input over the card, in the note's font, size, colour and left edge.
 * It takes input only while the screen is at rest; the sample flow paints its note instead.
 */
@Composable
private fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    live: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    assets: PastePlacesImportAssets,
    unit: Float,
    y0: Float,
    fontScale: Float,
) {
    val density = LocalDensity.current
    val note = PastePlacesImportTheme.Note
    val input = PastePlacesImportTheme.Input
    val fontSize = note.size * fontScale
    val lineHeight = kotlin.math.round(fontSize * 1.4f)
    val baselineInside = (lineHeight - (PastePlacesImportTheme.ASCENT + PastePlacesImportTheme.DESCENT) * fontSize) / 2f +
        PastePlacesImportTheme.ASCENT * fontSize
    val top = note.firstBase * unit + y0 - baselineInside
    val left = input.left * unit
    val width = (input.right - input.left) * unit
    val height = input.bottom * unit + y0 - top
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = live,
        textStyle = TextStyle(
            fontFamily = FontFamily(assets.textTypeface),
            fontSize = with(density) { fontSize.toSp() },
            lineHeight = with(density) { lineHeight.toSp() },
            color = Color(PastePlacesImportTheme.MotionColors.noteInk),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        cursorBrush = SolidColor(Color(PastePlacesImportTheme.MotionColors.detect)),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .alpha(if (live) 1f else 0f)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .semantics { contentDescription = input.label },
    )
}

private fun performHaptic(view: android.view.View, haptic: PastePlacesImportController.Haptic) {
    when (haptic) {
        PastePlacesImportController.Haptic.NONE -> Unit
        PastePlacesImportController.Haptic.LIGHT -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        PastePlacesImportController.Haptic.SUCCESS -> {
            val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            view.performHapticFeedback(feedback)
        }
    }
}

const val LOOP = PastePlacesImportMotion.LOOP

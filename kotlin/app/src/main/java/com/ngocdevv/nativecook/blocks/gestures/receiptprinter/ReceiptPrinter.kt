package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** Fixed autoplay loop period in milliseconds, matching the RN recording. */
internal const val LOOP = Tokens.LOOP_MS

/** Thermal run receipt, composed as one fixed-point Canvas with touch and autoplay sharing the same engine. */
@Composable
fun ReceiptPrinter(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val artwork = remember(context) { ReceiptPrinterArtwork(context) }
    val engine = remember(autoPlay, view) {
        ReceiptPrinterEngine(autoPlay) { kind ->
            val code = when (kind) {
                ReceiptPrinterEngine.Kind.TICK -> HapticFeedbackConstants.CLOCK_TICK
                ReceiptPrinterEngine.Kind.OPEN -> HapticFeedbackConstants.LONG_PRESS
                ReceiptPrinterEngine.Kind.TEAR -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(code)
        }
    }
    var frameTick by remember { mutableLongStateOf(0L) }
    var phaseOrdinal by remember { mutableIntStateOf(ReceiptPrinterPhase.GRID.ordinal) }
    var barVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
    val statusTopPx = ViewCompat.getRootWindowInsets(view)
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        ?.top ?: 0
    val safeTopPx = maxOf(WindowInsets.safeDrawing.getTop(density), statusTopPx)
    val safeTop = safeTopPx / density.density
    val shift = safeTop - Tokens.SOURCE.safeTop

    DisposableEffect(activity, view) {
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = bars?.isAppearanceLightStatusBars
        val statusBarsWereVisible = ViewCompat.getRootWindowInsets(view)
            ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        bars?.isAppearanceLightStatusBars = true
        onDispose {
            if (bars != null) {
                if (previous != null) bars.isAppearanceLightStatusBars = previous
                if (statusBarsWereVisible) bars.show(WindowInsetsCompat.Type.statusBars())
                else bars.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    SideEffect {
        activity?.window?.let { window ->
            val bars = WindowCompat.getInsetsController(window, view)
            if (phaseOrdinal == ReceiptPrinterPhase.GRID.ordinal || phaseOrdinal == ReceiptPrinterPhase.MENU.ordinal) {
                bars.show(WindowInsetsCompat.Type.statusBars())
            } else {
                bars.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    LaunchedEffect(engine, reduced) {
        engine.setReducedMotion(reduced)
        frameTick++
    }

    LaunchedEffect(engine) {
        var previousNanos = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val elapsed = (now - previousNanos) / 1_000_000f
            previousNanos = now
            val before = engine.snapshot()
            engine.tick(elapsed)
            val after = engine.snapshot()
            if (before != after) frameTick++
            if (phaseOrdinal != engine.phase.ordinal) phaseOrdinal = engine.phase.ordinal
            val showBar = engine.phase == ReceiptPrinterPhase.READING && after.barOpacity > 0.5f
            if (barVisible != showBar) barVisible = showBar
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(Tokens.COLOR.bg))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.rootWindowInsets?.displayCutout?.boundingRects
                ?.filter { it.top <= safeTopPx }
                ?.minByOrNull { abs(it.exactCenterX() - width * density.density / 2f) }
        } else {
            null
        }
        // Android has no Dynamic Island: cover the top camera cut-out, or sit just below the status bar.
        val islandTop = (cutout?.top?.div(density.density) ?: (safeTop + Tokens.ISLAND.noIslandGap)) - shift
        val islandCenterX = cutout?.exactCenterX()?.div(density.density) ?: width / 2f
        val paperTop = islandTop + Tokens.ISLAND.openH - Tokens.ISLAND.tuck
        SideEffect { engine.updateGeometry(width, height - shift, islandTop, islandCenterX) }
        Box(Modifier.fillMaxSize().pointerInput(engine, density.density, shift) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x / density.density
                val y = down.position.y / density.density - shift
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                val caught = engine.down(x, y, down.uptimeMillis)
                if (caught) down.consume()
                var ended = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val nextX = change.position.x / density.density
                        val nextY = change.position.y / density.density - shift
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (change.pressed) {
                            engine.move(nextX, nextY, change.uptimeMillis)
                            if (caught) change.consume()
                        } else {
                            engine.up(nextX, nextY, change.uptimeMillis,
                                velocityTracker.calculateVelocity().y / density.density)
                            if (caught) change.consume()
                            ended = true
                            break
                        }
                    }
                } finally {
                    if (!ended) engine.cancel()
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                frameTick // Snapshot read inside draw invalidates just the Canvas, not the composition.
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    val saved = canvas.save()
                    canvas.scale(density.density, density.density)
                    canvas.translate(0f, shift)
                    artwork.draw(canvas, engine.snapshot())
                    canvas.restoreToCount(saved)
                }
            }

            when (ReceiptPrinterPhase.entries[phaseOrdinal]) {
                ReceiptPrinterPhase.GRID -> ReceiptPrinterGridSemantics(engine, shift)
                ReceiptPrinterPhase.MENU -> ReceiptPrinterMenuSemantics(engine, shift)
                ReceiptPrinterPhase.PRINTING, ReceiptPrinterPhase.READING ->
                    ReceiptPrinterPaperSemantics(engine, shift, paperTop + shift, barVisible)
                ReceiptPrinterPhase.RETRACTING -> Unit
            }
        }
    }
}

@Composable
private fun ReceiptPrinterGridSemantics(engine: ReceiptPrinterEngine, shift: Float) {
    Tokens.CARDS.forEachIndexed { index, card ->
        val x = Tokens.GRID.gutter + (index % 3) * (Tokens.GRID.cardW + Tokens.GRID.gap)
        val y = Tokens.GRID.rowTop + (index / 3) * Tokens.GRID.rowPitch + shift
        Box(Modifier.offset(x.dp, y.dp).size(Tokens.GRID.cardW.dp, Tokens.GRID.cardH.dp).semantics {
            role = Role.Button
            contentDescription = "${card.date.joinToString(" ")}, ${card.km}. Print receipt"
            onClick("Print receipt") {
                engine.accessibilityPrint(index)
                true
            }
            customActions = listOf(CustomAccessibilityAction("Receipt options") {
                engine.accessibilityMenu(index)
                true
            })
        })
    }
}

@Composable
private fun ReceiptPrinterMenuSemantics(engine: ReceiptPrinterEngine, shift: Float) {
    val snapshot = engine.snapshot()
    val labels = listOf(Tokens.COPY.long, Tokens.COPY.short)
    labels.forEachIndexed { index, label ->
        val rowTop = snapshot.menuY + shift + Tokens.GRID.menuPaddingVertical + index * Tokens.GRID.menu.rowH
        Box(Modifier.offset(snapshot.menuX.dp, rowTop.dp)
            .size(Tokens.GRID.menu.w.dp, Tokens.GRID.menu.rowH.dp).semantics {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    engine.accessibilityChoose()
                    true
                }
            })
    }
    Box(Modifier.offset(0.dp, 0.dp).size(1.dp).semantics {
        customActions = listOf(CustomAccessibilityAction("Close menu") {
            engine.accessibilityCloseMenu()
            true
        })
    })
}

@Composable
private fun ReceiptPrinterPaperSemantics(
    engine: ReceiptPrinterEngine,
    shift: Float,
    paperTop: Float,
    barVisible: Boolean,
) {
    val paperWidth = Tokens.SLOT.paperX1 - Tokens.SLOT.paperX0
    Box(Modifier.offset(Tokens.SLOT.paperX0.dp, paperTop.dp)
        .size(paperWidth.dp, Tokens.TOUCH.paperSemanticHeight.dp).semantics {
        contentDescription = "Run receipt"
        customActions = listOf(
            CustomAccessibilityAction("Scroll down") {
                engine.accessibilityScroll(Tokens.TOUCH.accessibilityScroll)
                true
            },
            CustomAccessibilityAction("Scroll up") {
                engine.accessibilityScroll(-Tokens.TOUCH.accessibilityScroll)
                true
            },
            CustomAccessibilityAction("Tear off") {
                engine.accessibilityTear()
                true
            },
        )
    })
    if (!barVisible) return
    listOf(Tokens.COPY.save, Tokens.COPY.tear, Tokens.COPY.sign)
        .forEachIndexed { index, label ->
            val x = Tokens.BAR.x + index * (Tokens.BAR.w + Tokens.BAR.gap)
            Box(Modifier.offset(x.dp, (Tokens.BAR.top + shift).dp)
                .size(Tokens.BAR.w.dp, Tokens.BAR.h.dp).semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick(label) {
                        if (index == 1) engine.accessibilityTear()
                        true
                    }
                })
        }
}

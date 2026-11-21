package com.ngocdevv.nativecook.blocks.progress.pressedstampcard

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion

/** Eight wells, the source die timeline, and the same press/drag/redeem actions as the RN card. */
@Composable
fun PressedStampCardDemo(autoPlay: Boolean = true) {
    BlockSystemBars(darkIcons = true)
    val context = LocalContext.current
    val view = LocalView.current
    val art = remember(context) { PressedStampCardArt(context) }
    val reduced = rememberLiveReducedMotion()
    val debugIntent = remember(context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) null
        else generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.intent
    }
    val pinnedTime = remember(debugIntent) { debugIntent?.getStringExtra("pressed-stamp-card-time")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } }
    val pinnedCount = remember(debugIntent) { debugIntent?.getStringExtra("pressed-stamp-card-count")?.toIntOrNull()?.coerceIn(0, 8) }
    val automatic = autoPlay && pinnedCount == null
    val clock = remember(automatic, pinnedCount) { PressedStampCardClock(automatic, pinnedCount ?: 5) }
    var now by remember { mutableDoubleStateOf(SystemClock.elapsedRealtimeNanos() / 1e9) }
    var wake by remember { mutableIntStateOf(0) }
    LaunchedEffect(pinnedTime, reduced, wake) {
        if (pinnedTime == null) while (!reduced || clock.animating) withFrameNanos { now = it / 1e9 }
    }
    val f = if (pinnedTime != null) PressedStampCardMotion.frameAt(pinnedTime) else clock.frame(now, reduced)
    val scanAction: () -> Boolean = {
        if (pinnedTime != null) false else {
            clock.scan(SystemClock.elapsedRealtimeNanos() / 1e9, reduced); wake++
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM); true
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF4EDE0))) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val safeTop = insets.calculateTopPadding()
        val tabsBottom = (insets.calculateBottomPadding() - 10.dp).coerceAtLeast(10.dp)
        val unit = minOf(maxWidth / 402, (maxHeight - safeTop - tabsBottom) / 680).coerceAtLeast(.01.dp)
        val left = (maxWidth - unit * 402) / 2
        val top = safeTop - unit * 62
        val tabsY = (maxHeight - top - tabsBottom) / unit - 64f
        val density = LocalDensity.current
        val scale = with(density) { unit.toPx() }
        val startX = with(density) { left.toPx() }
        val startY = with(density) { top.toPx() }
        Canvas(Modifier.fillMaxSize().pointerInput(reduced, clock, pinnedTime, scale, startX, startY) {
            if (pinnedTime != null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = SystemClock.elapsedRealtimeNanos() / 1e9
                val downX = (down.position.x - startX).toDouble() / scale
                val downY = (down.position.y - startY).toDouble() / scale
                if (!clock.begin(downX, downY, start, reduced)) return@awaitEachGesture
                wake++
                try {
                    while (true) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val stamp = SystemClock.elapsedRealtimeNanos() / 1e9
                        val x = (change.position.x - startX).toDouble() / scale
                        val y = (change.position.y - startY).toDouble() / scale
                        if (change.changedToUp()) {
                            if (clock.end(x, y, stamp, reduced)) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            wake++; break
                        }
                        clock.move(x, y, stamp); change.consume()
                    }
                } finally { clock.cancel(SystemClock.elapsedRealtimeNanos() / 1e9) }
            }
        }) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.translate(startX, startY)
            canvas.scale(scale, scale)
            art.draw(canvas, (size.height - startY) / scale, tabsY, f, clock.magnetIndex, clock.pull)
            canvas.restore()
        }
        Box(Modifier.offset(left + unit * 20, top + unit * 118).size(unit * 362, unit * 344).semantics {
            contentDescription = "Coffee Club stamp card. Hold and drag to press a stamp."
            stateDescription = "${f.count} of 8 stamps"
            customActions = listOf(CustomAccessibilityAction(if (f.count == 8) "Redeem free coffee" else "Add a stamp", scanAction))
        }) {
            for (i in 0 until 8) Box(Modifier
                .offset(unit * (PressedStampCardMotion.wellX(i).toFloat() - 36), unit * (PressedStampCardMotion.wellY(i).toFloat() - 36))
                .size(unit * 72).semantics {
                    contentDescription = "Stamp well ${i + 1}"
                    stateDescription = if (i < f.count) "Stamped" else "Empty"
                    if (i == f.count) onLongClick(label = "Press a stamp", action = scanAction)
                })
        }
        Box(Modifier.offset(left + unit * 20, top + unit * 478).size(unit * 362, unit * 56).semantics {
            contentDescription = if (f.count == 8) "Redeem free coffee" else "Scan at the counter"
            stateDescription = "${f.count} of 8 stamps"
            role = Role.Button
            onClick(label = if (f.count == 8) "Redeem free coffee" else "Scan to add a stamp", action = scanAction)
        })
    }

}

@Preview
@Composable
private fun PressedStampCardDemoPreview() { PressedStampCardDemo() }

package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot
import kotlin.math.min
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.UI
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.S
import com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall.ListeningBuddyPaywallTheme.DY

/** Fixed-width reference geometry; height extends the cream sky and the Continue bar. */
@Composable
fun ListeningBuddyPaywall(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) {
        ListeningBuddyPaywallArt(context)
    }
    val driver = remember(autoPlay, reduced) {
        ListeningBuddyPaywallDriver()
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(ListeningBuddyPaywallMotion.frameAt(0.0))
    }
    val audio = remember(context, reduced) {
        if (reduced) null else ListeningBuddyPaywallAudio(context)
    }
    DisposableEffect(audio) {
        onDispose {
            audio?.release()
        }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    fun updateFrame() {
        val current = driver.frame(clock[0])
        frame.value = if (reduced) current.copy(amb = 0.0, selAt = 10.0, go = -1.0, poke = -1.0) else current
    }
    fun buzz(kind: Int) {
        view.performHapticFeedback(when (kind) {
            2 -> HapticFeedbackConstants.CLOCK_TICK
            3 -> if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        })
    }
    LaunchedEffect(driver, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { nanos ->
                val dt = min(if (previous == 0L) .016 else (nanos - previous) / 1e9, .05)
                previous = nanos
                clock[0] += dt
                driver.step(dt, clock[0], autoPlay && !reduced)
                updateFrame()
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Subscribe now. Monthly 12,99 euros per month. Annual 94,99 euros per year, most popular, selected. Lifetime 449,99 euros, one payment. Continue."
        onClick("Chooses the next plan.") {
            driver.takeOver(clock[0])
            driver.select((driver.plan + 1) % 3, clock[0])
            updateFrame()
            true
        }
    }.pointerInput(driver, reduced, audio) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = size.width / 402.0
            fun pinX(value: Float) = value / scale / S
            fun pinY(value: Float) = (value / scale - DY) / S
            driver.takeOver(clock[0])
            driver.released = 0
            val px = pinX(down.position.x)
            val py = pinY(down.position.y)
            if (py > UI.bar.y) {
                driver.pressing = 1
                driver.pressAt = clock[0]
            } else if (hypot(px - UI.close.x, py - UI.close.y) < UI.close.r + 14) {
                driver.pressing = 2
                driver.pressAt = clock[0]
            }
            down.consume()
            var position = down.position
            var pressed = true
            try {
                while (pressed) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull {
                        it.id == down.id
                    } ?: break
                    position = pointer.position
                    pressed = pointer.pressed
                    pointer.consume()
                }
            } finally {
                // Pan.finalize uses the final point even for cancellation, matching RN.
                val now = clock[0]
                driver.last = now
                val x = pinX(position.x)
                val y = pinY(position.y)
                val was = driver.pressing
                if (was > 0) {
                    driver.released = was
                    driver.releaseAt = now
                    driver.pressing = 0
                }
                if (was == 1) {
                    if (y >= UI.bar.y - 20) {
                        driver.goAt = now
                        audio?.play("notes")
                        buzz(3)
                    }
                } else if (was == 2) buzz(0)
                else if (y > UI.cards.y - 14 && y < UI.cards.y + UI.cards.h + 14) {
                    for (i in 0..2) if (x > UI.cards.x[i] - 12 && x < UI.cards.x[i] + UI.cards.w + 12 && driver.select(i, now)) {
                        audio?.play(if (i == 0) "pick1" else if (i == 2) "pick2" else "pick3")
                        buzz(2)
                    }
                } else if (x < 300 && y > 380 && y < UI.white) {
                    driver.pokeAt = now
                    audio?.play("boop")
                    buzz(0)
                }
                updateFrame()
            }
        }
    }) {
        drawIntoCanvas { canvas -> art.drawScene(canvas.nativeCanvas, size.width.toDouble(), size.height.toDouble(), frame.value)
        }
    }
}

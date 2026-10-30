package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.launch
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.T
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.PEAK
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.LOOP
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.frameAt
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.inRestA
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.inPostcard
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Sheet
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Bear
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardTheme.Card

/** One Canvas, with frame state observed only in draw. previewTime freezes a debug build. */

@Composable

fun QrJoyPostcard(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug) previewTime?.toDouble() else null
    val art = remember(context) {
        QrJoyPostcardArt(context)
    }
    val audio = remember(context, reduced) {
        if (reduced) null else QrJoyPostcardAudio(context)
    }
    DisposableEffect(audio) {
        onDispose {
            audio?.close()
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
    val driver = remember(autoPlay, reduced, pin) {
        QrJoyPostcardDriver(pin?: if (reduced) PEAK else 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val time = remember(driver) {
        mutableDoubleStateOf(driver.t)
    }
    val live = remember(driver) {
        mutableStateOf(QrJoyPostcardDriver.Live())
    }
    val fade = remember(driver) {
        Animatable(1f)
    }
    val scope = rememberCoroutineScope()

    fun toggleReduced(toB: Boolean) {
        scope.launch {
            fade.animateTo(if (toB) 1f else 0f, tween(220, easing = Easing {
                p -> if (p<0.5f) 2*p*p else 1-(-2*p+2).pow(2)/2
            }))
        }
    }

    fun buzz(kind: Int = 0) {
        view.performHapticFeedback(when (kind) {
            1 -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            2 -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        })
    }
    LaunchedEffect(driver, lifecycle) {
        if (reduced || pin != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos {
                nanos ->
                val dt = min(if (previous == 0L) 0.016 else (nanos-previous)/1e9, 0.05)
                previous = nanos
                clock[0]+=dt
                val before = driver.t
                val t = driver.step(dt, clock[0], autoPlay)
                live.value = driver.live(clock[0])
                time.doubleValue = t
                if (driver.mode == 1 && driver.rate>0) {
                    if (before<T.heartAt && t >= T.heartAt) {
                        audio?.play("sparkle")
                        buzz(2)
                    }
                    if (before<T.sticker[1] && t >= T.sticker[1]) {
                        audio?.play("sticker")
                        buzz()
                    }
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Share the joy. Decorate a plain QR code with a backdrop and warm words, then send it to whoever picks up the order."
        onClick(label = "Decorates the QR code into a postcard, or turns it back when it is decorated.") {
            if (pin == null) {
                if (reduced) toggleReduced(fade.value<0.5f)
                else {
                    driver.last = clock[0]
                    if (inRestA(driver.t)) {
                        driver.t = T.commit
                        driver.releaseA(true)
                    }
                    else if (!driver.closeB()) {
                        driver.mode = 1
                        driver.rate = 1.0
                        driver.stop = if (driver.t<PEAK) PEAK else LOOP
                    }
                }
            }
            true
        }
    }.pointerInput(driver, pin, reduced) {
        if (pin != null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val s = size.width/402.0
            val dy = size.height-874*s

            fun reference(pos: Offset) = Pair(pos.x/s, (pos.y-dy)/s)

            fun inBox(x: Double, y: Double, pad: Double): Boolean {
                val b = Sheet.cta
                return x >= b.x-pad && x <= b.x+b.w+pad && y >= b.y-pad && y <= b.y+b.h+pad
            }
            val start = down.position
            val (x, y) = reference(start)
            val showingA = if (reduced) fade.value<0.5 else inRestA(driver.t)
            val showingB = if (reduced) fade.value >= 0.5 else inPostcard(driver.t) || (driver.t >= PEAK && driver.t<T.back)
            val card = QrJoyPostcardTheme.toCard(x, y, frameAt(driver.t).lift)
            val onBear = card.first>Bear.x-48 && card.first<Bear.x+48 && card.second>Bear.y-Bear.h+8 && card.second<Bear.y-4
            val onCard = card.first> -8 && card.first<Card.w+8 && card.second> -8 && y<Sheet.heroBottom
            val target = when {
                hypot(x-Sheet.closeX, y-Sheet.closeY)<24 && showingB -> 2
                inBox(x, y, 6.0) -> if (showingB) 4 else if (showingA) 3 else 0
                onBear && !reduced -> 5
                onCard -> 1
                else -> 0
            }
            if (!reduced) {
                driver.touched(clock[0])
                if (target == 3) {
                    driver.pressA()
                    driver.ctaAim = 1.0
                }
                if (target == 4) driver.ctaAim = 1.0
            }
            var dragging = false
            var end = start
            var released = false
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    }
                    if (change != null) {
                        end = change.position
                        released = !change.pressed
                        if (!reduced && change.pressed) {
                            driver.last = clock[0]
                            if (target == 1 || target == 5) {
                                val delta = end-start
                                // GestureHandler reports translations in density-independent points.
                                if (dragging || delta.getDistance()/density >= 8) {
                                    dragging = true
                                    driver.aimX = (delta.x/density/110.0).coerceIn(-1.0, 1.0)
                                    driver.aimY = (delta.y/density/140.0).coerceIn(-1.0, 1.0)
                                }
                            }
                        }
                        change.consume()
                    }
                } while (!released && event.changes.any {
                    it.pressed
                })
            } finally {
                val (ex, ey) = reference(end)
                val tap = hypot(ex-x, ey-y)<12 && !dragging
                driver.ctaAim = 0.0
                driver.aimX = 0.0
                driver.aimY = 0.0
                if (reduced) {
                    if (tap && (target == 1 || target == 3)) toggleReduced(true)
                    else if (tap && target == 2) toggleReduced(false)
                } else {
                    when {
                        target == 3 -> {
                            val on = tap && inBox(ex, ey, 12.0)
                            driver.releaseA(on)
                            if (on) audio?.play("flip")
                        }
                        target == 1 && tap -> {
                            if (inRestA(driver.t)) {
                                driver.pressA()
                                driver.t = T.commit
                                driver.releaseA(true)
                                audio?.play("flip")
                                buzz()
                            }
                            else if (inPostcard(driver.t)) {
                                driver.jiggleAt = clock[0]
                                buzz()
                            }
                        }
                        target == 2 && tap -> if (driver.closeB()) buzz()
                        target == 4 && tap -> {
                            driver.jiggleAt = clock[0]
                            audio?.play("sparkle")
                            buzz(1)
                        }
                        target == 5 && tap -> {
                            driver.bearAt = clock[0]
                            buzz()
                        }
                    }
                    driver.last = clock[0]
                }
            }
        }
    }) {
        drawIntoCanvas {
            canvas ->
            val native = canvas.nativeCanvas
            if (reduced) {
                val k = fade.value
                if (k<0.999f) art.drawScene(native, size.width, size.height, frameAt(0.0), QrJoyPostcardDriver.Live())
                if (k>0.001f) {
                    val layer = android.graphics.Paint().apply {
                        alpha = (k*255).roundToInt()
                    }
                    native.saveLayer(null, layer)
                    art.drawScene(native, size.width, size.height, frameAt(PEAK), QrJoyPostcardDriver.Live())
                    native.restore()
                }
            } else art.drawScene(native, size.width, size.height, frameAt(time.doubleValue), live.value)
        }
    }
}

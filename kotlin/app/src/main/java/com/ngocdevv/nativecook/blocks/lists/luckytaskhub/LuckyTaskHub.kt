package com.ngocdevv.nativecook.blocks.lists.luckytaskhub
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
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
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.Driver
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.frameAt
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.frameOf
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.stepDriver
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.takeOver
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.kickWheel
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.flipCard
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.hopTrophy
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubMotion.landed
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.UI
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.S
import com.ngocdevv.nativecook.blocks.lists.luckytaskhub.LuckyTaskHubTheme.DY
/** The complete rewards screen; frame state is observed only during Canvas drawing. */
@Composable
fun LuckyTaskHub(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val pin = if (debug)previewTime?.toDouble()else null
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val auto = autoPlay && !reduced
    val art = remember(context) {
        LuckyTaskHubArt(context)
    }
    val audio = remember(context, reduced) {
        LuckyTaskHubAudio(context, !reduced)
    }
    DisposableEffect(audio) {
        onDispose {
            audio.release()
        }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) {
            (it as? ContextWrapper)?.baseContext
        }
        .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, view)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null)controller.isAppearanceLightStatusBars = previous
        }
    }
    val driver = remember(auto, reduced, pin) {
        Driver(pin?: 0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val frame = remember(driver) {
        mutableStateOf(frameAt(pin?: 0.0))
    }
    val vibrator = remember(context) {
        context.getSystemService(Vibrator::class.java)
    }
    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            val effect = when (kind) {
                2 -> VibrationEffect.EFFECT_TICK
                3 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                1 -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            vibrator?.vibrate(VibrationEffect.createPredefined(effect))
        }
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1)20 else 12, if (kind == 1)90 else 45))
    }
    LaunchedEffect(driver, lifecycle) {
        if (pin != null)return@LaunchedEffect
        var billsSeen = -10.0
        var landedSeen = 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos {
                    nanos ->
                    val dt = min(if (previous == 0L)0.016 else(nanos-previous)/1e9, 0.05)
                    previous = nanos
                    clock[0]+=dt
                    val now = clock[0]
                    stepDriver(driver, dt, now, auto)
                    if (driver.mode == 2 && driver.billsAt>0) {
                        if (billsSeen != driver.billsAt) {
                            billsSeen = driver.billsAt
                            landedSeen = 0
                        }
                        val n = floor(landed(now-driver.billsAt)+0.5).toInt()
                        if (n>landedSeen) {
                            if (landedSeen == 0)audio.play("cash")
                            landedSeen = n
                            buzz(if (n == 5)3 else 2)
                        }
                    }
                    frame.value = frameOf(driver, now)
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Rewards. Ranking: Bronze. Lucky Flip: 26 of 30 left. Lucky Spin. Newbie tasks: 3 of 14."
        onClick(label = "Spins the lucky wheel.") {
            takeOver(driver, clock[0])
            kickWheel(driver)
            true
        }
    }
    .pointerInput(driver, reduced, pin) {
        if (pin != null)return@pointerInput
        fun toPin(p: Offset): Pair<Double, Double> {
            val scale = size.width/402.0
            return Pair(p.x/scale/S, (p.y/scale-DY)/S)
        }
        fun inside(x: Double, y: Double, rx: Double, ry: Double, rw: Double, rh: Double, pad: Double = 0.0) =
        x>rx-pad && x<rx+rw+pad && y>ry-pad && y<ry+rh+pad
        fun angle(p: Pair<Double, Double>) = atan2(p.second-UI.spin.wheel.y, p.first-UI.spin.wheel.x)*180/PI
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = toPin(down.position)
            val now = clock[0]
            takeOver(driver, now)
            val x = start.first
            val y = start.second
            driver.press = -1
            val goes = listOf(doubleArrayOf(UI.ranking.go.x, UI.ranking.go.y, UI.ranking.go.w, UI.ranking.go.h),
            doubleArrayOf(UI.flip.go.x, UI.flip.go.y, UI.flip.go.w, UI.flip.go.h),
            doubleArrayOf(UI.spin.go.x, UI.spin.go.y, UI.spin.go.w, UI.spin.go.h))
            goes.forEachIndexed {
                i, g -> if (inside(x, y, g[0], g[1], g[2], g[3], 8.0)) {
                    driver.press = i
                    driver.pressLast = i
                    driver.pressAt = now
                }
            }
            val wheel = UI.spin.wheel
            val spin = UI.spin.card
            val flip = UI.flip.card
            val ranking = UI.ranking.card
            val chest = UI.inProgress.chest
            var lastAng = 0.0
            var lastT = 0.0
            val zone = if (driver.press<0 && hypot(x-wheel.x, y-wheel.y)<wheel.r+26 && inside(x, y, spin.x, spin.y, spin.w, spin.h, 10.0)) {
                if (!reduced && driver.billsAt<0) {
                    driver.dragging = 1
                    driver.spinning = 0
                    driver.wheelV = 0.0
                    lastAng = angle(start)
                    lastT = now
                }
                1
            }
            else if (inside(x, y, spin.x, spin.y, spin.w, spin.h))2
            else if (inside(x, y, flip.x, flip.y, flip.w, flip.h))3
            else if (inside(x, y, ranking.x, ranking.y, ranking.w, ranking.h))4
            else if (inside(x, y, chest.x, chest.y, chest.w, chest.h, 10.0))5 else 0
            down.consume()
            var end = start
            try {
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id == down.id
                    }
                    ?: break
                    end = toPin(change.position)
                    released = !change.pressed
                    change.consume()
                    if (!released && zone == 1 && driver.dragging == 1) {
                        val at = clock[0]
                        driver.last = at
                        val a = angle(end)
                        var delta = a-lastAng
                        if (delta>180)delta-=360
                        if (delta< -180)delta+=360
                        val elapsed = max(1.0/240, at-lastT)
                        driver.wheel+=delta
                        driver.wheelV = driver.wheelV*0.5+delta/elapsed*0.5
                        lastAng = a
                        lastT = at
                    }
                }
            }
            finally {
                val at = clock[0]
                driver.last = at
                if (driver.press >= 0) {
                    driver.press = -1
                    driver.releaseAt = at
                }
                val tap = hypot(end.first-start.first, end.second-start.second)<16
                if (zone == 1 && driver.dragging == 1) {
                    driver.dragging = 0
                    if (tap) {
                        if (kickWheel(driver)) {
                            audio.play("spin")
                            buzz(0)
                        }
                    }
                    else {
                        driver.spinning = 1
                        driver.spun = 0.0
                        driver.wheelV = driver.wheelV.coerceIn(-2400.0, 2400.0)
                        if (abs(driver.wheelV)>300)audio.play("spin")
                    }
                }
                else if (tap) {
                    when (zone) {
                        1, 2 -> if (reduced) {
                            driver.balance+=500
                            driver.balFrom = driver.balance
                            driver.progTo = min(14.0, driver.progTo+1)
                            driver.progFrom = driver.progTo
                            driver.progAt = at-1
                            audio.play("cash")
                            buzz(3)
                        }
                        else if (kickWheel(driver)) {
                            audio.play("spin")
                            buzz(0)
                        }
                        3 -> if (flipCard(driver, at)) {
                            if (reduced)driver.flipAt = at-1
                            audio.play("flip")
                            buzz(0)
                        }
                        4 -> if (hopTrophy(driver, at)) {
                            if (reduced)driver.hopAt = at-2
                            audio.play("flip")
                            buzz(1)
                        }
                        5 -> if (!reduced) {
                            driver.chestAt = at
                            buzz(0)
                        }
                    }
                }
                frame.value = frameOf(driver, at)
            }
        }
    }
    ) {
        drawIntoCanvas {
            art.drawScene(it.nativeCanvas, size.width, frame.value)
        }
    }
}

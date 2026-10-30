package com.ngocdevv.nativecook.blocks.sheets.bookfansignin

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.PEAK
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.T
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.inHome
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.inSheet
import com.ngocdevv.nativecook.blocks.sheets.bookfansignin.BookFanSigninMotion.frameAt

/** The page is top anchored, while the sheet receives the extra screen height at its bottom. */
@Composable
fun BookFanSignin(autoPlay: Boolean = false, previewTime: Float? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)==0f
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
    val pin = if (debug)previewTime?.toDouble() else null
    val driver = remember(autoPlay, reduced, pin) {
        BookFanSigninDriver().apply {
            t = pin?: 0.0
            amb = t
        }
    }
    var art by remember(context) {
        mutableStateOf<BookFanSigninArt?>(null)
    }
    LaunchedEffect(context) {
        art = withContext(Dispatchers.Default) {
            BookFanSigninArt(context)
        }
    }
    val tick = remember(driver) {
        mutableDoubleStateOf(0.0)
    }
    val clock = remember(driver) {
        doubleArrayOf(0.0)
    }
    val fade = remember{
        mutableDoubleStateOf(0.0)
    }
    val fadeAim = remember{
        mutableDoubleStateOf(0.0)
    }
    val still = remember{
        BookFanSigninDriver()
    }
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    }
    val loaded = remember{
        mutableSetOf<Int>()
    }
    val sounds = remember(soundPool) {
        soundPool.setOnLoadCompleteListener {
            _, id, status->if (status==0)loaded.add(id)
        }
        mapOf("flight" to soundPool.load(context,
             R.raw.bookfansignin_bfs_flight,
             1),
            "settle" to soundPool.load(context,
             R.raw.bookfansignin_bfs_settle,
             1),
            "tap" to soundPool.load(context,
             R.raw.bookfansignin_bfs_tap,
             1))
    }
    fun cue(name: String) {
        val id = sounds.getValue(name)
        if (!reduced&&id in loaded) {
            val volume = if (name=="tap")0.6f else 0.85f
            soundPool.play(id, volume, volume, 1, 0, 1f)
        }
    }
    fun buzz(medium: Boolean = false) {
        view.performHapticFeedback(if (medium)android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
    DisposableEffect(view, soundPool) {
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
            soundPool.release()
            if (previous!=null)controller.isAppearanceLightStatusBars = previous
        }
    }
    LaunchedEffect(reduced, fadeAim.doubleValue, art) {
        if (!reduced||art==null)return@LaunchedEffect
        val from = fade.doubleValue
        val aim = fadeAim.doubleValue
        if (from==aim)return@LaunchedEffect
        val start = withFrameNanos {
            it
        }
        do {
            val p = withFrameNanos {
                ((it-start)/1e9/0.22).coerceIn(0.0, 1.0)
            }
            val eased = if (p<0.5)2*p*p else 1-(-2*p+2).pow(2)/2
            fade.doubleValue = from+(aim-from)*eased
        }
        while (p<1.0)
    }
    LaunchedEffect(driver, lifecycle, art) {
        if (art==null||pin!=null||reduced)return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos {
                    nanos->
                    val dt = min(if (previous==0L)0.016 else (nanos-previous)/1e9, 0.05)
                    previous = nanos
                    clock[0]+=dt
                    val before = driver.t
                    val t = driver.step(dt, clock[0], autoPlay)
                    if (driver.mode==1&&driver.rate>0&&before<T.sheet+0.5&&t>=T.sheet+0.5&&t<T.close)buzz()
                    tick.doubleValue = clock[0]
                }
            }
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        role = Role.Button
        contentDescription = "Which book is on your mind? Sign in."
        onClick(label = "Opens the sign-in sheet, or closes it when it is open.") {
            if (reduced)fadeAim.doubleValue = if (fade.doubleValue<0.5)1.0 else 0.0
            else {
                driver.touched(clock[0])
                if (inHome(driver.t))driver.open() else if (!driver.close()) {
                    driver.mode = 1
                    driver.rate = 1.0
                    driver.stop = if (driver.t<PEAK)PEAK else 9.0
                }
            }
            true
        }
    }
    .pointerInput(driver, reduced, pin) {
        if (pin!=null)return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scale = size.width/402.0
            val dy = size.height-874*scale
            val x = down.position.x/scale
            val y = down.position.y/scale
            val ys = (down.position.y-dy)/scale
            val home = if (reduced)fade.doubleValue<0.5 else inHome(driver.t)
            val sheet = if (reduced)fade.doubleValue>=0.5 else inSheet(driver.t)||(driver.t>=PEAK&&driver.t<T.close)
            val target = if (home) {
                if (x>282&&x<385&&y>62&&y<118)2 else if (y<300)1 else 0
            }
            else if (sheet) {
                if (x>36&&x<366&&ys>755&&ys<812)4 else if (ys>418)3 else 5
            }
            else 0
            if (!reduced) {
                driver.touched(clock[0])
                if (target==2)driver.pressAim = 1.0
                if (target==4)driver.ctaAim = 1.0
                if (target==1||target==3)driver.dragging = 1
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var last = down.position
            var moved = false
            down.consume()
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull {
                        it.id==down.id
                    }
                    ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val translation = change.position-down.position
                    if (!reduced) {
                        driver.last = clock[0]
                        if (translation.getDistance()/density>8)moved = true
                        if (target==1) {
                            val delta = change.position-last
                            driver.scroll+=(-sin(BookFanSigninMotion.rad)*delta.x+cos(BookFanSigninMotion.rad)*delta.y)/scale
                        }
                        else if (target==3)driver.drag = translation.y/scale
                    }
                    last = change.position
                    change.consume()
                    if (!change.pressed)break
                }
                while (event.changes.any{
                    it.pressed
                }
                )
            }
            finally {
                driver.pressAim = 0.0
                driver.ctaAim = 0.0
                driver.dragging = 0
                driver.last = clock[0]
                if (reduced) {
                    if (!moved&&target==2)fadeAim.doubleValue = 1.0 else if (!moved&&(target==5||target==3))fadeAim.doubleValue = 0.0
                }
                else {
                    val velocity = tracker.calculateVelocity()
                    when (target) {
                        1->if (moved)driver.scrollV = (-sin(BookFanSigninMotion.rad)*velocity.x+cos(BookFanSigninMotion.rad)*velocity.y)/scale
                        2->if (!moved) {
                            driver.open()
                            cue("flight")
                            buzz()
                        }
                        3->{
                            val v = velocity.y/scale
                            if (driver.drag>110||v>900) {
                                if (driver.close()) {
                                    cue("flight")
                                    buzz()
                                }
                            }
                            driver.dragV = v
                        }
                        5->if (!moved&&driver.close())cue("flight")
                        4->if (!moved) {
                            cue("tap")
                            buzz(true)
                        }
                    }
                }
            }
        }
    }
    ) {
        tick.doubleValue
        drawIntoCanvas {
            canvas->
            val resources = art
            if (resources==null)canvas.nativeCanvas.drawColor(android.graphics.Color.WHITE)
            else if (reduced) {
                resources.draw(canvas.nativeCanvas, size.width, size.height, frameAt(0.0), still)
                if (fade.doubleValue>0.001) {
                    val paint = Paint().apply {
                        alpha = (fade.doubleValue*255).toInt()
                    }
                    canvas.nativeCanvas.saveLayer(0f, 0f, size.width, size.height, paint)
                    resources.draw(canvas.nativeCanvas, size.width, size.height, frameAt(PEAK), still)
                    canvas.nativeCanvas.restore()
                }
            }
            else resources.draw(canvas.nativeCanvas, size.width, size.height, frameAt(pin?: driver.t), driver)
        }
    }
}

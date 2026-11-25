package com.ngocdevv.nativecook.blocks.lists.snackstopvending

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.lists.snackstopvending.SnackStopVendingMotion as M
import kotlin.math.*

/** Compose owns lifecycle, touch and accessible actions; the cached native canvas draws in dp. */
@Composable
fun SnackStopVending(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    fun readReduced() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(readReduced()) }
    val art = remember { SnackStopVendingArt(context) }
    val clock = remember { SnackStopVendingClock() }
    val touch = remember(reduced, autoPlay) { M.Touch().also { it.manual = !autoPlay; it.loopStart = clock.now() } }
    val time = remember { mutableDoubleStateOf(0.0) }
    var status by remember { mutableStateOf("Pick a snack, Tap a code on the keypad") }
    var lcd by remember { mutableStateOf("") }
    fun refreshSemantics(c: Double) {
        val f = touch.frame(c, reduced)
        val text = if (!f.card || f.cardSlot < 0) "Pick a snack, Tap a code on the keypad"
        else M.drinks[M.color(f.cardSlot)].let { "${it[0]}, ${it[1]} · ${M.code(f.cardSlot)}, ${if (f.pill == 2) "Enjoy!" else it[2]}" }
        // The touch model is intentionally not snapshot state. Publish only text changes;
        // per-frame clock reads stay in the draw lambda, outside screen composition.
        if (status != text) status = text
        if (lcd != f.lcd) lcd = f.lcd
    }
    var wake by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            val foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            clock.setActive(foreground); active = foreground
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = readReduced() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true; controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
    LaunchedEffect(active, reduced, autoPlay, wake) {
        if (!active) return@LaunchedEffect
        do {
            withFrameNanos {
                val c = clock.now(); touch.step(c, autoPlay, reduced)
                val beat = if (touch.manual || reduced) touch.beat(c, reduced) else -1.0
                if (beat >= 3.6 && clock.lastBeat >= 0 && clock.lastBeat < 3.6 && touch.slot >= 0) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    @Suppress("DEPRECATION")
                    view.announceForAccessibility("${M.drinks[M.color(touch.slot)][0]} is ready in the tray")
                }
                clock.lastBeat = beat; refreshSemantics(c); time.doubleValue = c
            }
        } while (touch.moving(clock.now(), autoPlay, reduced))
    }
    fun act(action: (M.Touch, Double) -> Unit) {
        val c = clock.now(); touch.over(c, reduced); action(touch, c)
        refreshSemantics(c); time.doubleValue = c; wake++
    }
    val viewConfiguration = LocalViewConfiguration.current
    val machineConfiguration = remember(viewConfiguration) { object : ViewConfiguration by viewConfiguration {
        // Adjacent toy-machine keys are 25 dp apart. Enlarged semantics would overlap and shift
        // their accessible centres away from the matching circular pointer targets.
        override val minimumTouchTargetSize = DpSize.Zero
    } }
    CompositionLocalProvider(LocalViewConfiguration provides machineConfiguration) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = remember(maxWidth, maxHeight) { SnackStopVendingLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble()) }
        Canvas(Modifier.fillMaxSize().pointerInput(l, density, reduced, touch) {
            detectTapGestures(onPress = { p ->
                val x = p.x / density; val y = p.y / density
                val key = (0..<12).firstOrNull { val k = l.key(it); hypot(x - k.x, y - k.y) <= 13 }
                val slot = (0..<20).firstOrNull { l.slotRect(it).apply { inset(-4f, -4f) }.contains(x, y) }
                act { s, c ->
                    if (key != null) s.key(key, c, reduced)
                    else if (l.tray.contains(x, y)) s.take(c, reduced)
                    else if (slot != null) s.choose(slot, c, reduced)
                }
                tryAwaitRelease()
            })
        }) {
            val c = drawContext.canvas.nativeCanvas
            c.save(); c.scale(density, density)
            art.base(l, density).let { c.drawBitmap(it, null, RectF(0f, 0f, l.width.toFloat(), l.height.toFloat()), art.bitmapPaint) }
            art.draw(c, l, touch.frame(time.doubleValue, reduced)); c.restore()
        }
        fun Modifier.at(x: Double, y: Double, w: Double, h: Double) = offset(x.dp, y.dp).size(w.dp, h.dp)
        Box(Modifier.at(l.ox + 28, 56 * l.fy, 240.0, 50.0).semantics { heading(); contentDescription = "Snack Stop, Lobby, Level 2" })
        for (slot in 0..<20) {
            val p = l.slot(slot); val d = M.drinks[M.color(slot)]
            Box(Modifier.at(p.x - SnackStopVendingLayout.CW / 2, p.y - SnackStopVendingLayout.CH, SnackStopVendingLayout.CW, SnackStopVendingLayout.CH).semantics {
                role = Role.Button; contentDescription = "${d[0]}, ${M.code(slot)}, ${d[2]}"
                onClick { act { s, c -> s.choose(slot, c, reduced) }; true }
            })
        }
        for (key in 0..<12) {
            val p = l.key(key)
            Box(Modifier.at(p.x - 13, p.y - 13, 26.0, 26.0).semantics {
                role = Role.Button; contentDescription = if (key == 11) "Clear code" else M.keys[key]
                onClick { act { s, c -> s.key(key, c, reduced) }; true }
            })
        }
        val tray = l.tray
        Box(Modifier.at(tray.left.toDouble(), tray.top.toDouble(), tray.width().toDouble(), tray.height().toDouble()).semantics {
            role = Role.Button; contentDescription = "Take drink from tray"
            onClick { act { s, c -> s.take(c, reduced) }; true }
        })
        val lcdDescription = if (lcd.isEmpty()) "Code display" else "Code display, $lcd"
        val statusDescription = status
        Box(Modifier.at(l.x(750.0), l.y(300.0), 65.0, 45.0).semantics { contentDescription = lcdDescription })
        Box(Modifier.at(l.ox + 20, 716 * l.fy, 353.0, 108.0).semantics { contentDescription = statusDescription })
    }
    }
}

private class SnackStopVendingClock {
    // Keep the crossing across gesture wake-ups, including a busy tap on the landing frame.
    var lastBeat = -1.0
    private var origin = SystemClock.uptimeMillis() / 1000.0
    private var paused: Double? = null
    fun now() = (paused ?: (SystemClock.uptimeMillis() / 1000.0)) - origin
    fun setActive(active: Boolean) {
        val t = SystemClock.uptimeMillis() / 1000.0
        if (active) { paused?.let { origin += t - it }; paused = null }
        else if (paused == null) paused = t
    }
}

private data class SnackStopVendingPoint(val x: Double, val y: Double)
private data class SnackStopVendingLayout(val width: Double, val height: Double) {
    companion object {
        const val K = 360.0 / 930; const val CW = 92 * K; const val CH = CW * 159 / 96
        private val COIL_X = doubleArrayOf(191.0, 306.0, 419.0, 530.0, 642.0)
        private val SHELF_Y = doubleArrayOf(416.0, 605.0, 791.0, 974.0)
        private val KEY_X = doubleArrayOf(766.0, 832.0, 897.0)
        private val KEY_Y = doubleArrayOf(497.0, 567.0, 638.0, 710.0)
    }
    val ox = (width - 393) / 2; val fy = height / 852; val gy = 110 * (fy - 1)
    fun x(v: Double) = ox + 16.5 + (v - 45) * K
    fun y(v: Double) = gy + 110 + (v - 105) * K
    fun slot(s: Int) = SnackStopVendingPoint(x(COIL_X[s % 5]), y(SHELF_Y[s / 5]))
    fun key(k: Int) = SnackStopVendingPoint(x(KEY_X[k % 3]), y(KEY_Y[k / 3]))
    fun slotRect(s: Int): RectF { val p = slot(s); return RectF((p.x - CW / 2).toFloat(), (p.y - CH).toFloat(), (p.x + CW / 2).toFloat(), p.y.toFloat()) }
    val tray = RectF(x(135.0).toFloat(), y(1150.0).toFloat(), x(712.0).toFloat(), y(1335.0).toFloat())
    fun trayX(s: Int) = (slot(s).x - 40).coerceIn(tray.left + 4.0, tray.right - CH * 1.12 - 4)
}

private class SnackStopVendingArt(context: Context) {
    private val assets = context.assets
    private fun image(name: String) = assets.open("snackstopvending/snackstopvending_$name.webp").use { BitmapFactory.decodeStream(it) }
    private val machine = image("machine")
    private val cans = (0..<5).map { image("can$it") }
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fonts = mapOf("title" to "TitanOne-Regular", "sub" to "Inter-Medium", "name" to "Onest-ExtraBold", "pill" to "Onest-ExtraBold", "key" to "Inter-SemiBold", "lcd" to "JetBrainsMono-Medium").mapValues { Typeface.createFromAsset(assets, "snackstopvending/snackstopvending_${it.value}.ttf") }
    private val sizes = mapOf("title" to 30f, "sub" to 14f, "name" to 20f, "pill" to 22f, "key" to 9f, "lcd" to 15f)
    private var base: Bitmap? = null; private var baseLayout: SnackStopVendingLayout? = null
    private val coilPaths = (0..<2).map { n ->
        Path().apply { val rx = SnackStopVendingLayout.CW.toFloat() * .66f; val cy = -8f - n * 9; addArc(RectF(-rx, cy - 4.5f, rx, cy + 4.5f), (.15 * 180 / PI).toFloat(), ((PI - .3) * 180 / PI).toFloat()) }
    }
    private fun ink(rgb: Int, alpha: Double = 1.0) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).roundToInt(), rgb shr 16 and 255, rgb shr 8 and 255, rgb and 255)
    private fun fill(rgb: Int, alpha: Double = 1.0) { paint.reset(); paint.isAntiAlias = true; paint.color = ink(rgb, alpha) }
    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
    private fun text(c: android.graphics.Canvas, value: String, face: String, x: Double, y: Double, rgb: Int, center: Boolean = false, alpha: Double = 1.0) {
        fill(rgb, alpha); paint.typeface = fonts[face]; paint.textSize = sizes.getValue(face); paint.isSubpixelText = true
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(value, x.toFloat(), y.toFloat(), paint)
    }
    fun base(l: SnackStopVendingLayout, density: Float): Bitmap {
        if (baseLayout == l) return base!!
        val bitmap = Bitmap.createBitmap((l.width * density).roundToInt(), (l.height * density).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap); c.scale(density, density)
        fill(0xFFFFFF); paint.shader = LinearGradient(0f, 0f, 0f, l.height.toFloat(), ink(0xFFE2CF), ink(0xFFC9AE), Shader.TileMode.CLAMP)
        c.drawRect(rect(0.0, 0.0, l.width, l.height), paint)
        fill(0x962814, .25); paint.maskFilter = BlurMaskFilter(14f / 3, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(rect(l.ox + 26.5, l.gy + 638, 340.0, 28.0), paint)
        c.drawBitmap(machine, null, rect(l.ox + 16.5, l.gy + 110, 360.0, 1382 * SnackStopVendingLayout.K), bitmapPaint)
        fill(0xFFFFFF); paint.setShadowLayer(10f, 0f, 10f / 3, ink(0xA02814, .18))
        c.drawRoundRect(rect(l.ox + 20, 716 * l.fy, 353.0, 108.0), 28f, 28f, paint); paint.clearShadowLayer()
        base = bitmap; baseLayout = l; return bitmap
    }
    private fun can(c: android.graphics.Canvas, image: Bitmap, x: Double, y: Double, scale: Double = 1.0, rot: Double = 0.0, alpha: Double = 1.0) {
        if (alpha <= 0) return
        c.save(); c.translate(x.toFloat(), y.toFloat()); c.rotate((rot * 180 / PI).toFloat())
        bitmapPaint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        val w = SnackStopVendingLayout.CW * scale; val h = SnackStopVendingLayout.CH * scale
        c.drawBitmap(image, null, rect(-w / 2, -h, w, h), bitmapPaint); bitmapPaint.alpha = 255; c.restore()
    }
    private fun coil(c: android.graphics.Canvas, p: SnackStopVendingPoint) {
        c.save(); c.translate(p.x.toFloat(), p.y.toFloat()); fill(0xE1E4E8, .85); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f
        coilPaths.forEach { c.drawPath(it, paint) }; c.restore()
    }
    fun draw(c: android.graphics.Canvas, l: SnackStopVendingLayout, f: M.Frame) {
        for (s in 0..<20) {
            if (s == f.slot && f.can.kind != 0) continue
            val p = l.slot(s); can(c, cans[M.color(s)], p.x, p.y)
            if (s != f.slot) coil(c, p)
        }
        if (f.slot >= 0) {
            val p = l.slot(f.slot); val image = cans[M.color(f.slot)]; val a = f.can; val floor = l.tray.bottom - 6.0
            val clip = RectF(l.tray).apply { top += 2 }
            if (a.kind == 2) can(c, image, p.x, p.y, a.scale, 0.0, a.alpha)
            if (a.kind == 1) {
                val x = p.x + (l.trayX(f.slot) - p.x) * a.fall
                val y = if (a.fall >= 1) floor - a.bounce + a.taken else p.y + (floor - p.y) * a.drop
                c.save(); if (a.clip) c.clipRect(clip)
                can(c, image, x, y, a.scale, a.rot, a.alpha); c.restore()
            }
            if (f.trayAlpha > 0) {
                c.save(); c.clipRect(clip); can(c, image, l.trayX(f.slot), floor, 1.12, PI / 2, f.trayAlpha); c.restore()
            }
            if (a.veil || f.trayAlpha > 0) { fill(0xFFFFFF, .1); c.drawRect(rect(l.tray.left.toDouble(), l.tray.top.toDouble(), l.tray.width().toDouble(), l.tray.height() * .55), paint) }
            coil(c, p)
            if (f.glint >= 0) {
                fill(0xFFFFFF, .9 * sin(f.glint * PI)); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                val r = SnackStopVendingLayout.CW * .7
                c.drawArc(rect(p.x - r, p.y - 12 - r, r * 2, r * 2), (f.glint * 720).toFloat(), (1.1 * 180 / PI).toFloat(), false, paint)
            }
        }
        for ((key, u) in listOf(f.flashA to f.flashAU, f.flashB to f.flashBU)) if (key >= 0 && u >= 0) {
            val p = l.key(key); fill(0x7CFF8A, .55 * sin(u * PI)); c.drawCircle(p.x.toFloat(), p.y.toFloat(), 10f, paint)
        }
        for (key in 0..<12) {
            val p = l.key(key)
            if (key == 11) {
                fill(0x7A4A3A, .85); paint.strokeWidth = 1.2f; paint.strokeCap = Paint.Cap.ROUND
                c.drawLine((p.x - 2.8).toFloat(), (p.y - 2.8).toFloat(), (p.x + 2.8).toFloat(), (p.y + 2.8).toFloat(), paint)
                c.drawLine((p.x - 2.8).toFloat(), (p.y + 2.8).toFloat(), (p.x + 2.8).toFloat(), (p.y - 2.8).toFloat(), paint)
            } else text(c, M.keys[key], "key", p.x, p.y + 3.2, 0x7A4A3A, true, .85)
        }
        text(c, f.lcd, "lcd", l.x(837.0) + f.lcdShake, l.y(346.0) + 5, 0x7CFF8A, true)
        if (f.finger > 0) { val p = l.key(f.fingerKey); fill(0x28140A, .22 * f.finger); c.drawCircle(p.x.toFloat(), p.y.toFloat(), 20f, paint) }
        text(c, "Snack Stop", "title", l.ox + 28, 82 * l.fy, 0xC4142B)
        text(c, "Lobby · Level 2", "sub", l.ox + 29, 102 * l.fy, 0x8A5A48)
        val top = 716 * l.fy
        val d = if (f.card && f.cardSlot >= 0) M.drinks[M.color(f.cardSlot)] else null
        text(c, d?.get(0) ?: "Pick a snack", "name", l.ox + 42, top + 40, 0x2B1A14)
        text(c, if (d != null) d[1] + " · " + M.code(f.cardSlot) else "Tap a code on the keypad", "sub", l.ox + 42, top + 66, 0x9A7A6A)
        val pillTop = 736 * l.fy
        fill(if (f.pill == 2) 0x2E9E55 else if (f.pill == 1) 0xC4142B else 0xE8D8CF)
        c.drawRoundRect(rect(l.ox + 245, pillTop, 110.0, 66.0), 33f, 33f, paint)
        val label = if (f.pill == 2) "Enjoy!" else if (f.pill == 1) d?.get(2) ?: "—" else "—"
        text(c, label, "pill", l.ox + 300, pillTop + 41, if (f.pill == 0) 0x9A7A6A else 0xFFFFFF, true)
    }
}

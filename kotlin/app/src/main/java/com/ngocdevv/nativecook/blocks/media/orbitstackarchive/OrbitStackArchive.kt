package com.ngocdevv.nativecook.blocks.media.orbitstackarchive

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// The redesigned RN archive, translated number for number into dp.
// Anton headlines over warm paper; sixteen 200 x 268 posters orbit at radius 150,
// scale .22 -> 1, and gather at the centre (width / 2, height * .56).
// LOOP = 9 s: orbit 0–1.2; gather i over 1.2+.03i -> 2.4+.03i (ease-in-out cubic);
// four .75 s deals from 2.6–5.6 (+92 x, -26 y, +16 degrees, sine);
// burst i over 5.8+.03(15-i) -> 7+.03(15-i), then orbit until 9.
// Touch freezes the phase, spins with the finger, decays at 3/s for 2 s after release,
// then settles to the nearest whole turn over .8 s. Reduced motion stops the clock and drag.
/** Seconds. */
private const val LOOP = 9.0
private const val COUNT = 16
private const val RING = 150.0
private const val W = 200f
private const val H = 268f
private const val SMALL = .22
private const val DEAL_START = 2.6
private const val DEAL = .75
private const val DEALS = 4
private const val HOLD = 2.0
private const val RETURN = .8
private val PAPER = Color(0xFFEEECE6)
private val INK = Color(0xFF121212)
private val MUTED = Color(0xFF8A877F)
private const val WHITE = -1
private const val BLACK = 0xFF121212.toInt()
private const val RED = 0xFFE3241B.toInt()
private const val ASSETS = "orbitstackarchive/orbitstackarchive_"
private data class Design(val title: String, val meta: String, val art: String)
private val DESIGNS = listOf(
    Design("Form & Field", "Kunsthalle Basel · 1966", "bust"),
    Design("Ascent", "Architektur Forum · 1974", "stair"),
    Design("Yep.", "Lime Press · 1989", "blob"),
    Design("Blue Hour", "Studio Hafen · 1979", "sphere"),
    Design("Draw", "Werkschule Bern · 1961", "hand"),
    Design("Bloom", "Gartenamt Zürich · 1983", "flower"),
    Design("No. 7", "Galerie Raster · 1957", "grid"),
    Design("Loud Type", "Radio Zürich · 1991", "waves"),
)
private val TOPS = listOf(7, 6, 5, 4, 3)

private fun hash(i: Int, n: Int): Double {
    val x = sin((i + 1) * 17.371 + n * 91.719) * 43758.5453
    return x - floor(x)
}
private data class Pile(val tilt: Double, val dx: Double, val dy: Double)
private val PILE = List(COUNT) { i -> Pile((hash(i, 1) - .5) * 16, (hash(i, 2) - .5) * 20, (hash(i, 3) - .5) * 14) }
private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun cubic(x: Double) = if (x < .5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
private fun gatherAt(t: Double, i: Int) = cubic(seg(t, 1.2 + .03 * i, 2.4 + .03 * i)) *
    (1 - cubic(seg(t, 5.8 + .03 * (COUNT - 1 - i), 7.0 + .03 * (COUNT - 1 - i))))
private fun dealsAt(t: Double) = ((t - DEAL_START) / DEAL).coerceIn(0.0, DEALS.toDouble())
private fun captionOpacity(t: Double, k: Int): Double {
    if (k == 0) return (1 - smooth(seg(t, 1.6, 2.0))) + smooth(seg(t, 6.3, 6.7))
    val pile = smooth(seg(t, 2.2, 2.5)) * (1 - smooth(seg(t, 5.8, 6.1)))
    return pile * (1 - smooth((abs(dealsAt(t) - (k - 1)) - .35) / .15))
}

/** Only time and spin invalidate drawing; UI layout does not recompose per frame. */
private class ArchiveClock {
    var time by mutableDoubleStateOf(0.0)
    var spin by mutableDoubleStateOf(0.0)
    var manual by mutableStateOf(false)
    var elapsed = 0.0
    var origin = 0.0
    var velocity = 0.0
    var touching = false
    var released = -1.0
    var settleFrom = 0.0

    fun step(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching) {
            origin += dt
            return
        }
        if (manual) {
            origin += dt
            val since = elapsed - released
            if (since < HOLD) {
                spin += velocity * dt
                velocity *= exp(-3 * dt)
                settleFrom = spin
            } else {
                // JavaScript Math.round resolves a half-turn towards positive infinity.
                val target = floor(settleFrom / (2 * PI) + .5) * 2 * PI
                val k = cubic(((since - HOLD) / RETURN).coerceIn(0.0, 1.0))
                spin = settleFrom + (target - settleFrom) * k
                if (since > HOLD + RETURN) {
                    spin = 0.0
                    manual = false
                }
            }
            return
        }
        if (autoPlay) time = (elapsed - origin) % LOOP
    }
}

/** Decode and compose artwork once. Per-frame work is bitmap transforms and opacity. */
private class PosterArtwork(assets: AssetManager, anton: Typeface) {
    val posters = DESIGNS.mapIndexed { d, design ->
        val art = assets.open("${ASSETS}poster-${design.art}.jpg").use { BitmapFactory.decodeStream(it) }
        val bitmap = Bitmap.createBitmap(600, 804, Bitmap.Config.ARGB_8888)
        val c = NativeCanvas(bitmap)
        c.scale(3f, 3f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val card = RectF(0f, 0f, W, H)
        val clip = Path().apply { addRoundRect(card, 3f, 3f, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        p.color = 0xFFD9D6CF.toInt()
        c.drawRect(card, p)
        val scale = max(W / art.width, H / art.height)
        val sw = W / scale
        val sh = H / scale
        val source = RectF((art.width - sw) / 2, (art.height - sh) / 2, (art.width + sw) / 2, (art.height + sh) / 2)
        val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply { setRectToRect(source, card, Matrix.ScaleToFit.FILL) })
        p.color = WHITE
        p.shader = shader
        c.drawRect(card, p)
        p.shader = null
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = anton }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 600, false)
                else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        fun word(value: String, x: Float, y: Float, size: Float, color: Int, right: Boolean = false) {
            // Measure at 100 pt, then scale the canvas as in the RN glyph-width calculation.
            big.textSize = 100f
            big.color = color
            val k = size / 100
            c.save()
            c.translate(if (right) x - big.measureText(value) * k else x, y)
            c.scale(k, k)
            c.drawText(value, 0f, 0f, big)
            c.restore()
        }
        fun meta(y: Float, color: Int, alpha: Float) {
            small.color = color
            small.alpha = (255 * alpha).roundToInt()
            c.drawText(design.meta, 13f, y, small)
        }
        when (d) {
            0 -> {
                word("FORM", 12f, H - 14, 86f, WHITE)
                meta(22f, WHITE, .9f)
            }
            1 -> {
                p.color = RED
                c.drawRect(0f, H - 66, W, H, p)
                word("ASCENT", 12f, H - 14, 46f, WHITE)
                meta(22f, BLACK, .85f)
            }
            2 -> {
                word("YEP.", 10f, H - 12, 96f, BLACK)
                meta(22f, BLACK, .85f)
            }
            3 -> {
                word("BLUE", 12f, 72f, 62f, WHITE)
                word("HOUR", 12f, 132f, 62f, WHITE)
                meta(H - 14, WHITE, .85f)
            }
            4 -> {
                word("DRAW", W - 12, H - 14, 84f, RED, right = true)
                meta(22f, BLACK, .85f)
            }
            5 -> {
                word("BLOOM", 12f, H - 14, 70f, WHITE)
                meta(22f, WHITE, .9f)
            }
            6 -> {
                word("No.7", 12f, H - 12, 92f, BLACK)
                meta(22f, BLACK, .85f)
            }
            else -> {
                p.color = WHITE
                c.drawRect(10f, H - 62, W - 10, H - 10, p)
                word("LOUD TYPE", 20f, H - 20, 38f, BLACK)
                meta(22f, WHITE, .9f)
            }
        }
        c.restore()
        art.recycle()
        bitmap
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BLACK
        alpha = (255 * .14).roundToInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val target = RectF(0f, 0f, W, H)
    // Software masks preserve shape blur on API 26 too. Cache quarter-dp sigma steps at 2x.
    private val shadows = List(33) { index ->
        val bitmap = Bitmap.createBitmap(592, 728, Bitmap.Config.ALPHA_8)
        val canvas = NativeCanvas(bitmap)
        canvas.scale(2f, 2f)
        canvas.translate(48f, 48f)
        val sigma = 4f + index * .25f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            maskFilter = BlurMaskFilter((sigma - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(4f, 10f, W, H + 6, 3f, 3f, paint)
        bitmap
    }
    private val shadowTarget = RectF(-48f, -48f, W + 48, H + 48)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.BLACK }

    fun draw(c: NativeCanvas, t: Double, spin: Double, cx: Double, cy: Double) {
        c.drawCircle(cx.toFloat(), cy.toFloat(), RING.toFloat(), orbitPaint)
        val base = 2 * PI * t / LOOP + spin - PI / 2
        val done = dealsAt(t)
        val shuffles = floor(done).toInt()
        val u = done - shuffles
        val order = MutableList(COUNT) { (it - shuffles + COUNT * 4) % COUNT }
        val dealing = if (shuffles < DEALS && done > 0) (COUNT - 1 - shuffles + COUNT) % COUNT else -1
        if (dealing >= 0 && u >= .5) {
            order.remove(dealing)
            order.add(0, dealing)
        }
        val gathers = List(COUNT) { gatherAt(t, it) }
        val items = (0 until COUNT).sortedBy { if (gathers[it] < .5) gathers[it] else 1.0 + order.indexOf(it) }
        for (i in items) {
            val g = gathers[i]
            val a = base + i * 2 * PI / COUNT
            val pile = PILE[i]
            var px = cx + cos(a) * RING
            var py = cy + sin(a) * RING
            px += (cx + pile.dx - px) * g
            py += (cy + pile.dy - py) * g
            var rot = pile.tilt * g
            if (i == dealing) {
                val swing = sin(PI * u)
                px += 92 * swing
                py -= 26 * swing
                rot += 16 * swing
            }
            val scale = (SMALL + (1 - SMALL) * g).toFloat()
            c.save()
            c.translate(px.toFloat(), py.toFloat())
            c.rotate(rot.toFloat())
            c.scale(scale, scale)
            c.translate(-W / 2, -H / 2)
            shadowPaint.alpha = ((.08 + .14 * g) * 255).roundToInt()
            c.drawBitmap(shadows[(g * 32).roundToInt()], null, shadowTarget, shadowPaint)
            c.drawBitmap(posters[i % 8], null, target, imagePaint)
            c.restore()
        }
    }

    fun dispose() {
        posters.forEach(Bitmap::recycle)
        shadows.forEach(Bitmap::recycle)
    }
}

@Composable
fun OrbitStackArchive(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    brand: String = "PAPER ROOM",
    line: String = "Swiss posters 1957–1992",
    action: String = "Browse the archive",
) {
    ArchiveSystemBars()
    val reduced = rememberReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val clock = remember { ArchiveClock() }
    val context = LocalContext.current
    val anton = remember(context) { Typeface.createFromAsset(context.assets, "${ASSETS}Anton-Regular.ttf") }
    val antonFamily = remember(anton) { FontFamily(androidx.compose.ui.text.font.Typeface(anton)) }
    val artwork = remember(context, anton) { PosterArtwork(context.assets, anton) }
    DisposableEffect(artwork) { onDispose { artwork.dispose() } }
    val density = LocalDensity.current.density
    val view = LocalView.current
    val needsFrames = autoPlay || clock.manual
    LaunchedEffect(active, reduced, autoPlay, needsFrames) {
        if (!active || reduced || !needsFrames) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1_000_000_000.0
            previous = now
            clock.step(dt, autoPlay)
        }
    }
    val centeredLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    val textBase = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeightStyle = centeredLine)

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER)) {
        val cx = maxWidth.value / 2.0
        val cy = maxHeight.value * .56
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val width = maxWidth.value
        val footerTop = maxHeight.value - bottom.value - 14 - 56
        Column(Modifier.fillMaxWidth().offset(y = top + 8.dp).padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().height(30.dp).drawBehind {
                    drawRect(INK, topLeft = Offset(0f, size.height - 1.5.dp.toPx()), size = Size(size.width, 1.5.dp.toPx()))
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(brand, style = textBase.copy(color = INK, fontFamily = antonFamily, fontSize = 18.sp, letterSpacing = 1.sp))
                BasicText("16 / 16", style = textBase.copy(color = INK, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp))
            }
            val headlineStyle = textBase.copy(color = INK, fontFamily = antonFamily, fontSize = 88.sp, lineHeight = 112.sp, letterSpacing = (-.5).sp)
            BasicText("POSTER", Modifier.padding(top = 2.dp).height(112.dp), style = headlineStyle, maxLines = 1, softWrap = false)
            BasicText("ARCHIVE", Modifier.offset(y = (-24).dp).height(112.dp), style = headlineStyle, maxLines = 1, softWrap = false)
        }
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "$brand. $line. Drag to spin the orbit." }
                .pointerInput(reduced, cy, density, width, footerTop) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // The RN footer is outside GestureDetector, including its bottom padding.
                        if (down.position.y / density >= footerTop &&
                            down.position.x / density in 20f..(width - 20f)
                        ) return@awaitEachGesture
                        clock.touching = true
                        clock.manual = true
                        clock.velocity = 0.0
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var lastX = down.position.x
                        var dragging = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!dragging) {
                                    val delta = change.position - down.position
                                    dragging = hypot(delta.x, delta.y) / density >= 4f
                                }
                                if (dragging) {
                                    change.consume()
                                    val side = if (change.position.y / density > cy) -1 else 1
                                    clock.spin += side * (change.position.x - lastX) / density / RING
                                    lastX = change.position.x
                                    clock.velocity = side * tracker.calculateVelocity().x / density / RING
                                }
                            }
                        } finally {
                            clock.touching = false
                            clock.released = clock.elapsed
                        }
                    }
                },
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            artwork.draw(canvas, clock.time, clock.spin, cx, cy)
            canvas.restore()
        }
        Box(Modifier.fillMaxWidth().offset(y = (cy + 196).dp).padding(horizontal = 20.dp).height(60.dp)) {
            val captions = listOf(Design("Sixteen posters", line, "")) + TOPS.map { DESIGNS[it] }
            captions.forEachIndexed { k, design ->
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = captionOpacity(clock.time, k).toFloat() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(design.title.uppercase(Locale.ROOT), style = textBase.copy(color = INK, fontFamily = antonFamily, fontSize = 34.sp, lineHeight = 40.sp, textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
                    BasicText(design.meta, Modifier.padding(top = 2.dp), style = textBase.copy(color = MUTED, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .3.sp), maxLines = 1, softWrap = false)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = bottom + 14.dp)
                .height(56.dp).background(INK, RoundedCornerShape(4.dp)).padding(horizontal = 20.dp)
                .semantics(mergeDescendants = true) { role = Role.Button },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(action, style = textBase.copy(color = PAPER, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp))
            BasicText("→", style = textBase.copy(color = PAPER, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun ArchiveSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

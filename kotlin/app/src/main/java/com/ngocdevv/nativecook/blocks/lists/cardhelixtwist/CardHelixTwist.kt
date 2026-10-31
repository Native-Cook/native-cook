package com.ngocdevv.nativecook.blocks.lists.cardhelixtwist

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// Port of the RN collection screen: fourteen 150 x 210 dp cards projected with focal 900 dp,
// stage tilt -10 degrees, independent homographies, and the same depth ordering.
// LOOP = 8 s: rest 0-1; cubic wind 1-3.4, hold to 4.6, cubic unwind 4.6-6.8; rest to 8.
// Twist rises to .42 rad, lift from 3 to 16 dp. A sine-eased turn spans 1-6.8 s.
// Drag dx winds by .42*clamp(dx/220), turns by dx/120. Release holds 2 s, then returns
// to the nearest whole turn over .9 s (cubic). Reduced motion shows the resting stack.

/** Seconds. */
const val LOOP = 8.0
private const val N = 14
private const val CW = 150f
private const val CH = 210f
private const val PEAK = .42
private const val TWO_PI = PI * 2
private val BACKGROUND = Color(0xFF0B0A12)
private val HUES = arrayOf(
    intArrayOf(0xFFFF6FD8.toInt(), 0xFF3813C2.toInt()),
    intArrayOf(0xFF21D4FD.toInt(), 0xFFB721FF.toInt()),
    intArrayOf(0xFFFAD961.toInt(), 0xFFF76B1C.toInt()),
    intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),
    intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt()),
    intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),
    intArrayOf(0xFFA18CD1.toInt(), 0xFFFBC2EB.toInt()),
)
private val TITLES = arrayOf("PRISM", "HALO", "EMBER", "VERDANT", "BLOOM", "TIDAL", "LILAC")
private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun cubic(x: Double) = if (x < .5) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2
private data class Pose(val twist: Double = 0.0, val lift: Double = 3.0, val turn: Double = 0.0)
private fun helixAt(t: Double): Pose {
    val wind = cubic(seg(t, 1.0, 3.4)) * (1 - cubic(seg(t, 4.6, 6.8)))
    return Pose(PEAK * wind, 3 + 13 * wind, PI * (1 - cos(PI * seg(t, 1.0, 6.8))))
}

/** Only the draw clock is snapshot state: frame updates never recompose the screen. */
private class HelixClock {
    var elapsed = 0.0
    var origin = 0.0
    var pose = Pose()
    var touching = false
    var manual = false
    var released = -1.0
    var userW = 0.0
    var userTurn = 0.0
    var fromTurn = 0.0
    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching || manual) {
            var k = 0.0
            if (!touching) {
                val since = elapsed - released
                k = cubic(((since - 2) / .9).coerceIn(0.0, 1.0))
                if (since > 2.9) {
                    manual = false
                    origin = elapsed
                }
            }
            val w = userW * (1 - k)
            val target = floor(fromTurn / TWO_PI + .5) * TWO_PI
            pose = Pose(w, 3 + 13 * abs(w) / PEAK, if (touching) userTurn else fromTurn + (target - fromTurn) * k)
        } else pose = if (autoPlay) helixAt((elapsed - origin) % LOOP) else Pose()
    }
    fun begin() {
        touching = true
        manual = true
        userTurn = pose.turn
        fromTurn = pose.turn
    }
    fun drag(dx: Double) {
        userW = PEAK * (dx / 220).coerceIn(-1.0, 1.0)
        userTurn = fromTurn + dx / 120
    }
    fun release() {
        touching = false
        fromTurn = userTurn
        released = elapsed
    }
}

private class HelixRenderer(typeface: Typeface) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val card = RectF(0f, 0f, CW, CH)
    private val padded = RectF(-1f, -1f, CW + 1, CH + 1)
    private val clip = Path().apply { addRoundRect(card, 14f, 14f, Path.Direction.CW) }
    private val source = floatArrayOf(0f, 0f, CW, 0f, CW, CH, 0f, CH)
    private val matrix = Matrix()
    private val foil = intArrayOf(0xFF1B1530.toInt(), 0xFF7C5CFF.toInt(), 0xFF38F9D7.toInt(), 0xFFFF6FD8.toInt(), 0xFF1B1530.toInt())
    private val fronts = Array(N) { j ->
        // Cache static art at 3 px/dp. Only perspective and the back sheen change per frame.
        Bitmap.createBitmap(456, 636, Bitmap.Config.ARGB_8888).also { bitmap ->
            val c = NativeCanvas(bitmap)
            c.scale(3f, 3f)
            c.translate(1f, 1f)
            front(c, j, typeface)
        }
    }
    private fun reset() {
        paint.shader = null
        paint.color = NativeColor.WHITE
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }
    private fun front(c: NativeCanvas, j: Int, face: Typeface) {
        reset()
        paint.shader = LinearGradient(0f, 0f, CW, CH, HUES[j % 7], null, Shader.TileMode.CLAMP)
        c.drawRoundRect(card, 14f, 14f, paint)
        c.save()
        c.clipPath(clip)
        reset()
        paint.shader = RadialGradient(CW * .7f, CH * .32f, 70f, intArrayOf(0xBFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(CW * .7f, CH * .32f, 70f, paint)
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = 0x59FFFFFF
        c.drawCircle(CW * .3f, CH * .62f, 38f + (j % 3) * 8f, paint)
        c.restore()
        reset()
        paint.typeface = face
        paint.textSize = 100f
        c.save()
        c.translate(12f, CH - 30)
        c.scale(.16f, .16f)
        c.drawText(TITLES[j % 7], 0f, 0f, paint)
        c.restore()
        c.save()
        c.translate(12f, CH - 14)
        c.scale(.09f, .09f)
        c.drawText("No. ${(421 + j * 37).toString().padStart(4, '0')}", 0f, 0f, paint)
        c.restore()
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x8CFFFFFF.toInt()
        c.drawRoundRect(card, 14f, 14f, paint)
    }
    private fun back(c: NativeCanvas, j: Int, turn: Double) {
        val s = ((turn * .5 + j * .2) % 1).toFloat()
        reset()
        paint.shader = LinearGradient(-CW + s * CW * 2, 0f, s * CW * 2, CH, foil, floatArrayOf(0f, .3f, .5f, .7f, 1f), Shader.TileMode.MIRROR)
        c.drawRoundRect(card, 14f, 14f, paint)
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x99FFFFFF.toInt()
        c.drawCircle(CW / 2, CH / 2, 26f, paint)
        c.drawCircle(CW / 2, CH / 2, 14f, paint)
    }
    private data class Item(val j: Int, val z: Double, val quad: FloatArray, val facing: Boolean)
    fun draw(c: NativeCanvas, p: Pose, width: Float, height: Float) {
        val cx = width / 2
        val cy = height * .52f
        reset()
        paint.shader = RadialGradient(cx, cy, 260f, intArrayOf(0x597C5CFF, 0x007C5CFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 260f, paint)
        val ct = cos(-10 * PI / 180)
        val st = sin(-10 * PI / 180)
        val items = ArrayList<Item>(N)
        for (j in 0 until N) {
            val theta = p.turn + p.twist * (j - 6.5)
            val yc = (6.5 - j) * p.lift
            val quad = FloatArray(8)
            var depth = 0.0
            for (q in 0..3) {
                val x = if (q == 0 || q == 3) -CW / 2 else CW / 2
                val y = yc + if (q < 2) -CH / 2 else CH / 2
                val z = -x * sin(theta) - j * .6
                val y1 = y * ct - z * st
                val z1 = y * st + z * ct
                val scale = 900 / (900 - z1)
                quad[q * 2] = (cx + x * cos(theta) * scale).toFloat()
                quad[q * 2 + 1] = (cy + y1 * scale).toFloat()
                depth += z1 / 4
            }
            val cross = (quad[2] - quad[0]) * (quad[7] - quad[1]) - (quad[3] - quad[1]) * (quad[6] - quad[0])
            items.add(Item(j, depth - j * .01, quad, cross > 0))
        }
        items.sortBy { it.z }
        for (item in items) {
            if (!matrix.setPolyToPoly(source, 0, item.quad, 0, 4)) continue
            c.save()
            c.concat(matrix)
            if (item.facing) {
                reset()
                c.drawBitmap(fronts[item.j], null, padded, paint)
            } else back(c, item.j, p.turn)
            c.restore()
        }
    }
}

@Composable
fun CardHelixTwist(
    modifier: Modifier = Modifier,
    brand: String = "Holo",
    title: String = "Spring prints",
    action: String = "Open collection",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val face = remember(context) { Typeface.createFromAsset(context.assets, "cardhelixtwist/cardhelixtwist_ArchivoExpanded-Black.ttf") }
    val family = remember(face) { FontFamily(androidx.compose.ui.text.font.Typeface(face)) }
    val renderer = remember(face) { HelixRenderer(face) }
    val clock = remember { HelixClock() }
    val frame = remember { mutableLongStateOf(0) }
    var gestureVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(autoPlay, reduced, active, gestureVersion) {
        if (reduced) {
            clock.pose = Pose()
            clock.manual = false
            clock.touching = false
            frame.longValue++
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        var previous = 0L
        while (autoPlay || clock.manual) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                clock.advance(dt, autoPlay)
                frame.longValue++
            }
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val plain = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), color = Color.White, letterSpacing = 0.sp)
    val heavy = plain.copy(fontFamily = family)
    Box(modifier.fillMaxSize().background(BACKGROUND)) {
        // The header is behind the hero canvas, as in the RN hierarchy.
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + 8.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(brand, style = heavy.copy(fontSize = 20.sp))
                Spacer(Modifier.weight(1f))
                Text("14 cards", style = plain.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .55f)))
            }
            Text(title, Modifier.padding(top = 14.dp), style = heavy.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp))
            Text("Seven artists, two of each, printed on holographic foil.", Modifier.padding(top = 6.dp), style = plain.copy(fontSize = 15.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = .6f)))
        }
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "A stack of print cards winding into a helix. Drag to wind it." }
                .pointerInput(reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        clock.begin()
                        gestureVersion++
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                clock.drag(((change.position.x - down.position.x) / density).toDouble())
                            }
                        } finally { clock.release() }
                    }
                },
        ) {
            frame.longValue
            val c = drawContext.canvas.nativeCanvas
            c.save()
            c.scale(density, density)
            renderer.draw(c, clock.pose, size.width / density, size.height / density)
            c.restore()
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 24.dp)
                // The RN footer is outside its gesture detector and intercepts touches.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
                .padding(bottom = insets.calculateBottomPadding() + 14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("2 / 7", style = heavy.copy(fontSize = 20.sp))
                    Text("Sets complete", Modifier.padding(top = 2.dp), style = plain.copy(fontSize = 13.sp, color = Color.White.copy(alpha = .55f)))
                }
                Column {
                    Text("Rare", style = heavy.copy(fontSize = 20.sp))
                    Text("Next pull", Modifier.padding(top = 2.dp), style = plain.copy(fontSize = 13.sp, color = Color.White.copy(alpha = .55f)))
                }
            }
            Box(
                Modifier.fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp)).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) { Text(action, style = heavy.copy(fontSize = 15.sp, color = BACKGROUND)) }
        }
    }
}

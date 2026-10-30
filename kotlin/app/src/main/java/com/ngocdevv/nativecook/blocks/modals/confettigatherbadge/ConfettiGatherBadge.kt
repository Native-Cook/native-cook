package com.ngocdevv.nativecook.blocks.modals.confettigatherbadge

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The RN reference's violet onboarding screen, using its original Nunito faces and sprites.
 * Rest 0–.6; squash .6–.9; pop .9–1.45; spiral burst .9–2.1 (15 ms stagger);
 * drift 2.1–3.4; gather 3.4–4.4; gulp 4.3–4.85. Bob and tilt repeat twice per loop.
 * A tap starts at .6, plays once, rests for two seconds, then restarts autoPlay.
 */
private const val LOOP = 5.0
private const val START = 0.6
private const val GHOST = 212f
private const val PIECES = 22
private const val ASSETS = "confettigatherbadge/confettigatherbadge_"
private val Top = Color(0xFF7A5BFF)
private val Bottom = Color(0xFF4A2ED3)
private val Glow = Color(0xFFB9A6FF)
private val Soft = Color.White.copy(alpha = .78f)

private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
private fun easeInCubic(x: Double) = x * x * x
private fun smooth(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
private fun bounce(x: Double) = if (x <= 0 || x >= 1) 0.0 else sin(x * PI * 2.5) * exp(-4 * x)

private data class Piece(
    val sprite: Int,
    val angle: Double,
    val radius: Double,
    val size: Double,
    val spin: Double,
    val turn: Double,
    val front: Boolean,
)

private val pieces = List(PIECES) { i ->
    fun h(n: Int): Double {
        val x = sin((i + 1) * 12.9898 + n * 78.233) * 43758.5453
        return x - floor(x)
    }
    val angle = i.toDouble() / PIECES * PI * 2 + (h(1) - .5) * .35
    val front = i % 2 == 0 && sin(angle) < -.15
    Piece(
        sprite = i * 5 % 6,
        angle = angle,
        radius = 120 + 130 * h(2),
        size = (46 + 34 * h(3)) * if (front) 1.12 else .9,
        spin = (if (h(4) > .5) 1 else -1) * (40 + 80 * h(5)),
        turn = h(6) * 360,
        front = front,
    )
}

private data class GhostState(val sx: Float, val sy: Float, val bob: Float, val tilt: Float, val glow: Float)

private fun ghostAt(t: Double): GhostState {
    val squash = easeInCubic(seg(t, .6, .9)) * (1 - seg(t, .9, .96))
    val pop = bounce(seg(t, .9, 1.45)) * .42
    val gulp = bounce(seg(t, 4.3, 4.85)) * .3
    val phase = t / LOOP * PI * 4
    return GhostState(
        sx = (1 + .12 * squash - pop * .35 + gulp).toFloat(),
        sy = (1 - .14 * squash + pop + gulp).toFloat(),
        bob = (sin(phase) * 6).toFloat(),
        tilt = (sin(phase + .8) * 3).toFloat(),
        glow = (.5 + .35 * sin(PI * seg(t, .9, 2.1))).toFloat(),
    )
}

/** Decode the seven images once and reuse the small software surface for the contact shadow. */
private class Scene(private val assets: AssetManager) {
    private fun image(name: String) = assets.open(ASSETS + name).use { BitmapFactory.decodeStream(it) }
    private val ghost = image("ghost.png")
    private val sprites = listOf("ribbon", "star", "ball", "ring", "squiggle", "cube")
        .map { image("confetti-$it.png") }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val shadow = Bitmap.createBitmap(512, 184, Bitmap.Config.ARGB_8888)
    private val shadowCanvas = Canvas(shadow).apply { scale(2f, 2f) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF24107A.toInt()
        alpha = (.42f * 255).roundToInt()
        // Android's mask filter takes radius; the reference specifies a 9 dp sigma.
        maskFilter = BlurMaskFilter((9f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
    }
    private var shadowWidth = -1f

    fun drawPieces(canvas: Canvas, t: Double, cx: Float, cy: Float, front: Boolean) {
        pieces.forEachIndexed { i, piece ->
            if (piece.front != front) return@forEachIndexed
            val out = easeOutCubic(seg(t, .9 + i * .015, 2.1 + i * .015))
            val back = easeInCubic(seg(t, 3.4 + (PIECES - 1 - i) * .01, 4.4 - i * .003))
            val spread = out * (1 - back)
            if (spread <= .001) return@forEachIndexed
            val swirl = -1.1 * (1 - out) * sign(piece.spin) + .35 * smooth(seg(t, 2.1, 3.4)) + 1.5 * back
            val angle = piece.angle + swirl
            val radius = piece.radius * spread
            val x = cx + (cos(angle) * radius).toFloat()
            val y = cy + (sin(angle) * radius * 1.25).toFloat()
            val size = (piece.size * (.15 + .85 * min(1.0, spread * 1.6))).toFloat()
            paint.alpha = (255 * min(1.0, spread * 5)).roundToInt()
            rect.set(-size / 2, -size / 2, size / 2, size / 2)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate((piece.turn + piece.spin * (t - .9) * spread).toFloat())
            canvas.drawBitmap(sprites[piece.sprite], null, rect, paint)
            canvas.restore()
        }
    }

    fun drawGhost(canvas: Canvas, t: Double, cx: Float, cy: Float) {
        val g = ghostAt(t)
        val width = 118 * g.sx * (1 - g.bob / 60)
        if (width != shadowWidth) {
            // Rasterize only this changing oval. Scaling a cached blur would change its sigma.
            shadowCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            shadowCanvas.drawOval(128 - width / 2, 36f, 128 + width / 2, 56f, shadowPaint)
            shadowWidth = width
        }
        val shadowTop = cy + GHOST * .56f - 36
        rect.set(cx - 128, shadowTop, cx + 128, shadowTop + 92)
        paint.alpha = 255
        canvas.drawBitmap(shadow, null, rect, paint)
        canvas.save()
        canvas.translate(cx, cy + g.bob + GHOST * .42f)
        canvas.rotate(g.tilt)
        canvas.scale(g.sx, g.sy)
        canvas.translate(0f, -GHOST * .42f)
        rect.set(-GHOST / 2, -GHOST / 2, GHOST / 2, GHOST / 2)
        canvas.drawBitmap(ghost, null, rect, paint)
        canvas.restore()
    }
}

private fun typeStyle(family: FontFamily, size: Int, height: Int, color: Color, tracking: Float = 0f) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    // iOS applies the requested leading even below Nunito's intrinsic line height.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight),
)

@Composable
fun ConfettiGatherBadge(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
    headline: String = "All done!",
    body: String = "Your week is planned.\nNow go and enjoy it.",
    action: String = "Let’s go",
    secondary: String = "Edit my plan",
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val scene = remember(context) { Scene(context.assets) }
    val black = remember(context) { FontFamily(Font(ASSETS + "Nunito-Black.ttf", context.assets)) }
    val extra = remember(context) { FontFamily(Font(ASSETS + "Nunito-ExtraBold.ttf", context.assets)) }
    val time = remember { mutableDoubleStateOf(0.0) }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    var origin by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Only draw and graphicsLayer read time; no screen-wide per-frame recomposition.
    LaunchedEffect(active, reduced, autoPlay, playing) {
        if (reduced) {
            time.doubleValue = 0.0
            return@LaunchedEffect
        }
        if (!active || (!autoPlay && !playing)) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            if (previous != 0L) elapsed.doubleValue += (now - previous) / 1e9
            previous = now
            val local = elapsed.doubleValue - origin
            if (playing) {
                when {
                    local < LOOP -> time.doubleValue = local
                    local < LOOP + 2 -> time.doubleValue = 0.0
                    else -> {
                        playing = false
                        origin = elapsed.doubleValue
                        time.doubleValue = 0.0
                    }
                }
            } else time.doubleValue = if (autoPlay) local % LOOP else 0.0
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Top, Bottom)))) {
        val cy = maxHeight * .385f
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val headlineStyle = remember(black) { typeStyle(black, 62, 70, Color.White, -1f) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(reduced) {
                    if (!reduced) detectTapGestures {
                        playing = true
                        origin = elapsed.doubleValue - START
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                }
                .drawBehind {
                    val t = time.doubleValue
                    val cx = size.width / 2
                    val centreY = size.height * .385f
                    drawCircle(
                        Brush.radialGradient(listOf(Glow, Glow.copy(alpha = 0f)), Offset(cx, centreY), 230.dp.toPx()),
                        radius = 230.dp.toPx(),
                        center = Offset(cx, centreY),
                        alpha = ghostAt(t).glow,
                    )
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    canvas.scale(density, density)
                    scene.drawPieces(canvas, t, cx / density, centreY / density, false)
                    scene.drawGhost(canvas, t, cx / density, centreY / density)
                    canvas.restore()
                },
        )

        Row(
            Modifier
                .offset(y = safe.calculateTopPadding() + 10.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("tidy", style = typeStyle(black, 28, 34, Color.White, -.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { Box(Modifier.size(22.dp, 5.dp).background(Color.White, RoundedCornerShape(3.dp))) }
            }
        }

        Column(
            Modifier.offset(y = cy + 136.dp).fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Layout(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = headline },
                content = {
                    headline.forEachIndexed { k, ch ->
                        Text(
                            ch.toString(),
                            modifier = Modifier.graphicsLayer {
                                val b = bounce(seg(time.doubleValue, .95 + k * .045, 1.75 + k * .045)).coerceAtLeast(0.0).toFloat()
                                translationY = -12.dp.toPx() * b
                                scaleX = 1 + .2f * b
                                scaleY = 1 + .2f * b
                            },
                            style = headlineStyle,
                        )
                    }
                },
            ) { measurables, constraints ->
                val letters = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                // Compose trims a single glyph's tracking; RN keeps -1 pt in each Text's advance.
                val tracking = 1.sp.toPx()
                val width = letters.sumOf { it.width.toDouble() } - letters.size * tracking
                layout(width.roundToInt().coerceAtLeast(0), letters.maxOfOrNull { it.height } ?: 0) {
                    var x = 0f
                    letters.forEach { letter ->
                        letter.placeRelative(x.roundToInt(), 0)
                        x += letter.width - tracking
                    }
                }
            }
            Text(
                body,
                modifier = Modifier.padding(top = 8.dp),
                style = typeStyle(extra, 17, 23, Soft),
                textAlign = TextAlign.Center,
            )
        }

        // Near pieces only fly above the ghost, matching the reference's foreground layer.
        Box(Modifier.fillMaxSize().drawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            scene.drawPieces(canvas, time.doubleValue, size.width / density / 2, size.height / density * .385f, true)
            canvas.restore()
        })

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = safe.calculateBottomPadding() + 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp))
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = typeStyle(black, 19, 26, Bottom))
            }
            Text(
                secondary,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                style = typeStyle(extra, 15, 20, Soft),
            )
        }
    }
}

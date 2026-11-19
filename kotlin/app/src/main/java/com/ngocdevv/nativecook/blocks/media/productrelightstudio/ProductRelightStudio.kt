package com.ngocdevv.nativecook.blocks.media.productrelightstudio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Product Relight Studio: a photo editor's Light tool on a product shot. The photo is a G-buffer rendered in Blender
// (a pocket watch on a seamless paper sweep) shaded live by an AGSL runtime shader, so the light really moves: drag the
// softbox over the photo, pick Studio, Golden hour or Moody blue, set the intensity, tap Compare and drag the divider to
// see the flat original. A 16-second film uses every control once; it stops when touched and comes back 2 s after the
// last touch. Reduced motion (or autoPlay off) shows the poster and leaves every control to the finger.
// A port of ProductRelightStudio.tsx (and of the Swift port's tween-based touch state): every frame is a function of the
// loop time or of the touch tweens, read only in draw and graphicsLayer lambdas.
// API 26–32 have no runtime shaders: there the photo is the flat "before" pass (the controls still work, the light does
// not move the shading).

private val BG = Color(0xFF0C0C0D)
private val INK = Color(0xFFF5F5F4)
private val SECONDARY = INK.copy(alpha = 0.64f)
private val ACCENT = Color(0xFFFFD60A)
private val CHEVRON = Color(0xFF3A3A3C)

/** The five passes, decoded once as plain sRGB bitmaps (the data passes stay as stored). */
private class PrsPasses(val albedo: Bitmap, val gbuf: Bitmap, val mat: Bitmap, val matte: Bitmap, val before: Bitmap)

private fun loadPasses(context: Context): PrsPasses {
    fun load(name: String): Bitmap {
        val o = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        }
        return context.assets.open("product-relight-studio/$name").use { BitmapFactory.decodeStream(it, null, o)!! }
    }
    return PrsPasses(load("albedo.jpg"), load("gbuffer.png"), load("material.jpg"), load("matte.png"), load("before.jpg"))
}

/** The relighting shader with its five image inputs, scaled to the photo's side in pixels. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PrsShade(p: PrsPasses) {
    val shader = RuntimeShader(RELIGHT_AGSL)
    private val inputs = listOf("albedo" to p.albedo, "gbuf" to p.gbuf, "mat" to p.mat, "matte" to p.matte, "before" to p.before)
        .map { (name, bmp) ->
            name to BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    private val matrix = Matrix()
    private var sizePx = -1f
    private val rig = DoubleArray(16)
    val paint = Paint().apply { isAntiAlias = false }

    fun update(sizePx: Float, v: PrsValues) {
        if (sizePx != this.sizePx) {
            this.sizePx = sizePx
            matrix.setScale(sizePx / 1024f, sizePx / 1024f)
            for ((name, s) in inputs) {
                s.setLocalMatrix(matrix)
                shader.setInputShader(name, s)
            }
        }
        PrsMotion.rigMix(v.w0, v.w1, v.w2, rig)
        val r = rig
        val k = r[3] * (max(0.04, v.level) / PrsMotion.LEVEL)
        shader.setFloatUniform("size", sizePx)
        shader.setFloatUniform("light", v.lx.toFloat(), v.ly.toFloat(), r[4].toFloat())
        shader.setFloatUniform("keyCol", (r[0] * k).toFloat(), (r[1] * k).toFloat(), (r[2] * k).toFloat())
        shader.setFloatUniform("rimCol", (r[5] * r[8]).toFloat(), (r[6] * r[8]).toFloat(), (r[7] * r[8]).toFloat())
        shader.setFloatUniform("ambCol", r[9].toFloat(), r[10].toFloat(), r[11].toFloat())
        shader.setFloatUniform("paper", r[12].toFloat(), r[13].toFloat(), r[14].toFloat())
        shader.setFloatUniform("soft", r[15].toFloat())
        shader.setFloatUniform("split", v.split.toFloat())
        // a RuntimeShader's new uniforms reach the paint when it is set again
        paint.shader = shader
    }
}

private fun nowSeconds(): Double = System.nanoTime() / 1e9

@Composable
fun ProductRelightStudio(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val film = autoPlay && !reduceMotion

    val passes by produceState<PrsPasses?>(null) { value = withContext(Dispatchers.IO) { loadPasses(context) } }
    val shade = remember(passes) {
        val p = passes
        if (p != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PrsShade(p) else null
    }
    val beforePaint = remember { Paint().apply { isFilterBitmap = true } }
    val touch = remember { PrsTouch() }
    /** The one clock: the frame's values, written by the frame loop and read only in draw / layer lambdas. */
    var frame by remember { mutableStateOf(PrsValues.film(0.0)) }
    var awake by remember { mutableIntStateOf(0) }

    LaunchedEffect(film, passes, awake) {
        if (passes == null) return@LaunchedEffect
        if (touch.origin == 0.0) touch.origin = nowSeconds()
        frame = touch.frame(nowSeconds(), film)
        if (!film && awake == 0) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val now = nowSeconds()
            frame = touch.frame(now, film)
            if (!film && now - touch.lastActivity > 1.0 && !touch.dragging && !touch.draggingSplit && !touch.sliding) {
                awake = 0
                break
            }
        }
    }
    fun wake() {
        if (!film && awake == 0) awake = 1
    }

    val selected by remember {
        derivedStateOf {
            val v = frame
            if (v.w0 >= v.w1 && v.w0 >= v.w2) 0 else if (v.w1 >= v.w2) 1 else 2
        }
    }
    val levelPercent by remember { derivedStateOf { (frame.level * 100).roundToInt() } }

    val localDensity = LocalDensity.current
    val insetTop = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val insetBottom = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    fun choose(i: Int) {
        val now = nowSeconds()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        touch.takeOver(now, film)
        val w = doubleArrayOf(touch.w0.at(now), touch.w1.at(now), touch.w2.at(now))
        touch.w0 = PrsTween.to(if (i == 0) 1.0 else 0.0, w[0], now, PrsMotion.RIG_DUR, true)
        touch.w1 = PrsTween.to(if (i == 1) 1.0 else 0.0, w[1], now, PrsMotion.RIG_DUR, true)
        touch.w2 = PrsTween.to(if (i == 2) 1.0 else 0.0, w[2], now, PrsMotion.RIG_DUR, true)
        touch.lastActivity = now + PrsMotion.RIG_DUR
        wake()
    }

    fun compare() {
        val now = nowSeconds()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        touch.takeOver(now, film)
        val cur = touch.split.at(now)
        touch.split = PrsTween.to(if (cur > 0.02) 0.0 else 0.5, cur, now, 0.5, true)
        touch.lastActivity = now
        wake()
    }

    fun setIntensity(v: Double) {
        val now = nowSeconds()
        touch.takeOver(now, film)
        touch.level = v
        touch.lastActivity = now
        wake()
    }

    fun sliding(active: Boolean) {
        val now = nowSeconds()
        if (active) touch.takeOver(now, film)
        touch.sliding = active
        touch.lastActivity = now
        wake()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BG)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val s = min(1.1, max(0.88, width / 393))
        val top = insetTop.value + 44 * s
        val size = min(min(width, 560.0), height - top - insetBottom.value - 250 * s)
        val side = (width - size) / 2
        val column = min(width - 40 * s, 520.0)
        fun d(v: Double): Dp = v.dp

        // the bar
        Box(
            Modifier
                .offset(y = insetTop)
                .width(maxWidth)
                .height(d(44 * s))
                .padding(horizontal = d(20 * s)),
        ) {
            Text(
                "Light",
                modifier = Modifier.align(Alignment.Center),
                style = TextStyle(color = INK, fontSize = (17 * s).sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                "Compare",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .semantics { contentDescription = "Compare with the original" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { compare() }
                    .padding(vertical = 10.dp)
                    .graphicsLayer { alpha = (1 - 0.55 * frame.dip).toFloat() },
                maxLines = 1,
                softWrap = false,
                style = TextStyle(color = ACCENT, fontSize = (17 * s).sp, fontWeight = FontWeight.SemiBold),
            )
        }

        // the photo
        Box(
            Modifier
                .offset(x = d(side), y = d(top))
                .size(d(size))
                .clipToBounds()
                .semantics {
                    contentDescription = "Product photo of a pocket watch. Drag on the photo to move the softbox; drag the divider to compare"
                }
                .pointerInput(size, film) {
                    val sizePx = size.toFloat() * this.density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = nowSeconds()
                        touch.takeOver(start, film)
                        val split = touch.split.at(start)
                        if (split > 0.02 && abs(down.position.x - split * sizePx) < 28 * this.density) {
                            touch.draggingSplit = true
                            touch.splitGrab = PrsTween.to(1.0, touch.splitGrab.at(start), start, 0.16)
                        } else {
                            touch.dragging = true
                            touch.grab = PrsTween.to(1.0, touch.grab.at(start), start, 0.16)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        fun move(x: Float, y: Float) {
                            if (touch.draggingSplit) {
                                touch.split = PrsTween.constant(PrsMotion.clamp01((x / sizePx).toDouble()))
                            } else {
                                val r = PrsMotion.LIGHT_REACH
                                touch.lx = max(-r, min(r, x / sizePx - 0.5))
                                touch.ly = max(-r, min(r, -(y / sizePx - 0.5)))
                            }
                            touch.lastActivity = nowSeconds()
                        }
                        move(down.position.x, down.position.y)
                        wake()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            move(c.position.x, c.position.y)
                            c.consume()
                        }
                        val now = nowSeconds()
                        touch.dragging = false
                        touch.draggingSplit = false
                        touch.grab = PrsTween.to(0.0, touch.grab.at(now), now, 0.26)
                        touch.splitGrab = PrsTween.to(0.0, touch.splitGrab.at(now), now, 0.26)
                        touch.lastActivity = now
                        wake()
                    }
                },
        ) {
            val p = passes
            if (p != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val v = frame
                    val px = this.size.width
                    val sc = s.toFloat() * density
                    drawIntoCanvasCompat { c ->
                        if (shade != null) {
                            shade.update(px, v)
                            c.drawRect(0f, 0f, px, px, shade.paint)
                        } else {
                            c.drawBitmap(p.before, null, android.graphics.RectF(0f, 0f, px, px), beforePaint)
                        }
                    }
                    drawSplit(v, px, sc)
                    drawBox(v, px, sc)
                }
            }
            Pill(
                "Before", s,
                Modifier
                    .offset(x = d(12 * s), y = d(12 * s))
                    .graphicsLayer { alpha = (0.9 * PrsMotion.clamp01((frame.split - 0.14) * 8)).toFloat() },
            )
            Pill(
                "After", s,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = d(-12 * s), y = d(12 * s))
                    .graphicsLayer {
                        val sp = frame.split
                        alpha = (0.9 * PrsMotion.clamp01((0.86 - sp) * 8) * PrsMotion.clamp01(sp * 30)).toFloat()
                    },
            )
        }

        // the controls
        Box(Modifier.offset(x = d((width - column) / 2), y = d(top + size + 20 * s)).width(d(column))) {
            Box(Modifier.height(d(58 * s)).width(d(column))) {
                for (i in 0 until 3) {
                    Box(
                        Modifier.graphicsLayer {
                            val v = frame
                            val k = PrsMotion.clamp01(if (i == 0) v.w0 else if (i == 1) v.w1 else v.w2)
                            alpha = PrsMotion.clamp01((k - 0.35) / 0.65).toFloat()
                            translationY = ((1 - k) * 6 * s).toFloat() * density
                        },
                    ) {
                        Text(
                            PrsMotion.presetNames[i],
                            modifier = Modifier.height(d(34 * s)).semantics { if (i == selected) heading() },
                            maxLines = 1,
                            style = TextStyle(
                                color = INK, fontSize = (28 * s).sp, fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp, lineHeight = (34 * s).sp,
                            ),
                        )
                        Text(
                            PrsMotion.presetHints[i],
                            modifier = Modifier.offset(y = d(36 * s)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = SECONDARY, fontSize = (15 * s).sp, lineHeight = (20 * s).sp),
                        )
                    }
                }
            }
            Segmented(selected, column, Modifier.offset(y = d(58 * s + 14 * s))) { choose(it) }
            Row(
                Modifier.offset(y = d(58 * s + 14 * s + 32 + 18 * s)).width(d(column)).height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Intensity",
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = INK, fontSize = (15 * s).sp, fontWeight = FontWeight.Medium),
                )
                Box(Modifier.width(d(12 * s)))
                PrsSlider(
                    value = { frame.level },
                    onChange = { setIntensity(it) },
                    onEditing = { sliding(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Box(Modifier.width(d(12 * s)))
                Text(
                    "$levelPercent",
                    modifier = Modifier.width(d(32 * s)),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    style = TextStyle(
                        color = SECONDARY, fontSize = (15 * s).sp, fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasCompat(
    block: (android.graphics.Canvas) -> Unit,
) {
    drawContext.canvas.nativeCanvas.let(block)
}

private val shadowPaint = Paint().apply { isAntiAlias = true }

/** before | after: a hairline and a small frosted knob. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSplit(v: PrsValues, size: Float, s: Float) {
    val shown = PrsMotion.clamp01(v.split * 30).toFloat()
    if (shown <= 0f) return
    val x = (v.split * size).toFloat()
    drawLine(Color.White.copy(alpha = 0.7f * shown), Offset(x, 0f), Offset(x, size), strokeWidth = density)
    val ks = (1 + 0.12 * v.splitGrab).toFloat()
    val knob = 11 * s
    withTransform({
        translate(x, size * 0.5f)
        scale(ks, ks, Offset.Zero)
    }) {
        drawIntoCanvasCompat { c ->
            shadowPaint.color = Color.Black.copy(alpha = 0.18f * shown).toArgb()
            shadowPaint.maskFilter = BlurMaskFilter(4 * s, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(0f, 1.5f * s, knob + density, shadowPaint)
        }
        drawCircle(Color.White.copy(alpha = 0.92f * shown), knob, Offset.Zero)
        val chev = CHEVRON.copy(alpha = shown)
        val w = 1.5f * density
        drawLine(chev, Offset(-3 * s, -3.5f * s), Offset(-6 * s, 0f), w, StrokeCap.Round)
        drawLine(chev, Offset(-6 * s, 0f), Offset(-3 * s, 3.5f * s), w, StrokeCap.Round)
        drawLine(chev, Offset(3 * s, -3.5f * s), Offset(6 * s, 0f), w, StrokeCap.Round)
        drawLine(chev, Offset(6 * s, 0f), Offset(3 * s, 3.5f * s), w, StrokeCap.Round)
    }
}

/** The softbox: a thin outline (a soft dark line under it keeps it legible on white paper) and a small handle. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBox(v: PrsValues, size: Float, s: Float) {
    val a = (0.3 + 0.7 * v.grab).toFloat()
    val sc = (1 + 0.08 * v.grab).toFloat()
    val bw = 22 * s
    val bh = 30 * s
    val r = CornerRadius(6 * s)
    withTransform({
        translate((size * (0.5 + v.lx)).toFloat(), (size * (0.5 - v.ly)).toFloat())
        scale(sc, sc, Offset.Zero)
    }) {
        drawIntoCanvasCompat { c ->
            shadowPaint.color = Color.Black.copy(alpha = 0.22f * a).toArgb()
            shadowPaint.maskFilter = BlurMaskFilter(2 * density, BlurMaskFilter.Blur.NORMAL)
            shadowPaint.style = Paint.Style.STROKE
            shadowPaint.strokeWidth = 3 * density
            c.drawRoundRect(-bw / 2, -bh / 2, bw / 2, bh / 2, r.x, r.y, shadowPaint)
            shadowPaint.style = Paint.Style.FILL
        }
        drawRoundRect(
            Color.White.copy(alpha = 0.95f * a), Offset(-bw / 2, -bh / 2), Size(bw, bh), r,
            style = Stroke(width = 1.25f * density),
        )
        drawCircle(Color.Black.copy(alpha = 0.2f * a), 4 * s, Offset.Zero)
        drawCircle(Color.White.copy(alpha = a), 3 * s, Offset.Zero)
    }
}

@Composable
private fun Pill(text: String, s: Double, modifier: Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = TextStyle(color = Color.White, fontSize = (12 * s).sp, fontWeight = FontWeight.Medium))
    }
}

/** A segmented control in the platform's dark look (the React Native block's Android segments). */
@Composable
private fun Segmented(selected: Int, column: Double, modifier: Modifier, onSelect: (Int) -> Unit) {
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
    val cell = (column - 4) / 3
    Box(
        modifier
            .width(column.dp)
            .height(32.dp)
            .background(Color(0x3D767680), RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = pos * cell.toFloat() * density }
                .width(cell.dp)
                .fillMaxHeight()
                .background(Color(0xFF636366), RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                Box(
                    Modifier
                        .width(cell.dp)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        PrsMotion.presetNames[i],
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            color = INK, fontSize = 13.sp,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

/** The intensity slider: a 4 dp track with an ink fill and a round white thumb; drawn from the frame, never recomposed. */
@Composable
private fun PrsSlider(value: () -> Double, onChange: (Double) -> Unit, onEditing: (Boolean) -> Unit, modifier: Modifier) {
    val thumbR = 14.dp
    Canvas(
        modifier.pointerInput(Unit) {
            val r = thumbR.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                onEditing(true)
                fun at(x: Float) = PrsMotion.clamp01(((x - r) / (size.width - 2 * r)).toDouble())
                onChange(at(down.position.x))
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    onChange(at(c.position.x))
                    c.consume()
                }
                onEditing(false)
            }
        },
    ) {
        val r = thumbR.toPx()
        val cy = this.size.height / 2
        val x = r + (this.size.width - 2 * r) * value().toFloat()
        val h = 4.dp.toPx()
        drawRoundRect(INK.copy(alpha = 0.14f), Offset(r, cy - h / 2), Size(this.size.width - 2 * r, h), CornerRadius(h / 2))
        drawRoundRect(INK, Offset(r, cy - h / 2), Size(x - r, h), CornerRadius(h / 2))
        drawIntoCanvasCompat { c ->
            shadowPaint.color = Color.Black.copy(alpha = 0.25f).toArgb()
            shadowPaint.maskFilter = BlurMaskFilter(4.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(x, cy + 2.dp.toPx(), r - 1.dp.toPx(), shadowPaint)
        }
        drawCircle(Color.White, r, Offset(x, cy))
    }
}

package com.ngocdevv.nativecook.blocks.onboarding.glassiconorbit

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithful port of the React Native Halo onboarding. All distances are dp; the ring is centred
// at (width / 2, height * .45). Eight 68 dp glass tiles move on a radius of 92 -> 250 dp, with
// 68 degree tilt and focal length 520 dp. Tiles stay upright and are painted back to front.
//
// One 9 s loop, spin = 2 pi t / LOOP:
// 0–1.2 small ring; 1.2–2.4 swell/tilt with easeInOutCubic; 2.4–4.2 orbit the words;
// 4.2–5.1 gather with easeInCubic; 4.5–5.1 centre grows with easeOutBack(k=1.6);
// 5.1–6.2 dots bounce with .12 s offsets; 6.2–6.6 fold into an arrow; 6.6–7.8 hold;
// 7.8–9 reopen with easeOutCubic, centre shrinks 7.8–8.5 with easeInOutCubic.
// A pan after 4 dp spins by deltaX / 120 radians. The phase freezes through the gesture and
// for 2 s after release, then resumes as the extra angle decays by exp(-6 * dt).
// Reduced motion displays the finished centre arrow at t=7 and stops the frame clock.

/** Seconds. */
const val LOOP = 9.0
private const val TILE = 68f
private const val SMALL_R = 92.0
private const val BIG_R = 250.0
private const val FOCAL = 520.0
private const val HOLD = 2.0

private fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun easeInOutCubic(x: Double) = if (x < .5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
private fun easeOutBack(x: Double) = 1 + 2.6 * (x - 1).pow(3) + 1.6 * (x - 1).pow(2)

private data class Stage(val radius: Double, val tilt: Double, val ringAlpha: Float, val centre: Float, val arrow: Float)

private fun stageAt(t: Double): Stage {
    val swell = easeInOutCubic(seg(t, 1.2, 2.4))
    val gather = seg(t, 4.2, 5.1).pow(3)
    val reopen = easeOutCubic(seg(t, 7.8, 9.0))
    return Stage(
        if (gather < 1) SMALL_R + (BIG_R - SMALL_R) * swell * (1 - gather) - SMALL_R * gather else SMALL_R * reopen,
        68 * swell * (1 - gather),
        (if (gather < 1) 1 - gather else reopen).toFloat(),
        (easeOutBack(seg(t, 4.5, 5.1)) * (1 - easeInOutCubic(seg(t, 7.8, 8.5)))).toFloat(),
        easeInOutCubic(seg(t, 6.2, 6.6)).toFloat(),
    )
}

private class OrbitClock {
    var time by mutableDoubleStateOf(0.0)
    var spin by mutableDoubleStateOf(0.0)
    var elapsed = 0.0
    var origin = 0.0
    var extra = 0.0
    var touching = false
    var released = -10.0
    var paused = false

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching || elapsed - released < HOLD) {
            origin += dt
            paused = true
        } else if (paused) {
            extra *= exp(-6 * dt)
            if (abs(extra) < .002) {
                extra = 0.0
                paused = false
            }
        }
        time = if (autoPlay) ((elapsed - origin) % LOOP + LOOP) % LOOP else 0.0
        spin = 2 * PI * time / LOOP + extra
    }
}

private data class IconPart(val path: Path, val stroke: Boolean)
private data class Icon(val kind: String, val a: Int, val b: Int, val rimA: Int, val rimB: Int, val parts: List<IconPart>)

private fun color(hex: String) = AndroidColor.parseColor(hex)
private fun part(svg: String, stroke: Boolean = false) = IconPart(requireNotNull(PathParser.createPathFromPathData(svg)), stroke)

private fun makeIcons() = listOf(
    Icon("photo", color("#3CF0B4"), color("#2D7BFF"), color("#8A5CFF"), color("#3DA2FF"), listOf(
        part("M18 18 H46 A6 6 0 0 1 52 24 V42 A6 6 0 0 1 46 48 H18 A6 6 0 0 1 12 42 V24 A6 6 0 0 1 18 18 Z", true),
        part("M14 46 L27 32 L35 40 L41 34 L50 46 Z"),
        part("M44 27 A4 4 0 1 1 36 27 A4 4 0 1 1 44 27 Z"),
    )),
    Icon("eye", color("#E9FFF6"), color("#47C9FF"), color("#6E7BFF"), color("#C05CFF"), listOf(
        part("M10 32 C19 19 45 19 54 32 C45 45 19 45 10 32 Z"),
        part("M40 32 A8 8 0 1 1 24 32 A8 8 0 1 1 40 32 Z"),
    )),
    Icon("note", color("#FFE15A"), color("#FF6AD5"), color("#7A5CFF"), color("#3DA2FF"), listOf(
        part("M27 44 V20 L45 16 V40", true),
        part("M28 44 A6 6 0 1 1 16 44 A6 6 0 1 1 28 44 Z"),
        part("M46 40 A6 6 0 1 1 34 40 A6 6 0 1 1 46 40 Z"),
    )),
    Icon("search", color("#4DF5C8"), color("#2D9BFF"), color("#3DA2FF"), color("#8A5CFF"), listOf(
        part("M41 29 A12 12 0 1 1 17 29 A12 12 0 1 1 41 29 Z", true),
        part("M38 38 L48 48", true),
    )),
    Icon("spark", color("#FF7BD8"), color("#4D7BFF"), color("#C05CFF"), color("#3DA2FF"), listOf(
        part("M32 10 C34 26 38 30 54 32 C38 34 34 38 32 54 C30 38 26 34 10 32 C26 30 30 26 32 10 Z"),
    )),
    Icon("squiggle", color("#FFB03A"), color("#4D6BFF"), color("#8A5CFF"), color("#FF8A3D"), listOf(
        part("M13 38 C19 20 26 21 28 34 C30 47 37 46 40 32 C43 23 48 25 51 31", true),
    )),
    Icon("heart", color("#FF8AD0"), color("#8A6BFF"), color("#FF8A3D"), color("#8A5CFF"), listOf(
        part("M32 49 C13 36 13 19 24 19 C28 19 31 22 32 26 C33 22 36 19 40 19 C51 19 51 36 32 49 Z"),
    )),
    Icon("chat", color("#C9B8FF"), color("#6B4DFF"), color("#FF8A3D"), color("#6E7BFF"), listOf(
        part("M14 22 A6 6 0 0 1 20 16 H44 A6 6 0 0 1 50 22 V36 A6 6 0 0 1 44 42 H30 L22 49 V42 H20 A6 6 0 0 1 14 36 Z"),
    )),
)

/** Only the blurred solid tile silhouette is cached; the live layers retain RN's individual alpha. */
private class OrbitArt {
    private val icons = makeIcons()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val rect = RectF()
    private val arrowPath = Path()
    private val centreRimA = color("#FF8A3D")
    private val centreRimB = color("#5B5CFF")
    private val glowColors = (icons.map { it.rimA } + centreRimA).distinct()
    private val glows = glowColors.associateWith { makeGlow(it) }

    private fun makeGlow(ink: Int): Bitmap {
        val scale = 3f
        val pad = 30f
        val bitmap = Bitmap.createBitmap(((TILE + pad * 2) * scale).toInt(), ((TILE + pad * 2) * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.scale(scale, scale)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            color = ink
            // BlurMaskFilter takes a radius, whereas RN's Skia filter takes Gaussian sigma.
            maskFilter = BlurMaskFilter((TILE * .12f - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(pad, pad, pad + TILE, pad + TILE, TILE * .28f, TILE * .28f, glow)
        return bitmap
    }

    private fun solid(ink: Int, alpha: Float = 1f, stroke: Boolean = false, width: Float = 0f) {
        paint.shader = null
        paint.maskFilter = null
        paint.color = ink
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        paint.style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.MITER
    }

    private fun tile(canvas: AndroidCanvas, x: Float, y: Float, s: Float, rimA: Int, rimB: Int, alpha: Float) {
        solid(AndroidColor.WHITE, .35f * alpha)
        val pad = s * 30 / TILE
        rect.set(x - pad, y - pad, x + s + pad, y + s + pad)
        canvas.drawBitmap(requireNotNull(glows[rimA]), null, rect, paint)
        rect.set(x, y, x + s, y + s)
        solid(AndroidColor.WHITE, alpha)
        paint.shader = LinearGradient(x, y, x, y + s, intArrayOf(color("#1A1826"), color("#07070C")), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * .28f, s * .28f, paint)
        solid(AndroidColor.WHITE, alpha * .5f)
        paint.shader = RadialGradient(x + s * .5f, y + s * 1.1f, s * .7f, intArrayOf(rimB, AndroidColor.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * .28f, s * .28f, paint)
        solid(AndroidColor.WHITE, alpha, true, 1.6f)
        paint.shader = LinearGradient(x, y, x + s, y + s, intArrayOf(rimA, 0x26FFFFFF, rimB), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * .28f, s * .28f, paint)
    }

    private fun icon(canvas: AndroidCanvas, index: Int, x: Float, y: Float, s: Float, alpha: Float) {
        val icon = icons[index]
        tile(canvas, x, y, s, icon.rimA, icon.rimB, alpha)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s / 64, s / 64)
        icon.parts.forEachIndexed { i, part ->
            solid(AndroidColor.WHITE, alpha, part.stroke, 5f)
            paint.shader = LinearGradient(12f, 12f, 52f, 52f, icon.a, icon.b, Shader.TileMode.CLAMP)
            if (icon.kind == "eye" && i == 1) solid(color("#0A0A12"), alpha)
            canvas.drawPath(part.path, paint)
        }
        canvas.restore()
    }

    private fun centre(canvas: AndroidCanvas, cx: Float, cy: Float, size: Float, arrow: Float, t: Double) {
        if (size <= .01f) return
        val s = 92 * size
        solid(AndroidColor.WHITE)
        paint.shader = RadialGradient(cx, cy, s * 1.6f, intArrayOf(0xB3463CFF.toInt(), 0x00463CFF), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, s * 1.6f, paint)
        tile(canvas, cx - s / 2, cy - s / 2, s, centreRimA, centreRimB, 1f)
        val k = s / 92
        if (arrow < 1) {
            solid(AndroidColor.WHITE, 1 - arrow)
            repeat(3) { i ->
                val bounce = if (t > 5.1 && t < 6.2) max(0.0, sin((t - 5.1 - i * .12) * PI * 2.8)) * 7 else 0.0
                val x = cx + (i - 1) * 16 * k * (1 - arrow)
                val y = cy + 6 * k - bounce.toFloat() * k - if (i == 1) 18 * arrow * k else 0f
                canvas.drawCircle(x, y, 4.2f * k, paint)
            }
        }
        if (arrow > 0) {
            solid(AndroidColor.WHITE, arrow, true, 5 * k)
            arrowPath.rewind()
            arrowPath.moveTo(cx, cy + 20 * k)
            arrowPath.lineTo(cx, cy - 20 * k)
            arrowPath.moveTo(cx - 16 * k * arrow, cy - 4 * k)
            arrowPath.lineTo(cx, cy - 20 * k)
            arrowPath.lineTo(cx + 16 * k * arrow, cy - 4 * k)
            canvas.drawPath(arrowPath, paint)
        }
    }

    private data class PlacedIcon(val index: Int, val x: Float, val y: Float, val size: Float, val z: Double)

    fun draw(canvas: AndroidCanvas, t: Double, spin: Double, cx: Float, cy: Float) {
        val stage = stageAt(t)
        val gr = (150 + stage.radius * .6).toFloat()
        solid(AndroidColor.WHITE)
        paint.shader = RadialGradient(cx, cy, gr, intArrayOf(0x8C5032FF.toInt(), 0x2E2814A0, AndroidColor.TRANSPARENT), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, gr, paint)
        if (stage.ringAlpha > .01f) {
            val tilt = stage.tilt * PI / 180
            val items = icons.indices.map { i ->
                val angle = spin + i * PI * 2 / icons.size
                val x = stage.radius * cos(angle)
                val y0 = stage.radius * sin(angle)
                val y = y0 * cos(tilt)
                val z = y0 * sin(tilt)
                val scale = FOCAL / (FOCAL - z)
                PlacedIcon(i, cx + (x * scale).toFloat(), cy + (y * scale).toFloat(), (TILE * scale).toFloat(), z)
            }.sortedBy { it.z }
            items.forEach { icon(canvas, it.index, it.x - it.size / 2, it.y - it.size / 2, it.size, stage.ringAlpha) }
        }
        centre(canvas, cx, cy, stage.centre, stage.arrow, t)
    }
}

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Normal, ink: Color = Color.White, spacing: Float = 0f, lineHeight: Int = size) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    color = ink,
    letterSpacing = spacing.sp,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun GlassIconOrbit(
    autoPlay: Boolean = false,
    brand: String = "halo",
    action: String = "Get started",
) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val clock = remember { OrbitClock().apply { if (reduced) time = 7.0 } }
    val art = remember { OrbitArt() }
    LaunchedEffect(active, reduced, autoPlay) {
        if (reduced) {
            clock.time = 7.0
            clock.spin = 0.0
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                clock.advance((now - previous) / 1e9, autoPlay)
                previous = now
            }
        }
    }
    val density = LocalDensity.current.density
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val cy = maxHeight * .45f
        Box(
            Modifier.fillMaxSize().pointerInput(reduced, density) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var previous = down.position
                    var started = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!started && (change.position - down.position).getDistance() >= 4 * density) {
                                started = true
                                clock.touching = true
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            if (started) {
                                change.consume()
                                clock.extra += (change.position.x - previous.x) / density / 120
                                previous = change.position
                            }
                        }
                    } finally {
                        // Gesture.Pan.onFinalize also runs for a gesture that failed its minimum distance.
                        clock.touching = false
                        clock.released = clock.elapsed
                    }
                }
            },
        ) {
            Canvas(
                Modifier.fillMaxSize().semantics { contentDescription = "A ring of app icons. Drag to spin it." },
            ) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.scale(density, density)
                    art.draw(canvas.nativeCanvas, clock.time, clock.spin, size.width / density / 2, size.height / density * .45f)
                    canvas.nativeCanvas.restore()
                }
            }
            Box(Modifier.padding(top = cy - 24.dp).fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text("Any idea", Modifier.graphicsLayer { alpha = (seg(clock.time, 1.6, 2.2) * (1 - seg(clock.time, 3.1, 3.4))).toFloat() }, style = textStyle(38, FontWeight.SemiBold, spacing = -.8f))
                Text("Any need", Modifier.graphicsLayer { alpha = (seg(clock.time, 3.3, 3.6) * (1 - seg(clock.time, 4.2, 4.6))).toFloat() }, style = textStyle(38, FontWeight.SemiBold, spacing = -.8f))
            }
            Row(
                Modifier.padding(start = 24.dp, end = 24.dp, top = safe.calculateTopPadding() + 8.dp).fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(brand, style = textStyle(20, FontWeight.ExtraBold, spacing = -.5f))
                Text("Skip", style = textStyle(15, FontWeight.SemiBold, Color.White.copy(alpha = .55f)))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = safe.calculateBottomPadding() + 14.dp).fillMaxWidth()) {
            Text("One assistant for all of it", style = textStyle(28, FontWeight.ExtraBold, spacing = -.8f, lineHeight = 32))
            Text("Photos, notes, songs and chats — ask Halo and it finds, writes or plans.", Modifier.padding(top = 8.dp), style = textStyle(16, ink = Color.White.copy(alpha = .6f), lineHeight = 22))
            Box(
                Modifier.padding(top = 20.dp).fillMaxWidth().height(58.dp).background(Color.White, RoundedCornerShape(29.dp)).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = textStyle(17, FontWeight.Bold, Color.Black))
            }
        }
    }
}

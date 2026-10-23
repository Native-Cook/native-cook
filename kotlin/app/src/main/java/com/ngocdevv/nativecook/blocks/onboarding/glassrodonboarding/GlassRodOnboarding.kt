package com.ngocdevv.nativecook.blocks.onboarding.glassrodonboarding

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class Page(val photo: String, val label: String, val title: List<String>, val body: List<String>, val tone: Int)

// The photos fill the screen, about 400 dp wide; 1200 px covers that at 3x.
private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1200&q=80"

private val Pages = listOf(
    Page(
        photo("photo-1511860810434-a92f84c6f01e"), "DUNES · 01",
        listOf("Read the", "wind."), listOf("Live dune maps that shift with the", "sand, redrawn every dawn."), 0xB8643A,
    ),
    Page(
        photo("photo-1505521377774-103a8cc2f735"), "CANYONS · 02",
        listOf("Walk into", "the light."), listOf("Slot canyons rated by the hour the", "sun reaches the floor."), 0x8C3F22,
    ),
    Page(
        photo("photo-1629659343117-237c364e69ed"), "NIGHT · 03",
        listOf("Sleep under", "the stars."), listOf("Camps far from any road, with the", "moon and wind for tonight."), 0x1D2A44,
    ),
)

private const val INK = 0x0D0907
private const val CREAM = 0xF6EFE6
private val Ink = Color(0xFF0D0907)
private val Cream = Color(0xFFF6EFE6)
private val Sand = Color(0xFFE9B98F)

/**
 * The glass: everything under the rod is seen through a cylinder. Across it the image is flipped and
 * squeezed (the centre shows a strip 0.55 times as wide, the edges reach 1.45 radii to the other
 * side), red and blue split a little, the edges darken as the glass turns away, and two highlights
 * run down it. Outside the rod, a soft shadow falls on the page to its right. Coordinates arrive in
 * pixels; `pd` turns them into dp. The same shader as the React Native block's.
 */
private const val ROD_AGSL = """
uniform shader content;
uniform float pd;
uniform float2 size;
uniform float2 center;
uniform float angle;
uniform float radius;
uniform float chroma;
uniform float shade;

half4 main(float2 xy) {
  float2 p = xy / pd;
  float2 n = float2(cos(angle), sin(angle));
  float2 t = float2(-n.y, n.x);
  float u = dot(p - center, n);
  float s = u / radius;
  if (abs(s) >= 1.0) {
    half4 c = content.eval(xy);
    float shadow = s > 1.0 ? (1.0 - smoothstep(1.0, 1.9, s)) * 0.3 * shade : 0.0;
    return half4(c.rgb * (1.0 - shadow), c.a);
  }
  float z = sqrt(max(0.0, 1.0 - s * s));
  float bend = -s * (0.55 + 0.9 * (1.0 - z));
  float along = dot(p - center, t);
  float2 base = center + t * (along * (0.97 + 0.03 * z));
  float2 lo = float2(0.5);
  float2 hi = size - 0.5;
  float2 pr = clamp(base + n * radius * bend * (1.0 + chroma), lo, hi);
  float2 pg = clamp(base + n * radius * bend, lo, hi);
  float2 pb = clamp(base + n * radius * bend * (1.0 - chroma), lo, hi);
  float3 col = float3(content.eval(pr * pd).r, content.eval(pg * pd).g, content.eval(pb * pd).b);
  col = col * (0.9 + 0.1 * z) * float3(1.0, 0.985, 0.965);
  float fres = pow(1.0 - z, 2.4);
  col = mix(col, float3(0.05, 0.04, 0.035), fres * 0.6);
  float down = clamp(along / size.y + 0.5, 0.0, 1.0);
  float spec = exp(-pow((s + 0.5) / 0.1, 2.0)) * (0.5 - 0.2 * down) + exp(-pow((s - 0.8) / 0.045, 2.0)) * 0.2;
  float rim = smoothstep(0.9, 1.0, abs(s)) * 0.28;
  col += float3(spec + rim);
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/** Where everything sits, in dp, from the bottom up: the button, the dots, the copy. */
private class Layout(val width: Float, val height: Float, val top: Float, bottom: Float) {
    val margin = 28f
    val buttonTop = height - max(bottom, 16f) - 16f - 56f
    val dotsY = buttonTop - 30f
    private val body1 = dotsY - 36f
    private val body0 = body1 - 23f
    val titleSize = min(46f, (width - margin * 2) / 7.2f)
    private val title1 = body0 - 38f
    private val title0 = title1 - titleSize * 1.04f
    val label = title0 - titleSize - 12f
    val title = floatArrayOf(title0, title1)
    val body = floatArrayOf(body0, body1)
}

/** The photos as they arrive; read by the drawing. */
private class Art {
    var version by mutableIntStateOf(0)
    val bitmaps = arrayOfNulls<Bitmap>(Pages.size)
}

/** Paints and paths, made once; shapes and text are drawn in dp on a canvas scaled to it. */
private class Paints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val matrix = Matrix()
    val cut = Path()
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(700)
        textSize = 12f
        letterSpacing = 2.4f / 12f
        color = 0xFFE9B98F.toInt()
    }
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(700)
        color = 0xFF000000.toInt() or CREAM
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = sans(500)
        textSize = 16f
        color = (0xC4 shl 24) or CREAM
    }
    var lowShader: Shader? = null
    var topShader: Shader? = null
    var shaderFor = 0f
    // The glass under API 33, drawn over the pages without bending them.
    val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    // Across the rod, in its own coordinates: dark edges, the two highlights and the lit rim.
    val flatBody: Shader = LinearGradient(
        -ROD_RADIUS, 0f, ROD_RADIUS, 0f,
        intArrayOf(
            0x99FFFFFF.toInt(), 0x80000000.toInt(), 0x18000000, 0x80FFFFFF.toInt(), 0x10FFFFFF,
            0x0A000000, 0x18000000, 0x40FFFFFF, 0x50000000, 0x99FFFFFF.toInt(),
        ),
        floatArrayOf(0f, 0.08f, 0.2f, 0.25f, 0.3f, 0.5f, 0.86f, 0.9f, 0.94f, 1f),
        Shader.TileMode.CLAMP,
    )
    val flatShadow: Shader = LinearGradient(ROD_RADIUS, 0f, ROD_RADIUS * 1.9f, 0f, 0x4D000000, 0x00000000, Shader.TileMode.CLAMP)
    val runtime: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(ROD_AGSL) else null
}

private fun sans(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        if (weight >= 600) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

/**
 * An onboarding whose pages change under a glass rod. The rod lies across the screen: drag it and it
 * rolls over the page, showing the next one behind it and the seam between the two flipped and
 * squeezed in its glass, with colour fringes, highlights and a soft shadow. It follows the finger,
 * leans as it moves and tilts as the finger rises or falls; let go and it springs on to the next
 * page or back with the finger's speed. The page dots stretch with the paging, and the button reads
 * "Get started" on the last page.
 *
 * @param autoPlay Pages through the onboarding with the glass rod, presses the button and starts over.
 * @param onDone Called when "Get started" is pressed on the last page.
 */
@Composable
fun GlassRodOnboarding(modifier: Modifier = Modifier, autoPlay: Boolean = false, onDone: () -> Unit = {}) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val pager = remember { Pager(Pages.size) }
    val art = remember { Art() }
    val paints = remember { Paints() }
    // Bumped once a frame, so the picture and the controls are drawn again.
    var tick by remember { mutableIntStateOf(0) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    SideEffect {
        pager.reduced = reduced
        pager.feel = object : Feel {
            override fun changed() {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            override fun finished() {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onDone()
            }
        }
    }

    LaunchedEffect(pager, autoPlay) {
        pager.looping = autoPlay
        pager.clock = 0f
        pager.hold = 0f
    }

    LaunchedEffect(pager) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) pager.frame(dt)
                tick += 1
            }
        }
    }

    LaunchedEffect(art, context) {
        val loader = SingletonImageLoader.get(context)
        Pages.forEachIndexed { i, page ->
            launch {
                val request = ImageRequest.Builder(context).data(page.photo).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                art.bitmaps[i] = result.image.toBitmap()
                art.version += 1
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Ink)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val layout = remember(width, height, safeTop, safeBottom) { Layout(width, height, safeTop, safeBottom) }
        SideEffect { pager.resize(width, height) }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Onboarding pages. Swipe left or right to change page." }
                .graphicsLayer {
                    tick
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !pager.reduced) {
                        renderEffect = rodEffect(paints, pager, layout, density.density)
                    } else {
                        renderEffect = null
                    }
                }
                .pointerInput(pager) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.density
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        pager.hold = USER_HOLD
                        // One recogniser stands in for the RN pan (after 10 dp sideways, failing after
                        // 24 dp up or down first) and its tap.
                        var state = 0 // 0 pending, 1 panning, 2 failed
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val tx = (change.position.x - start.x) / d
                            val ty = (change.position.y - start.y) / d
                            if (!change.pressed) {
                                released = true
                                if (state == 1) {
                                    pager.panEnd(tracker.calculateVelocity().x / d)
                                } else if (state == 0 && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    // Taps on the photo: the right 40% goes on, the left 25% goes back.
                                    val x = change.position.x / d
                                    val y = change.position.y / d
                                    if (y <= layout.dotsY - 20f) {
                                        if (x > layout.width * 0.6f) pager.step(1) else if (x < layout.width * 0.25f) pager.step(-1)
                                    }
                                }
                                pager.finalize()
                                continue
                            }
                            if (state == 0) {
                                if (abs(ty) > FAIL_OFFSET) state = 2
                                else if (abs(tx) > ACTIVE_OFFSET) state = if (pager.panStart(change.position.y / d)) 1 else 2
                            }
                            if (state == 1) {
                                change.consume()
                                pager.panUpdate(tx, ty)
                            }
                        }
                        // A finger lost to the system still lets the page settle.
                        if (!released) {
                            if (state == 1) pager.panEnd(0f)
                            pager.finalize()
                        }
                    }
                },
        ) {
            tick
            art.version
            drawIntoCanvas { drawPages(it.nativeCanvas, pager, layout, art, paints, density.density) }
        }

        Controls(pager, layout, tick)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun rodEffect(paints: Paints, pager: Pager, layout: Layout, density: Float): androidx.compose.ui.graphics.RenderEffect {
    val shader = paints.runtime as RuntimeShader
    shader.setFloatUniform("pd", density)
    shader.setFloatUniform("size", layout.width, layout.height)
    shader.setFloatUniform("center", pager.rodX, pager.pivotY)
    shader.setFloatUniform("angle", pager.angle)
    shader.setFloatUniform("radius", ROD_RADIUS)
    shader.setFloatUniform("chroma", pager.chroma)
    shader.setFloatUniform("shade", 1f)
    return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/** The half-plane on one side of the rod's centre line, in dp. */
private fun cut(path: Path, pager: Pager, left: Boolean, layout: Layout) {
    val a = pager.angle
    val nx = cos(a)
    val ny = sin(a)
    val far = (layout.width + layout.height) * 3f
    val cx = pager.rodX
    val cy = pager.pivotY
    val side = if (left) -1f else 1f
    path.rewind()
    path.moveTo(cx + ny * far, cy - nx * far)
    path.lineTo(cx - ny * far, cy + nx * far)
    path.lineTo(cx - ny * far + side * nx * far, cy + nx * far + side * ny * far)
    path.lineTo(cx + ny * far + side * nx * far, cy - nx * far + side * ny * far)
    path.close()
}

private fun drawPages(canvas: android.graphics.Canvas, pager: Pager, layout: Layout, art: Art, paints: Paints, density: Float) {
    canvas.save()
    canvas.scale(density, density)
    paints.fill.shader = null
    paints.fill.color = 0xFF000000.toInt() or INK
    canvas.drawRect(0f, 0f, layout.width, layout.height, paints.fill)
    if (paints.shaderFor != layout.height) {
        paints.shaderFor = layout.height
        paints.lowShader = LinearGradient(
            0f, layout.height * 0.42f, 0f, layout.height,
            intArrayOf(INK, (0xB8 shl 24) or INK, (0xF2 shl 24) or INK), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        paints.topShader = LinearGradient(0f, 0f, 0f, 150f, (0x66 shl 24) or INK, INK, Shader.TileMode.CLAMP)
    }
    for (i in Pages.indices) drawPage(canvas, i, pager, layout, art, paints)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) drawFlatGlass(canvas, pager, layout, paints)
    canvas.restore()
}

/**
 * One page: its photo, filling the screen, and its words. It shows on the rod's left when it is the
 * page being left, on its right when it is the page coming in, and nowhere otherwise.
 */
private fun drawPage(canvas: android.graphics.Canvas, index: Int, pager: Pager, layout: Layout, art: Art, paints: Paints) {
    val seg = segment(pager.progress.value, pager.wrap, Pages.size)
    val side = when {
        index == seg.a && index == seg.b -> 2
        index == seg.a -> -1
        index == seg.b -> 1
        else -> return
    }
    val page = Pages[index]
    val w = layout.width
    val h = layout.height
    val reduced = pager.reduced
    // The page being left drifts left behind the rod, the one coming in drifts in from the right and
    // settles from a little closer.
    val travel = if (reduced || side == 2) 0f else if (side < 0) -seg.f else 1f - seg.f
    // Under reduced motion the pages crossfade instead: the incoming one fades in over the other, or,
    // when it is drawn underneath (the wrap round to the first page), the outgoing one fades out.
    val alpha = when {
        !reduced || side == 2 -> 1f
        side == 1 -> if (seg.b > seg.a) seg.f else 1f
        else -> if (seg.b > seg.a) 1f else 1f - seg.f
    }
    canvas.save()
    if (!reduced && side != 2) {
        cut(paints.cut, pager, side < 0, layout)
        canvas.clipPath(paints.cut)
    }
    if (alpha < 1f) canvas.saveLayerAlpha(0f, 0f, w, h, (alpha * 255f).toInt())

    canvas.save()
    val zoom = if (travel > 0f) 1f + (INCOMING_SCALE - 1f) * travel else 1f
    canvas.translate(w / 2f + travel * PHOTO_SHIFT * w, h / 2f)
    canvas.scale(zoom, zoom)
    canvas.translate(-w / 2f, -h / 2f)
    paints.fill.shader = null
    paints.fill.color = 0xFF000000.toInt() or page.tone
    canvas.drawRect(0f, 0f, w, h, paints.fill)
    art.bitmaps[index]?.let { bitmap ->
        if (bitmap.width > 0 && bitmap.height > 0) {
            val scale = max(w / bitmap.width, h / bitmap.height)
            paints.matrix.reset()
            paints.matrix.setScale(scale, scale)
            paints.matrix.postTranslate((w - bitmap.width * scale) / 2f, (h - bitmap.height * scale) / 2f)
            canvas.drawBitmap(bitmap, paints.matrix, paints.image)
        }
    }
    canvas.restore()

    // The scrim belongs to the page, so the words keep their contrast through the glass.
    paints.fill.shader = paints.lowShader
    canvas.drawRect(0f, h * 0.42f, w, h, paints.fill)
    paints.fill.shader = paints.topShader
    canvas.drawRect(0f, 0f, w, 150f, paints.fill)
    paints.fill.shader = null

    canvas.save()
    canvas.translate(travel * TEXT_SHIFT * w, 0f)
    val m = layout.margin
    canvas.drawText(page.label, m, layout.label, paints.label)
    paints.title.textSize = layout.titleSize
    canvas.drawText(page.title[0], m - 2f, layout.title[0], paints.title)
    canvas.drawText(page.title[1], m - 2f, layout.title[1], paints.title)
    canvas.drawText(page.body[0], m, layout.body[0], paints.body)
    canvas.drawText(page.body[1], m, layout.body[1], paints.body)
    canvas.restore()

    if (alpha < 1f) canvas.restore()
    canvas.restore()
}

/**
 * Below API 33 there is no shader to bend the pages, so the rod is drawn as a plain sheet of glass:
 * darker edges, the two highlights and the lit rim over the seam, and its shadow to the right.
 */
private fun drawFlatGlass(canvas: android.graphics.Canvas, pager: Pager, layout: Layout, paints: Paints) {
    if (pager.reduced) return
    val r = ROD_RADIUS
    canvas.save()
    canvas.translate(pager.rodX, pager.pivotY)
    canvas.rotate(Math.toDegrees(pager.angle.toDouble()).toFloat())
    val span = layout.height * 2f
    paints.glass.shader = paints.flatBody
    canvas.drawRect(-r, -span, r, span, paints.glass)
    paints.glass.shader = paints.flatShadow
    canvas.drawRect(r, -span, r * 1.9f, span, paints.glass)
    paints.glass.shader = null
    canvas.restore()
}

@Composable
private fun Controls(pager: Pager, layout: Layout, tick: Int) {
    // Read so the controls follow the paging every frame.
    tick
    val count = Pages.size
    val p = pager.progress.value
    val weights = FloatArray(count) { smooth(1f - ringDistance(p, it, count)) }
    val last = weights[count - 1]

    // Header: the mark and name, and Skip.
    Row(
        Modifier
            .offset(x = layout.margin.dp, y = (layout.top + 10f).dp)
            .width((layout.width - layout.margin * 2 + 10f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark()
        Spacer(Modifier.width(8.dp))
        Text("Sandline", color = Cream, style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp))
        Spacer(Modifier.weight(1f))
        Text(
            "Skip",
            color = Cream.copy(alpha = 0.85f * (1f - last)),
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            modifier = Modifier
                .clickable(enabled = last < 0.5f, interactionSource = remember { MutableInteractionSource() }, indication = null) { pager.skip() }
                // Faded out on the last page, so TalkBack passes it by there too.
                .then(if (last >= 0.5f) Modifier.clearAndSetSemantics {} else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    // Dots: every dot before this one may be stretched too, so its x is the sum of their widths.
    var x = 0f
    for (i in 0 until count) {
        val w = DOT + (DOT_ACTIVE - DOT) * weights[i]
        Box(
            Modifier
                .offset(x = (layout.margin + x).dp, y = (layout.dotsY - DOT / 2f).dp)
                .size(width = w.dp, height = DOT.dp)
                .graphicsLayer { alpha = 0.38f + 0.62f * weights[i] }
                .background(Cream, RoundedCornerShape(50)),
        )
        x += w + DOT_GAP
    }

    // The button: Continue, or Get started on the last page.
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressing by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 110 else 220), label = "press")
    Box(
        Modifier
            .offset(x = layout.margin.dp, y = layout.buttonTop.dp)
            .size(width = (layout.width - layout.margin * 2).dp, height = 56.dp)
            .graphicsLayer {
                val s = 1f - 0.035f * max(pressing, pager.pressValue)
                scaleX = s
                scaleY = s
            }
            .background(Cream, RoundedCornerShape(28.dp))
            .clickable(interactionSource = source, indication = null) { pager.buttonTapped() },
        contentAlignment = Alignment.Center,
    ) {
        val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Row(
            // The labels hand over without overlapping: "Continue" leaves in the first half, "Get
            // started" arrives in the second.
            Modifier
                .graphicsLayer {
                    alpha = 1f - smooth(last * 2f)
                    translationY = -8f * last * density
                }
                // Only the label on show is read out with the button.
                .then(if (last > 0.5f) Modifier.clearAndSetSemantics {} else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Continue", color = Ink, style = label)
            Spacer(Modifier.width(8.dp))
            Arrow()
        }
        Text(
            "Get started",
            color = Ink,
            style = label,
            modifier = Modifier
                .graphicsLayer {
                    alpha = smooth(last * 2f - 1f)
                    translationY = 8f * (1f - last) * density
                }
                .then(if (last <= 0.5f) Modifier.clearAndSetSemantics {} else Modifier),
        )
    }
}

/** The app's mark: two dune ridges. */
@Composable
private fun Mark() {
    Canvas(Modifier.size(22.dp)) {
        val k = size.width / 22f
        val back = androidx.compose.ui.graphics.Path().apply {
            moveTo(2f * k, 15.5f * k)
            cubicTo(5.2f * k, 10.1f * k, 8.6f * k, 7.5f * k, 12f * k, 7.5f * k)
            cubicTo(15.1f * k, 7.5f * k, 17.7f * k, 9.5f * k, 20f * k, 13.5f * k)
        }
        val front = androidx.compose.ui.graphics.Path().apply {
            moveTo(5.5f * k, 18.5f * k)
            cubicTo(7.9f * k, 15.9f * k, 10.3f * k, 14.7f * k, 12.7f * k, 14.7f * k)
            cubicTo(14.6f * k, 14.7f * k, 16.3f * k, 15.4f * k, 17.8f * k, 16.7f * k)
        }
        drawPath(back, Cream, style = Stroke(width = 2.2f * k, cap = StrokeCap.Round))
        drawPath(front, Sand, style = Stroke(width = 2.2f * k, cap = StrokeCap.Round))
    }
}

/** An arrow pointing right, in an 18 dp box. */
@Composable
private fun Arrow() {
    Canvas(Modifier.size(18.dp)) {
        val k = size.width / 18f
        val stroke = Stroke(width = 2.1f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(3f * k, 9f * k)
            lineTo(14.2f * k, 9f * k)
            moveTo(9.6f * k, 4.2f * k)
            lineTo(14.4f * k, 9f * k)
            lineTo(9.6f * k, 13.8f * k)
        }
        drawPath(path, Ink, style = stroke)
    }
}

@Preview
@Composable
private fun GlassRodOnboardingPreview() {
    GlassRodOnboarding(autoPlay = true)
}

package com.ngocdevv.nativecook.blocks.media.watercolorvoiceorbs

import android.annotation.TargetApi
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import com.ngocdevv.nativecook.blocks.media.watercolorvoiceorbs.OrbsModel as M

/*
 Watercolor Voice Orbs — a voice picker whose orb is a living watercolour that repaints itself for
 each voice. A port of the SwiftUI block (WatercolorVoiceOrbs.swift); the numbers are the same.

 Look: a black screen. "Choose a voice" (24 sp semibold, white) centred at 17 % of the height; the
 orb (radius 25 % of the width) centred at 43 %, with a soft glow of its mid colour behind it
 (radius 1.25 R, blur 34, 35 %). Under it the voice's name (18 sp semibold), its line (14 sp, white
 50 %) and a pager of four 6 dp dots (14 dp apart, the current one white, stretching between dots).
 A white "Continue" pill (52 dp tall, 24 dp from the sides) at the bottom.
 The orb is an AGSL RuntimeShader on Android 13+ (the same code as WatercolorVoiceOrbs.metal):
 domain-warped value noise (two fbm warps of five octaves) painted deep → mid → light, with a bright
 side facing each voice's direction, a darker rim and a soft light upper left. The noise drifts on
 circles of the loop's phase, so the texture flows and comes back to itself every loop. Voices:
 Lagoon (blue, bright at the top), Dune (terracotta, bright lower right), Moss (green, bright at the
 left), Dusk (violet, bright below); each also sits elsewhere in the noise and twists its middle by
 its own amount, and between two voices every parameter is blended, so a swipe makes the clouds flow
 and turn into the next ones while the next palette seeps in through them. Below Android 13 the orb
 is a radial gradient of the palette with three soft light clouds that drift with the loop.

 autoPlay timeline (LOOP = 10 s); v is the voice position, 0…3:
   0.0 – 1.4   Lagoon at rest; the orb breathes 1 → 1.02 (period 2.5 s, four per loop).
   1.4, 3.6, 5.8   a swipe to the next voice: v eases over 0.85 s (cubic in-out), the orb squashes
               1 − 0.11·e^(−2.4u)·sin(7.5u) (to ~0.94, then ~1.02), the names slide 24 dp per
               voice and cross-fade, the pager dot travels and stretches.
   +0.85       the voice lands and speaks a 1.1 s preview: the orb swells up to 3 % and its glow
               brightens with four syllables.
   7.8 – 8.8   a glide back through Moss and Dune to Lagoon (1.0 s), which speaks again.
   9.9 – 10.0  rest; the frame at 10.0 is the one at 0.0.

 Touch: drag sideways to move between voices continuously (a voice per 55 % of the width, a
 rubber band past the ends, a light tick at each voice); release settles on the nearest voice (or
 the next one in the direction of a quick flick) in 0.45 s, squashes and speaks. A tap makes the orb
 give and speak. 2 s after the last touch the picker glides back to Lagoon (0.9 s) and the loop
 starts again from its rest.
 Reduced motion: no flow, breathing, squash or speech; the picker shows Lagoon, and a drag or a tap
 changes voice with a 0.25 s cross-fade.
 */

private fun nowSeconds() = System.nanoTime() / 1e9

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private class OrbsShown(val v: Double, val scale: Double, val glow: Double, val tau: Double)

private class OrbsController(val autoPlay: Boolean, val reduced: Boolean) {
    var appear = nowSeconds()
    /** autoPlay drives everything while it is on. */
    var autoOn by mutableStateOf(false)
    /** Hand-made state: v glides from → to (from, to, at, duration); the last squash and the last landing. */
    var glide by mutableStateOf(doubleArrayOf(0.0, 0.0, -1e9, 0.0))
    var squashAt by mutableDoubleStateOf(-1e9)
    var landedAt by mutableDoubleStateOf(-1e9)
    /** A drag: the v it started from and the v now. */
    var drag by mutableStateOf<DoubleArray?>(null)
    var lastIndex = 0
    var frame by mutableDoubleStateOf(nowSeconds())
    var resume: Job? = null

    fun shown(now: Double): OrbsShown {
        val clock = now - appear
        val e = clock - floor(clock / M.LOOP) * M.LOOP
        val tau = 2 * PI * e / M.LOOP
        if (reduced) {
            val g = glide
            return OrbsShown(g[0] + (g[1] - g[0]) * M.clamp((now - g[2]) / 0.25), 1.0, 0.35, 0.0)
        }
        val breath = M.breathing(clock)
        if (autoOn) {
            val a = M.auto(e)
            val speak = M.speak(a.sinceLanding)
            return OrbsShown(a.v, breath * M.squash(a.sinceSwipe) + 0.03 * speak, 0.35 + 0.25 * speak, tau)
        }
        val speak = M.speak(now - landedAt)
        return OrbsShown(manualV(now), breath * M.squash(now - squashAt) + 0.03 * speak, 0.35 + 0.25 * speak, tau)
    }

    fun manualV(now: Double): Double {
        drag?.let { return it[1] }
        val g = glide
        val u = if (g[3] > 0) (now - g[2]) / g[3] else 1.0
        return g[0] + (g[1] - g[0]) * (if (g[3] > 0.6) M.easeInOut(u) else M.easeOut(u))
    }

    /** Freezes what autoPlay shows into the hand-made state. */
    fun takeOver(now: Double) {
        resume?.cancel()
        if (!autoOn) return
        val clock = now - appear
        val a = M.auto(clock - floor(clock / M.LOOP) * M.LOOP)
        glide = doubleArrayOf(a.v, a.v, now, 0.0)
        squashAt = now - a.sinceSwipe
        landedAt = now - a.sinceLanding
        autoOn = false
    }
}

@Composable
fun WatercolorVoiceOrbs(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val c = remember(reduced) { OrbsController(autoPlay, reduced) }
    val painter = remember { OrbsPainter() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) {
        c.appear = nowSeconds()
        c.autoOn = autoPlay && !reduced
        if (reduced) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        if (!autoPlay || reduced) return
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            val v = c.manualV(now)
            val moving = abs(v) > 0.001
            if (moving) {
                c.glide = doubleArrayOf(v, 0.0, now, 0.9)
                c.squashAt = now
                c.landedAt = now + 0.9
            }
            // Rejoin the loop inside its rest (loop time 9.9 … 1.4), after the glide and the speech.
            val ready = now + if (moving) 0.9 + M.SPEAK_LENGTH else 0.0
            val er = (ready - c.appear) % M.LOOP
            val wait = if (er >= 9.9 || er < 1.4) 0.0 else 9.9 - er
            delay(((ready - now + wait) * 1000).toLong())
            c.autoOn = true
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val density = LocalDensity.current.density
        val radius = 0.25 * w
        val cy = h * 0.43
        val nameY = cy + radius + 34
        val pagerY = nameY + 62
        val voices = M.voices.size
        val top = (voices - 1).toDouble()

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Voice " + M.voices[M.clamp(c.shown(c.frame).v, 0.0, top).roundToInt()].name }
                .pointerInput(c, w) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val now = nowSeconds()
                        c.takeOver(now)
                        val v0 = c.manualV(now)
                        c.drag = doubleArrayOf(v0, v0)
                        c.lastIndex = v0.roundToInt()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var dx = 0f
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            dx = (change.position.x - down.position.x) / density
                            if (abs(dx) > 4) moved = true
                            if (!moved) continue
                            change.consume()
                            var v = v0 - dx / (0.55 * w)
                            if (v < 0) v *= 0.3 else if (v > top) v = top + (v - top) * 0.3
                            c.drag = doubleArrayOf(v0, v)
                            val i = M.clamp(v, 0.0, top).roundToInt()
                            if (i != c.lastIndex) {
                                c.lastIndex = i
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        val end = nowSeconds()
                        val current = c.drag?.get(1) ?: v0
                        c.drag = null
                        var target = Math.round(current).toDouble()
                        if (moved) {
                            // A flick: the finger's speed carried for 0.2 s goes past 60 dp.
                            val flick = tracker.calculateVelocity().x / density * 0.2
                            if (abs(flick) > 60) target = if (flick < 0) floor(current) + 1 else ceil(current) - 1
                        }
                        target = M.clamp(target, 0.0, top)
                        c.glide = doubleArrayOf(current, target, end, if (reduced) 0.25 else 0.45)
                        if (!reduced) {
                            c.squashAt = end
                            c.landedAt = end + if (moved) 0.45 else 0.1
                        }
                        view.performHapticFeedback(if (moved) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.VIRTUAL_KEY)
                        scheduleResume()
                    }
                },
        ) {
            painter.draw(this, c.shown(c.frame), w / 2, cy, radius, pagerY, voices, density)
        }

        BasicText(
            "Choose a voice",
            Modifier.fillMaxWidth().offset(y = (h * 0.17 - 15).dp),
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        M.voices.forEachIndexed { i, voice ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = nameY.dp)
                    .graphicsLayer {
                        val d = i - c.shown(c.frame).v
                        alpha = (1 - M.smoothstep(0.0, 0.55, abs(d))).toFloat()
                        translationX = (24 * d * density).toFloat()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                BasicText(voice.name, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White), maxLines = 1)
                Spacer(Modifier.height(5.dp))
                BasicText(voice.line, style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f)), maxLines = 1)
            }
        }
        Box(
            Modifier
                .offset(x = 24.dp, y = (h - max(bottom.toDouble(), 16.0) - 12 - 52).dp)
                .width((w - 48).dp)
                .height(52.dp)
                .background(Color.White, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Continue", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black), maxLines = 1)
        }
    }
}

/** The glow, the orb (shader or fallback) and the pager. */
private class OrbsPainter {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shader = if (Build.VERSION.SDK_INT >= 33) OrbsShader() else null
    private var glowSigma = -1f

    fun draw(scope: DrawScope, s: OrbsShown, cxDp: Double, cyDp: Double, radiusDp: Double, pagerY: Double, n: Int, d: Float) {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val b = M.blend(s.v)
        val cx = (cxDp * d).toFloat()
        val cy = (cyDp * d).toFloat()
        // The glow of the voice's mid colour.
        if (glowSigma != 34 * d) {
            glowSigma = 34 * d
            glow.maskFilter = BlurMaskFilter((glowSigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        glow.color = Color(b.mid[0], b.mid[1], b.mid[2], s.glow.toFloat().coerceIn(0f, 1f)).toArgbInt()
        canvas.drawCircle(cx, cy, (radiusDp * 1.25 * s.scale * d).toFloat(), glow)

        val r = (radiusDp * s.scale * d).toFloat()
        if (shader != null) {
            shader.update(cx, cy, r, s.tau.toFloat(), b)
            scope.drawCircle(ShaderBrush(shader.shader), radius = r + d, center = Offset(cx, cy))
        } else {
            fallback(canvas, cx, cy, r, s.tau, b)
        }

        // Pager: the current dot travels and stretches between dots.
        val x0 = cxDp - (n - 1) * 7
        val y = (pagerY * d).toFloat()
        fill.shader = null
        fill.color = Color.White.copy(alpha = 0.28f).toArgbInt()
        for (i in 0 until n) canvas.drawCircle(((x0 + i * 14) * d).toFloat(), y + 3 * d, 3 * d, fill)
        val v = M.clamp(s.v, -0.3, n - 1 + 0.3)
        val f = v - floor(v)
        val stretch = 10 * (1 - abs(2 * f - 1))
        val px = x0 + v * 14
        fill.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(
            ((px - 3 - stretch / 2) * d).toFloat(), y, ((px + 3 + stretch / 2) * d).toFloat(), y + 6 * d, 3 * d, 3 * d, fill,
        )
    }

    /** Below Android 13: a radial gradient of the palette with three soft light clouds drifting with the loop. */
    private fun fallback(canvas: android.graphics.Canvas, cx: Float, cy: Float, r: Float, tau: Double, b: M.Blend) {
        fun mix(p: FloatArray, q: FloatArray) = FloatArray(3) { p[it] + (q[it] - p[it]) * b.f }
        val deep = mix(b.a.deep, b.b.deep)
        val light = mix(b.a.light, b.b.light)
        val bx = cx + b.band[0] * r * 0.45f
        val by = cy + b.band[1] * r * 0.45f
        fill.shader = RadialGradient(
            bx, by, r * 1.6f,
            intArrayOf(Color(light[0], light[1], light[2]).toArgbInt(), Color(b.mid[0], b.mid[1], b.mid[2]).toArgbInt(), Color(deep[0], deep[1], deep[2]).toArgbInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
        canvas.save()
        val clip = android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        for (k in 0 until 3) {
            val a = tau + k * 2.1 + b.swirl
            val px = cx + (0.45f * r * cos(a)).toFloat()
            val py = cy + (0.45f * r * sin(a + k)).toFloat()
            fill.shader = RadialGradient(
                px, py, r * 0.7f,
                Color(light[0], light[1], light[2], 0.55f).toArgbInt(), Color(light[0], light[1], light[2], 0f).toArgbInt(), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(px, py, r * 0.7f, fill)
        }
        fill.shader = null
        canvas.restore()
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(), (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(),
)

/** The orb's AGSL shader: the same code as WatercolorVoiceOrbs.metal. */
@TargetApi(33)
private class OrbsShader {
    val shader = RuntimeShader(SOURCE)

    fun update(cx: Float, cy: Float, radius: Float, tau: Float, b: M.Blend) {
        shader.setFloatUniform("uCenter", cx, cy)
        shader.setFloatUniform("uRadius", radius)
        shader.setFloatUniform("uTau", tau)
        shader.setFloatUniform("uDeepA", b.a.deep)
        shader.setFloatUniform("uMidA", b.a.mid)
        shader.setFloatUniform("uLightA", b.a.light)
        shader.setFloatUniform("uDeepB", b.b.deep)
        shader.setFloatUniform("uMidB", b.b.mid)
        shader.setFloatUniform("uLightB", b.b.light)
        shader.setFloatUniform("uMix", b.f)
        shader.setFloatUniform("uBand", b.band)
        shader.setFloatUniform("uOffset", b.offset)
        shader.setFloatUniform("uSwirl", b.swirl)
    }

    companion object {
        private const val SOURCE = """
uniform float2 uCenter;
uniform float uRadius;
uniform float uTau;
uniform float3 uDeepA;
uniform float3 uMidA;
uniform float3 uLightA;
uniform float3 uDeepB;
uniform float3 uMidB;
uniform float3 uLightB;
uniform float uMix;
uniform float2 uBand;
uniform float2 uOffset;
uniform float uSwirl;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.02 + 17.1;
        a *= 0.5;
    }
    return v;
}

float3 paint(float v, float qx, float3 deep, float3 mid, float3 light) {
    float3 col = mix(deep, mid, smoothstep(0.22, 0.58, v));
    return mix(col, light, smoothstep(0.52, 0.9, v + 0.18 * (qx - 0.5)));
}

half4 main(float2 position) {
    float2 uv = (position - uCenter) / uRadius;
    float r = length(uv);
    float ang = uSwirl * (1.0 - r * r) + 0.25 * sin(uTau);
    float c = cos(ang);
    float s = sin(ang);
    float2 p = float2(c * uv.x - s * uv.y, s * uv.x + c * uv.y) * 1.2 + uOffset;
    float2 q = float2(fbm(p + 0.7 * float2(cos(uTau), sin(uTau))),
                      fbm(p + float2(5.2, 1.3) + 0.7 * float2(sin(uTau), -cos(uTau))));
    float f = fbm(p + 2.4 * q + 0.5 * float2(cos(2.0 * uTau + 1.0), sin(2.0 * uTau + 1.0)));
    float v = f + 0.42 * dot(uv, uBand);
    float3 colA = paint(v, q.x, uDeepA, uMidA, uLightA);
    float3 colB = paint(v, q.x, uDeepB, uMidB, uLightB);
    // The seep: where the warp field is low the new colours arrive first.
    float seep = q.y + 0.25 * (f - 0.5);
    float3 col = mix(colA, colB, smoothstep(seep - 0.12, seep + 0.12, uMix * 1.5 - 0.25));
    // A little depth: the rim darkens, a soft light sits upper left.
    col *= 1.0 - 0.16 * smoothstep(0.62, 1.0, r);
    col += 0.08 * (1.0 - smoothstep(0.0, 0.9, length(uv - float2(-0.35, -0.45))));
    float edge = 1.0 / uRadius;
    float alpha = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, r);
    return half4(half3(min(col, float3(1.0)) * alpha), half(alpha));
}
"""
    }
}

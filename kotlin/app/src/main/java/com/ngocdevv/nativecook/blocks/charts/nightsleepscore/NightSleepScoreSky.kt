package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The values the night layers read, sampled once per frame (the React Native `NightSkyValues`). */
internal data class NightSleepScoreSkyValues(
    val night: Double = 0.0, val reveal: Double = 0.0, val phase: Double = 0.0, val meteor: Double = -1.0,
    val clock: Double = 0.0, val twinkle: Double = 1.0, val cursor: Double = 0.0, val presence: Double = 0.0,
)

/** One tick of everything: the controller's frame, the sky's chased values and whether anything is still moving. */
internal data class NightSleepScoreSample(val frame: NightSleepScoreFrame, val sky: NightSleepScoreSkyValues, val running: Boolean)

/** The hypnogram as one smooth ribbon (sky.ts): flat at each stage's level, an S-curve at each change. */
internal object NightSleepScoreRibbon {
    private val chart = NightSleepScoreTheme.Card.Chart

    class Segment(val stage: NightSleepScoreStage, val x0: Float, val x1: Float, val y: Float)

    val segments: List<Segment> = run {
        var minute = 0
        NightSleepScoreData.segments.map { segment ->
            val x0 = chart.left + chart.width * minute / NightSleepScoreData.windowMinutes
            minute += segment.minutes
            val x1 = chart.left + chart.width * minute / NightSleepScoreData.windowMinutes
            Segment(segment.stage, x0, x1, chart.top + chart.level(segment.stage))
        }
    }

    val halfWidths: List<Float> = (0 until segments.size - 1).map { i ->
        min(NightSleepScoreTheme.Aurora.transitionHalfWidth, min(.45f * (segments[i].x1 - segments[i].x0), .45f * (segments[i + 1].x1 - segments[i + 1].x0)))
    }
    val boundaries: List<Float> = segments.dropLast(1).map { it.x1 }
    val levels: List<Float> = segments.map { it.y }

    fun nightColor(stage: NightSleepScoreStage): Color = NightSleepScoreTheme.Night.stages.color(stage)

    /** In design points of the card. */
    val path: Path = Path().apply {
        moveTo(segments[0].x0, segments[0].y)
        segments.forEachIndexed { i, segment ->
            if (i == segments.lastIndex) {
                lineTo(segment.x1, segment.y)
            } else {
                val h = halfWidths[i]
                val next = segments[i + 1]
                lineTo(segment.x1 - h, segment.y)
                cubicTo(segment.x1, segment.y, segment.x1, next.y, segment.x1 + h, next.y)
            }
        }
    }

    /** Horizontal colour stops: each stage's colour on its flat run, blended across its changes. */
    val gradientStops: Array<Pair<Float, Color>> = run {
        val stops = ArrayList<Pair<Float, Color>>()
        segments.forEachIndexed { i, segment ->
            val start = segment.x0 + (if (i > 0) halfWidths[i - 1] else 0f)
            val end = segment.x1 - (if (i < segments.lastIndex) halfWidths[i] else 0f)
            val color = nightColor(segment.stage)
            stops += ((start - chart.left) / chart.width).coerceIn(0f, 1f) to color
            stops += ((end - chart.left) / chart.width).coerceIn(0f, 1f) to color
        }
        stops.toTypedArray()
    }

    /** The night ring: the night as a clock, each stage's share in its colour (hard stops). */
    val ringStops: Array<Pair<Float, Color>> = run {
        val stops = ArrayList<Pair<Float, Color>>()
        var minute = 0
        NightSleepScoreData.segments.forEach { segment ->
            val color = nightColor(segment.stage)
            stops += (minute.toFloat() / NightSleepScoreData.windowMinutes) to color
            stops += ((minute + segment.minutes).toFloat() / NightSleepScoreData.windowMinutes) to color
            minute += segment.minutes
        }
        stops.toTypedArray()
    }

    /** The ribbon's height at a card x, the same curve as the path (smoothstep across each change). */
    fun y(x: Float): Float {
        for (i in boundaries.indices) {
            val b = boundaries[i]
            val h = halfWidths[i]
            if (x < b - h) return levels[i]
            if (x <= b + h) {
                val u = (x - (b - h)) / (2 * h)
                return levels[i] + (levels[i + 1] - levels[i]) * u * u * (3 - 2 * u)
            }
        }
        return levels.last()
    }

    fun headColor(x: Float): Color {
        var i = 0
        while (i < boundaries.size && x > boundaries[i]) i += 1
        return nightColor(segments[i].stage)
    }
}

/**
 * Owns the controller and the sky chase (NightSleepScore.tsx `updateSkyOnUI`): after a touch the sky values follow
 * their targets through a critically damped spring; in pure autoplay they equal the frame exactly.
 */
internal class NightSleepScoreDriver {
    val controller = NightSleepScoreController()
    private val chase = NightSleepScoreMotion.Chase

    private var night = 0.0
    private var vNight = 0.0
    private var reveal = 0.0
    private var vReveal = 0.0
    private var phase = 0.0
    private var vPhase = 0.0
    private var locked = true
    private var last = -1.0
    private var clock = 0.0

    fun sample(now: Double): NightSleepScoreSample {
        val frame = controller.frame(now)
        val dt = if (last < 0) 1.0 / 60 else min(.1, max(0.0, now - last))
        val manual = controller.hasManualSelection
        val quiet = controller.quiet
        if (quiet || (!manual && locked)) {
            val inv = if (dt > 0) 1 / dt else 0.0
            vNight = (frame.night - night) * inv; night = frame.night
            vReveal = (frame.reveal - reveal) * inv; reveal = frame.reveal
            vPhase = (frame.cursorProgress - phase) * inv; phase = frame.cursorProgress
            locked = true
        } else {
            val n = NightSleepScoreMotion.chase(night, vNight, frame.night, dt, chase.night)
            val r = NightSleepScoreMotion.chase(reveal, vReveal, frame.reveal, dt, chase.reveal)
            val p = NightSleepScoreMotion.chase(phase, vPhase, frame.cursorProgress, dt, chase.phase)
            val settled = !manual && abs(n.first - frame.night) < chase.lockDistance && abs(r.first - frame.reveal) < chase.lockDistance &&
                abs(p.first - frame.cursorProgress) < chase.lockDistance && abs(n.second) < chase.lockVelocity &&
                abs(r.second) < chase.lockVelocity && abs(p.second) < chase.lockVelocity
            if (settled) {
                night = frame.night; vNight = 0.0; reveal = frame.reveal; vReveal = 0.0
                phase = frame.cursorProgress; vPhase = 0.0; locked = true
            } else {
                night = n.first.coerceIn(0.0, 1.0); vNight = n.second
                reveal = r.first.coerceIn(0.0, 1.0); vReveal = r.second
                phase = p.first.coerceIn(0.0, 1.0); vPhase = p.second
                locked = false
            }
        }
        last = now
        if (night > .0005 && !quiet) clock = now % 600
        val sky = NightSleepScoreSkyValues(
            night, reveal, phase, if (locked && !manual) frame.meteor else -1.0, clock, if (quiet) 0.0 else 1.0,
            frame.cursorProgress, frame.selectionPresence,
        )
        val skyMoving = !locked || (night > .0005 && !quiet)
        val running = controller.cadence(now) == NightSleepScoreCadence.ANIMATION || skyMoving
        return NightSleepScoreSample(frame, sky, running)
    }
}

// MARK: - AGSL (API 33+)

private const val NightSleepScoreSkySource = """
uniform float2 res;
uniform float scale;
uniform float night;
uniform float phase;
uniform float clock;
uniform float meteor;
uniform float twinkle;

float3 nssHex(float r, float g, float b) { return float3(r, g, b) / 255.0; }
float hash12(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float valueNoise(float2 p) {
  float2 i = floor(p); float2 u = fract(p); u = u * u * (3.0 - 2.0 * u);
  float a = hash12(i); float b = hash12(i + float2(1.0, 0.0));
  float c = hash12(i + float2(0.0, 1.0)); float d = hash12(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half4 main(float2 fragCoord) {
  float2 xy = fragCoord / scale;
  float v = clamp(xy.y / res.y, 0.0, 1.0);
  float a = smoothstep(0.0, 0.45, phase);
  float b = smoothstep(0.55, 0.85, phase);
  float c = smoothstep(0.8, 1.0, phase);
  float3 top = mix(mix(mix(nssHex(10.0, 13.0, 42.0), nssHex(5.0, 7.0, 26.0), a), nssHex(20.0, 27.0, 76.0), b), nssHex(51.0, 80.0, 143.0), c);
  float3 bottom = mix(mix(mix(nssHex(26.0, 31.0, 82.0), nssHex(17.0, 21.0, 61.0), a), nssHex(67.0, 53.0, 120.0), b), nssHex(197.0, 122.0, 142.0), c);
  float dusk = (1.0 - night) * (1.0 - smoothstep(0.0, 0.08, phase));
  bottom = mix(bottom, nssHex(182.0, 141.0, 180.0), 0.85 * dusk);
  float3 col = mix(top, bottom, pow(v, 1.35));

  float neb = valueNoise(xy * 0.018 + float2(phase * 1.6, 0.0)) * valueNoise(xy * 0.041 - float2(0.0, phase * 0.9));
  col += nssHex(107.0, 79.0, 200.0) * neb * 0.16 * (1.0 - c);

  float2 sunAt = float2(res.x * 0.94, res.y * 1.08);
  float sun = exp(-dot(xy - sunAt, xy - sunAt) / (res.x * res.x * 0.42)) * c;
  col = mix(col, nssHex(255.0, 183.0, 140.0), sun * 0.7);

  float cell = 11.0;
  float2 id = floor(xy / cell);
  float stars = 0.0;
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float2 k = id + float2(float(i), float(j));
      float h = hash12(k);
      if (h > 0.6) {
        float2 at = (k + 0.15 + 0.7 * float2(hash12(k + 7.1), hash12(k + 3.7))) * cell;
        float size = mix(0.35, 1.15, pow(hash12(k + 1.3), 3.0));
        float lum = mix(0.35, 1.0, hash12(k + 5.9));
        float tw = mix(0.85, 0.62 + 0.38 * sin(clock * (1.2 + 2.6 * hash12(k + 9.2)) + 6.2832 * hash12(k + 2.4)), twinkle);
        float2 d = xy - at;
        float r = length(d);
        float spot = 1.0 - smoothstep(size * 0.2, size + 0.55, r) + 0.28 * exp(-r * r / (size * size * 9.0));
        float flare = step(0.93, hash12(k + 1.3)) * (exp(-abs(d.x) * 1.1) * exp(-abs(d.y) * 7.0) + exp(-abs(d.y) * 1.1) * exp(-abs(d.x) * 7.0)) * 0.5;
        stars += lum * tw * (spot + flare);
      }
    }
  }
  float starFade = smoothstep(0.35, 0.9, night) * (1.0 - smoothstep(0.62, 0.95, phase)) * (1.0 - 0.55 * smoothstep(0.72, 1.0, v));
  col += float3(0.93, 0.95, 1.0) * stars * starFade;

  if (meteor >= 0.0) {
    float e = 1.0 - (1.0 - meteor) * (1.0 - meteor);
    float2 from = float2(252.0, 16.0);
    float2 to = float2(160.0, 64.0);
    float2 dir = normalize(to - from);
    float2 head = mix(from, to, e);
    float2 rel = xy - head;
    float along = -dot(rel, dir);
    float across = dot(rel, float2(-dir.y, dir.x));
    float tail = clamp(along / 44.0, 0.0, 1.0);
    float streak = step(-0.6, along) * (1.0 - tail) * (1.0 - tail) * exp(-across * across / (0.5 + 1.6 * tail));
    streak += exp(-dot(rel, rel) / 2.2) * 0.8;
    col += float3(1.0, 0.97, 0.92) * streak * sin(3.14159 * meteor) * starFade;
  }

  float fw = 0.6;
  float fx = clamp((night * (1.0 + fw) - v) / fw, 0.0, 1.0);
  float alpha = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
  return half4(half3(clamp(col, 0.0, 1.0) * alpha), half(alpha));
}
"""

private const val NightSleepScoreRaysSource = """
uniform float scale;
uniform float clock;
uniform float edge;
half4 main(float2 fragCoord) {
  float x = fragCoord.x / scale;
  float reveal = 1.0 - smoothstep(edge - 8.0, edge + 1.0, x);
  float sway = sin(x * 0.13 + clock * 0.7) * 2.2;
  float rays = 0.5 + 0.5 * sin(x * 0.95 + sway + clock * 1.1);
  float fine = 0.5 + 0.5 * sin(x * 2.3 - clock * 1.7 + sway * 0.5);
  float a = 0.42 + 0.4 * rays + 0.18 * fine;
  return half4(a * reveal);
}
"""

private fun nightSleepScoreAgslAvailable() = Build.VERSION.SDK_INT >= 33

// MARK: - Sky backdrop

/** Sky palette (sRGB), keyed by the playhead; the Canvas fallback below API 33 uses the same colours. */
private object NightSleepScoreSkyPalette {
    val nightTop = nightSleepScoreHex(0x0A0D2A); val nightBottom = nightSleepScoreHex(0x1A1F52)
    val deepTop = nightSleepScoreHex(0x05071A); val deepBottom = nightSleepScoreHex(0x11153D)
    val predawnTop = nightSleepScoreHex(0x141B4C); val predawnBottom = nightSleepScoreHex(0x433578)
    val dawnTop = nightSleepScoreHex(0x33508F); val dawnBottom = nightSleepScoreHex(0xC57A8E)
    val duskBottom = nightSleepScoreHex(0xB68DB4)
}

private fun nssSmoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun nssFract(x: Double) = x - floor(x)

/** The same hash as the shader's hash12, for the fallback's seeded stars. */
private fun nssHash(x: Double, y: Double): Double {
    var qx = nssFract(x * .1031)
    var qy = nssFract(y * .1031)
    var qz = nssFract(x * .1031)
    val d = qx * (qy + 33.33) + qy * (qz + 33.33) + qz * (qx + 33.33)
    qx += d; qy += d; qz += d
    return nssFract((qx + qy) * qz)
}

/** The sky behind the card's content (AGSL on API 33+, a Canvas approximation below). Transparent at rest. */
@Composable
internal fun NightSleepScoreSkyBackdrop(scale: Float, sample: State<NightSleepScoreSample>, modifier: Modifier = Modifier) {
    val card = NightSleepScoreTheme.Card
    val shader = remember { if (nightSleepScoreAgslAvailable()) RuntimeShader(NightSleepScoreSkySource) else null }
    Canvas(modifier.size((card.width * scale).dp, (card.height * scale).dp)) {
        val sky = sample.value.sky
        if (sky.night <= .0005) return@Canvas
        val k = scale * density
        if (shader != null) {
            shader.setFloatUniform("res", card.width, card.height)
            shader.setFloatUniform("scale", k)
            shader.setFloatUniform("night", sky.night.toFloat())
            shader.setFloatUniform("phase", sky.phase.toFloat())
            shader.setFloatUniform("clock", sky.clock.toFloat())
            shader.setFloatUniform("meteor", sky.meteor.toFloat())
            shader.setFloatUniform("twinkle", sky.twinkle.toFloat())
            drawRect(ShaderBrush(shader), size = size)
        } else {
            drawSkyFallback(sky, k)
        }
    }
}

private fun DrawScope.drawSkyFallback(sky: NightSleepScoreSkyValues, k: Float) {
    val p = NightSleepScoreSkyPalette
    val card = NightSleepScoreTheme.Card
    val phase = sky.phase
    val a = nssSmoothstep(0.0, .45, phase).toFloat()
    val b = nssSmoothstep(.55, .85, phase).toFloat()
    val c = nssSmoothstep(.8, 1.0, phase).toFloat()
    fun mix(x: Color, y: Color, t: Float) = nightSleepScoreMix(x, y, t)
    val top = mix(mix(mix(p.nightTop, p.deepTop, a), p.predawnTop, b), p.dawnTop, c)
    var bottom = mix(mix(mix(p.nightBottom, p.deepBottom, a), p.predawnBottom, b), p.dawnBottom, c)
    val dusk = ((1 - sky.night) * (1 - nssSmoothstep(0.0, .08, phase))).toFloat()
    bottom = mix(bottom, p.duskBottom, .85f * dusk)
    val steps = 12
    val stops = Array(steps + 1) { i ->
        val v = i.toDouble() / steps
        val fw = NightSleepScoreMotion.frontWidth
        val fx = ((sky.night * (1 + fw) - v) / fw).coerceIn(0.0, 1.0)
        val alpha = (fx * fx * fx * (fx * (fx * 6 - 15) + 10)).toFloat()
        val base = mix(top, bottom, v.pow(1.35).toFloat())
        v.toFloat() to Color(base.red * alpha, base.green * alpha, base.blue * alpha, alpha)
    }
    drawRect(Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height))
    val starFade = (nssSmoothstep(.35, .9, sky.night) * (1 - nssSmoothstep(.62, .95, phase))).toFloat()
    if (starFade <= 0.001f) return
    val cell = 11f
    val cols = (card.width / cell).toInt() + 1
    val rows = (card.height / cell).toInt() + 1
    for (cy in 0 until rows) {
        for (cx in 0 until cols) {
            val h = nssHash(cx.toDouble(), cy.toDouble())
            if (h <= .6) continue
            val sx = (cx + .15 + .7 * nssHash(cx + 7.1, cy + 7.1)).toFloat() * cell
            val sy = (cy + .15 + .7 * nssHash(cx + 3.7, cy + 3.7)).toFloat() * cell
            val size = (.35 + (1.15 - .35) * nssHash(cx + 1.3, cy + 1.3).pow(3.0)).toFloat()
            val lum = (.35 + .65 * nssHash(cx + 5.9, cy + 5.9)).toFloat()
            val tw = if (sky.twinkle > 0) (.62 + .38 * sin(sky.clock * (1.2 + 2.6 * nssHash(cx + 9.2, cy + 9.2)) + 6.2832 * nssHash(cx + 2.4, cy + 2.4))).toFloat() else .85f
            val fade = starFade * (1f - .55f * nssSmoothstep(.72, 1.0, (sy / card.height).toDouble()).toFloat())
            drawCircle(Color(.93f, .95f, 1f, (lum * tw * fade).coerceIn(0f, 1f)), radius = (size + .35f) * k, center = Offset(sx * k, sy * k))
        }
    }
    if (sky.meteor >= 0) {
        val m = sky.meteor.toFloat()
        val e = 1 - (1 - m) * (1 - m)
        val m0 = NightSleepScoreTheme.Card.Meteor
        val hx = m0.fromX + (m0.toX - m0.fromX) * e
        val hy = m0.fromY + (m0.toY - m0.fromY) * e
        val dx = m0.toX - m0.fromX
        val dy = m0.toY - m0.fromY
        val len = sqrt(dx * dx + dy * dy)
        val tailX = hx - dx / len * m0.tail
        val tailY = hy - dy / len * m0.tail
        val strength = sin(PI * m).toFloat() * starFade
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color(1f, .97f, .92f, strength)), Offset(tailX * k, tailY * k), Offset(hx * k, hy * k)),
            Offset(tailX * k, tailY * k), Offset(hx * k, hy * k), strokeWidth = 1.4f * k, cap = StrokeCap.Round,
        )
    }
}

// MARK: - Night ring, aurora hypnogram and playhead

private fun blurEffect(sigmaPx: Float) = if (Build.VERSION.SDK_INT >= 31 && sigmaPx > 0f) BlurEffect(sigmaPx, sigmaPx, TileMode.Decal) else null

/** Drawn over the day chart and ring while they fade (NightSky.tsx `NightSkyOverlay`). */
@Composable
internal fun NightSleepScoreOverlay(scale: Float, sample: State<NightSleepScoreSample>, modifier: Modifier = Modifier) {
    val card = NightSleepScoreTheme.Card
    val chart = NightSleepScoreTheme.Card.Chart
    val ring = NightSleepScoreTheme.Card.Ring
    val aurora = NightSleepScoreTheme.Aurora
    val rays = remember { if (nightSleepScoreAgslAvailable()) RuntimeShader(NightSleepScoreRaysSource) else null }
    val ribbonBrush = remember { Brush.horizontalGradient(colorStops = NightSleepScoreRibbon.gradientStops, startX = chart.left, endX = chart.left + chart.width) }

    fun DrawScope.k() = scale * density

    Box(modifier.size((card.width * scale).dp, (card.height * scale).dp)) {
        // Night ring: the night as a clock, filling with the stage colours as the playhead passes.
        Canvas(Modifier.matchParentSize().graphicsLayer { alpha = NightSleepScoreMotion.nightAt(sample.value.sky.night, NightSleepScoreTheme.Card.At.ring).toFloat() }) {
            val sky = sample.value.sky
            drawScale(k(), k(), pivot = Offset.Zero) {
                val center = Offset(ring.cx, ring.cy)
                drawCircle(NightSleepScoreTheme.Night.trackRing, ring.radius, center, style = Stroke(ring.stroke))
                if (sky.reveal > .0005) {
                    rotate(-90f, pivot = center) {
                        drawArc(
                            Brush.sweepGradient(colorStops = NightSleepScoreRibbon.ringStops, center = center),
                            startAngle = 0f, sweepAngle = 360f * sky.reveal.toFloat().coerceAtMost(1f), useCenter = false,
                            topLeft = Offset(center.x - ring.radius, center.y - ring.radius), size = Size(ring.radius * 2, ring.radius * 2),
                            style = Stroke(ring.stroke),
                        )
                    }
                }
            }
        }
        // Ring head: a glow and a bright core where the night ring is drawn up to.
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val sky = sample.value.sky
                val r = sky.reveal
                val headOpacity = min(1.0, r * 30) * (1 - ((r - .97) / .03).coerceIn(0.0, 1.0))
                alpha = (NightSleepScoreMotion.nightAt(sky.night, NightSleepScoreTheme.Card.At.ring) * headOpacity).toFloat()
                renderEffect = blurEffect((aurora.headGlowBlur + 2) * scale * density)
            },
        ) {
            val sky = sample.value.sky
            val turn = sky.reveal * 2 * PI - PI / 2
            val head = Offset((ring.cx + ring.radius * cos(turn)).toFloat(), (ring.cy + ring.radius * sin(turn)).toFloat())
            drawScale(k(), k(), pivot = Offset.Zero) {
                drawCircle(NightSleepScoreRibbon.headColor(chart.left + chart.width * sky.cursor.toFloat()), aurora.headGlow + 3, head)
            }
        }
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val sky = sample.value.sky
                val r = sky.reveal
                val headOpacity = min(1.0, r * 30) * (1 - ((r - .97) / .03).coerceIn(0.0, 1.0))
                alpha = (NightSleepScoreMotion.nightAt(sky.night, NightSleepScoreTheme.Card.At.ring) * headOpacity).toFloat()
            },
        ) {
            val sky = sample.value.sky
            val turn = sky.reveal * 2 * PI - PI / 2
            val head = Offset((ring.cx + ring.radius * cos(turn)).toFloat(), (ring.cy + ring.radius * sin(turn)).toFloat())
            drawScale(k(), k(), pivot = Offset.Zero) { drawCircle(NightSleepScoreTheme.Night.playhead, aurora.headCore + 1, head) }
        }

        // Chart group, faded in by the night front at the chart's height.
        Box(
            Modifier.matchParentSize().graphicsLayer {
                alpha = NightSleepScoreMotion.nightAt(sample.value.sky.night, NightSleepScoreTheme.Card.At.chart).toFloat()
                compositingStrategy = CompositingStrategy.Offscreen
            },
        ) {
            // Aurora curtain: blurred, lifted copies of the ribbon cut into rays by a destination-in mask.
            Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                aurora.curtains.forEach { curtain ->
                    Canvas(
                        Modifier.matchParentSize().graphicsLayer {
                            alpha = curtain.opacity
                            renderEffect = blurEffect(curtain.blur * scale * density)
                        },
                    ) {
                        drawScale(k(), k(), pivot = Offset.Zero) {
                            drawPath(
                                NightSleepScoreRibbon.path, ribbonBrush,
                                style = Stroke(curtain.width, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val sky = sample.value.sky
                    drawScale(k(), k(), pivot = Offset.Zero) {
                        val edge = chart.left + chart.width * sky.reveal.toFloat()
                        val left = chart.left - 12
                        val top = chart.top - 34
                        val width = chart.width + 24
                        val height = chart.height + 46
                        if (rays != null) {
                            rays.setFloatUniform("scale", k())
                            rays.setFloatUniform("clock", sky.clock.toFloat())
                            rays.setFloatUniform("edge", edge)
                            drawRect(ShaderBrush(rays), Offset(left, top), Size(width, height), blendMode = BlendMode.DstIn)
                        } else {
                            drawRect(Color.Black.copy(alpha = .7f), Offset(left, top), Size(width, height), blendMode = BlendMode.DstIn)
                        }
                    }
                }
            }
            // Aurora ribbon, drawn up to the playhead: a soft glow under the core and a white hot core.
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    alpha = aurora.glowOpacity
                    renderEffect = blurEffect(aurora.glowBlur * scale * density)
                },
            ) {
                val sky = sample.value.sky
                drawScale(k(), k(), pivot = Offset.Zero) {
                    clipRect(chart.left - 12, chart.top - 34, chart.left + chart.width * sky.reveal.toFloat(), chart.top - 34 + chart.height + 46) {
                        drawPath(NightSleepScoreRibbon.path, ribbonBrush, style = Stroke(aurora.glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val sky = sample.value.sky
                drawScale(k(), k(), pivot = Offset.Zero) {
                    clipRect(chart.left - 12, chart.top - 34, chart.left + chart.width * sky.reveal.toFloat(), chart.top - 34 + chart.height + 46) {
                        drawPath(NightSleepScoreRibbon.path, ribbonBrush, style = Stroke(aurora.core, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(
                            NightSleepScoreRibbon.path, Color.White, alpha = aurora.hotCoreOpacity,
                            style = Stroke(aurora.hotCore, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            // Playhead and its light where it meets the ribbon.
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = sample.value.sky.presence.toFloat() }) {
                val sky = sample.value.sky
                val x = chart.left + chart.width * sky.cursor.toFloat()
                drawScale(k(), k(), pivot = Offset.Zero) {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .7f), Color.White.copy(alpha = 0f)),
                            startY = chart.top - 2, endY = chart.top + chart.height + 2,
                        ),
                        Offset(x - aurora.playheadWidth / 2, chart.top - 2), Size(aurora.playheadWidth, chart.height + 4),
                    )
                }
            }
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    alpha = sample.value.sky.presence.toFloat()
                    renderEffect = blurEffect(aurora.headGlowBlur * scale * density)
                },
            ) {
                val sky = sample.value.sky
                val x = chart.left + chart.width * sky.cursor.toFloat()
                drawScale(k(), k(), pivot = Offset.Zero) {
                    drawCircle(NightSleepScoreRibbon.headColor(x), aurora.headGlow, Offset(x, NightSleepScoreRibbon.y(x)))
                }
            }
            Canvas(Modifier.matchParentSize().graphicsLayer { alpha = sample.value.sky.presence.toFloat() }) {
                val sky = sample.value.sky
                val x = chart.left + chart.width * sky.cursor.toFloat()
                drawScale(k(), k(), pivot = Offset.Zero) {
                    drawCircle(NightSleepScoreTheme.Night.playhead, aurora.headCore, Offset(x, NightSleepScoreRibbon.y(x)))
                }
            }
        }
    }
}

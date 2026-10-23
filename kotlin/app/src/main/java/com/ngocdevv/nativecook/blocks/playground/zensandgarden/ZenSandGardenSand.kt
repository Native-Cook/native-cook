package com.ngocdevv.nativecook.blocks.playground.zensandgarden

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The height map. The sand's shape lives in an offscreen bitmap, HEIGHT_SCALE pixels per dp, as a
// grey level: BASE is level sand, darker is lower, lighter is higher. Rake strokes, ripples and the
// smoothing sweep are drawn into it a few strips at a time, never redrawn from history.
internal const val HEIGHT_SCALE = 2f
internal const val BASE = 0.5f

// The rake: TINES teeth TINE_GAP dp apart. One pass leaves a groove under each tooth, a ridge
// between them, and a berm on either side, RAKE_HALF dp from the middle. The profile is sampled at
// PROFILE_STEPS + 1 points across and drawn as a strip of triangles from the last cross-section to
// the new one every STAMP_STEP dp the rake travels.
internal const val TINES = 5
internal const val TINE_GAP = 9f
internal const val RAKE_HALF = TINE_GAP * 3
internal const val GROOVE = 0.3f
internal const val PROFILE_STEPS = 48
internal const val STAMP_STEP = 1.5f

// A tap on a stone sends RINGS grooves out round it, TINE_GAP apart, over RIPPLE_TIME s.
internal const val RINGS = 5
internal const val RIPPLE_TIME = 1.3f
private const val RING_STOPS = 72
// The smoothing sweep: a soft pile of sand PILE dp deep crosses the garden in SWEEP_TIME s and
// leaves faint broom streaks STREAK_PERIOD dp apart behind it.
internal const val PILE = 44f
internal const val SWEEP_TIME = 2.6f
private const val STREAK_PERIOD = 37f
private val Streaks = floatArrayOf(0.0f, 0.62f, 0.15f, 0.9f, 0.35f, 0.05f, 0.7f, 0.48f, 0.95f, 0.22f, 0.55f, 0.1f, 0.8f, 0.3f)
private const val STREAK_DEPTH = 0.02f

/**
 * A stone, placed as a share of the screen: radius and height in dp, a wobbly outline made of
 * three cosines (amplitude, phase), a tint and how much moss grows at its foot.
 */
internal class ZenStone(
    val x: Float,
    val y: Float,
    val r: Float,
    val h: Float,
    val a2: Float,
    val p2: Float,
    val a3: Float,
    val p3: Float,
    val a5: Float,
    val p5: Float,
    val tint: FloatArray,
    val moss: Float,
) {
    fun cx(w: Float) = x * w
    fun cy(h: Float) = y * h

    /** The outline radius at a bearing (the same three cosines the shader uses). */
    fun radius(th: Float) = r * (1 + a2 * cos(2 * th + p2) + a3 * cos(3 * th + p3) + a5 * cos(5 * th + p5))

    /** Distance from the centre as a share of the outline at that bearing. */
    fun distance(px: Float, py: Float, w: Float, h: Float): Float {
        val qx = px - cx(w)
        val qy = py - cy(h)
        return hypot(qx, qy) / radius(atan2(qy, qx))
    }

    /** The outer edge of the outline, where its ring grooves start. */
    val reach get() = r * (1 + a2 + a3 + a5)
}

internal val Stones = listOf(
    ZenStone(0.27f, 0.42f, 48f, 36f, 0.12f, 0.6f, 0.05f, 2.1f, 0.025f, 0.4f, floatArrayOf(0.4f, 0.41f, 0.43f), 0.85f),
    ZenStone(0.74f, 0.6f, 33f, 27f, 0.09f, 2.2f, 0.06f, 0.9f, 0.02f, 1.7f, floatArrayOf(0.56f, 0.5f, 0.44f), 0.4f),
    ZenStone(0.41f, 0.79f, 21f, 19f, 0.14f, 1.3f, 0.04f, 3.0f, 0.03f, 2.6f, floatArrayOf(0.31f, 0.3f, 0.3f), 1f),
)

/** The rake's cross-section: grooves under the teeth, ridges between, berms at the edges. */
private fun profileAt(u: Float): Float {
    val a = abs(u)
    val edge = TINE_GAP * 2.5f
    if (a <= edge) return BASE - GROOVE * cos(2 * PI.toFloat() * u / TINE_GAP)
    val t = min(1f, (a - edge) / (RAKE_HALF - edge))
    val s = t * t * (3 - 2 * t)
    return BASE + GROOVE * (1 - s)
}

private fun grey(v: Float): Int {
    val c = (v.coerceIn(0f, 1f) * 255f).roundToInt()
    return (0xFF shl 24) or (c shl 16) or (c shl 8) or c
}

private val ProfileColors = IntArray((PROFILE_STEPS + 1) * 4).also { colors ->
    for (k in 0 until 2) {
        for (i in 0..PROFILE_STEPS) {
            colors[k * (PROFILE_STEPS + 1) + i] = grey(profileAt(-RAKE_HALF + 2 * RAKE_HALF * i / PROFILE_STEPS))
        }
    }
}

// Two triangles per step between the previous cross-section (first half) and the new one.
private val Strip = ShortArray(PROFILE_STEPS * 6).also { strip ->
    val n = PROFILE_STEPS + 1
    for (i in 0 until PROFILE_STEPS) {
        val o = i * 6
        strip[o] = i.toShort()
        strip[o + 1] = (i + 1).toShort()
        strip[o + 2] = (n + i).toShort()
        strip[o + 3] = (i + 1).toShort()
        strip[o + 4] = (n + i + 1).toShort()
        strip[o + 5] = (n + i).toShort()
    }
}

/** Repeating faint stripes, like a broom's, running in the sweep's direction. */
private fun streakShader(across: Boolean): Shader {
    val colors = IntArray(Streaks.size + 1)
    val stops = FloatArray(Streaks.size + 1)
    for (i in 0..Streaks.size) {
        val v = Streaks[i % Streaks.size]
        colors[i] = grey(BASE + (v - 0.5f) * STREAK_DEPTH)
        stops[i] = i.toFloat() / Streaks.size
    }
    val ex = if (across) STREAK_PERIOD else 0f
    val ey = if (across) 0f else STREAK_PERIOD
    return LinearGradient(0f, 0f, ex, ey, colors, stops, Shader.TileMode.REPEAT)
}

private val PileColors = intArrayOf(
    grey(BASE), grey(BASE + GROOVE * 0.35f), grey(BASE + GROOVE * 1.1f), grey(0.98f), grey(BASE + GROOVE * 0.4f),
)
private val PileStops = floatArrayOf(0f, 0.45f, 0.76f, 0.9f, 1f)

/**
 * The sand's shape: a bitmap 2 px per dp drawn into with a software canvas, the stones clipped
 * out of every stamp. It is only ever drawn into, so each frame costs the strips that changed.
 */
internal class ZenHeightMap(val w: Float, val h: Float) {
    val bitmap: Bitmap = Bitmap.createBitmap(
        ceil(w * HEIGHT_SCALE).toInt().coerceAtLeast(1),
        ceil(h * HEIGHT_SCALE).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    private val canvas = Canvas(bitmap)
    private val stonesPath = Path()
    private val stampPaint = Paint().apply { isAntiAlias = false }
    private val fill = Paint()
    private val flatAcross = Paint().apply { shader = streakShader(true) }
    private val flatDown = Paint().apply { shader = streakShader(false) }
    private val verts = FloatArray((PROFILE_STEPS + 1) * 4)

    /** For the shader: the bitmap sampled linearly, clamped at the edges. */
    val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    init {
        if (android.os.Build.VERSION.SDK_INT >= 33) shader.filterMode = BitmapShader.FILTER_MODE_LINEAR
        // All three outlines, slightly grown: sand is never drawn under a stone.
        for (s in Stones) {
            for (i in 0..72) {
                val th = i / 72f * 2 * PI.toFloat()
                val r = s.radius(th) + 0.5f
                val x = s.cx(w) + cos(th) * r
                val y = s.cy(h) + sin(th) * r
                if (i == 0) stonesPath.moveTo(x, y) else stonesPath.lineTo(x, y)
            }
            stonesPath.close()
        }
        reset()
    }

    /** Level, streaked sand. */
    fun reset() {
        canvas.save()
        canvas.scale(HEIGHT_SCALE, HEIGHT_SCALE)
        canvas.drawRect(-2f, -2f, w + 2f, h + 2f, flatAcross)
        canvas.restore()
    }

    /** Starts a frame's drawing: everything drawn until [end] misses the stones. */
    fun begin() {
        canvas.save()
        canvas.scale(HEIGHT_SCALE, HEIGHT_SCALE)
        canvas.clipOutPath(stonesPath)
    }

    fun end() = canvas.restore()

    /** Fills the strip between two cross-sections (centre and unit normal) with the rake's profile. */
    fun stamp(x0: Float, y0: Float, nx0: Float, ny0: Float, x1: Float, y1: Float, nx1: Float, ny1: Float) {
        val n = PROFILE_STEPS + 1
        for (i in 0..PROFILE_STEPS) {
            val u = -RAKE_HALF + 2 * RAKE_HALF * i / PROFILE_STEPS
            verts[i * 2] = x0 + nx0 * u
            verts[i * 2 + 1] = y0 + ny0 * u
            verts[(n + i) * 2] = x1 + nx1 * u
            verts[(n + i) * 2 + 1] = y1 + ny1 * u
        }
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, n * 4, verts, 0, null, 0, ProfileColors, 0,
            Strip, 0, Strip.size, stampPaint,
        )
    }

    /** The rings round a stone, out to `front` dp from its centre, as a radial gradient. */
    fun ripple(s: ZenStone, front: Float) {
        val inner = s.reach + 3
        if (front <= inner) return
        val colors = IntArray(RING_STOPS + 1)
        val stops = FloatArray(RING_STOPS + 1)
        for (i in 0..RING_STOPS) {
            val r = i.toFloat() / RING_STOPS * front
            var v = BASE + GROOVE * 0.25f
            if (r >= inner) {
                // Rings start on a ridge, so the sand rises gently from the stone's collar.
                v = BASE + GROOVE * cos(2 * PI.toFloat() * (r - inner) / TINE_GAP)
                // The leading edge is a berm of pushed sand.
                val lead = ((r - (front - TINE_GAP * 0.6f)) / (TINE_GAP * 0.6f)).coerceIn(0f, 1f)
                v += (BASE + GROOVE * 1.15f - v) * lead
            }
            colors[i] = grey(v)
            stops[i] = i.toFloat() / RING_STOPS
        }
        val cx = s.cx(w)
        val cy = s.cy(h)
        fill.isAntiAlias = true
        fill.shader = RadialGradient(cx, cy, front, colors, stops, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, front, fill)
        fill.shader = null
    }

    /**
     * One frame of the sweep: level, streaked sand behind the pile, the pile itself at `front` dp
     * along the sweep's direction (1 down, 2 up, 3 right, 4 left).
     */
    fun sweep(dir: Int, front: Float) {
        val vertical = dir <= 2
        val extent = if (vertical) h else w
        val back = front - PILE
        // Positions along the sweep's axis, measured from the edge it starts at.
        val forward = dir == 1 || dir == 3
        val a = if (forward) back else extent - back
        val b = if (forward) front else extent - front
        fill.isAntiAlias = false
        if (vertical) {
            val top = min(a, b)
            if (dir == 1) canvas.drawRect(0f, -2f, w, -2f + max(0f, a + 2), flatAcross)
            else canvas.drawRect(0f, a, w, a + max(0f, h + 2 - a), flatAcross)
            fill.shader = LinearGradient(0f, a, 0f, b, PileColors, PileStops, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, top, w, top + PILE, fill)
        } else {
            val left = min(a, b)
            if (dir == 3) canvas.drawRect(-2f, 0f, -2f + max(0f, a + 2), h, flatDown)
            else canvas.drawRect(a, 0f, a + max(0f, w + 2 - a), h, flatDown)
            fill.shader = LinearGradient(a, 0f, b, 0f, PileColors, PileStops, Shader.TileMode.CLAMP)
            canvas.drawRect(left, 0f, left + PILE, h, fill)
        }
        fill.shader = null
    }
}

/**
 * Lights the height map like sand under a low sun (with ridge shadows), and draws the stones and
 * their moss analytically, so the stones' long soft shadows fall across the grooves.
 */
internal const val SAND_AGSL = """
uniform shader heightMap;
uniform float2 size;
uniform float scale;
uniform float3 sun;
uniform float3 sunColor;
uniform float3 ambient;
uniform float4 aPos;
uniform float4 aShape;
uniform float2 aShape2;
uniform float4 aTint;
uniform float4 bPos;
uniform float4 bShape;
uniform float2 bShape2;
uniform float4 bTint;
uniform float4 cPos;
uniform float4 cShape;
uniform float2 cShape2;
uniform float4 cTint;
uniform float time;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
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
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.07 + 13.1;
    a *= 0.5;
  }
  return v;
}

float sandH(float2 p) {
  return heightMap.eval(p * scale).r;
}

float stoneD(float2 p, float4 pos, float4 shp, float2 shp2) {
  float2 q = p - pos.xy;
  float l = length(q);
  if (l > pos.z * 1.7) return 9.0;
  float th = atan(q.y, q.x);
  float r = pos.z * (1.0 + shp.x * cos(2.0 * th + shp.y) + shp.z * cos(3.0 * th + shp.w) + shp2.x * cos(5.0 * th + shp2.y));
  return l / r;
}

float domeH(float d, float h) {
  return h * pow(clamp(1.0 - d * d, 0.0, 1.0), 0.45);
}

float stonesH(float2 p) {
  float a = domeH(stoneD(p, aPos, aShape, aShape2), aPos.w);
  float b = domeH(stoneD(p, bPos, bShape, bShape2), bPos.w);
  float c = domeH(stoneD(p, cPos, cShape, cShape2), cPos.w);
  return max(a, max(b, c));
}

float3 stoneColor(float2 p, float d, float4 pos, float4 shp, float2 shp2, float3 tint) {
  float e = 0.8;
  float hx0 = domeH(stoneD(p - float2(e, 0.0), pos, shp, shp2), pos.w);
  float hx1 = domeH(stoneD(p + float2(e, 0.0), pos, shp, shp2), pos.w);
  float hy0 = domeH(stoneD(p - float2(0.0, e), pos, shp, shp2), pos.w);
  float hy1 = domeH(stoneD(p + float2(0.0, e), pos, shp, shp2), pos.w);
  float3 n = normalize(float3((hx0 - hx1) / (2.0 * e), (hy0 - hy1) / (2.0 * e), 1.0));
  float grain = fbm(p * 0.08 + pos.xy * 0.013);
  float vein = smoothstep(0.46, 0.5, fbm(p * float2(0.05, 0.16) + pos.yx * 0.02)) * 0.1;
  float3 alb = tint * (0.8 + 0.4 * grain) * (1.0 - vein);
  float speck = hash(floor(p * 1.6) + pos.xy);
  alb *= 1.0 - 0.35 * step(0.92, speck);
  alb = mix(alb, min(alb * 1.55, float3(1.0)), step(0.975, hash(floor(p * 1.6) + pos.yx)));
  float wrapL = clamp((dot(n, sun) + 0.25) / 1.25, 0.0, 1.0);
  float3 hv = normalize(sun + float3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, hv), 0.0), 30.0) * 0.2;
  float ao = 0.5 + 0.5 * smoothstep(1.0, 0.72, d);
  return alb * (ambient * (0.45 + 0.55 * n.z) * ao + sunColor * wrapL * 1.05) + sunColor * spec;
}

float mossMask(float2 p, float d, float amount, float seed) {
  if (amount <= 0.0 || d > 1.7) return 0.0;
  float reach = 1.0 + amount * (0.02 + 0.24 * fbm(p * 0.06 + seed));
  float patches = smoothstep(0.3, 0.52, fbm(p * 0.05 + seed * 1.7));
  float foot = smoothstep(0.82, 0.94, d);
  float fuzz = noise(p * 0.9 + seed) - 0.5;
  return clamp((reach - d) * 14.0 + fuzz * 1.8, 0.0, 1.0) * patches * foot;
}

float3 mossColor(float2 p, float shade, float m) {
  float c = fbm(p * 0.45);
  float3 alb = mix(float3(0.11, 0.15, 0.07), float3(0.34, 0.38, 0.15), c);
  alb = mix(float3(0.36, 0.33, 0.2), alb, smoothstep(0.0, 0.7, m));
  float b0 = noise(p * 1.4);
  float bx = noise(p * 1.4 + float2(0.6, 0.0)) - b0;
  float by = noise(p * 1.4 + float2(0.0, 0.6)) - b0;
  float3 n = normalize(float3(-bx * 2.0, -by * 2.0, 1.0));
  float lit = max(dot(n, sun), 0.0);
  return alb * (ambient * 0.75 + sunColor * lit * (0.25 + 0.75 * shade) * 1.5);
}

half4 main(float2 p) {
  float dA = stoneD(p, aPos, aShape, aShape2);
  float dB = stoneD(p, bPos, bShape, bShape2);
  float dC = stoneD(p, cPos, cShape, cShape2);
  float dMin = min(dA, min(dB, dC));
  float2 toSun = normalize(sun.xy);
  float tanE = sun.z / max(length(sun.xy), 0.001);

  float stoneShadow = 1.0;
  float3 col = float3(0.0);
  if (dMin > 0.96) {
    float D = 5.0;
    float h = sandH(p);
    float hl = sandH(p - float2(1.0, 0.0));
    float hr = sandH(p + float2(1.0, 0.0));
    float hu = sandH(p - float2(0.0, 1.0));
    float hd = sandH(p + float2(0.0, 1.0));
    float3 n = normalize(float3((hl - hr) * D * 0.5, (hu - hd) * D * 0.5, 1.0));
    float g = hash(floor(p * 2.0));
    float g2 = hash(floor(p * 2.0) + 17.0);
    n = normalize(n + float3(g - 0.5, g2 - 0.5, 0.0) * 0.14);
    float lit = max(dot(n, sun), 0.0);
    float occl = 0.0;
    for (int i = 1; i <= 5; i++) {
      float s = float(i) * 1.3;
      occl = max(occl, (sandH(p + toSun * s) - h) * D - s * tanE);
    }
    float sandShadow = 1.0 - smoothstep(0.0, 0.8, occl);
    for (int i = 1; i <= 12; i++) {
      float s = float(i) * 8.0;
      float pen = 1.5 + s * 0.14;
      stoneShadow = min(stoneShadow, smoothstep(-pen, pen, s * tanE - stonesH(p + toSun * s)));
    }
    float contact = 1.0 - 0.4 * (1.0 - smoothstep(1.0, 1.3, dMin));
    float3 alb = float3(0.9, 0.82, 0.68) * (0.94 + 0.12 * fbm(p * 0.011));
    alb *= 0.93 + 0.07 * g;
    alb *= 0.84 + 0.16 * smoothstep(0.12, 0.55, h);
    float shade = min(sandShadow, stoneShadow);
    col = alb * (ambient * (0.5 + 0.5 * n.z) * contact + sunColor * lit * shade * 1.15 * contact);
    float twinkle = 0.5 + 0.5 * sin(time * 2.3 + g2 * 40.0);
    col += sunColor * step(0.994, g) * lit * shade * 0.6 * twinkle;
  }
  if (dA < 1.0) col = mix(col, stoneColor(p, dA, aPos, aShape, aShape2, aTint.rgb), clamp((1.0 - dA) * aPos.z / 0.7, 0.0, 1.0));
  if (dB < 1.0) col = mix(col, stoneColor(p, dB, bPos, bShape, bShape2, bTint.rgb), clamp((1.0 - dB) * bPos.z / 0.7, 0.0, 1.0));
  if (dC < 1.0) col = mix(col, stoneColor(p, dC, cPos, cShape, cShape2, cTint.rgb), clamp((1.0 - dC) * cPos.z / 0.7, 0.0, 1.0));
  float moss = max(mossMask(p, dA, aTint.a, 3.1), max(mossMask(p, dB, bTint.a, 7.7), mossMask(p, dC, cTint.a, 12.4)));
  if (moss > 0.0) col = mix(col, mossColor(p, dMin < 1.0 ? 1.0 : stoneShadow, moss), moss * 0.94);

  col = (1.0 - exp(-col * 1.7)) * 1.1;
  float2 v = (p / size - 0.5) * float2(1.15, 0.85);
  col *= 1.0 - 0.22 * smoothstep(0.3, 0.75, length(v));
  return half4(half3(col), 1.0);
}
"""

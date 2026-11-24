// Heat Haze Weather — the scene: one AGSL runtime shader over the whole screen (API 33+), ported line
// for line from hhw-shader.ts, and a drawn version for API 26–32.
//
// The scene is drawn in code: a sky in three stops with the sun and its glow, a far ridge and a mesa,
// a desert floor in perspective with scrub, a road with its lines and a row of utility poles (the
// straight lines that show the haze best). Heat haze is a displacement of the sampling point: two
// layers of value noise rise through a band around the horizon; they cross-fade on a FLOW period so
// the field loops (each layer is invisible when it wraps). Near the horizon the road turns into a
// mirage: the sky, mirrored, shimmering. A finger adds its own local shimmer.
//
// Colours are sRGB, as Android's runtime shaders draw into an sRGB window (the RN shader's `wide`
// conversion is for iOS only, so it is left out here).
package com.ngocdevv.nativecook.blocks.cards.heathazeweather

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** what one frame of the scene needs, all in dp */
internal class SceneUniforms {
    var sunX = 0f
    var sunY = 0f
    var heat = 0f
    var dusk = 0f
    /** flow phase 0…1 */
    var phase = 0f
    var touchX = 0f
    var touchY = 0f
    var touchOn = 0f
}

/** the palette, noon → dusk (sRGB) */
private object Palette {
    val SKY_TOP = intArrayOf(0x2C69AE, 0x25284F)
    val SKY_MID = intArrayOf(0x8DB5D6, 0x8A5A7E)
    val SKY_LOW = intArrayOf(0xF2E4C6, 0xF29A5E)
    val SUN = intArrayOf(0xFFF4DC, 0xFFB46E)
    val RIDGE = intArrayOf(0xA7A9B4, 0x5E4868)
    val MESA = intArrayOf(0xB48C6C, 0x4A3044)
    val SAND_FAR = intArrayOf(0xDDBF90, 0xA06A52)
    val SAND_NEAR = intArrayOf(0xBC915F, 0x4F3631)
    val ASPHALT = intArrayOf(0x4C4744, 0x2B2528)
    val PAINT = intArrayOf(0xEDE6D5, 0xC9AFA0)
    val POLE = intArrayOf(0x3C3431, 0x22191F)
}

/** what draws the scene; the canvas is already scaled to dp */
internal interface HhwScene {
    fun draw(canvas: Canvas, width: Float, height: Float, u: SceneUniforms)
}

private fun f(v: Float): String = String.format(Locale.US, "%.4f", v)
private fun c3(hex: Int): String = "float3(${f(((hex shr 16) and 255) / 255f)}, ${f(((hex shr 8) and 255) / 255f)}, ${f((hex and 255) / 255f)})"
private fun mixed(pair: IntArray): String = "mix(${c3(pair[0])}, ${c3(pair[1])}, dusk)"

private val HHW_AGSL = """
uniform float2 size;     // dp
uniform float2 sun;      // dp
uniform float heat;      // 0…1
uniform float dusk;      // 0…1
uniform float phase;     // flow phase 0…1
uniform float3 touch;    // x, y (dp), strength
uniform float dpr;       // pixels per dp

float hsh(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 q = fract(p);
  float2 u = q * q * (3.0 - 2.0 * q);
  float a = hsh(i);
  float b = hsh(i + float2(1.0, 0.0));
  float c = hsh(i + float2(0.0, 1.0));
  float d = hsh(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float horizonY() { return size.y * ${f(HORIZON)}; }

float3 skyAt(float2 p) {
  float v = clamp(p.y / horizonY(), 0.0, 1.0);
  float3 top = ${mixed(Palette.SKY_TOP)};
  float3 mid = ${mixed(Palette.SKY_MID)};
  float3 low = ${mixed(Palette.SKY_LOW)};
  float3 col = v < 0.6 ? mix(top, mid, smoothstep(0.0, 0.6, v)) : mix(mid, low, smoothstep(0.6, 1.0, v));
  float d = length(p - sun);
  float3 sc = ${mixed(Palette.SUN)};
  float r = size.x * 0.046;
  col += sc * (0.55 * exp(-d / (size.x * 0.075)) + (0.18 + 0.22 * dusk) * exp(-d / (size.x * 0.42)));
  col = mix(col, sc, smoothstep(r + 0.8, r - 0.8, d));
  return col;
}

float ridgeTop(float x) {
  float u = x / size.x;
  return size.y * (${f(HORIZON)} - 0.03 - 0.014 * sin(u * 5.3 + 1.1) - 0.008 * sin(u * 13.1 + 0.3));
}

// a butte: a flat top, a cliff, then a long talus slope down to the plain
float butte(float u, float centre, float top, float base, float h) {
  float d = abs(u - centre);
  if (d >= base) { return 0.0; }
  if (d <= top) { return h; }
  return h * (1.0 - pow((d - top) / (base - top), 0.55));
}

float mesaTop(float x) {
  float u = x / size.x;
  float h = max(butte(u, 0.185, 0.07, 0.15, 0.07), butte(u, 0.865, 0.022, 0.06, 0.042));
  h += step(0.0005, h) * 0.003 * (vnoise(float2(x / 3.0, 7.0)) - 0.5);
  return size.y * (${f(HORIZON)} - h) + step(h, 0.0) * size.y;
}

float3 groundAt(float2 p) {
  float hy = horizonY();
  float span = size.y - hy;
  float dy = max(p.y - hy, 0.0001);
  float z = span / dy;
  float wb = size.x * 0.62;
  float xw = (p.x - size.x * 0.5) * z / wb;
  float perPt = z / wb;
  float nearK = clamp(dy / span, 0.0, 1.0);
  float3 col = mix(${mixed(Palette.SAND_FAR)}, ${mixed(Palette.SAND_NEAR)}, sqrt(nearK));
  float scrub = vnoise(float2(xw * 7.0, z * 2.6));
  col *= 1.0 - 0.3 * smoothstep(0.8, 0.9, scrub) * smoothstep(0.02, 0.12, nearK) * (1.0 - 0.5 * smoothstep(0.5, 1.0, nearK));
  col *= 0.95 + 0.1 * vnoise(float2(xw * 2.0, z * 0.7));
  col *= 0.96 + 0.08 * vnoise(float2(xw * 9.0, z * 4.0));

  float ax = abs(xw);
  float onRoad = 1.0 - smoothstep(0.95 - perPt, 0.95 + perPt, ax);
  float3 road = ${mixed(Palette.ASPHALT)} * (0.94 + 0.1 * vnoise(float2(xw * 14.0, z * 6.0)));
  float edge = smoothstep(0.82 - perPt, 0.82 + perPt, ax) * (1.0 - smoothstep(0.87 - perPt, 0.87 + perPt, ax));
  float dash = (1.0 - smoothstep(0.022 - perPt, 0.022 + perPt, ax)) * step(fract(z * 0.35), 0.5);
  road = mix(road, ${mixed(Palette.PAINT)}, clamp(edge + dash, 0.0, 1.0) * 0.85);
  col = mix(col, road, onRoad);

  col = mix(col, ${mixed(Palette.SKY_LOW)}, exp(-dy / (size.y * 0.035)) * 0.62);
  return col;
}

float3 scene(float2 p) {
  float hy = horizonY();
  float3 col;
  if (p.y < hy) {
    col = skyAt(p);
    float rt = ridgeTop(p.x);
    float3 ridge = mix(${mixed(Palette.RIDGE)}, ${mixed(Palette.SKY_LOW)}, 0.35);
    col = mix(col, ridge, smoothstep(rt - 0.6, rt + 0.6, p.y));
    float mt = mesaTop(p.x);
    float strata = vnoise(float2(p.x / 46.0, p.y / 2.6));
    float3 mesa = ${mixed(Palette.MESA)} * (0.84 + 0.2 * strata);
    float talus = smoothstep(mt + size.y * 0.018, hy, p.y);
    mesa = mix(mesa, mix(${mixed(Palette.SAND_FAR)}, ${mixed(Palette.MESA)}, 0.5), talus * 0.5);
    float cx0 = p.x < size.x * 0.5 ? size.x * 0.185 : size.x * 0.865;
    float halfW = p.x < size.x * 0.5 ? size.x * 0.11 : size.x * 0.04;
    float lit = smoothstep(-halfW, halfW, (p.x - cx0) * sign(sun.x - cx0));
    mesa *= 0.78 + 0.24 * lit;
    col = mix(col, mesa, smoothstep(mt - 0.6, mt + 0.6, p.y));
  } else {
    col = groundAt(p);
    float band = size.y * 0.05;
    float k = heat * (1.0 - smoothstep(0.0, band, p.y - hy));
    if (k > 0.002) {
      float2 orbit = float2(cos(6.2831853 * phase), sin(6.2831853 * phase)) * 0.9;
      float wob = vnoise(float2(p.x / 9.0, (p.y - hy) / 2.0) + orbit);
      float3 mirrored = skyAt(float2(p.x, hy - (p.y - hy) * 1.7));
      col = mix(col, mirrored, k * (0.55 + 0.35 * wob));
    }
  }

  float span = size.y - hy;
  float wb = size.x * 0.62;
  float z = 1.4;
  float px0 = 0.0;
  float py0 = 0.0;
  float pb0 = 0.0;
  for (int j = 0; j < 6; j++) {
    float base = hy + span / z;
    float xs = size.x * 0.5 + 1.55 * wb / z;
    float ph = 2.3 * span / z;
    float pw = max(0.016 * wb / z, 0.45);
    float barY = base - ph * 0.92;
    float bw = 0.3 * wb / z;
    float bh = max(0.02 * span / z, 0.45);
    float inX = 1.0 - smoothstep(pw - 0.5, pw + 0.5, abs(p.x - xs));
    float inY = step(base - ph, p.y) * (1.0 - smoothstep(base - 0.5, base + 0.5, p.y));
    float inBar = (1.0 - smoothstep(bh - 0.5, bh + 0.5, abs(p.y - barY))) * (1.0 - smoothstep(bw - 0.5, bw + 0.5, abs(p.x - xs)));
    float wire = 0.0;
    if (j > 0 && p.x > xs - bw && p.x < px0 + pb0) {
      for (int k = 0; k < 2; k++) {
        float side = k == 0 ? -0.8 : 0.8;
        float xa = xs + side * bw;
        float xb = px0 + side * pb0;
        float u = clamp((p.x - xa) / max(xb - xa, 0.001), 0.0, 1.0);
        float yw = mix(barY, py0, u) + 0.09 * span / z * 4.0 * u * (1.0 - u);
        wire = max(wire, 1.0 - smoothstep(0.25, 0.85, abs(p.y - yw)));
      }
    }
    float fog = exp(-(base - hy) / (size.y * 0.05));
    float3 pole = mix(${mixed(Palette.POLE)}, ${mixed(Palette.SKY_LOW)}, 0.75 * fog);
    col = mix(col, pole, max(max(inX * inY, inBar), wire * 0.7));
    px0 = xs;
    py0 = barY;
    pb0 = bw;
    z *= 1.9;
  }
  return col;
}

half4 main(float2 p) {
  float hy = horizonY();
  float w = exp(-abs(p.y - hy - size.y * ${f(Haze.BAND_OFFSET)}) / (size.y * ${f(Haze.BAND_FOLD)}));
  float2 tv = p - touch.xy;
  float poke = touch.z * exp(-dot(tv, tv) / ${f(Haze.TOUCH_RADIUS * Haze.TOUCH_RADIUS)});
  float amp = heat * ${f(Haze.AMP)} * w + poke * ${f(Haze.TOUCH_AMP)};
  float2 off = float2(0.0);
  if (amp > 0.01) {
    float wa = 1.0 - abs(1.0 - 2.0 * phase);
    float norm = 1.0 / sqrt(wa * wa + (1.0 - wa) * (1.0 - wa));
    float2 q = float2(p.x / ${f(Haze.CELL_X)}, p.y / ${f(Haze.CELL_Y)});
    float2 qa = q + float2(0.0, phase * ${f(Haze.RISE)});
    float2 qb = q + float2(3.7, fract(phase + 0.5) * ${f(Haze.RISE)});
    float na = vnoise(qa) + 0.5 * vnoise(qa * 2.3 + 5.1);
    float nb = vnoise(qb) + 0.5 * vnoise(qb * 2.3 + 5.1);
    float ma = vnoise(qa * 0.7 + 11.0);
    float mb = vnoise(qb * 0.7 + 11.0);
    float nx = (mix(nb, na, wa) / 1.5 - 0.5) * norm;
    float ny = (mix(mb, ma, wa) - 0.5) * norm;
    off = float2(nx * 2.0, ny) * amp;
  }
  float3 col = scene(p + off);
  col = mix(col, ${mixed(Palette.SKY_LOW)}, heat * 0.1 * w);
  col += poke * 0.035;

  float2 c = (p / size - 0.5) * float2(0.9, 1.1);
  col *= 1.0 - 0.14 * dot(c, c) * 2.0;
  col += (hsh(floor(p * dpr)) - 0.5) * 0.024;
  col = clamp(col, 0.0, 1.0);
  return half4(half3(col), 1.0);
}
"""

@RequiresApi(33)
internal class HhwRuntimeScene(private val dpr: Float) : HhwScene {
    private val shader = RuntimeShader(HHW_AGSL)
    private val paint = Paint().apply { shader = this@HhwRuntimeScene.shader }

    override fun draw(canvas: Canvas, width: Float, height: Float, u: SceneUniforms) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("sun", u.sunX, u.sunY)
        shader.setFloatUniform("heat", u.heat)
        shader.setFloatUniform("dusk", u.dusk)
        shader.setFloatUniform("phase", u.phase)
        shader.setFloatUniform("touch", u.touchX, u.touchY, u.touchOn)
        shader.setFloatUniform("dpr", dpr)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/**
 * API 26–32: no runtime shaders. The same scene from the same numbers, drawn with gradients and paths:
 * the three-stop sky, the sun's disc and two glows, the ridge and the two buttes (flat colour each, the
 * side away from the sun shaded), the sand in perspective, the road with its edge lines and dashes, the
 * mirage as a band of the low sky over the road near the horizon (stronger with the heat), the poles
 * and wires, and the vignette. Left out: the haze's displacement and the finger's shimmer (both need a
 * per-pixel lookup), the scrub and the strata noise, and the film grain.
 */
internal class HhwDrawnScene : HhwScene {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    private fun mixC(pair: IntArray, k: Float): Int = mixRgb(pair[0], pair[1], k)

    override fun draw(canvas: Canvas, width: Float, height: Float, u: SceneUniforms) {
        val dusk = u.dusk
        val hy = height * HORIZON
        val span = height - hy
        val skyLow = mixC(Palette.SKY_LOW, dusk)
        val sun = mixC(Palette.SUN, dusk)

        // the sky
        fill.shader = LinearGradient(
            0f, 0f, 0f, hy,
            intArrayOf(mixC(Palette.SKY_TOP, dusk), mixC(Palette.SKY_MID, dusk), skyLow),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        fill.alpha = 255
        canvas.drawRect(0f, 0f, width, hy + 1f, fill)
        // the sun's two glows (exponential falloffs, approximated by three stops) and its disc
        glow(canvas, u.sunX, u.sunY, width * 0.075f * 4f, sun, 0.55f)
        glow(canvas, u.sunX, u.sunY, width * 0.42f * 3f, sun, 0.18f + 0.22f * dusk)
        fill.shader = null
        fill.color = sun
        canvas.drawCircle(u.sunX, u.sunY, width * 0.046f, fill)

        // the ridge
        path.reset()
        path.moveTo(0f, hy + 1f)
        var x = 0f
        while (x <= width + 2f) {
            val v = x / width
            path.lineTo(x, height * (HORIZON - 0.03f - 0.014f * sin(v * 5.3f + 1.1f) - 0.008f * sin(v * 13.1f + 0.3f)))
            x += 3f
        }
        path.lineTo(width, hy + 1f)
        path.close()
        fill.color = mixRgb(mixC(Palette.RIDGE, dusk), skyLow, 0.35f)
        canvas.drawPath(path, fill)

        // the two buttes, the side away from the sun in shade
        butte(canvas, width, height, hy, 0.185f, 0.07f, 0.15f, 0.07f, 0.11f, u.sunX, dusk)
        butte(canvas, width, height, hy, 0.865f, 0.022f, 0.06f, 0.042f, 0.04f, u.sunX, dusk)

        // the sand: far to near on a square-root ramp, melting into the low sky at the horizon
        fill.shader = LinearGradient(
            0f, hy, 0f, height,
            IntArray(6) { i -> mixRgb(mixC(Palette.SAND_FAR, dusk), mixC(Palette.SAND_NEAR, dusk), sqrt(i / 5f)) },
            floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, hy, width, height, fill)

        // the road: half width 0.95 in world units narrows to the vanishing point
        val cx = width * 0.5f
        val wb = width * 0.62f
        trapezoid(canvas, cx, hy, span, wb, -0.95f, 0.95f, mixC(Palette.ASPHALT, dusk), 255)
        val paintC = mixC(Palette.PAINT, dusk)
        trapezoid(canvas, cx, hy, span, wb, -0.87f, -0.82f, paintC, 217)
        trapezoid(canvas, cx, hy, span, wb, 0.82f, 0.87f, paintC, 217)
        // the dashes: painted where fract(z · 0.35) < 0.5, z = span / dy
        fill.shader = null
        fill.color = paintC
        fill.alpha = 217
        var k = 0
        while (k < 40) {
            val zNear = max(k / 0.35f, 1f)
            val zFar = (k + 0.5f) / 0.35f
            val yNear = hy + span / zNear
            val yFar = hy + span / zFar
            if (yNear - yFar < 0.4f) break
            val hwNear = 0.022f * wb / zNear
            val hwFar = 0.022f * wb / zFar
            path.reset()
            path.moveTo(cx - hwFar, yFar)
            path.lineTo(cx + hwFar, yFar)
            path.lineTo(cx + hwNear, yNear)
            path.lineTo(cx - hwNear, yNear)
            path.close()
            canvas.drawPath(path, fill)
            k += 1
        }
        fill.alpha = 255

        // the far ground melts into the low sky, and the mirage mirrors it over the road near the horizon
        fill.shader = LinearGradient(
            0f, hy, 0f, hy + height * 0.12f,
            intArrayOf(withAlpha(skyLow, 0.62f), withAlpha(skyLow, 0.62f * exp(-1f)), withAlpha(skyLow, 0f)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, hy, width, hy + height * 0.12f, fill)
        if (u.heat > 0.002f) {
            val mirror = mixC(Palette.SKY_MID, dusk)
            fill.shader = LinearGradient(
                0f, hy, 0f, hy + height * 0.05f,
                intArrayOf(withAlpha(mixRgb(skyLow, mirror, 0.35f), 0.75f * u.heat), withAlpha(skyLow, 0f)),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, hy, width, hy + height * 0.05f, fill)
        }
        fill.shader = null

        // the poles, doubling in depth, with two wires between them
        var z = 1.4f
        var px0 = 0f
        var py0 = 0f
        var pb0 = 0f
        val poleC = mixC(Palette.POLE, dusk)
        for (j in 0 until 6) {
            val base = hy + span / z
            val xs = width * 0.5f + 1.55f * wb / z
            val ph = 2.3f * span / z
            val pw = max(0.016f * wb / z, 0.45f)
            val barY = base - ph * 0.92f
            val bw = 0.3f * wb / z
            val bh = max(0.02f * span / z, 0.45f)
            val fog = exp(-(base - hy) / (height * 0.05f))
            val c = mixRgb(poleC, skyLow, 0.75f * fog)
            fill.color = c
            canvas.drawRect(xs - pw, base - ph, xs + pw, base, fill)
            canvas.drawRect(xs - bw, barY - bh, xs + bw, barY + bh, fill)
            if (j > 0) {
                line.color = c
                line.alpha = (0.7f * 255f).toInt()
                line.strokeWidth = 1.1f
                for (side in floatArrayOf(-0.8f, 0.8f)) {
                    val xa = xs + side * bw
                    val xb = px0 + side * pb0
                    val sag = 0.09f * span / z * 4f * 0.25f
                    path.reset()
                    path.moveTo(xa, barY)
                    // a quadratic through the midpoint sagging by `sag`
                    path.quadTo((xa + xb) / 2f, (barY + py0) / 2f + 2f * sag, xb, py0)
                    canvas.drawPath(path, line)
                }
            }
            px0 = xs
            py0 = barY
            pb0 = bw
            z *= 1.9f
        }

        // the vignette
        val r = sqrt((width * 0.5f * 0.9f).pow(2) + (height * 0.5f * 1.1f).pow(2))
        fill.shader = RadialGradient(
            width / 2f, height / 2f, r * 1.6f,
            intArrayOf(0x00000000, 0x00000000, 0x30000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, fill)
        fill.shader = null
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        fill.shader = RadialGradient(
            x, y, r,
            intArrayOf(withAlpha(color, s), withAlpha(color, s * exp(-1.33f)), withAlpha(color, s * exp(-2.66f)), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.33f, 0.66f, 1f), Shader.TileMode.CLAMP,
        )
        fill.alpha = 255
        canvas.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    private fun butte(
        canvas: Canvas, width: Float, height: Float, hy: Float,
        centre: Float, top: Float, base: Float, h: Float, halfW: Float, sunX: Float, dusk: Float,
    ) {
        val mesa = mixC(Palette.MESA, dusk)
        val sandFar = mixC(Palette.SAND_FAR, dusk)
        val left = (centre - base) * width
        val right = (centre + base) * width
        val step = 2f
        path.reset()
        path.moveTo(left, hy + 0.5f)
        var x = left
        while (x <= right) {
            val d = abs(x / width - centre)
            val k = if (d >= base) 0f else if (d <= top) h else h * (1f - ((d - top) / (base - top)).pow(0.55f))
            path.lineTo(x, height * (HORIZON - k))
            x += step
        }
        path.lineTo(right, hy + 0.5f)
        path.close()
        // lit on the sun's side, shaded on the other; the talus a little dustier near the plain
        val cx0 = centre * width
        val dir = if (sunX >= cx0) 1f else -1f
        val litC = scale(mesa, 1.02f)
        val shadeC = scale(mesa, 0.78f)
        val hw = halfW * width
        fill.shader = LinearGradient(
            cx0 - hw * dir, 0f, cx0 + hw * dir, 0f,
            intArrayOf(shadeC, litC), null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, fill)
        fill.shader = LinearGradient(
            0f, height * (HORIZON - h) + height * 0.018f, 0f, hy,
            intArrayOf(withAlpha(mixRgb(sandFar, mesa, 0.5f), 0f), withAlpha(mixRgb(sandFar, mesa, 0.5f), 0.5f)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, fill)
        fill.shader = null
    }

    private fun trapezoid(canvas: Canvas, cx: Float, hy: Float, span: Float, wb: Float, a: Float, b: Float, color: Int, alpha: Int) {
        path.reset()
        path.moveTo(cx + a * wb * 0.0005f, hy + span * 0.0005f)
        path.lineTo(cx + b * wb * 0.0005f, hy + span * 0.0005f)
        path.lineTo(cx + b * wb, hy + span)
        path.lineTo(cx + a * wb, hy + span)
        path.close()
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
        fill.alpha = 255
    }
}

internal fun mixRgb(a: Int, b: Int, k: Float): Int {
    val t = k.coerceIn(0f, 1f)
    val r = ((a shr 16) and 255) + (((b shr 16) and 255) - ((a shr 16) and 255)) * t
    val g = ((a shr 8) and 255) + (((b shr 8) and 255) - ((a shr 8) and 255)) * t
    val bl = (a and 255) + ((b and 255) - (a and 255)) * t
    return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or bl.toInt()
}

private fun withAlpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (c and 0xFFFFFF)

private fun scale(c: Int, k: Float): Int {
    fun ch(v: Int) = (v * k).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch((c shr 16) and 255) shl 16) or (ch((c shr 8) and 255) shl 8) or ch(c and 255)
}

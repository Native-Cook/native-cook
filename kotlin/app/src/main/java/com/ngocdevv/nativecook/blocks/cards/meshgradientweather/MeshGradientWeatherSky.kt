package com.ngocdevv.nativecook.blocks.cards.meshgradientweather

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.blocks.cards.meshgradientweather.MeshGradientWeatherMotion as M
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The sky behind Soft Weather: a 4 x 4 Catmull-Rom mesh gradient in OKLab, and rain on the glass (drizzle
 * streaks, beads, sliding drops and their trails, each drop refracting the mesh through a lens), in one AGSL pass.
 * A port of the React Native `weatherShader.ts` by way of the Swift `.metal` port.
 *
 * Glass cards: the cards are not blurred copies of the screen. They draw the same mesh (without rain) through a
 * tone curve fitted to the Swift reference's Liquid Glass in dark mode (per channel `m * c + n * luma + t`, fitted
 * on dawn, rain and night frames to within 3/255), so a card darkens and keeps the sky's hue like the glass does.
 *
 * API 26–32 have no RuntimeShader: there the same mesh (offsets, palettes and drift, no rain) is evaluated on the
 * CPU into a 27 x 60 bitmap every frame and drawn scaled with bilinear filtering; the glass uses the same curve.
 *
 * Output colour: the Swift build matches its reference with the React Native "Display P3 shown as sRGB" matrix, and
 * that video is the reference here, so the same matrix is applied before encoding.
 */
internal class MeshGradientWeatherSky {
    // The current frame: offsets (32) and OKLab (48) on the screen of `w` x `h` dp.
    private val off = FloatArray(32)
    private val lab = FloatArray(48)
    private var rain = 0f
    private var phase = 0f
    private var w = 1f
    private var h = 1f
    private var dpr = 1f
    private var stamp = Double.NaN

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // API 33+
    private var sky: Any? = null
    private val glass = HashMap<Int, Any>()

    // API 26-32
    private val fbW = 27
    private val fbH = 60
    private var fbSky: Bitmap? = null
    private var fbGlass: Bitmap? = null
    private val fbPixels = IntArray(fbW * fbH)
    private val fbGlassPixels = IntArray(fbW * fbH)
    private val fbPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Computes this frame's uniforms once, however many draws ask for them. */
    fun update(key: Double, hour: Double, amb: Double, rain: Double, widthDp: Float, heightDp: Float, density: Float) {
        if (key == stamp && widthDp == w && heightDp == h) return
        stamp = key
        w = widthDp; h = heightDp; dpr = density
        this.rain = rain.toFloat()
        phase = (amb / M.LOOP).toFloat()
        M.offsets(hour, amb, rain, off)
        M.sky(hour, lab)
        if (Build.VERSION.SDK_INT < 33) renderFallback()
    }

    /** The whole screen. */
    fun drawSky(scope: DrawScope) = scope.drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.scale(dpr, dpr)
        if (Build.VERSION.SDK_INT >= 33) {
            val s = skyShader()
            set(s, rain, 0f, 0f, 0f)
            paint.shader = s
            nc.drawRect(0f, 0f, w, h, paint)
        } else {
            fbSky?.let { nc.drawBitmap(it, Rect(0, 0, fbW, fbH), RectF(0f, 0f, w, h), fbPaint) }
        }
        nc.restore()
    }

    /** A glass card of `cw` x `ch` dp whose top-left is at (`ox`, `oy`) dp on the sky. Clip before calling. */
    fun drawGlass(scope: DrawScope, id: Int, ox: Float, oy: Float, cw: Float, ch: Float) = scope.drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.scale(dpr, dpr)
        if (Build.VERSION.SDK_INT >= 33) {
            val s = glassShader(id)
            set(s, 0f, 1f, ox, oy)
            paint.shader = s
            nc.drawRect(0f, 0f, cw, ch, paint)
        } else {
            fbGlass?.let { nc.drawBitmap(it, Rect(0, 0, fbW, fbH), RectF(-ox, -oy, w - ox, h - oy), fbPaint) }
        }
        nc.restore()
    }

    @RequiresApi(33)
    private fun skyShader(): RuntimeShader = (sky as? RuntimeShader) ?: RuntimeShader(SKY_AGSL).also { sky = it }

    @RequiresApi(33)
    private fun glassShader(id: Int): RuntimeShader =
        (glass[id] as? RuntimeShader) ?: RuntimeShader(SKY_AGSL).also { glass[id] = it }

    @RequiresApi(33)
    private fun set(s: RuntimeShader, rain: Float, glass: Float, ox: Float, oy: Float) {
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("off", off)
        s.setFloatUniform("lab", lab)
        s.setFloatUniform("rain", rain)
        s.setFloatUniform("phase", phase)
        s.setFloatUniform("dpr", dpr)
        s.setFloatUniform("glass", glass)
        s.setFloatUniform("org", ox, oy)
    }

    // ------------------------------------------------------------------ CPU fallback (API 26-32)

    private fun renderFallback() {
        val wx = DoubleArray(4)
        val out = DoubleArray(3)
        for (y in 0 until fbH) for (x in 0 until fbW) {
            val qx = x / (fbW - 1.0)
            val qy = y / (fbH - 1.0)
            // two fixed-point steps of uv <- q - D(uv), as the shader does
            var ux = qx
            var uy = qy
            repeat(2) {
                weights(ux, wx)
                val gy = uy.coerceIn(0.0, 1.0) * 3
                var ax = 0.0
                var ay = 0.0
                for (j in 0 until 4) {
                    val wy = wt(gy, j)
                    for (i in 0 until 4) {
                        val k = (j * 4 + i) * 2
                        ax += wx[i] * wy * off[k]; ay += wx[i] * wy * off[k + 1]
                    }
                }
                ux = qx - ax; uy = qy - ay
            }
            weights(ux, wx)
            val gy = uy.coerceIn(0.0, 1.0) * 3
            out.fill(0.0)
            for (j in 0 until 4) {
                val wy = wt(gy, j)
                for (i in 0 until 4) {
                    val k = (j * 4 + i) * 3
                    val f = wx[i] * wy
                    out[0] += f * lab[k]; out[1] += f * lab[k + 1]; out[2] += f * lab[k + 2]
                }
            }
            val rgb = encode(out)
            fbPixels[y * fbW + x] = argb(rgb[0], rgb[1], rgb[2])
            val luma = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
            fbGlassPixels[y * fbW + x] = argb(
                GM[0] * rgb[0] + GN[0] * luma + GT[0],
                GM[1] * rgb[1] + GN[1] * luma + GT[1],
                GM[2] * rgb[2] + GN[2] * luma + GT[2],
            )
        }
        val s = fbSky ?: Bitmap.createBitmap(fbW, fbH, Bitmap.Config.ARGB_8888).also { fbSky = it }
        val g = fbGlass ?: Bitmap.createBitmap(fbW, fbH, Bitmap.Config.ARGB_8888).also { fbGlass = it }
        s.setPixels(fbPixels, 0, fbW, 0, 0, fbW, fbH)
        g.setPixels(fbGlassPixels, 0, fbW, 0, 0, fbW, fbH)
    }

    private fun cr(v: Double): Double {
        val x = abs(v)
        if (x < 1) return (1.5 * x - 2.5) * x * x + 1
        if (x < 2) return ((-0.5 * x + 2.5) * x - 4) * x + 2
        return 0.0
    }

    private fun wt(g: Double, i: Int): Double {
        var w = cr(g - i)
        if (i == 0) w += cr(g + 1)
        if (i == 3) w += cr(g - 4)
        return w
    }

    private fun weights(u: Double, into: DoubleArray) {
        val g = u.coerceIn(0.0, 1.0) * 3
        for (i in 0 until 4) into[i] = wt(g, i)
    }

    private fun encode(c: DoubleArray): DoubleArray {
        var l = c[0] + 0.3963377774 * c[1] + 0.2158037573 * c[2]
        var m = c[0] - 0.1055613458 * c[1] - 0.0638541728 * c[2]
        var s = c[0] - 0.0894841775 * c[1] - 1.2914855480 * c[2]
        l = l * l * l; m = m * m * m; s = s * s * s
        val r = (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s).coerceIn(0.0, 1.0)
        val g = (-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s).coerceIn(0.0, 1.0)
        val b = (-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s).coerceIn(0.0, 1.0)
        val pr = 0.8225 * r + 0.1774 * g
        val pg = 0.0332 * r + 0.9669 * g
        val pb = 0.0171 * r + 0.0724 * g + 0.9108 * b
        fun e(v: Double) = if (v < 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
        return doubleArrayOf(e(pr), e(pg), e(pb))
    }

    private fun argb(r: Double, g: Double, b: Double): Int {
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    companion object {
        // The glass tone curve, per channel: m * c + n * luma + t (sRGB-encoded 0..1).
        private val GM = doubleArrayOf(0.521, 0.404, 0.566)
        private val GN = doubleArrayOf(-0.262, -0.171, -0.363)
        private val GT = doubleArrayOf(0.087, 0.104, 0.134)

        private const val SKY_AGSL = """
uniform float2 size;
uniform float2 off[16];
uniform float3 lab[16];
uniform float rain;
uniform float phase;
uniform float dpr;
uniform float glass;
uniform float2 org;

const int N = 4;
const float G = 3.0;
const float TAU = 6.2831853;

float cr(float x) {
  x = abs(x);
  if (x < 1.0) return (1.5 * x - 2.5) * x * x + 1.0;
  if (x < 2.0) return ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0;
  return 0.0;
}

float wt(float g, int i) {
  float w = cr(g - float(i));
  if (i == 0) w += cr(g + 1.0);
  if (i == N - 1) w += cr(g - float(N));
  return w;
}

float2 disp(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx0 = wt(g.x, 0); float wx1 = wt(g.x, 1); float wx2 = wt(g.x, 2); float wx3 = wt(g.x, 3);
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    acc += wy * (wx0 * off[j * N] + wx1 * off[j * N + 1] + wx2 * off[j * N + 2] + wx3 * off[j * N + 3]);
  }
  return acc;
}

float3 meshLab(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx0 = wt(g.x, 0); float wx1 = wt(g.x, 1); float wx2 = wt(g.x, 2); float wx3 = wt(g.x, 3);
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    acc += wy * (wx0 * lab[j * N] + wx1 * lab[j * N + 1] + wx2 * lab[j * N + 2] + wx3 * lab[j * N + 3]);
  }
  return acc;
}

float3 meshAt(float2 q) {
  float2 uv = q - disp(q);
  uv = q - disp(uv);
  return meshLab(uv);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float3 encode(float3 lin) {
  lin = clamp(lin, 0.0, 1.0);
  lin = float3(
    dot(lin, float3(0.8225, 0.1774, 0.0)),
    dot(lin, float3(0.0332, 0.9669, 0.0)),
    dot(lin, float3(0.0171, 0.0724, 0.9108)));
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(max(lin, float3(0.0)), float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

float4 lensDrop(float2 p, float2 c, float r, float sy, float pres) {
  float2 d = (p - c) / float2(r, r * sy);
  return float4(d, r, pres);
}

half4 main(float2 local) {
  float2 p = local + org;
  float2 q = p / size;
  float3 lin = labToLinear(meshAt(q));

  if (rain > 0.002) {
    // drizzle behind the glass: thin streaks falling a little slanted, in loops of the clock
    float slant = p.x + p.y * 0.16;
    float col = floor(slant / 6.0);
    float hc = h21(float2(col, 3.7));
    float speed = 3.0 + floor(hc * 3.0);
    float y = p.y / size.y - phase * speed + hc * 7.0;
    float sg = fract(y * 5.0);
    float streak = smoothstep(0.0, 0.04, sg) * (1.0 - smoothstep(0.08, 0.30, sg));
    float across = 1.0 - abs(fract(slant / 6.0) - 0.5) * 2.0;
    streak *= smoothstep(0.55, 1.0, across) * step(0.45, hc);
    lin += float3(streak * 0.055 * rain);

    // drops on the glass: beads that come and go, a few that slide down, and the trail they leave
    float2 cell = float2(30.0, 30.0);
    float2 ci = floor(p / cell);
    float hb = h21(ci);
    float hb2 = h21(ci + 17.3);
    float life = fract(phase * 2.0 + hb * 5.0);
    float vis = smoothstep(0.0, 0.1, life) * (1.0 - smoothstep(0.75, 1.0, life));
    float2 cb = (ci + 0.25 + 0.5 * float2(hb, fract(hb2 * 7.0))) * cell;
    float4 best = lensDrop(p, cb, 2.2 + 3.0 * fract(hb * 13.0), 1.0, vis * step(hb2, 0.6 * rain));

    float colW = 52.0;
    float cx = floor(p.x / colW);
    float hs = h21(float2(cx, 9.1));
    float turns = 1.0 + floor(hs * 2.0);
    float fy = fract(phase * turns + hs * 3.0);
    float sy = -60.0 + fy * (size.y + 120.0);
    float slide = step(0.3, hs) * smoothstep(0.25, 0.6, rain);
    float sx = (cx + 0.3 + 0.4 * hs) * colW + sin(sy * 0.03 + hs * TAU) * 5.0;
    float4 sd = lensDrop(p, float2(sx, sy), 5.0 + 3.0 * fract(hs * 11.0), 1.18, slide);
    if (sd.w > 0.0 && length(sd.xy) < length(best.xy) + 0.6) best = sd;

    float ty = floor(p.y / 13.0);
    float ht = h21(float2(cx, ty));
    float tcy = (ty + 0.5) * 13.0;
    float behind = sy - tcy;
    float tx = (cx + 0.3 + 0.4 * hs) * colW + sin(tcy * 0.03 + hs * TAU) * 5.0 + (ht - 0.5) * 4.0;
    float tr = (1.0 + 1.6 * ht) * (1.0 - clamp(behind / 190.0, 0.0, 1.0));
    float tpres = slide * step(ht, 0.6) * step(12.0, behind) * step(behind, 190.0);
    float4 td = lensDrop(p, float2(tx, tcy), max(tr, 0.4), 1.0, tpres);
    if (td.w > 0.0 && length(td.xy) < length(best.xy)) best = td;

    float len = length(best.xy);
    float r = best.z;
    float cover = (1.0 - smoothstep(1.0 - 1.2 / r, 1.0, len)) * best.w;
    float2 dsh = best.xy - float2(0.0, 0.32);
    float shade = (1.0 - smoothstep(0.9, 1.35, length(dsh))) * best.w * (1.0 - cover);
    lin *= 1.0 - 0.10 * shade;
    if (cover > 0.001) {
      float2 rq = (p - best.xy * r * 7.0) / size;
      float3 seen = labToLinear(meshAt(rq));
      float rim = smoothstep(0.62, 1.0, len);
      float top = clamp(-best.y, 0.0, 1.0);
      float low = clamp(best.y, 0.0, 1.0);
      float3 glassC = seen * (1.1 - 0.42 * rim * top) + float3(0.10 * smoothstep(0.35, 0.95, len) * low);
      float2 hl = best.xy - float2(-0.34, -0.44);
      glassC += float3(0.75 * exp(-dot(hl, hl) * 38.0));
      lin = mix(lin, glassC, cover);
    }
  }

  float3 rgb = encode(lin);
  if (glass > 0.5) {
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb = float3(0.521, 0.404, 0.566) * rgb + float3(-0.262, -0.171, -0.363) * luma + float3(0.087, 0.104, 0.134);
  }
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(px)) - 0.5;
  rgb += n / 255.0 + g * 0.022;
  return half4(half3(rgb), 1.0);
}
"""
    }
}

package com.ngocdevv.nativecook.blocks.onboarding.meshgradientbreathe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

// The light behind Breathe Mesh: a 5 x 5 Catmull-Rom mesh gradient in OKLab whose control points move, and the
// glass orb that guides the breath, in one AGSL pass (API 33+). Copied from the React Native breatheShader.ts;
// like the Swift port it always applies the P3 matrix the reference video was measured against. Coordinates are
// in pixels here (`ppt` = pixels per point for the orb's anti-aliased edge).
//
// Below API 33 MgbFallback draws the same mesh on the CPU into a small bitmap (1/8 of the screen, bilinear
// upscaled: the field is soft, so it reads the same), and the orb from radial gradients over a magnified copy
// of that bitmap.

internal const val MGB_AGSL = """
uniform float2 size;
uniform float2 off[25];
uniform float3 lab[25];
uniform float3 orb;
uniform float lift;
uniform float ppt;

const int N = 5;
const float G = 4.0;

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
  float wx0 = wt(g.x, 0); float wx1 = wt(g.x, 1); float wx2 = wt(g.x, 2); float wx3 = wt(g.x, 3); float wx4 = wt(g.x, 4);
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    acc += wy * (wx0 * off[j * 5] + wx1 * off[j * 5 + 1] + wx2 * off[j * 5 + 2] + wx3 * off[j * 5 + 3] + wx4 * off[j * 5 + 4]);
  }
  return acc;
}

float3 meshLab(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx0 = wt(g.x, 0); float wx1 = wt(g.x, 1); float wx2 = wt(g.x, 2); float wx3 = wt(g.x, 3); float wx4 = wt(g.x, 4);
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    acc += wy * (wx0 * lab[j * 5] + wx1 * lab[j * 5 + 1] + wx2 * lab[j * 5 + 2] + wx3 * lab[j * 5 + 3] + wx4 * lab[j * 5 + 4]);
  }
  return acc;
}

float3 mesh(float2 q) {
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
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

half4 main(float2 p) {
  float2 q = p / size;
  float3 lin = labToLinear(mesh(q));

  float2 d = (p - orb.xy) / orb.z;
  float len = length(d);
  float2 ds = (p - orb.xy - float2(0.0, orb.z * 0.18)) / (orb.z * 1.25);
  float sh = 1.0 - smoothstep(0.55, 1.15, length(ds));
  lin *= 1.0 - 0.07 * sh;
  lin += float3(0.05 + 0.05 * lift) * exp(-max(len - 1.0, 0.0) * 5.0) * step(1.0, len);

  if (len < 1.02) {
    float z = sqrt(max(1.0 - len * len, 0.0));
    float2 sp = orb.xy + (p - orb.xy) * (0.42 + 0.38 * len * len) - float2(0.0, orb.z * 0.10);
    float3 seen = labToLinear(mesh(sp / size));
    float fres = pow(1.0 - z, 3.0);
    float3 glass = seen * (1.04 + 0.05 * lift);
    glass = mix(glass, float3(1.0), fres * (0.30 + 0.20 * lift));
    glass *= 1.0 - 0.10 * smoothstep(0.2, 1.0, d.y) * smoothstep(0.4, 1.0, len);
    float2 hl = d - float2(-0.36, -0.46);
    glass += float3(0.42 * exp(-dot(hl, hl) * 20.0));
    float2 hl2 = d - float2(0.42, 0.52);
    glass += float3(0.10 * exp(-dot(hl2, hl2) * 30.0));
    float aa = 1.0 - smoothstep(1.0 - 1.5 * ppt / orb.z, 1.0, len);
    lin = mix(lin, glass, aa);
  }

  float3 rgb = encode(lin);
  float n = ign(p) + ign(p + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(p)) - 0.5;
  rgb += n / 255.0 + g * 0.02;
  return half4(half3(rgb), 1.0);
}
"""

/** The same picture without a runtime shader (API 26-32). */
internal class MgbFallback {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val clip = Path()
    private val wx = DoubleArray(5)

    fun draw(c: Canvas, w: Float, h: Float, off: FloatArray, lab: FloatArray, ox: Float, oy: Float, or: Float, lift: Float) {
        val bw = ceil(w / 8f).toInt().coerceAtLeast(2)
        val bh = ceil(h / 8f).toInt().coerceAtLeast(2)
        var bmp = bitmap
        if (bmp == null || bmp.width != bw || bmp.height != bh) {
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bitmap = bmp
            pixels = IntArray(bw * bh)
        }
        for (y in 0 until bh) for (x in 0 until bw) {
            pixels[y * bw + x] = colour((x + 0.5) / bw, (y + 0.5) / bh, off, lab)
        }
        bmp!!.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        val sx = w / bw
        val sy = h / bh
        matrix.setScale(sx, sy)
        paint.alpha = 255
        c.drawBitmap(bmp, matrix, paint)

        // the orb's soft shadow a little below it, and the light it gathers around itself
        fill.shader = RadialGradient(ox, oy + or * 0.18f, or * 1.25f * 1.15f,
            intArrayOf(0x14000000, 0x14000000, 0x00000000), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ox, oy + or * 0.18f, or * 1.25f * 1.15f, fill)
        val glowA = ((0.05f + 0.05f * lift) * 255).toInt()
        fill.shader = RadialGradient(ox, oy, or * 1.6f,
            intArrayOf(glowA shl 24 or 0xFFFFFF, glowA shl 24 or 0xFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.625f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ox, oy, or * 1.6f, fill)

        // the glass: a magnified copy of the light, a bright rim and two highlights
        c.save()
        clip.reset()
        clip.addCircle(ox, oy, or, Path.Direction.CW)
        c.clipPath(clip)
        val k = 0.6f
        matrix.setScale(sx, sy)
        matrix.postTranslate(-ox, -oy + or * 0.10f)
        matrix.postScale(1f / k, 1f / k)
        matrix.postTranslate(ox, oy)
        c.drawBitmap(bmp, matrix, paint)
        val rimA = ((0.30f + 0.20f * lift) * 255).toInt()
        fill.shader = RadialGradient(ox, oy, or,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, (rimA / 8) shl 24 or 0xFFFFFF, (rimA * 7 / 10) shl 24 or 0xFFFFFF),
            floatArrayOf(0f, 0.7f, 0.9f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ox, oy, or, fill)
        fill.shader = RadialGradient(ox - or * 0.36f, oy - or * 0.46f, or * 0.45f,
            intArrayOf(0xB0FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(ox - or * 0.36f, oy - or * 0.46f, or * 0.45f, fill)
        fill.shader = RadialGradient(ox + or * 0.42f, oy + or * 0.52f, or * 0.38f,
            intArrayOf(0x1AFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(ox + or * 0.42f, oy + or * 0.52f, or * 0.38f, fill)
        c.restore()
    }

    private fun cr(x0: Double): Double {
        val x = abs(x0)
        if (x < 1) return (1.5 * x - 2.5) * x * x + 1
        if (x < 2) return ((-0.5 * x + 2.5) * x - 4) * x + 2
        return 0.0
    }

    private fun wt(g: Double, i: Int): Double {
        var w = cr(g - i)
        if (i == 0) w += cr(g + 1)
        if (i == 4) w += cr(g - 5)
        return w
    }

    private fun weights(u: Double) {
        val g = u.coerceIn(0.0, 1.0) * 4
        for (i in 0 until 5) wx[i] = wt(g, i)
    }

    private fun colour(qx: Double, qy: Double, off: FloatArray, lab: FloatArray): Int {
        var ux = qx
        var uy = qy
        repeat(2) {
            weights(ux)
            val gy = uy.coerceIn(0.0, 1.0) * 4
            var ax = 0.0
            var ay = 0.0
            for (j in 0 until 5) {
                val wy = wt(gy, j)
                for (i in 0 until 5) {
                    val f = wx[i] * wy
                    ax += f * off[(j * 5 + i) * 2]
                    ay += f * off[(j * 5 + i) * 2 + 1]
                }
            }
            ux = qx - ax
            uy = qy - ay
        }
        weights(ux)
        val gy = uy.coerceIn(0.0, 1.0) * 4
        var l = 0.0
        var a = 0.0
        var b = 0.0
        for (j in 0 until 5) {
            val wy = wt(gy, j)
            for (i in 0 until 5) {
                val f = wx[i] * wy
                val k = (j * 5 + i) * 3
                l += f * lab[k]
                a += f * lab[k + 1]
                b += f * lab[k + 2]
            }
        }
        var lm = l + 0.3963377774 * a + 0.2158037573 * b
        var mm = l - 0.1055613458 * a - 0.0638541728 * b
        var sm = l - 0.0894841775 * a - 1.2914855480 * b
        lm *= lm * lm
        mm *= mm * mm
        sm *= sm * sm
        val r0 = (4.0767416621 * lm - 3.3077115913 * mm + 0.2309699292 * sm).coerceIn(0.0, 1.0)
        val g0 = (-1.2684380046 * lm + 2.6097574011 * mm - 0.3413193965 * sm).coerceIn(0.0, 1.0)
        val b0 = (-0.0041960863 * lm - 0.7034186147 * mm + 1.7076147010 * sm).coerceIn(0.0, 1.0)
        val r = enc(0.8225 * r0 + 0.1774 * g0)
        val g = enc(0.0332 * r0 + 0.9669 * g0)
        val bb = enc(0.0171 * r0 + 0.0724 * g0 + 0.9108 * b0)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bb
    }

    private fun enc(v: Double): Int {
        val e = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
        return (e * 255 + 0.5).toInt().coerceIn(0, 255)
    }
}

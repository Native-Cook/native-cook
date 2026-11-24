// Louver Theme Switch — the turn: one AGSL shader over the two snapshots (API 33+), ported line for
// line from lts-shader.ts, and a drawn version for API 26–32.
//
// The screen is cut into Louver.SLATS horizontal slats. Slat i turns by θ around its long axis, at
// y0 = (i + ½)·h. A point s along the slat sits at (X, y0 + s·cos θ, s·sin θ), seen by a camera D away
// from the screen's centre (cx, cy). Inverting that projection for a pixel (px, py):
//   s = D (py − y0) / (D cos θ + (py − cy) sin θ)      k = D / (D − s sin θ)      X = cx + (px − cx) / k
// The face towards the camera shows the light page at (X, y0 + s), or the dark page at (X, y0 − s),
// so at θ = π the dark page reads the right way up. A slat can overhang its band in perspective, so
// each pixel asks its own slat and both neighbours and paints them far to near, over a dark cavity.
// At rest the effect returns the page itself, so swapping it for the live view shows no change.
package com.ngocdevv.nativecook.blocks.navigation.louverthemeswitch

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private fun f(v: Float) = String.format(java.util.Locale.US, "%.4f", v)

/** what shows between the slats, a blue-black (sRGB) */
private const val CAVITY_R = 0.055f
private const val CAVITY_G = 0.059f
private const val CAVITY_B = 0.082f

private val LTS_AGSL = """
uniform shader lightPage;
uniform shader darkPage;
uniform float2 res;       // dp
uniform float2 turn;      // amount 0…1, direction ±1
uniform float px;         // device pixels per dp
layout(color) uniform half4 cavityTone;

const float PI_ = 3.14159265;

float easeBack(float u) {
  float c = ${f(Louver.BACK)};
  float v = 2.0 * u;
  if (u < 0.5) { return 0.5 * v * v * ((c + 1.0) * v - c); }
  float w = v - 2.0;
  return 0.5 * (w * w * ((c + 1.0) * w + c) + 2.0);
}

float angleOf(float k) {
  float p = turn.y > 0.0 ? turn.x : 1.0 - turn.x;
  float e = easeBack(clamp(p * ${f(1 + Louver.SPREAD)} - ${f(Louver.SPREAD)} * k, 0.0, 1.0));
  return PI_ * (turn.y > 0.0 ? e : 1.0 - e);
}

// slat i at pixel p: colour with coverage in alpha, and how near the camera it is there
float4 slatAt(float i, float2 p, out float depth) {
  depth = -1.0e6;
  float count = ${f(Louver.SLATS.toFloat())};
  if (i < 0.0 || i > count - 1.0) { return float4(0.0); }
  float h = res.y / count;
  float y0 = (i + 0.5) * h;
  float th = angleOf(i / (count - 1.0));
  float sn = sin(th);
  float cs = cos(th);
  float cx = res.x * 0.5;
  float cy = res.y * 0.5;
  float cam = res.y * ${f(Louver.CAMERA)};
  float denom = cam * cs + (p.y - cy) * sn;
  if (abs(denom) < 0.001) { return float4(0.0); }
  float s = cam * (p.y - y0) / denom;
  float reach = h * 0.5 + ${f(Louver.OVERLAP)};
  float slope = abs(cam * (cam * cs + (y0 - cy) * sn) / (denom * denom));
  float cover = clamp((reach - abs(s)) / max(slope, 0.001) * px + 0.5, 0.0, 1.0);
  if (cover <= 0.0) { return float4(0.0); }
  float k = cam / (cam - s * sn);
  float wx = cx + (p.x - cx) / k;
  cover *= clamp(min(wx, res.x - wx) * px + 0.5, 0.0, 1.0);
  if (cover <= 0.0) { return float4(0.0); }
  depth = s * sn;

  float3 normal = float3(0.0, -sn, cs);
  float3 toEye = float3(cx - wx, cy - (y0 + s * cs), cam - s * sn);
  bool faceUp = dot(normal, toEye) > 0.0;
  if (!faceUp) { normal = -normal; }
  float2 uv = clamp(float2(wx, faceUp ? y0 + s : y0 - s), float2(0.5), res - float2(0.5));
  float3 col = faceUp ? float3(lightPage.eval(uv).rgb) : float3(darkPage.eval(uv).rgb);

  float3 sun = normalize(float3(0.0, ${f(Louver.LIGHT_Y)}, ${f(Louver.LIGHT_Z)}));
  float lit = max(dot(normal, sun), 0.0) / sun.z;
  float turned = sn * sn;
  col *= min(${f(Louver.AMBIENT)} + ${f(1 - Louver.AMBIENT)} * lit, 1.0);
  float3 mid = normalize(sun + float3(0.0, 0.0, 1.0));
  col += pow(max(dot(normal, mid), 0.0), ${f(Louver.GLOSS_POWER)}) * ${f(Louver.GLOSS)} * turned;
  float rim = reach - abs(s);
  col += smoothstep(${f(Louver.EDGE_WIDTH)}, 0.0, rim) * ${f(Louver.EDGE)} * abs(sn);
  col *= 1.0 - ${f(Louver.CONTACT)} * exp(-max(rim, 0.0) / ${f(Louver.CONTACT_FOLD)}) * clamp(abs(sn) * 1.4, 0.0, 1.0);
  return float4(clamp(col, 0.0, 1.0), cover);
}

half4 main(float2 p) {
  if (turn.x <= ${f(REST.toFloat())}) { return lightPage.eval(p); }
  if (turn.x >= ${f(1 - REST.toFloat())}) { return darkPage.eval(p); }
  float band = floor(p.y / (res.y / ${f(Louver.SLATS.toFloat())}));
  float da; float db; float dc;
  float4 a = slatAt(band - 1.0, p, da);
  float4 b = slatAt(band, p, db);
  float4 c = slatAt(band + 1.0, p, dc);
  // far to near
  if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
  if (db > dc) { float4 q = b; b = c; c = q; float r = db; db = dc; dc = r; }
  if (da > db) { float4 q = a; a = b; b = q; float r = da; da = db; db = r; }
  float shade = 0.72 + 0.28 * abs(p.y / res.y - 0.5) * 2.0;
  float3 col = float3(cavityTone.rgb) * (1.2 - 0.4 * shade);
  col = mix(col, a.rgb, a.a);
  col = mix(col, b.rgb, b.a);
  col = mix(col, c.rgb, c.a);
  return half4(half3(col), 1.0);
}
"""

/** what draws the turn; coordinates in dp, the canvas already scaled to them */
internal interface LtsTurn {
    fun draw(canvas: Canvas, width: Float, height: Float, amount: Float, dir: Float)
}

@RequiresApi(33)
internal class LtsRuntimeTurn(light: Bitmap, dark: Bitmap, private val scale: Float) : LtsTurn {
    private val shader = RuntimeShader(LTS_AGSL)
    private val paint = Paint()

    init {
        val toDp = Matrix().apply { setScale(1f / scale, 1f / scale) }
        for ((name, page) in listOf("lightPage" to light, "darkPage" to dark)) {
            val image = BitmapShader(page, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            image.filterMode = BitmapShader.FILTER_MODE_LINEAR
            image.setLocalMatrix(toDp)
            shader.setInputShader(name, image)
        }
        shader.setColorUniform("cavityTone", Color.valueOf(CAVITY_R, CAVITY_G, CAVITY_B))
        shader.setFloatUniform("px", scale)
        paint.shader = shader
    }

    override fun draw(canvas: Canvas, width: Float, height: Float, amount: Float, dir: Float) {
        shader.setFloatUniform("res", width, height)
        shader.setFloatUniform("turn", amount, dir)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/**
 * API 26–32: no runtime shaders. A flat slat seen in perspective is a projective image of its band, so
 * each slat is its band of the right page mapped onto its projected quad with Matrix.setPolyToPoly
 * (the same geometry as the shader), drawn bottom to top over the cavity, then darkened by the same
 * lighting (one value per slat, as its face is flat), with the highlight and the contact shadow as a
 * gradient in the slat's own space. Left out: the thin lit edge, and per-pixel ordering where a slat
 * turned past edge-on overhangs its upper neighbour (drawn under it here).
 */
internal class LtsDrawnTurn(light: Bitmap, dark: Bitmap, scale: Float) : LtsTurn {
    private val toDp = Matrix().apply { setScale(1f / scale, 1f / scale) }
    // one shader per slat and face: a shader's local matrix is not copied into a recorded draw, so
    // two draws in one frame must not share one
    private val lightShaders = Array(Louver.SLATS + 1) { BitmapShader(light, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val darkShaders = Array(Louver.SLATS + 1) { BitmapShader(dark, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val contactShaders = arrayOfNulls<LinearGradient>(Louver.SLATS)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contact = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cavity = Paint()
    private val quad = Path()
    private val map = Matrix()
    private val local = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private var cavityHeight = -1f
    private var contactReach = -1f
    private val sunY: Float
    private val sunZ: Float
    private val midY: Float
    private val midZ: Float

    init {
        val l = sqrt(Louver.LIGHT_Y * Louver.LIGHT_Y + Louver.LIGHT_Z * Louver.LIGHT_Z)
        sunY = Louver.LIGHT_Y / l
        sunZ = Louver.LIGHT_Z / l
        val m = sqrt(sunY * sunY + (sunZ + 1f) * (sunZ + 1f))
        midY = sunY / m
        midZ = (sunZ + 1f) / m
    }

    private fun tone(k: Float) = Color.rgb(
        (CAVITY_R * k * 255f).coerceIn(0f, 255f).toInt(),
        (CAVITY_G * k * 255f).coerceIn(0f, 255f).toInt(),
        (CAVITY_B * k * 255f).coerceIn(0f, 255f).toInt(),
    )

    override fun draw(canvas: Canvas, width: Float, height: Float, amount: Float, dir: Float) {
        if (amount <= REST) {
            face.shader = lightShaders[Louver.SLATS].also { it.setLocalMatrix(toDp) }
            canvas.drawRect(0f, 0f, width, height, face)
            return
        }
        if (amount >= 1 - REST) {
            face.shader = darkShaders[Louver.SLATS].also { it.setLocalMatrix(toDp) }
            canvas.drawRect(0f, 0f, width, height, face)
            return
        }
        if (cavityHeight != height) {
            cavityHeight = height
            // (1.2 − 0.4 shade), shade = 0.72 + 0.56 |y/h − ½|: linear in |y − ½|, so three stops are exact
            cavity.shader = LinearGradient(
                0f, 0f, 0f, height, intArrayOf(tone(0.8f), tone(0.912f), tone(0.8f)), floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width, height, cavity)

        val count = Louver.SLATS
        val h = height / count
        val reach = h * 0.5f + Louver.OVERLAP
        val cx = width * 0.5f
        val cy = height * 0.5f
        val cam = height * Louver.CAMERA
        if (contactReach != reach) {
            contactReach = reach
            for (n in contactShaders.indices) contactShaders[n] = contactGradient(reach)
        }
        for (i in count - 1 downTo 0) {
            val y0 = (i + 0.5f) * h
            val th = slatAngle(amount.toDouble(), dir.toDouble(), i)
            val sn = sin(th).toFloat()
            val cs = cos(th).toFloat()
            // the slat's four corners, s = −reach and +reach, at X = 0 and X = width
            var ok = true
            for (corner in 0 until 4) {
                val s = if (corner < 2) -reach else reach
                val x = if (corner == 0 || corner == 3) 0f else width
                val depth = cam - s * sn
                if (depth <= 1f) ok = false
                val k = cam / depth
                dst[corner * 2] = cx + (x - cx) * k
                dst[corner * 2 + 1] = cy + (y0 + s * cs - cy) * k
            }
            if (!ok) continue
            // which face the camera sees, from the slat's middle
            val faceUp = -sn * (cy - y0) + cs * cam > 0f
            val top = if (faceUp) y0 - reach else y0 + reach
            val bottom = if (faceUp) y0 + reach else y0 - reach
            src[0] = 0f; src[1] = top
            src[2] = width; src[3] = top
            src[4] = width; src[5] = bottom
            src[6] = 0f; src[7] = bottom
            if (!map.setPolyToPoly(src, 0, dst, 0, 4)) continue
            quad.rewind()
            quad.moveTo(dst[0], dst[1])
            quad.lineTo(dst[2], dst[3])
            quad.lineTo(dst[4], dst[5])
            quad.lineTo(dst[6], dst[7])
            quad.close()

            val image = if (faceUp) lightShaders[i] else darkShaders[i]
            local.set(toDp)
            local.postConcat(map)
            image.setLocalMatrix(local)
            face.shader = image
            canvas.drawPath(quad, face)

            // the face is flat, so its light is one number
            var ny = -sn
            var nz = cs
            if (!faceUp) {
                ny = -ny
                nz = -nz
            }
            val lit = max(ny * sunY + nz * sunZ, 0f) / sunZ
            val keep = min(Louver.AMBIENT + (1 - Louver.AMBIENT) * lit, 1f)
            if (keep < 0.999f) {
                shade.color = Color.argb(((1f - keep) * 255f).toInt(), 0, 0, 0)
                canvas.drawPath(quad, shade)
            }
            val gloss = max(ny * midY + nz * midZ, 0f).pow(Louver.GLOSS_POWER) * Louver.GLOSS * sn * sn
            if (gloss > 0.002f) {
                shade.color = Color.argb((gloss * 255f).toInt(), 255, 255, 255)
                canvas.drawPath(quad, shade)
            }
            val strength = (abs(sn) * 1.4f).coerceIn(0f, 1f)
            if (strength > 0.01f) {
                contactShaders[i]?.let {
                    // the gradient is laid out across the band from y0 − reach, in the slat's own space
                    local.reset()
                    local.setTranslate(0f, y0 - reach)
                    if (!faceUp) local.postScale(1f, -1f, 0f, y0)
                    local.postConcat(map)
                    it.setLocalMatrix(local)
                    contact.shader = it
                    contact.alpha = (strength * 255f).toInt()
                    canvas.drawPath(quad, contact)
                }
            }
        }
    }

    /** black whose alpha is CONTACT·exp(−rim / fold), rim the distance from the nearer long edge */
    private fun contactGradient(reach: Float): LinearGradient {
        val stops = 13
        val colors = IntArray(stops)
        val positions = FloatArray(stops)
        for (n in 0 until stops) {
            val u = n / (stops - 1f)
            // denser stops near the edges, where the shadow lives
            val e = if (u < 0.5f) 0.5f * (2f * u).pow(2f) else 1f - 0.5f * (2f * (1f - u)).pow(2f)
            val s = -reach + 2f * reach * e
            val rim = reach - abs(s)
            val a = Louver.CONTACT * exp(-max(rim, 0f) / Louver.CONTACT_FOLD)
            colors[n] = Color.argb((a * 255f).toInt(), 0, 0, 0)
            positions[n] = e
        }
        return LinearGradient(0f, 0f, 0f, 2f * reach, colors, positions, Shader.TileMode.CLAMP)
    }
}

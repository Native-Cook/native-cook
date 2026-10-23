package com.ngocdevv.nativecook.blocks.media.orbitphotoring

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.sqrt

/*
 * The Tumble style's camera: a soft, glossy toy-like body ray-marched in an AGSL runtime shader so
 * it can turn over freely in true 3D. A glossy shell on top with a carry hump and two dark knobs, a
 * satin body below with a pale base, a deep knurled lens barrel holding a matte white sphere, an
 * accent dot and a small tab on the front. It uses the same pinhole camera as the rest of the scene
 * (focal length and centre in dp), so the prints sort in front of and behind it. A port of the React
 * Native Skia shader in OrbitPhotoRingToy.ts; every number matches it. Android 13+ only: below that
 * the scene draws the box camera in the toy's colours instead (see drawAnyCamera).
 */

// Edge softness in dp: how far outside the silhouette a pixel still takes some colour.
private const val TOY_EDGE = 0.75f

private const val TOY_AGSL = """
uniform float2 center;
uniform float focal;
uniform float3 origin;
uniform float3 rx;
uniform float3 ry;
uniform float3 rz;
uniform float3 dims;
uniform float4 lens;
uniform float3 hump;
uniform float3 shellTone;
uniform float3 bodyTone;
uniform float3 accentTone;
uniform float bound;
uniform float flash;
uniform float alpha;
uniform float edge;

float roundBox(float3 p, float3 b, float r) {
  float3 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float cylZ(float3 p, float r, float h) {
  float2 d = float2(length(p.xy) - r, abs(p.z) - h);
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float cylY(float3 p, float r, float h) {
  float2 d = float2(length(p.xz) - r, abs(p.y) - h);
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float2 pick(float2 a, float2 b) {
  return a.x < b.x ? a : b;
}

// Materials: 1 glossy shell, 2 satin body (its underside a pale base plate), 4 black rubber,
// 5 white sphere, 6 accent.
float2 map(float3 p) {
  float hx = dims.x * 0.5;
  float hy = dims.y * 0.5;
  float hz = dims.z * 0.5;
  float sh = hump.z;
  float r = min(16.0, hz * 0.42);
  float2 res = float2(roundBox(p - float3(0.0, -hy + sh * 0.5, 0.0), float3(hx + 2.5, sh * 0.5, hz + 2.5), min(r, sh * 0.48)), 1.0);
  float top = -hy + sh - 4.0;
  float bh = (hy - top) * 0.5;
  res = pick(res, float2(roundBox(p - float3(0.0, top + bh, 0.0), float3(hx, bh, hz), r), 2.0));
  // The tab and the accent dot on the front of the body.
  float fy = -hy + sh + (dims.y - sh) * 0.3;
  res = pick(res, float2(roundBox(p - float3(-hx * 0.72, fy, -hz - 0.6), float3(dims.x * 0.045, 2.8, 2.0), 1.8), 1.0));
  res = pick(res, float2(length(p - float3(hx * 0.56, fy, -hz + 1.0)) - dims.x * 0.03, 6.0));
  // The carry hump: a rounded trapezoid on the shell with a slot through it.
  if (hump.y > 1.0) {
    float3 q = p - float3(-hx * 0.06, -hy - hump.y * 0.5 + 3.0, hz * 0.05);
    float taper = 1.0 - 0.28 * clamp(0.5 - q.y / hump.y, 0.0, 1.0);
    float3 qt = float3(q.x / taper, q.y, q.z);
    float dh = roundBox(qt, float3(hump.x * 0.5, hump.y * 0.5 + 3.0, hz * 0.38), min(6.0, hump.y * 0.45)) * taper;
    float slot = roundBox(q - float3(0.0, -hump.y * 0.08, 0.0), float3(hump.x * 0.16, hump.y * 0.16, hz), 1.5);
    res = pick(res, float2(max(dh, -slot), 1.0));
  }
  // Two dark knobs on the shell.
  float kr = dims.x * 0.045;
  res = pick(res, float2(cylY(p - float3(-hx * 0.64, -hy - 3.5, 0.0), kr, 5.0) - 1.0, 4.0));
  res = pick(res, float2(cylY(p - float3(hx * 0.6, -hy - 3.0, -hz * 0.1), kr * 0.85, 4.5) - 1.0, 4.0));
  // The lens, along -z from the front: a finely ribbed back ring, a deep knurled grip, a hollow
  // rim and the white sphere sitting in it. Its detail is only worked out near it.
  float rb = lens.z;
  float len = lens.w;
  float3 l = p - float3(lens.x, lens.y, -hz);
  float near = cylZ(l - float3(0.0, 0.0, -len * 0.5), rb + 2.0, len * 0.5 + 1.0);
  if (near < res.x + 2.0) {
    float a = atan(l.y, l.x);
    float fine = 0.5 + 0.5 * cos(a * 54.0);
    float back = cylZ(l - float3(0.0, 0.0, -len * 0.16), rb * 0.9 - 0.7 * fine, len * 0.16 + 1.0) - 1.0;
    float grip = 0.5 + 0.5 * cos(a * 22.0);
    float front = cylZ(l - float3(0.0, 0.0, -len * 0.64), rb - 2.6 * smoothstep(0.35, 0.9, grip) - 1.5, len * 0.36 - 1.5) - 1.5;
    front = max(front, -cylZ(l - float3(0.0, 0.0, -len), rb * 0.8, 4.0));
    res = pick(res, float2(min(back, front) * 0.85, 4.0));
    float rs = rb * 0.72;
    res = pick(res, float2(length(l - float3(0.0, 0.0, -len + rs * 0.42)) - rs, 5.0));
  }
  return res;
}

float3 normalAt(float3 p) {
  float e = 0.25;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy * map(p + k.xyy * e).x + k.yyx * map(p + k.yyx * e).x + k.yxy * map(p + k.yxy * e).x + k.xxx * map(p + k.xxx * e).x);
}

// A studio: a wide softbox above, a tall strip on the left, dark everywhere else.
float studio(float3 r) {
  float above = 1.0 - smoothstep(-0.85, -0.25, r.y);
  float left = (1.0 - smoothstep(-0.92, -0.5, r.x)) * (1.0 - smoothstep(0.15, 0.65, abs(r.y)));
  return 0.03 + above + 0.55 * left;
}

half4 main(float2 xy) {
  float3 eyeDir = normalize(float3((xy - center) / focal, 1.0));
  float3 rd = float3(dot(rx, eyeDir), dot(ry, eyeDir), dot(rz, eyeDir));
  float b = dot(origin, rd);
  float c = dot(origin, origin) - bound * bound;
  float disc = b * b - c;
  if (disc < 0.0) return half4(0.0);
  disc = sqrt(disc);
  float t = max(-b - disc, 0.0);
  float tEnd = -b + disc;
  float best = 1e9;
  float bestT = t;
  for (int i = 0; i < 96; i++) {
    float d = map(origin + rd * t).x;
    float ratio = d * focal / max(t, 1.0);
    if (ratio < best) {
      best = ratio;
      bestT = t;
    }
    if (ratio < 0.05) break;
    t += d * 0.9;
    if (t > tEnd) break;
  }
  if (best > edge) return half4(0.0);
  float cover = clamp(1.0 - best / edge, 0.0, 1.0);
  if (best < 0.05) cover = 1.0;

  float3 p = origin + rd * bestT;
  float mat = map(p).y;
  float3 n = normalAt(p);
  // Back to the eye's space for the light.
  float3 N = normalize(rx * n.x + ry * n.y + rz * n.z);
  float3 V = -eyeDir;
  float3 R = reflect(eyeDir, N);
  float3 key = normalize(float3(-0.5, -0.8, -0.45));
  float3 fillL = normalize(float3(0.75, -0.1, -0.6));
  float diff = max(dot(N, key), 0.0);
  float fill = max(dot(N, fillL), 0.0);
  float3 H = normalize(key + V);
  float nh = max(dot(N, H), 0.0);
  float fres = pow(1.0 - max(dot(N, V), 0.0), 4.0);
  float env = studio(R);

  float occ = 0.0;
  float weight = 1.0;
  for (int i = 0; i < 4; i++) {
    float h = 1.5 + 5.0 * float(i);
    occ += (h - map(p + n * h).x) * weight;
    weight *= 0.65;
  }
  float ao = clamp(1.0 - occ * 0.035, 0.3, 1.0);

  float3 col;
  if (mat < 1.5) {
    float3 base = shellTone;
    col = base * (0.07 + 0.42 * diff + 0.1 * fill) + base * 1.9 * env * 0.6 + mix(base, float3(1.0, 0.75, 0.55), 0.5) * pow(nh, 70.0) * 0.9 + fres * 0.08;
  } else if (mat < 2.5) {
    // The underside is a pale plate, blending in over the rounded bottom edge.
    float plate = smoothstep(0.55, 0.9, n.y);
    float3 base = mix(bodyTone, float3(0.86, 0.85, 0.82), plate);
    col = base * (0.2 + 0.85 * diff + 0.22 * fill + 0.25 * plate) + env * (0.035 + 0.22 * fres + 0.1 * plate) + pow(nh, 26.0) * (0.1 + 0.15 * plate);
  } else if (mat < 4.5) {
    float3 base = float3(0.075, 0.075, 0.085);
    col = base * (0.7 + 1.6 * diff + 0.5 * fill) + env * 0.06 * (1.0 + 3.0 * fres) + pow(nh, 22.0) * 0.16;
  } else if (mat < 5.5) {
    float3 base = float3(0.92, 0.91, 0.88);
    float wrap = clamp((dot(N, key) + 0.5) / 1.5, 0.0, 1.0);
    col = base * (0.34 + 0.72 * wrap) * (0.96 + 0.08 * fill) + pow(nh, 14.0) * 0.06 + flash * 0.6;
  } else {
    float3 base = accentTone;
    col = base * (0.25 + 0.75 * diff) + env * base * 0.3 + pow(nh, 90.0) * 0.9;
  }
  col = clamp(col * ao, 0.0, 1.0);
  float a = cover * alpha;
  return half4(half3(col * a), half(a));
}
"""

/** The toy's own measures and colours for a camera vector, in dp of the design and 0…1 RGB. */
internal class OrbitToyShape(v: FloatArray) {
    val width = v[V_W]
    val height = max(v[V_H], width * 0.6f)
    val depth = max(v[V_D], width * 0.42f)
    val shell = height * max(0.3f, v[V_PLATE] * 1.1f)
    val humpWidth = v[V_HUMP_W] * 0.72f
    val humpHeight = v[V_HUMP_H] * 0.7f
    val lensX = v[V_LENS_X] * 0.8f - width * 0.08f
    val lensY = height * 0.17f
    val lensRadius = v[V_LENS_R] * 1.35f
    val lensLength = max(v[V_LENS_L] * 0.8f, width * 0.16f)
    val shellTone = floatArrayOf(v[V_SHELL], v[V_SHELL + 1], v[V_SHELL + 2])
    val bodyTone = floatArrayOf(v[V_TOY_BODY], v[V_TOY_BODY + 1], v[V_TOY_BODY + 2])
    val accentTone = floatArrayOf(v[V_ACCENT], v[V_ACCENT + 1], v[V_ACCENT + 2])

    /** A sphere around the whole toy, in its own units. */
    val radius: Float
        get() {
            val tall = height + 2f * max(humpHeight, 12f) + 10f
            val deep = depth + 2f * (lensLength + lensRadius * 0.4f) + 6f
            return 0.5f * sqrt((width + 10f) * (width + 10f) + tall * tall + deep * deep)
        }
}

/** The toy shader and its paint, made once (Android 13+). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class OrbitToyPainter {
    private val shader = RuntimeShader(TOY_AGSL)
    private val paint = Paint().apply {
        // The shader's output is already faded by its alpha; the paint stays opaque.
        color = android.graphics.Color.BLACK
        shader = this@OrbitToyPainter.shader
    }

    /** Draws the toy camera at `pose`, `alpha` its opacity, `flash` the shutter's flash (0…1). */
    fun draw(canvas: Canvas, view: View3, pose: Pose, shape: OrbitToyShape, alpha: Float, flash: Float) {
        if (alpha < 0.01f || pose.s <= 0f) return
        val r = pose.r
        val s = pose.s
        val bound = shape.radius
        val eyeDistance = pose.z - bound * s
        if (eyeDistance < 2f) return
        // Rows of the inverse rotation (the transpose), and the eye seen from the object.
        val ox = -(r[0] * pose.x + r[3] * pose.y + r[6] * pose.z) / s
        val oy = -(r[1] * pose.x + r[4] * pose.y + r[7] * pose.z) / s
        val oz = -(r[2] * pose.x + r[5] * pose.y + r[8] * pose.z) / s
        val sx = view.cx + view.f * pose.x / pose.z
        val sy = view.cy + view.f * pose.y / pose.z
        val reach = view.f * bound * s / eyeDistance + 2f
        if (!(ox + oy + oz + sx + sy + reach + bound + r.sum()).isFinite()) return
        shader.setFloatUniform("center", view.cx, view.cy)
        shader.setFloatUniform("focal", view.f)
        shader.setFloatUniform("origin", ox, oy, oz)
        shader.setFloatUniform("rx", r[0], r[3], r[6])
        shader.setFloatUniform("ry", r[1], r[4], r[7])
        shader.setFloatUniform("rz", r[2], r[5], r[8])
        shader.setFloatUniform("dims", shape.width, shape.height, shape.depth)
        shader.setFloatUniform("lens", shape.lensX, shape.lensY, shape.lensRadius, shape.lensLength)
        shader.setFloatUniform("hump", shape.humpWidth, shape.humpHeight, shape.shell)
        shader.setFloatUniform("shellTone", shape.shellTone)
        shader.setFloatUniform("bodyTone", shape.bodyTone)
        shader.setFloatUniform("accentTone", shape.accentTone)
        shader.setFloatUniform("bound", bound)
        shader.setFloatUniform("flash", clamp01(flash))
        shader.setFloatUniform("alpha", clamp01(alpha))
        shader.setFloatUniform("edge", TOY_EDGE)
        // Uniforms are read when the paint is drawn, so the shader is set again after the change.
        paint.shader = shader
        canvas.drawRect(sx - reach, sy - reach, sx + reach, sy + reach, paint)
    }
}

/**
 * Below Android 13 there is no runtime shader to ray-march the toy, and a pre-rendered sprite
 * cannot turn over freely in 3D, so the box camera stands in for it, its metal and leather taking
 * the toy's shell and body colours as the style morphs in (`toy` 0…1). It tumbles the same way.
 */
internal fun toyTinted(v: FloatArray, toy: Float): FloatArray {
    val out = v.copyOf()
    for (c in 0 until 3) {
        out[V_METAL + c] = v[V_METAL + c] + (v[V_SHELL + c] - v[V_METAL + c]) * toy
        out[V_LEATHER + c] = v[V_LEATHER + c] + (v[V_TOY_BODY + c] - v[V_LEATHER + c]) * toy
    }
    return out
}

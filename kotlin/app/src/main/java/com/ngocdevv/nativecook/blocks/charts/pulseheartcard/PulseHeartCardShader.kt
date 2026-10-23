package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The living heart, its halo and the ECG trace as one AGSL fragment program: the SkSL of
 * heart-shader.ts, unchanged except for the colour-space step (see toP3).
 *
 * Shape: the pin's heart outline fitted as a polar cosine series around (201.17, 290) in ref pt;
 * rest look is a closed-form colour fit per channel; the pink halo and the ECG pen are added in
 * the same pass. At rest the shader reproduces the pin; motion only adds.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class PulseHeartCardShader {
    val shader = RuntimeShader(SOURCE)

    /** Writes one engine frame into the uniforms. [scale] is canvas px per ref pt. */
    fun update(frame: PulseHeartCardFrame, scale: Float) {
        val beat = frame.beat
        shader.setFloatUniform("scale", scale)
        shader.setFloatUniform("deform", beat.body.toFloat(), beat.lobe.toFloat(), beat.squash.toFloat(), beat.lift.toFloat())
        shader.setFloatUniform("shine", beat.gloss.toFloat(), beat.light.toFloat(), beat.push.toFloat(), beat.flash.toFloat())
        shader.setFloatUniform("taus", frame.tau0.toFloat(), frame.tau1.toFloat())
        shader.setFloatUniform("trace", frame.phi.toFloat(), frame.traceStart.toFloat(), frame.traceEnd.toFloat())
    }

    private companion object {
        const val SOURCE = """

uniform float scale;    // canvas pt per ref pt
uniform float4 deform;  // body, lobe, squash, lift
uniform float4 shine;   // gloss, light, push, flash
uniform float2 taus;    // seconds since the last two lubs (ripples)
uniform float3 trace;   // phi, traceStart, traceEnd

const float2 C = float2(201.1667, 290.0);
const float TOP = 120.0;
const float3 RED = float3(0.957, 0.18, 0.349);

// Android colour-manages RuntimeShader output, so the React Native sRGB-to-Display-P3 step is left out.
float3 toP3(float3 c) {
  return clamp(c, 0.0, 1.0);
}

float radiusAt(float cs, float sn, out float dr) {
  float r = 114.202; dr = 0.0; float ck = cs; float sk = sn; float t;
  r += 15.914 * ck; dr -= 15.914 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -2.614 * ck; dr -= -5.228 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -14.183 * ck; dr -= -42.549 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -4.316 * ck; dr -= -17.264 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -4.079 * ck; dr -= -20.395 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.688 * ck; dr -= -4.128 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -1.807 * ck; dr -= -12.649 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.379 * ck; dr -= -3.032 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -1.124 * ck; dr -= -10.116000000000001 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.233 * ck; dr -= -2.33 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.716 * ck; dr -= -7.8759999999999994 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.107 * ck; dr -= -1.284 * sk;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -0.477 * ck; dr -= -6.201 * sk;
  return r;
}

// Approximate signed distance to the heart outline (ref pt, negative inside), q from the centre.
float shapeDist(float2 q) {
  float rho = length(q) + 0.0001;
  float dr;
  float r = radiusAt(-q.y / rho, q.x / rho, dr);
  return (rho - r) / sqrt(1.0 + (dr * dr) / (r * r));
}

// Inverse of the beat's deformation: screen offset from the centre -> undeformed offset.
float2 unwarp(float2 q) {
  float w = smoothstep(20.0, -90.0, q.y);  // upper lobes
  float sx = (1.0 + deform.x) * (1.0 + deform.z) + deform.y * w;
  float sy = (1.0 + deform.x) * (1.0 - 0.8 * deform.z) + 0.4 * deform.y * w;
  return float2(q.x / sx, q.y / sy);
}

float haloRadius(float cs, float sn) {
  float r = 114.202; float ck = cs; float sk = sn; float t;
  r += 15.914 * ck;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -2.614 * ck;
  t = ck * cs - sk * sn; sk = sk * cs + ck * sn; ck = t;
  r += -14.183 * ck;
  return r;
}

// Distance to the rounder halo heart (radial, ref pt).
float haloDist(float2 q) {
  float2 q0 = unwarp(q);
  float rho = length(q0) + 0.0001;
  return (rho - haloRadius(-q0.y / rho, q0.x / rho)) * (1.0 + deform.x);
}

float distAt(float2 q) {
  return shapeDist(unwarp(q)) * (1.0 + deform.x);
}

// Soft ellipse at c with radii r, rotated by a (rad). edge >= 0: crisp rim at that inner share;
// edge < 0: gaussian falloff.
float blob(float2 q, float2 c, float2 r, float a, float edge) {
  float2 d = q - c;
  float cs = cos(a); float sn = sin(a);
  d = float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y) / r;
  float e = dot(d, d);
  return edge >= 0.0 ? smoothstep(1.0, edge, e) : exp(-2.0 * e);
}

float ecg(float u) {
  return 3.5 * exp(-((u - -0.2) * (u - -0.2)) / 0.0012500000000000002) + -3.0 * exp(-((u - -0.028) * (u - -0.028)) / 0.00016199999999999998) + 26.0 * exp(-((u - 0.0) * (u - 0.0)) / 0.00014450000000000002) + -8.0 * exp(-((u - 0.026) * (u - 0.026)) / 0.0002) + 6.0 * exp(-((u - 0.24) * (u - 0.24)) / 0.0032);
}

float ripple(float d, float tau) {
  if (tau < 0.0 || tau > 0.75) return 0.0;
  float o = 4.0 + 150.0 * tau;
  float w = 4.0 + 12.0 * tau;
  float k = 1.0 - tau / 0.75;
  return 0.11 * k * k * smoothstep(0.0, 0.03, tau) * exp(-((d - o) * (d - o)) / (w * w));
}

half4 main(float2 pos) {
  float2 p = pos / scale + float2(0.0, TOP);
  float2 q = p - C - float2(0.0, deform.w);
  float sd = distAt(q);

  // Halo: the pin's pink ring, pushed out by the beat, plus the pressure ripples.
  float d = haloDist(q) - shine.z;
  float vy = clamp((p.y - 172.0) / 232.0, 0.0, 1.0);
  float halo = (7.0 * (1.0 - smoothstep(36.0, 56.0, d)) + 7.0 * exp(-((d - 36.0) * (d - 36.0)) / 110.0)) / 193.0;
  halo *= (1.12 - 0.25 * vy) * smoothstep(-1.0, 3.0, sd);
  float rip = (ripple(sd, taus.x) + ripple(sd, taus.y)) * smoothstep(120.0, 160.0, p.y) * smoothstep(500.0, 450.0, p.y);
  float aHalo = clamp(halo + rip * smoothstep(-1.0, 3.0, sd), 0.0, 1.0);
  float4 o = float4(toP3(RED) * aHalo, aHalo);

  // Body: the pin's matte gel shading, carried by the deformation.
  float bodyA = 1.0 - smoothstep(-2.0, 2.0, sd);
  if (bodyA > 0.0) {
    float2 q0 = unwarp(q);
    float din = max(-sd / (1.0 + deform.x), 0.0);
    float v = (q0.y + C.y - 172.0) / 232.0;
    float u = q0.x / 122.0;
    float e1 = exp(-din / 3.0);
    float e2 = exp(-din / 15.0);
    float3 col = float3(254.3 + -16.0 * e1 + -8.8 * e1 * v + 2.4 * e2 + 2.0 * e2 * v + -0.8 * v + -0.6 * u * u, 124.9 + -22.2 * e1 + 43.3 * e1 * v + -37.6 * e2 + -53.6 * e2 * v + -27.7 * v + -71.4 * u * u, 145.8 + -31.5 * e1 + 30.5 * e1 * v + -24.5 * e2 + -32.9 * e2 * v + -26.7 * v + -53.9 * u * u) / 255.0;

    // Wet gloss: painted highlights on the lobes, carried by the deformation (q0) and slid by
    // the beat's light shift; a rim sheen on the upper-left edge. Zero at rest (the pin is matte).
    float gloss = shine.x;
    if (gloss > 0.002) {
      float2 slide = shine.y * float2(40.0, 22.0);
      float spec = 0.85 * blob(q0, float2(-62.0, -66.0) + slide, float2(24.0, 10.5), -0.7, 0.3)
                 + 0.45 * blob(q0, float2(58.0, -72.0) + 0.5 * slide, float2(9.0, 4.5), 0.6, 0.3)
                 + 0.14 * blob(q0, float2(-30.0, -40.0) + slide, float2(78.0, 56.0), -0.5, -1.0);
      float rim = exp(-din / 3.5) * clamp(dot(normalize(q0 + float2(0.0, 0.0001)), float2(-0.55, -0.83)), 0.0, 1.0) * 0.32;
      col = mix(col, float3(0.86, 0.06, 0.24), 0.10 * gloss);   // the muscle flushes deeper
      col += (spec + rim) * gloss;
    }
    // R-peak bloom: the heart lights from within for ~200 ms (the reading's flash).
    float bloom = shine.w * 0.2 * exp(-dot(q0 - float2(0.0, -6.0), q0 - float2(0.0, -6.0)) / 7200.0);
    col += bloom * float3(1.0, 0.82, 0.86);
    float3 pc = toP3(col);
    o = float4(pc * bodyA, bodyA) + o * (1.0 - bodyA);
  }

  // ECG trace: a phosphor line swept left to right once per beat, R-peak at the heart's centre.
  float phi = trace.x;
  if (p.y > 322.0 && p.y < 384.0 && phi > trace.y) {
    float W = 402.0;
    float penU = fract(phi + 0.5) - 0.5;
    float u = (p.x - C.x) / W;
    float age = fract(penU - u);
    float drawnAt = phi - age;
    float fadeOut = 1.0 - smoothstep(trace.z, trace.z + 0.6, phi);
    float vis = step(trace.y, drawnAt) * step(drawnAt, trace.z) * smoothstep(0.97, 0.9, age);
    float alpha = vis * exp(-1.25 * age) * fadeOut;
    float h = 0.0015;
    float yT = 362.0 - ecg(u);
    float slope = (ecg(u - h) - ecg(u + h)) / (2.0 * h * W);
    float dist = abs(p.y - yT) / sqrt(1.0 + slope * slope);
    float core = 1.0 - smoothstep(0.95, 1.8, dist);
    float glow = exp(-(dist * dist) / 26.0) * 0.4;
    float penOn = step(trace.y, phi) * step(phi, trace.z) * fadeOut;
    float2 head = float2(C.x + penU * W, 362.0 - ecg(penU));
    float hd = dot(p - head, p - head);
    float pen = penOn * (exp(-hd / 7.0) + 0.45 * exp(-hd / 70.0));
    float a = clamp(max(core, glow) * alpha + pen, 0.0, 1.0);
    if (a > 0.001) {
      float3 tc = mix(RED * 0.92, float3(1.0), bodyA);
      o = float4(toP3(tc) * a, a) + o * (1.0 - a);
    }
  }
  return half4(o);
}
"""
    }
}

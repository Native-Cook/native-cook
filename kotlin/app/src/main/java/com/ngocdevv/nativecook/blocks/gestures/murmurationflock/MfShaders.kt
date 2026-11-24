// Murmuration Flock — the birds in GLSL ES 3.00, ported from gpu/mf-wgsl.ts.
//
// The WGSL compute pass becomes a transform-feedback pass (MF_STEP_VS, one point per bird, the
// rasteriser off): it works out each bird's place from pose(), the same closed form as MfMotion.kt,
// and integrates the one thing that is state: how far a finger has pushed it (disp, vel), on a spring
// back to zero. MF_BIRD_VS / MF_BIRD_FS then draw one small dart per bird along its heading, instanced
// from the buffer the step wrote, with its wings beating; the darts overlap into the dark, folding cloud.
// Bird in the buffer: pos, head, disp, vel (8 floats, 32 bytes), the WGSL struct's layout.
package com.ngocdevv.nativecook.blocks.gestures.murmurationflock

import java.util.Locale

private fun n(v: Double): String = String.format(Locale.US, "%.5f", v)
private fun n(v: Float): String = n(v.toDouble())

/** pcg, the seeds, seg, smoother and pose: shared by both vertex shaders */
private val COMMON = """
const float LOOP_S = ${n(LOOP)};
const float TAU_ = ${n(TAU)};
const float PI_ = ${n(Math.PI)};

uint pcg(uint v) {
  uint s = v * 747796405u + 2891336453u;
  uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
  return (w >> 22u) ^ w;
}

vec4 seedsOf(uint i) {
  float k = 4294967296.0;
  return vec4(float(pcg(i * 4u)) / k, float(pcg(i * 4u + 1u)) / k, float(pcg(i * 4u + 2u)) / k, float(pcg(i * 4u + 3u)) / k);
}

float seg(float t, float a, float b) { return clamp((t - a) / (b - a), 0.0, 1.0); }
float smoother(float v) { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }

vec2 pose(vec4 s, float t) {
  float w = TAU_ * t / LOOP_S;
  float u = 2.0 * s.x - 1.0;
  float v0 = 2.0 * s.y - 1.0;
  float v = v0 * (0.55 + 0.45 * abs(v0));
  float width = (${n(Cloud.WIDTH)} * (1.0 - ${n(Cloud.NARROW)} * u * u) + ${n(Cloud.WIDTH_MIN)}) * (1.0 + ${n(Cloud.SWELL)} * sin(${n(Cloud.SWELL_FREQ)} * u + w));
  float phi = ${n(Cloud.TWIST)} * u + w + ${n(Cloud.TWIST_WAVE)} * sin(2.0 * w + ${n(Cloud.TWIST_WAVE_FREQ)} * u);
  float k = 1.0 + floor(s.z * 3.0);
  float orbit = TAU_ * s.w + k * w;
  float rj = ${n(Cloud.ORBIT)} + ${n(Cloud.ORBIT_ADD)} * s.z * s.z;
  vec2 cloud = vec2(
    ${n(Cloud.ALONG)} * u + ${n(Cloud.SWAY)} * sin(${n(Cloud.SWAY_FREQ)} * u + w) - sin(phi) * width * v + rj * cos(orbit),
    ${n(Cloud.RISE)} * sin(${n(Cloud.RISE_FREQ)} * u + w) + ${n(Cloud.RIPPLE)} * sin(${n(Cloud.RIPPLE_FREQ)} * u - 2.0 * w) + cos(phi) * width * v * ${n(Cloud.TILT)} + rj * sin(orbit)
  );

  float late = s.w * ${n(T.STAGGER)};
  float m = smoother(seg(t, ${n(T.INHALE_A)} + late, ${n(T.INHALE_B)})) - smoother(seg(t, ${n(T.EXHALE_A)} + late, ${n(T.EXHALE_B)}));
  float spin = TAU_ * s.x + w;
  float r = (${n(Ring.RADIUS)} * (${n(Ring.INNER)} + ${n(1 - Ring.INNER)} * sqrt(s.y)) + ${n(Ring.SPREAD)} * (s.z - 0.5)) * (${n(Ring.GROW)} + ${n(1 - Ring.GROW)} * m);
  vec2 ring = vec2(r * cos(spin), r * sin(spin));

  vec2 d = ring - cloud;
  float bend = sin(PI_ * m) * (${n(Air.ARC)} + ${n(Air.ARC_ADD)} * (2.0 * s.z - 1.0));
  vec2 p = cloud + d * m + vec2(-d.y, d.x) * bend;
  float gs = sin(PI_ * seg(t, ${n(T.EXHALE_A)}, ${n(T.EXHALE_B)}));
  float g = gs * gs;
  if (g > 0.0) {
    p += p / (length(p) + 0.001) * g * (${n(Air.GUST)} + ${n(Air.GUST_ADD)} * s.z);
  }
  return p;
}
"""

/** the step: one vertex per bird, read from one buffer and captured into the other */
internal val MF_STEP_VS = """#version 300 es
precision highp float;
precision highp int;
layout(location = 0) in vec2 aDisp;
layout(location = 1) in vec2 aVel;
uniform vec4 uSim;    // t, dt, clear (1 = forget the finger), hold (how much of the push is shown)
uniform vec4 uView;   // width, height, centre x, centre y (dp)
uniform vec4 uLook;   // pose unit (dp), -, -, -
uniform vec4 uTouch;  // x, y (dp), strength 0…1, -
out vec2 vPos;
out vec2 vHead;
out vec2 vDisp;
out vec2 vVel;
$COMMON
void main() {
  vec2 disp = aDisp;
  vec2 vel = aVel;
  if (uSim.z > 0.5) {
    disp = vec2(0.0);
    vel = vec2(0.0);
  }
  vec4 s = seedsOf(uint(gl_VertexID));
  float unit = uLook.x;
  vec2 now = pose(s, uSim.x);
  vec2 before = pose(s, uSim.x - ${n(Flock.HEAD_BACK)});
  vec2 home = uView.zw + now * unit;
  float dt = uSim.y;

  vec2 to = home + disp - uTouch.xy;
  float r2 = dot(to, to);
  float fall = uTouch.z * exp(-r2 / ${n(Touch.RADIUS * Touch.RADIUS)});
  vec2 away = to / sqrt(r2 + 1.0);
  vec2 side = vec2(-away.y, away.x) * (s.z > 0.35 ? 1.0 : -1.0);
  vec2 force = (away * ${n(Touch.FLEE)} + side * ${n(Touch.SWIRL)}) * fall;
  vel += (force - ${n(Touch.SPRING)} * disp - ${n(Touch.DAMP)} * vel) * dt;
  disp += vel * dt;

  vPos = home + disp * uSim.w;
  vHead = (now - before) * unit + vel * ${n(Flock.HEAD_BACK)} * uSim.w;
  vDisp = disp;
  vVel = vel;
  gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}
"""

/** the step draws nothing (the rasteriser is off), but a program needs a fragment shader */
internal const val MF_STEP_FS = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main() { fragColor = vec4(0.0); }
"""

internal val MF_BIRD_VS = """#version 300 es
precision highp float;
precision highp int;
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aHead;
uniform vec4 uSim;
uniform vec4 uView;
out vec2 vUv;
out vec4 vInk;
$COMMON
void main() {
  vec2 corners[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
  vec2 c = corners[gl_VertexID];
  vec4 s = seedsOf(uint(gl_InstanceID));
  float speed = length(aHead);
  vec2 along = speed > 0.0001 ? aHead / speed : vec2(1.0, 0.0);
  vec2 across = vec2(-along.y, along.x);
  float beats = ${n(Flock.BEATS)} + floor(s.z * ${n(Flock.BEATS_RANGE)});
  float flap = 0.55 + 0.45 * abs(sin(TAU_ * beats * uSim.x / LOOP_S + TAU_ * s.w));
  float len = ${n(Flock.LENGTH)} + ${n(Flock.LENGTH_ADD)} * s.z;
  float wid = (${n(Flock.WIDTH)} + ${n(Flock.WIDTH_ADD)} * s.z) * flap;
  vec2 p = aPos + along * c.x * len + across * c.y * wid;
  gl_Position = vec4(p.x / uView.x * 2.0 - 1.0, 1.0 - p.y / uView.y * 2.0, 0.0, 1.0);
  vUv = c;
  // near birds are darker; the flock warms a little towards the horizon
  float low = clamp(aPos.y / uView.y, 0.0, 1.0);
  vec3 tone = mix(vec3(0.075, 0.066, 0.145), vec3(0.150, 0.082, 0.135), low);
  vInk = vec4(tone * (1.18 - 0.36 * s.z), ${n(Flock.ALPHA)} * (0.72 + 0.28 * s.z));
}
"""

internal const val MF_BIRD_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
in vec4 vInk;
out vec4 fragColor;
void main() {
  float d = abs(vUv.x) + abs(vUv.y);
  float a = vInk.a * (1.0 - smoothstep(0.55, 1.0, d));
  fragColor = vec4(vInk.rgb * a, a);
}
"""

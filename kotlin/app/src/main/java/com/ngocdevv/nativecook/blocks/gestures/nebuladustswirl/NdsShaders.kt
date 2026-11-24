// Nebula Dust Swirl — the dust in GLSL ES 3.00, ported from gpu/nds-wgsl.ts.
//
// The WGSL compute pass becomes a transform-feedback pass (NDS_STEP_VS, one point per grain, the
// rasteriser off): it works out each grain's place from pose(), the WGSL's closed form, and integrates
// the one thing that is state: how far a finger has stirred it (disp, vel), on a spring back to zero.
// NDS_GRAIN_VS / NDS_GRAIN_FS then draw one soft round grain per particle, instanced from the buffer
// the step wrote, added together (additive light): the warm core burns where thousands overlap, the
// arms glow thinner. Grain in the buffer: pos, disp, vel, look (8 floats, 32 bytes), the WGSL struct.
//
// The pose here is the WGSL's, the same as nds-motion.ts: 15 % core, 85 % arms, radial jitter 0.08.
package com.ngocdevv.nativecook.blocks.gestures.nebuladustswirl

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private fun n(v: Double): String = String.format(Locale.US, "%.5f", v)
private fun v3(c: DoubleArray): String = "vec3(${n(c[0])}, ${n(c[1])}, ${n(c[2])})"

/** pcg, the seeds and pose: shared by both vertex shaders */
private val COMMON = """
const float LOOP_S = ${n(LOOP)};
const float TAU_ = ${n(TAU)};
const float PI_ = ${n(Math.PI)};

uint pcg(uint v) {
  uint s = v * 747796405u + 2891336453u;
  uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
  return (w >> 22u) ^ w;
}

float unitOf(uint v) { return float(pcg(v)) / 4294967296.0; }

// seeds 0…3 and 4…7 of a pair: radius, spread, size, phase | height, spread', kind, tone
vec4 seedA(uint pair) { return vec4(unitOf(pair * 8u), unitOf(pair * 8u + 1u), unitOf(pair * 8u + 2u), unitOf(pair * 8u + 3u)); }
vec4 seedB(uint pair) { return vec4(unitOf(pair * 8u + 4u), unitOf(pair * 8u + 5u), unitOf(pair * 8u + 6u), unitOf(pair * 8u + 7u)); }

// x, y in pose units around the centre, perspective scale, radius in the disc
vec4 pose(vec4 a, vec4 b, float arm, float t) {
  float w = TAU_ * t / LOOP_S;
  float x;
  float y;
  float h;
  float r;
  if (b.z < ${n(Core.SHARE)}) {
    r = ${n(Core.RADIUS)} * sqrt(-2.0 * log(1.0 - 0.97 * a.x)) * 0.62;
    float ang = TAU_ * a.y + arm * PI_;
    x = r * cos(ang);
    y = r * sin(ang);
    h = ${n(Core.FLAT * Core.RADIUS)} * (b.x + b.y - 1.0);
  } else {
    r = ${n(Spiral.R_MIN)} + ${n(Spiral.R_MAX - Spiral.R_MIN)} * pow(a.x, ${n(Spiral.DENSITY)});
    r = r * (1.0 + ${n(Spiral.GPU_JITTER)} * (b.y - 0.5));
    float tri = a.y + b.y - 1.0;
    float ang = arm * PI_ + ${n(Spiral.WIND)} * log2(1.0 + 6.0 * r) + tri * (${n(Spiral.SPREAD)} + ${n(Spiral.SPREAD_OUT)} * r);
    x = r * cos(ang);
    y = r * sin(ang);
    h = ${n(Spiral.THICK)} * (b.x - 0.5) * (1.3 - r);
  }
  float inner = 1.0 - min(r, 1.0);
  float rot = PI_ * t / LOOP_S + ${n(Spiral.BREATH)} * inner * inner * sin(w + 1.3);
  float cr = cos(rot);
  float sr = sin(rot);
  float dx = x * cr - y * sr;
  float dy = x * sr + y * cr;
  float k = 1.0 + floor(a.z * 3.0);
  float rj = ${n(Spiral.ORBIT)} + ${n(Spiral.ORBIT_ADD)} * a.z * a.z;
  dx += rj * cos(TAU_ * a.w + k * w);
  dy += rj * sin(TAU_ * a.w + k * w);
  float ct = ${n(cos(Spiral.TILT))};
  float st = ${n(sin(Spiral.TILT))};
  float sy = dy * ct - h * st;
  float z = dy * st + h * ct;
  float p = ${n(Spiral.CAMERA)} / (${n(Spiral.CAMERA)} - z);
  return vec4(dx * p, sy * p, p, min(r, 1.0));
}
"""

/** the step: one vertex per grain, read from one buffer and captured into the other */
internal val NDS_STEP_VS = """#version 300 es
precision highp float;
precision highp int;
layout(location = 0) in vec2 aDisp;
layout(location = 1) in vec2 aVel;
uniform vec4 uSim;    // t, dt, clear (1 = forget the finger), hold (how much of the stir is shown)
uniform vec4 uView;   // width, height, centre x, centre y (dp)
uniform vec4 uLook;   // pose unit (dp), -, -, -
uniform vec4 uTouch;  // x, y (dp), strength 0…1, -
uniform vec4 uPush;   // the finger's velocity x, y (dp/s), -, -
out vec2 vPos;
out vec2 vDisp;
out vec2 vVel;
out vec2 vLook;
$COMMON
void main() {
  vec2 disp = aDisp;
  vec2 vel = aVel;
  if (uSim.z > 0.5) {
    disp = vec2(0.0);
    vel = vec2(0.0);
  }
  uint i = uint(gl_VertexID);
  uint pair = i >> 1u;
  vec4 q = pose(seedA(pair), seedB(pair), float(i & 1u), uSim.x);
  vec2 home = uView.zw + q.xy * uLook.x;
  float dt = uSim.y;

  vec2 to = home + disp - uTouch.xy;
  float r2 = dot(to, to);
  float fall = uTouch.z * exp(-r2 / ${n(Touch.RADIUS * Touch.RADIUS)});
  vec2 side = vec2(-to.y, to.x) / sqrt(r2 + 1.0);
  vec2 force = side * ${n(Touch.SWIRL)} * fall + (uPush.xy - vel) * ${n(Touch.DRAG)} * fall;
  vel += (force - ${n(Touch.SPRING)} * disp - ${n(Touch.DAMP)} * vel) * dt;
  disp += vel * dt;

  vPos = home + disp * uSim.w;
  vDisp = disp;
  vVel = vel;
  vLook = q.zw;
  gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}
"""

/** the step draws nothing (the rasteriser is off), but a program needs a fragment shader */
internal const val NDS_STEP_FS = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main() { fragColor = vec4(0.0); }
"""

internal val NDS_GRAIN_VS = """#version 300 es
precision highp float;
precision highp int;
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aLook;
uniform vec4 uSim;    // t, -, -, -
uniform vec4 uView;   // width, height (dp), -, -
out vec2 vUv;
out vec4 vInk;
$COMMON
void main() {
  vec2 corners[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
  vec2 c = corners[gl_VertexID];
  uint pair = uint(gl_InstanceID) >> 1u;
  vec4 a = seedA(pair);
  vec4 b = seedB(pair);
  float p = aLook.x;
  float r = aLook.y;
  bool core = b.z < ${n(Core.SHARE)};
  bool star = !core && b.w > ${n(1 - Dust.STAR_SHARE)};

  float rad = (${n(Dust.SIZE)} + ${n(Dust.SIZE_ADD)} * a.z * a.z) * p;
  vec3 col = mix(${v3(Tone.CORE)}, ${v3(Tone.WARM)}, smoothstep(0.0, 0.3, r));
  col = mix(col, mix(${v3(Tone.COOL)}, ${v3(Tone.TEAL)}, b.w), smoothstep(0.22, 0.72, r));
  float gain = core ? ${n(Tone.CORE_GAIN)} : ${n(Tone.ARM_GAIN)};
  if (!core && b.w < ${n(Tone.ROSE_SHARE)}) {
    col = mix(col, ${v3(Tone.ROSE)}, 0.8);
  }
  if (star) {
    rad = ${n(Dust.STAR)} * p;
    col = ${v3(Tone.STAR)};
    gain = ${n(Tone.STAR_GAIN)};
  }
  float beats = ${n(Dust.TWINKLE)} + floor(a.y * ${n(Dust.TWINKLE_RANGE)});
  float twinkle = 0.72 + 0.28 * sin(TAU_ * beats * uSim.x / LOOP_S + TAU_ * a.w);
  // nearer grains are a little brighter; the rim fades out into the dark
  float light = gain * twinkle * p * p * (1.0 - smoothstep(0.8, 1.02, r));

  vec2 at = aPos + c * rad * 1.6;
  gl_Position = vec4(at.x / uView.x * 2.0 - 1.0, 1.0 - at.y / uView.y * 2.0, 0.0, 1.0);
  vUv = c;
  vInk = vec4(col * light, light);
}
"""

internal const val NDS_GRAIN_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
in vec4 vInk;
out vec4 fragColor;
void main() {
  float a = exp(-dot(vUv, vUv) * 3.0);
  fragColor = vec4(vInk.rgb * a, vInk.a * a * 0.5);
}
"""

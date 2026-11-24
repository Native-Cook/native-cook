// Ripple Pond Photo — the water in GLSL ES 3.00, ported line for line from the block's WGSL
// (gpu/rpp-wgsl.ts) and, for the fallback, from its SkSL (RppSkiaFallback.tsx).
//
// Two passes over one triangle:
//  1. STEP_FS, on a gridW × gridH RGBA16F texture, ping-ponged: r is the height now, g the height one
//     step ago. next = (2h − prev + c²·∇²h) · damp, with the neighbours clamped at the border, so the
//     edges are walls and rings bounce off them. Up to four drops are pressed into both r and g (a
//     displacement, not a push), each a zero-mean dip (1 − q)·exp(−q), so the level never drifts.
//  2. VIEW_FS, on the screen: the field's slope bends the photo's coordinate (red and blue a little
//     more and less than green), its curvature brightens where the surface focuses light, the slope
//     towards the light shades the rings and a narrow highlight glints on their lit side. A flat field
//     returns the photo untouched, which is what makes the loop's cut invisible.
//
// Rows: texel row 0 of the field and of the photo is the top of the screen. gl_FragCoord counts from
// the bottom of the window, so the two screen passes flip y; the step pass works in texels only.
package com.ngocdevv.nativecook.blocks.gestures.ripplepondphoto

internal const val FULL_VS = """#version 300 es
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

internal const val STEP_FS = """#version 300 es
precision highp float;
precision highp sampler2D;
uniform vec4 uCfg;        // c², damping, grid width, grid height
uniform vec4 uDrops[4];   // x, y (0…1), radius (cells), depth
uniform sampler2D uField;
out vec4 outColor;
void main() {
  ivec2 size = ivec2(uCfg.zw);
  ivec2 hi = size - ivec2(1);
  ivec2 p = clamp(ivec2(gl_FragCoord.xy), ivec2(0), hi);
  vec2 c = texelFetch(uField, p, 0).rg;
  float l = texelFetch(uField, clamp(p + ivec2(-1, 0), ivec2(0), hi), 0).r;
  float r = texelFetch(uField, clamp(p + ivec2(1, 0), ivec2(0), hi), 0).r;
  float u = texelFetch(uField, clamp(p + ivec2(0, -1), ivec2(0), hi), 0).r;
  float d = texelFetch(uField, clamp(p + ivec2(0, 1), ivec2(0), hi), 0).r;
  float next = (2.0 * c.x - c.y + uCfg.x * (l + r + u + d - 4.0 * c.x)) * uCfg.y;
  float add = 0.0;
  for (int i = 0; i < 4; i++) {
    vec4 drop = uDrops[i];
    if (drop.w != 0.0) {
      vec2 q = vec2(p) + vec2(0.5) - drop.xy * uCfg.zw;
      float k = dot(q, q) / (drop.z * drop.z);
      add += drop.w * (1.0 - k) * exp(-k);
    }
  }
  outColor = vec4(next + add, c.x + add, 0.0, 1.0);
}
"""

internal const val VIEW_FS = """#version 300 es
precision highp float;
precision highp sampler2D;
uniform vec4 uCanvas;   // width, height (px), pixel ratio, -
uniform vec4 uGrid;     // grid width, height, photo uv scale x, y (cover)
uniform vec4 uBend;     // refraction (dp per slope), its limit (dp), chroma spread, normal gain
uniform vec4 uLight;    // highlight power, highlight gain, caustic gain, shade gain
uniform sampler2D uField;
uniform sampler2D uPhoto;
out vec4 outColor;
float heightAt(vec2 uv) { return texture(uField, uv).r; }
vec3 photoAt(vec2 uv) {
  vec2 q = (uv - vec2(0.5)) * uGrid.zw + vec2(0.5);
  return texture(uPhoto, clamp(q, vec2(0.001), vec2(0.999))).rgb;
}
void main() {
  vec2 uv = vec2(gl_FragCoord.x, uCanvas.y - gl_FragCoord.y) / uCanvas.xy;
  vec2 cell = vec2(1.0) / uGrid.xy;
  float hc = heightAt(uv);
  float hl = heightAt(uv - vec2(cell.x, 0.0));
  float hr = heightAt(uv + vec2(cell.x, 0.0));
  float hu = heightAt(uv - vec2(0.0, cell.y));
  float hd = heightAt(uv + vec2(0.0, cell.y));
  vec2 slope = vec2(hr - hl, hd - hu) * 0.5;

  // refraction, in dp, then as a step in uv
  vec2 bend = slope * uBend.x;
  float far = length(bend);
  if (far > uBend.y) { bend = bend * (uBend.y / far); }
  vec2 shift = bend * uCanvas.z / uCanvas.xy;
  float red = photoAt(uv + shift * (1.0 + uBend.z)).r;
  float green = photoAt(uv + shift).g;
  float blue = photoAt(uv + shift * (1.0 - uBend.z)).b;
  vec3 col = pow(vec3(red, green, blue), vec3(2.2));

  // light from the upper left
  vec3 L = normalize(vec3(-0.45, -0.6, 0.66));
  vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
  vec3 n = normalize(vec3(-slope * uBend.w, 1.0));
  float glint = max(pow(max(dot(n, H), 0.0), uLight.x) - pow(H.z, uLight.x), 0.0);
  float lap = hl + hr + hu + hd - 4.0 * hc;
  col = col * (1.0 + clamp(-lap * uLight.z, -0.2, 0.5));
  col = col * (1.0 + clamp(-dot(slope, L.xy) * uLight.w, -0.22, 0.22));
  col = col + glint * uLight.y * vec3(1.0, 0.98, 0.92);
  outColor = vec4(pow(clamp(col, vec3(0.0), vec3(1.0)), vec3(1.0 / 2.2)), 1.0);
}
"""

// The fallback, where a float texture cannot be rendered to. No simulation: up to eight rings are
// drawn analytically over the photo, each a wave packet leaving its source at RING_SPEED, so there
// are no reflections off the edges and a wake is a string of small rings. The refraction, shading
// and glint follow the simulated look.
internal val RINGS_FS = """#version 300 es
precision highp float;
precision highp sampler2D;
uniform vec4 uCanvas;    // width, height (px), pixel ratio, -
uniform vec4 uGrid;      // -, -, photo uv scale x, y (cover)
uniform vec2 uClock;     // seconds now, the water's level 0…1 (0 = calmed)
uniform vec4 uRings[8];  // x, y (dp), born (s), strength (0 = unused)
uniform sampler2D uPhoto;
out vec4 outColor;
void main() {
  vec2 res = uCanvas.xy / uCanvas.z;
  vec2 p = vec2(gl_FragCoord.x, uCanvas.y - gl_FragCoord.y) / uCanvas.z;
  vec2 bend = vec2(0.0);
  for (int i = 0; i < 8; i++) {
    vec4 ring = uRings[i];
    float age = uClock.x - ring.z;
    if (ring.w > 0.0 && age > 0.0 && age < ${RING_LIFE.glsl()}) {
      vec2 d = p - ring.xy;
      float far = length(d) + 0.001;
      float x = far - age * ${RING_SPEED.glsl()};
      float env = exp(-x * x / ${(RING_WIDTH * RING_WIDTH).glsl()}) * exp(-age * ${RING_DECAY.glsl()}) * ring.w * clamp(age / 0.15, 0.0, 1.0) / sqrt(1.0 + far / 40.0);
      bend += d / far * cos(x * ${(2.0 * Math.PI / RING_WAVE).toFloat().glsl()}) * env;
    }
  }
  bend *= 0.16 * uClock.y;
  vec2 shift = bend * ${LOOK_REFRACT.glsl()};
  float reach = length(shift);
  if (reach > ${LOOK_REFRACT_MAX.glsl()}) { shift *= ${LOOK_REFRACT_MAX.glsl()} / reach; }
  vec2 at = clamp(p + shift, vec2(0.5), res - vec2(0.5));
  vec2 q = (at / res - vec2(0.5)) * uGrid.zw + vec2(0.5);
  vec3 c = texture(uPhoto, clamp(q, vec2(0.001), vec2(0.999))).rgb;
  vec3 L = normalize(vec3(-0.45, -0.6, 0.66));
  vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
  vec3 n = normalize(vec3(-bend * ${LOOK_NORMAL.glsl()}, 1.0));
  float glint = max(pow(max(dot(n, H), 0.0), ${LOOK_SPEC_POWER.glsl()}) - pow(H.z, ${LOOK_SPEC_POWER.glsl()}), 0.0);
  vec3 col = c * (1.0 + clamp(-dot(bend, L.xy) * ${LOOK_SHADE.glsl()}, -0.22, 0.22));
  col += glint * ${(LOOK_SPEC * 0.6f).glsl()} * vec3(1.0, 0.98, 0.92);
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

/** a float as a GLSL literal (always with a decimal point) */
private fun Float.glsl(): String = String.format(java.util.Locale.US, "%.5f", this)

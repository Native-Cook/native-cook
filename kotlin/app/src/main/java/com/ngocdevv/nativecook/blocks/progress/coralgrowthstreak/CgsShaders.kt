// Coral Growth Streak — the reef in GLSL ES 3.00, ported line for line from gpu/cgs-wgsl.ts.
//
// Two passes over one triangle:
//  1. STEP_FS, on a 256 × 256 RGBA16F texture, ping-ponged: r is u (the food), g is v (the coral). One
//     Gray-Scott step on the 9-point kernel, the neighbours clamped at the border. Outside the open
//     radius the kill rate is raised, so the coral fills the room it was given and stops.
//  2. VIEW_FS, on the round view: v becomes a height, lit from the upper left, coloured rose to orange
//     with sand on the ridges. `level` dissolves it from the rim inwards, `bloom` sends one band of
//     light out from the centre. At level 0 the result is the bare sea bed whatever the field holds,
//     which is what makes the loop's cut invisible.
//
// Rows: texel row 0 of the field is the top of the reef. gl_FragCoord counts from the bottom of the
// window, so the view pass flips y; the step pass works in texels only.
package com.ngocdevv.nativecook.blocks.progress.coralgrowthstreak

internal const val CGS_FULL_VS = """#version 300 es
void main() {
  vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

internal const val CGS_STEP_FS = """#version 300 es
precision highp float;
precision highp int;
precision highp sampler2D;
uniform vec4 uCfg;        // feed, kill, grid, seed radius (cells)
uniform vec4 uWall;       // open radius (cells), wall kill, wall half width (cells), -
uniform vec4 uSeeds[8];   // x, y (0…1), strength (0 = unused), -
uniform sampler2D uField;
out vec4 outColor;

vec2 cellAt(ivec2 p, ivec2 hi) { return texelFetch(uField, clamp(p, ivec2(0), hi), 0).rg; }

void main() {
  ivec2 size = ivec2(int(uCfg.z));
  ivec2 hi = size - ivec2(1);
  ivec2 p = clamp(ivec2(gl_FragCoord.xy), ivec2(0), hi);
  vec2 c = cellAt(p, hi);
  vec2 edges = cellAt(p + ivec2(-1, 0), hi) + cellAt(p + ivec2(1, 0), hi) + cellAt(p + ivec2(0, -1), hi) + cellAt(p + ivec2(0, 1), hi);
  vec2 corners = cellAt(p + ivec2(-1, -1), hi) + cellAt(p + ivec2(1, -1), hi) + cellAt(p + ivec2(-1, 1), hi) + cellAt(p + ivec2(1, 1), hi);
  vec2 lap = 0.2 * edges + 0.05 * corners - c;
  vec2 centre = vec2(p) + vec2(0.5);
  float far = length(centre - vec2(uCfg.z * 0.5));
  float kill = uCfg.y + uWall.y * smoothstep(uWall.x - uWall.z, uWall.x + uWall.z, far);
  float uvv = c.x * c.y * c.y;
  float u = c.x + 1.0 * lap.x - uvv + uCfg.x * (1.0 - c.x);
  float v = c.y + 0.5 * lap.y + uvv - (uCfg.x + kill) * c.y;
  for (int i = 0; i < 8; i++) {
    vec4 seed = uSeeds[i];
    if (seed.z != 0.0) {
      vec2 q = centre - seed.xy * uCfg.z;
      float e = seed.z * exp(-dot(q, q) / (uCfg.w * uCfg.w));
      v += e;
      u -= 0.5 * e;
    }
  }
  outColor = vec4(clamp(u, 0.0, 1.0), clamp(v, 0.0, 1.0), 0.0, 1.0);
}
"""

internal const val CGS_VIEW_FS = """#version 300 es
precision highp float;
precision highp sampler2D;
uniform vec4 uCanvas;     // width, height (px), grid, pixel ratio
uniform vec4 uAnim;       // level 0…1, bloom 0…1, phase 0…1 (one turn a loop), -
uniform sampler2D uField;
out vec4 outColor;

vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }
float heightAt(vec2 uv) { return smoothstep(0.06, 0.32, textureLod(uField, uv, 0.0).g); }
float grainAt(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
  vec2 pos = vec2(gl_FragCoord.x, uCanvas.y - gl_FragCoord.y);
  vec2 uv = pos / uCanvas.xy;
  vec2 d = (uv - vec2(0.5)) * 2.0;
  float r = length(d);
  float cell = 1.0 / uCanvas.z;
  // the dissolve: gone at the rim first, the centre last
  float gate = smoothstep(r - 0.3, r, uAnim.x * 1.6 - 0.3);
  float hc = heightAt(uv) * gate;
  float hl = heightAt(uv - vec2(cell, 0.0)) * gate;
  float hr = heightAt(uv + vec2(cell, 0.0)) * gate;
  float hu = heightAt(uv - vec2(0.0, cell)) * gate;
  float hd = heightAt(uv + vec2(0.0, cell)) * gate;
  vec2 slope = vec2(hr - hl, hd - hu) * 0.5;

  // light from the upper left
  vec3 L = normalize(vec3(-0.45, -0.6, 0.66));
  vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
  vec3 n = normalize(vec3(-slope * 2.4, 1.0));
  float dif = max(dot(n, L), 0.0);
  float spec = pow(max(dot(n, H), 0.0), 40.0);
  float shadow = heightAt(uv + vec2(-2.0, -3.0) * cell) * gate * (1.0 - hc);

  // the sea bed: deep teal, lighter at the centre, a slow caustic and a few grains of sand
  float ph = uAnim.z * 6.2831853;
  vec2 q = d * 3.2;
  float c1 = sin(q.x * 1.7 + sin(q.y * 2.3 + ph) * 1.2 + ph);
  float c2 = sin(q.y * 2.1 - sin(q.x * 1.9 - ph) * 1.3 + 2.0 * ph);
  float caustic = pow(0.5 + 0.5 * c1 * c2, 3.0);
  vec3 bed = mix(toLinear(vec3(0.075, 0.300, 0.340)), toLinear(vec3(0.030, 0.170, 0.215)), smoothstep(0.0, 1.0, r));
  bed += caustic * 0.022 * vec3(0.5, 1.0, 0.95);
  float grain = grainAt(floor(pos / (uCanvas.w * 1.5)));
  bed += step(0.965, grain) * 0.02 * vec3(1.0, 0.9, 0.7);
  bed *= 1.0 - 0.55 * shadow;

  // the coral: rose at the centre, orange at the rim, plum on the flanks and sand on the ridges
  vec3 body = mix(toLinear(vec3(0.900, 0.300, 0.370)), toLinear(vec3(1.000, 0.530, 0.360)), smoothstep(0.1, 1.0, r));
  body = mix(body * vec3(0.34, 0.22, 0.42), body, smoothstep(0.15, 0.75, hc));
  body = mix(body, toLinear(vec3(1.000, 0.880, 0.720)), smoothstep(0.82, 1.0, hc) * 0.3);
  vec3 coral = body * (0.42 + 0.74 * dif) + spec * 0.3 * vec3(1.0, 0.95, 0.85);
  float cover = smoothstep(0.08, 0.3, hc);
  vec3 col = mix(bed, coral, cover);

  // day seven: one band of light from the centre to the rim
  float off = (r - (uAnim.y * 1.5 - 0.25)) / 0.17;
  float band = exp(-off * off) * sin(3.14159265 * uAnim.y);
  col += band * (cover * vec3(1.0, 0.62, 0.45) * 0.5 + vec3(0.01, 0.035, 0.035));

  // the window: darker towards the rim, cut to a circle
  float rim = smoothstep(0.5, 1.0, r);
  col *= 1.0 - 0.4 * rim * rim;
  float a = clamp((1.0 - r) * uCanvas.x * 0.5, 0.0, 1.0);
  vec3 outc = pow(clamp(col, vec3(0.0), vec3(1.0)), vec3(1.0 / 2.2));
  outColor = vec4(outc * a, a);
}
"""

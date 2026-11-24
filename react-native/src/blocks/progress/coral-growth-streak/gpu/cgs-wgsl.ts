// Coral Growth Streak — the reef on WebGPU, in plain WGSL.
//
// Two passes over one triangle:
//  1. stepFs, on a grid × grid rgba16float texture, ping-ponged: r is u (the food), g is v (the
//     coral). One Gray-Scott step on the 9-point kernel:
//       u' = u + Du ∇²u − u v² + F (1 − u)
//       v' = v + Dv ∇²v + u v² − (F + k) v
//     with the neighbours clamped at the border. Outside the open radius k is raised by wallKill, so
//     the coral fills the room it has been given and stops. Up to eight seeds are stamped in
//     (v += e, u −= e / 2, e = exp(−r² / radius²)).
//  2. viewFs, on the round canvas: v becomes a height (smoothstep 0.06…0.32), lit from the upper left
//     with a highlight and a short shadow on the sea bed, coloured rose at the centre to orange at
//     the rim with sand on the ridges. `level` dissolves the coral from the rim inwards and `bloom`
//     sends one band of light out from the centre. With level 0 the result is the bare sea bed
//     whatever the field holds, which is what makes the loop's cut invisible.

import { SIM } from '../cgs-motion';

export const STEP_FLOATS = 8 + SIM.seedSlots * 4;
export const VIEW_FLOATS = 8;

const FULL = /* wgsl */ `
@vertex fn fullVs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
  let p = vec2f(f32((i << 1u) & 2u), f32(i & 2u));
  return vec4f(p * 2.0 - 1.0, 0.0, 1.0);
}
`;

export const STEP_WGSL = /* wgsl */ `
struct StepU {
  cfg: vec4f,                              // feed, kill, grid, seed radius (cells)
  wall: vec4f,                             // open radius (cells), wall kill, wall half width (cells), -
  seeds: array<vec4f, ${SIM.seedSlots}>,   // x, y (0…1), strength (0 = unused), -
};
@group(0) @binding(0) var<uniform> S: StepU;
@group(0) @binding(1) var field: texture_2d<f32>;
${FULL}
fn cellAt(p: vec2i, hi: vec2i) -> vec2f { return textureLoad(field, clamp(p, vec2i(0), hi), 0).rg; }

@fragment fn stepFs(@builtin(position) pos: vec4f) -> @location(0) vec4f {
  let size = vec2i(i32(S.cfg.z));
  let hi = size - vec2i(1);
  let p = clamp(vec2i(pos.xy), vec2i(0), hi);
  let c = cellAt(p, hi);
  let edges = cellAt(p + vec2i(-1, 0), hi) + cellAt(p + vec2i(1, 0), hi) + cellAt(p + vec2i(0, -1), hi) + cellAt(p + vec2i(0, 1), hi);
  let corners = cellAt(p + vec2i(-1, -1), hi) + cellAt(p + vec2i(1, -1), hi) + cellAt(p + vec2i(-1, 1), hi) + cellAt(p + vec2i(1, 1), hi);
  let lap = 0.2 * edges + 0.05 * corners - c;
  let centre = vec2f(p) + vec2f(0.5);
  let far = length(centre - vec2f(S.cfg.z * 0.5));
  let kill = S.cfg.y + S.wall.y * smoothstep(S.wall.x - S.wall.z, S.wall.x + S.wall.z, far);
  let uvv = c.x * c.y * c.y;
  var u = c.x + ${SIM.du.toFixed(2)} * lap.x - uvv + S.cfg.x * (1.0 - c.x);
  var v = c.y + ${SIM.dv.toFixed(2)} * lap.y + uvv - (S.cfg.x + kill) * c.y;
  for (var i = 0; i < ${SIM.seedSlots}; i++) {
    let seed = S.seeds[i];
    if (seed.z != 0.0) {
      let q = centre - seed.xy * S.cfg.z;
      let e = seed.z * exp(-dot(q, q) / (S.cfg.w * S.cfg.w));
      v += e;
      u -= 0.5 * e;
    }
  }
  return vec4f(clamp(u, 0.0, 1.0), clamp(v, 0.0, 1.0), 0.0, 1.0);
}
`;

export const VIEW_WGSL = /* wgsl */ `
struct ViewU {
  canvas: vec4f,   // width, height (px), grid, pixel ratio
  anim: vec4f,     // level 0…1, bloom 0…1, phase 0…1 (one turn a loop), -
};
@group(0) @binding(0) var<uniform> V: ViewU;
@group(0) @binding(1) var field: texture_2d<f32>;
@group(0) @binding(2) var samp: sampler;
${FULL}
fn toLinear(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn heightAt(uv: vec2f) -> f32 { return smoothstep(0.06, 0.32, textureSampleLevel(field, samp, uv, 0.0).g); }
fn grainAt(p: vec2f) -> f32 { return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453); }

@fragment fn viewFs(@builtin(position) pos: vec4f) -> @location(0) vec4f {
  let uv = pos.xy / V.canvas.xy;
  let d = (uv - vec2f(0.5)) * 2.0;
  let r = length(d);
  let cell = 1.0 / V.canvas.z;
  // the dissolve: gone at the rim first, the centre last
  let gate = smoothstep(r - 0.3, r, V.anim.x * 1.6 - 0.3);
  let hc = heightAt(uv) * gate;
  let hl = heightAt(uv - vec2f(cell, 0.0)) * gate;
  let hr = heightAt(uv + vec2f(cell, 0.0)) * gate;
  let hu = heightAt(uv - vec2f(0.0, cell)) * gate;
  let hd = heightAt(uv + vec2f(0.0, cell)) * gate;
  let slope = vec2f(hr - hl, hd - hu) * 0.5;

  // light from the upper left
  let L = normalize(vec3f(-0.45, -0.6, 0.66));
  let H = normalize(L + vec3f(0.0, 0.0, 1.0));
  let n = normalize(vec3f(-slope * 2.4, 1.0));
  let dif = max(dot(n, L), 0.0);
  let spec = pow(max(dot(n, H), 0.0), 40.0);
  let shadow = heightAt(uv + vec2f(-2.0, -3.0) * cell) * gate * (1.0 - hc);

  // the sea bed: deep teal, lighter at the centre, a slow caustic and a few grains of sand
  let ph = V.anim.z * 6.2831853;
  let q = d * 3.2;
  let c1 = sin(q.x * 1.7 + sin(q.y * 2.3 + ph) * 1.2 + ph);
  let c2 = sin(q.y * 2.1 - sin(q.x * 1.9 - ph) * 1.3 + 2.0 * ph);
  let caustic = pow(0.5 + 0.5 * c1 * c2, 3.0);
  var bed = mix(toLinear(vec3f(0.075, 0.300, 0.340)), toLinear(vec3f(0.030, 0.170, 0.215)), smoothstep(0.0, 1.0, r));
  bed += caustic * 0.022 * vec3f(0.5, 1.0, 0.95);
  let grain = grainAt(floor(pos.xy / (V.canvas.w * 1.5)));
  bed += step(0.965, grain) * 0.02 * vec3f(1.0, 0.9, 0.7);
  bed *= 1.0 - 0.55 * shadow;

  // the coral: rose at the centre, orange at the rim, plum on the flanks and sand on the ridges
  var body = mix(toLinear(vec3f(0.900, 0.300, 0.370)), toLinear(vec3f(1.000, 0.530, 0.360)), smoothstep(0.1, 1.0, r));
  body = mix(body * vec3f(0.34, 0.22, 0.42), body, smoothstep(0.15, 0.75, hc));
  body = mix(body, toLinear(vec3f(1.000, 0.880, 0.720)), smoothstep(0.82, 1.0, hc) * 0.3);
  let coral = body * (0.42 + 0.74 * dif) + spec * 0.3 * vec3f(1.0, 0.95, 0.85);
  let cover = smoothstep(0.08, 0.3, hc);
  var col = mix(bed, coral, cover);

  // day seven: one band of light from the centre to the rim
  let off = (r - (V.anim.y * 1.5 - 0.25)) / 0.17;
  let band = exp(-off * off) * sin(3.14159265 * V.anim.y);
  col += band * (cover * vec3f(1.0, 0.62, 0.45) * 0.5 + vec3f(0.01, 0.035, 0.035));

  // the window: darker towards the rim, cut to a circle
  let rim = smoothstep(0.5, 1.0, r);
  col *= 1.0 - 0.4 * rim * rim;
  let a = clamp((1.0 - r) * V.canvas.x * 0.5, 0.0, 1.0);
  let outc = pow(clamp(col, vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2));
  return vec4f(outc * a, a);
}
`;

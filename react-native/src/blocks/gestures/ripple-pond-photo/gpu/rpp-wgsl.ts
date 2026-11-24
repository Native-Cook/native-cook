// Ripple Pond Photo — the water on WebGPU, in plain WGSL.
//
// Two passes over one triangle:
//  1. stepFs, on a gridW × gridH rgba16float texture, ping-ponged: r is the height now, g the height
//     one step ago. next = (2h − prev + c²·∇²h) · damp, with the neighbours clamped at the border, so
//     the edges are walls and rings bounce off them. Up to four drops are pressed into both r and g
//     (a displacement, not a push), each a zero-mean dip (1 − q)·exp(−q), so the water's level never
//     drifts.
//  2. viewFs, on the canvas: the field's slope bends the photo's coordinate (red and blue a little
//     more and less than green), its curvature brightens where the surface focuses light, the slope
//     towards the light shades the rings and a narrow highlight glints on their lit side. A flat
//     field returns the photo untouched, which is what makes the loop's cut invisible.

export const STEP_FLOATS = 20;
export const VIEW_FLOATS = 16;

const FULL = /* wgsl */ `
@vertex fn fullVs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
  let p = vec2f(f32((i << 1u) & 2u), f32(i & 2u));
  return vec4f(p * 2.0 - 1.0, 0.0, 1.0);
}
`;

export const STEP_WGSL = /* wgsl */ `
struct StepU {
  cfg: vec4f,               // c², damping, grid width, grid height
  drops: array<vec4f, 4>,   // x, y (0…1), radius (cells), depth
};
@group(0) @binding(0) var<uniform> S: StepU;
@group(0) @binding(1) var field: texture_2d<f32>;
${FULL}
@fragment fn stepFs(@builtin(position) pos: vec4f) -> @location(0) vec4f {
  let size = vec2i(S.cfg.zw);
  let hi = size - vec2i(1);
  let p = clamp(vec2i(pos.xy), vec2i(0), hi);
  let c = textureLoad(field, p, 0).rg;
  let l = textureLoad(field, clamp(p + vec2i(-1, 0), vec2i(0), hi), 0).r;
  let r = textureLoad(field, clamp(p + vec2i(1, 0), vec2i(0), hi), 0).r;
  let u = textureLoad(field, clamp(p + vec2i(0, -1), vec2i(0), hi), 0).r;
  let d = textureLoad(field, clamp(p + vec2i(0, 1), vec2i(0), hi), 0).r;
  let next = (2.0 * c.x - c.y + S.cfg.x * (l + r + u + d - 4.0 * c.x)) * S.cfg.y;
  var add = 0.0;
  for (var i = 0; i < 4; i++) {
    let drop = S.drops[i];
    if (drop.w != 0.0) {
      let q = vec2f(p) + vec2f(0.5) - drop.xy * S.cfg.zw;
      let k = dot(q, q) / (drop.z * drop.z);
      add += drop.w * (1.0 - k) * exp(-k);
    }
  }
  return vec4f(next + add, c.x + add, 0.0, 1.0);
}
`;

export const VIEW_WGSL = /* wgsl */ `
struct ViewU {
  canvas: vec4f,   // width, height (px), pixel ratio, -
  grid: vec4f,     // grid width, height, photo uv scale x, y (cover)
  bend: vec4f,     // refraction (pt per slope), its limit (pt), chroma spread, normal gain
  light: vec4f,    // highlight power, highlight gain, caustic gain, shade gain
};
@group(0) @binding(0) var<uniform> V: ViewU;
@group(0) @binding(1) var field: texture_2d<f32>;
@group(0) @binding(2) var photo: texture_2d<f32>;
@group(0) @binding(3) var samp: sampler;
${FULL}
fn heightAt(uv: vec2f) -> f32 { return textureSampleLevel(field, samp, uv, 0.0).r; }
fn photoAt(uv: vec2f) -> vec3f {
  let q = (uv - vec2f(0.5)) * V.grid.zw + vec2f(0.5);
  return textureSampleLevel(photo, samp, clamp(q, vec2f(0.001), vec2f(0.999)), 0.0).rgb;
}

@fragment fn viewFs(@builtin(position) pos: vec4f) -> @location(0) vec4f {
  let uv = pos.xy / V.canvas.xy;
  let cell = vec2f(1.0) / V.grid.xy;
  let hc = heightAt(uv);
  let hl = heightAt(uv - vec2f(cell.x, 0.0));
  let hr = heightAt(uv + vec2f(cell.x, 0.0));
  let hu = heightAt(uv - vec2f(0.0, cell.y));
  let hd = heightAt(uv + vec2f(0.0, cell.y));
  let slope = vec2f(hr - hl, hd - hu) * 0.5;

  // refraction, in points, then as a step in uv
  var bend = slope * V.bend.x;
  let far = length(bend);
  if (far > V.bend.y) { bend = bend * (V.bend.y / far); }
  let shift = bend * V.canvas.z / V.canvas.xy;
  let red = photoAt(uv + shift * (1.0 + V.bend.z)).r;
  let green = photoAt(uv + shift).g;
  let blue = photoAt(uv + shift * (1.0 - V.bend.z)).b;
  var col = pow(vec3f(red, green, blue), vec3f(2.2));

  // light from the upper left
  let L = normalize(vec3f(-0.45, -0.6, 0.66));
  let H = normalize(L + vec3f(0.0, 0.0, 1.0));
  let n = normalize(vec3f(-slope * V.bend.w, 1.0));
  let glint = max(pow(max(dot(n, H), 0.0), V.light.x) - pow(H.z, V.light.x), 0.0);
  let lap = hl + hr + hu + hd - 4.0 * hc;
  col = col * (1.0 + clamp(-lap * V.light.z, -0.2, 0.5));
  col = col * (1.0 + clamp(-dot(slope, L.xy) * V.light.w, -0.22, 0.22));
  col = col + glint * V.light.y * vec3f(1.0, 0.98, 0.92);
  return vec4f(pow(clamp(col, vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2)), 1.0);
}
`;

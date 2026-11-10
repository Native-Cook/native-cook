// Glass Aperture Camera — the viewfinder and its iris on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block is a `d.struct` schema), and `tgpu.resolve` injects
// its WGSL into the template. One full-screen pass over the viewfinder:
//  1. depth of field from the street's photo: each pixel's depth (a hand-made depth of this photo:
//     the sky and the street's far end far, the van and the pavement near), its circle of confusion
//     from the f-number and the depth in focus, then a gather over 64 taps laid out in the shape of
//     the iris's nine-sided opening, the bright lights weighted up, so out-of-focus lamps bloom into
//     nonagons that grow as the iris opens;
//  2. the iris itself while it is being set or the shutter fires: nine blades of smoked glass with
//     curved seams round a nine-sided opening, refracting the street under them, in a brushed ring.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  cam: d.vec4f, // the viewfinder's bottom (pt), the photo's height (pt), f-number, depth in focus 0…1
  iris: d.vec4f, // shown 0…1, shutter 0…1, centre x, y (pt)
  ring: d.vec4f, // outer radius (pt), the blades' turn (rad), -, -
});

export const TAPS = 64;

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var photo: texture_2d<f32>;
@group(0) @binding(2) var samp: sampler;

const PI = 3.14159265;
const TAU = 6.2831853;
const BLADES = 9.0;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn rot(a: f32) -> mat2x2f { let c = cos(a); let s = sin(a); return mat2x2f(c, s, -s, c); }

// the photo covers the viewfinder's width, top aligned
fn uvAt(s: vec2f) -> vec2f { return vec2f(s.x / F.view.x, s.y / F.cam.y); }
fn photoAt(s: vec2f) -> vec3f { return lin(textureSampleLevel(photo, samp, clamp(uvAt(s), vec2f(0.001), vec2f(0.999)), 0.0).rgb); }

// the photo's depth, 0 near … 1 far, by hand: far at the street's vanishing point and in the sky, near
// on the van, the pavement and the left building's base
fn depthAt(s: vec2f) -> f32 {
  let uv = uvAt(s);
  let V = vec2f(0.36, 0.64);
  var dd = 1.0 - clamp(length((uv - V) * vec2f(1.5, 1.0)) * 1.9, 0.0, 1.0) * 0.8;
  // the sky between the roofs
  let sky = smoothstep(0.2, 0.26, uv.x) * (1.0 - smoothstep(0.56, 0.62, uv.x + (0.5 - uv.y) * 0.5)) * (1.0 - smoothstep(0.46, 0.52, uv.y));
  dd = max(dd, sky * 0.97);
  // the facades lean in towards the far end
  let right = smoothstep(0.52, 0.6, uv.x) * (1.0 - smoothstep(0.55, 0.6, uv.y));
  dd = mix(dd, 0.55 + 0.35 * (1.0 - uv.x) + 0.15 * (0.55 - uv.y), right * 0.9);
  // the van
  let van = smoothstep(0.48, 0.52, uv.x) * smoothstep(0.565, 0.59, uv.y) * (1.0 - smoothstep(0.89, 0.92, uv.y));
  dd = mix(dd, 0.2, van);
  // the pavement close by
  dd = mix(dd, 0.04, smoothstep(0.82, 0.98, uv.y) * (1.0 - van));
  return clamp(dd, 0.0, 1.0);
}

// how far a point blurs, pt: wider open, more; and the further from the depth in focus, more
fn cocAt(dd: f32) -> f32 { return min(abs(dd - F.cam.w) * 26.0 * (1.4 / F.cam.z), 22.0); }

// the radius of the iris's opening along an angle, for a unit nonagon turned by r
fn nonagon(th: f32, r: f32) -> f32 {
  let k = TAU / BLADES;
  let a = th - r;
  let m = a - k * floor(a / k) - k * 0.5;
  return cos(k * 0.5) / cos(m);
}

fn dof(s: vec2f) -> vec3f {
  let c0 = cocAt(depthAt(s));
  if (c0 < 0.35) { return photoAt(s); }
  var acc = vec3f(0.0);
  var wsum = 0.0;
  let turn = F.ring.y;
  // the spiral of taps turned per pixel, so its pattern dissolves into fine grain
  let jit = fract(sin(dot(floor(s * F.view.z), vec2f(12.9898, 78.233))) * 43758.5453) * TAU;
  for (var i = 0; i < ${TAPS}; i++) {
    let fi = f32(i) + 0.5;
    let rr = sqrt(fi / f32(${TAPS}));
    let th = fi * 2.39996323 + jit;
    let off = vec2f(cos(th), sin(th)) * rr * nonagon(th, turn) * c0;
    let q = s + off;
    let col = photoAt(q);
    // a sample only spreads as far as its own blur reaches (a sharp thing is not smeared over a blur)
    let cq = cocAt(depthAt(q));
    let reach = smoothstep(length(off) - 1.0, length(off) + 1.0, max(cq, c0 * 0.5));
    // lights bloom: the bright pixels count for more
    let lum = dot(col, vec3f(0.3, 0.55, 0.15));
    let w = reach * (1.0 + 14.0 * max(lum - 0.42, 0.0));
    acc += col * w;
    wsum += w;
  }
  return acc / max(wsum, 1e-4);
}

fn studioRing(n: vec2f) -> f32 { return 0.55 + 0.45 * sin(atan2(n.y, n.x) * 2.0 + 0.6); }

struct VOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f }

@vertex fn fullVs(@builtin(vertex_index) vi: u32) -> VOut {
  let uv = vec2f(f32((vi << 1u) & 2u), f32(vi & 2u));
  var o: VOut;
  o.pos = vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0);
  o.uv = uv;
  return o;
}

fn encode(c: vec3f) -> vec3f { return pow(clamp(c, vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2)); }

@fragment fn viewFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  if (s.y > F.cam.x) { return vec4f(0.0); }
  var col = dof(s);
  let show = F.iris.x;
  let snap = F.iris.y;
  if (show > 0.001 || snap > 0.001) {
    let p = s - F.iris.zw;
    let R = F.ring.x;
    let r = length(p);
    let th = atan2(p.y, p.x);
    // the opening: wide at ƒ1.4, small at ƒ16, shut by the shutter
    let u = log(F.cam.z / 1.4) / log(16.0 / 1.4);
    let open = R * mix(0.86, 0.2, u) * (1.0 - snap);
    let turn = F.ring.y + u * 0.9 + snap * 0.5;
    let edge = open * nonagon(th, turn);
    let a = max(show, snap);
    if (r < R + 9.0) {
      if (r > edge && r < R) {
        // a blade: which one, by an angle bent along the seams' curve
        let k = TAU / BLADES;
        let bent = th - turn - 1.2 * (r - edge) / R;
        let f = bent / k - floor(bent / k);
        let idx = floor(bent / k);
        // smoked glass: the street under it, shifted towards the centre and darkened, each blade a
        // little different, a soft sheen across, the seams dark with a lit lip
        let refr = photoAt(s - p / max(r, 1.0) * 10.0 * (1.0 - (r - edge) / R));
        let shade = 0.22 + 0.06 * sin(idx * 2.3);
        var g = refr * shade * lin(vec3f(0.85, 0.9, 1.0));
        g += vec3f(0.9, 0.95, 1.0) * 0.12 * pow(max(sin(th * 1.0 + 0.8), 0.0), 6.0);
        g += vec3f(1.0) * 0.5 * exp(-sq(f / 0.012)) * smoothstep(edge, edge + 10.0, r);
        g *= 1.0 - 0.6 * exp(-sq((1.0 - f) / 0.02));
        // the opening's rim, lit
        g += vec3f(1.0, 0.97, 0.92) * 0.6 * exp(-sq((r - edge) / 1.2));
        col = mix(col, g, a);
      } else if (r >= R) {
        // the brushed ring
        let n = p / max(r, 1.0);
        let m = vec3f(0.42, 0.43, 0.46) * studioRing(n) + vec3f(0.15) * fract(sin(r * 91.0) * 437.0) * 0.3;
        col = mix(col, m * m, a);
      } else {
        // looking through: a faint vignette on the opening
        col *= 1.0 - 0.25 * a * smoothstep(edge * 0.6, edge, r);
      }
    }
    // outside the ring the screen dims a little while the iris shows
    col *= 1.0 - 0.25 * show * smoothstep(R + 8.0, R + 40.0, r);
  }
  return vec4f(encode(col), 1.0);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}

// Raffle Drum Spin — the WebGPU shaders, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout: the uniform blocks are `d.struct` schemas, written from JS with
// typed `buffer.write()`, and `tgpu.resolve` injects their WGSL definitions into the template
// below (the names FrameU and BallU are replaced by the resolved structs).

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  viewport: d.vec4f, // canvas width, height (px), pixel ratio, time (s)
  drum: d.vec4f, // centre x, y (px), focal length (px), cage angle
  cam: d.vec4f, // cos/sin yaw, cos/sin pitch
  misc: d.vec4f, // camera distance, spin speed, page glow, seconds since the foil burst
  burst: d.vec4f, // burst origin x, y (px), radius (px), confetti count
  ghost: d.vec4f, // ring ghost angle step, ghost count, -, -
});

export const BallU = d.struct({
  pos: d.vec4f, // centre x, y (px), radius (px), camera depth
  rot: d.vec4f, // rotation about x, y, z; alpha
  fx: d.vec4f, // winner glow, -, -, -
});

export const BALLS = 3;
export const CONFETTI = 200;

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> frame: FrameU;
@group(0) @binding(1) var<uniform> balls: array<BallU, ${BALLS}>;
@group(0) @binding(2) var envTex: texture_2d<f32>;
@group(0) @binding(3) var envSamp: sampler;

const NEAR = 2.8;
const FAR = 9.5;

fn depthOf(z: f32) -> f32 { return clamp((z - NEAR) / (FAR - NEAR), 0.0, 1.0); }

fn toClip(px: vec2f, z: f32) -> vec4f {
  let ndc = vec2f(px.x / frame.viewport.x * 2.0 - 1.0, 1.0 - px.y / frame.viewport.y * 2.0);
  return vec4f(ndc * z, depthOf(z) * z, z);
}

fn rotX(v: vec3f, a: f32) -> vec3f { let s = sin(a); let c = cos(a); return vec3f(v.x, c * v.y - s * v.z, s * v.y + c * v.z); }
fn rotY(v: vec3f, a: f32) -> vec3f { let s = sin(a); let c = cos(a); return vec3f(c * v.x + s * v.z, v.y, -s * v.x + c * v.z); }
fn rotZ(v: vec3f, a: f32) -> vec3f { let s = sin(a); let c = cos(a); return vec3f(c * v.x - s * v.y, s * v.x + c * v.y, v.z); }

fn toView(p: vec3f) -> vec3f {
  let x1 = frame.cam.x * p.x + frame.cam.y * p.z;
  let z1 = -frame.cam.y * p.x + frame.cam.x * p.z;
  let y2 = frame.cam.z * p.y - frame.cam.w * z1;
  let z2 = frame.cam.w * p.y + frame.cam.z * z1;
  return vec3f(x1, y2, z2);
}

// A studio around the drum: a violet room, a big soft key above, two tall strip lights and a
// bright rim from the page's glow behind. Chrome is only as good as what it reflects.
fn studio(r: vec3f) -> vec3f {
  let up = r.y;
  var c = mix(vec3f(0.08, 0.06, 0.14), vec3f(0.46, 0.42, 0.56), smoothstep(-0.7, 0.25, up));
  c += vec3f(1.0, 0.98, 1.02) * smoothstep(0.3, 0.9, up) * 1.25;
  let strip1 = exp(-pow((r.x - 0.6) / 0.11, 2.0)) * smoothstep(-0.4, 0.4, up);
  let strip2 = exp(-pow((r.x + 0.66) / 0.09, 2.0)) * smoothstep(-0.3, 0.5, up) * 0.8;
  c += vec3f(1.0, 0.98, 1.0) * (strip1 * 1.5 + strip2);
  c += vec3f(0.5, 0.38, 0.85) * exp(-pow((r.z - 0.9) / 0.4, 2.0)) * 0.45;
  c -= vec3f(0.18) * exp(-pow((up + 0.05) / 0.07, 2.0));
  return max(c, vec3f(0.0));
}

// The studio photographed as a 360° panorama (env-studio.jpg); the procedural studio stands in
// until it has loaded (ghost.z = 0).
fn reflection(r: vec3f) -> vec3f {
  if (frame.ghost.z < 0.5) { return studio(r); }
  let u = atan2(r.x, r.z) / 6.2831853 + 0.5;
  let v = acos(clamp(r.y, -1.0, 1.0)) / 3.14159265;
  var e = textureSampleLevel(envTex, envSamp, vec2f(u, v), 0.0).rgb;
  // The reference's chrome is neutral silver: keep only a hint of the room's violet, and stretch
  // the LDR photo back towards HDR (its softboxes are far brighter than its walls).
  e = mix(vec3f(dot(e, vec3f(0.3, 0.55, 0.15))), e, 0.35);
  // A big soft fill behind the camera: what makes the wires read silver down their middle.
  let fill = vec3f(0.78, 0.77, 0.84) * smoothstep(0.15, -0.75, r.z) * (0.55 + 0.45 * smoothstep(-0.6, 0.5, r.y));
  return e * 1.3 + pow(e, vec3f(2.2)) * 2.4 + studio(r) * 0.4 + fill;
}

struct DrumOut {
  @builtin(position) pos: vec4f,
  @location(0) n: vec3f,
  @location(1) v: vec3f,
  @location(2) @interpolate(flat) mat: f32,
  @location(3) alpha: f32,
};

// Instance 0 is the drum; instances 1… are motion ghosts of what spins, a little behind it, so a
// fast cage smears like a real one filmed at 1/60 s instead of strobing.
@vertex fn drumVs(@location(0) p: vec3f, @location(1) n: vec3f, @location(2) mat: f32, @location(3) spins: f32, @builtin(instance_index) ii: u32) -> DrumOut {
  var wp = p;
  var wn = n;
  let ghost = f32(ii);
  if (spins > 0.5) {
    let a = frame.drum.w - ghost * frame.ghost.x;
    wp = rotX(p, a);
    wn = rotX(n, a);
  }
  let vp = toView(wp);
  let vn = toView(wn);
  let z = frame.misc.x + vp.z;
  let px = frame.drum.xy + vec2f(vp.x, -vp.y) * frame.drum.z / z;
  var out: DrumOut;
  out.pos = toClip(px, z);
  // Camera space: the eye at the origin looking down +z, +y up.
  out.n = vn;
  out.v = -vec3f(vp.x, vp.y, z);
  out.mat = mat;
  out.alpha = select(1.0, 0.55 * (1.0 - ghost / (frame.ghost.y + 1.0)), ii > 0u);
  if (ii > 0u && spins < 0.5) { out.pos = vec4f(2.0, 2.0, 0.0, 1.0); }
  return out;
}

@fragment fn drumFs(i: DrumOut) -> @location(0) vec4f {
  let v = normalize(i.v);
  var n = normalize(i.n);
  if (dot(n, v) < 0.0) { n = -n; }
  let nv = max(dot(n, v), 0.0);
  let r = reflect(-v, n);
  var env = reflection(r);
  var tint = vec3f(0.93, 0.93, 0.98);
  var rough = 0.0;
  if (i.mat > 0.5 && i.mat < 1.5) { tint = vec3f(0.82, 0.82, 0.88); env = mix(env, reflection(normalize(n + vec3f(0.0, 0.3, 0.0))), 0.35); }
  // The base: lacquered gunmetal, dark but glossy, so it mirrors the violet room along its edges
  // instead of reading as a flat grey slab.
  if (i.mat > 1.5 && i.mat < 2.5) { tint = vec3f(0.34, 0.33, 0.4); env = mix(env, reflection(n), 0.35) * 0.8 + vec3f(0.035, 0.03, 0.05); }
  if (i.mat > 2.5) { tint = vec3f(0.05, 0.045, 0.06); rough = 1.0; }
  let fres = 0.55 + 0.45 * pow(1.0 - nv, 4.0);
  var col = env * tint * fres;
  if (rough > 0.5) {
    // The handle's black lacquer: a sharp key highlight and a violet rim from the room.
    let l = normalize(vec3f(-0.4, 0.8, 0.5));
    col = tint + vec3f(1.0, 0.97, 0.94) * pow(max(dot(reflect(-l, n), v), 0.0), 48.0) * 0.9 + vec3f(0.3, 0.18, 0.62) * pow(1.0 - nv, 2.5) * 0.8;
  }
  // A warm key from the upper left, the gold of the ticket to come, as thin highlights on the metal.
  let key = normalize(vec3f(-0.55, 0.7, 0.45));
  col += vec3f(1.0, 0.86, 0.62) * pow(max(dot(r, key), 0.0), 90.0) * 0.55 * (1.0 - rough);
  col += vec3f(0.5, 0.3, 1.0) * pow(1.0 - nv, 3.0) * 0.16 * (1.0 + frame.misc.z);
  return vec4f(col * i.alpha, i.alpha);
}

// ── Balls: camera-facing impostors with a real sphere's normal and depth ──

struct BallOut {
  @builtin(position) pos: vec4f,
  @location(0) q: vec2f,
  @location(1) @interpolate(flat) id: u32,
};

@vertex fn ballVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> BallOut {
  let corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, 1.0));
  let b = balls[ii];
  let margin = 1.0 + 0.9 * b.fx.x;
  let q = corners[vi] * margin;
  var out: BallOut;
  let px = b.pos.xy + q * b.pos.z;
  out.pos = toClip(px, b.pos.w);
  if (b.rot.w <= 0.001) { out.pos = vec4f(2.0, 2.0, 0.0, 1.0); }
  out.q = q;
  out.id = ii;
  return out;
}

fn seam(t: f32) -> vec3f {
  let A = 0.78;
  let B = 0.22;
  return vec3f(A * cos(t) + B * cos(3.0 * t), A * sin(t) - B * sin(3.0 * t), 2.0 * sqrt(A * B) * sin(2.0 * t));
}

struct BallFrag {
  @location(0) color: vec4f,
  @builtin(frag_depth) depth: f32,
};

@fragment fn ballFs(i: BallOut) -> BallFrag {
  let b = balls[i.id];
  let d2 = dot(i.q, i.q);
  var out: BallFrag;
  let glow = b.fx.x;
  if (d2 > 1.0) {
    // The winner's halo, outside the ball.
    let rr = sqrt(d2);
    let h = glow * exp(-(rr - 1.0) * 3.2) * (1.0 - smoothstep(1.5, 1.9, rr));
    if (h < 0.004) { discard; }
    let hc = mix(vec3f(1.0, 0.86, 0.55), vec3f(0.72, 0.55, 1.0), smoothstep(1.0, 1.6, rr));
    out.color = vec4f(hc * h, h) * b.rot.w;
    out.depth = depthOf(b.pos.w);
    return out;
  }
  let n = vec3f(i.q.x, -i.q.y, sqrt(1.0 - d2));
  let o = rotZ(rotY(rotX(n, b.rot.x), b.rot.y), b.rot.z);
  var best = 9.0;
  var bt = 0.0;
  for (var k = 0; k < 48; k++) {
    let t = f32(k) * 0.1309;
    let dd = distance(o, seam(t));
    if (dd < best) { best = dd; bt = t; }
  }
  for (var k = 0; k < 6; k++) {
    let h = 0.065 / f32(k + 1);
    let da = distance(o, seam(bt - h));
    let db = distance(o, seam(bt + h));
    if (da < best) { best = da; bt -= h; }
    if (db < best) { best = db; bt += h; }
  }
  let sp = seam(bt);
  let tg = normalize(seam(bt + 0.01) - seam(bt - 0.01));
  let vv = abs(dot(o - sp, cross(sp, tg)));
  let u = bt * 24.0 / 6.2831853;
  let line = abs(fract(u * 4.0 + vv * 9.0) - 0.5);
  let stitch = (1.0 - smoothstep(0.1, 0.2, line)) * smoothstep(0.018, 0.03, vv) * (1.0 - smoothstep(0.075, 0.09, vv));
  let groove = 1.0 - smoothstep(0.0, 0.016, vv);
  let L = normalize(vec3f(-0.5, 0.6, 0.65));
  let diff = 0.5 + 0.5 * dot(n, L);
  let spec = pow(max(0.0, dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0))), 22.0);
  let rim = pow(1.0 - n.z, 2.2);
  var col = vec3f(0.97, 0.95, 0.91) * (0.42 + 0.62 * diff);
  col = mix(col, col * 0.8, groove * 0.6);
  col = mix(col, vec3f(0.78, 0.14, 0.16), stitch);
  col += vec3f(0.55, 0.35, 1.0) * rim * 0.45;
  col += vec3f(spec * 0.35);
  col *= 1.0 - 0.35 * smoothstep(0.2, -0.9, n.y);
  col += vec3f(1.0, 0.85, 0.5) * glow * rim * 0.8;
  let edge = 1.0 - smoothstep(1.0 - 2.0 / b.pos.z, 1.0, sqrt(d2));
  let a = edge * b.rot.w;
  out.color = vec4f(col * a, a);
  // A real sphere's depth, so the cage's front rings cross in front of it.
  let worldR = b.pos.z * b.pos.w / frame.drum.z;
  out.depth = depthOf(b.pos.w - worldR * n.z);
  return out;
}

// ── Foil confetti: pieces of a photographed atlas (gold leaf, ribbons, sequins, holographic foil),
// tumbling in 3D out of the golden ticket, with sparkles and a few out-of-focus orbs in front ──

@group(0) @binding(4) var confTex: texture_2d<f32>;
@group(0) @binding(5) var confSamp: sampler;

struct FoilOut {
  @builtin(position) pos: vec4f,
  @location(0) n: vec3f,
  @location(1) a: f32,
  @location(2) hue: f32,
  @location(3) uv: vec2f,
  @location(4) @interpolate(flat) kind: f32,
};

fn h1(i: f32, s: f32) -> f32 { return fract(sin(i * 127.1 + s * 311.7) * 43758.5453); }

// The atlas cells (4 × 4): 0–11 foil, 12–13 sparkles, 14–15 bokeh. Each foil cell's size on screen
// (the squares and the sequin read larger than the slips at the same quad size).
fn cellScale(cell: f32) -> f32 {
  if (cell == 0.0 || cell == 1.0) { return 0.9; }
  if (cell == 4.0 || cell == 10.0) { return 0.62; }
  if (cell == 7.0) { return 0.55; }
  if (cell == 11.0) { return 0.7; }
  return 1.0;
}

@vertex fn foilVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> FoilOut {
  let corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, 1.0));
  let i = f32(ii);
  let t = frame.misc.w;
  var out: FoilOut;
  if (t < 0.0) { out.pos = vec4f(2.0, 2.0, 0.0, 1.0); out.a = 0.0; return out; }
  let pr = frame.viewport.z;
  let pick = h1(i, 14.0);
  var kind = 0.0;
  var cell = floor(h1(i, 15.0) * 11.999);
  // Gold most of all: half the foil pieces are re-drawn from the gold cells.
  if (h1(i, 16.0) < 0.35) { cell = floor(h1(i, 17.0) * 3.999); }
  if (pick > 0.86) { kind = 1.0; cell = 12.0 + step(0.5, h1(i, 18.0)); }
  if (pick > 0.96) { kind = 2.0; cell = 14.0 + step(0.6, h1(i, 18.0)); }
  // Thrown out from the ticket's rim (the ticket hides their start), mostly sideways and up,
  // then air drag takes their speed and they flutter down.
  let ang = h1(i, 1.0) * 6.2831853;
  let up = h1(i, 2.0);
  var sp = (260.0 + 560.0 * pow(h1(i, 3.0), 0.8)) * pr;
  if (kind == 2.0) { sp *= 0.45; }
  let drag = 3.2;
  let travel = sp * (1.0 - exp(-drag * t)) / drag;
  let dir = normalize(vec2f(cos(ang) * 1.25, sin(ang) * 0.75 - 0.45 * up));
  var fall = 150.0 * pr * t * t * 0.5 + 26.0 * pr * t;
  if (kind == 2.0) { fall *= 0.3; }
  let sway = sin(t * (3.0 + 3.0 * h1(i, 4.0)) + i) * 14.0 * pr * smoothstep(0.25, 1.0, t);
  let c = frame.burst.xy + dir * (frame.burst.z * 0.7 + travel) + vec2f(sway, fall);
  var half = (8.0 + 6.0 * pow(h1(i, 5.0), 1.6)) * pr * cellScale(cell);
  var rp: vec3f;
  var rn = vec3f(0.0, 0.0, 1.0);
  let corner = corners[vi];
  if (kind == 0.0) {
    let axis = normalize(vec3f(h1(i, 7.0) - 0.5, h1(i, 8.0) - 0.5, h1(i, 9.0) - 0.5) + vec3f(0.0, 0.0, 0.01));
    let spin = t * (5.0 + 8.0 * h1(i, 10.0)) * exp(-t * 0.35) + h1(i, 11.0) * 6.28;
    let local = vec3f(corner * half, 0.0);
    let ca = cos(spin);
    let sa = sin(spin);
    rp = local * ca + cross(axis, local) * sa + axis * dot(axis, local) * (1.0 - ca);
    rn = vec3f(0.0, 0.0, 1.0) * ca + cross(axis, vec3f(0.0, 0.0, 1.0)) * sa + axis * axis.z * (1.0 - ca);
  } else if (kind == 1.0) {
    // A sparkle twinkles: it flares and fades a few times as it drifts.
    half = (9.0 + 12.0 * h1(i, 5.0)) * pr * (0.55 + 0.45 * sin(t * (9.0 + 6.0 * h1(i, 19.0)) + i * 2.0));
    let r = h1(i, 20.0) * 0.6;
    rp = vec3f(vec2f(corner.x * cos(r) - corner.y * sin(r), corner.x * sin(r) + corner.y * cos(r)) * half, 0.0);
  } else {
    half = (13.0 + 14.0 * h1(i, 5.0)) * pr;
    rp = vec3f(corner * half, 0.0);
  }
  out.pos = toClip(c + rp.xy, 3.0);
  out.n = rn;
  let life = 1.0 - smoothstep(1.4 + 0.6 * h1(i, 12.0), 2.3, t);
  out.a = life * smoothstep(0.0, 0.05, t);
  if (kind == 2.0) { out.a *= 0.32 * smoothstep(0.0, 0.3, t); }
  out.hue = h1(i, 13.0);
  let col = cell - 4.0 * floor(cell / 4.0);
  let row = floor(cell / 4.0);
  // The atlas's level 0 is the left 1024 × 1024 of its 1536 × 1024 image (mips beside it).
  out.uv = (vec2f(col, row) + corner * 0.5 + 0.5) / 4.0;
  out.kind = kind;
  return out;
}

@fragment fn foilFs(i: FoilOut) -> @location(0) vec4f {
  // Sampled first, in uniform control flow (the branches below return early).
  let tex = textureSample(confTex, confSamp, i.uv);
  let n = normalize(i.n);
  let facing = abs(n.z);
  if (frame.ghost.w < 0.5) {
    // Until the atlas has loaded: plain gold and violet slips.
    var base = vec3f(0.98, 0.76, 0.36);
    if (i.hue > 0.6) { base = vec3f(0.62, 0.42, 1.0); }
    if (i.kind > 0.5) { return vec4f(0.0); }
    let col = base * (0.3 + 0.7 * facing) + vec3f(1.0, 0.97, 0.9) * pow(facing, 30.0) * 0.8;
    return vec4f(col * i.a, i.a);
  }
  if (i.kind == 1.0) {
    // Light carries coverage equal to its brightness: a zero-alpha pixel is dropped when the
    // transparent canvas is composited.
    let lc = min(tex.rgb * tex.a * i.a * 1.4, vec3f(1.0));
    return vec4f(lc, max(lc.r, max(lc.g, lc.b)));
  }
  if (i.kind == 2.0) {
    return vec4f(tex.rgb * tex.a * i.a, tex.a * i.a);
  }
  // Foil: darker as it turns edge-on, and a flash off its brightest facets when it faces the light.
  let lum = dot(tex.rgb, vec3f(0.3, 0.55, 0.15));
  var col = tex.rgb * (0.38 + 0.75 * facing);
  col += vec3f(1.0, 0.96, 0.86) * pow(facing, 18.0) * smoothstep(0.35, 0.9, lum) * 0.9;
  let a = tex.a * i.a;
  return vec4f(col * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, BallU } });
}

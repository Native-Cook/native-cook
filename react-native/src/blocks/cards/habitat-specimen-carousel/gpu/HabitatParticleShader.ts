/**
 * One particle world for the habitat, simulated on the GPU (a compute pass) and drawn as instanced
 * quads in two layers: behind the creatures (depth < 0) and in front of them.
 *
 * - Ambient motes belong to whichever habitat they float in: embers in fire, bubbles in water,
 *   pollen in grass. When the habitat front passes one, it morphs into the new habitat's mote
 *   (look, size and physics) instead of fading out, so the world is continuous across a swipe.
 * - A finger stirs everything it passes (a wake that follows its velocity, a swirl, a push), a tap
 *   sends a ring of air out, a landing kicks a gust along the floor, and tilting the phone turns
 *   which way is up for everything buoyant.
 * - A signature move spawns its own particles from the posed body at the moment of their birth:
 *   embers off the flame's tip, spray off the cannons that lands and splashes, leaves and pollen
 *   off the bulb. Leaves flutter down, settle flat, can be blown up again by a finger, then fade.
 *
 * Units are screen points; the canvas covers the screen from its top to below the stage.
 */
export const PARTICLE_AMBIENT = 120;
export const PARTICLE_COUNT = 520;
/** Floats per particle in the storage buffer (Particle below). */
export const PARTICLE_STRIDE = 18;
/** Floats in the frame uniform (Frame below). */
export const PARTICLE_FRAME = 48;

export const HABITAT_PARTICLE_WGSL = /* wgsl */ `
struct Frame {
  canvas: vec4f,   // width, height (points), pixel scale, dt (seconds)
  clock: vec4f,    // ambient seconds, carousel position, frame number, seam phase
  stage: vec4f,    // screen height, stage top, stage bottom, exchange drift (points)
  touch: vec4f,    // finger x, y, velocity x, y
  press: vec4f,    // contact 0..1, petting 0..1, reach (points), live (0 = frozen)
  tap: vec4f,      // x, y, seconds since, unused
  land: vec4f,     // feet x, y, seconds since, unused
  gravity: vec4f,  // which way is down (x, y), unused, unused
  fx: vec4f,       // effect clock last frame, this frame, performer, unit
  emitA: vec4f,    // posed point, aim
  emitB: vec4f,    // posed point, aim
  tip: vec4f,      // flame tip x, y, half width, floor y
};
struct Particle {
  pos: vec2f, vel: vec2f,
  life: f32, span: f32, kind: f32, look: f32, prev: f32, morph: f32,
  depth: f32, seed: f32, angle: f32, turn: f32, phase: f32, ground: f32, heat: f32, size: f32,
};
@group(0) @binding(0) var<uniform> frame: Frame;
@group(0) @binding(1) var<storage, read_write> particles: array<Particle>;

const AMBIENT: u32 = ${PARTICLE_AMBIENT}u;
const COUNT: u32 = ${PARTICLE_COUNT}u;
// Signature slots, counted from AMBIENT.
const EMBERS: u32 = 96u;
const DROPS: u32 = 256u;
const LEAVES: u32 = 280u;
const POLLEN: u32 = 400u;
const MOVE: f32 = 1.8;
// A leaf lives until it has settled and faded; this is only its upper bound.
const LEAF_SPAN: f32 = 9.0;
const TAU: f32 = 6.28318530718;

fn pcg(v: u32) -> u32 {
  let s = v * 747796405u + 2891336453u;
  let w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
  return (w >> 22u) ^ w;
}
fn rand(a: u32, b: u32) -> f32 { return f32(pcg(a * 1973u + pcg(b + 17u))) / 4294967295.0; }
// Per-slot values that never change, for a move's schedule.
fn fixed(slot: u32, k: u32) -> f32 { return rand(slot + 7919u, k * 104729u); }
// Fresh values for a respawn.
fn fresh(i: u32, k: u32) -> f32 { return rand(i, k + u32(frame.clock.z) * 31u); }

fn seamLine(y: f32, f: f32, phase: f32, arriving: f32) -> f32 {
  let bow = sin(3.14159265 * f);
  var seam = 1.08 - 1.16 * f + bow * (0.040 * sin(y * 5.5 + phase) + 0.018 * sin(y * 14.0 - phase * 2.0 + 1.3));
  if (arriving > 1.5) { seam -= 0.011 * abs(sin(y * 46.0 + phase)) * bow; }
  return seam;
}
fn wrap3(x: f32) -> f32 { return x - 3.0 * floor(x / 3.0); }
// The habitat at a point: the leaving one on its side of the front, the arriving one beyond.
fn elementAt(p: vec2f) -> f32 {
  let q = wrap3(frame.clock.y);
  let leaving = floor(q);
  let f = q - leaving;
  if (f < 0.0005) { return leaving; }
  let arriving = wrap3(leaving + 1.0);
  let seam = seamLine(p.y / frame.stage.x, f, frame.clock.w, arriving);
  return select(arriving, leaving, p.x < seam * frame.canvas.x);
}
// Which side of the front a world is on: 1 shown, 0 wiped; and its parallax drift.
fn worldSide(world: f32, p: vec2f) -> vec2f {
  let q = wrap3(frame.clock.y);
  let leaving = floor(q);
  let f = q - leaving;
  if (f < 0.0005) { return vec2f(select(0.0, 1.0, abs(world - leaving) < 0.5), 0.0); }
  let arriving = wrap3(leaving + 1.0);
  let edge = (p.x - seamLine(p.y / frame.stage.x, f, frame.clock.w, arriving) * frame.canvas.x);
  let side = smoothstep(-1.0, 1.0, edge);
  if (abs(world - leaving) < 0.5) { return vec2f(1.0 - side, -0.5 * f * f * frame.stage.w); }
  if (abs(world - arriving) < 0.5) { return vec2f(side, (1.0 - f) * (1.0 - f) * frame.stage.w); }
  return vec2f(0.0, 0.0);
}
// A slow divergence-free current: the curl of a moving stream function.
fn curl(p: vec2f, t: f32) -> vec2f {
  let e = 1.5;
  let a = stream(p + vec2f(0.0, e), t) - stream(p - vec2f(0.0, e), t);
  let b = stream(p + vec2f(e, 0.0), t) - stream(p - vec2f(e, 0.0), t);
  return vec2f(a, -b) / (2.0 * e);
}
fn stream(p: vec2f, t: f32) -> f32 {
  return 38.0 * sin(p.x * 0.031 + t * 0.9) * cos(p.y * 0.027 - t * 0.7) + 17.0 * sin(p.x * 0.067 - t * 1.3 + p.y * 0.041);
}
// The finger, the tap ring and the landing gust, as an acceleration, plus how close the finger is.
fn stir(q: Particle) -> vec3f {
  var acc = vec2f(0.0);
  let reach = frame.press.z;
  let d = q.pos - frame.touch.xy;
  let r2 = dot(d, d);
  let near = exp(-r2 / (reach * reach)) * frame.press.x;
  let dist = sqrt(r2) + 0.001;
  let away = d / dist;
  // A wake: drawn along at the finger's velocity; a swirl around it; a soft push out of its way.
  acc += (frame.touch.zw - q.vel) * near * 7.0;
  acc += vec2f(-away.y, away.x) * min(length(frame.touch.zw), 900.0) * near * 1.6;
  acc += away * 260.0 * near;
  // Petting gathers the motes into a slow orbit round the finger.
  let held = frame.press.y * exp(-r2 / (reach * reach * 4.0));
  acc += (vec2f(-away.y, away.x) * 70.0 - away * 50.0 * smoothstep(0.0, reach, dist)) * held;
  if (frame.tap.z >= 0.0 && frame.tap.z < 0.6) {
    let t = frame.tap.xy;
    let dd = q.pos - t;
    let l = length(dd) + 0.001;
    let x = (l - frame.tap.z * 520.0) / 26.0;
    let ring = exp(-x * x) * (1.0 - frame.tap.z / 0.6);
    acc += dd / l * 2400.0 * ring;
  }
  if (frame.land.z >= 0.0 && frame.land.z < 0.5) {
    let dd = q.pos - frame.land.xy;
    let l = length(dd) + 0.001;
    let x = (l - frame.land.z * 380.0) / 30.0;
    let ring = exp(-x * x) * (1.0 - frame.land.z / 0.5) * exp(-abs(dd.y) / 60.0);
    acc += (dd / l + vec2f(0.0, -0.6)) * 1500.0 * ring;
  }
  return vec3f(acc, near + held * 0.5);
}

fn ambientSize(element: f32, seed: f32) -> f32 {
  if (element < 0.5) { return 1.0 + 1.3 * seed; }
  if (element < 1.5) { return 1.8 + 3.4 * seed; }
  return 0.85 + 1.0 * seed;
}
fn spawnAmbient(i: u32) -> Particle {
  var q: Particle;
  var p = vec2f(fresh(i, 1u) * frame.canvas.x, mix(frame.stage.y, frame.stage.z, fresh(i, 2u)));
  let element = elementAt(p);
  let top = frame.stage.y;
  let bottom = frame.stage.z;
  if (element < 0.5) { p.y = bottom - fresh(i, 3u) * (bottom - top) * 0.35; }
  else if (element < 1.5) { p.y = bottom - fresh(i, 3u) * (bottom - top) * 0.3; }
  q.pos = p;
  q.vel = vec2f(0.0, select(0.0, -40.0, element < 0.5));
  q.life = 0.0;
  q.span = select(select(4.0 + 4.0 * fresh(i, 4u), 3.0 + 3.0 * fresh(i, 4u), element < 1.5), 1.3 + 1.6 * fresh(i, 4u), element < 0.5);
  q.kind = 0.0;
  q.look = element;
  q.prev = element;
  q.morph = 1.0;
  q.depth = fresh(i, 5u) * 2.0 - 1.0;
  q.seed = fresh(i, 6u);
  q.angle = 0.0; q.turn = 0.0; q.phase = fresh(i, 7u) * TAU; q.ground = 0.0; q.heat = 0.0;
  q.size = 0.0;
  return q;
}
fn ambientStep(i: u32, q0: Particle, dt: f32) -> Particle {
  var q = q0;
  let out = q.pos.x < -30.0 || q.pos.x > frame.canvas.x + 30.0 || q.pos.y < frame.stage.y - 90.0 || q.pos.y > frame.stage.z + 30.0;
  if (q.span <= 0.0 || q.life > q.span || out) { return spawnAmbient(i); }
  // The front passing over a mote morphs it into the new habitat's mote.
  let element = elementAt(q.pos);
  if (abs(element - q.look) > 0.5) {
    q.prev = select(q.look, q.prev, q.morph < 0.5);
    q.look = element;
    q.morph = 0.0;
    q.span = max(q.span, q.life + select(3.0, 1.6, element < 0.5));
  }
  q.morph = min(1.0, q.morph + dt / 0.45);
  let up = -frame.gravity.xy;
  let t = frame.clock.x;
  var acc = vec2f(0.0);
  var drag = 0.0;
  for (var k = 0; k < 2; k++) {
    let e = select(q.prev, q.look, k == 1);
    let w = select(1.0 - q.morph, q.morph, k == 1);
    if (e < 0.5) {
      acc += (up * 120.0 + curl(q.pos, t) * 2.2) * w;
      drag += 1.4 * w;
    } else if (e < 1.5) {
      acc += (up * 95.0 + vec2f(sin(t * 4.5 + q.seed * 20.0) * 38.0, 0.0)) * w;
      drag += 2.4 * w;
    } else {
      acc += (vec2f(34.0, sin(t * 1.7 + q.seed * 9.0) * 14.0) + up * 6.0 + curl(q.pos, t * 0.6) * 0.8) * w;
      drag += 1.7 * w;
    }
  }
  let s = stir(q);
  let depth = 0.7 + 0.3 * (q.depth * 0.5 + 0.5);
  q.vel += (acc + s.xy) * dt * depth;
  q.vel *= exp(-drag * dt);
  q.pos += q.vel * dt;
  q.heat = max(0.0, q.heat - dt * 1.8) + s.z * dt * 6.0;
  q.life += dt;
  return q;
}

// A signature slot's birth time on the effect clock, and the habitat it belongs to.
fn birth(s: u32) -> vec2f {
  if (s < EMBERS) { return vec2f((0.12 + 0.52 * fixed(s, 1u)) * MOVE, 0.0); }
  if (s < DROPS) { return vec2f((0.15 + 0.40 * fixed(s, 1u)) * MOVE + 0.012 + 0.09 * fixed(s, 2u), 1.0); }
  if (s < LEAVES) { return vec2f(0.56 + 0.022 * f32(s - DROPS) + 0.02 * fixed(s, 1u), 2.0); }
  return vec2f((0.36 + 0.30 * fixed(s, 1u)) * MOVE, 2.0);
}
fn spawnSignature(s: u32, world: f32) -> Particle {
  var q: Particle;
  let u = frame.fx.w;
  q.life = 0.0; q.look = world; q.prev = world; q.morph = 1.0; q.seed = fixed(s, 3u);
  q.angle = 0.0; q.turn = 0.0; q.phase = fixed(s, 4u) * TAU; q.ground = 0.0; q.heat = 0.0;
  q.depth = fixed(s, 5u) * 2.0 - 1.0;
  if (s < EMBERS) {
    q.kind = 1.0;
    q.pos = frame.tip.xy + vec2f((fixed(s, 6u) - 0.5) * frame.tip.z, 0.0);
    q.vel = vec2f((fixed(s, 7u) - 0.62) * 95.0, -(110.0 + 150.0 * fixed(s, 8u))) * u;
    q.span = 0.5 + 0.4 * fixed(s, 9u);
    q.size = (1.1 + 1.5 * fixed(s, 10u)) * u;
    q.depth = abs(q.depth);
  } else if (s < DROPS) {
    // Torn off the jet near the muzzle: part of its speed, sideways spread, then gravity.
    q.kind = 2.0;
    let fromB = (s & 1u) == 1u;
    let emit = select(frame.emitA, frame.emitB, fromB);
    let aim = normalize(emit.zw);
    let speed = 640.0 * u;
    let shed = 0.012 + 0.09 * fixed(s, 2u);
    let g = 1500.0 * u;
    let jet = aim * speed + vec2f(0.0, g * shed);
    let spread = (fixed(s, 6u) - 0.5) * 1.2;
    let cs = cos(spread); let sn = sin(spread);
    q.pos = emit.xy + aim * speed * shed + vec2f(0.0, 0.5 * g * shed * shed);
    q.vel = vec2f(jet.x * cs - jet.y * sn, jet.x * sn + jet.y * cs) * (0.06 + 0.34 * fixed(s, 7u));
    q.span = 3.0;
    q.size = (1.8 + 2.4 * fixed(s, 8u)) * u;
  } else if (s < LEAVES) {
    q.kind = 4.0;
    let k = f32(s - DROPS);
    let launch = -1.5708 + ((k + 0.5) / 24.0 - 0.5) * 2.6 + (fixed(s, 6u) - 0.5) * 0.24;
    q.pos = frame.emitA.xy;
    q.vel = vec2f(cos(launch), sin(launch)) * (220.0 + 90.0 * fixed(s, 7u)) * u;
    q.span = LEAF_SPAN;
    q.size = u;
    q.angle = fixed(s, 8u) * TAU;
    q.turn = (fixed(s, 9u) - 0.5) * 24.0;
  } else {
    q.kind = 5.0;
    let angle = -1.5708 + (fixed(s, 6u) - 0.5) * 2.5;
    q.pos = frame.emitA.xy;
    q.vel = vec2f(cos(angle), sin(angle)) * (120.0 + 220.0 * fixed(s, 7u)) * u;
    q.span = 1.45;
    q.size = (1.3 + 1.8 * fixed(s, 8u)) * u;
  }
  return q;
}
// Where a particle meets the floor: in front of the feet or, for the far ones, behind them.
fn floorAt(q: Particle) -> f32 {
  let u = frame.fx.w;
  return frame.tip.w + select(-(4.0 + 14.0 * abs(q.depth)), 3.0 + 18.0 * q.depth, q.depth >= 0.0) * u;
}
fn signatureStep(i: u32, q0: Particle, dt: f32) -> Particle {
  var q = q0;
  let s = i - AMBIENT;
  let b = birth(s);
  if (abs(frame.fx.z - b.y) < 0.5 && frame.fx.x < b.x && b.x <= frame.fx.y) { return spawnSignature(s, b.y); }
  if (q.span <= 0.0 || q.life > q.span) { q.span = 0.0; return q; }
  let u = frame.fx.w;
  let down = frame.gravity.xy;
  let st = stir(q);
  q.life += dt;
  if (q.kind < 1.5) {
    // Embers rise on the flame's buoyancy, curl and burn out.
    q.vel += (-down * 120.0 * u + curl(q.pos, frame.clock.x) * 1.5 + st.xy) * dt;
    q.vel *= exp(-0.6 * dt);
    q.pos += q.vel * dt;
    q.heat = st.z;
  } else if (q.kind < 2.5) {
    q.vel += (down * 1500.0 * u + st.xy * 0.6) * dt;
    q.pos += q.vel * dt;
    let floorY = floorAt(q);
    if (q.pos.y >= floorY) { q.pos.y = floorY; q.kind = 3.0; q.life = 0.0; q.span = 0.34; q.vel = vec2f(0.0); }
    if (q.pos.x < -40.0 || q.pos.x > frame.canvas.x + 40.0) { q.span = 0.0; }
  } else if (q.kind < 3.5) {
    // A splash ring spreads on the floor and fades.
  } else if (q.kind < 4.5) {
    let floorY = floorAt(q);
    if (q.ground > 0.0) {
      q.ground += dt;
      // A finger sweeping through can lift a settled leaf again.
      if (st.z > 0.35) {
        q.ground = 0.0;
        let kick = frame.touch.zw * 0.35 + vec2f(0.0, -160.0 * u);
        q.vel = kick * min(1.0, 420.0 * u / max(length(kick), 1.0));
        q.life = 0.21;
      }
      if (q.ground > 1.05) { q.span = 0.0; }
    } else {
      // Drag pulls the burst towards a steady fall (under a metre a second) on the breeze.
      let drag = 2.3 + 0.8 * q.seed;
      let fall = (210.0 + 70.0 * fixed(s, 11u)) * u;
      let goal = down * fall + vec2f(14.0 + 14.0 * fixed(s, 12u), 0.0) * u;
      q.vel += ((goal - q.vel) * drag + st.xy) * dt;
      q.pos += q.vel * dt;
      q.turn *= exp(-drag * dt);
      q.angle += q.turn * dt;
      q.phase += TAU / (0.85 + 0.45 * fixed(s, 13u)) * dt;
      if (q.pos.y >= floorY && q.life > 0.2) { q.pos.y = floorY; q.ground = 0.001; q.vel = vec2f(0.0); }
    }
  } else {
    q.vel += (vec2f(26.0, 10.0) * u + st.xy) * dt;
    q.vel *= exp(-2.6 * dt);
    q.pos += q.vel * dt;
  }
  return q;
}

@compute @workgroup_size(64) fn simulate(@builtin(global_invocation_id) id: vec3u) {
  let i = id.x;
  if (i >= COUNT) { return; }
  let dt = frame.canvas.w * frame.press.w;
  if (i < AMBIENT) { particles[i] = ambientStep(i, particles[i], dt); }
  else if (frame.press.w > 0.5) { particles[i] = signatureStep(i, particles[i], dt); }
}

// ---- Drawing ----
struct Layer { value: vec4f };
@group(0) @binding(2) var<uniform> layer: Layer;
@group(0) @binding(3) var<storage, read> motes: array<Particle>;
struct Mote {
  @builtin(position) position: vec4f,
  @location(0) local: vec2f,
  @location(1) @interpolate(flat) look: vec4f,   // kind, element, previous element, morph
  @location(2) @interpolate(flat) shape: vec4f,  // radius, life fraction, heat, seed
  @location(3) @interpolate(flat) pose: vec4f,   // roll, seconds settled, world, depth
};
@vertex fn moteVertex(@builtin(vertex_index) v: u32, @builtin(instance_index) i: u32) -> Mote {
  var out: Mote;
  out.position = vec4f(2.0, 2.0, 0.0, 1.0);
  let q = motes[i];
  let back = q.depth < 0.0;
  if (q.span <= 0.0 || back != (layer.value.x < 0.5)) { return out; }
  var corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(-1.0, 1.0), vec2f(-1.0, 1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0));
  let c = corners[v];
  let lf = q.life / q.span;
  let depthScale = select(1.0, 0.86, back);
  var center = q.pos;
  var half = 1.0;
  var stretch = 1.0;
  var axis = vec2f(1.0, 0.0);
  var radius = 1.0;
  var roll = 0.0;
  let speed = length(q.vel);
  if (speed > 1.0) { axis = q.vel / speed; }
  if (q.kind < 0.5) {
    radius = mix(ambientSize(q.prev, q.seed), ambientSize(q.look, q.seed), q.morph) * depthScale * (1.0 + q.heat * 0.4);
    // Fast embers streak along their path; bubbles and pollen stay round.
    let fiery = select(0.0, 1.0 - q.morph, q.prev < 0.5) + select(0.0, q.morph, q.look < 0.5);
    stretch = 1.0 + min(speed, 400.0) * 0.004 * fiery;
    half = radius * 2.4;
  } else if (q.kind < 1.5 || q.kind > 4.5) {
    radius = q.size * depthScale;
    stretch = 1.0 + min(speed, 400.0) * 0.003;
    half = radius * 6.0;
  } else if (q.kind < 2.5) {
    radius = q.size;
    stretch = 1.0 + min(speed, 900.0) * 0.0012;
    half = radius * 3.0;
  } else if (q.kind < 3.5) {
    radius = q.size * (1.6 + 4.0 * lf);
    axis = vec2f(1.0, 0.0);
    half = radius * 2.2;
  } else {
    // A leaf: physics carries its centre; its flutter swings it as a pendulum round that path.
    let u = frame.fx.w;
    let rest = select(0.0, 1.0 - pow(1.0 - min(1.0, q.ground / 0.26), 3.0), q.ground > 0.0);
    let flutter = pow(1.0 - exp(-2.5 * q.life), 2.0) * (1.0 - rest);
    let swing = (11.0 + 11.0 * q.seed) * u;
    center += vec2f(swing * flutter * sin(q.phase), swing * 0.22 * flutter * cos(2.0 * q.phase));
    let level = round(q.angle / 3.14159265) * 3.14159265;
    let a = mix(q.angle, level, max(flutter, rest)) + 0.55 * flutter * cos(q.phase) + 0.14 * sin(rest * TAU) * (1.0 - rest);
    axis = vec2f(cos(a), sin(a));
    let rolling = (q.seed - 0.5) * 18.0 * (1.0 - exp(-2.3 * q.life)) / 2.3 + 0.75 * flutter * sin(q.phase);
    roll = mix(rolling, select(3.14159265 - 1.05, 1.05, cos(rolling) > 0.0), rest);
    radius = u * (1.0 + q.depth * 20.0 / 260.0) * (0.62 + 0.38 * smoothstep(0.0, 0.14, q.life));
    half = 22.0 * radius;
  }
  let side = worldSide(q.look, center);
  if (q.kind > 0.5) { center.x += side.y; }
  let along = c.x * half * stretch;
  let across = c.y * half;
  let p = center + axis * along + vec2f(-axis.y, axis.x) * across;
  out.position = vec4f(p.x / frame.canvas.x * 2.0 - 1.0, 1.0 - p.y / frame.canvas.y * 2.0, 0.0, 1.0);
  // The shape is drawn round in its own frame; the stretched quad turns it into a streak.
  out.local = vec2f(along / stretch, across);
  out.look = vec4f(q.kind, q.look, q.prev, q.morph);
  out.shape = vec4f(radius, lf, q.heat, q.seed);
  out.pose = vec4f(roll, q.ground, q.look, q.depth);
  if (q.kind > 0.5 && side.x <= 0.0) { out.position = vec4f(2.0, 2.0, 0.0, 1.0); }
  return out;
}

fn ambientMote(e: f32, l: vec2f, r: f32, lf: f32, heat: f32, seed: f32) -> vec4f {
  let life = smoothstep(0.0, 0.14, lf) * (1.0 - smoothstep(0.76, 1.0, lf));
  if (e < 0.5) {
    let p = l / vec2f(r * 0.6, r);
    let f = max(0.0, 1.0 - dot(p, p) * 0.2);
    let glow = f * f * f * life * (0.30 + 0.24 * seed) * (1.0 + heat * 1.6);
    return vec4f(vec3f(1.0, 0.37 + 0.32 * (1.0 - lf), 0.095) * glow, glow);
  }
  if (e < 1.5) {
    let ring = 1.0 - smoothstep(0.55, 1.1, abs(length(l) - r));
    let g = l + vec2f(r * 0.35);
    let fall = max(0.0, 1.0 - dot(g, g) * 0.4);
    let bubble = (ring * 0.17 + fall * fall * fall * 0.22) * life * (1.0 + heat * 0.8);
    return vec4f(vec3f(0.57, 0.87, 0.94) * bubble, bubble);
  }
  let p = l / r;
  let f = max(0.0, 1.0 - dot(p, p) * 0.20);
  let pollen = f * f * f * life * 0.38 * (1.0 + heat * 1.2);
  return vec4f(vec3f(0.85, 0.91, 0.47) * pollen, pollen);
}
fn leaf(l: vec2f, r: f32, roll: f32, back: bool) -> vec4f {
  let local = l / r;
  let face = cos(roll);
  let width = max(abs(face), 0.12);
  let along = local.x / 17.0;
  let halfWidth = 6.6 * width * max(0.0, 1.0 - along * along) * (1.0 + 0.16 * along);
  let body = (1.0 - smoothstep(halfWidth - 0.7, halfWidth + 0.3, abs(local.y))) * step(abs(along), 1.0);
  let vein = (1.0 - smoothstep(0.1, 0.5, abs(local.y) / max(width, 0.35))) * step(abs(along), 0.9);
  let top = mix(vec3f(0.22, 0.50, 0.16), vec3f(0.52, 0.80, 0.30), clamp(0.5 + local.y / (halfWidth + 0.01) * 0.45, 0.0, 1.0));
  let under = mix(vec3f(0.50, 0.68, 0.30), vec3f(0.70, 0.84, 0.44), clamp(0.5 - local.y / (halfWidth + 0.01) * 0.45, 0.0, 1.0));
  var green = select(under, top, face >= 0.0) * (0.62 + 0.38 * abs(face)) + vec3f(0.10, 0.14, 0.05) * vein;
  if (back) { green *= 0.74; }
  return vec4f(green * body, body);
}
@fragment fn moteFragment(in: Mote) -> @location(0) vec4f {
  let kind = in.look.x;
  let r = in.shape.x;
  let lf = in.shape.y;
  var c = vec4f(0.0);
  if (kind < 0.5) {
    c = ambientMote(in.look.z, in.local, r, lf, in.shape.z, in.shape.w) * (1.0 - in.look.w)
      + ambientMote(in.look.y, in.local, r, lf, in.shape.z, in.shape.w) * in.look.w;
    if (in.pose.w < 0.0) { c *= 0.75; }
    // The motes live on the stage: they thin out above it and at the screen's sides.
    let p = in.position.xy / frame.canvas.z;
    c *= smoothstep(frame.stage.y - 50.0, frame.stage.y + 40.0, p.y) * (1.0 - smoothstep(frame.stage.z - 10.0, frame.stage.z + 20.0, p.y))
      * smoothstep(0.0, 24.0, p.x) * (1.0 - smoothstep(frame.canvas.x - 24.0, frame.canvas.x, p.x));
  } else if (kind < 1.5) {
    let life = 1.0 - lf;
    let d2 = dot(in.local, in.local) / (r * r);
    let glow = (exp(-d2) + exp(-d2 / 9.0) * 0.25) * life * (1.0 + in.shape.z);
    c = vec4f(vec3f(1.0, 0.58 + 0.3 * life, 0.18) * glow, min(glow * 0.8, 1.0));
  } else if (kind < 2.5) {
    let d = length(in.local);
    let drop = (1.0 - smoothstep(r * 0.5, r, d)) * smoothstep(0.0, 0.04, in.shape.y * 3.0);
    let g = in.local + vec2f(r * 0.35);
    let sheen = exp(-dot(g, g) / (r * r * 0.25)) * drop;
    c = vec4f(vec3f(0.6, 0.88, 0.98) * drop * 0.8 + vec3f(1.0) * sheen * 0.6, drop * 0.75 + sheen * 0.3);
  } else if (kind < 3.5) {
    let q = in.local / vec2f(r, r * 0.3);
    let d = length(q);
    let x = (d - 1.0) / 0.25;
    let ring = exp(-x * x) * (1.0 - lf) * (1.0 - lf);
    let flash = exp(-d * d * 4.0) * (1.0 - smoothstep(0.0, 0.3, lf));
    c = vec4f(vec3f(0.75, 0.95, 1.0) * (ring * 0.75 + flash * 0.5), ring * 0.6 + flash * 0.4);
  } else if (kind < 4.5) {
    // Settled on the floor it lies still, then fades; a contact shadow grows under it as it settles.
    let ground = in.pose.y;
    let rest = select(0.0, 1.0 - pow(1.0 - min(1.0, ground / 0.26), 3.0), ground > 0.0);
    let birth = smoothstep(0.0, 0.06, lf * LEAF_SPAN);
    c = leaf(in.local, r, in.pose.x, in.pose.w < 0.0);
    let sq = in.local / (r * vec2f(16.0, 3.0));
    let shadow = exp(-dot(sq, sq) * 1.6) * 0.38 * rest;
    c = (c + vec4f(0.0, 0.0, 0.0, shadow) * (1.0 - c.a)) * birth * (1.0 - smoothstep(0.55, 1.05, ground));
  } else {
    let d2 = dot(in.local, in.local) / (r * r);
    let age = lf * 1.45;
    let life = smoothstep(0.0, 0.08, age) * (1.0 - smoothstep(1.05, 1.45, age));
    let twinkle = 0.7 + 0.3 * sin(age * 18.0 + in.shape.w * 40.0);
    let mote = (exp(-d2) + exp(-d2 / 7.0) * 0.25) * life * twinkle * (1.0 + in.shape.z);
    c = vec4f(vec3f(1.0, 0.95, 0.62) * mote, min(mote * 0.8, 1.0));
  }
  if (kind > 0.5) {
    // Wiped by the habitat front together with the world it belongs to.
    let p = in.position.xy / frame.canvas.z;
    c *= worldSide(in.pose.z, p).x;
  }
  return c;
}
`;

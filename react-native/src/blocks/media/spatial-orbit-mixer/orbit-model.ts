/** The five sources, their orbits, the camera, and the self-playing loop, as closed-form functions of time. */

/**
 * A source: orbit radius (points on the floor), start angle (degrees; 0 is straight ahead of the
 * listener, 90 to the right), whole turns per loop, pulses and bobs a second, and where it sits on
 * the line in Stereo (x on the floor).
 */
export type Source = { name: string; color: number; ring: number; start: number; turns: number; beat: number; bob: number; pan: number };

export const SOURCES: Source[] = [
  { name: 'Vocals', color: 0xb6f23a, ring: 72, start: 0, turns: 1, beat: 2, bob: 0.5, pan: 0 },
  { name: 'Keys', color: 0xa98bff, ring: 112, start: 110, turns: 2, beat: 3, bob: 0.3, pan: 120 },
  { name: 'Guitar', color: 0xffc247, ring: 112, start: 250, turns: 1, beat: 1.5, bob: 0.4, pan: -120 },
  { name: 'Drums', color: 0xff6a5c, ring: 152, start: 300, turns: 0, beat: 4, bob: 0.2, pan: -60 },
  { name: 'Bass', color: 0x35d6ff, ring: 152, start: 140, turns: -1, beat: 1, bob: 0.1, pan: 60 },
];
export const RINGS = [72, 112, 152];
export const MODES = ['Stereo', 'Spatial', 'Fixed'];
/** The stereo line runs this far in front of the listener. */
const STEREO_Z = 60;

export const LOOP = 10;
/** Camera: the floor is seen from 40° above (the rings' height is 64 % of their width); a vertical drag tilts it 24°–56°. */
export const ELEVATION = 40;

// Curves

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return u * u * (3 - 2 * u);
}
export function easeInOut(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  const c1 = 1.6;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}
/** The shortest turn from a to b, in degrees. */
function turn(a: number, b: number) {
  'worklet';
  let d = (b - a) % 360;
  if (d > 180) d -= 360;
  if (d < -180) d += 360;
  return d;
}
export function posmod(x: number, m: number) {
  'worklet';
  const r = x % m;
  return r < 0 ? r + m : r;
}

// Orbits

/** The drums sway ±12° instead of orbiting. */
function sway(t: number) {
  'worklet';
  return 12 * Math.sin((2 * Math.PI * t) / LOOP);
}

/** A source's idle angle (degrees) on the loop clock t; it repeats every loop. */
export function idleAngle(i: number, t: number) {
  'worklet';
  const s = SOURCES[i];
  return s.start + (360 * s.turns * t) / LOOP + (i === 3 ? sway(t) : 0);
}

/** A pulse 0…1 on each beat (a quick rise, an exponential fall), and how far the beat has gone. */
export function beat(i: number, t: number): [number, number] {
  'worklet';
  const q = posmod(t * SOURCES[i].beat, 1);
  return [Math.exp(-5 * q) * smoothstep(0, 0.04, q), q];
}
function height(i: number, t: number) {
  'worklet';
  return 6 * Math.sin(2 * Math.PI * SOURCES[i].bob * t);
}

// A scene

/** An orb: on the floor (x right, z towards the viewer) and its height; 1 or up to 1.3 while held; how bright its cone is; its spatial radius and angle before the stereo mix. */
export type Orb = { x: number; z: number; h: number; grow: number; lit: number; r: number; a: number };

export type Scene = {
  yaw: number;
  elevation: number;
  /** 0 spatial, 1 stereo (may overshoot); the segment indicator's place (0 Stereo … 2 Fixed). */
  stereo: number;
  segment: number;
  orbs: Orb[];
  /** The source being dragged, its readout's opacity, and a scripted finger's opacity. */
  held: number;
  chip: number;
  finger: number;
  /** Whether head tracking reads on. */
  tracking: boolean;
};

function emptyScene(): Scene {
  'worklet';
  return { yaw: 0, elevation: ELEVATION, stereo: 0, segment: 1, orbs: [], held: -1, chip: 0, finger: 0, tracking: true };
}

export function polar(r: number, a: number): [number, number] {
  'worklet';
  const rad = (a * Math.PI) / 180;
  return [r * Math.sin(rad), -r * Math.cos(rad)];
}
export function unpolar(x: number, z: number): [number, number] {
  'worklet';
  return [Math.hypot(x, z), (Math.atan2(x, -z) * 180) / Math.PI];
}

/** Mixes the spatial place of a source with its place on the stereo line. */
function place(i: number, r: number, a: number, t: number, m: number): Orb {
  'worklet';
  const [x, z] = polar(r, a);
  const s = SOURCES[i];
  return { x: x + (s.pan - x) * m, z: z + (STEREO_Z - z) * m, h: height(i, t) * (1 - Math.min(Math.max(m, 0), 1)), grow: 1, lit: 0, r, a };
}

/** The loop at time t (0 ≤ t < LOOP). */
export function auto(t: number): Scene {
  'worklet';
  const sc = emptyScene();
  // The view turns 25° and back, as if the phone turned.
  if (t >= 4 && t < 5.6) sc.yaw = 25 * Math.pow(Math.sin((Math.PI * (t - 4)) / 1.6), 2);
  // Stereo at 6.0 (0.6 s), back to spatial at 7.4 (0.6 s, springy).
  let m = 0;
  if (t >= 6 && t < 7.4) m = easeOutBack((t - 6) / 0.6);
  if (t >= 7.4) m = 1 - easeOutBack((t - 7.4) / 0.6);
  sc.stereo = m;
  sc.segment = 1 - Math.min(Math.max(m, 0), 1);
  for (let i = 0; i < 5; i++) {
    let r = SOURCES[i].ring;
    let a = idleAngle(i, t);
    if (i === 3 && t >= 1.3 && t < 6.6) {
      // A finger drags the drums from the outer ring to the inner ring on the left (240°).
      const from = idleAngle(3, 1.3);
      const u = easeInOut((t - 1.3) / 1.6);
      r = 152 + (72 - 152) * u;
      a = from + turn(from, 240) * u;
      if (t >= 2.9) a = 240 + sway(t) - sway(2.9);
    }
    const o = place(i, r, a, t, m);
    if (i === 3) {
      const held = smoothstep(1.2, 1.4, t) * (1 - smoothstep(3.3, 3.5, t));
      o.grow = 1 + 0.3 * held;
      o.lit = held;
      sc.chip = smoothstep(1.3, 1.5, t) * (1 - smoothstep(3.3, 3.6, t));
      sc.finger = smoothstep(1.0, 1.25, t) * (1 - smoothstep(3.3, 3.6, t));
      if (held > 0.01) sc.held = 3;
    }
    sc.orbs.push(o);
  }
  return sc;
}

/** Mixes two scenes (u 0…1) field by field. */
export function mix(a: Scene, b: Scene, u: number): Scene {
  'worklet';
  const l = (x: number, y: number) => x + (y - x) * u;
  const s = emptyScene();
  s.yaw = l(a.yaw, b.yaw);
  s.elevation = l(a.elevation, b.elevation);
  s.stereo = l(a.stereo, b.stereo);
  s.segment = l(a.segment, b.segment);
  for (let i = 0; i < 5; i++) {
    const p = a.orbs[i];
    const q = b.orbs[i];
    s.orbs.push({ x: l(p.x, q.x), z: l(p.z, q.z), h: l(p.h, q.h), grow: l(p.grow, q.grow), lit: l(p.lit, q.lit), r: l(p.r, q.r), a: p.a + turn(p.a, q.a) * u });
  }
  s.held = u < 0.5 ? a.held : b.held;
  s.chip = l(a.chip, b.chip);
  s.finger = l(a.finger, b.finger);
  s.tracking = u < 0.5 ? a.tracking : b.tracking;
  return s;
}

// Touch

/** The scene under a finger, on the block's clock (seconds since it appeared). */
export type Manual = {
  /** The clock time where the idle orbits' phase is 0 (the loop's start they continue from). */
  anchor: number;
  /** Each source's radius and its angle offset from its idle orbit; after a drop the radius eases from snapFrom (0.25 s) to its ring. */
  radius: number[];
  offset: number[];
  snapFrom: number[];
  snapAt: number[];
  yaw: number;
  yawFrom: number;
  yawAt: number;
  elevation: number;
  /** The mode (0 Stereo, 1 Spatial, 2 Fixed) and the eased change to it. */
  mode: number;
  stereoFrom: number;
  stereoTo: number;
  segmentFrom: number;
  segmentTo: number;
  modeAt: number;
  /** The source under the finger (−1: none), where it is on the floor, and when it was taken and dropped. */
  held: number;
  heldX: number;
  heldZ: number;
  heldAt: number;
  dropped: number;
  droppedAt: number;
};

export function newManual(): Manual {
  'worklet';
  return {
    anchor: 0,
    radius: SOURCES.map((s) => s.ring),
    offset: [0, 0, 0, 0, 0],
    snapFrom: SOURCES.map((s) => s.ring),
    snapAt: [-100, -100, -100, -100, -100],
    yaw: 0,
    yawFrom: 0,
    yawAt: -100,
    elevation: ELEVATION,
    mode: 1,
    stereoFrom: 0,
    stereoTo: 0,
    segmentFrom: 1,
    segmentTo: 1,
    modeAt: -100,
    held: -1,
    heldX: 0,
    heldZ: 0,
    heldAt: -100,
    dropped: -1,
    droppedAt: -100,
  };
}

export function manualScene(m: Manual, t: number): Scene {
  'worklet';
  const sc = emptyScene();
  const tl = t - m.anchor;
  const u = (t - m.modeAt) / 0.6;
  sc.stereo = m.stereoFrom + (m.stereoTo - m.stereoFrom) * easeOutBack(u);
  sc.segment = m.segmentFrom + (m.segmentTo - m.segmentFrom) * easeInOut(u);
  sc.yaw = m.yaw + (m.yawFrom - m.yaw) * (1 - easeInOut((t - m.yawAt) / 0.6));
  sc.elevation = m.elevation;
  sc.tracking = m.mode !== 2;
  for (let i = 0; i < 5; i++) {
    const snap = 1 - Math.pow(1 - Math.min(Math.max((t - m.snapAt[i]) / 0.25, 0), 1), 3);
    let r = m.radius[i] + (m.snapFrom[i] - m.radius[i]) * (1 - snap);
    let a = idleAngle(i, tl) + m.offset[i];
    if (m.held === i) [r, a] = unpolar(m.heldX, m.heldZ);
    const o = place(i, r, a, tl, sc.stereo);
    let on = 0;
    if (m.held === i) on = smoothstep(0, 0.2, t - m.heldAt);
    if (m.dropped === i) on = 1 - smoothstep(0, 0.25, t - m.droppedAt);
    o.grow = 1 + 0.3 * on;
    o.lit = on;
    sc.orbs.push(o);
  }
  if (m.held >= 0) {
    sc.held = m.held;
    sc.chip = smoothstep(0, 0.2, t - m.heldAt);
  } else if (m.dropped >= 0 && t - m.droppedAt < 0.3) {
    sc.held = m.dropped;
    sc.chip = 1 - smoothstep(0, 0.3, t - m.droppedAt);
  }
  return sc;
}

// Projection

export type View = { cx: number; cy: number; scale: number; yaw: number; elevation: number };

/** A floor point (x, z) at height h on the screen: [X, Y, depth] (depth positive towards the viewer). */
export function project(v: View, x: number, z: number, h: number): [number, number, number] {
  'worklet';
  const y = (v.yaw * Math.PI) / 180;
  const e = (v.elevation * Math.PI) / 180;
  const xr = x * Math.cos(y) - z * Math.sin(y);
  const zr = x * Math.sin(y) + z * Math.cos(y);
  return [v.cx + v.scale * xr, v.cy + v.scale * (zr * Math.sin(e) - h * Math.cos(e)), zr];
}

/** A screen point back on the floor (height 0). */
export function unproject(v: View, sx: number, sy: number): [number, number] {
  'worklet';
  const y = (v.yaw * Math.PI) / 180;
  const e = (v.elevation * Math.PI) / 180;
  const xr = (sx - v.cx) / v.scale;
  const zr = (sy - v.cy) / v.scale / Math.sin(e);
  return [xr * Math.cos(y) + zr * Math.sin(y), -xr * Math.sin(y) + zr * Math.cos(y)];
}

/** The readout for a source at (x, z): its level (0 dB on the inner ring) and its direction. */
export function readout(x: number, z: number) {
  'worklet';
  const r = Math.max(Math.hypot(x, z), 1);
  const db = 20 * Math.log10(72 / r);
  const az = (Math.atan2(x, -z) * 180) / Math.PI;
  const deg = Math.round(Math.abs(az));
  const side = deg === 0 || deg === 180 ? '' : az < 0 ? ' L' : ' R';
  return `${db < 0 ? '−' : '+'}${Math.abs(db).toFixed(1)} dB · ${deg}°${side}`;
}

export function ringNear(r: number) {
  'worklet';
  let best = RINGS[0];
  for (const g of RINGS) if (Math.abs(g - r) < Math.abs(best - r)) best = g;
  return Math.abs(best - r) <= 12 ? best : r;
}

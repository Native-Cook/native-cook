// One loop drives the whole film. Every value is a pure function of the loop time t (seconds, 0 <= t < LOOP), so
// t = LOOP gives the frame of t = 0. The 3D stage, the shadows and the player read the same values, so they never drift.
// Only a record and its sleeve, standing upright in the air over studio paper (glTF metres, y up, faces toward +z).
//
//   0.00 –  0.80  poster: the sleeve three-quarter from the left, the record peeking out of its open side, 0:00
//   0.80 –  2.30  the record slides out of the sleeve to the right
//   2.20 –  3.80  the sleeve drifts back and to the left; the record comes forward to the middle
//   3.00          play (the native button flips); 3.0 – 4.0 the record spins up and the song starts
//   4.20 –  7.20  the record turns a full 360° about its upright axis: its edge, its B side, its edge, its A side
//   7.50 –  8.90  rotary scrub: a finger turns the record 1.5 turns forward, the song jumps to about 1:40 with it
//   9.20 – 10.40  the volume goes up to full and back (the level bars follow)
//  10.60          forward +15 s
//  11.30          pause: the record runs down by 12.5; 11.8 – 12.3 back to 0:00
//  12.50 – 14.00  the sleeve comes back; the record moves beside it
//  13.90 – 15.10  the record slides back in; 15.1 – 15.8 rest = the poster

export const LOOP = 15.8;
export const TRACK_SECONDS = 228;
export const IDLE_MS = 2000;
export const VOLUME = 0.7;

export const PHASE = {
  slideOutA: 0.8,
  slideOutB: 2.3,
  awayZA: 2.2,
  awayZB: 3.5,
  awayXA: 2.45,
  awayXB: 3.8,
  centreA: 2.35,
  centreB: 3.8,
  play: 3.0,
  spinA: 3.0,
  spinB: 4.0,
  turnA: 4.2,
  turnB: 7.2,
  scrubA: 7.5,
  scrubB: 8.9,
  volUpA: 9.2,
  volUpB: 9.7,
  volDownA: 9.9,
  volDownB: 10.4,
  forward: 10.6,
  pause: 11.3,
  stopA: 11.3,
  stopB: 12.5,
  backA: 11.8,
  backB: 12.3,
  homeZA: 12.6,
  homeZB: 13.9,
  homeXA: 12.5,
  homeXB: 13.7,
  uncentreA: 12.5,
  uncentreB: 14.0,
  slideInA: 13.9,
  slideInB: 15.1,
} as const;

/** The film's moments the touch logic needs: the record is out and in the middle between CENTRED_FROM and
 * CENTRED_TO; a return after a touch there eases to CLOSE_T (stopped, 0:00) and the film carries on from it. */
export const CENTRED_FROM = 3.8;
export const CENTRED_TO = 12.5;
export const CLOSE_T = 12.5;
/** Reduced motion and autoPlay off: the record out in the middle, the sleeve behind it. */
export const STILL_T = 3.9;

// ---------------------------------------------------------------- the set (glTF metres)

/** The record: 12 inches, 1.8 mm thick, a 100 mm label. The sleeve: 316 mm square, open on its right edge. */
export const R_VINYL = 0.1505;
export const SLEEVE_HALF = 0.158;
/** The record's centre relative to the sleeve's: peeking out (poster) and fully out beside it. */
export const PEEK_X = 0.085;
export const OUT_X = 0.338;
/** Where the sleeve drifts to while the record plays (behind, to the left, turned toward the middle). */
export const AWAY: readonly [number, number, number, number] = [-0.205, 0.004, -0.37, 0.42];
/** The record in the middle, a little forward. */
export const MIDDLE: readonly [number, number, number] = [0.0, 0.0, 0.05];
/** The studio paper's floor, under everything (the record and sleeve float over it). */
export const FLOOR_Y = -0.205;

// ---------------------------------------------------------------- the record's speed and the song

/** Seconds of song a radian of rotary drag moves (a full turn is about a minute). */
export const SEEK_PER_RAD = 10;
/** The film's scrub: 1.5 turns forward. */
export const SCRUB_TURNS = 1.5;
const SCRUB_ANGLE = SCRUB_TURNS * 2 * Math.PI;
export const SKIP_S = 15;
const SKIP_TIME = 0.4;
/** The record turns six whole times over the film (motor plus scrub), so its label comes back where it began. */
const TURNS = 6;
const SPIN_TIME = (PHASE.spinB - PHASE.spinA) / 2 + (PHASE.stopA - PHASE.spinB) + (PHASE.stopB - PHASE.stopA) / 2;
/** Record speed while playing (rad/s): 32.5 rpm, close to 33⅓. */
export const OMEGA = (TURNS * 2 * Math.PI - SCRUB_ANGLE) / SPIN_TIME;

// ---------------------------------------------------------------- helpers (declared before their callers)

export function clamp01(x: number): number {
  "worklet";
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number): number {
  "worklet";
  return clamp01((t - a) / (b - a));
}
export function lerp(a: number, b: number, k: number): number {
  "worklet";
  return a + (b - a) * k;
}
export function smooth(x: number): number {
  "worklet";
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}
/** The integral of smooth over [0, u]. */
function smoothInt(u: number): number {
  "worklet";
  const v = clamp01(u);
  return v * v * v - (v * v * v * v) / 2;
}
export function easeInOutSine(x: number): number {
  "worklet";
  return -(Math.cos(Math.PI * clamp01(x)) - 1) / 2;
}
export function easeInOutCubic(x: number): number {
  "worklet";
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
export function fract(x: number): number {
  "worklet";
  return x - Math.floor(x);
}

// ---------------------------------------------------------------- the record

/** Motor speed 0..1 in the film. */
export function speedAt(t: number): number {
  "worklet";
  return smooth(seg(t, PHASE.spinA, PHASE.spinB)) * (1 - smooth(seg(t, PHASE.stopA, PHASE.stopB)));
}

/** Seconds the motor has played (the integral of speedAt). */
function motorAt(t: number): number {
  "worklet";
  const up = (PHASE.spinB - PHASE.spinA) * smoothInt(seg(t, PHASE.spinA, PHASE.spinB));
  const hold = Math.max(0, Math.min(t, PHASE.stopA) - PHASE.spinB);
  const d = PHASE.stopB - PHASE.stopA;
  const u = seg(t, PHASE.stopA, PHASE.stopB);
  return up + hold + d * (u - smoothInt(u));
}

/** 0..1 of the film's rotary scrub. */
function scrubAt(t: number): number {
  "worklet";
  return easeInOutCubic(seg(t, PHASE.scrubA, PHASE.scrubB));
}

/** Record angle (rad, clockwise as the viewer sees its A side): motor plus scrub; LOOP gives six whole turns. */
export function platterAt(t: number): number {
  "worklet";
  return OMEGA * motorAt(t) + SCRUB_ANGLE * scrubAt(t);
}

/** Seconds of the song: it plays with the motor, the scrub turns it forward, forward skips, back goes to 0:00. */
export function songAt(t: number): number {
  "worklet";
  const played = motorAt(t);
  const scrub = SCRUB_ANGLE * SEEK_PER_RAD * scrubAt(t);
  const skip = SKIP_S * easeInOutSine(seg(t, PHASE.forward, PHASE.forward + SKIP_TIME));
  return (played + scrub + skip) * (1 - easeInOutSine(seg(t, PHASE.backA, PHASE.backB)));
}

/** 0..1: music plays (the level bars move). */
export function levelAt(t: number): number {
  "worklet";
  return speedAt(t);
}

/** The film's play state (the native button shows pause while it is true). */
export function playingAt(t: number): boolean {
  "worklet";
  return t >= PHASE.play && t < PHASE.pause;
}

/** The film's volume: up to full and back to where it was. */
export function volumeAt(t: number): number {
  "worklet";
  const up = easeInOutSine(seg(t, PHASE.volUpA, PHASE.volUpB));
  const down = easeInOutSine(seg(t, PHASE.volDownA, PHASE.volDownB));
  return VOLUME + (1 - VOLUME) * up * (1 - down);
}

/** The record's full turn about its upright axis (rad, 0 .. 2π). */
export function turnAt(t: number): number {
  "worklet";
  return 2 * Math.PI * easeInOutCubic(seg(t, PHASE.turnA, PHASE.turnB));
}

/**
 * Where everything is: out = [record x, y, z, yaw, sleeve x, y, z, yaw]. The record rides in the sleeve's frame
 * (peeking, sliding out) and blends to the middle while the sleeve drifts back; the film reverses it at the end.
 * The yaw here is the record's whole-body turn; its spin on its own axis is platterAt.
 */
export function poseAt(t: number, out: number[]): void {
  "worklet";
  const awayZ = easeInOutSine(seg(t, PHASE.awayZA, PHASE.awayZB)) * (1 - easeInOutSine(seg(t, PHASE.homeZA, PHASE.homeZB)));
  const awayX = easeInOutSine(seg(t, PHASE.awayXA, PHASE.awayXB)) * (1 - easeInOutSine(seg(t, PHASE.homeXA, PHASE.homeXB)));
  const sx = AWAY[0] * awayX;
  const sy = AWAY[1] * awayX;
  const sz = AWAY[2] * awayZ;
  const syaw = AWAY[3] * awayX;
  const rel = lerp(PEEK_X, OUT_X, easeInOutCubic(seg(t, PHASE.slideOutA, PHASE.slideOutB)) * (1 - easeInOutCubic(seg(t, PHASE.slideInA, PHASE.slideInB))));
  // in the sleeve's frame: along its own x, which its yaw turns
  const ix = sx + Math.cos(syaw) * rel;
  const iz = sz - Math.sin(syaw) * rel;
  const c = easeInOutCubic(seg(t, PHASE.centreA, PHASE.centreB)) * (1 - easeInOutCubic(seg(t, PHASE.uncentreA, PHASE.uncentreB)));
  // a slow hover that loops (two breaths a film), a hair more while the record is out in the air
  const bob = 0.005 * Math.sin((4 * Math.PI * t) / LOOP) * c;
  out[0] = lerp(ix, MIDDLE[0], c);
  out[1] = lerp(sy, MIDDLE[1], c) + bob;
  // a small lift toward the lens as it leaves the sleeve, so it clears the sleeve's edge
  out[2] = lerp(iz, MIDDLE[2], c) + 0.03 * Math.sin(Math.PI * c);
  out[3] = lerp(syaw, 0, c) + turnAt(t);
  out[4] = sx;
  out[5] = sy;
  out[6] = sz;
  out[7] = syaw;
}

// ---------------------------------------------------------------- camera (orbit about a moving target)

/** Keyframes: t, azimuth (deg, from the front, + = right), elevation (deg), distance, target x, y, z. */
const KEYS = [
  [0.0, -17, 7, 0.93, 0.06, -0.012, 0],
  [0.8, -17, 7, 0.93, 0.06, -0.012, 0],
  [2.3, -9, 6, 1.14, 0.165, -0.01, 0],
  [3.8, -4, 5, 0.82, -0.03, 0, 0.02],
  [5.8, 6, 4, 0.75, -0.02, 0, 0.03],
  [7.5, 11, 9, 0.68, 0.0, 0, 0.05],
  [8.9, 9, 8, 0.7, 0.0, 0, 0.05],
  [10.6, 1, 6, 0.78, -0.02, 0, 0.03],
  [12.5, -5, 6, 0.86, 0.0, 0, 0.01],
  [13.9, -11, 6, 1.14, 0.165, -0.01, 0],
  [15.1, -17, 7, 0.93, 0.06, -0.012, 0],
  [15.8, -17, 7, 0.93, 0.06, -0.012, 0],
] as const;

function keyAt(t: number, c: number): number {
  "worklet";
  let i = 0;
  while (i < KEYS.length - 2 && t >= KEYS[i + 1][0]) i++;
  const k0 = KEYS[Math.max(0, i - 1)];
  const k1 = KEYS[i];
  const k2 = KEYS[i + 1];
  const k3 = KEYS[Math.min(KEYS.length - 1, i + 2)];
  const x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]));
  const p0 = k0[c];
  const p1 = k1[c];
  const p2 = k2[c];
  const p3 = k3[c];
  let m1 = (p2 - p0) / 2;
  let m2 = (p3 - p1) / 2;
  if ((p1 - p0) * (p2 - p1) <= 0) m1 = 0;
  if ((p2 - p1) * (p3 - p2) <= 0) m2 = 0;
  const x2 = x * x;
  const x3 = x2 * x;
  return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2;
}

/** Film camera: out = [eye x, y, z, target x, y, z]. */
export function cameraAt(t: number, out: number[]): void {
  "worklet";
  const az = (keyAt(t, 1) * Math.PI) / 180;
  const el = (keyAt(t, 2) * Math.PI) / 180;
  const dist = keyAt(t, 3);
  const tx = keyAt(t, 4);
  const ty = keyAt(t, 5);
  const tz = keyAt(t, 6);
  const ce = Math.cos(el);
  out[0] = tx + Math.sin(az) * ce * dist;
  out[1] = ty + Math.sin(el) * dist;
  out[2] = tz + Math.cos(az) * ce * dist;
  out[3] = tx;
  out[4] = ty;
  out[5] = tz;
}

/** Nearest whole turn to a yaw (the ease back after a touch lands the record's turn on 0 or 2π, whichever is closer). */
export function nearestTurn(a: number): number {
  "worklet";
  return Math.round(a / (2 * Math.PI)) * 2 * Math.PI;
}

/**
 * The scene for the stage time s, eased by r (0..1) toward the close (CLOSE_T) after a touch in the centred window:
 * out = [record x, y, z, yaw, sleeve x, y, z, yaw, eye x, y, z, target x, y, z].
 */
export function sceneAt(s: number, r: number, out: number[]): void {
  "worklet";
  const p = [0, 0, 0, 0, 0, 0, 0, 0];
  const c = [0, 0, 0, 0, 0, 0];
  poseAt(s, p);
  cameraAt(s, c);
  if (r > 0) {
    const q = [0, 0, 0, 0, 0, 0, 0, 0];
    const d = [0, 0, 0, 0, 0, 0];
    poseAt(CLOSE_T, q);
    cameraAt(CLOSE_T, d);
    q[3] = nearestTurn(p[3]);
    for (let i = 0; i < 8; i++) p[i] = lerp(p[i], q[i], r);
    for (let i = 0; i < 6; i++) c[i] = lerp(c[i], d[i], r);
  }
  for (let i = 0; i < 8; i++) out[i] = p[i];
  for (let i = 0; i < 6; i++) out[8 + i] = c[i];
}

export const HFOV_PHONE = 34;
/** Focal length (points) for a stage w x h: the horizontal field of view, unless the stage is short for it. */
export function focalFor(w: number, h: number): number {
  "worklet";
  const t = Math.tan((HFOV_PHONE * Math.PI) / 360);
  return Math.min(w / 2 / t, (h * 0.73) / 2 / t);
}

/** Camera basis from eye/target: out = [fx, fy, fz, rx, rz, ux, uy, uz] (right has no y: the camera never rolls). */
export function basis(c: number[], out: number[]): void {
  "worklet";
  let fx = c[3] - c[0];
  let fy = c[4] - c[1];
  let fz = c[5] - c[2];
  const fl = Math.hypot(fx, fy, fz) || 1;
  fx /= fl;
  fy /= fl;
  fz /= fl;
  let rx = -fz;
  let rz = fx;
  const rl = Math.hypot(rx, rz) || 1;
  rx /= rl;
  rz /= rl;
  out[0] = fx;
  out[1] = fy;
  out[2] = fz;
  out[3] = rx;
  out[4] = rz;
  out[5] = -rz * fy;
  out[6] = rz * fx - rx * fz;
  out[7] = rx * fy;
}

/** Project world (x, y, z) onto a stage w x h with eye/target c and basis b: out = [sx, sy, pt per metre]. */
export function project(c: number[], b: number[], w: number, h: number, x: number, y: number, z: number, out: number[]): boolean {
  "worklet";
  const px = x - c[0];
  const py = y - c[1];
  const pz = z - c[2];
  const d = px * b[0] + py * b[1] + pz * b[2];
  if (d < 0.02) return false;
  const f = focalFor(w, h);
  out[0] = w / 2 + ((px * b[3] + pz * b[4]) / d) * f;
  out[1] = h / 2 - ((px * b[5] + py * b[6] + pz * b[7]) / d) * f;
  out[2] = f / d;
  return true;
}

/**
 * The groove highlight's angle in the record's own plane: on circular grooves the bright streak lies along the
 * half vector between the eye and the key light, projected on the record's face. toKey is the unit vector toward
 * the key light; the record's yaw turns the world into its frame.
 */
export function sheenAngle(scene: number[], toKey: readonly [number, number, number]): number {
  "worklet";
  let vx = scene[8] - scene[0];
  let vy = scene[9] - scene[1];
  let vz = scene[10] - scene[2];
  const vl = Math.hypot(vx, vy, vz) || 1;
  vx = vx / vl + toKey[0];
  vy = vy / vl + toKey[1];
  vz = vz / vl + toKey[2];
  // into the record's frame: rotate by -yaw about y
  const a = scene[3];
  const lx = Math.cos(a) * vx - Math.sin(a) * vz;
  return Math.atan2(vy, lx);
}

/** The soft contact shadows on the paper under the floating record (k = 0) and sleeve (k = 1): half sizes of each
 * footprint along its own width and depth (m), and their darkness. */
export const SHADOW_HALF_W = [0.135, 0.165] as const;
export const SHADOW_HALF_D = [0.05, 0.055] as const;
export const SHADOW_ALPHA = [0.3, 0.24] as const;

/**
 * Shadow k on the floor, projected onto the stage: out = [centre x, y, radius along the width, radius along the
 * depth, angle (rad), alpha]. The footprint turns with the object's yaw, so the record's shadow narrows and swings
 * through its 360° turn.
 */
export function shadowAt(scene: number[], k: number, w: number, h: number, out: number[]): boolean {
  "worklet";
  const c = [scene[8], scene[9], scene[10], scene[11], scene[12], scene[13]];
  const b = [0, 0, 0, 0, 0, 0, 0, 0];
  basis(c, b);
  const x = scene[k * 4];
  const z = scene[k * 4 + 2];
  const yaw = scene[k * 4 + 3];
  const hw = SHADOW_HALF_W[k];
  const hd = SHADOW_HALF_D[k];
  const p0 = [0, 0, 0];
  const p1 = [0, 0, 0];
  const p2 = [0, 0, 0];
  if (!project(c, b, w, h, x, FLOOR_Y, z, p0)) return false;
  project(c, b, w, h, x + Math.cos(yaw) * hw, FLOOR_Y, z - Math.sin(yaw) * hw, p1);
  project(c, b, w, h, x + Math.sin(yaw) * hd, FLOOR_Y, z + Math.cos(yaw) * hd, p2);
  const ax = p1[0] - p0[0];
  const ay = p1[1] - p0[1];
  out[0] = p0[0];
  out[1] = p0[1];
  out[2] = Math.max(1, Math.hypot(ax, ay));
  out[3] = Math.max(1, Math.hypot(p2[0] - p0[0], p2[1] - p0[1]));
  out[4] = Math.atan2(ay, ax);
  // a floating object's shadow is lighter the higher it is over the floor
  const lift = scene[k * 4 + 1] - FLOOR_Y;
  out[5] = SHADOW_ALPHA[k] * Math.min(1.2, 0.205 / Math.max(0.12, lift));
  return true;
}

/** The shortest signed difference of two angles (rad). */
export function angleDelta(a: number, b: number): number {
  "worklet";
  let d = a - b;
  while (d > Math.PI) d -= 2 * Math.PI;
  while (d < -Math.PI) d += 2 * Math.PI;
  return d;
}

// ---------------------------------------------------------------- UI

/** The small "now playing" bars: three wobbling levels from the song position (a pause freezes them). */
export function barAt(k: number, song: number, level: number): number {
  "worklet";
  const b = song * 2;
  const f = fract(b);
  const kick = Math.exp(-f * 5);
  const w = 0.5 + 0.5 * Math.sin(song * (5.3 + k * 2.1) + k * 1.7);
  return level * (0.25 + 0.45 * w + 0.3 * kick * (k === 1 ? 1 : 0.6));
}

/** mm:ss of a song second. */
export function clock(s: number): string {
  "worklet";
  const v = Math.max(0, Math.floor(s));
  const m = Math.floor(v / 60);
  const r = v % 60;
  return `${m}:${r < 10 ? "0" : ""}${r}`;
}

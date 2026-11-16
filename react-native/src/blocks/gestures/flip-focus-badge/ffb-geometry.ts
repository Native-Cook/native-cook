// Flip Focus Badge — the badge's 3D bookkeeping: rotations, the ring's frame, the digits' layout and the uniform block the
// shader reads. Everything is a plain function of the badge's state, so the loop, a finger and the offline harness all
// build the same frame.
//
//   world   x right, y up, z toward the viewer, origin = the plate's centre (design pt)
//   body    the plate's frame:  world = Rx(pitch) · Ry(yaw) · body
//   ring    arc-centred: the ring is a circle of radius R in its local xy plane whose arc (343°) is centred on +y; its
//           plane is tilted by α and rolled by ρ (fitted to the pin), then carried round the vertical axis by its own
//           yaw (the viscous joint lags the plate) and, a little, against the pitch.

import type { Look } from './ffb-motion';
import { ATLAS, ref, SCENE } from './ffb-theme';

export type Vec3 = [number, number, number];
export type Mat3 = [number, number, number, number, number, number, number, number, number]; // row-major

export const D2R = Math.PI / 180;

export function hex3(hex: string): Vec3 {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16) / 255, parseInt(h.slice(2, 4), 16) / 255, parseInt(h.slice(4, 6), 16) / 255];
}

export function rotX(a: number): Mat3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [1, 0, 0, 0, c, -s, 0, s, c];
}
export function rotY(a: number): Mat3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [c, 0, s, 0, 1, 0, -s, 0, c];
}
export function rotZ(a: number): Mat3 {
  'worklet';
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [c, -s, 0, s, c, 0, 0, 0, 1];
}
export function mul(a: Mat3, b: Mat3): Mat3 {
  'worklet';
  return [
    a[0] * b[0] + a[1] * b[3] + a[2] * b[6],
    a[0] * b[1] + a[1] * b[4] + a[2] * b[7],
    a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
    a[3] * b[0] + a[4] * b[3] + a[5] * b[6],
    a[3] * b[1] + a[4] * b[4] + a[5] * b[7],
    a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
    a[6] * b[0] + a[7] * b[3] + a[8] * b[6],
    a[6] * b[1] + a[7] * b[4] + a[8] * b[7],
    a[6] * b[2] + a[7] * b[5] + a[8] * b[8],
  ];
}
export function mulV(m: Mat3, v: Vec3): Vec3 {
  'worklet';
  return [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]];
}
export function transpose(m: Mat3): Mat3 {
  'worklet';
  return [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
}
function row(m: Mat3, i: number): Vec3 {
  'worklet';
  return [m[i * 3], m[i * 3 + 1], m[i * 3 + 2]];
}

/** the ring's arc: tail and head angles in the fitted frame, the arc's centre and half aperture, and the shift that puts the centre on +y */
const TAIL = SCENE.ring.tailDeg;
const HEAD = SCENE.ring.headDeg + 360;
export const ARC = {
  centre: (TAIL + HEAD) / 2, // 410.85°
  half: (HEAD - TAIL) / 2, // 171.45°
};
const ARC_SHIFT = 90 - ARC.centre; // δ: local' = Rz(δ) · local

/** The ring's local frame matrices (shared by the uniforms and the sparkles' positions). Angles in degrees. */
export function ringBasis(yawRing: number, pitch: number) {
  'worklet';
  const extra = -0.28 * pitch * D2R; // the ring tilts a little against the pitch
  const wr = mul(rotX(pitch * D2R), mul(rotY(yawRing * D2R), rotX(extra))); // ring → world rotation
  const lr = mul(rotZ(SCENE.ring.roll * D2R), mul(rotX(SCENE.ring.alpha * D2R), rotZ(-ARC_SHIFT * D2R))); // local' → plate frame
  return { wr, lr };
}

/** world position of the ring's point at local angle `tDeg` (the fitted frame, 0° = +x of the circle before its tilt) */
export function ringPoint(tDeg: number, yawRing: number, pitch: number): Vec3 {
  'worklet';
  const { wr, lr } = ringBasis(yawRing, pitch);
  const t = tDeg * D2R;
  const p = mulV(lr, [SCENE.ring.R * Math.cos(t + ARC_SHIFT * D2R), SCENE.ring.R * Math.sin(t + ARC_SHIFT * D2R), 0]);
  return mulV(wr, [p[0] + SCENE.ring.cx, p[1] + SCENE.ring.cy, p[2]]);
}

/** the front numeral's cells, origins (design pt, plate-centred), glyph scale and baseline for a value 0 … 99. Two wide digits
 * are scaled down together to fit the plate; the pin's "10" (121 pt) is untouched. */
export function frontLayout(value: number): [number, number, number, number, number, number] {
  'worklet';
  const v = Math.max(0, Math.min(99, Math.round(value)));
  const tens = Math.floor(v / 10);
  const ones = v % 10;
  const adv = SCENE.num.advance;
  const fit = 124; // pt: the widest numeral that sits inside the plate with the pin's margins
  if (v < 10) {
    const w = ATLAS.inkR[ones] - ATLAS.inkL[ones];
    const ns = Math.min(1, fit / w);
    const x = (-w * ns) / 2 + SCENE.num.pitchLeft - ATLAS.inkL[ones] * ns;
    return [-1, ones, 0, x, ns, SCENE.num.baseY + ((1 - ns) * SCENE.num.cap) / 2];
  }
  const wt = ATLAS.inkR[tens] - ATLAS.inkL[tens];
  const wo = ATLAS.inkR[ones] - ATLAS.inkL[ones];
  const total = wt * adv + wo; // the second glyph starts one advance after the first
  const ns = Math.min(1, fit / total);
  const left = (-total * ns) / 2 + SCENE.num.pitchLeft;
  const xt = left - ATLAS.inkL[tens] * ns;
  const xo = left + wt * adv * ns - ATLAS.inkL[ones] * ns;
  return [tens, ones, xt, xo, ns, SCENE.num.baseY + ((1 - ns) * SCENE.num.cap) / 2];
}

/** the back's clock "mm:ss" at the clock scale: four cells and their origins; the colon's x is returned last */
export const CLOCK_SCALE = 0.38;
const CLOCK_ADV = 0.92; // the clock's digits sit apart (the front's overlap would blur small digits)
export function backLayout(secs: number): [number, number, number, number, number, number, number, number, number] {
  'worklet';
  const total = Math.max(0, Math.min(5999, Math.floor(secs)));
  const m = Math.floor(total / 60);
  const s = total % 60;
  const cells = [Math.floor(m / 10), m % 10, Math.floor(s / 10), s % 10];
  const adv = CLOCK_ADV;
  const k = CLOCK_SCALE;
  const widths = [0, 1, 2, 3].map((i) => (ATLAS.inkR[cells[i]] - ATLAS.inkL[cells[i]]) * k);
  const colon = 8; // design pt reserved for the colon at the clock scale
  const total2 = widths[0] * adv + widths[1] * adv + colon + widths[2] * adv + widths[3];
  let x = -total2 / 2;
  const xs: number[] = [];
  xs.push(x - ATLAS.inkL[cells[0]] * k);
  x += widths[0] * adv;
  xs.push(x - ATLAS.inkL[cells[1]] * k);
  x += widths[1] * adv;
  const colonX = x + colon / 2;
  x += colon;
  xs.push(x - ATLAS.inkL[cells[2]] * k);
  x += widths[2] * adv;
  xs.push(x - ATLAS.inkL[cells[3]] * k);
  return [cells[0], cells[1], cells[2], cells[3], xs[0], xs[1], xs[2], xs[3], colonX];
}

export type BadgeState = {
  yaw: number; // plate, degrees
  pitch: number; // degrees
  ringYaw: number; // ring, degrees (lags the plate)
  depth: number; // numeral extrusion, 1 = the pin's
  value: number; // the front numeral
  clockSecs: number; // the back's clock
  sat: number; // satellite lap 0 … 1 (minutes / 60)
  glintPlus: number;
  glintHead: number;
  glintSat: number;
  envYaw: number; // radians, the studio's turn (the hand's sway)
  scale: number; // badge press scale 1 … 1.03 (applied on the camera)
  popPlus: number; // pop-in scale of the "+" sparkle, 0.6 … 1
  popHr: number; // … and of the "hr"
};

export const TINT = hex3(ref.color.numeral);
export const EDGE = hex3(ref.color.edge);
export const PLATE = hex3(ref.color.plate);

/** The uniforms the badge shader reads, except the canvas's own (uC, uS, uPx, uAux) and the atlas child. */
export function badgeUniforms(b: BadgeState) {
  'worklet';
  // plate: world → body = Ry(−yaw) · Rx(−pitch)
  const wb = mul(rotX(b.pitch * D2R), rotY(b.yaw * D2R));
  const toBody = transpose(wb);
  // ring: local' = M · (pre · world − c) with pre = wr⁻¹, M = (lr)⁻¹; the shader subtracts `uRc` = M · c after the product
  const { wr, lr } = ringBasis(b.ringYaw, b.pitch);
  const pre = transpose(wr);
  const inv = transpose(lr);
  const toRing = mul(inv, pre);
  const c = mulV(inv, [SCENE.ring.cx, SCENE.ring.cy, 0]);
  const sparkHead = ringPoint(SCENE.ring.headDeg, b.ringYaw, b.pitch);
  const satDeg = SCENE.ring.satRestDeg + 360 * (b.sat - 0.4);
  const sparkSat = ringPoint(satDeg, b.ringYaw, b.pitch);
  const front = frontLayout(b.value);
  const back = backLayout(b.clockSecs);
  return {
    uBx: row(toBody, 0),
    uBy: row(toBody, 1),
    uBz: row(toBody, 2),
    uRx: row(toRing, 0),
    uRy: row(toRing, 1),
    uRz: row(toRing, 2),
    uRc: c,
    uSpH: sparkHead,
    uSpS: sparkSat,
    uFront: [front[0], front[1], front[2], front[3]],
    uNum: [front[4], front[5]],
    uBack: [back[0], back[1], back[2], back[3]],
    uBackX: [back[4], back[5], back[6], back[7]],
    uGlint: [b.glintPlus, b.glintHead, b.glintSat, back[8]], // the 4th carries the colon's x (a glint of the ring needs none)
    uMat: [b.depth, 0.22, b.envYaw, b.scale],
    uPop: [b.popPlus, b.popHr],
    uTint: TINT,
    uEdge: EDGE,
    uPlate: PLATE,
  };
}

/** the badge's state for a look (the loop's, or the live values the finger writes) */
export function badgeFromLook(l: Look): BadgeState {
  'worklet';
  return {
    yaw: l.yaw,
    pitch: l.pitch,
    ringYaw: l.ringYaw,
    depth: l.depth,
    value: l.value,
    clockSecs: l.clock,
    sat: l.sat,
    glintPlus: l.glintPlus,
    glintHead: l.glintHead,
    glintSat: l.glintSat,
    envYaw: l.env,
    scale: l.press,
    popPlus: l.popPlus,
    popHr: l.popHr,
  };
}

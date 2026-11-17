/*
 The capsule's pose and its projected outlines, from one tilt angle phi, and the light curves.

 The capsule is a cylinder of radius r between two seams 2h apart, closed by two hemispheres. It
 turns about the screen's anti-diagonal through its centre; at phi = 0 it lies flat along the
 diagonal with end A at the top right, at phi = PI/2 end A points at the viewer. Every outline is a
 polygon sampled the same way as the Swift reference (flat [x0, y0, x1, y1, …] arrays). All
 functions are worklets, declared before the ones that call them.
*/

/** One full turn of the capsule, in seconds. */
export const LOOP = 10;

export const UX = 0.70710678;
export const UY = -0.70710678;
/** Camera distance in points: a weak perspective. */
const FOCAL = 2000;
/** The camera looks down on the turn by this angle, so end-on the rings stack below the disc. */
const TILT = 0.09;

export type Geometry = {
  cx: number;
  cy: number;
  sinP: number;
  cosP: number;
  facing: number;
  aX: number;
  aY: number;
  bX: number;
  bY: number;
  rA: number;
  rB: number;
  nearIsA: boolean;
  axisX: number;
  axisY: number;
  acrossX: number;
  acrossY: number;
  nearX: number;
  nearY: number;
  nearR: number;
  farX: number;
  farY: number;
  farR: number;
  outX: number;
  outY: number;
};

export function makeGeometry(cx: number, cy: number, r: number, h: number, phi: number): Geometry {
  'worklet';
  const sinP = Math.sin(phi);
  const cosP = Math.cos(phi);
  const x0 = cosP * UX;
  const y0 = cosP * UY;
  const z0 = sinP;
  const dx = x0;
  const dy = y0 * Math.cos(TILT) - z0 * Math.sin(TILT);
  const dz = y0 * Math.sin(TILT) + z0 * Math.cos(TILT);
  const zA = h * dz;
  const sA = FOCAL / (FOCAL - zA);
  const sB = FOCAL / (FOCAL + zA);
  const aX = cx + dx * h * sA;
  const aY = cy + dy * h * sA;
  const bX = cx - dx * h * sB;
  const bY = cy - dy * h * sB;
  const rA = r * sA;
  const rB = r * sB;
  const nearIsA = dz >= 0;
  const length = Math.sqrt(dx * dx + dy * dy);
  const axisX = length > 1e-6 ? dx / length : UX;
  const axisY = length > 1e-6 ? dy / length : UY;
  return {
    cx,
    cy,
    sinP,
    cosP,
    facing: Math.abs(dz),
    aX,
    aY,
    bX,
    bY,
    rA,
    rB,
    nearIsA,
    axisX,
    axisY,
    acrossX: -axisY,
    acrossY: axisX,
    nearX: nearIsA ? aX : bX,
    nearY: nearIsA ? aY : bY,
    nearR: nearIsA ? rA : rB,
    farX: nearIsA ? bX : aX,
    farY: nearIsA ? bY : aY,
    farR: nearIsA ? rB : rA,
    outX: nearIsA ? axisX : -axisX,
    outY: nearIsA ? axisY : -axisY,
  };
}

export function ellipse(
  cx: number,
  cy: number,
  a: number,
  b: number,
  axAx: number,
  axAy: number,
  axBx: number,
  axBy: number,
  from: number,
  to: number,
  steps: number,
): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = from + ((to - from) * i) / steps;
    const c = Math.cos(t);
    const s = Math.sin(t);
    out.push(cx + a * c * axAx + b * s * axBx, cy + a * c * axAy + b * s * axBy);
  }
  return out;
}

/** Convex hull of the two end discs: the silhouette. */
export function silhouette(g: Geometry, steps = 40): number[] {
  'worklet';
  const dx = g.aX - g.bX;
  const dy = g.aY - g.bY;
  const d = Math.sqrt(dx * dx + dy * dy);
  if (d + Math.min(g.rA, g.rB) <= Math.max(g.rA, g.rB) + 0.01) {
    const bigA = g.rA >= g.rB;
    const rr = Math.max(g.rA, g.rB);
    return ellipse(bigA ? g.aX : g.bX, bigA ? g.aY : g.bY, rr, rr, g.acrossX, g.acrossY, g.axisX, g.axisY, 0, 2 * Math.PI, steps * 2);
  }
  const a = Math.atan2(dy, dx);
  const gam = Math.acos(Math.min(Math.max((g.rB - g.rA) / d, -1), 1));
  const out: number[] = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = a - gam + (2 * gam * i) / steps;
    out.push(g.aX + g.rA * Math.cos(t), g.aY + g.rA * Math.sin(t));
  }
  for (let i = 0; i <= steps; i += 1) {
    const t = a + gam + ((2 * Math.PI - 2 * gam) * i) / steps;
    out.push(g.bX + g.rB * Math.cos(t), g.bY + g.rB * Math.sin(t));
  }
  return out;
}

/** The near cap as seen: its outer half-disc joined to the front half of its seam ellipse. */
export function nearCap(g: Geometry, steps = 32): number[] {
  'worklet';
  const out: number[] = [];
  const rn = g.nearR;
  for (let i = 0; i <= steps; i += 1) {
    const t = (Math.PI * i) / steps;
    out.push(
      g.nearX + rn * (Math.cos(t) * g.acrossX + Math.sin(t) * g.outX),
      g.nearY + rn * (Math.cos(t) * g.acrossY + Math.sin(t) * g.outY),
    );
  }
  for (let i = 1; i < steps; i += 1) {
    const t = Math.PI + (Math.PI * i) / steps;
    out.push(
      g.nearX + rn * (Math.cos(t) * g.acrossX + g.facing * Math.sin(t) * g.outX),
      g.nearY + rn * (Math.cos(t) * g.acrossY + g.facing * Math.sin(t) * g.outY),
    );
  }
  return out;
}

/** The front half of the near seam: the edge between the lit cap and the body. */
export function nearSeamFront(g: Geometry, steps = 32): number[] {
  'worklet';
  return ellipse(g.nearX, g.nearY, g.nearR, g.nearR * g.facing, g.acrossX, g.acrossY, g.outX, g.outY, Math.PI, 2 * Math.PI, steps);
}

/** The outer arc of the near cap, inset by `inset`, where the dispersion band sits. */
export function nearRimArc(g: Geometry, inset: number, steps = 32): number[] {
  'worklet';
  const rr = g.nearR - inset;
  return ellipse(g.nearX, g.nearY, rr, rr, g.acrossX, g.acrossY, g.outX, g.outY, 0, Math.PI, steps);
}

/** A cross-section ring of the body at lambda (0 far seam, 1 near seam). */
export function ring(g: Geometry, lambda: number, steps = 48): number[] {
  'worklet';
  const x = g.farX + (g.nearX - g.farX) * lambda;
  const y = g.farY + (g.nearY - g.farY) * lambda;
  const rr = g.farR + (g.nearR - g.farR) * lambda;
  return ellipse(x, y, rr, rr * g.facing, g.acrossX, g.acrossY, g.axisX, g.axisY, 0, 2 * Math.PI, steps);
}

/** The half of a body ring on the far side, the part that stacks below the disc end-on. */
export function ringBack(g: Geometry, lambda: number, steps = 32): number[] {
  'worklet';
  const x = g.farX + (g.nearX - g.farX) * lambda;
  const y = g.farY + (g.nearY - g.farY) * lambda;
  const rr = g.farR + (g.nearR - g.farR) * lambda;
  return ellipse(x, y, rr, rr * g.facing, g.acrossX, g.acrossY, g.outX, g.outY, Math.PI, 2 * Math.PI, steps);
}

/** Where the near cap's pole lands, pushed k radii along the outward direction. */
export function towardsPole(g: Geometry, k: number): [number, number] {
  'worklet';
  return [g.nearX + g.outX * g.nearR * k, g.nearY + g.outY * g.nearR * k];
}

/** One full turn: phi = 2PI u - 0.10 sin 4PI u, slower at the flat poses, faster end-on. */
export function phiAt(t: number): number {
  'worklet';
  const u = t / LOOP;
  return 2 * Math.PI * u - 0.1 * Math.sin(4 * Math.PI * u);
}

export function loopTime(seconds: number): number {
  'worklet';
  const t = seconds % LOOP;
  return t < 0 ? t + LOOP : t;
}

export function bell(x: number, c: number, w: number): number {
  'worklet';
  const k = (x - c) / w;
  return Math.exp(-k * k);
}

export function smoothstep(a: number, b: number, x: number): number {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

export function easeInOutCubic(x: number): number {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** 1 when phi is at target (mod 2PI), falling off over about a quarter of a radian. */
export function endOn(phi: number, target: number): number {
  'worklet';
  let d = (phi - target) % (2 * Math.PI);
  if (d < -Math.PI) d += 2 * Math.PI;
  if (d > Math.PI) d -= 2 * Math.PI;
  return bell(d, 0, 0.45);
}

export function hash(i: number, k: number): number {
  'worklet';
  const x = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
  return x - Math.floor(x);
}

export type Light = { rainbow: number; warm: number; frost: number; rings: number; fibres: number; rim: number };

/** Rainbow band, warm caustic, frost, end-on rings, lit fibres for a facing of c. */
export function lightFor(c: number): Light {
  'worklet';
  const rings = smoothstep(0.8, 0.99, c);
  return {
    rainbow: bell(c, 0.4, 0.18) * smoothstep(0.1, 0.22, c),
    warm: bell(c, 0.72, 0.17) * (1 - rings),
    frost: bell(c, 0.68, 0.24),
    rings,
    fibres: 0.6 * bell(c, 0.95, 0.05),
    rim: 1 - 0.3 * rings,
  };
}

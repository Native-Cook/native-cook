import { STEP_LOOP, STEP_PACE } from './icon-contract';

export const ICON_HERO = [1.3, 1.4, 1.5, 1.15] as const;

export function iconClamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return Math.max(lo, Math.min(hi, v));
}

export function iconEase(a: number, b: number, v: number) {
  'worklet';
  const x = iconClamp((v - a) / (b - a));
  return x * x * (3 - 2 * x);
}

function iconLerp(a: number, b: number, v: number) {
  'worklet';
  return a + (b - a) * v;
}

function iconPop(a: number, b: number, t: number, overshoot = 1.5) {
  'worklet';
  const x = iconClamp((t - a) / (b - a));
  const tail = x - 1;
  return 1 + (1 + overshoot) * tail * tail * tail + overshoot * tail * tail;
}

/** Clock integration is separate from the renderer, so a held scrub never advances a pose. */
export function advanceIconTimes(
  times: readonly number[], position: number, previousPosition: number,
  delta: number, running: number, wasRunning: number, reduced: boolean,
): number[] {
  'worklet';
  const next = [times[0], times[1], times[2], times[3]];
  const rest = Math.round(position);
  const atRest = Math.abs(position - rest) < 0.00001;
  for (let i = 0; i < 4; i++) {
    if (reduced) next[i] = ICON_HERO[i];
    else if (running < 0.5 || (atRest && i !== rest)) next[i] = 0;
    else if (atRest && Math.abs(previousPosition - rest) < 0.00001 && wasRunning > 0.5) {
      next[i] = (next[i] + Math.max(0, delta) * STEP_PACE[i]) % STEP_LOOP[i];
    }
  }
  return next;
}

function coinPose(t: number, release: number, which: number) {
  'worklet';
  const big = which === 0;
  const phase = (t - 1.3) * (big ? 3.0 : 3.8) + which * 0.35;
  const hx = big ? 0 : which === 1 ? -0.37 : 0.37;
  const hy = big ? 0.61 : -0.08;
  const x = big ? -0.08 + 0.46 * Math.sin(phase) : (which === 1 ? -1.03 : 1.12) + 0.12 * Math.sin(phase);
  const y = big ? 0.52 + 0.18 * Math.sin(phase) : -0.60 + 0.26 * Math.sin(phase + which);
  const z = big ? 0.12 + 0.54 * Math.cos(phase) : 0.20 + 0.43 * Math.sin(phase);
  return [iconLerp(hx, x, release), iconLerp(hy, y, release), iconLerp(0, z, release),
    (big ? 0.63 : 0.26) * iconLerp(0.16, 1, iconEase(0, t > 2.6 ? 0.24 : 0.6, release)),
    phase * (big ? 1.65 : 2.2) - (big ? 0.38 : 0.12), big ? -0.18 : 0.60 * Math.cos(phase),
    release];
}

// Support functions project the actual rounded parts onto the front direction.
// They use the same local rotations/dimensions as the shader, including satellites.
function turnX(v: number[], a: number) {
  'worklet';
  return [v[0], Math.cos(a) * v[1] + Math.sin(a) * v[2], -Math.sin(a) * v[1] + Math.cos(a) * v[2]];
}
function turnY(v: number[], a: number) {
  'worklet';
  return [Math.cos(a) * v[0] - Math.sin(a) * v[2], v[1], Math.sin(a) * v[0] + Math.cos(a) * v[2]];
}
function turnZ(v: number[], a: number) {
  'worklet';
  return [Math.cos(a) * v[0] + Math.sin(a) * v[1], -Math.sin(a) * v[0] + Math.cos(a) * v[1], v[2]];
}
function dot3(a: number[], b: readonly number[]) {
  'worklet';
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}
function roundedSupport(n: number[], x: number, y: number, r: number, depth: number, bevel: number) {
  'worklet';
  return (x - r) * Math.abs(n[0]) + (y - r) * Math.abs(n[1])
    + (r - bevel) * Math.hypot(n[0], n[1]) + (depth - bevel) * Math.abs(n[2]) + bevel;
}
type FrontPose = {
  uZero: number[]; uCoin0: number[]; uCoin1: number[]; uCoin2: number[];
  uCoinTurn0: number[]; uCoinTurn1: number[]; uCoinTurn2: number[];
  uSafe: number[]; uEnvelope: number[]; uNote: number[]; uBook: number[]; uBookTurn: number[];
};
function sceneSupport(id: number, sign: number, pose: FrontPose) {
  'worklet';
  const n = [Math.SQRT1_2 * sign, Math.SQRT1_2 * sign, 0];
  if (id === 0) {
    const q = turnY(n, -0.18);
    q[1] *= pose.uZero[1];
    let bound = roundedSupport(q, 0.51, 0.85, 0.43, 0.205, 0.065);
    if (pose.uZero[0] > 0) {
      const coins = [pose.uCoin0, pose.uCoin1, pose.uCoin2];
      const turns = [pose.uCoinTurn0, pose.uCoinTurn1, pose.uCoinTurn2];
      for (let i = 0; i < 3; i++) {
        const c = coins[i], t = turns[i];
        const v = [n[0], n[1] * t[2], -n[1] * t[3]];
        const local = [v[0] * t[0] - v[2] * t[1], v[1], v[0] * t[1] + v[2] * t[0]];
        const depth = c[3] * iconLerp(0.80, 0.105, Math.max(pose.uZero[2], iconEase(0, 0.82, pose.uZero[0]))) + 0.028;
        bound = Math.max(bound, dot3(n, c) + c[3] * Math.hypot(local[0], local[1]) + depth * Math.abs(local[2]));
      }
    }
    return bound;
  }
  if (id === 1) {
    const k = pose.uSafe[0];
    const q = turnY(n, -Math.PI * k - 0.14 * k);
    let bound = roundedSupport(q, 0.88, 0.88, 0.88, 0.11 - 0.02 * k, 0.044);
    if (k > 0) {
      const f = turnX([-q[0], q[1], -q[2]], -0.16 * k);
      const bud = pose.uSafe[2];
      if (bud > 0) {
        const centre = [iconLerp(0.42, 0.65, bud), iconLerp(0.44, 0.66, bud), iconLerp(-0.065, 0.20, bud)];
        const size = iconLerp(0.18, 1, iconEase(0, 0.65, bud));
        // Shield shoulders/crown/foot, with its rounded bevel.
        const rim = Math.max(0.285 * f[1], 0.285 * Math.abs(f[0]) + 0.2394 * f[1], -0.325 * f[1]);
        bound = Math.max(bound, dot3(f, centre) + size * (rim + 0.025 + 0.095 * Math.abs(f[2])));
      }
      bound = Math.max(bound, dot3(f, [0, 0.015, 0.32 * k]) + (f[1] > 0 ? 0.12 : 0.28) * k * Math.abs(f[1]) + iconLerp(0.065, 0.35, k));
    }
    return bound;
  }
  if (id === 2) {
    const q = turnZ(turnX(turnY(n, pose.uEnvelope[3] - 0.10), -0.16), -0.10);
    const offset = q[1] * pose.uNote[1];
    let bound = roundedSupport(q, 0.91, 0.57, 0.13, 0.28, 0.06) - 0.10 * q[1] + 0.085 * q[2];
    const flapN = turnX(q, -pose.uEnvelope[0] * 2.82);
    const flap = Math.max(0.80 * Math.abs(flapN[0]), -0.66 * flapN[1]) + 0.06 * Math.hypot(flapN[0], flapN[1]) + 0.065 * Math.abs(flapN[2]);
    bound = Math.max(bound, dot3(q, [0, 0.42, 0.02]) + flap + 0.14 * flapN[2]);
    const rise = pose.uNote[0];
    if (rise > 0) bound = Math.max(bound, roundedSupport(q, 0.76, 0.44 + 0.12 * rise, 0.14, 0.045, 0.028) + dot3(q, [0, -0.08 + 0.37 * rise, -0.03 + 0.10 * rise]));
    for (let i = 0; i < 2; i++) {
      const bud = pose.uEnvelope[i + 1];
      if (bud <= 0) continue;
      const h = i === 0;
      const at = [iconLerp(h ? 0.40 : -0.50, h ? 1.04 : -0.96, bud),
        iconLerp(h ? 0.02 : -0.20, h ? 0.78 : -0.54, bud) + (h ? 0.025 : 0.023) * Math.sin(pose.uNote[3] * 3 + (h ? 0 : 2)) * Math.min(bud, 1),
        iconLerp(h ? -0.06 : 0.02, h ? 0.20 : 0.36, bud)];
      bound = Math.max(bound, dot3(q, at) + (h ? 0.265 : 0.34) * iconLerp(0.16, 1, iconEase(0, 0.72, bud)));
    }
    return bound + offset;
  }
  const q = turnX(turnY(turnZ(n, pose.uBookTurn[1]), pose.uBookTurn[2]), pose.uBookTurn[0]);
  let bound = roundedSupport(q, 0.93, 0.75, 0.18, 0.22, 0.027);
  // Curl rises only at the lower-right corner; do not inflate the opposite edge.
  const corner = 0.75 * q[0] - 0.57 * q[1] + (0.17 + 0.24 * pose.uBook[3]) * q[2] + 0.18;
  bound = Math.max(bound, corner);
  if (pose.uBook[1] > 0) {
    const b = pose.uBook[1];
    bound = Math.max(bound, dot3(q, [iconLerp(0.63, 0.86, b), iconLerp(0.54, 0.68, b), iconLerp(0.06, 0.37, b)]) + 0.22 * iconLerp(0.16, 1, iconEase(0, 0.70, b)));
  }
  return bound;
}

/** All animation uniforms, with no hidden state, JS callbacks, or render-thread reads. */
export function iconFrame(
  position: number, t0: number, t1: number, t2: number, t3: number,
  bounce: number, lean: number, press: number, reduced: boolean,
) {
  'worklet';
  const pos = iconClamp(position, 0, 3);
  const lower = Math.floor(pos);
  const upper = Math.min(3, lower + 1);
  const times = [t0, t1, t2, t3];
  // Only the scene we departed has a nonzero clock. Preserve it on backward drags too.
  // Direction is derived from the inputs, never latched inside the renderer.
  const reverse = !reduced && pos > lower && times[lower] === 0 && times[upper] > 0;
  const a = reverse ? upper : lower;
  const b = reverse ? lower : upper;
  const s = reverse ? upper - pos : pos - lower;
  const w = iconClamp((s - 0.06) / 0.88);
  // Pose/heat settle before the integer boundary. The front owns the active window;
  // the swing starts with it, with no swing-only prelude or tail.
  const activity = iconEase(0.06, 0.15, s) * (1 - iconEase(0.85, 0.94, s));
  const mid = Math.sin(Math.PI * s) * activity;
  // Satellites are swallowed locally by the shader front, never on a global timer.
  const take = 1;
  const arrive = 1;
  for (let i = 0; i < 4; i++) {
    times[i] = reduced ? ICON_HERO[i] : i === a ? times[i] % STEP_LOOP[i] : 0;
  }
  const zt = times[0];
  const st = times[1];
  const et = times[2];
  const bt = times[3];
  const zr = iconEase(0.38, 0.82, zt) * (1 - iconEase(2.68, 2.96, zt)) * (a === 0 ? take : b === 0 ? arrive : 1);
  const c0 = coinPose(zt, zr, 0);
  const c1 = coinPose(zt, zr, 1);
  const c2 = coinPose(zt, zr, 2);
  const safe = iconEase(0.30, 0.70, st) * (1 - iconEase(2.75, 3.10, st));
  const safeTake = a === 1 ? take : b === 1 ? arrive : 1;
  const opened = iconEase(0.25, 0.70, et) * (1 - iconEase(2.40, 2.90, et));
  const envTake = a === 2 ? take : b === 2 ? arrive : 1;
  const heart = iconPop(0.32, 0.75, et) * (1 - iconEase(2.40, 2.80, et)) * envTake;
  const grin = iconPop(0.40, 0.83, et) * (1 - iconEase(2.48, 2.88, et)) * envTake;
  const flop = iconEase(1.35, 1.90, bt) * (1 - iconEase(2.20, 2.80, bt));
  const badge = iconPop(0.50, 0.86, bt) * (1 - iconEase(1.35, 1.62, bt)) * (a === 3 ? take : b === 3 ? arrive : 1);
  const fan = Math.sin(Math.PI * flop);
  const frame = {
    uScene: [a, b, w, s],
    uRadius: Math.max(1.72,
      Math.sqrt(c0[0] * c0[0] + c0[1] * c0[1] + c0[2] * c0[2]) + c0[3] + 0.06,
      Math.sqrt(c1[0] * c1[0] + c1[1] * c1[1] + c1[2] * c1[2]) + c1[3] + 0.06,
      Math.sqrt(c2[0] * c2[0] + c2[1] * c2[1] + c2[2] * c2[2]) + c2[3] + 0.06),
    uShape: [0.24, take, reduced ? 0 : 0.16 * Math.sin(2 * Math.PI * s) * activity, reverse ? -1 : 1],
    uTurn: reduced ? 0 : (reverse ? 0.7 : -0.7) * mid + 0.35 * bounce + 0.45 * iconClamp(lean, -1, 1),
    uScale: reduced ? 1 : (1 - 0.05 * mid) * (1 + 0.08 * iconClamp(bounce, -1, 1)),
    uPress: reduced ? 0 : iconClamp(press),
    uWet: mid * mid,
    uFront: [0, 0, 0, activity],
    uZero: [zr, 1 - 0.025 * Math.sin(Math.PI * zr), zt > 2.6 ? 1 : 0, 0],
    uCoin0: c0.slice(0, 4), uCoin1: c1.slice(0, 4), uCoin2: c2.slice(0, 4),
    uCoinTurn0: [Math.cos(c0[4]), Math.sin(c0[4]), Math.cos(c0[5]), Math.sin(c0[5])],
    uCoinTurn1: [Math.cos(c1[4]), Math.sin(c1[4]), Math.cos(c1[5]), Math.sin(c1[5])],
    uCoinTurn2: [Math.cos(c2[4]), Math.sin(c2[4]), Math.cos(c2[5]), Math.sin(c2[5])],
    uSafe: [safe, Math.sin((st - 0.7) * Math.PI * 2 / 1.025),
      iconPop(0.50, 0.82, st, 1.9) * (1 - iconEase(2.75, 2.93, st)) * safeTake, safeTake],
    uEnvelope: [opened * envTake, heart, grin, reduced ? 0 : Math.PI * 2 * iconEase(2.90, 3.40, et)],
    uNote: [iconPop(0.28, 0.78, et) * (1 - iconEase(2.40, 2.88, et)) * envTake,
      Math.sin(et * 2.5) * 0.025 * opened * envTake, Math.sin(et * 3.5) * 0.10, et],
    uBook: [flop, badge, iconEase(0.80, 1.0, bt), fan],
    uBookTurn: [-0.83 + 0.35 * flop, -0.10 + 0.28 * flop, -0.15 + 0.12 * flop, 0],
  };
  // A departed coin orbit must not leave a larger ray interval behind after the
  // front has swallowed it: different march origins can alter edge pixels.
  if (a === 0) frame.uRadius = iconLerp(frame.uRadius, 1.72, iconEase(0.85, 0.94, s));
  const lo = Math.min(a, b), hi = Math.max(a, b);
  const band = lo === 1 && hi === 2 ? 0.60 : hi === 3 ? 0.55 : 0.46;
  const direction = reverse ? -1 : 1;
  frame.uFront = [-Math.max(sceneSupport(a, -direction, frame), sceneSupport(b, -direction, frame)) - band,
    Math.max(sceneSupport(a, direction, frame), sceneSupport(b, direction, frame)) + band, band, activity];
  return frame;
}

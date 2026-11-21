// Tumble Card Routine: the autoplay timeline (spec §4) as a pure function of the loop time, and the card poses as a
// pure function of the deck state, so every frame is reproducible and the three stacks can match it frame for frame.
// Times are in seconds, lengths in points on the 402-wide canvas.
//
// The deck state is one number, `pos` = hand-offs done + progress h of the current one. Card k = floor(pos) is in
// front (leaving with h), k + 1 rises out of the slot (arriving with h), k + 2 waits under the panel's edge. The same
// poses are driven by the finger (h = −dy / 240) and by the loop, so the loop is a demo of the gesture.

import { CARDS, G, SPRING } from './tcr-theme';

export const LOOP = 9;

export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};
export const sineInOut = (k: number) => {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * k);
};

/** cubic-bezier(x1, y1, x2, y2) at x = k (Newton on x, then bisection as a fallback). */
export const bezier = (x1: number, y1: number, x2: number, y2: number, k: number) => {
  'worklet';
  if (k <= 0) return 0;
  if (k >= 1) return 1;
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let u = k;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * u + bx) * u + cx) * u - k;
    const dx = (3 * ax * u + 2 * bx) * u + cx;
    if (Math.abs(x) < 1e-6) break;
    if (Math.abs(dx) < 1e-6) break;
    u -= x / dx;
  }
  if (u < 0 || u > 1 || Math.abs(((ax * u + bx) * u + cx) * u - k) > 1e-4) {
    let lo = 0;
    let hi = 1;
    u = k;
    for (let i = 0; i < 30; i++) {
      const x = ((ax * u + bx) * u + cx) * u;
      if (Math.abs(x - k) < 1e-6) break;
      if (x < k) lo = u;
      else hi = u;
      u = (lo + hi) / 2;
    }
  }
  return ((ay * u + by) * u + cy) * u;
};
/** The throw: a hard launch that glides out (spec `emph`). */
export const emph = (k: number) => {
  'worklet';
  return bezier(0.05, 0.7, 0.1, 1, k);
};
/** The flick from a held scrub: the finger speeds up, then lets go. */
export const flick = (k: number) => {
  'worklet';
  return bezier(0.55, 0, 0.25, 1, k);
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
export const springAt = (t: number, from: number, to: number, v0: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  if (z === 1) {
    const e = Math.exp(-w0 * t);
    return to + e * (x0 + (v0 + w0 * x0) * t);
  }
  const s = Math.sqrt(z * z - 1);
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

// The landing overshoot is a `weighty` spring kicked from rest: normalise its first peak to 1.
const PULSE_PEAK = (() => {
  let m = 0;
  for (let t = 0; t < 1; t += 0.001) m = Math.max(m, springAt(t, 0, 0, 1, SPRING.weighty.d, SPRING.weighty.k));
  return m;
})();
export const pulse = (t: number) => {
  'worklet';
  if (t <= 0 || t > 1.4) return 0;
  return springAt(t, 0, 0, 1, SPRING.weighty.d, SPRING.weighty.k) / PULSE_PEAK;
};

/** Reveal clock of a landing card (s): numbers roll, dots fill, bars rise, the photo settles. Done at REVEAL. */
export const REVEAL = 0.8;

/** Everything the deck shows, for one moment. */
export type Deck = {
  pos: number; // hand-offs done + h
  peek: number; // focus pull on the slot card 0..1
  dA: number; // the slot card's drop below its slot (pt) while it rises in after a throw
  live: number; // 1 = a finger drives: the waiting card rises with h
  antY: number; // anticipation: front card dips (pt)
  antRot: number; // … and leans further in its own direction (deg)
  antSliver: number; // … while the slot card lifts (pt, negative = up)
  bumpIdx: number; // card that is landing (-1 none)
  bump: number; // its overshoot, 0..1 of its own bump
  rv: number[]; // reveal clock per card (s)
  posRate: number; // d pos / dt, for the motion blur
  spin: number; // extra spin of a thrown card (deg)
  press: number; // tap press on the front card 0..1
  nod: number; // "+" nod of the front card (deg)
  clk: number; // idle clock (s): the resting card breathes on it
};

// Autoplay beats (spec §4; throw 2 is the slow scrub and flick, see BUILD.md for the retimed hold)
const T0 = { a0: 0.7, a1: 0.8, s: 0.8, e: 1.35, land: 1.15 };
const T1 = { p0: 2.9, p1: 3.2, s0: 3.45, s1: 4.15, hHold: 0.55, f0: 4.3, e: 4.58, land: 4.45 };
const T2 = { a0: 6.0, a1: 6.1, s: 6.1, e: 6.65, land: 6.45 };
const T3 = { a0: 7.6, a1: 7.7, s: 7.7, e: 8.25, land: 8.05 };
/** Per card: when its reveal starts and when it has left the stage (end of the throw that removes it). */
const REVEAL_AT = [T3.land, T0.land + 0.1, T1.land, T2.land];
const LEFT_AT = [T0.e, T1.e, T2.e, T3.e];
/** Loop time at which each card rests on the stage (where the loop restarts after a touch). */
export const REST_AT = [0.35, 2.1, 5.4, 7.45];

const wrap = (t: number) => {
  'worklet';
  return ((t % LOOP) + LOOP) % LOOP;
};

/** The deck position and focus pull at loop time t. */
export const posAt = (tIn: number) => {
  'worklet';
  const t = wrap(tIn);
  let pos = 0;
  let peek = 0;
  if (t < T0.s) pos = 0;
  else if (t < T0.e) pos = emph(seg(t, T0.s, T0.e));
  else if (t < T1.s0) pos = 1;
  else if (t < T1.s1) pos = 1 + mix(0.22, T1.hHold, sineInOut(seg(t, T1.s0, T1.s1)));
  else if (t < T1.f0) pos = 1 + T1.hHold;
  else if (t < T1.e) pos = 1 + mix(T1.hHold, 1, flick(seg(t, T1.f0, T1.e)));
  else if (t < T2.s) pos = 2;
  else if (t < T2.e) pos = 2 + emph(seg(t, T2.s, T2.e));
  else if (t < T3.s) pos = 3;
  else if (t < T3.e) pos = 3 + emph(seg(t, T3.s, T3.e));
  else pos = 4;
  if (t >= T1.p0 && t < T1.e) peek = t < T1.p1 ? easeOut(seg(t, T1.p0, T1.p1)) : 1;
  return { pos, peek };
};

const RISE_FROM = [T0.e, T1.e, T2.e, T3.e];
const RISE_UNTIL = [T1.p0, T2.s, T3.s, T0.s];
/** After each throw the next card glides up into the slot from under the panel's edge: its drop (pt). */
const slotRise = (t: number) => {
  'worklet';
  for (let i = 0; i < 4; i++) {
    const tau = wrap(t - RISE_FROM[i]);
    if (tau < wrap(RISE_UNTIL[i] - RISE_FROM[i])) return Math.max(0, springAt(tau, G.slotHidden, 0, 0, SPRING.glide.d, SPRING.glide.k));
  }
  return 0;
};

const antAt = (t: number) => {
  'worklet';
  const beats = [T0, T2, T3];
  for (let i = 0; i < 3; i++) {
    const b = beats[i];
    if (t >= b.a0 && t < b.s + 0.2) return easeOut(seg(t, b.a0, b.a1)) * (1 - easeOut(seg(t, b.s, b.s + 0.2)));
  }
  return 0;
};

/** The deck at loop time t (the posRate is filled in by the caller from two samples). */
export const frameAt = (tIn: number): Deck => {
  'worklet';
  const t = wrap(tIn);
  const { pos, peek } = posAt(t);
  const a = antAt(t);
  // landing overshoot
  const lands = [T0.land, T1.land, T2.land, T3.land];
  let bumpIdx = -1;
  let bump = 0;
  for (let i = 0; i < 4; i++) {
    let tau = t - lands[i];
    if (tau < 0) tau += LOOP;
    if (tau < 1.4) {
      bumpIdx = (i + 1) % 4;
      bump = pulse(tau);
    }
  }
  const rv = [0, 0, 0, 0];
  for (let i = 0; i < 4; i++) {
    let tau = t - REVEAL_AT[i];
    if (tau < 0) tau += LOOP;
    let win = LEFT_AT[i] - REVEAL_AT[i];
    if (win < 0) win += LOOP;
    rv[i] = tau <= win ? Math.min(tau, REVEAL) : 0;
  }
  return {
    pos,
    peek,
    dA: slotRise(t),
    live: 0,
    antY: 6 * a,
    antRot: 1.5 * a,
    antSliver: -6 * a,
    bumpIdx,
    bump,
    rv,
    posRate: 0,
    spin: 0,
    press: 0,
    nod: 0,
    clk: t,
  };
};

/** Which card holds which role, and the hand-off split into the front card's throw (hf) and the slot card's rise (ha). */
export const rolesAt = (posIn: number, peek: number) => {
  'worklet';
  const k = Math.floor(posIn + 1e-6);
  const h = Math.max(0, posIn - k);
  const p22 = 0.22 * peek;
  // the peek lifts the slot card to h = 0.22 and softens the front card instead of throwing it; a drag from the peek
  // continues from there without a jump
  const ha = Math.max(h, p22);
  const hf = Math.max(0, (h - p22) / (1 - p22));
  const soft = peek * (1 - seg(hf, 0, 0.25));
  return { k, h, ha, hf, soft };
};

export const idxOf = (k: number) => {
  'worklet';
  return ((k % 4) + 4) % 4;
};

export type Lay = { tOff: number; bOff: number };

export type Pose = {
  vis: number; // 0 = not drawn
  role: number; // 0 front, 1 arriving, 2 waiting, 3 hidden
  x: number; // centre (canvas pt)
  y: number;
  rot: number; // deg
  s: number;
  alpha: number;
  z: number;
  blurLen: number; // motion streak length (pt)
  blurAng: number; // its direction (rad)
  D: number; // depth blur sigma (pt)
  U: number; // 1 = uniform depth blur, 0 = graded from y0
  y0: number; // card-local y where the graded blur starts
  soft: number; // front card softened by the focus pull
  lift: number; // shadow e2 (0) → e4 (1)
  rx: number; // tumble about the card's horizontal axis (deg, perspective 900 pt)
  rv: number; // reveal clock (s)
};

const restY = (i: number, lay: Lay) => {
  'worklet';
  return CARDS[i].cy + lay.tOff;
};
const slotY = (i: number, lay: Lay) => {
  'worklet';
  return G.slotTop + lay.bOff + (G.slotScale * CARDS[i].h) / 2;
};

/**
 * Motion streak length (pt): proportional to the card's speed above a 240 pt/s floor, so a slow drag stays sharp
 * (≤ 60 pt/s → 0) and a flick smears (1500 pt/s → 54 pt), capped at 64 pt.
 */
export const streak = (speed: number) => {
  'worklet';
  return Math.min(Math.max(0.043 * (speed - 240), 0), 64);
};

export const cardPose = (i: number, d: Deck, lay: Lay): Pose => {
  'worklet';
  const c = CARDS[i];
  const { k, h, ha, hf, soft } = rolesAt(d.pos, d.peek);
  const role = (((i - k) % 4) + 4) % 4;
  const dir = c.tilt < 0 ? -1 : 1;
  const bump = d.bumpIdx === i ? d.bump : 0;
  const pose: Pose = {
    vis: 1,
    role,
    x: c.cx,
    y: restY(i, lay),
    rot: c.tilt,
    s: 1,
    alpha: 1,
    z: 4 - role,
    blurLen: 0,
    blurAng: 0,
    D: 0,
    U: 0,
    y0: 0,
    soft: 0,
    lift: 0,
    rx: 0,
    rv: d.rv[i],
  };
  if (role === 0) {
    // leaving: thrown up and out, tumbling in the direction of its own tilt
    pose.x = c.cx + dir * 18 * hf;
    pose.y = restY(i, lay) - 520 * Math.pow(hf, 1.6) + d.antY - 5 * bump;
    // idle life on the resting card: a slow breath of tilt and float (two whole periods per loop, so it closes)
    const idle = 1 - seg(hf, 0, 0.08);
    const br = Math.sin((2 * Math.PI * d.clk) / (LOOP / 2));
    pose.rot = c.tilt + dir * (38 * hf + d.antRot + c.bump * bump) + d.spin * seg(hf, 0.02, 0.25) + d.nod + 0.4 * br * idle;
    pose.y += 2 * Math.sin((2 * Math.PI * d.clk) / (LOOP / 2) + 1.1) * idle;
    // the throw tumbles in depth too: the card tips back over its top edge (0 → 55°)
    pose.rx = 55 * Math.pow(hf, 0.9);
    pose.s = (1 - 0.1 * hf) * (1 - 0.03 * soft) * (1 - 0.03 * d.press);
    pose.alpha = 1 - seg(hf, 0.7, 1);
    pose.z = 4;
    pose.D = 3 * soft;
    pose.U = 1;
    pose.soft = soft;
    pose.lift = seg(hf, 0, 0.3);
    // velocity of the centre = d(centre)/d(hf) · d(hf)/dt
    const p22 = 0.22 * d.peek;
    const hfRate = h > p22 ? d.posRate / (1 - p22) : 0;
    const vx = dir * 18 * hfRate;
    const vy = -520 * 1.6 * Math.pow(Math.max(hf, 1e-4), 0.6) * hfRate;
    const sp = Math.sqrt(vx * vx + vy * vy);
    pose.blurLen = streak(sp);
    pose.blurAng = Math.atan2(vy, vx);
    if (pose.alpha <= 0.001) pose.vis = 0;
  } else if (role === 1) {
    // arriving: from the blurred slot behind the "+" to the stage, at the opposite tilt
    const ky = Math.pow(ha, 1.15);
    const ks = Math.pow(ha, 0.56);
    const y1 = slotY(i, lay) + d.dA * (1 - ha) + d.antSliver;
    pose.y = mix(y1, restY(i, lay), ky) - 5 * bump;
    pose.s = mix(G.slotScale, 1, ks);
    pose.rot = mix(c.tilt * G.slotTilt, c.tilt, ks) + dir * c.bump * bump;
    // it comes up out of the slot leaning back (−18°) and stands up as it lands
    pose.rx = -18 * (1 - ks);
    pose.z = 2;
    pose.D = 11 * (1 - seg(ha, 0.2, 0.85));
    pose.U = 0;
    pose.y0 = c.h * 0.7 * Math.min(1, ha / 0.22);
    pose.lift = 1 - seg(ha, 0.55, 1);
    if (d.dA * (1 - ha) > G.slotHidden - 1) pose.vis = 0;
  } else if (role === 2) {
    // waiting under the panel's edge; a finger raises it with h, the loop after the throw (as the next slot card)
    const drop = d.live ? G.slotHidden * (1 - seg(h, 0.4, 1)) : G.slotHidden;
    pose.y = slotY(i, lay) + drop;
    pose.s = G.slotScale;
    pose.rot = c.tilt * G.slotTilt;
    pose.rx = -18;
    pose.z = 1;
    pose.D = 11;
    pose.U = 0;
    pose.y0 = 0;
    pose.lift = 1;
    if (drop > G.slotHidden - 1) pose.vis = 0;
  } else {
    pose.vis = 0;
    pose.z = 0;
  }
  return pose;
};

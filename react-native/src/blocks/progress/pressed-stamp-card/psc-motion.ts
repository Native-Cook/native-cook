// Seconds throughout. All autoplay properties are deterministic functions of loop time.
import { CARD, LABEL, LIST, ROT, SEAL_R, sealCX, sealCY, SPRING } from './psc-theme';

export const LOOP = 10;
export const T = { events: [0.6, 2.2, 3.9], impacts: [1.56, 3.16, 5.04], gold: 5.04, return: 8.2, rest: 9.6 } as const;
export const clamp = (x: number, lo = 0, hi = 1) => { 'worklet'; return Math.max(lo, Math.min(hi, x)); };
export const seg = (t: number, a: number, b: number) => { 'worklet'; return clamp((t - a) / (b - a)); };
export const mix = (a: number, b: number, p: number) => { 'worklet'; return a + (b - a) * p; };
/** Exact cubic-bezier inversion, rather than approximating the batch's named curves. */
export const bezier = (p: number, x1: number, y1: number, x2: number, y2: number) => {
  'worklet';
  let lo = 0; let hi = 1; let u = p;
  for (let i = 0; i < 16; i++) {
    u = (lo + hi) / 2;
    const v = 1 - u;
    const x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
    if (x < p) lo = u; else hi = u;
  }
  if (p <= 0 || p >= 1) return clamp(p);
  const v = 1 - u;
  return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
};
export const inOut = (p: number) => { 'worklet'; return bezier(p, 0.65, 0, 0.35, 1); };
export const out = (p: number) => { 'worklet'; return bezier(p, 0.2, 0.8, 0.2, 1); };
export const into = (p: number) => { 'worklet'; return bezier(p, 0.32, 0, 0.67, 0); };
export const springAt = (t: number, from: number, to: number, velocity: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const w = Math.sqrt(k); const z = d / (2 * w); const x = from - to;
  if (z >= 1) return to + Math.exp(-w * t) * (x + (velocity + w * x) * t);
  const wd = w * Math.sqrt(1 - z * z);
  return to + Math.exp(-z * w * t) * (x * Math.cos(wd * t) + (velocity + z * w * x) / wd * Math.sin(wd * t));
};
export type Seal = { x: number; y: number; r: number; a: number; rotation: number; gold: number; flash: number; lift: number };
export type Frame = {
  seals: Seal[]; count: number; oldCount: number; roll: number; orders: number; dip: number;
  ripple: [number, number, number, number]; light: number; gold: number; fly: Seal; target: number; event: number;
  textFrom: number; textTo: number; text: number;
  phase: number; tilt: number; dieTilt: number; dieGlow: number; dent: number; push: number;
  sweep: number; buttonGold: number; buttonRoll: number; redeemRing: number; flip: number; flipFlash: number; jolt: number;
};
export const emptyFly = (): Seal => { 'worklet'; return { x: 0, y: 0, r: 0, a: 0, rotation: 0, gold: 0, flash: 0, lift: 0 }; };
export const restFrame = (count: number, light = 315): Frame => {
  'worklet';
  const seals: Seal[] = [];
  for (let i = 0; i < 8; i++) seals.push({ x: sealCX(i), y: sealCY(i), r: SEAL_R, a: i < count ? 1 : 0, rotation: ROT[i], gold: count === 8 ? 1 : 0, flash: 0, lift: 0 });
  return { seals, count, oldCount: count, roll: 1, orders: count, dip: 0, ripple: [0, 0, 0, 0], light, gold: count === 8 ? 1 : 0, fly: emptyFly(), target: -1, event: -1, textFrom: count, textTo: count, text: 1,
    phase: 0, tilt: 0, dieTilt: 0, dieGlow: 0, dent: 0, push: 1, sweep: -1, buttonGold: count === 8 ? 1 : 0, buttonRoll: count === 8 ? 1 : 0, redeemRing: 0, flip: 0, flipFlash: 0, jolt: 0 };
};
/** Normalised impulse for the 15 / 93 card spring. */
export const cardImpulse = (dt: number, depth: number) => {
  'worklet'; return dt >= 0 && dt < 0.7 ? springAt(dt, 0, 0, depth / 0.0447, 15, 93) : 0;
};
/** Opaque tool: approach, accelerating fall, compression, dwell, lift and clear exit. */
export const stampEvent = (f: Frame, elapsed: number, target: number, reduced = false, origin: { x: number; y: number } = { x: LABEL.cx, y: LABEL.cy }): Frame => {
  'worklet';
  const eighth = target === 7; const impact = eighth ? 1.14 : 0.96;
  const drop = impact - 0.14; const cx = sealCX(target); const cy = sealCY(target);
  const dt = elapsed - impact; const landed = dt >= -1e-9;
  f.target = target; f.event = elapsed;
  f.orders = target + clamp(springAt(elapsed, 0, 1, 0, SPRING.glide.d, SPRING.glide.k));
  if (reduced) {
    f.seals[target].a = seg(elapsed, 0, 0.2); f.count = target + 1; f.oldCount = target;
    f.roll = seg(elapsed, 0, 0.2); f.textFrom = target; f.textTo = target + 1; f.text = f.roll;
    if (eighth) { f.gold = seg(elapsed, 0.2, 0.4); f.buttonGold = f.gold; f.buttonRoll = f.gold; for (let i = 0; i < 8; i++) f.seals[i].gold = f.gold; }
    return f;
  }
  if (elapsed >= 0.25 && !landed) {
    const p = inOut(seg(elapsed, 0.25, 0.7)); const fall = into(seg(elapsed, drop, impact));
    f.fly = { x: mix(440 - CARD.x, cx, p), y: mix(LIST.top + origin.y - CARD.y, cy, p) - 120 * p * (1 - p),
      r: mix(52, 37, fall), a: 1, rotation: 8 * (1 - fall), gold: 0, flash: 0, lift: 1 - fall };
    f.dieTilt = 8 * (1 - fall);
    if (eighth) { f.dieGlow = 0.7 * seg(elapsed, 0.7, 1); f.push = 1 + 0.05 * seg(elapsed, 0.7, 1); }
  }
  if (landed) {
    const age = Math.max(0, dt);
    f.count = target + 1; f.oldCount = target; f.textFrom = target; f.textTo = target + 1;
    f.text = out(seg(age, 0.08, 0.38)); f.roll = clamp(springAt(age - 0.04, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
    f.seals[target].a = 1; f.seals[target].r = SEAL_R;
    // lift carries wetness (1 -> 0 over 1200 ms); the impression is already underneath the die.
    f.seals[target].lift = 1 - seg(age, 0, 1.2);
    f.seals[target].flash = (1 - out(seg(age, 0, 0.44))) * 0.55;
    f.dip = cardImpulse(age, eighth ? 9 : 6); f.tilt = f.dip / (eighth ? 9 : 6) * 1.6;
    f.jolt = age; f.dent = 1.4 * (1 - out(seg(age, 0, 0.38)));
    const rp = out(seg(age, 0, 0.44)); f.ripple = [cx, cy, 180 * rp, 0.6 * (1 - rp)];
    for (let i = 0; i < target; i++) {
      const distance = Math.hypot(sealCX(i) - cx, sealCY(i) - cy); const hit = age - distance / 900;
      if (hit >= 0 && hit < 0.6) {
        const bounce = springAt(hit, 0, 0, -65, 12, 260);
        f.seals[i].y += bounce; f.seals[i].rotation += bounce / 2.5 * (i % 2 ? 1.5 : -1.5);
      }
      const edge = (distance - f.ripple[2]) / 14; f.seals[i].flash += Math.exp(-edge * edge) * f.ripple[3] * 0.6;
    }
    // The final reveal must be clear by the director's 5.30 s poster.
    const dwell = eighth ? 0.06 : 0.15; const lift = out(seg(age, dwell, dwell + 0.32));
    if (age < dwell + 0.32) {
      const s = seg(age, dwell, dwell + 0.32); const e = Math.pow(s, 1.85);
      f.fly = { x: mix(cx, 440 - CARD.x, eighth ? e : lift), y: cy - (eighth ? 120 * e : 92 * lift),
        r: age < 0.06 ? 35.5 : mix(37, 55, Math.pow(seg(age, dwell, dwell + 0.32), 0.6929)), a: 1, rotation: 8 * lift, gold: 0, flash: 0, lift };
      f.dieTilt = 8 * lift; f.dieGlow = eighth ? 0.7 * (1 - s * s) : 0;
    } else f.fly = emptyFly();
    if (eighth) {
      // The push-in is released by the hit itself: a short spring, never a one-frame snap.
      if (age < 0.6) f.push = 1 + 0.05 * springAt(age, 1, 0, 0, 24, 520);
      f.gold = seg(age, 0, 1.2); f.sweep = seg(age, 0.08, 0.78);
      for (let i = 0; i < 8; i++) {
        const distance = Math.hypot(sealCX(i) - cx, sealCY(i) - cy);
        f.seals[i].gold = out(seg(age - distance / 500, 0, 0.11));
        f.seals[i].flash += Math.sin(Math.PI * seg(age - distance / 77 * 0.06, 0, 0.5)) * 0.4;
      }
      f.buttonGold = out(seg(age, 1.26, 1.76)); f.buttonRoll = clamp(springAt(age - 1.26, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
    }
  }
  return f;
};
/** A single face replacement happens edge-on; no fading or reverse gilding. */
export const flipFrame = (elapsed: number, light = 315, reduced = false): Frame => {
  'worklet';
  const p = reduced ? 1 : elapsed >= 0.7 ? 1 : clamp(springAt(elapsed, 0, 1, 0, 16, 140));
  const swapped = p >= 0.5; const f = restFrame(swapped ? 0 : 8, light);
  f.flip = 180 * p - (swapped ? 180 : 0);
  f.flipFlash = !swapped ? Math.exp(-Math.pow((p * 180 - 60) / 10, 2)) : 0;
  f.orders = mix(8, 5, out(seg(elapsed, 0, 0.7)));
  f.buttonGold = 1 - out(seg(elapsed, 0, 0.5)); f.buttonRoll = 1 - clamp(springAt(elapsed, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
  f.dip = cardImpulse(elapsed - 0.48, 3);
  return f;
};
export const frameAt = (time: number, origin: { x: number; y: number } = { x: LABEL.cx, y: LABEL.cy }): Frame => {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  const light = 315 + 12 * Math.sin(2 * Math.PI * t / LOOP);
  let f = restFrame(5, light); f.phase = t;
  for (let j = 0; j < 3; j++) if (t >= T.events[j]) stampEvent(f, t - T.events[j], 5 + j, false, origin);
  if (t >= 6.9 && t < 7.4) f.sweep = seg(t, 6.9, 7.4);
  if (t >= 7.6 && t < 8.1) f.sweep = seg(t, 7.6, 8.1);
  if (t >= 8.05 && t < 8.29) f.redeemRing = seg(t, 8.05, 8.29);
  if (t >= 8.2) {
    f = flipFrame(t - 8.2, light); f.phase = t;
    if (t >= 8.9 && t < 8.98) {
      const p = out(seg(t, 8.9, 8.98));
      f.fly = { x: mix(440 - CARD.x, sealCX(0), p), y: sealCY(0) - 14 * Math.sin(Math.PI * p), r: mix(52, 37, p), a: 1, rotation: 0, gold: 0, flash: 0, lift: 1 - p };
    }
    if (t >= 8.98) {
      const index = Math.min(4, Math.floor((t - 8.98) / 0.13)); const age = t - 8.98 - index * 0.13;
      f = restFrame(index + 1, light); f.phase = t; f.orders = 5;
      f.dip = age < 0.13 ? 1.5 * Math.sin(Math.PI * age / 0.13) : 0;
      f.oldCount = index; f.roll = out(seg(age, 0, 0.06)); f.textFrom = index; f.textTo = index + 1; f.text = f.roll;
      if (t < 9.6) {
        const p = clamp(age / (index === 4 ? 0.1 : 0.13)); const hop = 14 * Math.sin(Math.PI * p);
        const next = Math.min(4, index + 1);
        f.fly = { x: mix(sealCX(index), index === 4 ? 440 - CARD.x : sealCX(next), index === 4 ? out(p) : p),
          y: mix(sealCY(index), sealCY(next), p) - hop, r: 37 + hop * 0.28, a: 1, rotation: hop * 0.15, gold: 0, flash: 0, lift: hop / 14 };
      }
    }
  }
  if (t >= 9.6) { f = restFrame(5, light); f.phase = t; }
  return f;
};

import { mat3AxisAngle, mat3Identity, mat3Multiply, mat3Orthonormalise, type Mat3 } from './prism-math';

export const TUMBLE = 5;
export const LOOP = 10;
export const CLOCK_WRAP = 600;
export const ANTICIPATION = 6.75;
export const BURST = 7.3;

export function clamp01(x: number) {
  'worklet';
  return Math.max(0, Math.min(1, x));
}
export function modulo(x: number, period: number) {
  'worklet';
  return ((x % period) + period) % period;
}
export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function smoothstep(low: number, high: number, value: number) {
  'worklet';
  const t = clamp01((value - low) / (high - low));
  return t * t * (3 - 2 * t);
}
/** Signed time difference, including gestures that straddle the 600-second clock wrap. */
export function prismAge(now: number, then: number) {
  'worklet';
  const difference = now - then;
  return difference < -300 ? difference + CLOCK_WRAP : difference > 300 ? difference - CLOCK_WRAP : difference;
}
/** Forward age for a past touch; a retired timestamp must never become recent after a wrap. */
export function prismSinceTouch(now: number, then: number) {
  'worklet';
  return then < 0 ? CLOCK_WRAP : modulo(now - then, CLOCK_WRAP);
}
export function prismCaption(clock: number, reduced = false) {
  'worklet';
  const t = modulo(clock, TUMBLE);
  const away = clamp01((t - 2.8) / 1.4);
  const opacity = t < 0.25 || t >= 4.2 ? 0 : easeOutCubic((t - 0.25) / 1.2) * (1 - away);
  return { opacity, blur: reduced ? 0 : 8 * away, progress: reduced ? 0 : away };
}
export function prismIdleLight(clock: number): number[] {
  'worklet';
  return [0.18 * Math.sin(2 * Math.PI * modulo(clock, TUMBLE) / TUMBLE), 0];
}
export function prismRest(clock: number) {
  'worklet';
  const t = modulo(clock, TUMBLE);
  const bump = t >= 2.2 && t <= 3.6 ? Math.pow(Math.sin(Math.PI * (t - 2.2) / 1.4), 2) : 0;
  const sweep = clamp01((t - 1.9) / 1.6);
  return { energy: 0.4 + 0.2 * bump, sweep,
    sweepPower: t > 1.9 && t < 3.5 ? 0.62 * Math.pow(Math.sin(Math.PI * sweep), 2) : 0 };
}

/** s is time since anticipation; negative means inactive. Shared by touch and script. */
export function prismSurge(s: number, poseFan: number, gain = 1, releaseCharge = 1) {
  'worklet';
  let charge = 0, scale = 1, flash = 0, ring = 0, sweep = 0, sweepPower = 0;
  let energy = 0, spin = 0, fan = poseFan;
  const elapsed = s - 0.55;
  const b = Math.abs(elapsed) < 1e-9 ? 0 : elapsed;
  if (s < 0) return { charge, scale, flash, ring, sweep, sweepPower, energy, spin, fan };
  if (b < 0) {
    charge = easeOutCubic(s / 0.55);
    scale = 1 - 0.035 * charge;
    spin = -0.3 * charge;
    fan = poseFan + (Math.PI - poseFan) * 0.6 * charge;
    energy = 0.3 * charge;
  } else {
    charge = releaseCharge * Math.exp(-b / 0.08);
    if (charge < 0.002) charge = 0;
    const kick = Math.exp(-7 * b) * (-0.035 * releaseCharge * Math.cos(11 * b) + 0.09 * gain * Math.sin(11 * b));
    // Use the decaying envelope to settle only once, not at an early zero crossing.
    scale = Math.exp(-7 * b) * Math.hypot(0.035 * releaseCharge, 0.09 * gain) < 0.0005 ? 1 : 1 + kick;
    flash = gain * Math.exp(-b / 0.28);
    if (flash < 0.004) flash = 0;
    ring = b < 0.9 - 1e-9 ? Math.max(0.0001, easeOutCubic(b / 0.9)) : 0;
    energy = 1.1 * flash + 0.3 * gain * Math.exp(-b / 0.9);
    spin = b < 2.6 - 1e-9 ? -0.3 * releaseCharge + (2 * Math.PI + 0.3 * releaseCharge) * (1 - Math.pow(1 - clamp01(b / 2.6), 4)) : 0;
    const opening = b < 0.35 ? easeOutCubic(b / 0.35) : 1 - smoothstep(1.1, 2.2, b);
    // The pose may already be wider than the burst's own opening; never pull it shut.
    fan = poseFan + (Math.max(poseFan, 1.82 * Math.PI) - poseFan) * opening;
    const pass = clamp01((b - 0.35) / 1);
    sweep = pass;
    sweepPower = b > 0.35 && b < 1.35 ? 0.5 * Math.pow(Math.sin(Math.PI * pass), 2) : 0;
  }
  // The first band crosses the burst, rather than being cut off by it.
  if (s >= 0.2 && s < 0.7) {
    const power = clamp01((s - 0.2) / 0.1) * clamp01((0.7 - s) / 0.2);
    if (power > sweepPower) { sweep = clamp01((s - 0.2) / 0.5); sweepPower = power; }
  }
  return { charge, scale, flash, ring, sweep, sweepPower, energy, spin, fan: Math.min(1.975 * Math.PI, fan) };
}

export function prismTimeline(clock: number, poseFan: number, surgeTime = -1, gain = 1,
  reduced = false, heldCharge = -1, heldTime = 0, releaseCharge = 1) {
  'worklet';
  const rest = prismRest(clock);
  const surge = prismSurge(surgeTime, poseFan, gain, releaseCharge);
  if (heldCharge >= 0) {
    surge.charge = heldCharge;
    surge.scale = 1 - 0.035 * heldCharge;
    surge.spin = -0.3 * heldCharge;
    surge.fan = poseFan + (Math.PI - poseFan) * 0.6 * heldCharge;
    surge.energy = 0.3 * heldCharge;
    surge.sweep = clamp01((heldTime - 0.2) / 0.5);
    surge.sweepPower = clamp01((heldTime - 0.2) / 0.1) * clamp01((0.7 - heldTime) / 0.2);
  }
  const useSurge = surge.sweepPower > rest.sweepPower;
  return {
    charge: reduced ? 0 : surge.charge, scale: reduced ? 1 : surge.scale,
    flash: surge.flash, ring: reduced ? 0 : surge.ring,
    energy: Math.min(1.5, rest.energy + (reduced ? 1.1 * surge.flash : surge.energy)),
    sweep: reduced ? 0 : useSurge ? surge.sweep : rest.sweep,
    sweepPower: reduced ? 0 : Math.max(rest.sweepPower, surge.sweepPower),
    spin: reduced ? 0 : surge.spin, fan: reduced ? poseFan : surge.fan,
  };
}
/** Pure reference for a complete untouched autoplay loop (also used by the proof script). */
export function prismScriptedTimeline(clock: number, poseFan = Math.PI) {
  'worklet';
  const loop = modulo(clock, LOOP);
  return prismTimeline(clock, poseFan, loop >= ANTICIPATION ? loop - ANTICIPATION : -1);
}

/** +x drag is +Y rotation; +y drag is -X rotation in the contract's view coordinates. */
export function prismTrackball(offset: Mat3, dx: number, dy: number, radius: number): Mat3 {
  'worklet';
  return mat3Orthonormalise(mat3Multiply(mat3AxisAngle(-dy, dx, 0, Math.hypot(dx, dy) / radius), offset));
}
export function prismTranspose(m: Mat3): Mat3 {
  'worklet';
  return [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]];
}
/** Shortest-axis exponential return, including the otherwise singular half-turn. */
export function prismReturn(offset: Mat3, dt: number): Mat3 {
  'worklet';
  const angle = Math.acos(Math.max(-1, Math.min(1, (offset[0] + offset[4] + offset[8] - 1) / 2)));
  const next = angle * Math.exp(-dt / 0.35);
  if (next < 0.001) return mat3Identity();
  let x = offset[7] - offset[5], y = offset[2] - offset[6], z = offset[3] - offset[1];
  if (Math.PI - angle < 0.0001) {
    // Recover the dominant diagonal so mixed-sign axes at PI keep their signs.
    if (offset[0] >= offset[4] && offset[0] >= offset[8]) {
      x = Math.sqrt(Math.max(0, (offset[0] + 1) / 2));
      y = (offset[1] + offset[3]) / (4 * x); z = (offset[2] + offset[6]) / (4 * x);
    } else if (offset[4] >= offset[8]) {
      y = Math.sqrt(Math.max(0, (offset[4] + 1) / 2));
      x = (offset[1] + offset[3]) / (4 * y); z = (offset[5] + offset[7]) / (4 * y);
    } else {
      z = Math.sqrt(Math.max(0, (offset[8] + 1) / 2));
      x = (offset[2] + offset[6]) / (4 * z); y = (offset[5] + offset[7]) / (4 * z);
    }
  }
  return mat3AxisAngle(x, y, z, next);
}

export type PrismMote = {
  x: number; y: number; z: number; radius: number; alpha: number; colour: number;
  phase: number; cycles: number; rise: number; sway: number; twinkle: number; throw: number; bokeh: boolean;
};
/** Deterministic data, shared by the actual Skia pictures and the offline density proof. */
export function makePrismMotes(): PrismMote[] {
  let seed = 62417;
  const random = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
  const motes: PrismMote[] = [];
  for (let i = 0; i < 64; i++) {
    const angle = random() * Math.PI * 2;
    const distance = 124 * (1.08 + 1.52 * random());
    const back = i < 38;
    const bokeh = i >= 58;
    motes.push({ x: Math.cos(angle) * distance * 0.9, y: Math.sin(angle) * distance * 1.25,
      z: (back ? -1 : 1) * (0.05 + 0.95 * random()),
      radius: bokeh ? 4 + 3 * random() : back ? 0.7 + 0.9 * random() : 1 + 1.2 * random(),
      alpha: bokeh ? 0.09 + 0.07 * random() : 0.22 + 0.42 * random(),
      colour: bokeh ? 2 : random() < 0.1 ? 3 : Math.floor(random() * 3), phase: random(), cycles: random() < 0.5 ? 1 : 2,
      rise: 40 + 70 * random(), sway: 4 + 4 * random(), twinkle: random(), throw: 24 + 40 * random(), bokeh });
  }
  return motes;
}
export const PRISM_MOTES = makePrismMotes();

/** Distance from a point to a box given by its centre and half sizes (0 inside), in design points from the sphere's centre. */
function boxDistance(x: number, y: number, centreX: number, centreY: number, halfWidth: number, halfHeight: number) {
  'worklet';
  const dx = Math.max(0, Math.abs(x - centreX) - halfWidth);
  const dy = Math.max(0, Math.abs(y - centreY) - halfHeight);
  return Math.hypot(dx, dy);
}

export function prismMoteAt(mote: PrismMote, clock: number, charge: number, b: number, gain: number, flash: number, scale: number) {
  'worklet';
  const u = modulo(mote.cycles * clock / LOOP + mote.phase, 1);
  const x = mote.x + mote.sway * Math.sin(2 * Math.PI * (mote.cycles * clock / LOOP + mote.phase));
  const y = mote.y - mote.rise * (u - 0.5);
  const distance = Math.hypot(x, y);
  const thrown = b >= 0 ? mote.throw * gain * (1 - Math.exp(-b / 0.12)) * Math.exp(-b / 0.9) : 0;
  const radial = -0.16 * (distance - 124) * charge + thrown;
  const ratio = 1 + radial / Math.max(distance, 1);
  const finalDistance = distance * ratio;
  const twinkle = 0.75 + 0.25 * Math.sin(2 * Math.PI * (clock / LOOP + mote.twinkle));
  let alpha = mote.alpha * Math.pow(Math.sin(Math.PI * u), 1.5) * twinkle * (1 + 1.5 * flash);
  if (mote.z < 0) alpha *= smoothstep(124 * scale, 124 * scale + 6, finalDistance);
  const px = x * ratio, py = y * ratio, reach = mote.radius * (mote.bokeh ? 2.5 : 1);
  // Dust shows where the light is: bright in the pillar, faint towards the screen's edges.
  alpha *= 0.3 + 0.7 * Math.exp(-(px * px) / (150 * 150));
  // And never over the type: a speck on a letter reads as dirt.
  alpha *= smoothstep(0, 14, boxDistance(px, py, 0, -232, 150, 44) - reach);
  alpha *= smoothstep(0, 14, boxDistance(px, py, 0, 200, 96, 12) - reach);
  return { x: px, y: py, alpha: Math.min(mote.bokeh ? 0.16 : 1, alpha) };
}

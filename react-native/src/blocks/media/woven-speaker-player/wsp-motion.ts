// Woven Speaker Player — the loop, the gesture constants and the physics. Every frame of the autoplay is a pure function of the loop
// time t (no accumulated state), so the Swift and Kotlin ports can match it frame for frame. The finger drives the same `Look`
// through the live controller in WovenSpeaker.tsx; both end in the same numbers the canvas reads.
//
// The state is a handful of numbers (see `Look`): the track position, how far the pause glyph has morphed, the two weave layers'
// angles (the outer one follows the finger, the inner one at 0.6 of it, so the lozenges shear), the finger's dent, up to four
// travelling pulses, the lamp, and the three text lines of the page.
//
// Loop (LOOP = 8 s; payoff in the first 3 s). t is seconds; "touch" is the autoplay's finger.
//   t start | t end | element            | property             | from → to           | curve / spring
//   0.00    | 0.40  | all                | rest (paused, 0:00)  | the shot            | —
//   0.40    | 0.48  | glyph              | press scale          | 1 → 0.9 → 1 (0.62)  | out, spring
//   0.40    | 0.66  | glyph              | play → pause         | path morph          | inOut (.65, 0, .35, 1)
//   0.46    | 0.76  | display bloom      | radius 60 → 84, 0.65 → 1 | the lamp comes up first | out (.2, .8, .2, 1)
//   0.52    |       | mesh               | ring 0 (strong, 4 pt) | launched at r 104   | 111 pt/s, reaches the rim at 1.07
//   0.52    | 0.74  | mesh red rim light | reach 18 → 46 pt     | red spills onto the weave | out
//   0.56    | 0.88  | title, line, pill  | text                 | cross-fade, 4 pt rise | 40 ms steps behind the ring
//   0.73    | 0.93  | bezel              | red reflection       | fades in            | follows the arc head
//   0.40    | 1.40  | arc, time          | position             | 0:00 → 0:01         | linear, 1 s per s
//   1.03    |       | mesh               | ring 1 (2.2 pt)      | one beat later      | 508 ms beat grid (118 bpm)
//   1.40    | 1.48  | dome               | dent 5 pt under the finger at 3 o'clock | 0 → 5 pt | out; light haptic on a touch
//   1.50    | 2.60  | dome, arc, time    | the turn (hero)      | 0:01 → 1:40, finger 3 → 9 o'clock (178°) | inOut (.45, 0, .25, 1)
//           |       | outer / inner layer | angle               | 0.22 × finger / 0.6 of that | the weave shears; peak at 2.3 s
//   2.60    | 2.90  | all                | hold                 | the shear holds     | —
//   2.90    | 3.40  | dome               | coast 12°            | 6.7 s of track more | exp decay (0.996 per ms, τ 0.18 s)
//   3.40    | 3.75  | position           | snap to the second   | 106.25 → 106        | spring (response 0.30, ζ 1)
//   2.90    | 4.30  | inner layer        | catches up to outer  | shear → 0           | spring (response 0.96, ζ 0.76), forced to 0 by 4.3
//   3.57    | 5.10  | mesh               | rings 6 – 9 on the beat | 2.2 pt, strong at 4.59 | as above
//   5.60    | 5.68  | dome               | dent 5 pt at 9:30    | 0 → 5 pt            | out
//   5.68    | 6.40  | dome, arc, time    | the turn back, faster | 1:47 → 0:00         | inOut (.4, 0, .2, 1); both layers back to rest
//   6.40    | 6.62  | arc head           | limit pulse          | the start of the track | once
//   6.45    | 7.25  | inner layer        | catches up           | shear → 0           | spring (0.55, ζ 0.8), forced to 0 by 7.25
//   6.60    | 7.10  | glyph, bloom, rim, wash, texts | pause   | the reverse of play, texts in reverse order | 220 ms glyph
//   7.25    | 8.00  | all                | rest = t 0           | —                   | —
// Rest: 0.40 s + 0.75 s = 14 % of the loop.

import { ref } from './wsp-theme';

export const LOOP = 8;

/** The frame a poster or a preview of the effect should use: the shear at its peak. */
export const POSTER_T = 2.3;

export const T = {
  tap: 0.4,
  pressEnd: 0.48,
  glyph: [0.4, 0.66],
  bloom: [0.46, 0.76],
  rim: [0.52, 0.74],
  title: [0.56, 0.8],
  line: [0.6, 0.84],
  pill: [0.64, 0.88],
  refl: [0.73, 0.93],
  ring0: 0.52,
  touch1: 1.4,
  dent1: [1.4, 1.48],
  move1: [1.5, 2.6],
  release1: 2.9,
  snap: [3.4, 3.75],
  relax1End: 4.3,
  touch2: 5.6,
  dent2: [5.6, 5.68],
  move2: [5.68, 6.4],
  limit: [6.4, 6.62],
  release2: 6.45,
  pause: 6.6,
  glyphBack: [6.6, 6.82],
  pillBack: [6.64, 6.88],
  lineBack: [6.68, 6.92],
  titleBack: [6.72, 6.96],
  lampBack: [6.6, 7.0],
  rest: 7.25,
} as const;

// ---- constants of the object ----
/** The display's bloom at rest (paused): 0.65 of full, so the first frame already holds the lamp. */
export const BLOOM_REST = 0.65;
export const TRACK = 200; // seconds in the demo track (3:20)
export const TURN_SECONDS = 200; // track seconds per full turn of the finger round the dome
export const BPM = 118;
export const BEAT = 60 / BPM; // 0.5085 s
export const ACCENT_EVERY = 4;
/** A turn of the finger moves the weave 0.22 as far: a 128-strand weave turning 1:1 would strobe at 60 fps. */
export const GAIN = 0.22;
export const INNER = 0.6; // the inner layer turns at 0.6 of the outer one
export const SHEAR_FULL = 0.25; // rad of difference between the layers at full shear
export const RING = { r0: 104, rim: 165, life: 0.55, weak: 2.2, strong: 4 } as const;
export const FINGER_R = 135; // design pt from the dome's centre: the finger rides the middle of the annulus
export const ANNULUS = { inner: 104, outer: 170 } as const;
export const DENT = 5; // pt
export const PULL = 0.8; // pt: strands left out of line after a turn

/** Gesture mapping, design pt and seconds. */
export const GESTURE = {
  slop: 6, // pt before a touch on the mesh becomes a turn
  coastDecay: 0.996, // per ms
  coastMaxTurns: 1.5,
  coastStop: 0.05, // rad/s: below it the coast ends and the position snaps
  rubber: 0.2, // past the ends of the track the weave follows at 0.2
  resumeAfter: 2, // s after the last touch the loop starts again
  tickEvery: 5, // s of track crossed per selection tick
  tickGap: 0.1, // s between ticks while turning
  swipe: 56, // pt of swipe on the title that changes track
  swipeVelocity: 520, // pt/s
  titleRubber: 0.3,
  coverFollow: 0.25,
  slideSpan: 140,
  vTail: 0.08, // s of the finger's speed that the release carries
} as const;

// DESIGN-SYSTEM §8 presets (mass 1)
export const snap = { damping: 30.5, stiffness: 322.3, mass: 1 } as const;
export const glide = { damping: 22.1, stiffness: 157.9, mass: 1 } as const;
export const SPRING = {
  snapSecond: { damping: 20, stiffness: 260, mass: 1 }, // the position snaps to the second
  relax: { damping: 14, stiffness: 60, mass: 1.4 }, // the inner layer catches up: slow, under-damped, cloth easing
  press: { response: 0.35, zeta: 0.85 },
  turn: { damping: 18, stiffness: 240, mass: 1 }, // the time grows under the finger
} as const;

// ---- helpers (declared above their callers: worklets) ----

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const k = clamp01((x - a) / (b - a));
  return k * k * (3 - 2 * k);
}

/** 0 before a, 1 after b, linear in between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

/** The y of a CSS cubic-bezier(x1, y1, x2, y2) at x (Newton, then bisection). */
export function bezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let s = x;
  for (let i = 0; i < 6; i++) {
    const e = ((ax * s + bx) * s + cx) * s - x;
    const d = (3 * ax * s + 2 * bx) * s + cx;
    if (Math.abs(e) < 1e-5 || Math.abs(d) < 1e-6) break;
    s -= e / d;
  }
  s = clamp01(s);
  return ((ay * s + by) * s + cy) * s;
}

export function easeOut(x: number) {
  'worklet';
  return bezier(x, 0.2, 0.8, 0.2, 1);
}

export function easeInOut(x: number) {
  'worklet';
  return bezier(x, 0.65, 0, 0.35, 1);
}

/** 0 → 1 step response of a spring (response s, damping ratio). */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

/** The step response of a raw spring (damping c, stiffness k, mass m). */
export function springRaw(t: number, c: number, k: number, m: number) {
  'worklet';
  const w = Math.sqrt(k / m);
  const zeta = c / (2 * Math.sqrt(k * m));
  return springAt(t, (2 * Math.PI) / w, zeta);
}

/** A rubber band: past the ends the value follows at `k` of the finger. */
export function rubber(x: number, lo: number, hi: number, k: number) {
  'worklet';
  if (x < lo) return lo + (x - lo) * k;
  if (x > hi) return hi + (x - hi) * k;
  return x;
}

export function wrapPi(a: number) {
  'worklet';
  let x = a;
  while (x > Math.PI) x -= 2 * Math.PI;
  while (x < -Math.PI) x += 2 * Math.PI;
  return x;
}

/** "m:ss" of a position in seconds. */
export function timeLabel(sec: number) {
  'worklet';
  const s = Math.max(0, Math.floor(sec + 1e-6));
  const m = Math.floor(s / 60);
  const r = s - m * 60;
  return `${m}:${r < 10 ? '0' : ''}${r}`;
}

// ---- the pulses ----

/** One pulse launched at `t0`: where it is at `now`, and how high. Radius 104 → 165 pt in 0.55 s, gone at the rim. */
export function ringAt(now: number, t0: number, amp: number) {
  'worklet';
  const age = now - t0;
  const u = age / RING.life;
  if (u < 0 || u >= 1) return { r: 0, a: 0 };
  const env = smooth(0, 0.1, u) * (1 - smooth(0.72, 1, u));
  return { r: RING.r0 + (RING.rim - RING.r0) * u, a: amp * env };
}

/** The accented beat's arrival at the rim: 0 → 1 → 0 over about 0.3 s (the page's lamp breathes, the headline opens). */
export function pulseAt(now: number, t0: number) {
  'worklet';
  const u = now - (t0 + RING.life);
  if (u < 0 || u > 0.6) return 0;
  return (1 - Math.exp(-u / 0.03)) * Math.exp(-u / 0.12) * 1.6;
}

// ---- the Look ----

export type Look = {
  /** track position, seconds */
  pos: number;
  /** 0 paused … 1 playing: the play → pause path */
  glyph: number;
  /** the centre control's press scale, 1 … 0.9 */
  press: number;
  bloom: number;
  bloomR: number;
  /** the mesh's red rim light: strength 0 … 1 and reach in pt */
  lamp: number;
  reach: number;
  /** the three lines of the text block, 0 resting copy … 1 playing copy */
  title: number;
  line: number;
  pill: number;
  /** the bezel's red reflection */
  refl: number;
  /** the finger is on the mesh, 0 … 1 */
  turn: number;
  psiO: number;
  psiI: number;
  omO: number;
  omI: number;
  shear: number;
  finger: number;
  dentX: number;
  dentY: number;
  dent: number;
  r0: number;
  a0: number;
  r1: number;
  a1: number;
  r2: number;
  a2: number;
  r3: number;
  a3: number;
  /** the lamp's wash on the page, 0 … 0.08 */
  wash: number;
  /** an accented beat has reached the rim */
  pulse: number;
  pull: number;
  pullAt: number;
  /** the arc's handle dot 0 … 1 */
  handle: number;
  /** the arc head's limit pulse 0 … 1 */
  head: number;
};

export const REST_LOOK: Look = {
  pos: 0,
  glyph: 0,
  press: 1,
  bloom: BLOOM_REST,
  bloomR: 60,
  lamp: 0.12,
  reach: 18,
  title: 0,
  line: 0,
  pill: 0,
  refl: 0,
  turn: 0,
  psiO: 0,
  psiI: 0,
  omO: 0,
  omI: 0,
  shear: 0,
  finger: 0,
  dentX: 0,
  dentY: 0,
  dent: 0,
  r0: 0,
  a0: 0,
  r1: 0,
  a1: 0,
  r2: 0,
  a2: 0,
  r3: 0,
  a3: 0,
  wash: 0,
  pulse: 0,
  pull: 0,
  pullAt: 0,
  handle: 0,
  head: 0,
};

type Launch = { t: number; amp: number };

/** The beat grid of the demo: a pulse every beat while it plays and nobody touches the dome (no pulses under the finger). */
function buildLaunches(): Launch[] {
  const out: Launch[] = [];
  for (let k = 0; k < 20; k++) {
    const t = T.ring0 + k * BEAT;
    if (t >= T.pause) break;
    const touching = (t >= T.touch1 && t < T.release1 + 0.25) || (t >= T.touch2 && t < T.release2 + 0.1);
    if (touching) continue;
    out.push({ t, amp: k % ACCENT_EVERY === 0 ? RING.strong : RING.weak });
  }
  return out;
}

export const LAUNCHES = buildLaunches();

/** Fills the four ring slots of a Look from a list of launches (the newest four alive at `now`). */
export function fillRings(look: Look, now: number, launches: Launch[]) {
  'worklet';
  let n = 0;
  let pulse = 0;
  for (let i = launches.length - 1; i >= 0 && n < 4; i--) {
    const l = launches[i];
    if (l.amp >= RING.strong - 1e-6) pulse = Math.max(pulse, pulseAt(now, l.t));
    const g = ringAt(now, l.t, l.amp);
    if (g.a <= 0) continue;
    if (n === 0) {
      look.r0 = g.r;
      look.a0 = g.a;
    } else if (n === 1) {
      look.r1 = g.r;
      look.a1 = g.a;
    } else if (n === 2) {
      look.r2 = g.r;
      look.a2 = g.a;
    } else {
      look.r3 = g.r;
      look.a3 = g.a;
    }
    n++;
  }
  look.pulse = pulse;
}

// finger path of the demo: forward from 3 o'clock to ~9 o'clock, then back faster
const FWD_TRAVEL = ((TRACK / 2 - 1) / TURN_SECONDS) * 2 * Math.PI; // 99 s of track as an angle: 3.110 rad
const COAST_ANGLE = (12 * Math.PI) / 180;
const COAST_TAU = 0.18;
const POS_TOUCH1 = T.touch1 - T.tap; // 1.0 s: it has played for a second
const POS_SNAP_FROM = TRACK / 2 + (COAST_ANGLE / (2 * Math.PI)) * TURN_SECONDS * (1 - Math.exp(-(T.snap[0] - T.release1) / COAST_TAU));
const POS_SNAPPED = Math.round(POS_SNAP_FROM);
const POS_TOUCH2 = POS_SNAPPED + (T.touch2 - T.snap[1]);
const PSI_TOUCH2 = GAIN * (FWD_TRAVEL + COAST_ANGLE);
const REV_START = Math.PI + 0.2;
const REV_TRAVEL = (POS_TOUCH2 / TURN_SECONDS) * 2 * Math.PI;

/** Position of the demo's track at loop time t. */
function demoPos(t: number) {
  'worklet';
  if (t < T.tap) return 0;
  if (t < T.move1[0]) return Math.min(t - T.tap, POS_TOUCH1);
  if (t < T.release1) {
    const e = bezier(seg(t, T.move1[0], T.move1[1]), 0.45, 0, 0.25, 1);
    return POS_TOUCH1 + (TRACK / 2 - POS_TOUCH1) * e;
  }
  if (t < T.snap[0]) return TRACK / 2 + (COAST_ANGLE / (2 * Math.PI)) * TURN_SECONDS * (1 - Math.exp(-(t - T.release1) / COAST_TAU));
  if (t < T.touch2) {
    const sp = springAt(t - T.snap[0], 0.3, 1);
    const base = POS_SNAP_FROM + (POS_SNAPPED - POS_SNAP_FROM) * sp;
    return base + Math.max(0, t - T.snap[1]);
  }
  if (t < T.move2[0]) return POS_TOUCH2;
  const e = bezier(seg(t, T.move2[0], T.move2[1]), 0.4, 0, 0.2, 1);
  return POS_TOUCH2 * (1 - e);
}

/** The outer layer's angle, the layers' difference and the finger's bearing. */
function demoTurn(t: number) {
  'worklet';
  let psiO = 0;
  let shearD = 0;
  let finger = 0;
  if (t < T.touch1) {
    return { psiO, shearD, finger };
  }
  if (t < T.release1) {
    const e = bezier(seg(t, T.move1[0], T.move1[1]), 0.45, 0, 0.25, 1);
    psiO = GAIN * FWD_TRAVEL * e;
    shearD = (1 - INNER) * psiO;
    finger = FWD_TRAVEL * e;
    return { psiO, shearD, finger };
  }
  const since = t - T.release1;
  const coast = COAST_ANGLE * (1 - Math.exp(-since / COAST_TAU));
  psiO = GAIN * (FWD_TRAVEL + coast);
  const d0 = (1 - INNER) * GAIN * FWD_TRAVEL;
  if (t < T.touch2) {
    // the inner layer catches up (cloth easing), and is forced home by 4.3
    shearD =
      d0 *
      (1 - springRaw(since, SPRING.relax.damping, SPRING.relax.stiffness, SPRING.relax.mass)) *
      (1 - smooth(T.release1 + 1.1, T.relax1End, t));
    return { psiO, shearD, finger: FWD_TRAVEL };
  }
  if (t < T.move2[0]) return { psiO: PSI_TOUCH2, shearD: 0, finger: REV_START };
  const e = bezier(seg(t, T.move2[0], T.move2[1]), 0.4, 0, 0.2, 1);
  psiO = PSI_TOUCH2 * (1 - e);
  const dEnd = -(1 - INNER) * PSI_TOUCH2;
  if (t < T.release2) {
    shearD = dEnd * e;
    finger = REV_START - REV_TRAVEL * e;
    return { psiO, shearD, finger };
  }
  const s2 = t - T.release2;
  shearD = dEnd * (1 - springAt(s2, 0.55, 0.8)) * (1 - smooth(T.release2 + 0.5, T.rest, t));
  return { psiO: 0, shearD, finger: REV_START - REV_TRAVEL };
}

/** Every number of the demo's frame at loop time t (seconds, any value: it wraps). */
export function lookAt(tIn: number): Look {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const k = { ...REST_LOOK };

  // play and pause: the choreography of the concept, the pause in reverse order with the same steps
  const up = (a: number, b: number) => easeInOut(seg(t, a, b));
  const playing = t >= T.tap && t < T.pause;
  const rise = (w: readonly number[], fall: readonly number[]) => (playing ? up(w[0], w[1]) : t >= T.pause ? 1 - up(fall[0], fall[1]) : 0);
  k.glyph = rise(T.glyph, T.glyphBack);
  const lampUp = playing ? easeOut(seg(t, T.rim[0], T.rim[1])) : t >= T.pause ? 1 - easeOut(seg(t, T.lampBack[0], T.lampBack[1] - 0.1)) : 0;
  k.lamp = mix(0.12, 1, lampUp);
  k.reach = mix(18, 46, lampUp);
  const bloomUp = playing ? easeOut(seg(t, T.bloom[0], T.bloom[1])) : t >= T.pause ? 1 - easeOut(seg(t, T.lampBack[0], T.lampBack[1])) : 0;
  k.bloom = mix(BLOOM_REST, 1, bloomUp);
  k.bloomR = mix(ref.bloom.r0, ref.bloom.r1, bloomUp);
  k.title = rise(T.title, T.titleBack);
  k.line = rise(T.line, T.lineBack);
  k.pill = rise(T.pill, T.pillBack);
  k.refl = playing ? easeOut(seg(t, T.refl[0], T.refl[1])) : t >= T.pause ? 1 - easeOut(seg(t, T.glyphBack[0], T.glyphBack[0] + 0.2)) : 0;
  // the centre control's press: down 80 ms, back on a spring (at the tap and at the pause)
  const pressAt = (t0: number) => {
    const d = seg(t, t0, t0 + 0.08);
    const u = t - (t0 + 0.08);
    return u < 0 ? 1 - 0.1 * easeOut(d) : 1 - 0.1 * (1 - springAt(u, SPRING.press.response, SPRING.press.zeta));
  };
  k.press = t >= T.pause ? pressAt(T.pause) : t >= T.tap ? pressAt(T.tap) : 1;
  if (t >= T.pause + 0.5) k.press = 1;
  k.handle = playing ? easeOut(seg(t, 0.9, 1.15)) : t >= T.pause ? 1 - easeOut(seg(t, T.glyphBack[0], T.glyphBack[1] + 0.3)) : 0;

  // position, layers, finger
  k.pos = demoPos(t);
  const tr = demoTurn(t);
  k.psiO = tr.psiO;
  k.psiI = tr.psiO - tr.shearD;
  k.shear = Math.max(-1, Math.min(1, tr.shearD / SHEAR_FULL));
  k.finger = tr.finger;
  // angular speeds for the weave's motion blur (rad of turn per frame at 60 fps), by a finite difference
  const dt = 1 / 120;
  const ahead = demoTurn(t + dt);
  const behind = demoTurn(t - dt);
  k.omO = Math.abs(ahead.psiO - behind.psiO) / (2 * dt) / 60;
  k.omI = Math.abs(ahead.psiO - ahead.shearD - (behind.psiO - behind.shearD)) / (2 * dt) / 60;

  // the finger's dent and the engagement of the turn
  const on1 = t >= T.touch1 && t < T.release1;
  const on2 = t >= T.touch2 && t < T.release2;
  const dentIn = (a: readonly number[]) => easeOut(seg(t, a[0], a[1]));
  let dent = 0;
  let turn = 0;
  if (t >= T.touch1 && t < T.touch2) {
    dent = on1 ? dentIn(T.dent1) : 1 - easeInOut(seg(t, T.release1, T.release1 + 0.3));
    turn = on1 ? springAt(t - T.touch1, 0.35, 0.85) : 1 - easeInOut(seg(t, T.release1, T.release1 + 0.4));
  } else if (t >= T.touch2) {
    dent = on2 ? dentIn(T.dent2) : 1 - easeInOut(seg(t, T.release2, T.release2 + 0.3));
    turn = on2 ? springAt(t - T.touch2, 0.35, 0.85) : 1 - easeInOut(seg(t, T.release2, T.release2 + 0.4));
  }
  k.dent = DENT * Math.max(0, dent);
  k.turn = Math.max(0, Math.min(1.05, turn));
  k.dentX = FINGER_R * Math.cos(tr.finger);
  k.dentY = FINGER_R * Math.sin(tr.finger);
  // strands left out of line where the finger last was, relaxing after the release
  if (t >= T.release1 && t < T.touch2) {
    k.pull = PULL * (1 - smooth(T.release1, T.release1 + 1.2, t));
    k.pullAt = FWD_TRAVEL;
  } else if (t >= T.release2) {
    k.pull = PULL * (1 - smooth(T.release2, T.rest - 0.2, t));
    k.pullAt = REV_START - REV_TRAVEL;
  }

  // the pulses
  if (t >= T.rest) {
    // nothing of the finger is left: the same numbers as the first frame
    k.finger = 0;
    k.pullAt = 0;
    k.dentX = FINGER_R;
    k.dentY = 0;
  }
  fillRings(k, t, LAUNCHES);
  // the lamp's wash on the page (Pro 2): 5 % while it plays, breathing to 8 % on the accented beats
  const playE = playing ? up(T.tap, T.tap + 0.45) : t >= T.pause ? 1 - up(T.pause, T.pause + 0.5) : 0;
  k.wash = playE * (ref.wash.base + (ref.wash.beat - ref.wash.base) * Math.min(k.pulse, 1));
  return k;
}

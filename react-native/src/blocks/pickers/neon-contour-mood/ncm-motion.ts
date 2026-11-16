// Every motion value of the block. Pure scalar maths, no React Native imports, so the offline harness runs it as it ships.
// Everything marked 'worklet' runs on the UI runtime: no closures, no .map callbacks, nothing allocated per frame.
export const LOOP = 8;
export const REST = 1;
/** Loop phases (s). The finger drags to between two moods, parks there, flicks to the last mood, lets go and returns. */
export const T = { restEnd: 0.6, dragEnd: 1.85, holdEnd: 2.85, flickEnd: 3.85, radiateEnd: 4.7, returnEnd: 6.9 } as const;
/** Path of the 7-mood reference loop, in mood units; other mood counts scale it by (n - 1) / 6. */
export const PATH = { rest: 1, hold: 2.7, end: 6 } as const;
/**
 * Nested moods you can hold: while a finger rests slower than `slow` stops/s, ring k reads m - dir * k * gap.
 * `on` / `off` are where the loop's drag and flick cross that speed (see check-motion.cjs).
 */
export const HOLD = { gap: 0.22, rise: 0.18, ringDelay: 0.04, slow: 0.4, on: 1.72, off: 2.92 } as const;
export const SPRING = {
  snap: { damping: 18, stiffness: 220, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  press: { damping: 30.5, stiffness: 322.3, mass: 1 },
  tag: { damping: 20, stiffness: 260, mass: 1 },
  squeeze: { damping: 14, stiffness: 200, mass: 1 },
} as const;
function resp(stiffness: number, damping: number) {
  const w = Math.sqrt(stiffness);
  return { response: (2 * Math.PI) / w, zeta: damping / (2 * w) };
}
/** The same springs as closed-form (response in s, damping ratio) so autoplay stays a pure function of time. */
export const RESP = {
  glide: resp(SPRING.glide.stiffness, SPRING.glide.damping),
  tag: resp(SPRING.tag.stiffness, SPRING.tag.damping),
  squeeze: resp(SPRING.squeeze.stiffness, SPRING.squeeze.damping),
} as const;
export const GESTURE = {
  resumeAfter: 2,
  returnDuration: 0.6,
  projection: 0.12,
  maxFlick: 2,
  rubber: 0.35,
  echoMs: 96,
  wakeTau: 0.18,
  speedTau: 0.05,
} as const;
/** Hysteresis: label i becomes i + 1 above i + 0.62 and goes back below i + 0.38. */
export const HYST = 0.62;
/** One tube going dark and another being struck. All in seconds. */
export const STRIKE = {
  inStep: 0.026,
  inRise: 0.03,
  dipAt: 0.05,
  dipDur: 0.024,
  dipTo: 0.45,
} as const;
export const GLOW = { blurFrom: 2.4, blurTo: 6, alpha: 0.35, grow: 0.16 } as const;
/**
 * The word is never shown in part. The old word goes out whole: its tubes dim while their glow swells and spreads (it
 * de-focuses) and it shrinks a little. The new word arrives whole as unlit glass tubes (a dim tint of its colour), settling
 * from slightly large, and its letters are then struck left to right on top of that.
 */
export const WORD = {
  outDur: 0.17,
  outBlur: 14,
  outScale: 0.94,
  inStart: 0.08,
  inDur: 0.2,
  inScale: 1.05,
  tube: 0.22,
  strike: 0.2,
} as const;
/** The tag fades and shrinks out, the capsule springs to the new width, the new tag fades in growing with it; it moves 2 pt at most, so it never crosses the capsule's stroke. */
export const TAG = { outStart: 0.02, outDur: 0.09, slide: 2, inStart: 0.15, inDur: 0.16, rise: 2, springStart: 0.08, zoom: 0.06 } as const;
export const REDUCED_DIP = 0.12;
/** Seconds after a switch when every one-shot of it has finished (the longest is the capsule spring). */
export const LABEL_SETTLE = 1.2;

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function smooth(t: number) {
  'worklet';
  const x = t < 0 ? 0 : t > 1 ? 1 : t;
  return x * x * x * (x * (x * 6 - 15) + 10);
}
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(Math.max(1e-9, 1 - zeta * zeta));
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}
function wrap(time: number) {
  'worklet';
  return ((time % LOOP) + LOOP) % LOOP;
}
/** Mood of the loop's finger at loop time `time`, for a picker with `scale = (n - 1) / 6`. */
export function moodAt(time: number, scale = 1) {
  'worklet';
  const t = wrap(time);
  let m: number = PATH.rest;
  if (t >= T.restEnd && t < T.returnEnd) {
    if (t < T.dragEnd) m = mix(PATH.rest, PATH.hold, smooth((t - T.restEnd) / (T.dragEnd - T.restEnd)));
    else if (t < T.holdEnd) m = PATH.hold;
    else if (t < T.flickEnd) m = mix(PATH.hold, PATH.end, smooth((t - T.holdEnd) / (T.flickEnd - T.holdEnd)));
    else if (t < T.radiateEnd) m = PATH.end;
    else m = mix(PATH.end, PATH.rest, smooth((t - T.radiateEnd) / (T.returnEnd - T.radiateEnd)));
  }
  return m * scale;
}
/** hold: 0 → 1 over `rise`, then back to 0 on the glide spring. `from`/`target` are the state it moves between. */
export function holdProfile(from: number, target: number, tau: number) {
  'worklet';
  if (tau <= 0) return from;
  if (target > from) return from + (target - from) * smooth(tau / HOLD.rise);
  const v = from + (target - from) * springAt(tau, RESP.glide.response, RESP.glide.zeta);
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
/** The loop's hold factor at loop time t (the finger is parked from `on` to `off`). */
export function holdKAt(t: number) {
  'worklet';
  if (t < HOLD.on) return 0;
  if (t < HOLD.off) return smooth((t - HOLD.on) / HOLD.rise);
  return holdProfile(1, 0, t - HOLD.off);
}

export type Look = { mood: number; echoes: number[]; wake: number; press: number; breath: number; radiate: number; time: number };
export function newLook(rest = REST): Look {
  return { mood: rest, echoes: [rest, rest, rest, rest, rest, rest, rest], wake: 0, press: 0, breath: 0, radiate: 0, time: 0 };
}
/** Writes the autoplay state at loop time `time` into `out`: a pure function of time, nothing allocated. */
export function fillTimeline(out: Look, time: number, echoMs: number, n: number) {
  'worklet';
  const t = wrap(time);
  const scale = (n - 1) / 6;
  const last = n - 1;
  const core = moodAt(t, scale);
  const lo = Math.max(0, core - 1.6);
  const hi = Math.min(last, core + 1.6);
  for (let k = 0; k < 7; k++) {
    const e = moodAt(Math.max(0, t - (k * echoMs) / 1000), scale);
    const echo = e < lo ? lo : e > hi ? hi : e;
    const nested = clamp(core - k * HOLD.gap, 0, last);
    out.echoes[k] = echo + (nested - echo) * holdKAt(t - k * HOLD.ringDelay);
  }
  const speed = Math.abs(core - moodAt(Math.max(0, t - 1 / 120), scale)) * 120;
  const active = smooth((t - T.restEnd) / 0.12) * (1 - smooth((t - (T.returnEnd - 0.2)) / 0.2));
  out.mood = out.echoes[0];
  out.wake = clamp(speed / 10);
  out.press = active;
  // two slow breaths per loop close exactly; the hold gets live motion without a finger
  out.breath = Math.sin((4 * Math.PI * t) / LOOP) * 1.5 * active;
  out.radiate = smooth((t - T.flickEnd) / 0.2) * (1 - smooth((t - (T.radiateEnd - 0.2)) / 0.2));
  out.time = t;
}
/** Allocating twin of fillTimeline for tests and the offline harness. */
export function timeline(time: number, echoMs: number = GESTURE.echoMs, n = 7) {
  const look = newLook();
  fillTimeline(look, time, echoMs, n);
  return look;
}

export function rubberMood(v: number, last = 6) {
  'worklet';
  if (v < 0) return (-GESTURE.rubber * -v) / (1 - v);
  if (v > last) return last + (GESTURE.rubber * (v - last)) / (1 + v - last);
  return v;
}
export function projectedStop(value: number, velocity: number, last = 6) {
  'worklet';
  const nearest = Math.round(clamp(value, 0, last));
  const target = clamp(
    value + velocity * GESTURE.projection,
    Math.max(0, nearest - GESTURE.maxFlick),
    Math.min(last, nearest + GESTURE.maxFlick),
  );
  return Math.round(target);
}
/** 1 pt of spacing per 6 pt of finger travel, banded softly at +-12 %. */
export function squeezeFor(dy: number, limit: number) {
  'worklet';
  return limit * Math.tanh(dy / 6 / limit);
}
/** Haptic weight by mood: the ends are Medium, the middle Soft, the rest Light. */
export function hapticWeight(index: number, n: number) {
  'worklet';
  if (index === 0 || index === n - 1) return 2;
  if (index === (n - 1) / 2) return 0;
  return 1;
}

// ---- Labels: one integer, one word, one tag --------------------------------------------------------------------

export const MAXL = 10;
/** Layout of the label frame `lab`: scalars, then three numbers (level, glow alpha, glow blur) per letter, in then out. */
export const LAB = {
  from: 0,
  to: 1,
  strike: 2,
  width: 3,
  tagIn: 4,
  tagInY: 5,
  tagOut: 6,
  tagOutY: 7,
  inScale: 8,
  outScale: 9,
  inBase: 10,
  outBase: 10 + 3 * MAXL,
  size: 10 + 6 * MAXL,
} as const;
export const MAXEV = 10;
export function newLab() {
  return new Array<number>(LAB.size).fill(0);
}
export function newEvents() {
  return new Array<number>(MAXEV * 3).fill(0);
}
export function settleLabel(idx: number, m: number, last: number) {
  'worklet';
  let i = idx;
  while (m > i + HYST && i < last) i++;
  while (m < i - HYST && i > 0) i--;
  return i;
}
/** Switch times of the autoplay loop as flat (t, from, to) triples, found by running the hysteresis over the loop. */
export function buildCrossings(n: number) {
  const scale = (n - 1) / 6;
  const last = n - 1;
  let idx = Math.round(PATH.rest * scale);
  const out: number[] = [];
  const steps = LOOP * 2000;
  for (let pass = 0; pass < 2; pass++) {
    for (let i = 0; i < steps; i++) {
      const t = i / 2000;
      const prev = idx;
      idx = settleLabel(idx, moodAt(t, scale), last);
      if (pass === 1 && idx !== prev) out.push(t, prev, idx);
    }
  }
  return out;
}
export const CROSSINGS = buildCrossings(7);

/** A tube strike for one letter; u is the time since that letter's strike began. */
export function strikeIn(u: number) {
  'worklet';
  if (u <= 0) return 0;
  if (u < STRIKE.inRise) return smooth(u / STRIKE.inRise);
  const d = (u - STRIKE.dipAt) / STRIKE.dipDur;
  if (d <= 0 || d >= 1) return 1;
  const edge = d < 0.25 ? d / 0.25 : d > 0.75 ? (1 - d) / 0.25 : 1;
  return 1 - (1 - STRIKE.dipTo) * edge;
}
/** Level of incoming letter j, d s after its switch: an unlit tube once the word has arrived, then struck. */
function inLevel(d: number, j: number) {
  'worklet';
  const tube = WORD.tube * smooth((d - WORD.inStart) / WORD.inDur);
  return tube + (1 - tube) * strikeIn(d - WORD.strike - j * STRIKE.inStep);
}
function writeLetters(out: number[], base: number, idx: number, lens: number[]) {
  'worklet';
  const len = lens[idx];
  for (let j = 0; j < MAXL; j++) {
    const o = base + 3 * j;
    out[o] = j < len ? 1 : 0;
    out[o + 1] = j < len ? GLOW.alpha : 0;
    out[o + 2] = GLOW.blurTo;
  }
}
/** Everything lit and still: the state before the first switch and the state a second after the last. */
export function labelSettled(out: number[], idx: number, widths: number[], lens: number[]) {
  'worklet';
  out[LAB.from] = idx;
  out[LAB.to] = idx;
  out[LAB.strike] = 99;
  out[LAB.width] = widths[idx];
  out[LAB.tagIn] = 1;
  out[LAB.tagInY] = 0;
  out[LAB.tagOut] = 0;
  out[LAB.tagOutY] = 0;
  out[LAB.inScale] = 1;
  out[LAB.outScale] = 1;
  writeLetters(out, LAB.inBase, idx, lens);
  for (let j = 0; j < MAXL; j++) {
    const o = LAB.outBase + 3 * j;
    out[o] = 0;
    out[o + 1] = 0;
    out[o + 2] = GLOW.blurTo;
  }
}
/**
 * The label frame at time `now` from the switch list `ev` (flat t, from, to; `count` of them, oldest first). Each switch runs
 * a one-shot: the old letters go out right to left, the new ones are struck left to right, the tag swaps in two steps and the
 * capsule springs to the new width. A switch during a one-shot restarts it from what is lit at that moment, so a fast scrub
 * never stacks letters. `snap` is scratch for MAXL numbers.
 */
export function computeLabel(
  out: number[],
  ev: number[],
  count: number,
  now: number,
  idle: number,
  widths: number[],
  lens: number[],
  snap: number[],
) {
  'worklet';
  if (count <= 0) {
    labelSettled(out, idle, widths, lens);
    return;
  }
  let wFrom = widths[ev[1]];
  let tagSnap = 1;
  const len0 = lens[ev[1]];
  for (let j = 0; j < MAXL; j++) snap[j] = j < len0 ? 1 : 0;
  for (let e = 0; e < count; e++) {
    const o = e * 3;
    const te = ev[o];
    const fromW = ev[o + 1];
    const toW = ev[o + 2];
    const isLast = e === count - 1;
    const d = (isLast ? now : ev[o + 3]) - te;
    const lenTo = lens[toW];
    const widthK = springAt(d - TAG.springStart, RESP.tag.response, RESP.tag.zeta);
    const tagIn = smooth((d - TAG.inStart) / TAG.inDur);
    if (!isLast) {
      wFrom = wFrom + (widths[toW] - wFrom) * widthK;
      tagSnap = tagIn;
      for (let j = 0; j < MAXL; j++) snap[j] = j < lenTo ? inLevel(d, j) : 0;
      continue;
    }
    if (d > LABEL_SETTLE) {
      labelSettled(out, toW, widths, lens);
      return;
    }
    out[LAB.from] = fromW;
    out[LAB.to] = toW;
    out[LAB.strike] = d;
    out[LAB.width] = wFrom + (widths[toW] - wFrom) * widthK;
    const tagOut = smooth((d - TAG.outStart) / TAG.outDur);
    out[LAB.tagIn] = tagIn;
    out[LAB.tagInY] = TAG.rise * (1 - tagIn);
    out[LAB.tagOut] = tagSnap * (1 - tagOut);
    out[LAB.tagOutY] = -TAG.slide * tagOut;
    const arrive = smooth((d - WORD.inStart) / WORD.inDur);
    const leave = smooth(d / WORD.outDur);
    out[LAB.inScale] = mix(WORD.inScale, 1, arrive);
    out[LAB.outScale] = mix(1, WORD.outScale, leave);
    const lenFrom = lens[fromW];
    for (let j = 0; j < MAXL; j++) {
      const u = d - WORD.strike - j * STRIKE.inStep;
      const lit = j < lenTo ? inLevel(d, j) : 0;
      const struck = j < lenTo ? strikeIn(u) : 0;
      const grow = smooth(u / GLOW.grow);
      const a = LAB.inBase + 3 * j;
      out[a] = lit;
      out[a + 1] = GLOW.alpha * struck * grow;
      out[a + 2] = mix(GLOW.blurFrom, GLOW.blurTo, grow);
      // All letters together: the crisp tube fades first, the glow lingers and spreads, so the word de-focuses as it goes.
      const was = j < lenFrom ? snap[j] : 0;
      const b = LAB.outBase + 3 * j;
      out[b] = was * (1 - leave) * (1 - leave);
      out[b + 1] = GLOW.alpha * was * (1 - leave) * (1 + leave);
      out[b + 2] = mix(GLOW.blurTo, WORD.outBlur, leave);
    }
  }
}
/** Reads the autoplay switch table: the label frame at loop time `t`. */
export function labelFromTable(out: number[], table: number[], t: number, widths: number[], lens: number[], snap: number[], idle: number) {
  'worklet';
  const total = table.length / 3;
  let count = 0;
  while (count < total && table[count * 3] <= t) count++;
  computeLabel(out, table, count, t, total > 0 ? table[1] : idle, widths, lens, snap);
}
/** Appends a switch to the live list, dropping switches whose successor is more than 2 s old (everything they started has finished). */
export function pushEvent(ev: number[], count: number, t: number, from: number, to: number) {
  'worklet';
  let c = count;
  while (c > 1 && t - ev[3] > 2) {
    for (let i = 0; i < (c - 1) * 3; i++) ev[i] = ev[i + 3];
    c--;
  }
  if (c >= MAXEV) {
    for (let i = 0; i < (c - 1) * 3; i++) ev[i] = ev[i + 3];
    c--;
  }
  ev[c * 3] = t;
  ev[c * 3 + 1] = from;
  ev[c * 3 + 2] = to;
  return c + 1;
}
/** Letter value from the label frame: `field` 0 level, 1 glow alpha, 2 glow blur. */
export function labLetter(lab: number[], word: number, j: number, field: number, fallback: number) {
  'worklet';
  const jj = j < MAXL ? j : MAXL - 1;
  if (lab[LAB.to] === word) return lab[LAB.inBase + 3 * jj + field];
  if (lab[LAB.from] === word) return lab[LAB.outBase + 3 * jj + field];
  return fallback;
}
/** Tag value from the label frame: level (0), vertical offset (1) or scale (2) of tag `word`. */
export function labTag(lab: number[], word: number, field: number) {
  'worklet';
  if (lab[LAB.to] === word) return field === 0 ? lab[LAB.tagIn] : field === 1 ? lab[LAB.tagInY] : 1 - TAG.zoom * (1 - lab[LAB.tagIn]);
  if (lab[LAB.from] === word) return field === 0 ? lab[LAB.tagOut] : field === 1 ? lab[LAB.tagOutY] : 1 - TAG.zoom * (1 - lab[LAB.tagOut]);
  return field === 2 ? 1 : 0;
}
/** Scale of word `word` from the label frame (1 when it is neither arriving nor leaving). */
export function labWord(lab: number[], word: number) {
  'worklet';
  if (lab[LAB.to] === word) return lab[LAB.inScale];
  if (lab[LAB.from] === word) return lab[LAB.outScale];
  return 1;
}

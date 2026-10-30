// Cashback Pick Five — everything is a list of events (pick, drop, scroll) replayed up to a time.
// The loop is a fixed list; touches append to a live one on the clock. Replaying gives the picks in
// order, every icon's flight between its row and its slot in the button, and the rejections.

import { CATS, MAX_PICKS } from './cpf-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const FLY = 0.5; // an icon's flight
export const NEVER = -100;
export const MAX_SCROLL = 420;

export type Ev = { t: number; k: 0 | 1 | 2; row: number; v: number }; // 0 pick, 1 drop, 2 scroll to v (px)

/** The loop: five picks (one row needs the list scrolled), a sixth refused, the banner sparkles, all dropped. */
export const LOOP_EVENTS: Ev[] = [
  { t: 0.6, k: 0, row: 0, v: 0 },
  { t: 1.25, k: 0, row: 1, v: 0 },
  { t: 1.85, k: 0, row: 3, v: 0 },
  { t: 2.45, k: 0, row: 2, v: 0 },
  { t: 3.05, k: 0, row: 4, v: 0 },
  { t: 3.55, k: 2, row: -1, v: 240 },
  { t: 4.45, k: 0, row: 6, v: 0 }, // refused: five already
  { t: 5.45, k: 2, row: -1, v: 0 },
  { t: 6.1, k: 1, row: 4, v: 0 },
  { t: 6.35, k: 1, row: 2, v: 0 },
  { t: 6.6, k: 1, row: 3, v: 0 },
  { t: 6.85, k: 1, row: 1, v: 0 },
  { t: 7.1, k: 1, row: 0, v: 0 },
];
export const SPARKLE_AT = 5.0;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}

export type Flight = { row: number; from: number; to: number; at: number; dir: 1 | -1 }; // dir 1 row → slot, −1 slot → row; from/to slot index (−1 = the row)
export type State = {
  picks: number[]; // rows, in slot order
  slotOf: number[]; // per row: slot index or −1
  slotAt: number[]; // per row: when it last changed slot (for the slide between slots)
  slotFrom: number[];
  checkAt: number[]; // per row: when its check last changed …
  checked: boolean[]; // … and what it is now
  flights: Flight[];
  nopeAt: number; // a refused pick (time) …
  nopeRow: number; // … on this row
  countAt: number; // the count last changed …
  countFrom: number; // … from this
  panelAt: number; // the panel came up (or went down) …
  panelUp: boolean;
  scrollFrom: number;
  scrollTo: number;
  scrollAt: number;
};

export function scrollOf(s: State, now: number) {
  'worklet';
  return mix(s.scrollFrom, s.scrollTo, easeInOut((now - s.scrollAt) / 0.7));
}

export function replay(events: Ev[], now: number): State {
  'worklet';
  const n = CATS.length;
  const s: State = {
    picks: [],
    slotOf: new Array(n).fill(-1),
    slotAt: new Array(n).fill(NEVER),
    slotFrom: new Array(n).fill(-1),
    checkAt: new Array(n).fill(NEVER),
    checked: new Array(n).fill(false),
    flights: [],
    nopeAt: NEVER,
    nopeRow: -1,
    countAt: NEVER,
    countFrom: 0,
    panelAt: NEVER,
    panelUp: false,
    scrollFrom: 0,
    scrollTo: 0,
    scrollAt: NEVER,
  };
  for (let i = 0; i < events.length; i++) {
    const e = events[i];
    if (e.t > now) break;
    if (e.k === 2) {
      s.scrollFrom = scrollOf(s, e.t);
      s.scrollTo = e.v;
      s.scrollAt = e.t;
      continue;
    }
    const r = e.row;
    if (e.k === 0) {
      if (s.slotOf[r] >= 0) continue;
      if (s.picks.length >= MAX_PICKS) {
        s.nopeAt = e.t;
        s.nopeRow = r;
        continue;
      }
      const slot = s.picks.length;
      s.picks.push(r);
      s.slotOf[r] = slot;
      s.slotFrom[r] = slot;
      s.slotAt[r] = NEVER;
      s.checked[r] = true;
      s.checkAt[r] = e.t;
      s.flights.push({ row: r, from: -1, to: slot, at: e.t, dir: 1 });
      s.countFrom = s.picks.length - 1;
      s.countAt = e.t;
      if (s.picks.length === 1) {
        s.panelUp = true;
        s.panelAt = e.t;
      }
    } else {
      const slot = s.slotOf[r];
      if (slot < 0) continue;
      s.picks.splice(slot, 1);
      s.slotOf[r] = -1;
      s.checked[r] = false;
      s.checkAt[r] = e.t;
      s.flights.push({ row: r, from: slot, to: -1, at: e.t, dir: -1 });
      // the ones after it slide one slot left
      for (let k = slot; k < s.picks.length; k++) {
        const q = s.picks[k];
        s.slotFrom[q] = k + 1;
        s.slotOf[q] = k;
        s.slotAt[q] = e.t;
      }
      s.countFrom = s.picks.length + 1;
      s.countAt = e.t;
      if (s.picks.length === 0) {
        s.panelUp = false;
        s.panelAt = e.t + FLY * 0.6;
      }
    }
  }
  // flights that have landed are done
  s.flights = s.flights.filter((f) => now - f.at < FLY + 0.35);
  return s;
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  events: Ev[];
  dragging: number;
  scroll: number; // while a finger drags the page
  v: number;
  sparkleAt: number;
  homing: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, events: [], dragging: 0, scroll: 0, v: 0, sparkleAt: NEVER, homing: 0 };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  d.homing = 0;
  if (d.mode === 2) return;
  d.mode = 2;
  const k = now - d.t;
  d.events = [];
  for (let i = 0; i < LOOP_EVENTS.length; i++) {
    const e = LOOP_EVENTS[i];
    if (e.t <= d.t) d.events.push({ t: e.t + k, k: e.k, row: e.row, v: e.v });
  }
  const s = replay(d.events, now);
  d.scroll = scrollOf(s, now);
  d.v = 0;
  d.sparkleAt = d.t >= SPARKLE_AT && d.t < SPARKLE_AT + 1.2 ? SPARKLE_AT + k : NEVER;
}

export function push(d: Driver, e: Ev) {
  'worklet';
  d.events.push(e);
  // keep the log short: fold everything older than 3 s into fresh picks at the same moment
  if (d.events.length > 40) {
    const s = replay(d.events, e.t - 3);
    const keep = d.events.filter((x) => x.t > e.t - 3);
    const base: Ev[] = s.picks.map((row) => ({ t: e.t - 3.5, k: 0, row, v: 0 }));
    base.push({ t: e.t - 3.5, k: 2, row: -1, v: scrollOf(s, e.t - 3) });
    d.events = base.concat(keep);
  }
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  if (d.dragging === 0 && Math.abs(d.v) > 1) {
    d.scroll = Math.max(0, Math.min(MAX_SCROLL, d.scroll + d.v * dt));
    d.v *= Math.exp(-dt * 4);
    if (Math.abs(d.v) < 5) d.v = 0;
  }
  if (!auto || d.dragging === 1 || now - d.last < RESUME) return;
  const s = replay(d.events, now);
  if (d.homing === 0) {
    // drop the picks from the last, a beat apart, and scroll back to the top
    d.homing = 1;
    for (let i = s.picks.length - 1, k = 0; i >= 0; i--, k++) push(d, { t: now + k * 0.25, k: 1, row: s.picks[i], v: 0 });
    if (d.scroll > 0.5) {
      // the scripted scroll starts from where the finger left the page
      push(d, { t: now - 0.75, k: 2, row: -1, v: d.scroll });
      push(d, { t: now, k: 2, row: -1, v: 0 });
    }
    d.events.sort((a, b) => a.t - b.t);
    d.v = 0;
    return;
  }
  if (s.picks.length === 0 && s.flights.length === 0 && now - s.panelAt > 0.6 && now - s.scrollAt > 0.8 && now - s.nopeAt > 0.6) {
    d.mode = 0;
    d.t = 0;
    d.events = [];
    d.scroll = 0;
  }
}


export type Frame = { amb: number; now: number; s: State; scroll: number; sparkle: number; press: number };

export function frameAt(t: number): Frame {
  'worklet';
  const s = replay(LOOP_EVENTS, t);
  return { amb: t, now: t, s, scroll: scrollOf(s, t), sparkle: t >= SPARKLE_AT ? t - SPARKLE_AT : -1, press: 0 };
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const s = replay(d.events, now);
  // a scroll event (homing) animates; otherwise the finger's scroll rules
  const scripted = now - s.scrollAt < 0.7 ? scrollOf(s, now) : d.scroll;
  if (now - s.scrollAt < 0.7) d.scroll = scripted;
  return { amb: d.amb, now, s, scroll: scripted, sparkle: now - d.sparkleAt < 1.6 ? now - d.sparkleAt : -1, press: 0 };
}

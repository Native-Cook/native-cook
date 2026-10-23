import { demoPointer, type KeycapPointer } from './keycap-demo';
import { FAN_ORDER, MOOD_RGB, type KeycapLayout } from './keycap-theme';

export type LegendFlip = { from: number; to: number; t: number; mode: 0 | 1 | 2 };
export type MoodMix = { from: number; to: number; p: number; ox: number; oy: number; radial: 0 | 1 };
export type KeycapConfig = { layout: KeycapLayout; reduced: boolean; autoPlay?: boolean };
export type KeycapInput = {
  ready: boolean; pointer?: KeycapPointer; cancelled?: boolean;
  action?: { type: 'next' | 'previous' | 'selectMood' | 'toggleFan'; key?: number; mood?: number };
};
// bottom bits 0..3; other bits describe causal events, not continuously changing values.
export const EVENT = { burst: 16, fanOpen: 32, selection: 64, changed: 128, fanClose: 256, reset: 512, wrap: 1024 };
export function initialState(config: KeycapConfig) {
  'worklet';
  const legends: LegendFlip[] = [];
  for (let i = 0; i < 4; i++) legends.push({ from: 0, to: 0, t: -1, mode: config.reduced ? 2 : i === 3 ? 1 : 0 });
  return {
    config, clock: 0, remainder: 0, events: 0, eventLog: [] as number[],
    x: [0, 0, 0, 0], v: [0, 0, 0, 0], held: [0, 0, 0, 0], stroke: [0, 0, 0, 0],
    pressAge: [0, 0, 0, 0], pressFrom: [0, 0, 0, 0], bottom: [0, 0, 0, 0], direction: [1, 1, 1, 1],
    autoRelease: [0, 0, 0, 0], choices: [0, 0, 0, 0], legends,
    mood: { from: 0, to: 0, p: 1, ox: 0, oy: 0, radial: 0 } as MoodMix,
    moodAge: 0, moodDuration: 200, moodPending: 0, moodTarget: -1, ox: 0, oy: 0,
    charge: 0, chargeAge: 0, burst: 1, burstAge: -1, ring: 0, rim: [0, 0, 0, 0], rimStart: [-1, -1, -1, -1],
    scene: [MOOD_RGB[0][0], MOOD_RGB[0][1], MOOD_RGB[0][2]],
    fan: { open: 0, since: 0, sticky: 0 }, fanSession: 0, fanRequest: 0,
    out: [0, 0, 0, 0, 0, 0], outV: [0, 0, 0, 0, 0, 0], outFrom: [0, 0, 0, 0, 0, 0],
    miniX: [0, 0, 0, 0, 0, 0], miniV: [0, 0, 0, 0, 0, 0], miniActive: -1, dim: 1, dimFrom: 1,
    pointer: { down: 0, x: 0, y: 0, key: -1, startKey: -1, t0: 0, eligible: 0 },
    realDown: 0, demo: { active: config.autoPlay ? 1 : 0, t: 0, idle: 0, resetting: 0 },
  };
}
export type KeycapState = ReturnType<typeof initialState>;
function unit(x: number) { 'worklet'; return Math.max(0, Math.min(1, x)); }
function easeOut(x: number) { 'worklet'; const a = 1 - unit(x); return 1 - a * a * a; }
function event(s: KeycapState, mask: number, key: number) {
  'worklet';
  s.events |= mask;
  s.eventLog.push(mask, key, s.clock);
}
function bezier(t: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (t <= 0 || t >= 1) return unit(t);
  let lo = 0; let hi = 1; let q = t;
  for (let i = 0; i < 14; i++) {
    q = (lo + hi) / 2;
    const v = 3 * (1 - q) * (1 - q) * q * x1 + 3 * (1 - q) * q * q * x2 + q * q * q;
    if (v < t) lo = q; else hi = q;
  }
  return 3 * (1 - q) * (1 - q) * q * y1 + 3 * (1 - q) * q * q * y2 + q * q * q;
}
function flip(s: KeycapState, key: number, to: number) {
  'worklet';
  const from = s.choices[key];
  if (from === to) return;
  s.choices[key] = to;
  s.legends[key] = { from, to, t: 0, mode: s.config.reduced ? 2 : key === 3 ? 1 : 0 };
  event(s, EVENT.changed, key);
}
function mixMood(s: KeycapState, to: number, radial: number, duration: number) {
  'worklet';
  const from = s.mood.to;
  flip(s, 3, to);
  s.mood = { from, to, p: from === to ? 1 : 0, ox: s.ox, oy: s.oy, radial: s.config.reduced ? 0 : radial ? 1 : 0 };
  s.moodAge = 0;
  s.moodDuration = s.config.reduced ? 200 : duration;
}
function burst(s: KeycapState) {
  'worklet';
  s.burstAge = 0;
  s.charge = 0;
  if (!s.config.reduced) {
    s.burst = 2.4;
    for (let i = 0; i < 3; i++) s.rimStart[i] = s.clock + (3 - i) * 70;
  }
  event(s, EVENT.burst, 3);
}
function commitMood(s: KeycapState) {
  'worklet';
  const target = s.moodTarget >= 0 ? s.moodTarget : (s.choices[3] + s.direction[3] + 6) % 6;
  mixMood(s, target, 1, 460);
  s.moodPending = 0; s.moodTarget = -1;
  burst(s);
}
function openFan(s: KeycapState) {
  'worklet';
  s.fan = { open: 1, since: s.clock, sticky: 0 };
  s.fanSession = 1; s.fanRequest = 0; s.moodPending = 0; s.held[3] = 1;
  s.dimFrom = s.dim;
  event(s, EVENT.fanOpen, 3);
}
function closeFan(s: KeycapState, celebrate: boolean) {
  'worklet';
  s.fan = { open: 0, since: s.clock, sticky: 0 };
  s.dimFrom = s.dim;
  for (let i = 0; i < 6; i++) s.outFrom[i] = s.out[i];
  s.fanSession = 0; s.held[3] = 0; s.miniActive = -1;
  event(s, EVENT.fanClose, 3);
  if (celebrate) burst(s);
}
function selectMini(s: KeycapState, mini: number) {
  'worklet';
  s.miniActive = mini;
  if (s.choices[3] === FAN_ORDER[mini]) return;
  mixMood(s, FAN_ORDER[mini], 0, 160);
  event(s, EVENT.selection, mini);
}
function hitKey(layout: KeycapLayout, x: number, y: number) {
  'worklet';
  for (let i = 0; i < 4; i++) {
    const r = layout.hits[i];
    if (x >= r.x && x <= r.x + r.width && y >= r.y && y < r.y + r.height) return i;
  }
  return -1;
}
function hitMini(layout: KeycapLayout, x: number, y: number) {
  'worklet';
  let index = -1; let best = 34 * layout.u;
  for (let i = 0; i < 6; i++) {
    const d = Math.hypot(x - layout.minis[i].x, y - layout.minis[i].y);
    if (d < best) { best = d; index = i; }
  }
  return index;
}
function press(s: KeycapState, key: number) {
  'worklet';
  s.held[key] = 1;
  // A second entry during a latched stroke cannot restart or drop its bottom-out.
  if (s.stroke[key]) return;
  s.stroke[key] = 1; s.bottom[key] = 0; s.pressAge[key] = 0; s.pressFrom[key] = s.x[key];
  if (key === 3) { s.chargeAge = 0; s.charge = 0; }
}
function release(s: KeycapState, key: number, cancelled: boolean) {
  'worklet';
  s.held[key] = 0;
  if (key === 3 && !s.fanSession && !cancelled) {
    if (s.bottom[3]) commitMood(s); else s.moodPending = 1;
  }
}
function pointer(s: KeycapState, p: KeycapPointer, cancelled: boolean) {
  'worklet';
  const wasDown = s.pointer.down === 1;
  const key = p.down ? hitKey(s.config.layout, p.x, p.y) : -1;
  if (p.down && !wasDown) {
    s.pointer.startKey = key; s.pointer.t0 = s.clock; s.pointer.eligible = key === 3 ? 1 : 0;
  }
  if (cancelled && s.fan.open) closeFan(s, false);
  if (s.fan.open) {
    const mini = hitMini(s.config.layout, p.x, p.y);
    if (p.down && mini >= 0) selectMini(s, mini);
    if (p.down && !wasDown && s.fan.sticky && mini < 0) closeFan(s, false);
    else if (!p.down && wasDown) {
      if (mini >= 0 && !cancelled) closeFan(s, true);
      else s.fan.sticky = 1;
    }
    s.pointer.key = -1;
  } else {
    if (key !== 3) s.pointer.eligible = 0;
    if (s.pointer.key !== key) {
      if (s.pointer.key >= 0) release(s, s.pointer.key, cancelled);
      if (key >= 0) { s.direction[key] = 1; press(s, key); }
    }
    s.pointer.key = key;
  }
  // Only the last position actually on the mood key is a flood origin.
  if (key === 3) {
    s.ox = p.x - s.config.layout.width / 2;
    s.oy = p.y - s.config.layout.keys[3].y - s.config.layout.K / 2;
  }
  s.pointer.down = p.down ? 1 : 0; s.pointer.x = p.x; s.pointer.y = p.y;
}
function stopDemo(s: KeycapState) {
  'worklet';
  if (s.demo.active) pointer(s, { down: false, x: s.pointer.x, y: s.pointer.y }, false);
  s.demo.active = 0; s.demo.idle = 0; s.demo.resetting = 0;
}
function action(s: KeycapState, a: NonNullable<KeycapInput['action']>) {
  'worklet';
  stopDemo(s);
  if (a.type === 'toggleFan') {
    if (s.fan.open) closeFan(s, false);
    else { press(s, 3); s.fanRequest = 1; s.fan.since = s.clock; }
    return;
  }
  if (a.type === 'selectMood' && s.fan.open) {
    for (let i = 0; i < 6; i++) if (FAN_ORDER[i] === a.mood) selectMini(s, i);
    closeFan(s, true);
    return;
  }
  const k = a.type === 'selectMood' ? 3 : Math.max(0, Math.min(3, Math.round(a.key ?? 0)));
  if (s.fan.open) closeFan(s, false);
  s.direction[k] = a.type === 'previous' ? -1 : 1;
  if (a.type === 'selectMood') s.moodTarget = Math.max(0, Math.min(5, Math.round(a.mood ?? 0)));
  s.ox = 0; s.oy = 0;
  press(s, k); s.autoRelease[k] = 1;
}
/** Exact oscillator update; arrays avoid allocating temporary objects in every substep. */
function spring(x: number[], v: number[], i: number, target: number, k: number, c: number) {
  'worklet';
  if (x[i] === target && v[i] === 0) return;
  const z = x[i] - target;
  const d = c / 2;
  const e = Math.exp(-d * 0.001);
  if (k <= d * d) {
    const b = v[i] + d * z;
    x[i] = target + (z + b * 0.001) * e;
    v[i] = (v[i] - d * b * 0.001) * e;
  } else {
    const w = Math.sqrt(k - d * d);
    const cs = Math.cos(w * 0.001); const sn = Math.sin(w * 0.001);
    x[i] = target + e * (z * cs + (v[i] + d * z) * sn / w);
    v[i] = e * (v[i] * cs - (d * v[i] + k * z) * sn / w);
  }
  if (Math.abs(x[i] - target) < 0.0001 && Math.abs(v[i]) < 0.001) { x[i] = target; v[i] = 0; }
}
function mechanics(s: KeycapState) {
  'worklet';
  const reduced = s.config.reduced;
  for (let i = 0; i < 4; i++) {
    if (s.stroke[i]) {
      s.pressAge[i]++;
      const age = s.pressAge[i];
      const old = s.x[i];
      if (reduced) s.x[i] = s.pressFrom[i] + (0.6 - s.pressFrom[i]) * easeOut(age / 85);
      else {
        // A stiff cam drive, dwell at the tactile bump, then acceleration into the hard stop.
        const drive = age <= 40 ? 0.45 * easeOut(age / 40) : age <= 60 ? 0.45 : 0.45 + 0.55 * ((age - 60) / 25) ** 2;
        s.x[i] = s.pressFrom[i] + (1 - s.pressFrom[i]) * Math.min(1, drive);
      }
      s.v[i] = (s.x[i] - old) * 1000;
      if (age >= 85) {
        s.x[i] = reduced ? 0.6 : 1; s.v[i] = reduced ? 0 : -s.v[i] * 0.2;
        s.stroke[i] = 0; s.bottom[i] = 1;
        event(s, 1 << i, i);
        if (i < 3) flip(s, i, (s.choices[i] + s.direction[i] + (i === 2 ? 3 : 2)) % (i === 2 ? 3 : 2));
        else if (s.moodPending) commitMood(s);
        if (s.autoRelease[i]) { s.autoRelease[i] = 0; release(s, i, false); }
      }
    } else if (s.held[i]) {
      spring(s.x, s.v, i, reduced ? 0.6 : 1, 6400, 160);
      if (s.x[i] > 1) { s.x[i] = 1; s.v[i] = 0; }
    } else {
      spring(s.x, s.v, i, 0, reduced ? 1600 : 1100, reduced ? 80 : 42);
      if (s.x[i] < -0.12) { s.x[i] = -0.12; s.v[i] = 0; }
    }
    const f = s.legends[i];
    if (f.t >= 0) {
      f.t++;
      const duration = reduced ? 140 : i === 3 ? 110 + (40 * s.config.layout.u + Math.hypot(s.mood.ox, s.mood.oy)) * 0.9 : 208;
      if (f.t >= duration) { f.from = f.to; f.t = -1; }
    }
  }
  if (s.held[3] && s.bottom[3]) { s.chargeAge++; s.charge = easeOut(s.chargeAge / 300); }
  else { s.charge *= Math.exp(-1 / 60); if (s.charge < 0.0001) s.charge = 0; }
  if (!s.fan.open && ((s.pointer.down && s.pointer.eligible && s.clock - s.pointer.t0 >= 450)
    || (s.fanRequest && s.pressAge[3] >= 85 && s.clock - s.fan.since >= 450))) openFan(s);
  const age = s.clock - s.fan.since;
  s.dim = s.dimFrom + ((s.fan.open ? 0.72 : 1) - s.dimFrom) * unit(age / 200);
  for (let i = 0; i < 6; i++) {
    const rank = Math.min(Math.abs(i - 2), Math.abs(i - 3));
    if (reduced) s.out[i] = s.fan.open ? unit(age / 200) : s.outFrom[i] * (1 - unit(age / 200));
    else if (s.fan.open) {
      if (age >= rank * 28) spring(s.out, s.outV, i, 1, 260, 20);
      s.out[i] = Math.min(1.06, s.out[i]);
    } else {
      s.out[i] = s.outFrom[i] * (1 - bezier(unit((age - (2 - rank) * 28) / 280), 0.32, 0.72, 0, 1));
      s.outV[i] = 0;
    }
    spring(s.miniX, s.miniV, i, s.fan.open && FAN_ORDER[i] === s.choices[3] ? (reduced ? 0.6 : 1) : 0, 1600, reduced ? 80 : 58);
  }
}
function lighting(s: KeycapState) {
  'worklet';
  if (s.mood.p < 1) {
    s.moodAge++;
    const t = unit(s.moodAge / s.moodDuration);
    s.mood.p = s.mood.radial ? bezier(t, 0.23, 1, 0.32, 1) : t;
    if (t === 1) s.mood.from = s.mood.to;
  }
  for (let i = 0; i < 3; i++) {
    const target = MOOD_RGB[s.choices[3]][i];
    s.scene[i] += (target - s.scene[i]) * (1 - Math.exp(-1 / 90));
    if (Math.abs(target - s.scene[i]) < 0.00001) s.scene[i] = target;
  }
  if (s.burstAge >= 0) s.burstAge++;
  s.burst = s.config.reduced ? 1 : s.burstAge >= 0 && s.burstAge < 700 ? 1 + 1.4 * (1 - easeOut(s.burstAge / 700))
    : 1 + 0.05 * Math.sin(s.clock * Math.PI / 2000) * (s.demo.active ? 1 - easeOut((s.demo.t - 12000) / 1000) : 1);
  s.ring = !s.config.reduced && s.burstAge >= 0 && s.burstAge < 900 ? s.burstAge / 900 : 0;
  for (let i = 0; i < 3; i++) {
    const age = s.clock - s.rimStart[i];
    if (!s.config.reduced && s.rimStart[i] >= 0 && age >= 0 && age < 580) {
      if (age === 0 && !s.held[i] && !s.stroke[i]) s.v[i] += 3.4;
      s.rim[i] = age < 60 ? age / 60 : (Math.exp(-5 * (age - 60) / 520) - Math.exp(-5)) / (1 - Math.exp(-5));
    } else s.rim[i] = 0;
  }
}
function quietReset(s: KeycapState) {
  'worklet';
  if (s.fan.open) closeFan(s, false);
  for (let i = 0; i < 3; i++) flip(s, i, 0);
  mixMood(s, 0, 0, 200);
  s.demo.resetting = 1;
  event(s, EVENT.reset, -1);
}

/** Mutates serialisable state. dt is milliseconds; input edges also work at dt=0.
 * Fixed 1ms ticks retain fractional frame time, so 60/120Hz share an identical timeline.
 * eventLog contains [event bit, key/mini, simulation timestamp] triples, including repeated events.
 */
export function step(state: KeycapState, input: KeycapInput, dtMs: number): KeycapState {
  'worklet';
  let s = state;
  s.events = 0; s.eventLog = [];
  if (!input.ready) return s;
  if (input.pointer) {
    if (input.pointer.down || s.realDown) { stopDemo(s); s.realDown = input.pointer.down ? 1 : 0; pointer(s, input.pointer, input.cancelled === true); }
  }
  if (input.action) action(s, input.action);
  s.remainder += Math.max(0, Math.min(100, dtMs));
  while (s.remainder >= 1 - 0.0000001) {
    s.remainder = Math.max(0, s.remainder - 1);
    s.clock++;
    if (s.demo.active) {
      s.demo.t++;
      if (s.demo.t === 13000) {
        const rest = initialState(s.config);
        rest.remainder = s.remainder;
        rest.events = s.events; rest.eventLog = s.eventLog;
        event(rest, EVENT.wrap, -1);
        s = rest;
        continue;
      }
      pointer(s, demoPointer(s.demo.t, s.config.layout), false);
    } else if (!s.realDown) {
      s.demo.idle++;
      if (s.config.autoPlay && s.demo.idle === 3500) quietReset(s);
      if (s.config.autoPlay && s.demo.idle >= 4200) {
        const rest = initialState(s.config);
        rest.remainder = s.remainder; rest.events = s.events; rest.eventLog = s.eventLog;
        event(rest, EVENT.reset, -1); s = rest; continue;
      }
    }
    mechanics(s);
    lighting(s);
  }
  return s;
}

// Cabin Climate Car — the loop and the touch driver. The temperature moves a degree at a time (each
// one rolls in), the air over the car takes its colour, the seats slide for more legroom.

import { TEMP } from './ccc-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const ROLL = 0.18;

/** The loop's temperature steps: four down, eight up, four back. */
export const STEPS: [number, number][] = [
  [1.0, 69],
  [1.3, 68],
  [1.6, 67],
  [1.9, 66],
  [3.0, 67],
  [3.25, 68],
  [3.5, 69],
  [3.75, 70],
  [4.0, 71],
  [4.25, 72],
  [4.5, 73],
  [4.75, 74],
  [7.2, 73],
  [7.45, 72],
  [7.7, 71],
  [7.95, 70],
];
export const LEG = [5.4, 6.6] as const;

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
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
export function bump(t: number, at: number, dur: number) {
  'worklet';
  const p = (t - at) / dur;
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

export type Frame = {
  amb: number;
  temp: number; // the target (an integer) …
  from: number; // … the one before …
  tempAt: number; // … seconds since it changed (the digits roll for ROLL)
  heat: number; // the air's temperature, eased (a float, for colour)
  plusK: number;
  minusK: number;
  leg: number; // 0 distributed … 1 extra rear legroom (a spring)
  legOn: boolean;
  legAt: number;
  fan: number; // 0 Auto, 1 Low, 2 High
  fanAt: number;
  cardK: number; // a pressed card (legroom 3, fan 4, pill 5) …
  card: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  let temp = TEMP.rest;
  let from = TEMP.rest;
  let at = -10;
  let plusK = 0;
  let minusK = 0;
  let heat = TEMP.rest;
  for (let i = 0; i < STEPS.length; i++) {
    const [st, v] = STEPS[i];
    const k = bump(t, st - 0.04, 0.2);
    if (v > (i > 0 ? STEPS[i - 1][1] : TEMP.rest)) plusK = Math.max(plusK, k);
    else minusK = Math.max(minusK, k);
    if (t >= st) {
      from = temp;
      temp = v;
      at = st;
    }
  }
  // the air follows the setting a little behind it
  heat = mix(from, temp, easeOut((t - at) / 0.9));
  const legOn = t >= LEG[0] && t < LEG[1];
  const legAt = t >= LEG[1] ? LEG[1] : t >= LEG[0] ? LEG[0] : -10;
  const lk = legAt < 0 ? 0 : spring(t - legAt, 0.55, 0.8);
  return {
    amb: t,
    temp,
    from,
    tempAt: t - at,
    heat,
    plusK,
    minusK,
    leg: legOn ? lk : t >= LEG[1] ? 1 - lk : 0,
    legOn,
    legAt: t - legAt,
    fan: 0,
    fanAt: 10,
    cardK: Math.max(bump(t, LEG[0] - 0.05, 0.25), bump(t, LEG[1] - 0.05, 0.25)),
    card: 3,
  };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  temp: number;
  from: number;
  tempAt: number;
  heatFrom: number;
  leg: boolean;
  legAt: number;
  legFrom: number;
  fan: number;
  fanAt: number;
  pressing: number; // 1 plus, 2 minus, 3 legroom, 4 fan, 5 pill, 6 dragging the temperature
  pressAt: number;
  releaseAt: number;
  released: number;
  repeatAt: number;
  homing: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, temp: TEMP.rest, from: TEMP.rest, tempAt: -10, heatFrom: TEMP.rest, leg: false, legAt: -10, legFrom: 0, fan: 0, fanAt: -10, pressing: 0, pressAt: -10, releaseAt: -10, released: 0, repeatAt: -10, homing: 0 };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  d.homing = 0;
  if (d.mode === 2) return;
  const f = frameAt(d.t);
  d.mode = 2;
  d.temp = f.temp;
  d.from = f.from;
  d.tempAt = now - f.tempAt;
  d.heatFrom = f.from;
  d.leg = f.legOn;
  d.legAt = now - f.legAt;
  d.legFrom = f.legOn ? 0 : 1;
  d.fan = 0;
}

function heatOf(d: Driver, now: number) {
  'worklet';
  return mix(d.heatFrom, d.temp, easeOut((now - d.tempAt) / 0.9));
}

export function setTemp(d: Driver, v: number, now: number) {
  'worklet';
  const nv = Math.max(TEMP.min, Math.min(TEMP.max, v));
  if (nv === d.temp) return false;
  d.heatFrom = heatOf(d, now);
  d.from = d.temp;
  d.temp = nv;
  d.tempAt = now;
  return true;
}

export function toggleLeg(d: Driver, now: number) {
  'worklet';
  d.leg = !d.leg;
  d.legAt = now;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean): number {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return 0;
  }
  // holding + or − repeats after a beat
  if ((d.pressing === 1 || d.pressing === 2) && now - d.pressAt > 0.45 && now - d.repeatAt > 0.16) {
    d.repeatAt = now;
    return setTemp(d, d.temp + (d.pressing === 1 ? 1 : -1), now) ? 1 : 0;
  }
  if (!auto || d.pressing > 0 || now - d.last < RESUME) return 0;
  // home: back to 70 a degree at a time, seats back, fan on Auto; then the loop
  d.homing = 1;
  if (d.leg) toggleLeg(d, now);
  if (d.fan !== 0) {
    d.fan = 0;
    d.fanAt = now;
  }
  if (d.temp !== 70 && now - d.tempAt > 0.22) {
    setTemp(d, d.temp + (d.temp > 70 ? -1 : 1), now);
    return 0;
  }
  if (d.temp === 70 && now - d.tempAt > 1 && now - d.legAt > 0.8 && now - d.fanAt > 0.5) {
    d.mode = 0;
    d.t = 0;
    d.homing = 0;
  }
  return 0;
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const pk = (id: number) => (d.pressing === id ? smooth((now - d.pressAt) / 0.15) : d.released === id ? 1 - smooth((now - d.releaseAt) / 0.15) : 0);
  const lk = spring(now - d.legAt, 0.55, 0.8);
  const card = d.pressing >= 3 && d.pressing <= 5 ? d.pressing : d.released;
  return {
    amb: d.amb,
    temp: d.temp,
    from: d.from,
    tempAt: now - d.tempAt,
    heat: heatOf(d, now),
    plusK: pk(1),
    minusK: pk(2),
    leg: d.leg ? lk : 1 - lk,
    legOn: d.leg,
    legAt: now - d.legAt,
    fan: d.fan,
    fanAt: now - d.fanAt,
    cardK: card >= 3 && card <= 5 ? pk(card) : 0,
    card,
  };
}

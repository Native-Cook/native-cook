import {
  BASE_A,
  type Drop,
  type Flight,
  type Geo,
  K,
  LAND_T,
  LAND_Y,
  type Mix,
  POT_A,
  type Ripple,
  type Splash,
  TEAL,
  YELLOW,
  autoMix,
  easeInOutCubic,
  easeInOutSine,
  landing,
  mixedShare,
  potCenter,
  seg,
  windingAt,
} from './paintMixerMotion';

/*
 The bowl under the finger: the same paint model as the script, stepped on the UI thread. Drops
 fly from the pot (or straight down from the finger) and land; each landing kicks the swirl, whose
 angular speed decays (τ 1.1 s); stirring pulls the swirl towards the finger's angular speed.
 Winding (the total turned, either way) is what mixes the drops. 2 s after the last touch the
 bowl is washed back to white in 0.85 s and the script restarts from rest.
 */

const MAX_DROPS = 4;
const SWIRL_DECAY = 1.1;
const LAND_KICK = 2.4;
const WASH_TIME = 0.85;
export const EVENT_LAND = 1;
export const EVENT_GRAB = 2;

type SimDrop = { x: number; y: number; amt: number; a: [number, number, number]; windLand: number; twistLand: number };
type SimFlight = { fx: number; fy: number; tx: number; ty: number; t0: number; dur: number; pot: number; into: boolean };

export type Sim = {
  baseA: [number, number, number];
  baseW: number;
  drops: SimDrop[];
  wind: number;
  twist: number;
  omega: number;
  stirW: number;
  stirring: boolean;
  ripples: { x: number; y: number; t0: number; str: number }[];
  splashes: { x: number; y: number; t0: number; pot: number }[];
  flights: SimFlight[];
  held: { x: number; y: number; pot: number } | null;
  lift: number[];
  /** Wash-back start time (−1: none). */
  wash: number;
  /** Amount scale left by a hand-over during the script's own wash. */
  fade: number;
  events: number;
  reduced: boolean;
};

export function createSim(reduced: boolean): Sim {
  'worklet';
  return {
    baseA: [BASE_A[0], BASE_A[1], BASE_A[2]],
    baseW: 1,
    drops: [],
    wind: 0,
    twist: 0,
    omega: 0,
    stirW: 0,
    stirring: false,
    ripples: [],
    splashes: [],
    flights: [],
    held: null,
    lift: [0, 0, 0, 0, 0],
    wash: -1,
    fade: 1,
    events: 0,
    reduced,
  };
}

/** Takes the script's bowl at loop time t over (the same frame, now driven by the sim). */
export function simFromAuto(sim: Sim, g: Geo, t: number, now: number) {
  'worklet';
  const m = autoMix(g, t);
  const wind = windingAt(t);
  sim.wind = wind;
  sim.twist = t > 6.6 ? wind * (1 - easeInOutSine(seg(t, 6.6, 8.4))) : wind;
  const dt = 0.02;
  sim.omega = t < 6.6 ? (windingAt(t + dt) - wind) / dt : 0;
  sim.drops = [];
  sim.flights = [];
  sim.held = null;
  sim.stirring = false;
  const events: [number, number][] = [
    [YELLOW, LAND_Y],
    [TEAL, LAND_T],
  ];
  for (const [pot, at] of events) {
    if (t >= at) {
      const [x, y] = landing(g, pot);
      const d = m.drops[sim.drops.length];
      sim.drops.push({ x, y, amt: d.amt, a: POT_A[pot], windLand: windingAt(at), twistLand: sim.twist - d.tw });
    } else if (t >= at - 0.5) {
      const [px, py] = potCenter(g, pot);
      const [lx, ly] = landing(g, pot);
      sim.flights.push({ fx: px, fy: py, tx: lx, ty: ly, t0: now - (t - (at - 0.5)), dur: 0.5, pot, into: true });
    }
  }
  sim.ripples = m.ripples.map((r) => ({ x: r.x, y: r.y, t0: now - r.age, str: r.str }));
  sim.splashes = m.splashes.map((s) => ({ x: s.x, y: s.y, t0: now - s.age, pot: s.pot }));
  sim.lift = m.lift.slice();
  sim.fade = m.fade;
  sim.wash = -1;
}

function addDrop(sim: Sim, x: number, y: number, pot: number, now: number) {
  'worklet';
  if (sim.drops.length >= MAX_DROPS) {
    // The oldest drop is folded into the base paint.
    const o = sim.drops.shift()!;
    const wgt = K * o.amt * sim.fade;
    for (let c = 0; c < 3; c += 1) sim.baseA[c] = (sim.baseA[c] * sim.baseW + o.a[c] * wgt) / (sim.baseW + wgt);
    sim.baseW += wgt;
  }
  sim.drops.push({ x, y, amt: sim.reduced ? 0.001 : 0, a: POT_A[pot], windLand: sim.wind, twistLand: sim.twist });
  if (!sim.reduced) {
    sim.omega += LAND_KICK;
    sim.ripples.push({ x, y, t0: now, str: 1 });
    if (sim.ripples.length > 3) sim.ripples.shift();
    sim.splashes.push({ x, y, t0: now, pot });
  }
  sim.events |= EVENT_LAND;
}

export function simStep(sim: Sim, g: Geo, now: number, dt: number) {
  'worklet';
  // Flights land.
  for (let i = sim.flights.length - 1; i >= 0; i -= 1) {
    const f = sim.flights[i];
    if (now >= f.t0 + f.dur) {
      sim.flights.splice(i, 1);
      if (f.into) addDrop(sim, f.tx, f.ty, f.pot, now);
    }
  }
  // Drops pour in; the swirl decays or follows the stirring finger.
  for (const d of sim.drops) d.amt += (1 - d.amt) * (1 - Math.exp(-dt / (sim.reduced ? 0.25 : 0.13)));
  if (sim.stirring && !sim.reduced) sim.omega += (sim.stirW - sim.omega) * Math.min(1, dt * 8);
  else sim.omega *= Math.exp(-dt / SWIRL_DECAY);
  if (sim.reduced) sim.omega = 0;
  sim.twist += sim.omega * dt;
  sim.wind += Math.abs(sim.omega) * dt;
  sim.ripples = sim.ripples.filter((r) => now - r.t0 < 1.6);
  sim.splashes = sim.splashes.filter((s) => now - s.t0 < 0.45);
  for (let i = 0; i < 5; i += 1) {
    const target = sim.held && sim.held.pot === i ? 1 : 0;
    sim.lift[i] += (target - sim.lift[i]) * (1 - Math.exp(-dt / 0.08));
  }
  // Wash back to white, then empty.
  if (sim.wash >= 0 && now - sim.wash >= WASH_TIME) {
    sim.drops = [];
    sim.baseA = [BASE_A[0], BASE_A[1], BASE_A[2]];
    sim.baseW = 1;
    sim.wind = 0;
    sim.twist = 0;
    sim.omega = 0;
    sim.fade = 1;
    sim.wash = -1;
  }
}

/** Whether anything is still moving (otherwise the frame can be skipped). */
export function simBusy(sim: Sim) {
  'worklet';
  if (sim.flights.length || sim.ripples.length || sim.splashes.length || sim.held || sim.wash >= 0) return true;
  if (Math.abs(sim.omega) > 0.01) return true;
  for (const d of sim.drops) if (d.amt < 0.995) return true;
  for (const l of sim.lift) if (l > 0.005) return true;
  return false;
}

export function simMix(sim: Sim, g: Geo, now: number): Mix {
  'worklet';
  let front = -1;
  let fade = sim.fade;
  let back = 0;
  if (sim.wash >= 0) {
    const p = seg(now - sim.wash, 0, WASH_TIME);
    front = (g.paint + 46 * g.u) * easeInOutSine(p);
    fade *= 1 - easeInOutCubic(p);
    back = -2.4 * Math.PI * easeInOutSine(p);
  }
  const drops: Drop[] = sim.drops.map((d) => ({
    x: d.x,
    y: d.y,
    amt: d.amt,
    m: sim.reduced ? 1 : mixedShare(sim.wind - d.windLand),
    tw: sim.twist - d.twistLand,
    a: d.a,
  }));
  const flights: Flight[] = sim.flights.map((f) => {
    const s = seg(now, f.t0, f.t0 + f.dur);
    const e = f.into ? easeInOutSine(s) : 1 - (1 - s) * (1 - s) * (1 - s);
    return {
      x: f.fx + (f.tx - f.fx) * e,
      y: f.fy + (f.ty - f.fy) * e,
      h: f.dur > 0.4 ? Math.sin(Math.PI * s) : 1 - s,
      pot: f.pot,
    };
  });
  if (sim.held) flights.push({ x: sim.held.x, y: sim.held.y, h: 1, pot: sim.held.pot });
  const ripples: Ripple[] = sim.ripples.map((r) => ({ x: r.x, y: r.y, age: now - r.t0, str: r.str }));
  const splashes: Splash[] = sim.splashes.map((s) => ({ x: s.x, y: s.y, age: now - s.t0, pot: s.pot }));
  return {
    baseA: sim.baseA,
    baseW: sim.baseW,
    drops,
    ripples,
    splashes,
    flights,
    wash: [front, back],
    fade,
    stick: [0, 0, 0],
    lift: sim.lift,
  };
}

/** A finger down: on a pot it picks up a drop, in the bowl it stirs. */
export function simDown(sim: Sim, g: Geo, x: number, y: number) {
  'worklet';
  for (let i = 0; i < 5; i += 1) {
    const [px, py] = potCenter(g, i);
    if (Math.hypot(x - px, y - py) < g.potR + 16) {
      sim.held = { x, y: y - 26 * g.u, pot: i };
      sim.events |= EVENT_GRAB;
      return;
    }
  }
  if (Math.hypot(x - g.cx, y - g.cy) < g.rim) {
    sim.stirring = true;
    sim.stirW = 0;
  }
}

/** A finger moving: carries the drop, or stirs at the finger's angular speed (rad/s). */
export function simMove(sim: Sim, g: Geo, x: number, y: number, vx: number, vy: number) {
  'worklet';
  if (sim.held) {
    // The drop hangs a little above the fingertip so it stays visible.
    sim.held.x = x;
    sim.held.y = y - 26 * g.u;
    return;
  }
  if (sim.stirring) {
    const dx = x - g.cx;
    const dy = y - g.cy;
    const r2 = Math.max(dx * dx + dy * dy, (g.paint * 0.25) ** 2);
    sim.stirW = Math.max(-14, Math.min(14, (dx * vy - dy * vx) / r2));
  }
}

/** A finger up: a drop over the paint falls in; anywhere else it flies back to its pot. */
export function simUp(sim: Sim, g: Geo, now: number) {
  'worklet';
  sim.stirring = false;
  sim.stirW = 0;
  const h = sim.held;
  if (!h) return;
  sim.held = null;
  const dx = h.x - g.cx;
  const dy = h.y - g.cy;
  const r = Math.hypot(dx, dy);
  if (r < g.paint) {
    sim.flights.push({ fx: h.x, fy: h.y, tx: h.x, ty: h.y, t0: now, dur: sim.reduced ? 0 : 0.22, pot: h.pot, into: true });
  } else {
    const [px, py] = potCenter(g, h.pot);
    sim.flights.push({ fx: h.x, fy: h.y, tx: px, ty: py, t0: now, dur: sim.reduced ? 0 : 0.3, pot: h.pot, into: false });
  }
}

export function simWash(sim: Sim, now: number) {
  'worklet';
  if (sim.drops.length === 0 && sim.baseW === 1) return;
  sim.wash = now;
}

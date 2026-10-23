/**
 * A run of dominoes standing on a table, each a rigid tile that tips about its leading bottom edge.
 *
 * Everything is measured in table units (pt on the table plane). A domino stands at a point on its
 * path, facing along it. Tipping forward by φ, it turns about its front bottom edge under gravity:
 * with its centre of mass T/2 behind and H/2 above that edge, α = 3g / (2√(T² + H²)) · sin(φ − β₀),
 * where β₀ = atan(T/H) is the lean it must pass before it falls by itself.
 *
 * A falling domino's top front edge meets the next one's back face when
 * sin(φ − φₙ) = (d·cos φₙ − T) / H, with d the distance between their centres and φₙ the next one's
 * tilt. If the next one is standing, the hit hands it a share of the falling one's spin and the
 * falling one leans on it from then on, its tilt given by that same contact. So the closer the
 * dominoes, the sooner each one strikes the next, and the faster the wave runs. The last domino
 * lands flat on the table.
 *
 * Laying a new run (dominoes pop up in turn), standing a fallen run back up (in reverse, last first)
 * and clearing it are animations on the same clock, so the whole thing runs the same, step for step,
 * on any platform.
 */

// A domino's thickness (along its path), height and width, in table units.
export const T = 7;
export const H = 40;
export const W = 20;
// Distance between the centres of neighbouring dominoes on a path.
export const SPACING = 21;
export const MAX_DOMINOES = 96;

// Gravity, table units/s² (a gentle slow motion of the real thing at this scale).
const GRAVITY = 2600;
// Share of the striking domino's spin the struck one starts with.
const TRANSFER = 0.82;
// Share of a leaning domino's weight that pushes on the one it leans on.
const LEAN_PUSH = 0.7;
// The last domino's bounce off the table: share of its spin it keeps, reversed.
const BOUNCE = 0.18;
// A push that tips a domino over by itself, rad/s.
export const PUSH = 3.6;

// Popping up: each domino drops in from POP_DROP above over POP_TIME s, STAGGER s after the one before.
export const POP_TIME = 0.34;
export const POP_DROP = 34;
// Standing back up, in reverse: each domino takes RISE_TIME s, RISE_STAGGER s after the one ahead of it.
export const RISE_TIME = 0.42;
export const RISE_STAGGER = 0.028;
// Clearing a run: each sinks over SINK_TIME s, SINK_STAGGER s after the one before.
export const SINK_TIME = 0.26;
export const SINK_STAGGER = 0.012;

const BETA = Math.atan(T / H);
const ALPHA = (3 * GRAVITY) / (2 * Math.sqrt(T * T + H * H));
const FLAT = Math.PI / 2;

/** 0 standing, 1 falling, 2 leaning on the next one, 3 flat on the table, 4 standing back up. */
export type DominoState = 0 | 1 | 2 | 3 | 4;

export type Chain = {
  count: number;
  x: Float64Array;
  z: Float64Array;
  /** Heading along the path (unit vector on the table). */
  hx: Float64Array;
  hz: Float64Array;
  /** Distance to the next domino's centre. */
  gap: Float64Array;
  tilt: Float64Array;
  spin: Float64Array;
  state: Uint8Array;
  /** When it popped up, when it starts to sink (Infinity when it stays), and its tilt when the rise began. */
  born: Float64Array;
  sink: Float64Array;
  riseFrom: Float64Array;
  riseAt: Float64Array;
  /** The pips on each half of its face, 0…6. */
  pipsA: Uint8Array;
  pipsB: Uint8Array;
  /** The chain's own clock, s. */
  time: number;
  /** Hits so far (for a click every few). */
  hits: number;
  /** The last rise was gentle (reduced motion): no stagger, no overshoot. */
  gentle: boolean;
  seed: number;
};

export function createChain(): Chain {
  'worklet';
  const n = MAX_DOMINOES;
  return {
    count: 0,
    x: new Float64Array(n),
    z: new Float64Array(n),
    hx: new Float64Array(n),
    hz: new Float64Array(n),
    gap: new Float64Array(n),
    tilt: new Float64Array(n),
    spin: new Float64Array(n),
    state: new Uint8Array(n),
    born: new Float64Array(n),
    sink: new Float64Array(n).fill(Infinity),
    riseFrom: new Float64Array(n),
    riseAt: new Float64Array(n),
    pipsA: new Uint8Array(n),
    pipsB: new Uint8Array(n),
    time: 0,
    hits: 0,
    gentle: false,
    seed: 0x2d0e1f,
  };
}

/** A small seeded random in [0, 1): xorshift32 on the chain's seed. */
function random(c: Chain) {
  'worklet';
  let s = c.seed | 0;
  s ^= s << 13;
  s ^= s >>> 17;
  s ^= s << 5;
  c.seed = s;
  return (s >>> 0) / 4294967296;
}

/** Ease out with a little overshoot. */
export function easeOutBack(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  const c = 1.7;
  const u = k - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/** Empties the run at once. */
export function clearChain(c: Chain) {
  'worklet';
  c.count = 0;
  c.hits = 0;
  c.seed = 0x2d0e1f;
}

/** Sinks every domino into the table, first to last; returns when the last one is gone. */
export function sinkChain(c: Chain) {
  'worklet';
  let end = c.time;
  for (let i = 0; i < c.count; i += 1) {
    const at = c.time + i * SINK_STAGGER;
    if (c.sink[i] > at) c.sink[i] = at;
    end = Math.max(end, c.sink[i] + SINK_TIME);
  }
  return end;
}

/** Stands a domino up at (x, z) facing (hx, hz), popping up `delay` s from now. */
export function addDomino(c: Chain, x: number, z: number, hx: number, hz: number, delay: number) {
  'worklet';
  if (c.count >= MAX_DOMINOES) return false;
  const i = c.count;
  c.x[i] = x;
  c.z[i] = z;
  c.hx[i] = hx;
  c.hz[i] = hz;
  c.tilt[i] = 0;
  c.spin[i] = 0;
  c.state[i] = 0;
  c.born[i] = c.time + delay;
  c.sink[i] = Infinity;
  c.gap[i] = Infinity;
  c.pipsA[i] = Math.floor(random(c) * 7);
  c.pipsB[i] = Math.floor(random(c) * 7);
  if (i > 0) c.gap[i - 1] = Math.hypot(x - c.x[i - 1], z - c.z[i - 1]);
  c.count = i + 1;
  return true;
}

/** Re-aims a domino's heading (used while a path is drawn, once the next point is known). */
export function aimDomino(c: Chain, i: number, hx: number, hz: number) {
  'worklet';
  c.hx[i] = hx;
  c.hz[i] = hz;
}

/** Tips domino `i` forward with spin `w`, if it is standing. */
export function pushDomino(c: Chain, i: number, w: number) {
  'worklet';
  if (i < 0 || i >= c.count || c.state[i] !== 0) return false;
  c.state[i] = 1;
  c.spin[i] = w;
  return true;
}

/** Whether any domino is down or on its way down. */
export function anyFallen(c: Chain) {
  'worklet';
  for (let i = 0; i < c.count; i += 1) if (c.state[i] !== 0) return true;
  return false;
}

/** Whether something is still moving (falling, rising, popping up or sinking). */
export function chainBusy(c: Chain) {
  'worklet';
  for (let i = 0; i < c.count; i += 1) {
    const s = c.state[i];
    if (s === 1 || s === 4) return true;
    if (c.time < c.born[i] + POP_TIME) return true;
    if (c.sink[i] !== Infinity) return true;
  }
  return false;
}

/** Stands every fallen domino back up, in reverse: the last one first (all at once when `gentle`). */
export function raiseChain(c: Chain, gentle: boolean) {
  'worklet';
  let k = 0;
  c.gentle = gentle;
  for (let i = c.count - 1; i >= 0; i -= 1) {
    if (c.state[i] === 0) continue;
    c.state[i] = 4;
    c.riseFrom[i] = c.tilt[i];
    c.riseAt[i] = c.time + (gentle ? 0 : k * RISE_STAGGER);
    c.spin[i] = 0;
    k += 1;
  }
}

/** The tilt at which domino i, leaning forward, touches the next one tilted by `next`. */
function leanOn(c: Chain, i: number, next: number) {
  'worklet';
  const s = (c.gap[i] * Math.cos(next) - T) / H;
  if (s >= 1) return FLAT + 1;
  return next + Math.asin(Math.max(-1, s));
}

/** One fixed step of the run. */
export function stepChain(c: Chain, dt: number) {
  'worklet';
  c.time += dt;
  // Back to front, so each domino sees where the one ahead of it is this step.
  for (let i = c.count - 1; i >= 0; i -= 1) {
    const state = c.state[i];
    if (state === 1) {
      // Its own weight, plus the weight of the one leaning on it from behind, which pushes it on.
      let torque = Math.sin(c.tilt[i] - BETA);
      if (i > 0 && c.state[i - 1] === 2) torque += LEAN_PUSH * Math.max(0, Math.sin(c.tilt[i - 1] - BETA));
      let w = c.spin[i] + ALPHA * torque * dt;
      let a = c.tilt[i] + w * dt;
      // Not pushed hard enough: it rocks back onto its base.
      if (a <= 0) {
        a = 0;
        w = 0;
        c.state[i] = 0;
      }
      const hasNext = i + 1 < c.count && c.sink[i + 1] === Infinity;
      const contact = hasNext ? leanOn(c, i, c.tilt[i + 1]) : FLAT + 1;
      if (hasNext && contact <= FLAT && a >= contact) {
        a = contact;
        c.state[i] = 2;
        c.hits += 1;
        if (c.state[i + 1] === 0) {
          c.state[i + 1] = 1;
          c.spin[i + 1] = Math.max(w * TRANSFER, 1);
        }
        w = 0;
      } else if (a >= FLAT) {
        a = FLAT;
        w = -w * BOUNCE;
        if (Math.abs(w) < 0.3) {
          w = 0;
          c.state[i] = 3;
        }
        c.hits += 1;
      }
      c.tilt[i] = a;
      c.spin[i] = w;
    } else if (state === 2) {
      // Nothing left to lean on: it falls the rest of the way.
      if (i + 1 < c.count) c.tilt[i] = Math.min(FLAT, leanOn(c, i, c.tilt[i + 1]));
      else c.state[i] = 1;
    } else if (state === 4) {
      const p = (c.time - c.riseAt[i]) / RISE_TIME;
      if (p >= 1) {
        c.tilt[i] = 0;
        c.state[i] = 0;
      } else if (p > 0) c.tilt[i] = c.riseFrom[i] * (1 - (c.gentle ? p * (2 - p) : easeOutBack(p)));
    }
  }
  // Drop the dominoes that have sunk away for good.
  if (c.count > 0 && c.sink[0] !== Infinity && c.time >= c.sink[c.count - 1] + SINK_TIME) c.count = 0;
}

/** Height a domino is drawn at (popping in from above), its scale and opacity. */
export function popAt(c: Chain, i: number) {
  'worklet';
  const p = (c.time - c.born[i]) / POP_TIME;
  let lift = 0;
  let scale = 1;
  let alpha = 1;
  if (p < 1) {
    const k = Math.max(0, p);
    const e = easeOutBack(k);
    lift = POP_DROP * (1 - k) * (1 - k);
    scale = 0.7 + 0.3 * e;
    alpha = Math.min(1, k * 3);
  }
  const q = (c.time - c.sink[i]) / SINK_TIME;
  if (q > 0) {
    const k = Math.min(1, q);
    scale *= 1 - 0.3 * k;
    alpha *= 1 - k;
    lift -= 6 * k;
  }
  return { lift, scale, alpha };
}

// ─── Paths ───────────────────────────────────────────────────────────────────────────────────────

/** Points of the autoPlay spiral on the table, from its outer end inwards (table units). */
export function spiralPoints(cx: number, cz: number, outer: number, inner: number, turns: number, start: number) {
  'worklet';
  const xs: number[] = [];
  const zs: number[] = [];
  const steps = 360;
  for (let k = 0; k <= steps; k += 1) {
    const t = k / steps;
    const r = outer + (inner - outer) * t;
    const a = start + t * turns * Math.PI * 2;
    xs.push(cx + Math.cos(a) * r);
    zs.push(cz + Math.sin(a) * r);
  }
  return { xs, zs };
}

/** Where along a polyline arc length `s` falls: the point and the heading there. */
export function pointAt(xs: number[], zs: number[], s: number) {
  'worklet';
  let left = s;
  for (let k = 1; k < xs.length; k += 1) {
    const dx = xs[k] - xs[k - 1];
    const dz = zs[k] - zs[k - 1];
    const len = Math.hypot(dx, dz);
    if (left <= len || k === xs.length - 1) {
      const u = len > 0 ? Math.min(1, left / len) : 0;
      return { x: xs[k - 1] + dx * u, z: zs[k - 1] + dz * u, hx: len > 0 ? dx / len : 1, hz: len > 0 ? dz / len : 0 };
    }
    left -= len;
  }
  return { x: xs[0], z: zs[0], hx: 1, hz: 0 };
}

/** Length of a polyline. */
export function lengthOf(xs: number[], zs: number[]) {
  'worklet';
  let total = 0;
  for (let k = 1; k < xs.length; k += 1) total += Math.hypot(xs[k] - xs[k - 1], zs[k] - zs[k - 1]);
  return total;
}

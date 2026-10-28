import { FX_S } from './HabitatMotion';

/**
 * Particles that outlive a signature move, with their physics in seconds (FX clock). Distances are
 * in `unit`s (points at a 344 pt creature). At this scale a metre is about 330 points: a leaf falls
 * at under a metre a second, as a real one does; drops fall at about half real gravity, a touch of
 * slow motion that keeps the spray readable.
 */

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}
function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

export const LEAVES = 12;
/** Gravity on a drop of water, in units per second². */
export const DROP_GRAVITY = 1500;

type LeafPath = { x: number; y: number; angle: number; roll: number };
/** A leaf's steady fall speed once its burst is spent. */
function leafFall(i: number, unit: number) {
  'worklet';
  return (210 + 70 * hash(i * 7.31 + 5)) * unit;
}

/**
 * One leaf thrown out of the bulb. A leaf is mostly drag: its burst dies away exponentially while it
 * settles to a terminal fall, and once slow it flutters like a pendulum: it swings side to side,
 * rises a little at each end of the swing, tilts into it and rolls on its long axis (2.5D). Its
 * spin from the burst decays into that flat, falling attitude.
 */
function leafPath(i: number, t: number, unit: number): LeafPath {
  'worklet';
  const h = (k: number) => hash(i * 7.31 + k);
  const launch = -Math.PI / 2 + ((i + 0.5) / LEAVES - 0.5) * 2.6 + (h(2) - 0.5) * 0.24;
  const speed = (220 + 90 * h(3)) * unit;
  const drag = 2.3 + 0.8 * h(4);
  const fall = leafFall(i, unit);
  const wind = (14 + 14 * h(6)) * unit;
  const omega = (Math.PI * 2) / (0.85 + 0.45 * h(7));
  const phase = Math.PI * 2 * h(8);
  const swing = (11 + 11 * h(9)) * unit;
  const decay = Math.exp(-drag * t);
  const spent = (1 - decay) / drag;
  const flutter = (1 - decay) * (1 - decay);
  const sway = omega * t + phase;
  const spin = (h(10) - 0.5) * 24;
  const tumble = (h(11) * Math.PI * 2) + spin * spent;
  // The burst's spin dies into a near-flat attitude (long axis across the fall).
  const flat = Math.round(tumble / Math.PI) * Math.PI;
  return {
    x: (Math.cos(launch) * speed - wind) * spent + wind * t + swing * flutter * Math.sin(sway),
    y: (Math.sin(launch) * speed - fall) * spent + fall * t + swing * 0.22 * flutter * Math.cos(2 * sway),
    angle: tumble + (flat - tumble) * flutter + 0.55 * flutter * Math.cos(sway),
    roll: (h(12) - 0.5) * 18 * spent + 0.75 * flutter * Math.sin(sway),
  };
}

/**
 * Every leaf for one frame: position and attitude (x, y, angle, roll) and its state (opacity, size,
 * how settled on the ground, layer: 0 behind the creature, 1 in front). A leaf lands on the floor
 * at its own depth, comes to rest flat in a quarter of a second, lies there, then fades.
 */
export function leafUniforms(seconds: number, originX: number, originY: number, floorY: number, unit: number) {
  'worklet';
  const leaf: number[] = [];
  const state: number[] = [];
  const ending = 1 - smooth(FX_S - 0.5, FX_S, seconds);
  for (let i = 0; i < LEAVES; i++) {
    const h = (k: number) => hash(i * 7.31 + k);
    const born = 0.56 + 0.022 * i + 0.02 * h(1);
    const age = seconds - born;
    if (age <= 0 || ending <= 0) { leaf.push(0, 0, 0, 0); state.push(0, 0, 0, 0); continue; }
    // Some fall behind the creature, the rest in front of it: the floor recedes for the far ones.
    const behind = h(13) < 0.4;
    const depth = (behind ? -(4 + 14 * h(14)) : 3 + 18 * h(14)) * unit;
    const ground = floorY + depth - originY;
    // First touch of the floor. Once the burst is spent the fall is close to steady, so Newton's
    // method from the steady-fall estimate converges in a few steps (the flutter never reverses it).
    const fall = leafFall(i, unit);
    let landed = Math.max(0.3, ground / fall);
    for (let k = 0; k < 5; k++) {
      const y = leafPath(i, landed, unit).y - ground;
      const slope = (leafPath(i, landed + 0.01, unit).y - ground - y) / 0.01;
      landed = Math.max(0.3, landed - y / Math.max(slope, fall * 0.2));
    }
    const scale = (1 + depth / (260 * unit)) * (0.62 + 0.38 * smooth(0, 0.14, age));
    if (age < landed) {
      const p = leafPath(i, age, unit);
      leaf.push(originX + p.x, originY + p.y, p.angle, p.roll);
      state.push(smooth(0, 0.06, age) * ending, scale, 0, behind ? 0 : 1);
      continue;
    }
    // On the floor: a short slide in the direction it was moving, then flat and still.
    const touch = leafPath(i, landed, unit);
    const after = leafPath(i, landed + 0.02, unit);
    const rest = 1 - Math.pow(1 - Math.min(1, (age - landed) / 0.26), 3);
    const lying = Math.round(touch.angle / Math.PI) * Math.PI + (h(15) - 0.5) * 0.5;
    const rock = 0.14 * Math.sin(rest * Math.PI * 2) * (1 - rest);
    const roll = touch.roll + ((Math.sign(Math.cos(touch.roll)) > 0 ? 1.05 : Math.PI - 1.05) - touch.roll) * rest;
    leaf.push(
      originX + touch.x + (after.x - touch.x) * 2.5 * rest,
      originY + ground,
      touch.angle + (lying - touch.angle) * rest + rock,
      roll,
    );
    state.push((1 - smooth(0.55, 1.05, age - landed)) * ending, scale, rest, behind ? 0 : 1);
  }
  return { leaf, leafState: state };
}

/*
 Warp Coin Welcome — the loop's timeline as pure functions of the loop time, and the touch state.
 Every worklet here is declared above the worklets that call it.
 */

export const LOOP = 7;
export const IDLE = 2;
/** One tunnel length per loop at rest. */
export const REST_SPEED = 1 / 7;
/** Extra speed at full warp: 9 extra tunnel lengths over the loop, spread by the warp curve (∫w = 3.45 s). */
export const BOOST = 9 / 3.45;

/** ln of the depth (1 at rest), world offset from the vanishing point at depth 1, angles in radians. */
export type Pose = {
  lnZ: number;
  x: number;
  y: number;
  pitch: number;
  roll: number;
  twist: number;
  spin: number;
  hop: number;
};

export type Frame = {
  /** Continuous loop phase for twinkle and hover (seconds, 0 ..< LOOP). */
  phase: number;
  /** Distance travelled through the tunnel (depth units; only its fraction matters). */
  travel: number;
  /** Signed speed in depth units per second. */
  speed: number;
  pose: Pose;
  glint: number;
  vanishShift: number;
  hover: number;
};

export type Touch = {
  touching: boolean;
  start: number;
  engageFrom: number;
  /** Clock of the last release, or -1. */
  release: number;
  /** Loop time = clock - base (wrapped). */
  base: number;
  /** Loop time held while the finger drives the scene. */
  heldStory: number;
  dragX: number;
  dragY: number;
  releaseX: number;
  releaseY: number;
  spinStart: number;
  anchorX: number;
  anchorY: number;
  moved: number;
  full: boolean;
  /** Travel bookkeeping: the last frame's travel, and the offset added to the loop's travel after a resume. */
  lastTravel: number;
  lastClock: number;
  lastStory: number;
  lastSpeed: number;
  travelOffset: number;
  resumeApplied: boolean;
};

export function initialTouch(): Touch {
  return {
    touching: false,
    start: 0,
    engageFrom: 0,
    release: -1,
    base: 0,
    heldStory: 0,
    dragX: 0,
    dragY: 0,
    releaseX: 0,
    releaseY: 0,
    spinStart: -10,
    anchorX: 0,
    anchorY: 0,
    moved: 0,
    full: false,
    lastTravel: 0,
    lastClock: -1,
    lastStory: 0,
    lastSpeed: REST_SPEED,
    travelOffset: 0,
    resumeApplied: true,
  };
}

export function restPose(): Pose {
  'worklet';
  return { lnZ: 0, x: 0, y: 162, pitch: 1.13, roll: 0, twist: 0, spin: 0, hop: 0 };
}

export function restFrame(): Frame {
  'worklet';
  return { phase: 0, travel: 0, speed: REST_SPEED, pose: restPose(), glint: 0, vanishShift: 0, hover: 0 };
}

export function vanishY(height: number) {
  'worklet';
  return 0.45 * height;
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return 1 - Math.pow(1 - u, 3);
}

export function easeIn(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

export function smoother(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * u * (u * (u * 6 - 15) + 10);
}

export function wrap(t: number) {
  'worklet';
  const r = t % LOOP;
  return r < 0 ? r + LOOP : r;
}

/** Warp level: smoothstep up over 0.8–2.0 s, full until 3.9 s, smoothstep down over 3.9–5.8 s. */
export function warpLevel(t: number) {
  'worklet';
  if (t < 0.8 || t >= 5.8) return 0;
  if (t < 2.0) return smooth((t - 0.8) / 1.2);
  if (t < 3.9) return 1;
  return 1 - smooth((t - 3.9) / 1.9);
}

function smoothArea(x: number) {
  'worklet';
  return x * x * x - (x * x * x * x) / 2;
}

/** ∫₀ᵗ warp: the closed form of the smoothstep's integral is x³ − x⁴/2. */
export function warpArea(t: number) {
  'worklet';
  if (t < 0.8) return 0;
  if (t < 2.0) return 1.2 * smoothArea((t - 0.8) / 1.2);
  if (t < 3.9) return 0.6 + (t - 2.0);
  if (t < 5.8) {
    const x = (t - 3.9) / 1.9;
    return 2.5 + 1.9 * (x - smoothArea(x));
  }
  return 3.45;
}

/** Travel over one loop is exactly 10 tunnel lengths, so every particle is back where it started. */
export function travelAt(t: number) {
  'worklet';
  return REST_SPEED * t + BOOST * warpArea(t);
}

export function speedAt(t: number) {
  'worklet';
  return REST_SPEED + BOOST * warpLevel(t);
}

/** Step response of a spring (damping ratio 0.62, period 1.0 s) from 1 to 0, forced to 0 after 1.8 s. */
export function settle(tau: number) {
  'worklet';
  if (tau <= 0) return 1;
  const zeta = 0.62;
  const wn = 2 * Math.PI;
  const wd = wn * Math.sqrt(1 - zeta * zeta);
  const decay = zeta * wn;
  const v = Math.exp(-decay * tau) * (Math.cos(wd * tau) + (decay / wd) * Math.sin(wd * tau));
  return v * (1 - smooth((tau - 1.4) / 0.4));
}

export function poseAt(t: number): Pose {
  'worklet';
  const far = Math.log(9);
  const dip = Math.log(0.93);
  const close = Math.log(0.62);
  const p = restPose();
  if (t < 2.65) {
    const a = easeOut(seg(t, 0.85, 1.15));
    const f = easeIn(seg(t, 1.15, 2.25));
    const h = easeInOut(seg(t, 2.25, 2.65));
    p.lnZ = dip * a + (far - dip) * f;
    p.pitch = 1.13 - 0.12 * a + 0.1 * f;
    p.roll = 0.25 * f;
    p.twist = -0.9 * f + 1.8 * h;
  } else if (t < 3.6) {
    const r = smoother(seg(t, 2.65, 3.6));
    p.lnZ = far + (close - far) * r;
    p.x = -50 * r;
    p.y = 162 - 52 * r;
    p.pitch = 1.11 + 0.15 * r;
    p.roll = 0.25 - 0.53 * r;
    p.twist = 0.9 - 0.55 * r;
  } else {
    const s = settle(t - 3.6);
    p.lnZ = close * s;
    p.x = -50 * s;
    p.y = 162 - 52 * s;
    p.pitch = 1.13 + 0.13 * s;
    p.roll = -0.28 * s;
    p.twist = 0.35 * s;
  }
  return p;
}

export function glintAt(t: number) {
  'worklet';
  if (t <= 2.05 || t >= 2.95) return 0;
  const s = Math.sin((Math.PI * (t - 2.05)) / 0.9);
  return s * s;
}

/** Warp control from the vertical drag: up is faster (1 = full warp), down drifts back (to −0.5). */
export function control(dragY: number) {
  'worklet';
  const c = -dragY / 240;
  if (c > 1) {
    const over = c - 1;
    return 1 + (over / (1 + over * 2)) * 0.5;
  }
  return Math.max(-0.5, c);
}

export function controlPose(dx: number, dy: number): Pose {
  'worklet';
  const c = control(dy);
  const p = restPose();
  p.lnZ = Math.log(1 + 0.25 * Math.max(c, 0));
  p.x = dx * 0.15;
  p.pitch = 1.13 - 0.22 * c;
  p.roll = Math.min(Math.max(dx / 300, -1), 1) * 0.35;
  return p;
}

export function controlSpeed(dy: number) {
  'worklet';
  const c = control(dy);
  return c >= 0 ? REST_SPEED + BOOST * c : REST_SPEED + 0.8 * c;
}

function mixPose(a: Pose, b: Pose, k: number): Pose {
  'worklet';
  return {
    lnZ: a.lnZ + (b.lnZ - a.lnZ) * k,
    x: a.x + (b.x - a.x) * k,
    y: a.y + (b.y - a.y) * k,
    pitch: a.pitch + (b.pitch - a.pitch) * k,
    roll: a.roll + (b.roll - a.roll) * k,
    twist: a.twist + (b.twist - a.twist) * k,
    spin: a.spin + (b.spin - a.spin) * k,
    hop: a.hop + (b.hop - a.hop) * k,
  };
}

function held(t: Touch, clock: number) {
  'worklet';
  return t.touching || (t.release >= 0 && clock < t.release + IDLE);
}

/** 0 → 1 blend from the loop's pose to the finger's control pose. */
export function engageAt(t: Touch, clock: number) {
  'worklet';
  if (t.touching) return t.engageFrom + (1 - t.engageFrom) * easeOut((clock - t.start) / 0.45);
  if (t.release < 0) return 0;
  const resume = t.release + IDLE;
  if (clock < resume) return t.engageFrom + (1 - t.engageFrom) * easeOut((clock - t.start) / 0.45);
  return 1 - easeOut((clock - resume) / 0.45);
}

/** The drag the control pose follows: live while touching, then a critically damped return to zero. */
function dragDecay(t: Touch, clock: number) {
  'worklet';
  if (t.touching) return 1;
  if (t.release < 0) return 0;
  const age = Math.max(0, clock - t.release);
  const tau = 0.16;
  return (1 + age / tau) * Math.exp(-age / tau);
}

export function beginTouch(t: Touch, x: number, y: number, clock: number): Touch {
  'worklet';
  const current = engageAt(t, clock);
  return {
    ...t,
    heldStory: held(t, clock) ? t.heldStory : t.lastStory,
    engageFrom: current,
    touching: true,
    start: clock,
    release: -1,
    resumeApplied: false,
    anchorX: x,
    anchorY: y,
    moved: 0,
    dragX: 0,
    dragY: 0,
    full: false,
  };
}

/** `full` is true on the move where the drag passes full warp, for a haptic. */
export function moveTouch(t: Touch, x: number, y: number): { next: Touch; full: boolean } {
  'worklet';
  const dragX = x - t.anchorX;
  const dragY = y - t.anchorY;
  const moved = Math.max(t.moved, Math.hypot(dragX, dragY));
  const c = control(dragY);
  let full = t.full;
  let crossed = false;
  if (c > 0.9 && !full) {
    full = true;
    crossed = true;
  } else if (c < 0.7) {
    full = false;
  }
  return { next: { ...t, dragX, dragY, moved, full }, full: crossed };
}

/** `tapped` is true when the touch was a tap on the coin (it spins). */
export function endTouch(t: Touch, clock: number, coinY: number): { next: Touch; tapped: boolean } {
  'worklet';
  const tap =
    t.moved < 10 && clock - t.start < 0.35 && Math.abs(t.anchorX - 196.5) < 150 && Math.abs(t.anchorY - coinY) < 95;
  const tapped = tap && clock - t.spinStart > 1.0;
  return {
    next: {
      ...t,
      touching: false,
      release: clock,
      releaseX: t.dragX,
      releaseY: t.dragY,
      spinStart: tapped ? clock : t.spinStart,
    },
    tapped,
  };
}

/** One frame from the clock (seconds since the screen appeared) and the touch state, with the updated state. */
export function stepFrame(clock: number, t: Touch, autoPlay: boolean): { frame: Frame; next: Touch } {
  'worklet';
  const next = { ...t };
  const engage = engageAt(t, clock);
  const decay = dragDecay(t, clock);
  const dx = (t.touching ? t.dragX : t.releaseX) * decay;
  const dy = (t.touching ? t.dragY : t.releaseY) * decay;
  const isHeld = held(t, clock);

  let story = 0;
  if (isHeld) {
    story = t.heldStory;
  } else {
    if (t.release >= 0 && !t.resumeApplied) {
      // Resume: the loop restarts from rest, and its travel continues from where the finger left it.
      next.base = t.release + IDLE;
      next.resumeApplied = true;
      const step = t.lastClock < 0 ? 0 : t.lastSpeed * Math.min(0.1, Math.max(0, clock - t.lastClock));
      next.travelOffset = t.lastTravel + step - travelAt(wrap(clock - next.base));
    }
    story = autoPlay ? wrap(clock - next.base) : 0;
  }

  let pose = poseAt(story);
  let speed = speedAt(story);
  if (engage > 0) {
    pose = mixPose(pose, controlPose(dx, dy), engage);
    speed += (controlSpeed(dy) - speed) * engage;
  }
  const u = (clock - t.spinStart) / 1.0;
  if (u > 0 && u < 1) {
    pose.spin += 2 * Math.PI * easeInOut(u);
    pose.hop += 14 * Math.sin(Math.PI * u);
  }

  let travel = 0;
  if (isHeld) {
    const dt = t.lastClock < 0 ? 0 : Math.min(0.1, Math.max(0, clock - t.lastClock));
    travel = t.lastTravel + speed * dt;
  } else {
    travel = travelAt(story) + next.travelOffset;
  }
  next.lastTravel = travel;
  next.lastClock = clock;
  next.lastStory = story;
  next.lastSpeed = speed;

  return {
    frame: {
      phase: wrap(clock),
      travel,
      speed,
      pose,
      glint: glintAt(story) * (1 - engage),
      vanishShift: -dx * 0.12 * engage,
      hover: 1,
    },
    next,
  };
}

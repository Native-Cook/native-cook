/*
 Timing and touch model of Petal Glass Scenes, ported from the Swift reference
 (PetalGlassScenesMotion.swift). Every value is a pure function of the loop time (autoPlay) or of
 the clock since a touch event, so the loop's last frame equals its first. Every function is a
 worklet; helpers are declared above their callers.
 */

export const LOOP = 9.6;
export const SCENES = 3;
/** Each scene owns 3.2 s of the loop: it rests, then the next one grows out of the glass. */
export const SCENE_LENGTH = 3.2;
export const CHANGE_AT = 1.8;
export const CHANGE_LENGTH = 1.4;
/** Reduced motion: the scenes cross-fade. */
export const FADE_LENGTH = 0.4;

/** What one frame shows: scene `from` lies under, scene `to` grows out of the card (p 0 … 1). */
export type Frame = {
  from: number;
  to: number;
  p: number;
  theta: number; // drift phase, 2π per loop
  fade: number; // cross-fade 0 … 1 instead of the front (negative: none)
  offsetX: number; // the card's drag offset, units
  offsetY: number;
};

export type Fx = { alpha: number; shift: number; blur: number };

// Curves

export function mod(a: number, b: number) {
  'worklet';
  const r = a - b * Math.floor(a / b);
  return r < 0 ? r + b : r;
}

export function clamp01(v: number) {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Wraps an angle to −π … π. */
export function wrapAngle(a: number) {
  'worklet';
  return mod(a + Math.PI, 2 * Math.PI) - Math.PI;
}

// The scene change (p = 0 … 1 over 1.4 s)

/**
 Where the front is, in units from the card's edge: from the card's centre (−124) to its rim, a
 short hold there, then out past the farthest corner of the screen.
 */
export function front(p: number, reach: number) {
  'worklet';
  if (p < 0.26) return -124 + 124 * easeOutCubic(p / 0.26);
  if (p < 0.36) return 0;
  const x = seg(p, 0.36, 1);
  return reach * x * x * (3 - 2 * x);
}

/** Strength of the lens bulge along the front. */
export function bulge(p: number) {
  'worklet';
  return Math.sin(Math.PI * clamp01(p));
}

/** Amplitude of the glass's liquid warp, units. */
export function warp(p: number) {
  'worklet';
  return 9 + 16 * Math.sin(Math.PI * clamp01(p));
}

/** The card gives a little as the front reaches its rim, then swells as it leaves. */
export function cardScale(p: number) {
  'worklet';
  return 1 - 0.03 * Math.sin(Math.PI * seg(p, 0.06, 0.44)) + 0.012 * Math.sin(Math.PI * seg(p, 0.44, 1));
}

/** The text of the scene going out. */
export function textOut(p: number, delay: number): Fx {
  'worklet';
  const e = easeInCubic(seg(p, 0.24 + delay, 0.4 + delay));
  return { alpha: 1 - e, shift: 18 * e, blur: 6 * e };
}

/** The text of the scene coming in. */
export function textIn(p: number, delay: number): Fx {
  'worklet';
  const e = easeOutCubic(seg(p, 0.4 + delay, 0.72 + delay));
  return { alpha: e, shift: -18 * (1 - e), blur: 6 * (1 - e) };
}

function still(scene: number): Frame {
  'worklet';
  return { from: scene, to: scene, p: 0, theta: 0, fade: -1, offsetX: 0, offsetY: 0 };
}

/** The loop's frame at loop time t. */
export function autoFrame(t: number): Frame {
  'worklet';
  const time = mod(t, LOOP);
  const k = Math.min(Math.floor(time / SCENE_LENGTH), SCENES - 1);
  const u = time - k * SCENE_LENGTH;
  return {
    from: k,
    to: (k + 1) % SCENES,
    p: seg(u, CHANGE_AT, CHANGE_AT + CHANGE_LENGTH),
    theta: (2 * Math.PI * time) / LOOP,
    fade: -1,
    offsetX: 0,
    offsetY: 0,
  };
}

// The card's drag

/** Rubber band: the card follows the finger less and less. */
export function rubber(x: number) {
  'worklet';
  return x / (1 + Math.abs(x) / 180);
}

export function unrubber(o: number) {
  'worklet';
  const a = Math.min(Math.abs(o), 170);
  return ((o < 0 ? -1 : 1) * a) / (1 - a / 180);
}

export function rubberSlope(x: number) {
  'worklet';
  return 1 / Math.pow(1 + Math.abs(x) / 180, 2);
}

/** A released spring (response 0.5 s, damping 0.72) from x0 moving at v0. */
export function spring(tau: number, x0: number, v0: number) {
  'worklet';
  if (tau <= 0) return x0;
  const z = 0.72;
  const w = (2 * Math.PI) / 0.5;
  const wd = w * Math.sqrt(1 - z * z);
  return Math.exp(-z * w * tau) * (x0 * Math.cos(wd * tau) + ((v0 + z * w * x0) / wd) * Math.sin(wd * tau));
}

// Touch

/**
 What the finger did. Auto → touch makes it manual; 2 s after the last touch (and after the scene
 change it started) the loop resumes at the shown scene's rest, its drift eased back.
 */
export type Touch = {
  manual: boolean;
  loopStart: number;
  resumedAt: number;
  thetaLag: number;
  scene: number;
  changeFrom: number;
  changeStart: number;
  fading: boolean;
  driftBase: number;
  manualSince: number;
  touching: boolean;
  lastTouch: number;
  dragX: number;
  dragY: number;
  baseX: number;
  baseY: number;
  releasedAt: number;
  releaseX: number;
  releaseY: number;
  releaseVx: number;
  releaseVy: number;
  haptic: number;
  hapticTaken: number;
};

export function initialTouch(): Touch {
  'worklet';
  return {
    manual: false,
    loopStart: 0,
    resumedAt: -100,
    thetaLag: 0,
    scene: 0,
    changeFrom: 0,
    changeStart: -100,
    fading: false,
    driftBase: 0,
    manualSince: 0,
    touching: false,
    lastTouch: -100,
    dragX: 0,
    dragY: 0,
    baseX: 0,
    baseY: 0,
    releasedAt: -100,
    releaseX: 0,
    releaseY: 0,
    releaseVx: 0,
    releaseVy: 0,
    haptic: -100,
    hapticTaken: -100,
  };
}

function changeEnd(s: Touch) {
  'worklet';
  return s.changeStart + (s.fading ? FADE_LENGTH : CHANGE_LENGTH);
}

export function idleUntil(s: Touch) {
  'worklet';
  return Math.max(s.lastTouch, changeEnd(s)) + 2.2;
}

/** The state with a pending resume applied. */
export function resolved(s: Touch, clock: number, autoPlay: boolean): Touch {
  'worklet';
  if (!s.manual || !autoPlay || s.touching) return s;
  const at = Math.max(s.lastTouch, changeEnd(s)) + 2;
  if (clock < at) return s;
  const manualTheta = (2 * Math.PI * (s.driftBase + at - s.manualSince)) / LOOP;
  const loopTheta = (2 * Math.PI * s.scene * SCENE_LENGTH) / LOOP;
  return {
    ...s,
    manual: false,
    loopStart: at - s.scene * SCENE_LENGTH,
    resumedAt: at,
    thetaLag: wrapAngle(manualTheta - loopTheta),
  };
}

export function offsetX(s: Touch, clock: number) {
  'worklet';
  return s.touching ? rubber(s.dragX) : spring(clock - s.releasedAt, s.releaseX, s.releaseVx);
}

export function offsetY(s: Touch, clock: number) {
  'worklet';
  return s.touching ? rubber(s.dragY) : spring(clock - s.releasedAt, s.releaseY, s.releaseVy);
}

export function touchFrame(t: Touch, clock: number, autoPlay: boolean, reduced: boolean): Frame {
  'worklet';
  const s = resolved(t, clock, autoPlay);
  let f: Frame;
  if (reduced) {
    f = still(s.scene);
    const p = seg(clock, s.changeStart, s.changeStart + FADE_LENGTH);
    if (p < 1) {
      f.from = s.changeFrom;
      f.p = p;
      f.fade = easeInOutCubic(p);
    }
    return f;
  }
  if (!s.manual && autoPlay) {
    f = autoFrame(clock - s.loopStart);
    const ease = easeInOutCubic(seg(clock, s.resumedAt, s.resumedAt + 1.2));
    f.theta += s.thetaLag * (1 - ease);
  } else {
    f = still(s.scene);
    const p = seg(clock, s.changeStart, s.changeStart + CHANGE_LENGTH);
    if (p < 1) {
      f.from = s.changeFrom;
      f.p = p;
    }
    f.theta = (2 * Math.PI * (s.driftBase + clock - s.manualSince)) / LOOP;
  }
  f.offsetX = offsetX(s, clock);
  f.offsetY = offsetY(s, clock);
  return f;
}

export function beginTouch(t: Touch, clock: number, autoPlay: boolean, reduced: boolean): Touch {
  'worklet';
  const f = touchFrame(t, clock, autoPlay, reduced);
  const ox = offsetX(t, clock);
  const oy = offsetY(t, clock);
  const s = { ...resolved(t, clock, autoPlay) };
  if (!s.manual && autoPlay && !reduced) {
    // Take over the loop where it is, a scene change in progress included.
    s.manual = true;
    s.driftBase = (f.theta / (2 * Math.PI)) * LOOP;
    s.manualSince = clock;
    if (f.p > 0) {
      s.changeFrom = f.from;
      s.scene = f.to;
      s.changeStart = clock - f.p * CHANGE_LENGTH;
      s.fading = false;
    } else {
      s.scene = f.from;
      s.changeStart = -100;
    }
  } else if (!s.manual) {
    s.manual = true;
    s.driftBase = clock;
    s.manualSince = clock;
  }
  s.touching = true;
  // Pick the card up where the spring left it.
  s.baseX = unrubber(ox);
  s.baseY = unrubber(oy);
  s.dragX = s.baseX;
  s.dragY = s.baseY;
  s.lastTouch = clock;
  return s;
}

export function moveTouch(t: Touch, clock: number, dx: number, dy: number): Touch {
  'worklet';
  return { ...t, dragX: t.baseX + dx, dragY: t.baseY + dy, lastTouch: clock };
}

/** Ends a drag; a horizontal release over 80 units or 650 units/s changes the scene. */
export function endTouch(t: Touch, clock: number, dx: number, dy: number, vx: number, vy: number, reduced: boolean): Touch {
  'worklet';
  const s = { ...t };
  s.touching = false;
  s.lastTouch = clock;
  s.releasedAt = clock;
  s.releaseX = rubber(t.dragX);
  s.releaseY = rubber(t.dragY);
  s.releaseVx = Math.max(-1500, Math.min(1500, vx * rubberSlope(t.dragX)));
  s.releaseVy = Math.max(-1500, Math.min(1500, vy * rubberSlope(t.dragY)));
  const horizontal = Math.abs(dx) > Math.abs(dy);
  const busy = clock < t.changeStart + (t.fading ? FADE_LENGTH : CHANGE_LENGTH) * 0.6;
  if (!horizontal || busy || !(Math.abs(dx) > 80 || Math.abs(vx) > 650)) return s;
  const step = (Math.abs(dx) > 80 ? dx : vx) < 0 ? 1 : SCENES - 1;
  s.changeFrom = t.scene;
  s.scene = (t.scene + step) % SCENES;
  s.changeStart = clock;
  s.fading = reduced;
  s.haptic = clock;
  return s;
}

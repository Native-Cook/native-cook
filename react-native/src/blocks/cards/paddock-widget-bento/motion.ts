/**
 * Pure geometry and the analytic 9-second demo for the three equal quick cards (Swift: PaddockWidgetBentoMotion).
 * Cards are addressed by stable index: 0 laps, 1 reflex, 2 homeCards. Every function the UI thread calls is a
 * worklet and only calls worklets declared above it (a forward call is `undefined` on the UI runtime).
 */
export const LOOP = 9;
/** Tops of the three quick-card slots on the 402-point screen (pin: 54.3, 128.3, 202.7). */
export const SLOT_Y = [54.5, 128.5, 202.5];
export const LONG_PRESS = 0.32;
export const TARGET_HYSTERESIS = 8;
export const OUTSIDE_CANCEL_DISTANCE = 44;
export const DEFAULT_ORDER = [0, 1, 2];
export const REFLOW_STIFFNESS = 360;
export const REFLOW_DAMPING = 28;
export const REFLOW_MASS = 0.9;
export const SETTLE_STIFFNESS = 420;
export const SETTLE_DAMPING = 30;
export const SETTLE_MASS = 0.8;
export const SPRING_SETTLE_DURATION = 0.6;
export const QUIET_REFLOW_DURATION = 0.12;
export const LIFT_SCALE = 1.025;
export const LIFT_SHADOW = 14;
export const REST_SHADOW = 4;
export const MAX_ROTATION = 0.7;
export const ROTATION_PER_POINT = 0.012;
export const CARD_COUNT = 3;
/** Restart delay after a manual edit or a short press. */
export const RESTART_DELAY = 2;
/**
 * The demo's timeline, in seconds of the loop: the pit stop (0.55–2.04), the sticker peel (2.45–4.35), the quick-card
 * rearrangement (4.75–7.45), then rest until 9 (23 % of the loop at rest, counting 0–0.55).
 */
export const TIMELINE = {
  editStart: 4.75,
  editEnd: 5.0,
  reflowEnd: 5.8,
  settleEnd: 6.1,
  holdEnd: 6.3,
  returnEnd: 7.1,
  restEnd: 7.45,
  curve: [0.32, 0.72, 0, 1],
} as const;

/** Pit-stop tiles in reading order; the quick cards keep their stable ids 0–2. */
export const TILE = { violet: 0, orange: 1, yellow: 2, pitcast: 3, stats: 4, glossary: 5, education: 6, hero: 7, guide: 8, banner: 9 } as const;
export const TILE_COUNT = 10;

/**
 * The pit stop: the bento braces (air guns ready), every tile is flung off to its own side, and after a beat the crew
 * slams them back in one by one, each on its own launched spring (2 % overshoot), stretched along its travel by its
 * speed and squashed by the stop, with a colour smear behind it. Seconds after the pit stop starts; points.
 */
export const PIT = {
  loopStart: 0.55,
  brace: 0.14,
  braceScale: 0.965,
  exit: 0.24,
  exitPower: 2.2,
  gap: 0.1,
  /** Arrival spring: natural frequency (rad/s), damping ratio, launch speed in units of omega × distance. */
  omega: 17,
  zeta: 0.85,
  launch: 0.9,
  settle: 0.62,
  /** A tile leaves and returns this far beyond the screen edge. */
  margin: 46,
  width: [186, 186, 186, 186, 382, 186, 186, 382, 382, 382],
  delay: [0, 0.07, 0.14, 0.03, 0.1, 0.19, 0.21, 0.27, 0.33, 0.39],
  side: [-1, -1, -1, 1, 1, -1, 1, -1, 1, -1],
  rise: [-26, 0, 22, -34, 10, 16, -16, 24, -20, 40],
  tilt: [-6, -5, -6, 5, 3, -5, 5, -3, 3, -2.5],
  stretchPerSpeed: 1 / 22000,
  maxStretch: 0.18,
  squashPerAccel: 1 / 700000,
  maxSquash: 0.08,
  ghosts: 4,
  ghostStep: 0.016,
  ghostAlpha: 0.55,
  ghostSpeed: 2400,
} as const;
export const PIT_ARRIVE = PIT.brace + PIT.exit + PIT.gap;
export const PIT_DURATION = PIT_ARRIVE + Math.max(...PIT.delay) + PIT.settle;
export const PIT_WINDOW = [PIT.loopStart, PIT.loopStart + PIT_DURATION];

/**
 * "exclusive widgets" is a die-cut sticker. Its bottom-right corner lifts, the fold line sweeps up-left (the part past
 * it flips over, showing the sticker's paper back), flutters, then slaps back down: a press, a shake and impact lines.
 * The fold is the perpendicular bisector of the corner and where the corner has been pulled to.
 */
export const PEEL = {
  start: 2.45,
  tease: 2.62,
  full: 3.22,
  hold: 3.55,
  snap: 3.8,
  end: 4.35,
  /** Corner of the lettering the peel starts from, hero-card-local points; pull direction in degrees. */
  cornerX: 324,
  cornerY: 124,
  angle: -153.4,
  teaseDistance: 26,
  maxDistance: 236,
  touchMaxDistance: 290,
  flutter: 7,
  flutterHz: 2.6,
  wobble: 4,
  /** A released finger: the corner falls back in this long, accelerating, then slaps. */
  release: 0.24,
  slapPress: 0.03,
  slapPressTime: 0.16,
  shake: 2.2,
  shakeHz: 16,
  shakeDecay: 0.07,
  slapEnd: 0.55,
  lines: 7,
  lineInner: 10,
  lineReach: 26,
  lineLength: 12,
  lineTime: 0.3,
  /** The paper back is darkest at the fold and reaches full paper colour this far from it. */
  foldShade: 34,
  /** The die-cut margin around the letters (points): the sticker's outline. */
  border: 6,
  shadowBlur: 5,
  shadowOffset: 5,
  /** At rest the corner stays lifted this far (points): a small curl that says the sticker can be peeled. */
  hint: 16,
} as const;

/** Edit-mode jiggle of the quick cards that are not held: degrees and hertz, alternating direction per card. */
export const JIGGLE = { amplitude: 1.2, hz: 5.6, phase: 2.1 } as const;
/** Gooey neighbours: the three capsules blurred and alpha-thresholded under the cards, so bridges show between them. */
export const GOO = { blur: 6.5, gain: 18, bias: 8, pad: 28 } as const;
/** Liquid Glass dock lens. */
export const DOCK = {
  lens: { duration: 420, dampingRatio: 0.72 },
  stretch: 1.16,
  stretchMs: 110,
  settle: { duration: 320, dampingRatio: 0.6 },
  press: 0.92,
  pressMs: 110,
} as const;
/**
 * Pulling the feed this far past its top and letting go runs the pit stop (a Gesture Handler pan on both platforms).
 * A flick down of `flick` pt/s is enough from `flickShare` of the distance. Android has no overscroll, so the feed
 * follows the finger with rubber-band resistance (`rubber`: pt of travel at which the pull approaches its limit) and
 * springs back on release.
 */
export const PULL = { trigger: 72, flick: 900, flickShare: 0.5, rubber: 260, back: { duration: 400, dampingRatio: 1 } } as const;

/** Time ranges (seconds) in which the demo needs 60 Hz frames. */
export const ACTIVE_RANGES = [PIT_WINDOW, [PEEL.start, PEEL.end], [TIMELINE.editStart, TIMELINE.restEnd]];
export const PHASE = { rest: 0, editCue: 2, reflow: 3, previewHold: 4, returnReflow: 5, settle: 6, pitStop: 7, peel: 8 } as const;
/** What the UI thread evaluates: nothing moves, the analytic demo, or a gesture / transition owned by the controller. */
export const KIND = { rest: 0, demo: 1, live: 2 } as const;

export type Frame = {
  phase: number;
  order: number[];
  y: number[];
  opacity: number[];
  handles: number;
  selected: number;
  scale: number;
  shadow: number;
  rotation: number;
  isDemo: boolean;
  /** Seconds into the loop while the demo plays, -1 otherwise: the pit stop, peel and jiggle read it. */
  loopTime: number;
};

export type Transition = {
  from: (number | null)[];
  to: (number | null)[];
  velocity: (number | null)[];
  startedAt: number;
  duration: number;
  stiffness: number;
  damping: number;
  mass: number;
  quietFade: boolean;
  selected: number;
  scaleFrom: number;
  shadowFrom: number;
  rotationFrom: number;
};

export type DragPlan = { id: number; pointerOffset: number; candidate: number; quiet: boolean };

export type Plan = {
  kind: number;
  anchor: number;
  order: number[];
  base: Frame;
  tr: Transition | null;
  drag: DragPlan | null;
};

export const VALID_ORDERS = [
  [0, 1, 2],
  [0, 2, 1],
  [1, 0, 2],
  [1, 2, 0],
  [2, 0, 1],
  [2, 1, 0],
];

export function isValid(order: number[]) {
  'worklet';
  return order.length === 3 && order.indexOf(0) >= 0 && order.indexOf(1) >= 0 && order.indexOf(2) >= 0;
}

export function clamp(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

export function smooth(value: number) {
  'worklet';
  const t = clamp(value);
  return t * t * (3 - 2 * t);
}

function bezierCoordinate(t: number, a: number, b: number) {
  'worklet';
  const u = 1 - t;
  return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
}

export function cubicBezier(value: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp(value);
  let low = 0;
  let high = 1;
  let t = x;
  for (let step = 0; step < 14; step++) {
    const current = bezierCoordinate(t, x1, x2);
    if (Math.abs(current - x) < 0.00001) break;
    if (current < x) low = t;
    else high = t;
    t = (low + high) * 0.5;
  }
  return bezierCoordinate(t, y1, y2);
}

/** Positions of the cards when `order` fills the three slots top to bottom. */
export function slotPositions(order: number[]) {
  'worklet';
  const result = [SLOT_Y[0], SLOT_Y[0], SLOT_Y[0]];
  for (let slot = 0; slot < order.length; slot++) result[order[slot]] = SLOT_Y[slot];
  return result;
}

/** The demonstration moves the last card to the first slot. */
export function previewOrder(order: number[]) {
  'worklet';
  if (!isValid(order)) return DEFAULT_ORDER.slice();
  return [order[2], order[0], order[1]];
}

export function restFrame(order: number[]): Frame {
  'worklet';
  const valid = isValid(order) ? order : DEFAULT_ORDER;
  return {
    phase: PHASE.rest,
    order: valid.slice(),
    y: slotPositions(valid),
    opacity: [1, 1, 1],
    handles: 0,
    selected: -1,
    scale: 1,
    shadow: REST_SHADOW,
    rotation: 0,
    isDemo: false,
    loopTime: -1,
  };
}

function interpolatePositions(from: number[], to: number[], progress: number) {
  'worklet';
  return [from[0] + (to[0] - from[0]) * progress, from[1] + (to[1] - from[1]) * progress, from[2] + (to[2] - from[2]) * progress];
}

/** The analytic demo: the held card is the last one in `order`. */
export function sample(elapsed: number, order: number[]): Frame {
  'worklet';
  const valid = isValid(order) ? order : DEFAULT_ORDER;
  const t = elapsed % LOOP;
  const selected = valid[2];
  const preview = previewOrder(valid);
  const initialY = slotPositions(valid);
  const previewY = slotPositions(preview);
  const k = TIMELINE;
  let phase: number = PHASE.rest;
  let positions = initialY;
  let handles = 0;
  let scale = 1;
  let shadow = REST_SHADOW;
  let rotation = 0;

  if (t >= PIT_WINDOW[0] && t < PIT_WINDOW[1]) {
    phase = PHASE.pitStop;
  } else if (t >= PEEL.start && t < PEEL.end) {
    phase = PHASE.peel;
  } else if (t >= k.editStart && t < k.editEnd) {
    phase = PHASE.editCue;
    const p = smooth((t - k.editStart) / (k.editEnd - k.editStart));
    handles = p;
    scale = 1 + (LIFT_SCALE - 1) * p;
    shadow = REST_SHADOW + (LIFT_SHADOW - REST_SHADOW) * p;
    rotation = MAX_ROTATION * p;
  } else if (t >= k.editEnd && t < k.reflowEnd) {
    phase = PHASE.reflow;
    const p = cubicBezier((t - k.editEnd) / (k.reflowEnd - k.editEnd), k.curve[0], k.curve[1], k.curve[2], k.curve[3]);
    positions = interpolatePositions(initialY, previewY, p);
    handles = 1;
    scale = LIFT_SCALE;
    shadow = LIFT_SHADOW;
    rotation = MAX_ROTATION * (1 - p);
  } else if (t >= k.reflowEnd && t < k.settleEnd) {
    phase = PHASE.settle;
    positions = previewY;
    const p = smooth((t - k.reflowEnd) / (k.settleEnd - k.reflowEnd));
    handles = 1;
    scale = LIFT_SCALE - (LIFT_SCALE - 1) * p;
    shadow = LIFT_SHADOW - (LIFT_SHADOW - REST_SHADOW) * p;
  } else if (t >= k.settleEnd && t < k.holdEnd) {
    phase = PHASE.previewHold;
    positions = previewY;
    handles = 1;
  } else if (t >= k.holdEnd && t < k.returnEnd) {
    phase = PHASE.returnReflow;
    const p = cubicBezier((t - k.holdEnd) / (k.returnEnd - k.holdEnd), k.curve[0], k.curve[1], k.curve[2], k.curve[3]);
    positions = interpolatePositions(previewY, initialY, p);
    handles = 1;
    scale = LIFT_SCALE - (LIFT_SCALE - 1) * p;
    shadow = LIFT_SHADOW - (LIFT_SHADOW - REST_SHADOW) * p;
  } else if (t >= k.returnEnd && t < k.restEnd) {
    phase = PHASE.settle;
    positions = initialY;
    const p = smooth((t - k.returnEnd) / (k.restEnd - k.returnEnd));
    handles = 1 - p;
    scale = 1;
    shadow = REST_SHADOW;
  }

  const showsPreview = phase === PHASE.reflow || (phase === PHASE.settle && t < k.settleEnd) || phase === PHASE.previewHold || phase === PHASE.returnReflow;
  const holdsCard = phase === PHASE.editCue || showsPreview;
  return {
    phase,
    order: showsPreview ? preview : valid.slice(),
    y: positions,
    opacity: [1, 1, 1],
    handles,
    selected: holdsCard ? selected : -1,
    scale,
    shadow,
    rotation,
    isDemo: true,
    loopTime: t,
  };
}

function springValue(from: number, to: number, velocity: number, elapsed: number, stiffness: number, damping: number, mass: number) {
  'worklet';
  const omega = Math.sqrt(stiffness / mass);
  const zeta = damping / (2 * Math.sqrt(stiffness * mass));
  const displacement = from - to;
  if (zeta < 1) {
    const damped = omega * Math.sqrt(1 - zeta * zeta);
    const coefficient = (velocity + zeta * omega * displacement) / damped;
    const exponential = Math.exp(-zeta * omega * elapsed);
    const cosine = Math.cos(damped * elapsed);
    const sine = Math.sin(damped * elapsed);
    return {
      position: to + exponential * (displacement * cosine + coefficient * sine),
      velocity: exponential * (-zeta * omega * (displacement * cosine + coefficient * sine) + damped * (-displacement * sine + coefficient * cosine)),
    };
  }
  const c = velocity + omega * displacement;
  const exponential = Math.exp(-omega * elapsed);
  return { position: to + exponential * (displacement + c * elapsed), velocity: exponential * (c - omega * (displacement + c * elapsed)) };
}

/** Spring (or, in quiet mode, a short fade) from `from` to `to`; a card missing from both stays at 55. */
export function sampleTransition(tr: Transition, now: number) {
  'worklet';
  const elapsed = Math.max(0, now - tr.startedAt);
  const y = [0, 0, 0];
  const v = [0, 0, 0];
  const o = [1, 1, 1];
  for (let id = 0; id < CARD_COUNT; id++) {
    const start = tr.from[id] ?? tr.to[id] ?? SLOT_Y[0];
    const target = tr.to[id] ?? start;
    const delta = target - start;
    const moves = Math.abs(delta) > 0.001;
    if (elapsed >= tr.duration) {
      y[id] = target;
      v[id] = 0;
      o[id] = 1;
    } else if (tr.quietFade) {
      const p = clamp(elapsed / tr.duration);
      y[id] = start + delta * smooth(p);
      v[id] = tr.duration > 0 ? (delta * 6 * p * (1 - p)) / tr.duration : 0;
      o[id] = moves ? (p < 0.5 ? 1 - p * 2 : (p - 0.5) * 2) : 1;
    } else {
      const value = springValue(start, target, tr.velocity[id] ?? 0, elapsed, tr.stiffness, tr.damping, tr.mass);
      y[id] = value.position;
      v[id] = value.velocity;
      o[id] = 1;
    }
  }
  return { y, v, o };
}

/** Rotation of the held card: leans with the finger, at most 0.7 degrees. */
export function dragRotation(dragY: number, candidate: number, pointerOffset: number, quiet: boolean) {
  'worklet';
  if (quiet) return 0;
  return Math.min(MAX_ROTATION, Math.max(-MAX_ROTATION, (dragY - (SLOT_Y[candidate] + pointerOffset)) * ROTATION_PER_POINT));
}

/**
 * The slot the held card is nearest to: it changes only after its centre crosses a slot midpoint by 8 pt
 * (hysteresis), exactly like Swift's `updateDrag`.
 */
export function nextCandidate(current: number, center: number) {
  'worklet';
  let target = current;
  while (target < 2) {
    const boundary = (SLOT_Y[target] + SLOT_Y[target + 1]) / 2;
    if (center > boundary + TARGET_HYSTERESIS) target += 1;
    else break;
  }
  while (target > 0) {
    const boundary = (SLOT_Y[target - 1] + SLOT_Y[target]) / 2;
    if (center < boundary - TARGET_HYSTERESIS) target -= 1;
    else break;
  }
  return target;
}

/**
 * The frame a plan shows at `now` (Swift: `PaddockWidgetBentoController.frame`). `dragY` is the finger's reference
 * y, written on the UI thread by the gesture, so the held card follows it without a trip through React.
 */
export function evaluatePlan(plan: Plan, now: number, dragY: number): Frame {
  'worklet';
  if (plan.kind === KIND.demo) return sample(Math.max(0, now - plan.anchor), plan.order);
  const base = plan.base;
  let y = base.y;
  let opacity = base.opacity;
  let selected = base.selected;
  let scale = base.scale;
  let shadow = base.shadow;
  let rotation = base.rotation;
  const tr = plan.tr;
  if (tr) {
    const samples = sampleTransition(tr, now);
    y = samples.y;
    opacity = samples.o;
    const progress = smooth((now - tr.startedAt) / tr.duration);
    selected = tr.selected;
    scale = tr.scaleFrom + (1 - tr.scaleFrom) * progress;
    shadow = tr.shadowFrom + (REST_SHADOW - tr.shadowFrom) * progress;
    rotation = tr.rotationFrom * (1 - progress);
  }
  const drag = plan.drag;
  if (drag) {
    y = y.slice();
    y[drag.id] = dragY - drag.pointerOffset;
    selected = drag.id;
    scale = drag.quiet ? 1 : LIFT_SCALE;
    shadow = drag.quiet ? REST_SHADOW : LIFT_SHADOW;
    rotation = dragRotation(dragY, drag.candidate, drag.pointerOffset, drag.quiet);
  }
  return {
    phase: base.phase,
    order: base.order,
    y,
    opacity,
    handles: base.handles,
    selected,
    scale,
    shadow,
    rotation,
    isDemo: false,
    loopTime: -1,
  };
}

/** The card nearest to a touch, or -1 when none is within 38 pt of its centre line. */
export function pickCard(y: number[], referenceY: number, radius: number) {
  'worklet';
  let id = 0;
  let best = Math.abs(y[0] - referenceY);
  for (let candidate = 1; candidate < CARD_COUNT; candidate++) {
    const distance = Math.abs(y[candidate] - referenceY);
    if (distance < best) {
      best = distance;
      id = candidate;
    }
  }
  return best <= radius ? id : -1;
}

/** The loop phase (seconds) for a demo time, wrapped into one period. */
export function loopPhase(elapsed: number) {
  'worklet';
  return elapsed % LOOP;
}

export type TilePose = { x: number; y: number; sx: number; sy: number; rotate: number; speed: number };

function easeOut(value: number) {
  'worklet';
  const t = clamp(value);
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

/** Launched spring from 1 to 0 (normalised displacement): [position, velocity, acceleration] per second. */
function launchedSpring(tau: number) {
  'worklet';
  const w = PIT.omega;
  const z = PIT.zeta;
  const wd = w * Math.sqrt(1 - z * z);
  const b = (-PIT.launch * w + z * w) / wd;
  const e = Math.exp(-z * w * tau);
  const c = Math.cos(wd * tau);
  const s = Math.sin(wd * tau);
  const f = c + b * s;
  const df = wd * (-s + b * c);
  return [e * f, e * (df - z * w * f), e * ((z * z * w * w - wd * wd) * f - 2 * z * w * df)];
}

/**
 * Pose of pit-stop tile `index` at `t` seconds into the pit stop (rest outside 0…PIT_DURATION): offset in points,
 * scale along x (travel) and y, rotation in degrees and speed in points per second (drives the smear).
 */
export function pitTile(index: number, t: number): TilePose {
  'worklet';
  if (t < 0 || t >= PIT_DURATION) return { x: 0, y: 0, sx: 1, sy: 1, rotate: 0, speed: 0 };
  const dx = PIT.side[index] * (PIT.width[index] + PIT.margin);
  const dy = PIT.rise[index];
  const tilt = PIT.tilt[index];
  if (t < PIT.brace) {
    const b = 1 - (1 - PIT.braceScale) * easeOut(t / PIT.brace);
    return { x: 0, y: 0, sx: b, sy: b, rotate: 0, speed: 0 };
  }
  if (t < PIT.brace + PIT.exit) {
    const e = (t - PIT.brace) / PIT.exit;
    const f = Math.pow(e, PIT.exitPower);
    const speed = (Math.abs(dx) * PIT.exitPower * Math.pow(e, PIT.exitPower - 1)) / PIT.exit;
    const stretch = 1 + Math.min(PIT.maxStretch, speed * PIT.stretchPerSpeed);
    return { x: dx * f, y: dy * f, sx: PIT.braceScale * stretch, sy: PIT.braceScale / Math.sqrt(stretch), rotate: tilt * f, speed };
  }
  const tau = t - PIT_ARRIVE - PIT.delay[index];
  if (tau < 0) return { x: dx, y: dy, sx: 1, sy: 1, rotate: tilt, speed: 0 };
  const state = launchedSpring(tau);
  const speed = Math.abs(dx * state[1]);
  const stretch = Math.min(PIT.maxStretch, speed * PIT.stretchPerSpeed);
  const squash = state[0] < 0 ? Math.min(PIT.maxSquash, Math.abs(dx * state[2]) * PIT.squashPerAccel) : 0;
  const along = 1 + stretch - squash;
  return { x: dx * state[0], y: dy * state[0], sx: along, sy: 1 / Math.sqrt(along), rotate: tilt * state[0], speed };
}

/** Opacity at the head of the smear behind a tile moving at `speed` points per second. */
export function smearAlpha(speed: number) {
  'worklet';
  return PIT.ghostAlpha * clamp(speed / PIT.ghostSpeed);
}

export type PeelState = { distance: number; angle: number; slap: number };

/** The demo's peel at loop time `t`: how far the corner is pulled (points), its direction, seconds since the slap. */
export function peelDemo(t: number): PeelState {
  'worklet';
  const k = PEEL;
  if (t < k.start || t >= k.end) return { distance: 0, angle: k.angle, slap: -1 };
  if (t < k.tease) return { distance: k.teaseDistance * easeOut((t - k.start) / (k.tease - k.start)), angle: k.angle, slap: -1 };
  if (t < k.full) {
    const p = cubicBezier((t - k.tease) / (k.full - k.tease), 0.77, 0, 0.175, 1);
    return { distance: k.teaseDistance + (k.maxDistance - k.teaseDistance) * p, angle: k.angle, slap: -1 };
  }
  if (t < k.hold) {
    const local = t - k.full;
    const fade = 1 - local / (k.hold - k.full);
    const wave = Math.sin(2 * Math.PI * k.flutterHz * local);
    return { distance: k.maxDistance + k.flutter * wave * fade, angle: k.angle + k.wobble * Math.sin(2 * Math.PI * k.flutterHz * local + 0.6) * fade, slap: -1 };
  }
  if (t < k.snap) {
    const e = (t - k.hold) / (k.snap - k.hold);
    return { distance: k.maxDistance * (1 - e * e * e), angle: k.angle, slap: -1 };
  }
  return { distance: 0, angle: k.angle, slap: t - k.snap };
}

/** A released finger: the corner falls back from `distance` over PEEL.release (accelerating), then slaps. */
export function peelRelease(distance: number, angle: number, since: number): PeelState {
  'worklet';
  if (since < PEEL.release) {
    const e = since / PEEL.release;
    return { distance: distance * (1 - e * e * e), angle, slap: -1 };
  }
  const slap = since - PEEL.release;
  return { distance: 0, angle, slap: slap < PEEL.slapEnd ? slap : -1 };
}

/** The slap after a peel: lettering press (scale), card shake (points) and impact-line progress (0…1, -1 = none). */
export function slapPose(slap: number) {
  'worklet';
  if (slap < 0) return { press: 1, shake: 0, lines: -1 };
  const press = 1 - PEEL.slapPress * Math.sin(Math.PI * clamp(slap / PEEL.slapPressTime));
  const shake = PEEL.shake * Math.exp(-slap / PEEL.shakeDecay) * Math.sin(2 * Math.PI * PEEL.shakeHz * slap);
  return { press, shake, lines: slap < PEEL.lineTime ? slap / PEEL.lineTime : -1 };
}

/** Rotation (degrees) of a quick card that is not held while the cards are being arranged. */
export function jiggle(id: number, time: number, handles: number) {
  'worklet';
  const direction = id % 2 === 0 ? 1 : -1;
  return handles * JIGGLE.amplitude * direction * Math.sin(2 * Math.PI * JIGGLE.hz * time + id * JIGGLE.phase);
}

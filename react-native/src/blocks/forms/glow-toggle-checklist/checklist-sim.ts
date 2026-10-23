import { demoTouch, FIXED_DT, LOOP_PERIOD, LOOP_START, RESUME_DELAY } from './checklist-demo';
import { D } from './checklist-theme';

/** control: -2 none, -1 cap, 0..3 row. dx is screen points, velocity points/sec. */
export type ChecklistTouch = {
  control: number; down: boolean; dx: number; velocity: number; k: number;
  cancel?: boolean; activate?: boolean;
};
export type KnobState = {
  on: boolean; lead: number; trail: number; leadV: number; trailV: number; hold: number;
  linearFrom: number; linearAge: number;
};
export type ChecklistEvents = {
  changed: boolean; cap: boolean; onMask: number; offMask: number; success: boolean;
  reset: boolean; paused: boolean; resumed: boolean;
};
export type ChecklistState = {
  knobs: KnobState[]; clock: number; present: number; letters: number[];
  open: number; openTarget: number; openFrom: number; openAge: number;
  press: number; sum: number; fill: number; fillV: number; hue: number; hueV: number;
  tip: number; tipV: number; surge: number; surgeAge: number; flash: number;
  glare: number; glareAge: number; glareDelay: number; surgeDelay: number;
  introAge: number; outroAge: number; still: number; opacity: number;
  fadeAge: number; fadeFrom: number; fadeKind: number; fadeTarget: number;
  autoPlay: boolean; started: boolean; loopTime: number; loopTick: number;
  paused: boolean; idle: number; remainder: number;
  finger: number; fingerReal: boolean; realDown: boolean; start: number;
  moved: number; drag: boolean; lastDx: number; fingerVelocity: number;
  events: ChecklistEvents;
};

export function clamp01(n: number) { 'worklet'; return Math.max(0, Math.min(1, n)); }

/** Monotonic x bisection is stable even for the closing curve's zero x handle. */
export function bezier(t: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  let lo = 0; let hi = 1; let u = t;
  for (let i = 0; i < 18; i++) {
    u = (lo + hi) / 2;
    const v = 1 - u;
    const x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
    if (x < t) lo = u; else hi = u;
  }
  const v = 1 - u;
  return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
}

export function clearEvents(s: ChecklistState) {
  'worklet';
  s.events = { changed: false, cap: false, onMask: 0, offMask: 0, success: false,
    reset: false, paused: false, resumed: false };
}

export function createState(autoPlay = false, still = false): ChecklistState {
  'worklet';
  const knobs: KnobState[] = [];
  for (let i = 0; i < 4; i++) knobs.push({ on: false, lead: 0, trail: 0,
    leadV: 0, trailV: 0, hold: 0, linearFrom: 0, linearAge: 0.12 });
  return {
    knobs, clock: 0, present: 0, letters: [0, 0, 0, 0, 0, 0, 0],
    open: 0, openTarget: 0, openFrom: 0, openAge: 1, press: 0, sum: 0,
    fill: 0, fillV: 0, hue: 0, hueV: 0, tip: 0, tipV: 0,
    surge: 0, surgeAge: -1, flash: 0, glare: -0.3, glareAge: -1,
    glareDelay: -1, surgeDelay: -1, introAge: -1, outroAge: -1,
    still: still ? 1 : 0, opacity: still ? 0 : 1, fadeAge: -1, fadeFrom: 0,
    fadeKind: 0, fadeTarget: 0, autoPlay, started: false,
    loopTime: LOOP_START, loopTick: Math.round(LOOP_START / FIXED_DT),
    paused: false, idle: 0, remainder: 0, finger: -2, fingerReal: false,
    realDown: false, start: 0, moved: 0, drag: false, lastDx: 0, fingerVelocity: 0,
    events: { changed: false, cap: false, onMask: 0, offMask: 0, success: false,
      reset: false, paused: false, resumed: false },
  };
}

function fireGlare(s: ChecklistState) {
  'worklet';
  if (!s.still) s.glareAge = 0;
}
function fade(s: ChecklistState, kind: number, target: number) {
  'worklet';
  s.fadeAge = 0; s.fadeFrom = s.opacity; s.fadeKind = kind; s.fadeTarget = target;
}
function intro(s: ChecklistState) {
  'worklet';
  s.clock = 0; s.introAge = 0; s.outroAge = -1;
  s.glareDelay = 0.1;
  if (s.still) fade(s, 2, 1); else s.present = 0.08;
}
function outro(s: ChecklistState) {
  'worklet';
  s.outroAge = 0; s.introAge = -1;
  if (s.still) fade(s, 2, 0);
}
function resetHidden(s: ChecklistState) {
  'worklet';
  for (let i = 0; i < 4; i++) {
    const n = s.knobs[i];
    n.on = false; n.lead = 0; n.trail = 0; n.leadV = 0; n.trailV = 0;
    n.hold = 0; n.linearFrom = 0; n.linearAge = 0.12;
  }
  s.sum = 0; s.fill = 0; s.fillV = 0; s.hue = 0; s.hueV = 0;
  s.tip = 0; s.tipV = 0; s.surge = 0; s.surgeAge = -1; s.surgeDelay = -1;
  s.flash = 0; s.press = 0; s.glare = -0.3; s.glareAge = -1; s.glareDelay = -1;
  s.open = 0; s.openTarget = 0; s.openFrom = 0; s.openAge = 1;
  s.present = 0; s.introAge = -1; s.outroAge = -1; s.finger = -2;
  s.events.changed = true; s.events.reset = true;
}
function commit(s: ChecklistState, i: number, on: boolean, real: boolean) {
  'worklet';
  const n = s.knobs[i];
  n.linearFrom = n.lead; n.linearAge = 0;
  if (n.on === on) return;
  n.on = on;
  let count = 0;
  for (let j = 0; j < 4; j++) if (s.knobs[j].on) count++;
  s.events.changed = true;
  if (on && count === 4) {
    s.surgeDelay = 0.06;
    if (real) s.events.success = true;
  } else if (on) {
    s.flash = Math.max(s.flash, 0.35);
    if (real) s.events.onMask |= 1 << i;
  } else {
    if (count === 3) { s.flash = 0.25; s.surgeDelay = -1; }
    if (real) s.events.offMask |= 1 << i;
  }
}
function toggleCap(s: ChecklistState, real: boolean) {
  'worklet';
  s.openTarget = 1 - s.openTarget; s.openFrom = s.open; s.openAge = 0;
  if (s.still) fade(s, 1, s.openTarget);
  s.events.changed = true;
  if (real) s.events.cap = true;
}
function rubber(n: number) {
  'worklet';
  if (n < 0) return -0.08 * (1 - Math.exp(n / 0.08));
  if (n > 1) return 1 + 0.08 * (1 - Math.exp(-(n - 1) / 0.08));
  return n;
}
function cancelFinger(s: ChecklistState) {
  'worklet';
  if (s.finger >= 0) {
    const n = s.knobs[s.finger];
    n.linearFrom = n.lead; n.linearAge = 0;
  }
  s.finger = -2; s.fingerVelocity = 0;
}
function input(s: ChecklistState, t: ChecklistTouch, real: boolean) {
  'worklet';
  if (real) {
    if (!s.paused && s.autoPlay) {
      cancelFinger(s); s.paused = true; s.events.paused = true;
    }
    s.realDown = t.down; s.idle = 0;
  }
  if (t.cancel) { cancelFinger(s); return; }
  if (t.activate) {
    if (s.present < 1) return;
    if (t.control === -1) toggleCap(s, real);
    else if (t.control >= 0 && s.open >= 0.98) commit(s, t.control, !s.knobs[t.control].on, real);
    return;
  }
  if (t.down && s.finger === -2) {
    if (s.present < 1 || (t.control >= 0 && s.open < 0.98)) return;
    s.finger = t.control; s.fingerReal = real;
    s.start = t.control >= 0 ? s.knobs[t.control].lead : 0;
    s.moved = 0; s.drag = false; s.lastDx = 0;
  }
  if (s.finger === -2 || real !== s.fingerReal) return;
  s.moved = Math.max(s.moved, Math.abs(t.dx));
  if (s.finger >= 0) {
    const n = s.knobs[s.finger];
    // Horizontal movement alone decides tap vs drag; vertical wiggles remain taps.
    s.drag = s.drag || s.moved >= 6;
    // Follow even sub-threshold movement; 6 pt classifies the release, not the response.
    if (s.moved > 0) {
      const pos = s.start + t.dx / (D.sw.travel * t.k);
      n.lead = s.still ? clamp01(pos) : rubber(pos);
      n.leadV = t.velocity / (D.sw.travel * t.k);
      if (s.still) { n.trail = n.lead; n.trailV = n.leadV; }
    }
    if (!t.down) {
      commit(s, s.finger, s.drag ? n.lead + 0.1 * n.leadV > 0.5 : !n.on, real);
      s.finger = -2;
    }
  } else if (!t.down) { toggleCap(s, real); s.finger = -2; }
  s.lastDx = t.dx; s.fingerVelocity = t.velocity;
}

function advance(s: ChecklistState, h: number) {
  'worklet';
  if (s.present > 0) s.clock += h;
  if (s.glareDelay >= 0) {
    s.glareDelay -= h;
    if (s.glareDelay <= 1e-9) { s.glareDelay = -1; fireGlare(s); }
  }
  if (s.surgeDelay >= 0) {
    s.surgeDelay -= h;
    if (s.surgeDelay <= 1e-9) {
      s.surgeDelay = -1; s.flash = 1;
      if (!s.still) { s.surgeAge = 0; s.glareDelay = 0.08; }
    }
  }
  if (s.surgeAge >= 0) {
    s.surgeAge += h; s.surge = s.surgeAge / 0.95;
    if (s.surgeAge >= 0.95) { s.surgeAge = -1; s.surge = 0; }
  }
  s.flash *= Math.exp(-h / 0.28);
  if (s.glareAge >= 0) {
    s.glareAge += h;
    s.glare = -0.3 + 1.6 * bezier(s.glareAge / 0.7, 0.77, 0, 0.175, 1);
    if (s.glareAge >= 0.7) { s.glareAge = -1; s.glare = -0.3; }
  }
  if (!s.still) {
    if (s.introAge >= 0) {
      s.introAge += h;
      s.present = 0.08 + 0.92 * bezier(s.introAge / 0.52, 0.23, 1, 0.32, 1);
      for (let i = 0; i < 7; i++) s.letters[i] = bezier((s.introAge - 0.055 * i) / 0.22, 0.23, 1, 0.32, 1);
      if (s.introAge >= 0.55) s.introAge = -1;
    }
    if (s.outroAge >= 0) {
      s.outroAge += h;
      s.present = 1 - bezier(s.outroAge / 0.38, 0.77, 0, 0.175, 1);
      if (s.outroAge >= 0.38) s.outroAge = -1;
    }
    if (s.open !== s.openTarget) {
      s.openAge += h;
      const duration = s.openTarget ? 0.56 : 0.44;
      const p = s.openTarget ? bezier(s.openAge / duration, 0.23, 1, 0.32, 1)
        : bezier(s.openAge / duration, 0.32, 0.72, 0, 1);
      s.open = s.openFrom + (s.openTarget - s.openFrom) * p;
      if (s.openAge >= duration) { s.open = s.openTarget; if (!s.open) fireGlare(s); }
    }
    s.press += ((s.finger === -1 ? 1 : 0) - s.press) * (1 - Math.exp(-h / 0.055));
  } else if (s.fadeAge >= 0) {
    const before = s.fadeAge;
    s.fadeAge += h;
    if (s.fadeAge < 0.12) s.opacity = s.fadeFrom * (1 - s.fadeAge / 0.12);
    else {
      if (before < 0.12) {
        if (s.fadeKind === 1) s.open = s.fadeTarget;
        else { s.present = s.fadeTarget; for (let i = 0; i < 7; i++) s.letters[i] = 1; }
      }
      s.opacity = clamp01((s.fadeAge - 0.12) / 0.18);
      if (s.fadeAge >= 0.3) s.fadeAge = -1;
    }
  }
  s.sum = 0;
  for (let i = 0; i < 4; i++) {
    const n = s.knobs[i]; const held = s.finger === i;
    n.hold += ((held ? 1 : 0) - n.hold) * (1 - Math.exp(-h / (held ? 0.07 : 0.12)));
    if (s.still) {
      if (!(held && s.moved > 0)) {
        n.linearAge = Math.min(0.12, n.linearAge + h);
        n.lead = n.linearFrom + ((n.on ? 1 : 0) - n.linearFrom) * n.linearAge / 0.12;
        n.trail = n.lead; n.leadV = 0; n.trailV = 0;
      }
    } else {
      if (!(held && s.moved > 0)) {
        n.leadV += (900 * ((n.on ? 1 : 0) - n.lead) - 2 * 0.82 * 30 * n.leadV) * h;
        n.lead += n.leadV * h;
      }
      const w = held && s.moved > 0 ? 22 : 17;
      const z = held && s.moved > 0 ? 0.8 : 0.72;
      const target = held && s.moved > 0 ? n.lead : n.on ? 1 : 0;
      n.trailV += (w * w * (target - n.trail) - 2 * z * w * n.trailV) * h;
      n.trail += n.trailV * h;
      // Bound extreme fling energy while preserving the velocity hand-off.
      n.lead = Math.max(-0.08, Math.min(1.08, n.lead));
      n.trail = Math.max(-0.08, Math.min(1.08, n.trail));
    }
    s.sum += clamp01((n.lead + n.trail) / 2);
  }
  s.fillV += (256 * (s.sum - s.fill) - 32 * s.fillV) * h; s.fill += s.fillV * h;
  s.hueV += (49 * (s.sum - s.hue) - 14 * s.hueV) * h; s.hue += s.hueV * h;
  const p = clamp01((s.sum - 3.1) / 0.85); const target = p * p * (3 - 2 * p);
  s.tipV += (121 * (target - s.tip) - 22 * (s.still ? 1 : 0.38) * s.tipV) * h;
  s.tip += s.tipV * h;
}

/** Mutates only a plain state. Events accumulate until clearEvents, including dt=0 input edges. */
export function step(s: ChecklistState, touch: ChecklistTouch | null, dt: number) {
  'worklet';
  if (!s.started) { s.started = true; if (!s.autoPlay) intro(s); }
  if (touch) input(s, touch, true);
  s.remainder += Math.max(0, Math.min(dt, 1 / 30));
  while (s.remainder + 1e-10 >= FIXED_DT) {
    s.remainder = Math.max(0, s.remainder - FIXED_DT);
    if (s.autoPlay && s.paused) {
      if (!s.realDown) s.idle += FIXED_DT;
      if (s.idle + 1e-9 >= RESUME_DELAY) {
        s.paused = false; s.idle = 0; s.events.resumed = true;
        s.loopTick = Math.round((s.openTarget || s.open > 0 ? 7.5 : 8.7) / FIXED_DT);
      }
    }
    if (s.autoPlay && !s.paused) {
      s.loopTime = s.loopTick * FIXED_DT;
      const t = s.loopTime;
      // Event thresholds are quantized once to the same 240 Hz grid as physics.
      if (s.loopTick === Math.round(0.15 / FIXED_DT)) fireGlare(s);
      if (s.loopTick === Math.round(8.7 / FIXED_DT)) outro(s);
      if (s.loopTick === Math.round(9.2 / FIXED_DT)) resetHidden(s);
      if (s.loopTick === Math.round(9.3 / FIXED_DT)) intro(s);
      const f = demoTouch(t, !!s.still);
      if (f.down || s.finger !== -2) {
        // Keep the final drag displacement on release; the tease releases at zero velocity.
        input(s, { control: f.down ? f.control : s.finger, down: f.down,
          dx: f.down ? f.dx * D.sw.travel : s.lastDx,
          velocity: f.down ? f.velocity * D.sw.travel : 0, k: 1 }, false);
      }
      s.loopTick = (s.loopTick + 1) % Math.round(LOOP_PERIOD / FIXED_DT);
      s.loopTime = s.loopTick * FIXED_DT;
    }
    advance(s, FIXED_DT);
  }
  return s;
}

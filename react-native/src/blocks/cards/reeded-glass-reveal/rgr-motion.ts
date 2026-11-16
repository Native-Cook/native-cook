// Reeded Glass Reveal — the timeline and the live physics. Every autoplay frame is a pure function of the loop
// time t (0 ..< LOOP): the front's path is a timed curve and each rib's depth is an analytic spring started when
// the front crosses the rib's centre, so the three stacks can match frame for frame. Under a finger the same
// springs are integrated live (the numbers are the same presets), and 2 s after the last touch the glass eases
// back to rest and the loop starts again at t = 0.
//
// Pro upgrade 2: the front is a travelling lens that PARKS. It combs the left half flat, holds there for a full
// second breathing a third of a rib (the gasp: the face is sharp on the left and reeded on the right, the number half
// resolved), then goes on. The leading rib swells before it falls.

/** Loop period, s (4-10 s). */
export const LOOP = 8.0;

/** Phase starts and lengths, s. */
export const T = {
  press1: 0.7, // the swap button is pressed (anticipation of the sweep)
  pressIn: 0.06,
  pressOut: 0.1,
  sweep1: 0.8, // the front combs the glass flat, left edge -> mid-screen
  sweep1Dur: 0.8,
  parkStart: 1.6, // PARKED 1.6 -> 2.6: the gasp, held for a second, breathing +-0.3 rib at 0.8 Hz
  parkEnd: 2.6,
  sweepOn: 2.6, // on to the right edge
  sweepOnDur: 0.8,
  glyph1: 3.46, // the glass is clear and the glyph has popped past 180: the state flips to revealed
  press2: 4.76, // clear hold 3.4 -> 4.84 (time to read the face and the number), then the button again
  sweep2: 4.84, // the ribs rise again, right -> left (last rib at about 6.4)
  sweepDur: 1.6,
  glyph2: 6.5, // the ribs are back and the glyph has turned home: the state flips to hidden
  rest: 7.2, // every spring tail has ended
  run: 7.0, // the soft glint that runs down three flutes at rest starts here and wraps through the loop point
  runDur: 1.4,
} as const;

/** Named springs (DESIGN-SYSTEM section 8), mass 1. Response s / damping ratio for the analytic form, k / c for the live one. */
export const SPRING = {
  snap: { response: 0.35, zeta: 0.85, k: 322.3, c: 30.5 }, // the front following a finger
  glide: { response: 0.5, zeta: 0.88, k: 157.9, c: 22.1 }, // the front after release
  pop: { response: 0.45, zeta: 0.68, k: 195.0, c: 19.0 }, // each rib (5.4 % overshoot: a rib goes a little concave before it lies flat)
} as const;

/** Named easings (x1, y1, x2, y2). */
export const EASE = {
  out: [0.2, 0.8, 0.2, 1] as const,
  inOut: [0.65, 0, 0.35, 1] as const,
  // the sweep: a hand wiping glass: soft start and stop, near-constant speed between
  sweep: [0.35, 0.15, 0.65, 0.85] as const,
  // into the park: leaves at once, settles to a stop
  park: [0.3, 0.1, 0.25, 1] as const,
  // out of the park: a slow start, then the hand carries on
  through: [0.45, 0, 0.4, 1] as const,
};

/** Gesture constants. */
export const GESTURE = {
  projection: 0.12, // s: release projection tau
  ribSoft: 1.2, // the front's soft edge, in rib widths (the lens is 2.4 ribs wide)
  dip: 0.2, // ribs under a resting finger dip 20 %
  dipRadius: 1.2, // in rib widths
  resumeAfter: 2.0, // s after the last touch
  tickGap: 0.0625, // s between two rib ticks (<= 16 per second)
  reducedFade: 0.3, // s: Reduce Motion optical dissolve
  settle: 0.004, // depth / velocity threshold for "at rest"
  swell: 0.25, // the leading rib swells to 1.25 before it falls
  peekAfter: 0.18, // s: a press held this long within 6 pt opens a window
  peekDepth: 0.12, // depth inside the window
  peekSigma: 1.4, // ribs
  peekReturn: 0.03, // s per rib: the window closes outward
  directionDecide: 12, // pt: the first 12 pt of a drag decide the direction from a resting end
  wakeSlow: 19, // rib damping after a slow wipe
  wakeFast: 11, // ... and after a fast one (speed >= 1800 pt/s)
};

/** Button press scale. */
export const PRESS = { scale: 0.94, finger: 0.96 } as const;

/** The glass at rest: every rib at full depth. */
export const RIBS = 22;

/** Cubic bezier easing y(x) for control points (x1, y1, x2, y2): Newton on x. */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let s = x;
  for (let i = 0; i < 8; i++) {
    const cx = 3 * x1 * s * (1 - s) * (1 - s) + 3 * x2 * s * s * (1 - s) + s * s * s - x;
    const dx = 3 * x1 * (1 - s) * (1 - 3 * s) + 3 * x2 * s * (2 - 3 * s) + 3 * s * s;
    if (Math.abs(cx) < 1e-6 || dx === 0) break;
    s = Math.min(1, Math.max(0, s - cx / dx));
  }
  return 3 * y1 * s * (1 - s) * (1 - s) + 3 * y2 * s * s * (1 - s) + s * s * s;
}

export function sweepEase(x: number) {
  'worklet';
  return bezier(EASE.sweep[0], EASE.sweep[1], EASE.sweep[2], EASE.sweep[3], x);
}

export function easeOut(x: number) {
  'worklet';
  return bezier(EASE.out[0], EASE.out[1], EASE.out[2], EASE.out[3], x);
}

/** 0 → 1 step response of a spring (response s, damping ratio ζ), evaluated in closed form. */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

function smooth(a: number, b: number, x: number) {
  'worklet';
  const s = clamp01((x - a) / (b - a));
  return s * s * (3 - 2 * s);
}

/** The rib under screen x, by the flute edges (the flutes are not a ruler's width). */
export function ribAt(edges: number[], x: number) {
  'worklet';
  let idx = 0;
  for (let k = 1; k < RIBS; k++) {
    if (x >= edges[k]) idx = k;
  }
  return idx;
}

export function parkEase(x: number) {
  'worklet';
  return bezier(EASE.park[0], EASE.park[1], EASE.park[2], EASE.park[3], x);
}

export function throughEase(x: number) {
  'worklet';
  return bezier(EASE.through[0], EASE.through[1], EASE.through[2], EASE.through[3], x);
}

/** Front progress 0 (left of the glass) -> 1 (right of it) at loop time t: to mid-screen, parked, on, held, back. */
export function frontAt(t: number) {
  'worklet';
  if (t < T.sweep1) return 0;
  if (t < T.sweep1 + T.sweep1Dur) return 0.5 * parkEase((t - T.sweep1) / T.sweep1Dur);
  if (t < T.sweepOn) return 0.5;
  if (t < T.sweepOn + T.sweepOnDur) return 0.5 + 0.5 * throughEase((t - T.sweepOn) / T.sweepOnDur);
  if (t < T.sweep2) return 1;
  if (t < T.sweep2 + T.sweepDur) return 1 - sweepEase((t - T.sweep2) / T.sweepDur);
  return 0;
}

/** The swap button's scale at loop time t: a press before each sweep. */
export function pressAt(t: number) {
  'worklet';
  const one = (t0: number) => {
    const a = t - t0;
    if (a <= 0 || a >= T.pressIn + T.pressOut) return 1;
    if (a < T.pressIn) return 1 - (1 - PRESS.scale) * easeOut(a / T.pressIn);
    return PRESS.scale + (1 - PRESS.scale) * easeOut((a - T.pressIn) / T.pressOut);
  };
  return one(T.press1) * one(T.press2);
}

/**
 * The swap glyph's turn, degrees: 180 x the front's progress, followed through the pop spring (Pro upgrade 5), so it turns
 * while the front travels, hangs at 90 while the front is parked, and pops past 180 when the glass is clear. Integrated
 * at a fixed 1/120 s from the first press (semi-implicit Euler, the same spring as the live physics), so the three stacks agree.
 */
export function glyphAt(t: number) {
  'worklet';
  const h = 1 / 120;
  const k = SPRING.pop.k;
  const c = SPRING.pop.c;
  const from = T.sweep1 - 0.05;
  const steps = Math.floor((Math.min(t, T.rest) - from) / h);
  let g = 0;
  let v = 0;
  for (let i = 0; i < steps; i++) {
    const target = 180 * frontAt(from + i * h);
    v += (k * (target - g) - c * v) * h;
    g += v * h;
  }
  return g;
}

/** Geometry the timeline needs, computed once per screen width (plain numbers, safe to capture in worklets). */
export type Geo = {
  W: number;
  ribW: number;
  front0: number;
  front1: number;
  /** the 23 flute edges and the 22 centres between them (pt) */
  edges: number[];
  centres: number[];
  /** times the front crosses each rib centre on the way out (tA) and back (tB) */
  tA: number[];
  tB: number[];
  /** where the front parks, pt */
  xPark: number;
};

/** Inverse of a monotone 0..1 easing by bisection (JS, once per layout). */
function inverse(ease: (x: number) => number, y: number) {
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (ease(mid) < y) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

/** The gasp frame: the front parked at mid-screen, half way through the hold. */
export const GASP_T = (T.parkStart + T.parkEnd) / 2;

/** What a layout must provide to cut the timeline (Layout in rgr-theme satisfies it). */
export type GeoSource = { W: number; ribW: number; front0: number; front1: number; edges: number[]; centres: number[] };

export function makeGeo(L: GeoSource): Geo {
  const tA: number[] = [];
  const tB: number[] = [];
  for (let i = 0; i < RIBS; i++) {
    const p = (L.centres[i] - L.front0) / (L.front1 - L.front0);
    tA.push(
      p <= 0.5 ? T.sweep1 + T.sweep1Dur * inverse(parkEase, p / 0.5) : T.sweepOn + T.sweepOnDur * inverse(throughEase, (p - 0.5) / 0.5),
    );
    tB.push(T.sweep2 + T.sweepDur * inverse(sweepEase, 1 - p));
  }
  return {
    W: L.W,
    ribW: L.ribW,
    front0: L.front0,
    front1: L.front1,
    edges: L.edges.slice(),
    centres: L.centres.slice(),
    tA,
    tB,
    xPark: L.front0 + 0.5 * (L.front1 - L.front0),
  };
}

/** What the screen draws: one value per rib, the glyph's turn, the button's scale, the front (pt). */
export type Look = {
  depths: number[];
  glyph: number;
  press: number;
  front: number;
  mean: number;
  /** the front 60 ms ago (pt): the capsule's highlight follows it */
  lag: number;
  /** 0..1 progress of the soft glint that runs down three flutes at rest; -1 = off */
  run: number;
  /** -1..1 where the key light sits */
  light: number;
  /** -1..1 breathing of the magnification */
  breath: number;
  /** 0..1 a long press has opened a window */
  peek: number;
};

function clampT(t: number) {
  'worklet';
  const r = t % LOOP;
  return r < 0 ? r + LOOP : r;
}

/** The leading rib swells before it falls: 1 -> 1.25 over the 0.12 s before the front reaches its centre, then it follows the fall. */
function swell(tau: number) {
  'worklet';
  const rise = smooth(-0.12, 0, tau);
  return 1 + GESTURE.swell * rise * Math.exp(-Math.max(tau, 0) / 0.12);
}

export function timelineLook(tIn: number, g: Geo): Look {
  'worklet';
  const t = clampT(tIn);
  const depths: number[] = [];
  let sum = 0;
  // the soft glint that runs down three flutes at rest wraps through the loop point, so the seam is invisible
  const r = ((t - T.run + LOOP) % LOOP) / T.runDur;
  const run = r < 1 ? r : -1;
  const light = 0.5 * Math.sin((2 * Math.PI * t) / LOOP);
  const breath = 0.2 * Math.sin((8 * Math.PI * t) / LOOP);
  const inMotion = t > 0 && t < T.rest;
  // the parked front breathes: its edge moves a third of a rib to and fro at 0.8 Hz
  const park = smooth(T.parkStart, T.parkStart + 0.3, t) * (1 - smooth(T.parkEnd - 0.3, T.parkEnd, t));
  const wob = 0.35 * park * Math.sin(2 * Math.PI * 0.8 * (t - T.parkStart));
  for (let i = 0; i < RIBS; i++) {
    let d = 1;
    if (inMotion) {
      const ta = t - g.tA[i];
      const tb = t - g.tB[i];
      d = (1 - springAt(ta, SPRING.pop.response, SPRING.pop.zeta)) * swell(ta) + springAt(tb, SPRING.pop.response, SPRING.pop.zeta);
      if (park > 0) {
        const q = (g.centres[i] - g.xPark - 0.3 * g.ribW) / (0.8 * g.ribW);
        d += wob * Math.exp(-q * q);
      }
    }
    depths.push(d);
    sum += d;
  }
  const front = inMotion ? g.front0 + (g.front1 - g.front0) * frontAt(t) : g.front0;
  return {
    depths,
    glyph: inMotion ? glyphAt(t) : 0,
    press: inMotion ? pressAt(t) : 1,
    front,
    mean: sum / RIBS,
    lag: inMotion ? g.front0 + (g.front1 - g.front0) * frontAt(t - 0.06) : g.front0,
    run,
    light,
    breath,
    peek: 0,
  };
}

/** Live state under a finger (or after one): integrated on the UI thread. */
export type Live = {
  fx: number; // front, pt
  fv: number; // pt/s
  mode: 'drag' | 'spring' | 'sweep' | 'hold';
  target: number; // pt
  sweepFrom: number;
  sweepTo: number;
  sweepT: number;
  sweepDur: number;
  depth: number[];
  vel: number[];
  glyph: number;
  glyphV: number;
  glyphTarget: number;
  touching: boolean;
  touchX: number;
  revealed: boolean;
  lastRib: number;
  lastTick: number; // s
  fade: number; // Reduce Motion: 1 = deep, 0 = clear
  press: number;
  /** the front clears from the right edge (a drag that began there) */
  flip: boolean;
  /** the finger has moved past the dead zone */
  dragged: boolean;
  /** seconds the finger has rested without dragging */
  holdT: number;
  /** 0..1 the peek window's opening; peekRel: s since the finger lifted from an open window (-1: not lifted) */
  peek: number;
  peekRel: number;
  peekX: number;
  /** 0..1 how fast the last swipe was: sets the ribs' damping */
  speedK: number;
  /** the front 60 ms ago (smoothed), pt */
  fl: number;
  light: number;
};

function liveBase(g: Geo): Live {
  'worklet';
  return {
    fx: g.front0,
    fv: 0,
    mode: 'spring',
    target: g.front0,
    sweepFrom: g.front0,
    sweepTo: g.front0,
    sweepT: 0,
    sweepDur: 0,
    depth: [],
    vel: [],
    glyph: 0,
    glyphV: 0,
    glyphTarget: 0,
    touching: false,
    touchX: 0,
    revealed: false,
    lastRib: -1,
    lastTick: -1,
    fade: 1,
    press: 1,
    flip: false,
    dragged: false,
    holdT: 0,
    peek: 0,
    peekRel: -1,
    peekX: 0,
    speedK: 0,
    fl: g.front0,
    light: 0,
  };
}

/** Hand over from the timeline at loop time t: positions and velocities, no jump. */
export function liveFromTimeline(tIn: number, g: Geo): Live {
  'worklet';
  const t = clampT(tIn);
  const a = timelineLook(t, g);
  const b = timelineLook(t + 1 / 120, g);
  const vel: number[] = [];
  for (let i = 0; i < RIBS; i++) vel.push((b.depths[i] - a.depths[i]) * 120);
  // the state flips with the glyph's turn; the front springs to the nearer end
  const revealed = t >= T.glyph1 && t < T.glyph2;
  const p = (a.front - g.front0) / (g.front1 - g.front0);
  const glyphDelta = b.glyph - a.glyph;
  const m = liveBase(g);
  m.fx = a.front;
  m.fv = (b.front - a.front) * 120;
  m.target = p >= 0.5 ? g.front1 : g.front0;
  m.sweepFrom = a.front;
  m.sweepTo = a.front;
  m.depth = a.depths.slice();
  m.vel = vel;
  m.glyph = a.glyph;
  m.glyphV = glyphDelta * 120;
  m.glyphTarget = 180 * Math.min(1, Math.max(0, p));
  m.revealed = revealed;
  m.press = a.press;
  m.fl = a.lag;
  m.light = a.light;
  return m;
}

/** A resting live state (no autoplay, or Reduce Motion). */
export function restLive(g: Geo): Live {
  'worklet';
  const m = liveBase(g);
  const depth: number[] = [];
  const vel: number[] = [];
  for (let i = 0; i < RIBS; i++) {
    depth.push(1);
    vel.push(0);
  }
  m.depth = depth;
  m.vel = vel;
  return m;
}

/** The glass already clear (defaultRevealed). */
export function clearedLive(g: Geo): Live {
  'worklet';
  const m = restLive(g);
  for (let i = 0; i < RIBS; i++) m.depth[i] = 0;
  m.fx = g.front1;
  m.target = g.front1;
  m.fl = g.front1;
  m.revealed = true;
  m.glyph = 180;
  m.glyphTarget = 180;
  m.fade = 0;
  return m;
}


/** Start a timed sweep (the button) from wherever the front is, at the loop's speed. */
export function startSweep(m: Live, g: Geo, to: number) {
  'worklet';
  m.mode = 'sweep';
  m.sweepFrom = m.fx;
  m.sweepTo = to;
  m.sweepT = 0;
  m.sweepDur = Math.max(0.35, (T.sweepDur * Math.abs(to - m.fx)) / (g.front1 - g.front0));
  m.target = to;
}

/** Finger displacement is direct inside the travel limits, with resistance beyond them. A flipped front runs the other way. */
export function dragLive(m: Live, g: Geo, frontStart: number, translation: number, velocity: number) {
  'worklet';
  const sign = m.flip ? -1 : 1;
  const raw = frontStart + sign * translation;
  const over = raw < g.front0 ? raw - g.front0 : raw > g.front1 ? raw - g.front1 : 0;
  const front = over === 0 ? raw : (raw < g.front0 ? g.front0 : g.front1) + over / (1 + Math.abs(over) / 120);
  m.mode = 'drag';
  m.fx = front;
  m.target = front;
  m.fv = sign * velocity;
}

/** From a resting end the first pt of a drag decide which edge the front starts from (upgrade 4). */
export function decideDirection(m: Live, g: Geo, startX: number, translation: number) {
  'worklet';
  const atMasked = m.fx <= g.front0 + 1 && !m.revealed;
  if (!atMasked) return;
  m.flip = startX > g.W * 0.5 && translation < 0;
}

/** Release carries velocity into the spring and selects the end the projected front is nearer to (past `commit` it clears). */
export function releaseLive(m: Live, g: Geo, velocity: number, commit = 0.5) {
  'worklet';
  const v = m.flip ? -velocity : velocity;
  const projected = m.fx + v * GESTURE.projection;
  const p = (projected - g.front0) / (g.front1 - g.front0);
  m.target = p >= commit ? g.front1 : g.front0;
  m.mode = 'spring';
  m.fv = v;
}

/** Cancellation releases the hold but never activates the reduced-motion toggle. */
export function finishTouch(m: Live, g: Geo, dragged: boolean, velocity: number, reduce: boolean, success: boolean, commit = 0.5) {
  'worklet';
  m.touching = false;
  if (m.peek > 0.05) {
    m.peekRel = 0;
    m.mode = 'spring';
    return;
  }
  if (reduce && success) m.target = m.target >= g.front1 - 1 ? g.front0 : g.front1;
  else if (!reduce && (m.mode === 'drag' || dragged)) releaseLive(m, g, success ? velocity : 0, commit);
  m.mode = 'spring';
}

/** Only real input updates lastTouch; completion events never extend this deadline. */
export function shouldResume(m: Live, now: number, lastTouch: number) {
  'worklet';
  return !m.touching && m.mode === 'spring' && m.peek < 0.02 && now - lastTouch >= GESTURE.resumeAfter;
}

/** Events a live step reports (for haptics and callbacks). */
export type StepEvents = { tick: boolean; edge: boolean; revealed: boolean; masked: boolean };

/** One live step of dt seconds. `now` is the clock (s) for rate-limiting ticks. */
export function stepLive(m: Live, dt: number, g: Geo, reduce: boolean, now: number, peekOn = true): StepEvents {
  'worklet';
  const ev = { tick: false, edge: false, revealed: false, masked: false };
  const steps = Math.max(1, Math.ceil(dt / (1 / 240)));
  const h = dt / steps;
  if (reduce) {
    // Reduce Motion: no travelling front; the whole pane dissolves between deep and clear
    const want = m.target >= g.front1 - 1 ? 0 : 1;
    const dir = want < m.fade ? -1 : 1;
    m.fade = clamp01(m.fade + (dir * dt) / GESTURE.reducedFade);
    if ((dir < 0 && m.fade < want) || (dir > 0 && m.fade > want)) m.fade = want;
    m.fx = m.target;
    m.fv = 0;
    m.mode = 'spring';
    for (let i = 0; i < RIBS; i++) {
      m.depth[i] = m.fade;
      m.vel[i] = 0;
    }
  } else {
    // a long press opens a window that follows the finger; it closes outward from the finger on lift
    if (peekOn && m.touching && !m.dragged && m.mode === 'hold') {
      m.holdT += dt;
      if (m.holdT >= GESTURE.peekAfter) m.peek = clamp01(m.peek + dt / 0.12);
    } else if (m.touching && m.peek > 0) {
      m.peek = clamp01(m.peek + dt / 0.12); // an open window stays open while the finger slides
    } else if (m.peekRel >= 0) {
      m.peekRel += dt;
      if (m.peekRel > GESTURE.peekReturn * RIBS + 0.1) {
        m.peekRel = -1;
        m.peek = 0;
      }
    } else if (!m.touching) {
      m.peek = 0;
    }
    if (!m.touching) m.holdT = 0;
    m.speedK = Math.max(m.speedK * Math.exp(-dt / 0.35), clamp01(Math.abs(m.fv) / 1800));
    const damping = GESTURE.wakeSlow + (GESTURE.wakeFast - GESTURE.wakeSlow) * m.speedK;
    const soft = GESTURE.ribSoft * g.ribW;
    const mirror = g.front0 + g.front1;
    const iTouch = ribAt(g.edges, m.touchX);
    for (let s = 0; s < steps; s++) {
      // the front
      if (m.mode === 'sweep') {
        m.sweepT += h;
        const p = clamp01(m.sweepT / m.sweepDur);
        const nx = m.sweepFrom + (m.sweepTo - m.sweepFrom) * sweepEase(p);
        m.fv = (nx - m.fx) / h;
        m.fx = nx;
        if (p >= 1) {
          m.mode = 'spring';
          m.fv = 0;
        }
      } else if (m.mode === 'spring') {
        const sp = SPRING.glide;
        const a = sp.k * (m.target - m.fx) - sp.c * m.fv;
        m.fv += a * h;
        m.fx += m.fv * h;
      }
      // each rib follows its own target through its own spring
      for (let i = 0; i < RIBS; i++) {
        const c = m.flip ? mirror - g.centres[i] : g.centres[i];
        let target = 1 - smooth(c - soft, c + soft, m.fx);
        // the leading rib swells before it falls
        if (m.mode !== 'hold' || m.dragged) {
          const lead = (c - m.fx - 0.9 * g.ribW) / (0.7 * g.ribW);
          if (c > m.fx && target > 0.5) target += GESTURE.swell * Math.exp(-lead * lead) * clamp01(Math.abs(m.fv) / 120);
        }
        if (m.touching) {
          const q = (g.centres[i] - m.touchX) / (GESTURE.dipRadius * g.ribW);
          target *= 1 - GESTURE.dip * Math.exp(-q * q);
        }
        // the peek window: the ribs around the finger lie almost flat; after a lift they close outward, 30 ms apart
        let open = 0;
        if (m.touching) open = m.peek;
        else if (m.peekRel >= 0 && m.peekRel < GESTURE.peekReturn * Math.abs(i - iTouch)) open = 1;
        if (open > 0) {
          const q = (g.centres[i] - m.touchX) / (GESTURE.peekSigma * g.ribW);
          const win = Math.exp(-q * q);
          target = Math.min(target, 1 - (1 - GESTURE.peekDepth) * open * win);
        }
        const a = SPRING.pop.k * (target - m.depth[i]) - damping * m.vel[i];
        m.vel[i] += a * h;
        m.depth[i] += m.vel[i] * h;
      }
    }
  }
  m.press += (1 - m.press) * Math.min(1, dt / 0.08);
  // the glyph turns with the front (180 x progress) through the pop spring
  {
    m.glyphTarget = 180 * clamp01((m.fx - g.front0) / (g.front1 - g.front0));
    const a = SPRING.pop.k * (m.glyphTarget - m.glyph) - SPRING.pop.c * m.glyphV;
    m.glyphV += a * dt;
    m.glyph += m.glyphV * dt;
    if (reduce) {
      m.glyph = 0;
      m.glyphV = 0;
    }
  }
  // the lagging front the capsule's highlight follows (60 ms), and the light: it follows the finger, else settles at the centre
  m.fl += (m.fx - m.fl) * (1 - Math.exp(-dt / 0.06));
  const wantLight = m.touching && m.touchX > -1e8 ? Math.max(-1, Math.min(1, (m.touchX / g.W - 0.5) * 1.6)) : 0;
  m.light += (wantLight - m.light) * (1 - Math.exp(-dt / 0.25));
  // a tick each time the front crosses a rib under the finger (<= 16 per second); the last rib is a harder tap
  if (m.mode === 'drag') {
    const rib = ribAt(g.edges, m.fx);
    if (m.lastRib >= 0 && rib !== m.lastRib && rib >= 0 && rib < RIBS && now - m.lastTick >= GESTURE.tickGap) {
      ev.tick = true;
      ev.edge = rib === 0 || rib === RIBS - 1;
      m.lastTick = now;
    }
    m.lastRib = rib;
  } else {
    m.lastRib = -1;
  }
  // completed states
  if (m.mode !== 'drag' && m.mode !== 'sweep' && !m.touching && m.peek < 0.02) {
    const atEnd = Math.abs(m.fx - m.target) < 1 && Math.abs(m.fv) < 8;
    let clear = true;
    let masked = true;
    for (let i = 0; i < RIBS; i++) {
      if (m.depth[i] > 0.02) clear = false;
      if (m.depth[i] < 0.98) masked = false;
    }
    if (atEnd && clear && m.target >= g.front1 - 1 && !m.revealed) {
      m.revealed = true;
      ev.revealed = true;
    } else if (atEnd && masked && m.target <= g.front0 + 1 && m.revealed) {
      m.revealed = false;
      m.flip = false;
      ev.masked = true;
    } else if (atEnd && masked && m.target <= g.front0 + 1) {
      m.flip = false;
    }
  }
  return ev;
}

/** The look of a live state. */
export function liveLook(m: Live, pressScale: number): Look {
  'worklet';
  let sum = 0;
  for (let i = 0; i < RIBS; i++) sum += m.depth[i];
  return {
    depths: m.depth.slice(),
    glyph: m.glyph,
    press: pressScale * m.press,
    front: m.fx,
    mean: sum / RIBS,
    lag: m.fl,
    run: -1,
    light: m.light,
    breath: 0,
    peek: m.peek,
  };
}

/** True once a live state has come back to the timeline's first frame. */
export function liveAtRest(m: Live, g: Geo) {
  'worklet';
  if (Math.abs(m.fx - g.front0) > 1 || Math.abs(m.fv) > 8) return false;
  if (Math.abs(m.glyph) > 0.5 || Math.abs(m.glyphV) > 2) return false;
  if (m.peek > 0.01 || m.peekRel >= 0 || Math.abs(m.light) > 0.02) return false;
  for (let i = 0; i < RIBS; i++) {
    if (Math.abs(m.depth[i] - 1) > GESTURE.settle * 5 || Math.abs(m.vel[i]) > 0.05) return false;
  }
  return true;
}

/** Depth of the glass at screen x (the rib under it). */
export function depthAt(depths: number[], x: number, edges: number[]) {
  'worklet';
  return depths[ribAt(edges, x)];
}

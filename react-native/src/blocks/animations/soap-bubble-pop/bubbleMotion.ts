/*
 Soap Bubble Pop — geometry, the autoPlay script and the touch simulation (all worklets, plain
 arithmetic, shared by the component and the offline renderer).

 LOOP = 8 s:
   0.0 – 1.2   rest: three bubbles hover (a 3–4 pt bob at 1 and 2 cycles per loop, so it repeats).
   1.2 – 3.0   they drift up (ease-in-out).
   3.0         a tap pops bubble A: a ragged hole tears across it in 0.2 s, droplets fly out and
               fall, a "+10" star floats up, the round score rolls 0 → 10.
   4.6         bubble B pops the same way (10 → 20).
   5.4 – 6.4   hold: only C hovers.
   6.4 – 6.95  a new A inflates from the wand's soap film, detaches and floats to its place
               (6.95 – 7.55); 6.95 – 7.5 a new B inflates, floats home 7.5 – 8.0. C sinks back
               to rest over 6.4 – 8.0; the score rolls back to 0 (a new round) 6.45 – 7.0.
   8.0         the frame at 0.
*/

export const LOOP = 8;
export const REDUCED_AT = 0.4;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;
export const MODE_AUTO = 0;
export const MODE_LIVE = 1;
export const MODE_BACK = 2;

/** Inflating at the wand, then floating to the bubble's place (s). */
const INFLATE = 0.55;
const TRAVEL = 0.6;
const POP_AT = [3.0, 4.6];
/** Where the scripted taps land, as a share of the bubble's radius. */
const TAP_AT = [
  [0.42, -0.5],
  [-0.56, -0.18],
];
const RESPAWN_AT = [6.4, 6.95];
const TRAVEL_B = 0.5;
/** A popped bubble's star floats this long (s). */
export const STAR_FOR = 1.1;

export const HEADER_H = 128;
export const CARD_H = 136;

export type BubbleGeo = {
  w: number;
  h: number;
  /** Rest x, y and radius of A, B, C. */
  rest: number[];
  /** Drift of each bubble (dx, dy) during 1.2 – 3.0. */
  drift: number[];
  wx: number;
  wy: number;
  wr: number;
  cardTop: number;
};

export function makeBubbleGeo(w: number, h: number, insetTop: number, insetBottom: number): BubbleGeo {
  'worklet';
  const cardTop = h - Math.max(insetBottom, 16) - 12 - CARD_H;
  const wy = cardTop - 56;
  const fieldTop = insetTop + HEADER_H + 8;
  const fieldH = wy - 70 - fieldTop;
  const lift = Math.min(44, fieldH * 0.11);
  return {
    w,
    h,
    rest: [
      w * 0.3, fieldTop + fieldH * 0.3, Math.min(w * 0.17, 72),
      w * 0.7, fieldTop + fieldH * 0.55, Math.min(w * 0.14, 60),
      w * 0.36, fieldTop + fieldH * 0.84, Math.min(w * 0.11, 46),
    ],
    drift: [8, -lift, -10, -lift * 0.8, 12, -lift * 0.7],
    wx: w * 0.5,
    wy,
    wr: 24,
    cardTop,
  };
}

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

/** The hover: 3 pt sideways once a loop, 4 pt up and down twice (film = loop clock in loops). */
function bobX(i: number, film: number) {
  'worklet';
  return 3 * Math.sin(2 * Math.PI * (film + i * 0.31));
}
function bobY(i: number, film: number) {
  'worklet';
  return 4 * Math.sin(2 * Math.PI * (2 * film + i * 0.17));
}

/** Pose of one bubble: x, y, radius, alpha, pop age (< 0 whole), tap dx, tap dy, wobble. */
export type Pose = number[];

/**
 Bubble i growing at the wand and floating home: s seconds after it started, `travel` the time
 its trip takes. Writes into out (8 numbers). Returns the wand film's share (0 while it is used).
*/
export function inflatePose(g: BubbleGeo, i: number, s: number, travel: number, film: number, out: Pose) {
  'worklet';
  const R = g.rest[i * 3 + 2];
  const k = easeOut(clamp01(s / INFLATE));
  const r = g.wr * 1.05 + (R - g.wr * 1.05) * k;
  // Attached: the bubble balloons up out of the ring.
  const ax = g.wx;
  const ay = g.wy - (r - g.wr * 0.92);
  const u = easeInOut(clamp01((s - INFLATE) / travel));
  const tx = g.rest[i * 3] + bobX(i, film) * u;
  const ty = g.rest[i * 3 + 1] + bobY(i, film) * u;
  const arc = Math.sin(Math.PI * u) * (i === 0 ? -22 : 22);
  out[0] = ax + (tx - ax) * u + arc;
  out[1] = ay + (ty - ay) * u;
  out[2] = r;
  out[3] = 1;
  out[4] = -1;
  out[5] = 0;
  out[6] = 0;
  // Wobbly while it grows and right after it lets go, calm by the time it lands.
  const since = Math.max(0, s - INFLATE);
  out[7] = 0.012 + 0.03 * (1 - k) + 0.03 * Math.exp(-since / 0.3) * (s > INFLATE ? 1 : 0) * (1 - u);
}

export type BubbleFrame = {
  poses: number[][];
  /** Wand film alpha. */
  wandFilm: number;
  /** Finger mark: x, y, age (< 0 none). */
  tap: number[];
  /** Star age per bubble (< 0 none), and where it starts. */
  stars: number[];
  starX: number[];
  starY: number[];
  /** Round score in tens (continuous while it rolls). */
  score: number;
  film: number;
};

export function emptyFrame(): BubbleFrame {
  'worklet';
  return {
    poses: [
      [0, 0, 0, 0, -1, 0, 0, 0.012],
      [0, 0, 0, 0, -1, 0, 0, 0.012],
      [0, 0, 0, 0, -1, 0, 0, 0.012],
    ],
    wandFilm: 1,
    tap: [0, 0, -1],
    stars: [-1, -1, -1],
    starX: [0, 0, 0],
    starY: [0, 0, 0],
    score: 0,
    film: 0,
  };
}

function restPose(g: BubbleGeo, i: number, dx: number, dy: number, film: number, out: Pose) {
  'worklet';
  out[0] = g.rest[i * 3] + dx + bobX(i, film);
  out[1] = g.rest[i * 3 + 1] + dy + bobY(i, film);
  out[2] = g.rest[i * 3 + 2];
  out[3] = 1;
  out[4] = -1;
  out[5] = 0;
  out[6] = 0;
  out[7] = 0.012;
}

/** The autoPlay frame at loop time t (pure). */
export function scriptFrame(t: number, g: BubbleGeo, f: BubbleFrame) {
  'worklet';
  const film = t / LOOP;
  f.film = film;
  const up = easeInOut(seg(t, 1.2, 3.0));
  f.tap[2] = -1;
  f.wandFilm = 1;
  for (let i = 0; i < 2; i += 1) {
    const p = f.poses[i];
    f.stars[i] = -1;
    if (t < POP_AT[i]) {
      restPose(g, i, g.drift[i * 2] * up, g.drift[i * 2 + 1] * up, film, p);
    } else if (t < RESPAWN_AT[i]) {
      // Popped where it was when the finger came down.
      restPose(g, i, g.drift[i * 2], g.drift[i * 2 + 1], POP_AT[i] / LOOP, p);
      p[4] = t - POP_AT[i];
      p[5] = TAP_AT[i][0] * p[2];
      p[6] = TAP_AT[i][1] * p[2];
      f.stars[i] = t - POP_AT[i] < STAR_FOR ? t - POP_AT[i] : -1;
      f.starX[i] = p[0];
      f.starY[i] = p[1];
      const tapAge = t - POP_AT[i] + 0.08;
      if (tapAge < 0.45) {
        f.tap[0] = p[0] + p[5];
        f.tap[1] = p[1] + p[6];
        f.tap[2] = tapAge;
      }
    } else {
      inflatePose(g, i, t - RESPAWN_AT[i], i === 0 ? TRAVEL : TRAVEL_B, film, p);
      if (t - RESPAWN_AT[i] < INFLATE + 0.25) f.wandFilm = 0;
    }
    // The finger mark shows just before the pop too.
    const pre = t - POP_AT[i] + 0.08;
    if (pre >= 0 && pre < 0.08) {
      f.tap[0] = g.rest[i * 3] + g.drift[i * 2] + TAP_AT[i][0] * g.rest[i * 3 + 2];
      f.tap[1] = g.rest[i * 3 + 1] + g.drift[i * 2 + 1] + TAP_AT[i][1] * g.rest[i * 3 + 2];
      f.tap[2] = pre;
    }
  }
  // The film comes back to the ring once the second bubble has left it.
  const back = seg(t, RESPAWN_AT[1] + INFLATE + 0.05, RESPAWN_AT[1] + INFLATE + 0.35);
  if (t > RESPAWN_AT[1] + INFLATE) f.wandFilm = back;
  const settle = easeInOut(seg(t, 6.4, 8.0));
  restPose(g, 2, g.drift[4] * up * (1 - settle), g.drift[5] * up * (1 - settle), film, f.poses[2]);
  f.stars[2] = -1;
  f.score = easeInOut(seg(t, 3.05, 3.4)) + easeInOut(seg(t, 4.65, 5.0)) - 2 * easeInOut(seg(t, 6.45, 7.0));
}

/** Touch simulation (UI thread). */
export type BubbleSim = {
  mode: number;
  t: number;
  now: number;
  last: number;
  back: number;
  frame: BubbleFrame;
  /** Base (un-bobbed) x, y of each whole bubble while live; spawn clock (< 0 none). */
  base: number[];
  spawn: number[];
  score: number;
  /** Snapshot at the start of "back". */
  from: number[];
  filmFrom: number;
  scoreFrom: number;
};

export function makeSim(): BubbleSim {
  'worklet';
  return {
    mode: MODE_AUTO,
    t: 0,
    now: 0,
    last: 0,
    back: 0,
    frame: emptyFrame(),
    base: [0, 0, 0, 0, 0, 0],
    spawn: [-1, -1, -1],
    score: 0,
    from: [0, 0, 0, 0, 0, 0, 0, 0, 0],
    filmFrom: 0,
    scoreFrom: 0,
  };
}

/** A finger lands: the script stops; whole bubbles keep hovering where they are. */
function goLive(s: BubbleSim, g: BubbleGeo) {
  'worklet';
  if (s.mode === MODE_LIVE) return;
  const f = s.frame;
  for (let i = 0; i < 3; i += 1) {
    const p = f.poses[i];
    s.base[i * 2] = p[0] - bobX(i, f.film);
    s.base[i * 2 + 1] = p[1] - bobY(i, f.film);
    s.spawn[i] = -1;
    // A bubble still at the wand or on its way home finishes that trip under the finger's clock.
    if (s.mode === MODE_AUTO && f.poses[i][4] < 0 && i < 2 && s.t >= RESPAWN_AT[i]) {
      s.spawn[i] = s.t - RESPAWN_AT[i];
    }
  }
  s.score = Math.round(f.score);
  f.score = s.score;
  s.mode = MODE_LIVE;
  void g;
}

/** A tap: pops the topmost whole bubble under the finger. Returns its index, or -1. */
export function simTap(s: BubbleSim, g: BubbleGeo, x: number, y: number) {
  'worklet';
  goLive(s, g);
  s.last = s.now;
  const f = s.frame;
  f.tap[0] = x;
  f.tap[1] = y;
  f.tap[2] = 0;
  for (let i = 0; i < 3; i += 1) {
    const p = f.poses[i];
    if (p[4] >= 0 || p[3] < 0.5) continue;
    const dx = x - p[0];
    const dy = y - p[1];
    if (dx * dx + dy * dy > p[2] * p[2] * 1.12) continue;
    s.base[i * 2] = p[0] - bobX(i, f.film);
    s.base[i * 2 + 1] = p[1] - bobY(i, f.film);
    s.spawn[i] = -1;
    p[4] = 0;
    p[5] = dx;
    p[6] = dy;
    f.stars[i] = 0;
    f.starX[i] = p[0];
    f.starY[i] = p[1];
    s.score = Math.min(9, s.score + 1);
    return i;
  }
  return -1;
}

export function simTouch(s: BubbleSim, g: BubbleGeo) {
  'worklet';
  goLive(s, g);
  s.last = s.now;
}

/** One frame; returns true when the loop starts over. */
export function simStep(s: BubbleSim, dt: number, g: BubbleGeo, auto: boolean, reduced: boolean) {
  'worklet';
  s.now += dt;
  const f = s.frame;
  if (s.mode === MODE_AUTO) {
    if (auto) s.t = (s.t + dt) % LOOP;
    scriptFrame(reduced ? REDUCED_AT : s.t, g, f);
    return false;
  }
  if (f.tap[2] >= 0) f.tap[2] += dt;
  if (s.mode === MODE_LIVE) {
    f.film += dt / LOOP;
    f.wandFilm = 1;
    let busy = false;
    for (let i = 0; i < 3; i += 1) if (s.spawn[i] >= 0 && s.spawn[i] < INFLATE + 0.25) busy = true;
    for (let i = 0; i < 3; i += 1) {
      const p = f.poses[i];
      if (f.stars[i] >= 0) f.stars[i] = f.stars[i] + dt < STAR_FOR ? f.stars[i] + dt : -1;
      if (s.spawn[i] >= 0) {
        s.spawn[i] += dt;
        inflatePose(g, i, s.spawn[i], TRAVEL, f.film, p);
        if (s.spawn[i] < INFLATE + 0.25) f.wandFilm = 0;
        if (s.spawn[i] >= INFLATE + TRAVEL) {
          s.spawn[i] = -1;
          s.base[i * 2] = g.rest[i * 3];
          s.base[i * 2 + 1] = g.rest[i * 3 + 1];
        }
      } else if (p[4] >= 0) {
        p[4] += dt;
        // Once its droplets are gone, a new one is blown at the wand (one at a time).
        if (p[4] > 1.3 && !busy) {
          s.spawn[i] = 0;
          busy = true;
          inflatePose(g, i, 0, TRAVEL, f.film, p);
        }
      } else {
        p[0] = s.base[i * 2] + bobX(i, f.film);
        p[1] = s.base[i * 2 + 1] + bobY(i, f.film);
        p[7] = 0.012;
      }
    }
    // The score's digits roll to the new value and rest exactly on it.
    f.score += (s.score - f.score) * Math.min(1, dt * 9);
    if (Math.abs(s.score - f.score) < 0.002) f.score = s.score;
    if (auto && s.now - s.last > IDLE_BEFORE_BACK) {
      s.mode = MODE_BACK;
      s.back = 0;
      for (let i = 0; i < 3; i += 1) {
        const p = f.poses[i];
        const whole = p[4] < 0 && s.spawn[i] < 0;
        s.from[i * 3] = whole ? p[0] - bobX(i, f.film) : g.rest[i * 3];
        s.from[i * 3 + 1] = whole ? p[1] - bobY(i, f.film) : g.rest[i * 3 + 1];
        s.from[i * 3 + 2] = whole ? 1 : 0;
      }
      s.filmFrom = f.film;
      s.scoreFrom = f.score;
    }
    return false;
  }
  // Back to rest: whole bubbles glide home; missing ones fade in at their place.
  s.back = Math.min(1, s.back + dt / BACK_FOR);
  const k = easeInOut(s.back);
  f.film = s.filmFrom + (Math.ceil(s.filmFrom) - s.filmFrom) * k;
  f.wandFilm = Math.max(f.wandFilm, k);
  for (let i = 0; i < 3; i += 1) {
    const p = f.poses[i];
    const bx = s.from[i * 3] + (g.rest[i * 3] - s.from[i * 3]) * k;
    const by = s.from[i * 3 + 1] + (g.rest[i * 3 + 1] - s.from[i * 3 + 1]) * k;
    restPose(g, i, bx - g.rest[i * 3], by - g.rest[i * 3 + 1], f.film, p);
    if (s.from[i * 3 + 2] < 0.5) {
      p[3] = k;
      p[2] = g.rest[i * 3 + 2] * (0.92 + 0.08 * k);
    }
    if (f.stars[i] >= 0) f.stars[i] = f.stars[i] + dt < STAR_FOR ? f.stars[i] + dt : -1;
  }
  f.score = s.scoreFrom * (1 - k);
  if (s.back >= 1) {
    s.mode = MODE_AUTO;
    s.t = 0;
    s.score = 0;
    for (let i = 0; i < 3; i += 1) s.spawn[i] = -1;
    scriptFrame(0, g, f);
    return true;
  }
  return false;
}

export function bubbleUniforms(f: BubbleFrame, g: BubbleGeo) {
  'worklet';
  const P = f.poses;
  return {
    size: [Math.max(1, g.w), Math.max(1, g.h)],
    b0: [P[0][0], P[0][1], P[0][2], P[0][3]],
    b1: [P[1][0], P[1][1], P[1][2], P[1][3]],
    b2: [P[2][0], P[2][1], P[2][2], P[2][3]],
    k0: [P[0][4], P[0][5], P[0][6], P[0][7]],
    k1: [P[1][4], P[1][5], P[1][6], P[1][7]],
    k2: [P[2][4], P[2][5], P[2][6], P[2][7]],
    wand: [g.wx, g.wy, g.wr, f.wandFilm],
    tap: [f.tap[0], f.tap[1], f.tap[2], 0],
    film: f.film - Math.floor(f.film),
  };
}

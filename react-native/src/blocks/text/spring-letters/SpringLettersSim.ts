/**
 * The letters' physics, kept free of React and Skia (and of the font: each letter is just a box)
 * so it ports step for step to SwiftUI and Compose. The component calls `stepLetters` once a
 * frame and draws what it finds.
 *
 * Every letter is a rigid body (position, angle and their speeds) under gravity, standing on a
 * floor. A soft spring pulls it back over its slot and another stands it upright; springs between
 * the facing edges of neighbours chain the word together, so a letter that is dragged tugs the
 * ones beside it and they swing on the links. Contact with the floor is a corner hitting a plane:
 * an impulse with bounce and friction, which also tips the letter.
 */

export const MAX_LETTERS = 8;
export const WORDS = ['BOUNCE', 'SPRING', 'WOBBLE', 'JIGGLE'];

// Gravity (pt/s²).
const GRAVITY = 2600;
// The spring over a letter's slot: across (stiffness, damping) and, gently, up and down.
const HOME_X = 60;
const HOME_X_DAMPING = 4.6;
const HOME_Y = 10;
const AIR = 0.5;
// Standing upright: angular stiffness and damping (1/s², 1/s).
const UPRIGHT = 150;
const UPRIGHT_DAMPING = 8;
// Links between neighbours, edge to edge.
const LINK = 75;
const LINK_DAMPING = 5;
// The finger's hold on a letter: stiff enough to follow, soft enough to lag and swing.
const GRAB = 330;
// A touch this far (pt) outside every letter still picks up the nearest one.
const GRAB_REACH = 26;
const GRAB_DAMPING = 20;
// On release, the letter takes this share of the finger's speed; speeds are capped.
const THROW_SHARE = 0.65;
const MAX_SPEED = 2800;
// Floor: bounce for a hard landing, none for a soft one, and friction.
const BOUNCE = 0.42;
const BOUNCE_BELOW = 140;
const FRICTION = 0.5;
// Corners are rounded by this share of the letter's smaller half-size where it meets the floor.
const ROUNDING = 0.6;
// A flick up from the floor throws every letter, the nearest first, the wave crossing at this speed (pt/s).
const FLICK_MIN = 450;
const FLICK_WAVE = 1300;
const FLICK_POWER = 0.7;
const FLICK_MAX = 1750;
// A word change: old letters shrink and turn away over LEAVE_TIME, new ones drop in one by one.
export const LEAVE_TIME = 0.45;
const LEAVE_STAGGER = 0.035;
const DROP_DELAY = 0.12;
const DROP_STAGGER = 0.075;
const DROP_FROM = 140;

const DT_CAP = 1 / 30;
const SUBSTEPS = 6;

// autoPlay: drag the O up and fling it, flick the floor, change word, drag the G and throw it,
// change back, rest.
export const LOOP = 12;
// [start, end, letter, release vx, release vy, then (dx, dy) pairs the finger passes through]
const DRAGS: number[][] = [
  [0.6, 1.75, 1, 1100, -900, 0, 0, 30, -90, 70, -190, 90, -230],
  [6.2, 7.35, 5, -1000, -700, 0, 0, -30, -80, -90, -170, -130, -210],
];
const FLICK_AT = 3.2;
const SWAPS: number[][] = [
  // [time, word]
  [4.7, 1],
  [8.5, 0],
];
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 3.5;

export type LettersSim = {
  time: number;
  clock: number;
  hold: number;
  looping: boolean;
  still: boolean;
  seed: number;
  // Screen: where the floor is, the side walls and the ceiling.
  floor: number;
  left: number;
  right: number;
  ceiling: number;
  // The word on the floor.
  word: number;
  count: number;
  x: number[];
  y: number[];
  a: number[];
  vx: number[];
  vy: number[];
  va: number[];
  restX: number[];
  restY: number[];
  halfW: number[];
  halfH: number[];
  // Seconds before each new letter drops in (it is hidden until then); 0 once it has.
  wait: number[];
  // Seconds since the word was placed; with reduced motion the new word fades in over this.
  arrive: number;
  // A pending kick from a floor flick: its strength and the seconds until it arrives.
  kick: number[];
  kickIn: number[];
  // The word leaving: its letters' poses and how far through leaving they are.
  leftWord: number;
  leftCount: number;
  lx: number[];
  ly: number[];
  la: number[];
  leaveAge: number;
  // The finger: down, where, how fast, and which letter it holds (−1 none) at what local point.
  down: boolean;
  scripted: boolean;
  fx: number;
  fy: number;
  fvx: number;
  fvy: number;
  held: number;
  gx: number;
  gy: number;
  // Where the scripted finger came down, which its path is measured from.
  dragX: number;
  dragY: number;
  // Things a real finger did, counted; the component answers each with a haptic.
  userGrabs: number;
  userFlicks: number;
  userSwaps: number;
  // The last double tap, drawn as a ripple (seconds since, or −1).
  tapAge: number;
  tapX: number;
  tapY: number;
};

function filled(n: number, v: number) {
  'worklet';
  const a: number[] = [];
  for (let i = 0; i < n; i += 1) a.push(v);
  return a;
}

/** A seeded random number in [0, 1) (mulberry32), so every loop plays the same. */
function random(sim: LettersSim) {
  'worklet';
  sim.seed = (sim.seed + 0x6d2b79f5) | 0;
  let t = sim.seed;
  t = Math.imul(t ^ (t >>> 15), t | 1);
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

export function makeLettersSim(looping: boolean, still: boolean): LettersSim {
  'worklet';
  return {
    time: 0,
    clock: 0,
    hold: 0,
    looping,
    still,
    seed: 7,
    floor: 0,
    left: 0,
    right: 0,
    ceiling: 0,
    word: 0,
    count: 0,
    x: filled(MAX_LETTERS, 0),
    y: filled(MAX_LETTERS, 0),
    a: filled(MAX_LETTERS, 0),
    vx: filled(MAX_LETTERS, 0),
    vy: filled(MAX_LETTERS, 0),
    va: filled(MAX_LETTERS, 0),
    restX: filled(MAX_LETTERS, 0),
    restY: filled(MAX_LETTERS, 0),
    halfW: filled(MAX_LETTERS, 0),
    halfH: filled(MAX_LETTERS, 0),
    wait: filled(MAX_LETTERS, 0),
    arrive: 0,
    kick: filled(MAX_LETTERS, 0),
    kickIn: filled(MAX_LETTERS, -1),
    leftWord: -1,
    leftCount: 0,
    lx: filled(MAX_LETTERS, 0),
    ly: filled(MAX_LETTERS, 0),
    la: filled(MAX_LETTERS, 0),
    leaveAge: -1,
    down: false,
    scripted: false,
    fx: 0,
    fy: 0,
    fvx: 0,
    fvy: 0,
    held: -1,
    gx: 0,
    gy: 0,
    dragX: 0,
    dragY: 0,
    userGrabs: 0,
    userFlicks: 0,
    userSwaps: 0,
    tapAge: -1,
    tapX: 0,
    tapY: 0,
  };
}

/**
 * Lays a word out on the floor. `boxes` holds each letter's width and height (pt) for every word,
 * flat: boxes[word][2i], boxes[word][2i + 1]. With `drop`, the letters fall in from above;
 * otherwise they stand in their slots at once.
 */
export function placeWord(sim: LettersSim, word: number, boxes: number[][], gap: number, drop: boolean) {
  'worklet';
  const sizes = boxes[word];
  if (!sizes) return;
  const n = Math.min(MAX_LETTERS, Math.floor(sizes.length / 2));
  let total = gap * (n - 1);
  for (let i = 0; i < n; i += 1) total += Math.max(2, sizes[i * 2]);
  let x = (sim.left + sim.right) / 2 - total / 2;
  sim.word = word;
  sim.count = n;
  sim.arrive = drop ? 0 : 1;
  for (let i = 0; i < n; i += 1) {
    // Never a zero box (a glyph that failed to load): the physics divides by its size.
    const hw = Math.max(1, sizes[i * 2] / 2);
    const hh = Math.max(1, sizes[i * 2 + 1] / 2);
    sim.halfW[i] = hw;
    sim.halfH[i] = hh;
    sim.restX[i] = x + hw;
    sim.restY[i] = sim.floor - hh;
    x += hw * 2 + gap;
    sim.kickIn[i] = -1;
    if (drop && !sim.still) {
      sim.wait[i] = DROP_DELAY + i * DROP_STAGGER;
      sim.x[i] = sim.restX[i] + (random(sim) - 0.5) * 30;
      sim.y[i] = sim.ceiling - DROP_FROM - hh;
      sim.a[i] = (random(sim) - 0.5) * 0.9;
      sim.vx[i] = 0;
      sim.vy[i] = 0;
      sim.va[i] = (random(sim) - 0.5) * 6;
    } else {
      sim.wait[i] = 0;
      sim.x[i] = sim.restX[i];
      sim.y[i] = sim.restY[i];
      sim.a[i] = 0;
      sim.vx[i] = 0;
      sim.vy[i] = 0;
      sim.va[i] = 0;
    }
  }
}

/** Swaps to word `word`: the letters on the floor leave, the new ones drop in. */
export function swapWord(sim: LettersSim, word: number, boxes: number[][], gap: number, user: boolean) {
  'worklet';
  sim.leftWord = sim.word;
  sim.leftCount = sim.count;
  for (let i = 0; i < sim.count; i += 1) {
    sim.lx[i] = sim.x[i];
    sim.ly[i] = sim.y[i];
    sim.la[i] = sim.a[i];
  }
  sim.leaveAge = 0;
  sim.held = -1;
  placeWord(sim, word, boxes, gap, true);
  if (user) {
    sim.userSwaps += 1;
    sim.hold = USER_HOLD;
  }
}

/** The letter under a point, topmost first, or −1. */
function letterAt(sim: LettersSim, px: number, py: number) {
  'worklet';
  for (let k = sim.count - 1; k >= 0; k -= 1) {
    const i = k;
    if (sim.wait[i] > 0) continue;
    const dx = px - sim.x[i];
    const dy = py - sim.y[i];
    const c = Math.cos(-sim.a[i]);
    const s = Math.sin(-sim.a[i]);
    const lx = dx * c - dy * s;
    const ly = dx * s + dy * c;
    if (Math.abs(lx) < sim.halfW[i] + 12 && Math.abs(ly) < sim.halfH[i] + 12) return i;
  }
  // Nothing under the finger: the nearest letter, if the finger is close to it.
  let best = -1;
  let bestDistance = Infinity;
  for (let i = 0; i < sim.count; i += 1) {
    if (sim.wait[i] > 0) continue;
    const d = Math.hypot(px - sim.x[i], py - sim.y[i]);
    if (d < Math.max(sim.halfW[i], sim.halfH[i]) + GRAB_REACH && d < bestDistance) {
      best = i;
      bestDistance = d;
    }
  }
  return best;
}

/** Takes hold of letter `i` at the finger. */
function grab(sim: LettersSim, i: number) {
  'worklet';
  const dx = sim.fx - sim.x[i];
  const dy = sim.fy - sim.y[i];
  const c = Math.cos(-sim.a[i]);
  const s = Math.sin(-sim.a[i]);
  sim.held = i;
  sim.gx = dx * c - dy * s;
  sim.gy = dx * s + dy * c;
}

/** Lets go of the held letter, throwing it with the finger's speed. */
function letGo(sim: LettersSim) {
  'worklet';
  const i = sim.held;
  if (i < 0) return;
  sim.held = -1;
  if (sim.still) return;
  let vx = sim.vx[i] * (1 - THROW_SHARE) + sim.fvx * THROW_SHARE;
  let vy = sim.vy[i] * (1 - THROW_SHARE) + sim.fvy * THROW_SHARE;
  const speed = Math.hypot(vx, vy);
  if (speed > MAX_SPEED) {
    vx *= MAX_SPEED / speed;
    vy *= MAX_SPEED / speed;
  }
  sim.vx[i] = vx;
  sim.vy[i] = vy;
}

/** A flick up off the floor at `x` with upward speed `speed` (pt/s): every letter jumps, nearest first. */
function flick(sim: LettersSim, x: number, speed: number, user: boolean) {
  'worklet';
  if (sim.still) return;
  const power = Math.min(FLICK_MAX, 380 + Math.abs(speed) * FLICK_POWER);
  const width = Math.max(1, sim.right - sim.left);
  for (let i = 0; i < sim.count; i += 1) {
    const far = Math.abs(sim.x[i] - x);
    sim.kick[i] = power * (1 - (0.3 * far) / width);
    sim.kickIn[i] = far / FLICK_WAVE;
  }
  if (user) sim.userFlicks += 1;
}

export function fingerDown(sim: LettersSim, x: number, y: number) {
  'worklet';
  sim.down = true;
  sim.scripted = false;
  sim.fx = x;
  sim.fy = y;
  sim.fvx = 0;
  sim.fvy = 0;
  sim.hold = USER_HOLD;
  const i = letterAt(sim, x, y);
  if (i >= 0) {
    grab(sim, i);
    sim.userGrabs += 1;
  } else {
    sim.held = -1;
  }
}

export function fingerMove(sim: LettersSim, x: number, y: number, vx: number, vy: number) {
  'worklet';
  if (!sim.down || sim.scripted) return;
  sim.fx = x;
  sim.fy = y;
  sim.fvx = vx;
  sim.fvy = vy;
  sim.hold = USER_HOLD;
}

/** The finger lifts: a held letter is thrown; a flick up from empty floor makes every letter jump. */
export function fingerUp(sim: LettersSim, vx: number, vy: number) {
  'worklet';
  if (!sim.down || sim.scripted) return;
  sim.down = false;
  sim.fvx = vx;
  sim.fvy = vy;
  sim.hold = USER_HOLD;
  if (sim.held >= 0) letGo(sim);
  else if (vy < -FLICK_MIN) flick(sim, sim.fx, vy, true);
}

/** A double tap at a point: the next word. */
export function doubleTap(sim: LettersSim, x: number, y: number, boxes: number[][], gap: number) {
  'worklet';
  if (boxes.length === 0) return;
  sim.tapAge = 0;
  sim.tapX = x;
  sim.tapY = y;
  swapWord(sim, (sim.word + 1) % boxes.length, boxes, gap, true);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

/** Did the loop clock pass `at` going from `before` to `now` (wrapping at LOOP)? */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : before < at || now >= at;
}

/** The scripted hand: drags, a flick and two word changes, at their times in the loop. */
function playScript(sim: LettersSim, before: number, now: number, boxes: number[][], gap: number) {
  'worklet';
  for (let n = 0; n < DRAGS.length; n += 1) {
    const d = DRAGS[n];
    const start = d[0];
    const end = d[1];
    const letter = d[2];
    if (crossed(before, now, start) && letter < sim.count) {
      // Touch down on the letter's middle, a little above centre.
      sim.down = true;
      sim.scripted = true;
      sim.fx = sim.x[letter];
      sim.fy = sim.y[letter] - sim.halfH[letter] * 0.25;
      sim.fvx = 0;
      sim.fvy = 0;
      grab(sim, letter);
      sim.gx = 0;
      sim.gy = -sim.halfH[letter] * 0.25;
      sim.dragX = sim.fx;
      sim.dragY = sim.fy;
    }
    if (sim.scripted && sim.down && sim.held === letter && now > start && now < end) {
      // Through the keyframes, eased, from where the finger first came down.
      const keys = (d.length - 5) / 2;
      const t = easeInOut((now - start) / (end - start)) * (keys - 1);
      const k = Math.min(keys - 2, Math.floor(t));
      const f = t - k;
      const ox = sim.dragX;
      const oy = sim.dragY;
      const nx = ox + d[5 + k * 2] + (d[7 + k * 2] - d[5 + k * 2]) * f;
      const ny = oy + d[6 + k * 2] + (d[8 + k * 2] - d[6 + k * 2]) * f;
      sim.fx = nx;
      sim.fy = ny;
    }
    if (crossed(before, now, end) && sim.scripted && sim.down) {
      sim.fvx = d[3];
      sim.fvy = d[4];
      sim.down = false;
      letGo(sim);
    }
  }
  if (crossed(before, now, FLICK_AT)) flick(sim, (sim.left + sim.right) / 2, -1500, false);
  for (let n = 0; n < SWAPS.length; n += 1) {
    if (crossed(before, now, SWAPS[n][0])) {
      sim.tapAge = 0;
      sim.tapX = (sim.left + sim.right) / 2;
      sim.tapY = sim.floor - 70;
      swapWord(sim, SWAPS[n][1], boxes, gap, false);
    }
  }
}

/** Applies force (fx, fy) at world point (px, py) of letter `i` for `h` seconds. */
function push(sim: LettersSim, i: number, fx: number, fy: number, px: number, py: number, h: number) {
  'worklet';
  const inertia = (4 * (sim.halfW[i] * sim.halfW[i] + sim.halfH[i] * sim.halfH[i])) / 12;
  const rx = px - sim.x[i];
  const ry = py - sim.y[i];
  sim.vx[i] += fx * h;
  sim.vy[i] += fy * h;
  sim.va[i] += ((rx * fy - ry * fx) / inertia) * h;
}

/** Point on letter `i` at local (lx, ly), in world space. */
function worldX(sim: LettersSim, i: number, lx: number, ly: number) {
  'worklet';
  return sim.x[i] + lx * Math.cos(sim.a[i]) - ly * Math.sin(sim.a[i]);
}

function worldY(sim: LettersSim, i: number, lx: number, ly: number) {
  'worklet';
  return sim.y[i] + lx * Math.sin(sim.a[i]) + ly * Math.cos(sim.a[i]);
}

/** Corner contact with the floor, walls and ceiling: push out, bounce, rub. */
function collide(sim: LettersSim, i: number) {
  'worklet';
  const inertia = (4 * (sim.halfW[i] * sim.halfW[i] + sim.halfH[i] * sim.halfH[i])) / 12;
  // The letter's lowest point, treating it as a box with well-rounded corners: as it tips it
  // rolls on a curve instead of see-sawing between corners, so it rights itself.
  const round = Math.min(sim.halfW[i], sim.halfH[i]) * ROUNDING;
  const c = Math.cos(sim.a[i]);
  const sn = Math.sin(sim.a[i]);
  // "Down" in the letter's own frame.
  const dx = sn;
  const dy = c;
  const lx = (sim.halfW[i] - round) * Math.sign(dx) + round * dx;
  const ly = (sim.halfH[i] - round) * Math.sign(dy) + round * dy;
  const cx = lx * c - ly * sn;
  const cy = lx * sn + ly * c;
  const deepest = sim.y[i] + cy - sim.floor;
  if (deepest > 0) {
    sim.y[i] -= deepest;
    // Speed of the contact point, into the floor (+y).
    const vcx = sim.vx[i] - sim.va[i] * cy;
    const vcy = sim.vy[i] + sim.va[i] * cx;
    if (vcy > 0) {
      const e = vcy > BOUNCE_BELOW ? BOUNCE : 0;
      const j = ((1 + e) * vcy) / (1 + (cx * cx) / inertia);
      sim.vy[i] -= j;
      sim.va[i] -= (cx * j) / inertia;
      // Friction along the floor, no more than the push allows.
      const jt0 = -vcx / (1 + (cy * cy) / inertia);
      const jt = Math.max(-FRICTION * j, Math.min(FRICTION * j, jt0));
      sim.vx[i] += jt;
      sim.va[i] -= (cy * jt) / inertia;
    }
  }
  // Walls and ceiling, against the letter's reach.
  const reach = Math.hypot(sim.halfW[i], sim.halfH[i]) * 0.8;
  if (sim.x[i] - reach < sim.left) {
    sim.x[i] = sim.left + reach;
    if (sim.vx[i] < 0) sim.vx[i] *= -0.5;
  }
  if (sim.x[i] + reach > sim.right) {
    sim.x[i] = sim.right - reach;
    if (sim.vx[i] > 0) sim.vx[i] *= -0.5;
  }
  if (sim.y[i] - reach < sim.ceiling && sim.vy[i] < 0 && sim.held !== i) {
    sim.y[i] = sim.ceiling + reach;
    if (sim.vy[i] < 0) sim.vy[i] *= -0.5;
  }
}

/** One substep of forces for every letter that is on the floor (not still waiting to drop). */
function forces(sim: LettersSim, h: number) {
  'worklet';
  for (let i = 0; i < sim.count; i += 1) {
    if (sim.wait[i] > 0) continue;
    // Home, upright, gravity, air.
    sim.vx[i] += (HOME_X * (sim.restX[i] - sim.x[i]) - HOME_X_DAMPING * sim.vx[i]) * h;
    sim.vy[i] += (GRAVITY + HOME_Y * (sim.restY[i] - sim.y[i]) - AIR * sim.vy[i]) * h;
    sim.va[i] += (-UPRIGHT * sim.a[i] - UPRIGHT_DAMPING * sim.va[i]) * h;
  }
  // Links: the right edge of each letter to the left edge of the next.
  for (let i = 0; i < sim.count - 1; i += 1) {
    const j = i + 1;
    if (sim.wait[i] > 0 || sim.wait[j] > 0) continue;
    const ax = worldX(sim, i, sim.halfW[i], 0);
    const ay = worldY(sim, i, sim.halfW[i], 0);
    const bx = worldX(sim, j, -sim.halfW[j], 0);
    const by = worldY(sim, j, -sim.halfW[j], 0);
    const rest = sim.restX[j] - sim.halfW[j] - (sim.restX[i] + sim.halfW[i]);
    const dx = bx - ax;
    const dy = by - ay;
    const length = Math.hypot(dx, dy) || 1e-3;
    const ux = dx / length;
    const uy = dy / length;
    // Speeds of the two link ends, and how fast the link is stretching.
    const vax = sim.vx[i] - sim.va[i] * (ay - sim.y[i]);
    const vay = sim.vy[i] + sim.va[i] * (ax - sim.x[i]);
    const vbx = sim.vx[j] - sim.va[j] * (by - sim.y[j]);
    const vby = sim.vy[j] + sim.va[j] * (bx - sim.x[j]);
    const stretch = (vbx - vax) * ux + (vby - vay) * uy;
    const f = LINK * (length - rest) + LINK_DAMPING * stretch;
    push(sim, i, f * ux, f * uy, ax, ay, h);
    push(sim, j, -f * ux, -f * uy, bx, by, h);
  }
  // The finger, on the point where it took hold.
  const g = sim.held;
  if (g >= 0 && sim.wait[g] <= 0) {
    const px = worldX(sim, g, sim.gx, sim.gy);
    const py = worldY(sim, g, sim.gx, sim.gy);
    const vpx = sim.vx[g] - sim.va[g] * (py - sim.y[g]);
    const vpy = sim.vy[g] + sim.va[g] * (px - sim.x[g]);
    const fx = GRAB * (sim.fx - px) - GRAB_DAMPING * vpx;
    const fy = GRAB * (sim.fy - py) - GRAB_DAMPING * vpy;
    push(sim, g, fx, fy, px, py, h);
  }
}

/** One frame: the script, the drops and kicks, the physics, the leaving word. */
export function stepLetters(sim: LettersSim, frameDt: number, boxes: number[][], gap: number) {
  'worklet';
  const dt = Math.min(Math.max(frameDt, 0), DT_CAP);
  if (dt <= 0) return;
  sim.time += dt;
  sim.arrive += dt;

  if (sim.hold > 0) {
    if (!sim.down) sim.hold -= dt;
    if (sim.hold <= 0 && sim.looping) {
      // The loop starts over from its first word, at rest.
      sim.clock = 0;
      if (sim.word !== 0) swapWord(sim, 0, boxes, gap, false);
    }
  } else if (sim.looping) {
    const before = sim.clock;
    sim.clock = (sim.clock + dt) % LOOP;
    playScript(sim, before, sim.clock, boxes, gap);
  }

  for (let i = 0; i < sim.count; i += 1) {
    if (sim.wait[i] > 0) {
      sim.wait[i] = Math.max(0, sim.wait[i] - dt);
    }
    if (sim.kickIn[i] >= 0) {
      sim.kickIn[i] -= dt;
      if (sim.kickIn[i] < 0 && sim.wait[i] <= 0) {
        sim.vy[i] = -sim.kick[i];
        sim.vx[i] += (random(sim) - 0.5) * 160;
        sim.va[i] += (random(sim) - 0.5) * 9;
      }
    }
  }

  const h = dt / SUBSTEPS;
  for (let step = 0; step < SUBSTEPS; step += 1) {
    forces(sim, h);
    for (let i = 0; i < sim.count; i += 1) {
      if (sim.wait[i] > 0) continue;
      sim.x[i] += sim.vx[i] * h;
      sim.y[i] += sim.vy[i] * h;
      sim.a[i] += sim.va[i] * h;
      collide(sim, i);
    }
  }

  if (sim.leaveAge >= 0) {
    sim.leaveAge += dt;
    if (sim.leaveAge > LEAVE_TIME + LEAVE_STAGGER * MAX_LETTERS) sim.leaveAge = -1;
  }
  if (sim.tapAge >= 0) {
    sim.tapAge += dt;
    if (sim.tapAge > 0.6) sim.tapAge = -1;
  }
}

/** How far letter `i` of the leaving word is through leaving, 0 … 1. */
export function leaving(sim: LettersSim, i: number) {
  'worklet';
  if (sim.leaveAge < 0) return 1;
  return Math.min(1, Math.max(0, (sim.leaveAge - i * LEAVE_STAGGER) / LEAVE_TIME));
}

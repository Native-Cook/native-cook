/**
 * The paint floating on the size: a stack of closed outlines, one per drop, oldest first.
 *
 * Every operation is applied once, forward, to every outline's vertices (the marbling maths of
 * Lu, Jaffer et al.), so nothing has to be stored or replayed:
 *
 * - A drop of radius r at c is circle-area preserving: every point p moves out to
 *   c + (p − c)·√(1 + r² / |p − c|²). A drop growing from r₀ to r₁ is the same map with
 *   r² = r₁² − r₀², so a spreading drop is applied in small exact increments.
 * - A tine moving by v pushes every point by v·G·u^d, where d is the point's distance from the
 *   tine's path, u = 2^(−1/c) halves the push every `c` pt (Jaffer's tine line), and G < 1 lets the
 *   tine slide through the paint instead of carrying it. Behind the tine the push also fades over
 *   `wake` pt, so a stroke of any length is local, and a comb is a row of such tines.
 *
 * After each step every touched outline is refined: long edges are split, points on straight runs
 * are dropped, and points far outside the screen are removed when that cannot change what is seen.
 * An outline wholly outside the screen is deleted, and one that surrounds the whole screen becomes
 * the ground colour (with every outline under it). A vertex budget coarsens the refinement when the
 * pattern grows very fine, so the cost of a frame stays bounded.
 *
 * Plain arithmetic on arrays with a fixed tick, so it runs the same, step for step, anywhere.
 */

/** One drop's outline (closed polygon) and its colour, an index into the palette. */
export type Blot = {
  id: number;
  x: number[];
  y: number[];
  color: number;
  /** Bumped every time the outline changes, so a drawing of it can be cached. */
  rev: number;
  moved: boolean;
  /** Bounding box, refreshed whenever the outline is refined. */
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
};

/** A drop still spreading: its centre, final radius and how far through its spread it is. */
type Spread = {
  id: number;
  cx: number;
  cy: number;
  radius: number;
  color: number;
  age: number;
  time: number;
  /** r² already applied. */
  done: number;
};

/** A tine (stylus) or a row of tines (comb). */
export type Tool = {
  /** Distance between tines along the comb's axis, pt (ignored for one tine). */
  spacing: number;
  /** Tines on each side of the middle one: 0 for a stylus. */
  side: number;
  /** The comb's axis (unit vector): its tines sit along it. */
  axisX: number;
  axisY: number;
  /** Distance over which the push halves, pt (Jaffer's c). */
  sharpness: number;
  /** Share of the tine's motion the paint right at the tine follows. */
  gain: number;
  /** Distance behind the tine over which its push fades, pt. */
  wake: number;
};

export type Marble = {
  width: number;
  height: number;
  blots: Blot[];
  spreads: Spread[];
  ground: number;
  nextId: number;
  /** Bumped whenever any outline changes or one is added or removed. */
  rev: number;
  /** Longest edge allowed on screen, pt: grows past `SEGMENT` when the budget is exceeded. */
  segment: number;
  vertices: number;
};

// Longest edge on screen, pt, and the coarsest it may grow to under the budget.
const SEGMENT = 6;
const SEGMENT_MAX = 14;
// A point on a straight run is dropped when it lies within this distance of the chord, pt.
const FLATNESS = 0.08;
// Points closer than this to the last one kept are dropped, pt.
const MIN_GAP = 0.6;
// Total vertices aimed for.
const BUDGET = 16000;
// Hard limits, past the budget's coarsening: beyond them the oldest outline (the bottom of the
// stack) is folded into the ground, so neither the outlines nor their vertices grow without bound.
const HARD_BUDGET = 40000;
const MAX_BLOTS = 160;
// An edge is never split into more parts than this.
const MAX_SPLIT = 256;
// Margin around the screen within which outlines are kept exact, pt.
const MARGIN = 48;
// The tine's push is ignored past this many halvings, and past this many wake lengths behind it.
const REACH = 9;
const WAKE_REACH = 4;

/** The spread of a drop from 0 to 1 over its time: fast at first, then slowing (cubic ease-out on its area). */
function spreadAt(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  const r = 1 - k;
  return 1 - r * r * r;
}

export function createMarble(width: number, height: number, ground: number): Marble {
  'worklet';
  return {
    width,
    height,
    blots: [],
    spreads: [],
    ground,
    nextId: 1,
    rev: 1,
    segment: SEGMENT,
    vertices: 0,
  };
}

/** Clears the tray to its ground colour. */
export function clearMarble(m: Marble, ground: number) {
  'worklet';
  m.blots = [];
  m.spreads = [];
  m.ground = ground;
  m.segment = SEGMENT;
  m.vertices = 0;
  m.rev += 1;
}

/** Drops paint at (x, y): it spreads to `radius` over `time` s. */
export function dropPaint(m: Marble, x: number, y: number, radius: number, color: number, time: number) {
  'worklet';
  m.spreads.push({ id: m.nextId, cx: x, cy: y, radius, color, age: 0, time: Math.max(time, 1e-3), done: 0 });
  m.nextId += 1;
}

/** The drop map for several drops at once, applied to each point in order. */
function pushOut(m: Marble, cx: number[], cy: number[], r2: number[]) {
  'worklet';
  const count = r2.length;
  for (let b = 0; b < m.blots.length; b += 1) {
    const blot = m.blots[b];
    const xs = blot.x;
    const ys = blot.y;
    for (let i = 0; i < xs.length; i += 1) {
      let x = xs[i];
      let y = ys[i];
      for (let k = 0; k < count; k += 1) {
        const dx = x - cx[k];
        const dy = y - cy[k];
        const d2 = dx * dx + dy * dy;
        if (d2 < 1e-9) continue;
        const f = Math.sqrt(1 + r2[k] / d2);
        x = cx[k] + dx * f;
        y = cy[k] + dy * f;
      }
      xs[i] = x;
      ys[i] = y;
    }
    blot.moved = true;
  }
}

/**
 * Spreads every growing drop by one tick. Each spread is the circle-area-preserving drop map with
 * r² = the area it adds this tick / π; all of them are applied to each point in turn, oldest first,
 * in one pass over the outlines.
 */
function spreadDrops(m: Marble, dt: number) {
  'worklet';
  const cx: number[] = [];
  const cy: number[] = [];
  const add: number[] = [];
  for (let i = 0; i < m.spreads.length; i += 1) {
    const s = m.spreads[i];
    s.age += dt;
    const target = s.radius * s.radius * spreadAt(s.age / s.time);
    const r2 = target - s.done;
    if (r2 <= 0) continue;
    if (s.done === 0) {
      // A new drop: the outlines so far make room first, then its own outline appears at its first radius.
      if (add.length > 0) pushOut(m, cx, cy, add);
      cx.length = 0;
      cy.length = 0;
      add.length = 0;
      pushOut(m, [s.cx], [s.cy], [r2]);
      const r = Math.sqrt(r2);
      const n = Math.max(40, Math.ceil((Math.PI * 2 * s.radius) / SEGMENT));
      const xs: number[] = [];
      const ys: number[] = [];
      for (let k = 0; k < n; k += 1) {
        const a = (k / n) * Math.PI * 2;
        xs.push(s.cx + Math.cos(a) * r);
        ys.push(s.cy + Math.sin(a) * r);
      }
      m.blots.push({
        id: s.id,
        x: xs,
        y: ys,
        color: s.color,
        rev: 0,
        moved: true,
        minX: s.cx - r,
        minY: s.cy - r,
        maxX: s.cx + r,
        maxY: s.cy + r,
      });
    } else {
      cx.push(s.cx);
      cy.push(s.cy);
      add.push(r2);
    }
    s.done = target;
  }
  if (add.length > 0) pushOut(m, cx, cy, add);
  let i = 0;
  while (i < m.spreads.length) {
    if (m.spreads[i].age >= m.spreads[i].time) m.spreads.splice(i, 1);
    else i += 1;
  }
}

/** Moves a tool from (ax, ay) to (bx, by), dragging the paint around each of its tines. */
export function moveTool(m: Marble, tool: Tool, ax: number, ay: number, bx: number, by: number) {
  'worklet';
  const vx = bx - ax;
  const vy = by - ay;
  const len = Math.sqrt(vx * vx + vy * vy);
  if (len < 1e-4) return;
  const mx = vx / len;
  const my = vy / len;
  const k = Math.LN2 / tool.sharpness;
  const reach = REACH * tool.sharpness;
  const behind = len + tool.wake * WAKE_REACH;
  const gx = vx * tool.gain;
  const gy = vy * tool.gain;
  const ax2 = tool.axisX;
  const ay2 = tool.axisY;
  const spacing = Math.max(1, tool.spacing);
  const side = tool.side;
  const invWake = 1 / tool.wake;
  for (let b = 0; b < m.blots.length; b += 1) {
    const blot = m.blots[b];
    // Skip an outline whose box lies wholly ahead of the tool's reach or wholly behind its wake
    // (and, for one tine, wholly to one side of it).
    let lo = Infinity;
    let hi = -Infinity;
    let left = Infinity;
    let right = -Infinity;
    for (let c = 0; c < 4; c += 1) {
      const cx = (c & 1 ? blot.maxX : blot.minX) - bx;
      const cy = (c & 2 ? blot.maxY : blot.minY) - by;
      const along = cx * mx + cy * my;
      const across = cx * my - cy * mx;
      if (along < lo) lo = along;
      if (along > hi) hi = along;
      if (across < left) left = across;
      if (across > right) right = across;
    }
    if (lo > reach || hi < -behind) continue;
    if (side === 0 && (left > reach || right < -reach)) continue;
    const xs = blot.x;
    const ys = blot.y;
    let moved = false;
    for (let i = 0; i < xs.length; i += 1) {
      let rx = xs[i] - bx;
      let ry = ys[i] - by;
      let along = rx * mx + ry * my;
      if (along > reach + spacing || along < -behind - spacing) continue;
      if (side > 0) {
        // The nearest tine along the comb's axis.
        let j = Math.round((rx * ax2 + ry * ay2) / spacing);
        if (j > side) j = side;
        else if (j < -side) j = -side;
        rx -= ax2 * j * spacing;
        ry -= ay2 * j * spacing;
        along = rx * mx + ry * my;
        if (along > reach || along < -behind) continue;
      }
      let across = rx * my - ry * mx;
      if (across < 0) across = -across;
      if (across > reach) continue;
      // Ahead of the tine the push falls off with distance, beside its path with the distance
      // across, and behind it also over the wake.
      let w: number;
      if (along > 0) w = Math.exp(-k * Math.sqrt(along * along + across * across));
      else if (along >= -len) w = Math.exp(-k * across);
      else w = Math.exp(-k * across + (along + len) * invWake);
      if (w < 2e-4) continue;
      xs[i] += gx * w;
      ys[i] += gy * w;
      moved = true;
    }
    if (moved) blot.moved = true;
  }
}

/** Which sides of the kept area a point lies outside of, as bits (left 1, right 2, top 4, bottom 8). */
function outside(m: Marble, x: number, y: number) {
  'worklet';
  let code = 0;
  if (x < -MARGIN) code |= 1;
  else if (x > m.width + MARGIN) code |= 2;
  if (y < -MARGIN) code |= 4;
  else if (y > m.height + MARGIN) code |= 8;
  return code;
}

/** Whether (x, y) lies inside the outline (even–odd rule). */
function contains(blot: Blot, x: number, y: number) {
  'worklet';
  const xs = blot.x;
  const ys = blot.y;
  let inside = false;
  for (let i = 0, j = xs.length - 1; i < xs.length; j = i, i += 1) {
    if (ys[i] > y !== ys[j] > y && x < ((xs[j] - xs[i]) * (y - ys[i])) / (ys[j] - ys[i]) + xs[i]) inside = !inside;
  }
  return inside;
}

/** Splits long edges, drops redundant points; returns the new outline (or null when unchanged). */
function refineOutline(m: Marble, blot: Blot) {
  'worklet';
  const xs = blot.x;
  const ys = blot.y;
  const n = xs.length;
  if (n < 3) return;
  const seg = m.segment;
  const flat = FLATNESS * (seg / SEGMENT);
  const ox: number[] = [];
  const oy: number[] = [];
  let lastX = xs[0];
  let lastY = ys[0];
  let lastCode = outside(m, lastX, lastY);
  ox.push(lastX);
  oy.push(lastY);
  for (let i = 1; i <= n; i += 1) {
    const closing = i === n;
    const px = closing ? xs[0] : xs[i];
    const py = closing ? ys[0] : ys[i];
    const code = outside(m, px, py);
    if (!closing) {
      const nx = xs[(i + 1) % n];
      const ny = ys[(i + 1) % n];
      const nextCode = outside(m, nx, ny);
      // Outside the same side as the point before and after it: the chord stays outside too.
      if ((lastCode & code & nextCode) !== 0) continue;
      const cx = nx - lastX;
      const cy = ny - lastY;
      const chord = Math.sqrt(cx * cx + cy * cy);
      const gx = px - lastX;
      const gy = py - lastY;
      const gap = Math.sqrt(gx * gx + gy * gy);
      if (gap < MIN_GAP) continue;
      // On a straight run, short enough to stand for: the point adds nothing.
      if (chord < seg && chord > 1e-6 && Math.abs((px - lastX) * cy - (py - lastY) * cx) / chord < flat) continue;
    }
    // Split the edge from the last point kept when it is long and may be on screen.
    if ((lastCode & code) === 0) {
      const ex = px - lastX;
      const ey = py - lastY;
      const len = Math.sqrt(ex * ex + ey * ey);
      if (len > seg) {
        const parts = Math.min(MAX_SPLIT, Math.ceil(len / seg));
        for (let s = 1; s < parts; s += 1) {
          const t = s / parts;
          ox.push(lastX + (px - lastX) * t);
          oy.push(lastY + (py - lastY) * t);
        }
      }
    }
    if (closing) break;
    ox.push(px);
    oy.push(py);
    lastX = px;
    lastY = py;
    lastCode = code;
  }
  blot.x = ox;
  blot.y = oy;
}

/** Refines what moved, deletes what left the screen and folds a screen-covering outline into the ground. */
function tidy(m: Marble) {
  'worklet';
  let total = 0;
  let changed = false;
  for (let b = m.blots.length - 1; b >= 0; b -= 1) {
    const blot = m.blots[b];
    if (!blot.moved) {
      total += blot.x.length;
      continue;
    }
    blot.moved = false;
    blot.rev += 1;
    changed = true;
    refineOutline(m, blot);
    const xs = blot.x;
    const ys = blot.y;
    let all = 15;
    let any = 0;
    let inView = false;
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (let i = 0; i < xs.length; i += 1) {
      const x = xs[i];
      const y = ys[i];
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
      const c = outside(m, x, y);
      all &= c;
      if (c === 0) inView = true;
      any |= c;
    }
    blot.minX = minX;
    blot.minY = minY;
    blot.maxX = maxX;
    blot.maxY = maxY;
    if (!inView) {
      if (all === 0 && any !== 0 && contains(blot, m.width / 2, m.height / 2)) {
        // It covers the whole screen: it is the ground now, and nothing under it shows.
        m.ground = blot.color;
        m.blots.splice(0, b + 1);
        break;
      }
      m.blots.splice(b, 1);
      continue;
    }
    total += xs.length;
  }
  // Too many outlines or vertices even at the coarsest edges: the bottom one becomes the ground.
  while (m.blots.length > 1 && (m.blots.length > MAX_BLOTS || total > HARD_BUDGET)) {
    const bottom = m.blots[0];
    m.ground = bottom.color;
    total -= bottom.x.length;
    m.blots.splice(0, 1);
    changed = true;
  }
  if (changed) m.rev += 1;
  m.vertices = total;
  // Coarsen the outlines while the pattern is over budget; refine again once it is not.
  if (total > BUDGET) m.segment = Math.min(SEGMENT_MAX, m.segment * 1.08);
  else if (total < BUDGET * 0.7) m.segment = Math.max(SEGMENT, m.segment / 1.02);
}

/** One fixed tick: drops spread, then everything that moved is refined. Tools move through `moveTool`. */
export function stepMarble(m: Marble, dt: number) {
  'worklet';
  if (m.spreads.length > 0) spreadDrops(m, dt);
  tidy(m);
}

/** A small, fast, seeded random in [0, 1): xorshift32 on `state[0]`. */
export function seeded(state: number[]) {
  'worklet';
  let x = state[0] | 0;
  x ^= x << 13;
  x ^= x >>> 17;
  x ^= x << 5;
  state[0] = x;
  return (x >>> 0) / 4294967296;
}

// ─── The self-playing script ─────────────────────────────────────────────────────────────────────
// One loop, LOOP s long, from a clean tray back to a clean tray: a sprinkle of drops in a loose
// grid, a few drops on drops for rings, a coarse comb down and back up (gel-git), a fine comb
// across (nonpareil), then a print. Positions are shares of the tray; radii shares of its width.

export const LOOP = 16;
/** When the sheet is laid on; the print takes `PRINT_TIME` s from there. */
export const PRINT_AT = 11.2;

export type ScriptDrop = { at: number; x: number; y: number; r: number; color: number };

/** A comb pulled in a straight line from (x0, y0) to (x1, y1) between two times. */
export type CombPass = {
  from: number;
  to: number;
  x0: number;
  y0: number;
  x1: number;
  y1: number;
  /** The comb's axis is horizontal (tines side by side across the screen) or vertical. */
  horizontal: boolean;
  /** Tine spacing as a share of the width. */
  spacing: number;
  sharpness: number;
  gain: number;
  wake: number;
};

// Colours cycled by the sprinkle (indices into the palette; 0 is the ground).
const SPRINKLE_COLORS = [1, 2, 3, 4, 6, 5, 1, 3, 2, 7, 4, 1];
const GRID_COLS = 4;
const GRID_ROWS = 6;
const SPRINKLE_START = 0.35;
const SPRINKLE_GAP = 0.12;

function buildDrops(): ScriptDrop[] {
  const state = [0x5eed1234];
  const cells: number[] = [];
  for (let i = 0; i < GRID_COLS * GRID_ROWS; i += 1) cells.push(i);
  // A fixed shuffle, so the sprinkle lands in a lively order.
  for (let i = cells.length - 1; i > 0; i -= 1) {
    const j = Math.floor(seeded(state) * (i + 1));
    const t = cells[i];
    cells[i] = cells[j];
    cells[j] = t;
  }
  const drops: ScriptDrop[] = [];
  cells.forEach((cell, k) => {
    const col = cell % GRID_COLS;
    const row = Math.floor(cell / GRID_COLS);
    const stagger = row % 2 === 0 ? 0 : 0.5;
    drops.push({
      at: SPRINKLE_START + k * SPRINKLE_GAP,
      x: (col + 0.25 + stagger * 0.5 + seeded(state) * 0.3) / GRID_COLS,
      y: (row + 0.3 + seeded(state) * 0.4) / GRID_ROWS,
      r: 0.15 + seeded(state) * 0.03,
      color: SPRINKLE_COLORS[k % SPRINKLE_COLORS.length],
    });
  });
  // Drops on drops: rings at three spots.
  const rings = [
    [0.3, 0.3, 3, 2],
    [0.72, 0.52, 1, 7],
    [0.38, 0.76, 6, 4],
  ];
  rings.forEach(([x, y, a, b], k) => {
    const at = 3.5 + k * 0.32;
    drops.push({ at, x, y, r: 0.085, color: a });
    drops.push({ at: at + 0.16, x, y, r: 0.06, color: b });
  });
  return drops;
}

export const SCRIPT_DROPS = buildDrops();

export const SCRIPT_PASSES: CombPass[] = [
  // Gel-git: a coarse comb down the tray, then back up between its first lines.
  { from: 4.9, to: 6.7, x0: 0.5, y0: -0.08, x1: 0.5, y1: 1.08, horizontal: true, spacing: 0.15, sharpness: 9, gain: 0.8, wake: 70 },
  { from: 6.95, to: 8.75, x0: 0.575, y0: 1.08, x1: 0.575, y1: -0.08, horizontal: true, spacing: 0.15, sharpness: 9, gain: 0.8, wake: 70 },
  // Nonpareil: a fine comb across.
  { from: 9.0, to: 10.7, x0: -0.12, y0: 0.5, x1: 1.12, y1: 0.5, horizontal: false, spacing: 0.06, sharpness: 6, gain: 0.45, wake: 26 },
];

/** Ease in and out (cubic), for a comb that gathers speed and slows at the far edge. */
export function easeInOut(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
export function crossed(before: number, t: number, at: number) {
  'worklet';
  return (before < at && t >= at) || (t < before && (at > before || at <= t));
}

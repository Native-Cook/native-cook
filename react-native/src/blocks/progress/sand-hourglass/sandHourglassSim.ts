/**
 * The sand of the hourglass: a cellular grain grid that lives in the glass's own frame.
 *
 * Every cell is empty (0) or holds one grain, stored as its tint (1…255) so a grain keeps its
 * colour as it moves. Each pass moves every grain at most one cell along gravity, or slides it
 * diagonally when the cell below is taken, or — now and then — two cells sideways, which lays the
 * pile at about 34° instead of the grid's 45°. Gravity is the screen's "down" seen from the glass,
 * snapped to the nearest of eight grid directions, so turning the glass makes the sand slide and
 * pour the other way. Everything is plain arithmetic on typed arrays with a seeded random, so it
 * runs the same, step for step, on any platform.
 *
 * A gate at the neck sets how fast the glass runs: a grain may only enter the neck's middle row when
 * a token is free, and tokens come at the timer's rate (grains per second). Opened, the neck lets
 * through about 219 grains a second, all a one-cell neck will pass.
 */

// The grid, in cells. Odd column count so the neck is centred on a column.
export const COLS = 77;
export const ROWS = 151;
// Size of one cell, in pt, as drawn.
export const CELL = 2.2;
// The neck's and the bulbs' half-width, in cells from the middle column: the neck is one cell
// wide, so the sand runs through it as a single thin stream.
const NECK = 0.6;
const BULB = 36.4;
// The bulb profile: the straight neck runs for NECK_RUN of the half-height, the funnel widens to
// the bulb's widest point at WIDEST, and the shoulder narrows to SHOULDER of that at the cap.
const NECK_RUN = 0.03;
const WIDEST = 0.6;
const SHOULDER = 0.68;
// Grains in the glass.
export const GRAINS = 2300;
// Chance that a grain with both diagonals blocked slides two cells sideways: lowers the pile's slope.
const SLIDE_CHANCE = 0.32;
// When gravity is this far (in eighths of a turn) off a grid direction, grains slide to that side.
const TILT_BIAS = 0.12;
// Initial pile: rows of height lost per column away from the middle (a 34° cone).
const PILE_SLOPE = 0.68;
// The neck's middle row, which the gate guards, and the rows around it counted as "in the neck".
export const MID_ROW = (ROWS - 1) / 2;
const NECK_BAND = 6;

// Grid directions, counter-clockwise from +x in a y-down frame: 0 = right, 2 = down, 4 = left, 6 = up.
const DX = [1, 1, 0, -1, -1, -1, 0, 1];
const DY = [0, 1, 1, 1, 0, -1, -1, -1];
// The same directions as steps in the flat grid index.
const OFFSET = DX.map((dx, k) => DY[k] * COLS + dx);

export type SandState = {
  /** 0 = empty, otherwise the grain's tint. */
  grid: Uint8Array;
  /** 1 inside the glass. */
  mask: Uint8Array;
  /** First and last column inside the glass on each row (left > right on a row with none). */
  left: Int16Array;
  right: Int16Array;
  /** First and last row inside the glass in each column. */
  top: Int16Array;
  bottom: Int16Array;
  /** Grains on each row and in each column, so empty ones are skipped. */
  rowCount: Int32Array;
  colCount: Int32Array;
  /**
   * Lines (rows, or columns under sideways gravity) where something moved, or might have, in the
   * last pass (`dirty`) and in this one (`next`). A line is only visited when it or the line ahead
   * of it along gravity is dirty, so a settled pile costs nothing.
   */
  dirty: Uint8Array;
  next: Uint8Array;
  byRow: boolean;
  /** Rows whose pixels are already all clear. */
  clearRow: Uint8Array;
  /** RGBA, written by `paintSand` for the grain shader. */
  pixels: Uint8Array;
  /** Colour of each tint, RGB. */
  palette: Uint8Array;
  seed: number;
  pass: number;
  /** Primary direction of gravity used by the last pass (index into DX/DY). */
  dir: number;
  /** Grains in the glass's upper and lower bulbs (its own frame), counted by `paintSand`. */
  upper: number;
  lower: number;
  /** Grains in and near the neck (within NECK_BAND rows of its middle), counted by `paintSand`. */
  neck: number;
  /** Grains per second the neck lets through; below 0 it is open. Tokens saved up, at most 2. */
  gate: number;
  tokens: number;
};

/** Half-width of the inside of the glass, in cells, `t` = 0 at the neck … 1 at the caps. */
export function glassHalfWidth(t: number) {
  'worklet';
  if (t < NECK_RUN) return NECK;
  const u = Math.min(1, (t - NECK_RUN) / (WIDEST - NECK_RUN));
  let s = 1 - Math.pow(1 - u, 2.4);
  if (t > WIDEST) s = 1 - (1 - SHOULDER) * Math.pow((t - WIDEST) / (1 - WIDEST), 2.2);
  return NECK + (BULB - NECK) * s;
}

/** A small, fast, seeded random in [0, 1): xorshift32 on the state's seed. */
function random(s: SandState) {
  'worklet';
  let x = s.seed | 0;
  x ^= x << 13;
  x ^= x >>> 17;
  x ^= x << 5;
  s.seed = x;
  return (x >>> 0) / 4294967296;
}

function mixChannel(a: number, b: number, t: number) {
  'worklet';
  return Math.round(a + (b - a) * t);
}

/** The colour of every tint: terracotta sand from burnt sienna to pale ochre, with dark and clear flecks. */
function buildPalette() {
  'worklet';
  const palette = new Uint8Array(256 * 3);
  const deep = [150, 74, 38];
  const mid = [204, 118, 62];
  const pale = [238, 178, 112];
  for (let v = 1; v < 256; v += 1) {
    const t = (v - 1) / 254;
    let r: number;
    let g: number;
    let b: number;
    if (v < 10) {
      // A few dark grains, like flecks of mica.
      r = 92;
      g = 50;
      b = 32;
    } else if (v > 248) {
      // And a few clear ones that catch the light.
      r = 255;
      g = 236;
      b = 206;
    } else if (t < 0.5) {
      r = mixChannel(deep[0], mid[0], t * 2);
      g = mixChannel(deep[1], mid[1], t * 2);
      b = mixChannel(deep[2], mid[2], t * 2);
    } else {
      r = mixChannel(mid[0], pale[0], t * 2 - 1);
      g = mixChannel(mid[1], pale[1], t * 2 - 1);
      b = mixChannel(mid[2], pale[2], t * 2 - 1);
    }
    palette[v * 3] = r;
    palette[v * 3 + 1] = g;
    palette[v * 3 + 2] = b;
  }
  return palette;
}

/**
 * A new glass with all its sand settled in the lower bulb as a cone, as if it had just run out.
 * The same seed gives the same grains.
 */
export function createSandState(seed: number): SandState {
  'worklet';
  const n = COLS * ROWS;
  const s: SandState = {
    grid: new Uint8Array(n),
    mask: new Uint8Array(n),
    left: new Int16Array(ROWS),
    right: new Int16Array(ROWS),
    top: new Int16Array(COLS),
    bottom: new Int16Array(COLS),
    rowCount: new Int32Array(ROWS),
    colCount: new Int32Array(COLS),
    clearRow: new Uint8Array(ROWS),
    dirty: new Uint8Array(ROWS).fill(1),
    next: new Uint8Array(ROWS),
    byRow: true,
    pixels: new Uint8Array(n * 4),
    palette: buildPalette(),
    seed: seed | 0 || 1,
    pass: 0,
    dir: 2,
    upper: 0,
    lower: 0,
    neck: 0,
    gate: -1,
    tokens: 0,
  };
  const mid = ROWS / 2;
  const cx = (COLS - 1) / 2;
  for (let c = 0; c < COLS; c += 1) {
    s.top[c] = ROWS;
    s.bottom[c] = -1;
  }
  for (let r = 0; r < ROWS; r += 1) {
    const w = glassHalfWidth(Math.abs(r + 0.5 - mid) / mid);
    s.left[r] = COLS;
    s.right[r] = -1;
    for (let c = 0; c < COLS; c += 1) {
      if (Math.abs(c - cx) <= w) {
        s.mask[r * COLS + c] = 1;
        if (c < s.left[r]) s.left[r] = c;
        if (c > s.right[r]) s.right[r] = c;
        if (r < s.top[c]) s.top[c] = r;
        if (r > s.bottom[c]) s.bottom[c] = r;
      }
    }
  }
  // Fill the lower bulb from its floor up, highest in the middle: a cone of PILE_SLOPE.
  const cells: number[] = [];
  const keys: number[] = [];
  for (let r = Math.ceil(mid); r < ROWS; r += 1) {
    for (let c = s.left[r]; c <= s.right[r]; c += 1) {
      cells.push(r * COLS + c);
      keys.push(ROWS - 1 - r + PILE_SLOPE * Math.abs(c - cx) + random(s) * 0.9);
    }
  }
  const order = cells.map((_, i) => i);
  order.sort((a, b) => keys[a] - keys[b]);
  const count = Math.min(GRAINS, order.length);
  for (let i = 0; i < count; i += 1) {
    const cellIndex = cells[order[i]];
    s.grid[cellIndex] = 1 + Math.floor(random(s) * 255);
    s.rowCount[Math.floor(cellIndex / COLS)] += 1;
    s.colCount[cellIndex % COLS] += 1;
  }
  return s;
}

/** Moves the grain at `from` to `to`, keeping the counts and waking both lines. */
function move(s: SandState, from: number, to: number) {
  'worklet';
  s.grid[to] = s.grid[from];
  s.grid[from] = 0;
  const r = (from / COLS) | 0;
  const c = from - r * COLS;
  const toRow = (to / COLS) | 0;
  const toCol = to - toRow * COLS;
  s.rowCount[r] -= 1;
  s.rowCount[toRow] += 1;
  s.colCount[c] -= 1;
  s.colCount[toCol] += 1;
  if (s.byRow) {
    s.next[r] = 1;
    s.next[toRow] = 1;
  } else {
    s.next[c] = 1;
    s.next[toCol] = 1;
  }
}

/**
 * Whether a grain may move from `from` to `to`: only a move into the neck's middle row needs a token,
 * and a grain held back keeps its line awake so it tries again next pass.
 */
function gateAllows(s: SandState, from: number, to: number) {
  'worklet';
  if (s.gate < 0) return true;
  const toRow = (to / COLS) | 0;
  const fromRow = (from / COLS) | 0;
  if (toRow !== MID_ROW || fromRow === MID_ROW) return true;
  if (s.tokens >= 1) {
    s.tokens -= 1;
    return true;
  }
  s.next[s.byRow ? fromRow : from - fromRow * COLS] = 1;
  return false;
}

/** Saves up the neck's tokens for `dt` seconds at the gate's rate. */
export function feedGate(s: SandState, dt: number) {
  'worklet';
  if (s.gate < 0) s.tokens = 0;
  else s.tokens = Math.min(2, s.tokens + s.gate * dt);
}

/**
 * One grain's turn, at cell index `i`: along gravity, else a diagonal (downhill side first), else
 * a long slide. The grid's first and last columns are outside the glass and an index past either
 * end reads as undefined, so neighbours need no bounds checks: anything not inside and empty is
 * simply taken.
 */
function updateGrain(s: SandState, i: number, k: number, bias: number) {
  'worklet';
  const { mask, grid } = s;
  const down = i + OFFSET[k];
  if (mask[down] === 1 && grid[down] === 0) {
    if (gateAllows(s, i, down)) move(s, i, down);
    return;
  }
  // The two neighbours of the primary direction; `bias` > 0 means the clockwise one is downhill.
  const a = i + OFFSET[(k + 1) % 8];
  const b = i + OFFSET[(k + 7) % 8];
  const aFree = mask[a] === 1 && grid[a] === 0;
  const bFree = mask[b] === 1 && grid[b] === 0;
  if (aFree || bFree) {
    let clockwise = aFree;
    if (aFree && bFree) clockwise = bias === 0 ? random(s) < 0.5 : bias > 0;
    const to = clockwise ? a : b;
    if (gateAllows(s, i, to)) move(s, i, to);
    return;
  }
  // Straight gravity only: slip two cells sideways and one down, over an empty cell beside it.
  if (k % 2 !== 0) return;
  // The sideways axis is gravity turned a quarter.
  const side = OFFSET[(k + 2) % 8];
  const right = mask[i + side] === 1 && grid[i + side] === 0;
  const left = mask[i - side] === 1 && grid[i - side] === 0;
  if (!left && !right) return;
  if (random(s) >= SLIDE_CHANCE) {
    // It may slide on a later pass: keep its line awake.
    s.next[s.byRow ? (i / COLS) | 0 : i % COLS] = 1;
    return;
  }
  const way = left && right ? (random(s) < 0.5 ? 1 : -1) : right ? 1 : -1;
  const to = i + 2 * way * side + OFFSET[k];
  if (mask[to] === 1 && grid[to] === 0 && gateAllows(s, i, to)) move(s, i, to);
}

/**
 * One pass of the sand under gravity (gx, gy), given in the glass's frame. Cells are visited from
 * the far end of gravity backwards, so a grain that moved is never visited again in the same pass.
 * Rows (or columns) without a grain are skipped.
 */
export function stepSand(s: SandState, gx: number, gy: number) {
  'worklet';
  const octant = Math.atan2(gy, gx) / (Math.PI / 4);
  const nearest = Math.round(octant);
  const off = octant - nearest;
  const k = ((nearest % 8) + 8) % 8;
  const bias = Math.abs(off) > TILT_BIAS ? Math.sign(off) : 0;
  const dx = DX[k];
  const dy = DY[k];
  const grid = s.grid;
  const byRow = dy !== 0;
  // A new direction of gravity can move anything.
  if (k !== s.dir || byRow !== s.byRow) s.dirty.fill(1);
  s.dir = k;
  s.byRow = byRow;
  s.pass += 1;
  const alternate = s.pass % 2 === 0 ? 1 : -1;
  const { dirty, next } = s;
  if (byRow) {
    // Rows from the far end of gravity; within a row, from the far end of its sideways part.
    const step = dx !== 0 ? -dx : alternate;
    for (let i = 0; i < ROWS; i += 1) {
      const r = dy > 0 ? ROWS - 1 - i : i;
      if (s.rowCount[r] === 0) continue;
      const ahead = r + dy;
      const awake = dirty[r] === 1 || (ahead >= 0 && ahead < ROWS && (dirty[ahead] === 1 || next[ahead] === 1));
      if (!awake) continue;
      const lo = s.left[r];
      const hi = s.right[r];
      const base = r * COLS;
      if (step < 0) {
        for (let c = hi; c >= lo; c -= 1) if (grid[base + c] !== 0) updateGrain(s, base + c, k, bias);
      } else {
        for (let c = lo; c <= hi; c += 1) if (grid[base + c] !== 0) updateGrain(s, base + c, k, bias);
      }
    }
  } else {
    // Sideways gravity: columns from the far end, rows alternating.
    for (let i = 0; i < COLS; i += 1) {
      const c = dx > 0 ? COLS - 1 - i : i;
      if (s.colCount[c] === 0) continue;
      const ahead = c + dx;
      const awake = dirty[c] === 1 || (ahead >= 0 && ahead < COLS && (dirty[ahead] === 1 || next[ahead] === 1));
      if (!awake) continue;
      const lo = s.top[c];
      const hi = s.bottom[c];
      if (alternate < 0) {
        for (let r = hi; r >= lo; r -= 1) if (grid[r * COLS + c] !== 0) updateGrain(s, r * COLS + c, k, bias);
      } else {
        for (let r = lo; r <= hi; r += 1) if (grid[r * COLS + c] !== 0) updateGrain(s, r * COLS + c, k, bias);
      }
    }
  }
  for (let i = 0; i < ROWS; i += 1) {
    dirty[i] = next[i];
    next[i] = 0;
  }
}

/**
 * Writes the grid into `pixels` (one RGBA pixel per cell) and counts the grains in each bulb.
 * A grain with open space above it (against gravity) is lit, one buried on all four sides is
 * shaded, so the pile reads as a surface and the stream as loose grains.
 */
export function paintSand(s: SandState) {
  'worklet';
  const { grid, pixels, palette } = s;
  const ux = -DX[s.dir];
  const uy = -DY[s.dir];
  const midRow = (ROWS - 1) / 2;
  let upper = 0;
  let lower = 0;
  let neck = 0;
  for (let r = 0; r < ROWS; r += 1) {
    if (s.rowCount[r] === 0) {
      if (s.clearRow[r] === 1) continue;
      s.clearRow[r] = 1;
    } else {
      s.clearRow[r] = 0;
    }
    const lo = s.left[r];
    const hi = s.right[r];
    for (let c = lo; c <= hi; c += 1) {
      const i = r * COLS + c;
      const p = i * 4;
      const v = grid[i];
      if (v === 0) {
        pixels[p + 3] = 0;
        pixels[p] = 0;
        pixels[p + 1] = 0;
        pixels[p + 2] = 0;
        continue;
      }
      if (r < midRow) upper += 1;
      else if (r > midRow) lower += 1;
      if (Math.abs(r - midRow) <= NECK_BAND) neck += 1;
      const ar = r + uy;
      const ac = c + ux;
      const above = ar >= 0 && ar < ROWS && ac >= 0 && ac < COLS ? grid[ar * COLS + ac] : 0;
      let shade = 1;
      if (above === 0) shade = 1.14;
      else if (
        c > 0 &&
        c < COLS - 1 &&
        r > 0 &&
        r < ROWS - 1 &&
        grid[i - 1] !== 0 &&
        grid[i + 1] !== 0 &&
        grid[i - COLS] !== 0 &&
        grid[i + COLS] !== 0
      ) {
        shade = 0.9;
      }
      pixels[p] = Math.min(255, palette[v * 3] * shade);
      pixels[p + 1] = Math.min(255, palette[v * 3 + 1] * shade);
      pixels[p + 2] = Math.min(255, palette[v * 3 + 2] * shade);
      pixels[p + 3] = 255;
    }
  }
  s.upper = upper;
  s.lower = lower;
  s.neck = neck;
}

/**
 * The grain shader. It reads one texel per cell and draws each grain as a small lit bead that
 * overlaps its neighbours a little, so a pile reads as packed sand and the stream as loose grains.
 * `light` points from a grain towards the light, in the glass's frame.
 */
export const GRAIN_SKSL = `
uniform shader cells;
uniform float cell;
uniform float2 light;

half4 main(float2 p) {
  float2 g = p / cell;
  float2 c = floor(g);
  float2 f = g - c - 0.5;
  half4 best = half4(0.0);
  float cover = 0.0;
  float2 off = float2(0.0);
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      float2 o = float2(float(i), float(j));
      half4 s = cells.eval(c + o + 0.5);
      if (s.a > 0.5) {
        // Each cell's grain sits a little off centre and varies in size, so the grid never shows.
        float2 cc = c + o;
        float2 h = fract(sin(float2(dot(cc, float2(127.1, 311.7)), dot(cc, float2(269.5, 183.3)))) * 43758.5453);
        float2 d = f - o - (h - 0.5) * 0.32;
        float r = 0.5 + 0.1 * h.x;
        float k = 1.0 - smoothstep(r - 0.1, r + 0.12, length(d));
        if (k > cover) {
          cover = k;
          best = s;
          off = d;
        }
      }
    }
  }
  if (cover <= 0.0) {
    return half4(0.0);
  }
  float lit = clamp(dot(off, light) / 0.6, -1.0, 1.0);
  float spec = 1.0 - smoothstep(0.0, 0.2, length(off - light * 0.24));
  half3 col = best.rgb * half(1.0 + 0.3 * lit) + half3(0.26, 0.24, 0.2) * half(spec);
  return half4(col * half(cover), half(cover));
}
`;

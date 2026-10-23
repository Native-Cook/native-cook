/**
 * A fractal tree grown from a seed, its wind, its foliage through the seasons and what falls from it.
 *
 * The tree is built once from a seed: every branch splits into two (now and then three) shorter,
 * thinner ones, a leader that keeps close to its parent's line and side shoots that spread, all
 * leaning a little towards the light. Branches are stored parents first, so one pass lays them out.
 *
 * Growth runs depth by depth: a branch at depth d grows over one unit of `growth` from d on, eased,
 * so the trunk rises first and the twigs last. The wind reaches the trunk first and each level after
 * a lag (a chain of first-order followers), and every branch also sways on its own breeze, thin
 * ones more. The season is a number that runs 0 spring, 1 summer, 2 autumn, 3 winter and back to 4
 * (spring again); each bud, leaf and blossom reads it a little late (its own stagger), so colours
 * and foliage morph across the crown instead of all at once. Leaves fall and petals drift down as
 * particles, snow falls in winter; everything is seeded and runs on a fixed tick.
 */

export const MAX_DEPTH = 7;
// The trunk, pt, and how much shorter and thinner each level is.
const TRUNK_LENGTH = 120;
const TRUNK_WIDTH = 15;
const LEADER_RATIO = 0.82;
const SIDE_RATIO = 0.7;
const WIDTH_RATIO = 0.68;
// Spread of the side shoots, rad, and the pull of every branch back towards upright.
const SPREAD_MIN = 0.32;
const SPREAD_MAX = 0.54;
const LIGHT_PULL = 0.16;
// Foliage on the last levels: sites per branch.
const LEAF_LEVELS = 3;
export const MAX_PARTICLES = 480;
// How far ahead of the season the earliest site runs (the latest runs exactly on it).
export const SITE_LEAD = 0.35;

// Wind: how fast the trunk takes it up and each level after it (1/s), and how far it bends a twig.
const WIND_TRUNK_RATE = 5;
const WIND_LEVEL_RATE = 3.6;
const BEND = 0.32;
// The breeze every branch sways in: amplitude at the tips, rad, and its pace.
const BREEZE = 0.05;
const BREEZE_SPEED = 1.3;

export type Tree = {
  count: number;
  parent: Int16Array;
  depth: Uint8Array;
  length: Float64Array;
  angle: Float64Array;
  width: Float64Array;
  phase: Float64Array;
  /** Laid out each frame: base, tip, absolute angle and drawn width at base and tip. */
  bx: Float64Array;
  by: Float64Array;
  tx: Float64Array;
  ty: Float64Array;
  abs: Float64Array;
  w0: Float64Array;
  w1: Float64Array;
  shown: Float64Array;
  /** Foliage sites: branch, place along it (0…1), offset across it, size and look. */
  sites: number;
  siteBranch: Int16Array;
  siteAt: Float64Array;
  siteSide: Float64Array;
  siteSize: Float64Array;
  siteSpin: Float64Array;
  /** How late the site reads the season (0…0.35), when its leaf falls, and its autumn colour (0…1). */
  siteLag: Float64Array;
  siteFall: Float64Array;
  siteHue: Float64Array;
  /** 0 or 1: which blossom it bears. */
  siteKind: Uint8Array;
  /** Its season last tick (for leaves falling and petals dropping as it passes). */
  siteSeen: Float64Array;
};

export type Particles = {
  count: number;
  kind: Uint8Array;
  x: Float64Array;
  y: Float64Array;
  vx: Float64Array;
  vy: Float64Array;
  rot: Float64Array;
  spin: Float64Array;
  size: Float64Array;
  hue: Float64Array;
  phase: Float64Array;
  /** Seconds since it came to rest on the ground (−1 while falling). */
  rest: Float64Array;
  /** Next slot to reuse when the pool is full. */
  cursor: number;
};

/** The mound the tree stands on: a low hump on a flat ground line. */
export type Ground = { base: number; cx: number; bump: number; spread: number };

/** Height of the ground at x. */
export function groundY(g: Ground, x: number) {
  'worklet';
  const u = (x - g.cx) / g.spread;
  return g.base - g.bump * Math.exp(-u * u);
}

export type Weather = {
  /** The wind the finger (or the script) asks for, the wind now, its speed, and each level's share. */
  target: number;
  wind: number;
  windSpeed: number;
  levels: Float64Array;
  time: number;
  seed: number;
  snowDue: number;
};

// Particle kinds.
export const PETAL = 0;
export const LEAF = 1;
export const SNOW = 2;

/** A small seeded random in [0, 1): xorshift32 on `state[0]`. */
function random(state: number[]) {
  'worklet';
  let x = state[0] | 0;
  x ^= x << 13;
  x ^= x >>> 17;
  x ^= x << 5;
  state[0] = x;
  return (x >>> 0) / 4294967296;
}

/** Ease out (cubic). */
export function easeOut(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  return 1 - (1 - k) * (1 - k) * (1 - k);
}

/** Ease in and out (cubic). */
export function easeInOut(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
}

/** Ease out with a little overshoot. */
export function easeOutBack(t: number) {
  'worklet';
  const k = Math.min(1, Math.max(0, t));
  const u = k - 1;
  return 1 + 2.4 * u * u * u + 1.4 * u * u;
}

/** Builds the tree for a seed (the same seed always grows the same tree). */
export function buildTree(seed: number): Tree {
  'worklet';
  const state = [seed | 1];
  const max = 1200;
  const parent = new Int16Array(max);
  const depth = new Uint8Array(max);
  const length = new Float64Array(max);
  const angle = new Float64Array(max);
  const width = new Float64Array(max);
  const phase = new Float64Array(max);
  // The trunk, slightly off upright.
  parent[0] = -1;
  depth[0] = 0;
  length[0] = TRUNK_LENGTH;
  angle[0] = (random(state) - 0.5) * 0.08;
  width[0] = TRUNK_WIDTH;
  phase[0] = 0;
  let count = 1;
  // Breadth first: parents always come before their children.
  let abs = [angle[0]];
  for (let i = 0; i < count && count < max - 3; i += 1) {
    const d = depth[i];
    if (d >= MAX_DEPTH) continue;
    const three = d === 0 ? random(state) < 0.6 : random(state) < 0.06;
    const n = three ? 3 : 2;
    const spread = SPREAD_MIN + (SPREAD_MAX - SPREAD_MIN) * random(state);
    // The leader goes to one side a little, the others further the other way.
    const lean = random(state) < 0.5 ? -1 : 1;
    for (let k = 0; k < n; k += 1) {
      const leader = k === 0;
      let a: number;
      if (leader) a = lean * spread * (0.18 + 0.2 * random(state));
      else if (n === 2) a = -lean * spread * (0.85 + 0.3 * random(state));
      else a = (k === 1 ? -lean : lean) * spread * (0.9 + 0.35 * random(state));
      // Lean back towards the light.
      const absolute = abs[i] + a;
      a -= absolute * LIGHT_PULL;
      const ratio = (leader ? LEADER_RATIO : SIDE_RATIO) * (0.9 + 0.2 * random(state));
      parent[count] = i;
      depth[count] = d + 1;
      length[count] = length[i] * ratio;
      angle[count] = a;
      width[count] = width[i] * WIDTH_RATIO * (leader ? 1 : 0.86);
      phase[count] = random(state) * Math.PI * 2;
      abs.push(abs[i] + a);
      count += 1;
    }
  }
  abs = [];
  // Foliage sites along the last levels.
  const maxSites = 900;
  const siteBranch = new Int16Array(maxSites);
  const siteAt = new Float64Array(maxSites);
  const siteSide = new Float64Array(maxSites);
  const siteSize = new Float64Array(maxSites);
  const siteSpin = new Float64Array(maxSites);
  const siteLag = new Float64Array(maxSites);
  const siteFall = new Float64Array(maxSites);
  const siteHue = new Float64Array(maxSites);
  const siteKind = new Uint8Array(maxSites);
  let sites = 0;
  for (let i = 0; i < count && sites < maxSites; i += 1) {
    if (depth[i] < MAX_DEPTH - LEAF_LEVELS + 1) continue;
    // A twig bears one at its tip; the boughs below it one now and then.
    if (depth[i] < MAX_DEPTH && random(state) > 0.6) continue;
    const n = depth[i] === MAX_DEPTH ? 2 : 1;
    for (let k = 0; k < n && sites < maxSites; k += 1) {
      siteBranch[sites] = i;
      siteAt[sites] = depth[i] === MAX_DEPTH && k === 0 ? 0.85 + 0.15 * random(state) : 0.4 + 0.4 * random(state);
      siteSide[sites] = (random(state) - 0.5) * 7;
      siteSize[sites] = 0.75 + 0.5 * random(state);
      siteSpin[sites] = random(state) * Math.PI * 2;
      siteLag[sites] = random(state) * 0.35;
      // A few leaves let go during autumn, the rest as winter comes.
      siteFall[sites] = random(state) < 0.4 ? 1.85 + random(state) * 0.45 : 2.3 + random(state) * 0.4;
      siteHue[sites] = random(state);
      siteKind[sites] = random(state) < 0.62 ? 0 : 1;
      sites += 1;
    }
  }
  return {
    count,
    parent,
    depth,
    length,
    angle,
    width,
    phase,
    bx: new Float64Array(count),
    by: new Float64Array(count),
    tx: new Float64Array(count),
    ty: new Float64Array(count),
    abs: new Float64Array(count),
    w0: new Float64Array(count),
    w1: new Float64Array(count),
    shown: new Float64Array(count),
    sites,
    siteBranch,
    siteAt,
    siteSide,
    siteSize,
    siteSpin,
    siteLag,
    siteFall,
    siteHue,
    siteKind,
    siteSeen: new Float64Array(sites).fill(-1),
  };
}

export function createWeather(seed: number): Weather {
  'worklet';
  return { target: 0, wind: 0, windSpeed: 0, levels: new Float64Array(MAX_DEPTH + 1), time: 0, seed, snowDue: 0 };
}

export function createParticles(): Particles {
  'worklet';
  const n = MAX_PARTICLES;
  return {
    count: 0,
    kind: new Uint8Array(n),
    x: new Float64Array(n),
    y: new Float64Array(n),
    vx: new Float64Array(n),
    vy: new Float64Array(n),
    rot: new Float64Array(n),
    spin: new Float64Array(n),
    size: new Float64Array(n),
    hue: new Float64Array(n),
    phase: new Float64Array(n),
    rest: new Float64Array(n),
    cursor: 0,
  };
}

/** How far a branch at `depth` has grown (0…1) at `growth` (0 … MAX_DEPTH + 1). */
export function grownAt(growth: number, depth: number) {
  'worklet';
  return easeOut(growth - depth);
}

/**
 * Lays the tree out from its root at (x, y): every branch's base, tip, angle and widths, bent by
 * each level's share of the wind and its own breeze.
 */
export function layoutTree(tree: Tree, weather: Weather, x: number, y: number, growth: number, sway: number) {
  'worklet';
  const t = weather.time;
  for (let i = 0; i < tree.count; i += 1) {
    const d = tree.depth[i];
    const p = tree.parent[i];
    const grown = grownAt(growth, d);
    // Thin branches give more: the bend grows towards the tips.
    const flex = (d + 1) / (MAX_DEPTH + 1);
    const lean = flex * flex * (BEND * weather.levels[d] + BREEZE * Math.sin(t * BREEZE_SPEED * (1 + d * 0.06) + tree.phase[i] - d * 0.5)) * sway;
    const a = (p < 0 ? 0 : tree.abs[p]) + tree.angle[i] + lean * (d === 0 ? 0.25 : 1);
    tree.abs[i] = a;
    const bx = p < 0 ? x : tree.tx[p];
    const by = p < 0 ? y : tree.ty[p];
    const len = tree.length[i] * grown;
    tree.bx[i] = bx;
    tree.by[i] = by;
    tree.tx[i] = bx + Math.sin(a) * len;
    tree.ty[i] = by - Math.cos(a) * len;
    tree.shown[i] = grown;
    const w = tree.width[i] * (0.35 + 0.65 * grown);
    tree.w0[i] = p < 0 ? w : Math.min(w, tree.w1[p]);
    tree.w1[i] = w * WIDTH_RATIO * 1.05;
  }
}

/** One tick of the wind: it springs towards the target; each level follows the one below it, late. */
export function stepWind(weather: Weather, dt: number) {
  'worklet';
  weather.time += dt;
  // A soft, slightly underdamped spring, so a released bough swings back past rest once.
  const accel = 26 * (weather.target - weather.wind) - 6.5 * weather.windSpeed;
  weather.windSpeed += accel * dt;
  weather.wind += weather.windSpeed * dt;
  const levels = weather.levels;
  levels[0] += (weather.wind - levels[0]) * Math.min(1, WIND_TRUNK_RATE * dt);
  for (let d = 1; d <= MAX_DEPTH; d += 1) levels[d] += (levels[d - 1] - levels[d]) * Math.min(1, WIND_LEVEL_RATE * dt);
}

/** Adds a particle; when the pool is full the oldest one makes room. */
function spawn(ps: Particles, kind: number, x: number, y: number, size: number, hue: number, state: number[]) {
  'worklet';
  let i = ps.count;
  if (i >= MAX_PARTICLES) {
    i = ps.cursor % MAX_PARTICLES;
    ps.cursor = i + 1;
  } else ps.count += 1;
  ps.kind[i] = kind;
  ps.x[i] = x;
  ps.y[i] = y;
  ps.vx[i] = (random(state) - 0.5) * 20;
  ps.vy[i] = kind === SNOW ? 20 : -6 - random(state) * 10;
  ps.rot[i] = random(state) * Math.PI * 2;
  ps.spin[i] = (random(state) - 0.5) * 5;
  ps.size[i] = size;
  ps.hue[i] = hue;
  ps.phase[i] = random(state) * Math.PI * 2;
  ps.rest[i] = -1;
}

/** Whether a site's leaf drops as a falling leaf (most do); the rest wither on the twig. */
export function leafFalls(tree: Tree, i: number) {
  'worklet';
  return tree.siteSpin[i] < Math.PI * 1.4;
}

/**
 * The season (0…4) as a site reads it, wrapped into 0…4. Sites run up to SITE_LEAD ahead of the
 * season, each by its own share, so a change sweeps across the crown, and at rest (a whole season)
 * every site sits within SITE_LEAD past it, where nothing is changing.
 */
export function localSeason(season: number, lag: number) {
  'worklet';
  const s = season + SITE_LEAD - lag;
  return s - 4 * Math.floor(s / 4);
}

/**
 * Leaves fall and petals drop as each site passes its moment in the season; snow falls in winter;
 * every particle drifts down on the wind and rests on the ground a while, fading.
 */
export function stepFalling(
  tree: Tree,
  ps: Particles,
  weather: Weather,
  season: number,
  dt: number,
  width: number,
  ground: Ground,
  falling: boolean,
) {
  'worklet';
  const state = [weather.seed];
  for (let i = 0; i < tree.sites; i += 1) {
    const s = localSeason(season, tree.siteLag[i]);
    const before = tree.siteSeen[i];
    tree.siteSeen[i] = s;
    if (before < 0 || s < before || !falling) continue;
    const b = tree.siteBranch[i];
    if (tree.shown[b] < 0.99) continue;
    const x = tree.bx[b] + (tree.tx[b] - tree.bx[b]) * tree.siteAt[i];
    const y = tree.by[b] + (tree.ty[b] - tree.by[b]) * tree.siteAt[i];
    // A leaf lets go at its own moment in autumn.
    if (leafFalls(tree, i) && before < tree.siteFall[i] && s >= tree.siteFall[i]) spawn(ps, LEAF, x, y, tree.siteSize[i], tree.siteHue[i], state);
    // About half the blossoms shed a petal as the leaves come.
    const shed = 0.5 + tree.siteHue[i] * 0.4;
    if (tree.siteKind[i] === 0 && tree.siteHue[i] < 0.55 && before < shed && s >= shed) spawn(ps, PETAL, x, y, tree.siteSize[i], tree.siteHue[i], state);
  }
  // Snow from above the screen while it is winter.
  const s = season - 4 * Math.floor(season / 4);
  const snowing = falling && s > 2.85 && s < 3.85;
  if (snowing) {
    weather.snowDue += dt * 24;
    while (weather.snowDue >= 1) {
      weather.snowDue -= 1;
      spawn(ps, SNOW, random(state) * (width + 120) - 60, -10, 0.5 + random(state) * 0.7, random(state), state);
    }
  } else weather.snowDue = 0;
  weather.seed = state[0];

  const wind = weather.wind;
  for (let i = 0; i < ps.count; i += 1) {
    if (ps.rest[i] >= 0) {
      ps.rest[i] += dt;
      continue;
    }
    const kind = ps.kind[i];
    // Each kind drifts down at its own pace, swept by the wind and fluttering side to side.
    const fall = kind === SNOW ? 60 + ps.size[i] * 30 : kind === LEAF ? 105 : 66;
    const flutter = kind === SNOW ? 10 : 26;
    const drift = wind * (kind === SNOW ? 70 : 95) + Math.sin(weather.time * 2.1 + ps.phase[i]) * flutter;
    ps.vx[i] += (drift - ps.vx[i]) * Math.min(1, dt * 2.4);
    ps.vy[i] += (fall - ps.vy[i]) * Math.min(1, dt * 1.8);
    ps.x[i] += ps.vx[i] * dt;
    ps.y[i] += ps.vy[i] * dt;
    ps.rot[i] += ps.spin[i] * dt * (kind === SNOW ? 0.3 : 1) + Math.sin(weather.time * 3 + ps.phase[i]) * dt;
    const floor = groundY(ground, ps.x[i]) - (kind === SNOW ? 1 : 2);
    if (ps.y[i] >= floor) {
      ps.y[i] = floor;
      ps.rest[i] = 0;
    }
  }
  // Drop what has rested long enough, keeping the pool packed.
  let n = 0;
  for (let i = 0; i < ps.count; i += 1) {
    const keep = ps.rest[i] < (ps.kind[i] === SNOW ? 1.2 : 2) && ps.x[i] > -80 && ps.x[i] < width + 80;
    if (!keep) continue;
    if (n !== i) {
      ps.kind[n] = ps.kind[i];
      ps.x[n] = ps.x[i];
      ps.y[n] = ps.y[i];
      ps.vx[n] = ps.vx[i];
      ps.vy[n] = ps.vy[i];
      ps.rot[n] = ps.rot[i];
      ps.spin[n] = ps.spin[i];
      ps.size[n] = ps.size[i];
      ps.hue[n] = ps.hue[i];
      ps.phase[n] = ps.phase[i];
      ps.rest[n] = ps.rest[i];
    }
    n += 1;
  }
  ps.count = n;
}

export type RibbonPoint = { x: number; y: number };
type Particle = RibbonPoint & { px: number; py: number };
export type RibbonSample = RibbonPoint & { nx: number; ny: number; width: number; light: number };
export type RibbonBand = {
  points: Particle[];
  samples: RibbonSample[];
  length: number;
  top: RibbonPoint;
  end: RibbonPoint;
};
export type RibbonState = {
  bands: RibbonBand[];
  tip: RibbonPoint;
  release: RibbonPoint;
  worn: number;
  releaseWorn: number;
  leaving: boolean;
  caught: boolean;
  active: boolean;
  opacity: number;
  step: number;
};

const COUNT = 15;
const SAMPLES = 71;
const MAX_STEP = 1 / 120;

function clamp(n: number, low: number, high: number) {
  'worklet';
  return Math.max(low, Math.min(high, n));
}

export function createRibbonState(): RibbonState {
  'worklet';
  const bands: RibbonBand[] = [];
  for (let b = 0; b < 2; b++) {
    const points: Particle[] = [];
    const samples: RibbonSample[] = [];
    for (let i = 0; i < COUNT; i++) points.push({ x: 0, y: 0, px: 0, py: 0 });
    for (let i = 0; i < SAMPLES; i++) samples.push({ x: 0, y: 0, nx: 1, ny: 0, width: 13, light: 1 });
    bands.push({ points, samples, length: 1, top: { x: 0, y: 0 }, end: { x: 0, y: 0 } });
  }
  return {
    bands,
    tip: { x: 0, y: 0 },
    release: { x: 0, y: 0 },
    worn: 0,
    releaseWorn: 1,
    leaving: false,
    caught: false,
    active: false,
    opacity: 0,
    step: MAX_STEP,
  };
}

function seedBand(band: RibbonBand, tx: number, ty: number, ex: number, ey: number, side: number, straight: boolean) {
  'worklet';
  const dx = ex - tx;
  const dy = ey - ty;
  const span = Math.max(1, Math.hypot(dx, dy));
  // A single broad belly seeds the slack; starting with a straight, compressed chain buckles it.
  const belly = straight ? 0 : span * 0.115 * side;
  for (let i = 0; i < COUNT; i++) {
    const p = band.points[i];
    const t = i / (COUNT - 1);
    const bow = Math.sin(Math.PI * t) * belly;
    p.x = p.px = tx + dx * t + (dy / span) * bow;
    p.y = p.py = ty + dy * t - (dx / span) * bow;
  }
  band.top.x = tx;
  band.top.y = ty;
  band.end.x = ex;
  band.end.y = ey;
  band.length = (span * (straight ? 1 : 1.033)) / (COUNT - 1);
}

function constrain(band: RibbonBand, tx: number, ty: number, ex: number, ey: number) {
  'worklet';
  const points = band.points;
  const last = COUNT - 1;
  // The unseen neck feeds a little cloth as the medal travels, rather than stretching fabric.
  band.length = Math.max(1, Math.hypot(ex - tx, ey - ty)) * 1.033 / last;
  points[0].x = tx;
  points[0].y = ty;
  points[last].x = ex;
  points[last].y = ey;
  for (let pass = 0; pass < 384; pass++) {
    for (let j = 0; j < last; j++) {
      const i = pass % 2 === 0 ? j : last - 1 - j;
      const a = points[i];
      const b = points[i + 1];
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const span = Math.max(0.00001, Math.hypot(dx, dy));
      const correction = (span - band.length) / span;
      const wa = i === 0 ? 0 : i + 1 === last ? 1 : 0.5;
      const wb = i + 1 === last ? 0 : i === 0 ? 1 : 0.5;
      a.x += dx * correction * wa;
      a.y += dy * correction * wa;
      b.x -= dx * correction * wb;
      b.y -= dy * correction * wb;
    }
    if (pass % 8 === 7) {
      let error = 0;
      for (let i = 0; i < last; i++) {
        error = Math.max(error, Math.abs(Math.hypot(
          points[i + 1].x - points[i].x, points[i + 1].y - points[i].y,
        ) / band.length - 1));
      }
      if (error < 0.001) break;
    }
  }
}

function sampleBand(band: RibbonBand, side: number, scale: number, straight: boolean) {
  'worklet';
  const points = band.points;
  for (let i = 0; i < SAMPLES; i++) {
    const t = i / (SAMPLES - 1);
    const at = t * (COUNT - 1);
    const j = Math.min(COUNT - 2, Math.floor(at));
    const f = at - j;
    const a = points[Math.max(0, j - 1)];
    const b = points[j];
    const c = points[j + 1];
    const d = points[Math.min(COUNT - 1, j + 2)];
    const q = band.samples[i];
    // Catmull–Rom keeps the silhouette and both stitched edges tangent through every joint.
    q.x = 0.5 * ((2 * b.x) + (-a.x + c.x) * f +
      (2 * a.x - 5 * b.x + 4 * c.x - d.x) * f * f + (-a.x + 3 * b.x - 3 * c.x + d.x) * f * f * f);
    q.y = 0.5 * ((2 * b.y) + (-a.y + c.y) * f +
      (2 * a.y - 5 * b.y + 4 * c.y - d.y) * f * f + (-a.y + 3 * b.y - 3 * c.y + d.y) * f * f * f);
  }
  for (let i = 0; i < SAMPLES; i++) {
    const a = band.samples[Math.max(0, i - 1)];
    const b = band.samples[Math.min(SAMPLES - 1, i + 1)];
    const p = band.samples[i];
    const span = Math.max(0.001, Math.hypot(b.x - a.x, b.y - a.y));
    p.nx = (b.y - a.y) / span;
    p.ny = -(b.x - a.x) / span;
    const t = i / (SAMPLES - 1);
    // A sewn half-turn near the clasp presents an edge briefly, without a paper-thin flicker.
    const fold = straight ? 0 : Math.exp(-Math.pow((t - (side < 0 ? 0.73 : 0.84)) / 0.12, 2));
    p.width = 13 * scale * (1 - 0.62 * fold);
    p.light = clamp(0.86 - 0.4 * fold - 0.16 * p.ny + 0.05 * side, 0.35, 1);
  }
}

/** Mutates owned buffers; seconds and sheet points in, no platform dependencies. */
export function stepRibbon(
  state: RibbonState,
  elapsed: number,
  worn: number,
  pivot: RibbonPoint,
  attach: RibbonPoint,
  scale = 1,
  reduced = false,
) {
  'worklet';
  const amount = clamp(worn, 0, 1);
  if (amount === 0) {
    state.active = false;
    state.opacity = 0;
    state.worn = 0;
    state.leaving = false;
    state.caught = false;
    return;
  }
  const fresh = !state.active;
  if (amount < state.worn - 0.00001 && !state.leaving) {
    state.leaving = true;
    state.release.x = state.tip.x;
    state.release.y = state.tip.y;
    state.releaseWorn = state.worn;
  } else if (amount > state.worn + 0.00001 && state.leaving) {
    // A new wear request can interrupt the withdrawal without discarding the cloth's inertia.
    state.leaving = false;
  }
  const catchNow = !state.leaving && amount >= 0.55;
  const catchKick = catchNow && !state.caught && !fresh;
  let tipX = attach.x;
  let tipY = attach.y;
  let lift = 0;
  if (!reduced) {
    if (state.leaving) {
      const retract = 1 - amount / Math.max(0.0001, state.releaseWorn);
      lift = Math.pow(retract, 0.8) * Math.max(64 * scale, state.release.y - pivot.y + 64 * scale);
      tipX = state.release.x;
      tipY = state.release.y - lift;
    } else {
      const drop = Math.min(1, amount / 0.55);
      lift = (1 - drop * drop) * Math.max(64 * scale, attach.y - pivot.y + 64 * scale);
      tipY -= lift;
    }
  }
  const travel = fresh ? 0 : Math.hypot(tipX - state.tip.x, tipY - state.tip.y);
  state.tip.x = tipX;
  state.tip.y = tipY;
  state.opacity = reduced ? amount : Math.min(1, amount * 12);
  const duration = clamp(elapsed, 0, 0.1);
  // A flip may move the attachment abruptly even on a short frame; interpolate that jolt too.
  const steps = Math.max(1, Math.ceil(duration / MAX_STEP), Math.min(24, Math.ceil(travel / (4 * scale))));
  const dt = duration > 0 ? duration / steps : MAX_STEP;
  for (let b = 0; b < 2; b++) {
    const band = state.bands[b];
    const side = b === 0 ? -1 : 1;
    const tx = pivot.x + side * 46 * scale;
    const ty = pivot.y;
    // Crossing the sewn ends over each other makes the load pass through the middle of the loop.
    const ex = tipX - side * 4 * scale;
    const ey = tipY - 13 * scale;
    if (fresh || reduced) seedBand(band, tx, ty, ex, ey, side, reduced);
    if (!reduced && (duration > 0 || fresh || travel > 0)) {
      const oldTX = band.top.x;
      const oldTY = band.top.y;
      const oldEX = band.end.x;
      const oldEY = band.end.y;
      for (let sub = 0; sub < steps; sub++) {
        const blend = (sub + 1) / steps;
        for (let i = 1; i < COUNT - 1; i++) {
          const p = band.points[i];
          const ratio = dt / (sub === 0 ? state.step : dt);
          const drag = Math.exp(-3.8 * dt);
          const vx = clamp((p.x - p.px) * ratio, -900 * scale * dt, 900 * scale * dt);
          const vy = clamp((p.y - p.py) * ratio, -900 * scale * dt, 900 * scale * dt);
          p.px = p.x;
          p.py = p.y;
          p.x += vx * drag;
          p.y += vy * drag + 430 * scale * dt * dt;
          if (catchKick && sub === 0) p.x += side * 38 * scale * dt * Math.sin(i / (COUNT - 1) * Math.PI);
        }
        // Woven fabric resists a sharp fold; a weak bending term removes high-frequency chatter.
        for (let i = 1; i < COUNT - 1; i++) {
          const p = band.points[i];
          const a = band.points[i - 1];
          const c = band.points[i + 1];
          const bend = 1 - Math.exp(-5 * dt);
          p.x += ((a.x + c.x) * 0.5 - p.x) * bend;
          p.y += ((a.y + c.y) * 0.5 - p.y) * bend;
        }
        constrain(band, oldTX + (tx - oldTX) * blend, oldTY + (ty - oldTY) * blend,
          oldEX + (ex - oldEX) * blend, oldEY + (ey - oldEY) * blend);
      }
    }
    band.top.x = tx;
    band.top.y = ty;
    band.end.x = ex;
    band.end.y = ey;
    sampleBand(band, side, scale, reduced);
  }
  state.step = dt;
  state.active = true;
  state.caught = catchNow;
  state.worn = amount;
}

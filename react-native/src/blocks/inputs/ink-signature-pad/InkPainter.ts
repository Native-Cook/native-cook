import { BlendMode, BlurStyle, ClipOp, PaintStyle, type SkCanvas, type SkPaint, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';

// Ink is kept as one flat list of samples, STRIDE numbers each: x, y (pt in the pad), the time it
// was laid down (ms on the pad's clock), the nib's width there (pt) and the stroke it belongs to.
export const STRIDE = 5;

// The nib: slow ink is wide, fast ink thin. width = MIN + (MAX − MIN)·e^(−speed / FALLOFF), speed
// in pt/s, and each sample moves EASE of the way from the last width to that, so it swells and
// thins smoothly instead of jumping with every jitter of the finger.
const NIB_MAX = 7;
const NIB_MIN = 1.1;
const NIB_FALLOFF = 500;
const NIB_EASE = 0.3;
// The width a stroke lands with, as a share of NIB_MAX: the pen pools a little where it touches.
const NIB_LANDING = 0.82;
// Samples closer than this to the last one (pt) are dropped.
const MIN_STEP = 0.8;
// Each span between two samples is drawn through as a Catmull-Rom curve in pieces about SUBSTEP pt
// long, at most MAX_PIECES of them.
const SUBSTEP = 2;
const MAX_PIECES = 8;

// Wet ink is a brighter, glossier blue that darkens to blue-black over DRY_MS, and carries a thin
// highlight on its upper left that fades as it dries. Wetness is drawn in WET_LEVELS steps.
export const DRY_MS = 1100;
const WET_LEVELS = 8;
const INK_WET = [0.16, 0.25, 0.6];
const INK_DRY = [0.1, 0.13, 0.32];
const SHEEN = [0.72, 0.8, 1];
const SHEEN_MIX = 0.62;
const SHEEN_WIDTH = 0.3;
// While a signature replays, the old ink shows as a pale ghost under the new.
const GHOST_ALPHA = 0.16;

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function nibWidth(speed: number) {
  'worklet';
  return NIB_MIN + (NIB_MAX - NIB_MIN) * Math.exp(-speed / NIB_FALLOFF);
}

function catmull(p0: number, p1: number, p2: number, p3: number, u: number) {
  'worklet';
  return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
}

/**
 * Appends a sample to `ink` (in place) and says whether it was kept. `speed` is the finger's speed
 * in pt/s; leave it below 0 to take it from the last sample's place and time.
 */
export function addSample(ink: number[], x: number, y: number, t: number, stroke: number, starts: boolean, speed: number) {
  'worklet';
  const n = ink.length;
  if (starts || n < STRIDE || ink[n - 1] !== stroke) {
    ink.push(x, y, t, NIB_MAX * NIB_LANDING, stroke);
    return true;
  }
  const d = Math.hypot(x - ink[n - 5], y - ink[n - 4]);
  if (d < MIN_STEP) return false;
  const v = speed >= 0 ? speed : d / Math.max((t - ink[n - 3]) / 1000, 1 / 240);
  const last = ink[n - 2];
  ink.push(x, y, t, last + (nibWidth(v) - last) * NIB_EASE, stroke);
  return true;
}

/**
 * A copy of `ink` for replaying from `start` (ms): the same samples, the same rhythm, but with the
 * pauses between strokes cut to `maxGap` ms.
 */
export function retime(ink: number[], start: number, maxGap: number) {
  'worklet';
  const out: number[] = [];
  let shift = start - ink[2];
  for (let i = 0; i < ink.length; i += STRIDE) {
    if (i > 0 && ink[i + 4] !== ink[i - 1]) {
      const gap = ink[i + 2] - ink[i - 3];
      if (gap > maxGap) shift -= gap - maxGap;
    }
    out.push(ink[i], ink[i + 1], ink[i + 2] + shift, ink[i + 3], ink[i + 4]);
  }
  return out;
}

/**
 * Where the pen is at `time` along `ink`, given that the first `cursor` samples are down: on the
 * last of them, or, between two strokes, gliding from the end of one to the start of the next and
 * lifted (lift 0…1) on the way.
 */
export function penAt(ink: number[], cursor: number, time: number) {
  'worklet';
  if (cursor <= 0) return { x: ink[0] ?? 0, y: ink[1] ?? 0, lift: 1 };
  const last = (cursor - 1) * STRIDE;
  const next = cursor * STRIDE;
  if (next >= ink.length || ink[next + 4] === ink[last + 4]) return { x: ink[last], y: ink[last + 1], lift: 0 };
  const f = clamp01((time - ink[last + 2]) / Math.max(1, ink[next + 2] - ink[last + 2]));
  const e = f * f * (3 - 2 * f);
  return {
    x: ink[last] + (ink[next] - ink[last]) * e,
    y: ink[last + 1] + (ink[next + 1] - ink[last + 1]) * e,
    lift: Math.sin(Math.PI * f),
  };
}

function rgba(c: number[], a: number) {
  'worklet';
  return Float32Array.of(c[0], c[1], c[2], a);
}

function mixed(a: number[], b: number[], k: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
}

function strokePaint() {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

/**
 * Cuts the ink drawn up to `upTo` (ms) into short straight pieces along its curves: x0, y0, x1, y1,
 * width, time for each.
 */
function pieces(ink: number[], upTo: number) {
  'worklet';
  const out: number[] = [];
  let a = 0;
  while (a < ink.length) {
    // The stroke runs from sample a to sample b (indices into ink).
    let b = a;
    while (b + STRIDE < ink.length && ink[b + STRIDE + 4] === ink[a + 4]) b += STRIDE;
    if (ink[a + 2] <= upTo) {
      if (a === b) {
        out.push(ink[a], ink[a + 1], ink[a], ink[a + 1], ink[a + 3], ink[a + 2]);
      }
      for (let i = a; i < b; i += STRIDE) {
        const j = i + STRIDE;
        if (ink[j + 2] > upTo && ink[i + 2] > upTo) break;
        const h = Math.max(a, i - STRIDE);
        const k = Math.min(b, j + STRIDE);
        const reach = ink[j + 2] <= upTo ? 1 : clamp01((upTo - ink[i + 2]) / Math.max(1, ink[j + 2] - ink[i + 2]));
        const steps = Math.min(MAX_PIECES, Math.max(1, Math.ceil(Math.hypot(ink[j] - ink[i], ink[j + 1] - ink[i + 1]) / SUBSTEP)));
        let px = ink[i];
        let py = ink[i + 1];
        for (let s = 1; s <= steps; s += 1) {
          let u = s / steps;
          if (u > reach) u = reach;
          const qx = catmull(ink[h], ink[i], ink[j], ink[k], u);
          const qy = catmull(ink[h + 1], ink[i + 1], ink[j + 1], ink[k + 1], u);
          out.push(px, py, qx, qy, ink[i + 3] + (ink[j + 3] - ink[i + 3]) * u, ink[i + 2] + (ink[j + 2] - ink[i + 2]) * u);
          px = qx;
          py = qy;
          if (u >= reach) break;
        }
        if (reach < 1) break;
      }
    }
    a = b + STRIDE;
  }
  return out;
}

/** Strokes the pieces: the ink first, then its highlight on top, so no piece hides another's shine. */
function drawPieces(canvas: SkCanvas, cut: number[], now: number) {
  'worklet';
  const inks: SkPaint[] = [];
  const sheens: SkPaint[] = [];
  for (let level = 0; level < WET_LEVELS; level += 1) {
    const wet = level / (WET_LEVELS - 1);
    const ink = strokePaint();
    const tone = mixed(INK_DRY, INK_WET, wet);
    ink.setColor(rgba(tone, 1));
    inks.push(ink);
    const sheen = strokePaint();
    sheen.setColor(rgba(mixed(tone, SHEEN, SHEEN_MIX * wet), 1));
    sheens.push(sheen);
  }
  const levels: number[] = [];
  for (let i = 0; i < cut.length; i += 6) {
    const wet = clamp01(1 - (now - cut[i + 5]) / DRY_MS);
    // A NaN level would index past the paints and crash on the next call.
    const level = Number.isFinite(wet) ? Math.round(wet * (WET_LEVELS - 1)) : 0;
    levels.push(level);
    const paint = inks[level];
    paint.setStrokeWidth(cut[i + 4]);
    if (cut[i] === cut[i + 2] && cut[i + 1] === cut[i + 3]) {
      paint.setStyle(PaintStyle.Fill);
      canvas.drawCircle(cut[i], cut[i + 1], cut[i + 4] / 2, paint);
      paint.setStyle(PaintStyle.Stroke);
    } else {
      canvas.drawLine(cut[i], cut[i + 1], cut[i + 2], cut[i + 3], paint);
    }
  }
  for (let i = 0, n = 0; i < cut.length; i += 6, n += 1) {
    const level = levels[n];
    if (level === 0) continue;
    const w = cut[i + 4];
    const paint = sheens[level];
    paint.setStrokeWidth(w * SHEEN_WIDTH);
    const dx = -w * 0.17;
    const dy = -w * 0.2;
    canvas.drawLine(cut[i] + dx, cut[i + 1] + dy, cut[i + 2] + dx, cut[i + 3] + dy, paint);
  }
}

/** The pale copy of the whole signature that the replay draws over. */
function drawGhost(canvas: SkCanvas, ink: number[], alpha: number) {
  'worklet';
  if (alpha <= 0.002 || ink.length === 0) return;
  const layer = Skia.Paint();
  layer.setAlphaf(alpha * GHOST_ALPHA);
  canvas.saveLayer(layer);
  const cut = pieces(ink, Infinity);
  const paint = strokePaint();
  paint.setColor(rgba(INK_DRY, 1));
  for (let i = 0; i < cut.length; i += 6) {
    paint.setStrokeWidth(cut[i + 4]);
    canvas.drawLine(cut[i], cut[i + 1], cut[i + 2] + 0.01, cut[i + 3], paint);
  }
  canvas.restore();
}

// The wiper Clear sweeps across: a walnut block faced with felt on its leading edge, as tall as the
// pad less WIPER_INSET at each end, with a brass knob to hold it by. It travels from off the left
// edge to off the right edge.
export const WIPER_WIDTH = 46;
const WIPER_INSET = 22;
const FELT = 12;
const KNOB = 11;
const WIPER_LEAN = 7;
// Ink dust brushed off by the felt: CRUMBS specks, each living CRUMB_LIFE of the sweep.
const CRUMBS = 34;
const CRUMB_LIFE = 0.32;

function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

/** Where the felt edge of the wiper is at a point of the sweep (0…1). */
export function wiperEdge(sweep: number, width: number) {
  'worklet';
  return -WIPER_WIDTH + (width + 2 * WIPER_WIDTH + FELT) * sweep;
}

function drawWiper(canvas: SkCanvas, sweep: number, width: number, height: number) {
  'worklet';
  const edge = wiperEdge(sweep, width);
  const top = WIPER_INSET;
  const h = height - 2 * WIPER_INSET;
  // Dust first, behind the block.
  const dust = Skia.Paint();
  dust.setAntiAlias(true);
  for (let i = 0; i < CRUMBS; i += 1) {
    const born = (i + 0.5) / CRUMBS;
    const age = (sweep - born) / CRUMB_LIFE;
    if (age <= 0 || age >= 1) continue;
    const bornX = wiperEdge(born, width);
    const y = top + 12 + hash(i) * (h - 24) + age * age * 22;
    const x = bornX - age * (10 + hash(i + 7) * 18);
    dust.setColor(rgba(INK_DRY, 0.55 * (1 - age)));
    canvas.drawCircle(x, y, 0.7 + hash(i + 3) * 1.1, dust);
  }
  // The block leans into its travel, most in the middle of the sweep.
  const lean = WIPER_LEAN * Math.sin(Math.PI * sweep);
  canvas.save();
  canvas.rotate(lean, edge, top + h / 2);
  const left = edge - WIPER_WIDTH;
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0.1, 0.06, 0.03, 0.32));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
  canvas.drawRRect({ rect: { x: left - 4, y: top + 8, width: WIPER_WIDTH + 4, height: h }, rx: 14, ry: 14 }, shadow);
  const wood = Skia.Paint();
  wood.setAntiAlias(true);
  wood.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: left, y: 0 },
      { x: edge, y: 0 },
      [Skia.Color('#6b4a33'), Skia.Color('#4a3121'), Skia.Color('#2d1d13')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect({ rect: { x: left, y: top, width: WIPER_WIDTH, height: h }, rx: 14, ry: 14 }, wood);
  // The grain: a few darker lines along the block.
  const grain = Skia.Paint();
  grain.setAntiAlias(true);
  grain.setStyle(PaintStyle.Stroke);
  grain.setStrokeWidth(0.8);
  grain.setColor(Float32Array.of(0.12, 0.07, 0.04, 0.35));
  for (let g = 0; g < 4; g += 1) {
    const gx = left + 6 + g * 7 + hash(g + 11) * 3;
    canvas.drawLine(gx, top + 10, gx + hash(g + 5) * 2 - 1, top + h - 10, grain);
  }
  // A brass knob in the middle, where a hand would hold it.
  const brass = Skia.Paint();
  brass.setAntiAlias(true);
  const knobX = left + (WIPER_WIDTH - FELT) / 2;
  const knobY = top + h / 2;
  brass.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: knobX - 3, y: knobY - 4 },
      KNOB * 1.3,
      [Skia.Color('#fbe7b5'), Skia.Color('#c99a46'), Skia.Color('#6d5020')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(knobX, knobY, KNOB, brass);
  const felt = Skia.Paint();
  felt.setAntiAlias(true);
  felt.setColor(Skia.Color('#d9d0bf'));
  canvas.drawRRect({ rect: { x: edge - FELT, y: top + 2, width: FELT, height: h - 4 }, rx: 4, ry: 4 }, felt);
  // The felt picks up ink as it goes.
  felt.setColor(rgba(INK_DRY, 0.45 * clamp01(sweep * 1.6)));
  canvas.drawRRect({ rect: { x: edge - FELT, y: top + 2, width: 3, height: h - 4 }, rx: 1.5, ry: 1.5 }, felt);
  canvas.restore();
}

// The fountain pen's nib, drawn while the pad writes or replays by itself: a gold nib with its slit
// and breather hole, its point on the ink, leaning NIB_ANGLE° and lifting on a growing shadow.
const NIB_ANGLE = 38;
function nibShape() {
  'worklet';
  return Skia.PathBuilder.Make()
    .moveTo(0, 0)
    .cubicTo(-3, -6, -8, -15, -8, -24)
    .lineTo(-8, -38)
    .lineTo(8, -38)
    .lineTo(8, -24)
    .cubicTo(8, -15, 3, -6, 0, 0)
    .close()
    .build();
}

function drawNib(canvas: SkCanvas, x: number, y: number, lift: number, alpha: number) {
  'worklet';
  if (alpha <= 0.01) return;
  const up = lift * 7;
  const shape = nibShape();
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(NIB_ANGLE, 0, 0);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0.12, 0.08, 0.02, 0.28 * alpha));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5 + up * 0.5, true));
  canvas.save();
  canvas.translate(3 + up, 2 + up);
  canvas.drawPath(shape, shadow);
  canvas.restore();
  canvas.translate(-up * 0.4, -up);
  const gold = Skia.Paint();
  gold.setAntiAlias(true);
  gold.setAlphaf(alpha);
  gold.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: -8, y: 0 },
      { x: 8, y: 0 },
      [Skia.Color('#8a6a2c'), Skia.Color('#f4dc9a'), Skia.Color('#c99a46'), Skia.Color('#6d5020')],
      [0, 0.35, 0.65, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(shape, gold);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(0.9);
  line.setColor(Float32Array.of(0.25, 0.17, 0.05, alpha));
  canvas.drawLine(0, -1.5, 0, -17, line);
  line.setStyle(PaintStyle.Fill);
  canvas.drawCircle(0, -18.5, 1.8, line);
  // The ink on the point.
  line.setColor(rgba(INK_WET, alpha));
  canvas.drawCircle(0, -0.8, 1.2, line);
  canvas.restore();
}

export type InkFrame = {
  ink: number[];
  /** While replaying: the retimed copy being drawn, else empty. */
  replay: number[];
  /** The pad's clock, ms. */
  now: number;
  ghost: number;
  /** 0…1 through a sweep of the wiper; 0 when none. */
  sweep: number;
  /** The whole ink's opacity: reduced motion fades it out instead of sweeping it away. */
  alpha: number;
  width: number;
  height: number;
  pen: { x: number; y: number; lift: number; alpha: number };
};

/** Everything drawn over the paper: the ink (multiplied into it), the wiper and the pen. */
export function paintInk(canvas: SkCanvas, f: InkFrame) {
  'worklet';
  // The ink is drawn opaque into a layer that is multiplied into the paper, so the paper's grain
  // shows through it and pieces that overlap never darken each other.
  const layer = Skia.Paint();
  layer.setBlendMode(BlendMode.Multiply);
  layer.setAlphaf(f.alpha);
  canvas.saveLayer(layer);
  if (f.sweep > 0) {
    canvas.clipRect({ x: wiperEdge(f.sweep, f.width) - FELT / 2, y: -50, width: f.width + 100, height: f.height + 100 }, ClipOp.Intersect, true);
  }
  if (f.replay.length > 0) {
    drawGhost(canvas, f.ink, f.ghost);
    drawPieces(canvas, pieces(f.replay, f.now), f.now);
  } else {
    drawGhost(canvas, f.ink, f.ghost);
    drawPieces(canvas, pieces(f.ink, Infinity), f.now);
  }
  canvas.restore();
  if (f.sweep > 0 && f.sweep < 1) drawWiper(canvas, f.sweep, f.width, f.height);
  drawNib(canvas, f.pen.x, f.pen.y, f.pen.lift, f.pen.alpha);
}

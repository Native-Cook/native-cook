import { BlurStyle, Skia, type SkImage, type SkPath } from '@shopify/react-native-skia';

import { clamp01, type Fuse, fuseAt, hash, LOOP, type Pose, SAMPLES } from './fuseBurnMotion';
import { BACKDROP_SKSL } from './fuseBurnShader';

/*
 Drawing helpers. The poster is recorded once into a picture; the fuse, its sparks and its smoke
 are rebuilt in worklets each frame as a handful of batched paths (never one node per particle).
 */

export type Layout = {
  width: number;
  height: number;
  headerTop: number;
  poster: { x: number; y: number; w: number; h: number };
  rowsTop: number;
  capsule: { x: number; y: number; w: number; h: number };
  hintTop: number;
};

const HEADER = 86;
const ROWS = 76;
const CAPSULE_H = 60;

export function makeLayout(width: number, height: number, top: number, bottom: number): Layout {
  const headerTop = top + 14;
  const hintTop = height - Math.max(bottom, 14) - 22;
  const capsuleY = hintTop - 18 - CAPSULE_H;
  const w = Math.min(width - 48, 420);
  const x = (width - w) / 2;
  const rowsTop = capsuleY - 30 - ROWS;
  const posterY = headerTop + HEADER + 8;
  const posterH = Math.max(150, Math.min(460, rowsTop - 18 - posterY));
  return {
    width,
    height,
    headerTop,
    poster: { x, y: posterY, w, h: posterH },
    rowsTop,
    capsule: { x, y: capsuleY, w, h: CAPSULE_H },
    hintTop,
  };
}

const backdropEffect = Skia.RuntimeEffect.Make(BACKDROP_SKSL);

/** The backdrop, baked into one image at `scale` pixels per point. */
export function bakeBackdrop(l: Layout, scale: number): SkImage | null {
  if (!backdropEffect) return null;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(l.width * scale), Math.ceil(l.height * scale));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  const paint = Skia.Paint();
  paint.setShader(backdropEffect.makeShader([l.width, l.height, l.width / 2, l.capsule.y + l.capsule.h / 2]));
  canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), paint);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** The event poster: a rooftop at dusk, string lights, a skyline. Recorded once. */
export function recordPoster(p: Layout['poster']) {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(p.x, p.y, p.w, p.h));
  const clip = Skia.RRectXY(Skia.XYWHRect(p.x, p.y, p.w, p.h), 24, 24);
  c.save();
  c.clipRRect(clip, 1, true);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: p.x, y: p.y },
      { x: p.x, y: p.y + p.h },
      [Skia.Color('#0b1030'), Skia.Color('#1e1d55'), Skia.Color('#5b2a6e'), Skia.Color('#d8694a'), Skia.Color('#f0a35e')],
      [0, 0.38, 0.62, 0.84, 1],
      0,
    ),
  );
  c.drawRect(Skia.XYWHRect(p.x, p.y, p.w, p.h), paint);
  // A warm glow on the horizon.
  const glow = Skia.Paint();
  glow.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: p.x + p.w * 0.55, y: p.y + p.h * 0.86 },
      p.w * 0.6,
      [Skia.Color('rgba(255,170,100,0.55)'), Skia.Color('rgba(255,120,80,0.15)'), Skia.Color('rgba(255,120,80,0)')],
      [0, 0.5, 1],
      0,
    ),
  );
  c.drawRect(Skia.XYWHRect(p.x, p.y, p.w, p.h), glow);
  // Stars.
  const star = Skia.Paint();
  star.setAntiAlias(true);
  for (let i = 0; i < 46; i += 1) {
    const sx = p.x + hash(i * 3.1) * p.w;
    const sy = p.y + hash(i * 7.7 + 2) * p.h * 0.5;
    star.setColor(Skia.Color(`rgba(220,228,255,${0.25 + hash(i + 9) * 0.6})`));
    c.drawCircle(sx, sy, 0.5 + hash(i + 4) * 0.9, star);
  }
  // The moon with a halo.
  const mx = p.x + p.w * 0.78;
  const my = p.y + p.h * 0.2;
  const halo = Skia.Paint();
  halo.setShader(
    Skia.Shader.MakeRadialGradient({ x: mx, y: my }, 60, [Skia.Color('rgba(255,236,210,0.35)'), Skia.Color('rgba(255,236,210,0)')], null, 0),
  );
  c.drawCircle(mx, my, 60, halo);
  const moon = Skia.Paint();
  moon.setAntiAlias(true);
  moon.setShader(
    Skia.Shader.MakeRadialGradient({ x: mx - 5, y: my - 5 }, 22, [Skia.Color('#fff4df'), Skia.Color('#f3dcb6'), Skia.Color('#d9b98f')], [0, 0.6, 1], 0),
  );
  c.drawCircle(mx, my, 16, moon);
  // Skyline, two layers.
  const ground = p.y + p.h;
  for (let layer = 0; layer < 2; layer += 1) {
    const b = Skia.Paint();
    b.setAntiAlias(true);
    b.setColor(Skia.Color(layer === 0 ? '#3a2559' : '#140f2b'));
    const win = Skia.Paint();
    win.setColor(Skia.Color('rgba(255,196,120,0.75)'));
    let x = p.x - 6;
    let k = layer * 50;
    while (x < p.x + p.w) {
      const bw = 18 + hash(k) * 30;
      const bh = (layer === 0 ? 0.28 : 0.16) * p.h + hash(k + 1) * p.h * (layer === 0 ? 0.22 : 0.18);
      c.drawRect(Skia.XYWHRect(x, ground - bh, bw - 2, bh), b);
      if (layer === 1) {
        for (let wy = ground - bh + 6; wy < ground - 6; wy += 7) {
          for (let wx = x + 4; wx < x + bw - 6; wx += 6) {
            if (hash(wx * 0.37 + wy * 1.3) > 0.72) c.drawRect(Skia.XYWHRect(wx, wy, 2, 3), win);
          }
        }
      }
      x += bw;
      k += 2;
    }
  }
  // The rooftop's string lights: a sagging wire with warm bulbs.
  const wire = Skia.PathBuilder.Make();
  const y0 = p.y + p.h * 0.34;
  const y1 = p.y + p.h * 0.3;
  wire.moveTo(p.x - 4, y0);
  wire.quadTo(p.x + p.w * 0.5, p.y + p.h * 0.52, p.x + p.w + 4, y1);
  const wp = Skia.Paint();
  wp.setAntiAlias(true);
  wp.setStyle(1);
  wp.setStrokeWidth(1);
  wp.setColor(Skia.Color('rgba(20,16,40,0.9)'));
  c.drawPath(wire.build(), wp);
  const bulbGlow = Skia.Paint();
  bulbGlow.setAntiAlias(true);
  bulbGlow.setColor(Skia.Color('rgba(255,190,110,0.5)'));
  bulbGlow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  const bulb = Skia.Paint();
  bulb.setAntiAlias(true);
  bulb.setColor(Skia.Color('#ffe2a8'));
  for (let i = 1; i < 14; i += 1) {
    const u = i / 14;
    // The quadratic's point at u.
    const bx = (1 - u) * (1 - u) * (p.x - 4) + 2 * u * (1 - u) * (p.x + p.w * 0.5) + u * u * (p.x + p.w + 4);
    const by = (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * (p.y + p.h * 0.52) + u * u * y1 + 4;
    c.drawCircle(bx, by, 7, bulbGlow);
    c.drawCircle(bx, by, 2.2, bulb);
  }
  // Vignette inside the poster.
  const vig = Skia.Paint();
  vig.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: p.x + p.w / 2, y: p.y + p.h * 0.45 },
      Math.max(p.w, p.h) * 0.75,
      [Skia.Color('rgba(5,6,20,0)'), Skia.Color('rgba(5,6,20,0.45)')],
      [0.55, 1],
      0,
    ),
  );
  c.drawRect(Skia.XYWHRect(p.x, p.y, p.w, p.h), vig);
  c.restore();
  return rec.finishRecordingAsPicture();
}

/** Small per-sample crinkle of the charcoal, so the burnt cord looks curled, not drawn. */
function crinkle(i: number) {
  'worklet';
  return Math.sin(i * 0.9) * 0.9 + Math.sin(i * 2.3 + 1) * 0.5;
}

function addRange(b: ReturnType<typeof Skia.PathBuilder.Make>, f: Fuse, i0: number, i1: number, wobble: boolean) {
  'worklet';
  if (i1 - i0 < 1) return;
  for (let i = i0; i <= i1; i += 1) {
    const o = wobble ? crinkle(i) : 0;
    const x = f.xs[i] + f.nx[i] * o;
    const y = f.ys[i] + f.ny[i] * o;
    if (i === i0) b.moveTo(x, y);
    else b.lineTo(x, y);
  }
}

function addTicks(b: ReturnType<typeof Skia.PathBuilder.Make>, f: Fuse, i0: number, i1: number) {
  'worklet';
  for (let i = i0 - (i0 % 3); i <= i1; i += 3) {
    if (i < i0) continue;
    const nx = f.nx[i];
    const ny = f.ny[i];
    // Along the cord is the normal turned a quarter; a twist runs diagonally across it.
    const tx = -ny;
    const ty = nx;
    b.moveTo(f.xs[i] - nx * 2 - tx * 1.4, f.ys[i] - ny * 2 - ty * 1.4);
    b.lineTo(f.xs[i] + nx * 2 + tx * 1.4, f.ys[i] + ny * 2 + ty * 1.4);
  }
}

export type CordPaths = { cord: SkPath; ticks: SkPath; charcoal: SkPath; ember: SkPath };

/** The cord for a burn at b with the start re-woven up to w. */
export function cordPaths(f: Fuse, b: number, w: number): CordPaths {
  'worklet';
  const last = SAMPLES - 1;
  const ib = Math.round(clamp01(b) * last);
  const iw = Math.round(clamp01(w) * last);
  const cord = Skia.PathBuilder.Make();
  const ticks = Skia.PathBuilder.Make();
  const charcoal = Skia.PathBuilder.Make();
  const ember = Skia.PathBuilder.Make();
  addRange(cord, f, ib, last, false);
  addTicks(ticks, f, ib, last);
  if (iw > 0) {
    addRange(cord, f, 0, Math.min(iw, last), false);
    addTicks(ticks, f, 0, Math.min(iw, ib));
  }
  addRange(charcoal, f, iw, ib, true);
  addRange(ember, f, Math.max(iw, ib - 18), ib, true);
  return { cord: cord.build(), ticks: ticks.build(), charcoal: charcoal.build(), ember: ember.build() };
}

// Sparks thrown by the burning fuse: each is a pure function of the time, its seed and the
// spark's place, and lives LOOP / k s with a whole k, so the loop repeats exactly.
export const SPARKS = 96;
const GRAVITY = 300;

/** [hot, warm] streak paths of the sparks; a burst adds 30 radial ones from the capsule. */
export function sparkPaths(f: Fuse, pose: Pose, cx: number, cy: number): [SkPath, SkPath] {
  'worklet';
  const hot = Skia.PathBuilder.Make();
  const warm = Skia.PathBuilder.Make();
  if (pose.spark > 0.01) {
    const [sx, sy, nx, ny] = fuseAt(f, pose.b);
    // Along the fuse (clockwise): the normal turned a quarter.
    const tx = -ny;
    const ty = nx;
    for (let i = 0; i < SPARKS; i += 1) {
      if (hash(i + 5.5) > pose.spark) continue;
      const k = 12 + Math.floor(hash(i + 0.7) * 12);
      const life = LOOP / k;
      const phase = (pose.time / life + hash(i + 2.1)) % 1;
      const age = phase * life;
      // Thrown outward and up, a little back along the fuse; emitted from where the spark was.
      const spread = (hash(i + 3.3) - 0.5) * 2.2;
      const speed = 50 + hash(i + 8.8) * 110;
      const dirX = nx * Math.cos(spread) - ny * Math.sin(spread) - tx * 0.35;
      const dirY = ny * Math.cos(spread) + nx * Math.sin(spread) - ty * 0.35 - 0.45;
      const ex = sx - tx * pose.speed * age * 0.6;
      const ey = sy - ty * pose.speed * age * 0.6;
      const vx = dirX * speed;
      const vy = dirY * speed;
      const x = ex + vx * age;
      const y = ey + vy * age + 0.5 * GRAVITY * age * age;
      const len = 0.018 + (1 - phase) * 0.02;
      const target = phase < 0.45 ? hot : warm;
      target.moveTo(x, y);
      target.lineTo(x - vx * len, y - (vy + GRAVITY * age) * len);
    }
  }
  if (pose.burstAge >= 0 && pose.burstAge < 0.9) {
    const a = pose.burstAge;
    for (let i = 0; i < 30; i += 1) {
      const ang = (i / 30) * Math.PI * 2 + hash(i + 40) * 0.2;
      const speed = 170 + hash(i + 41) * 170;
      const travel = (speed * (1 - Math.exp(-3.2 * a))) / 3.2;
      const x = cx + Math.cos(ang) * (travel + 20) * 1.6;
      const y = cy + Math.sin(ang) * (travel + 8) + 60 * a * a;
      const tail = 10 * Math.exp(-2.5 * a);
      const target = a < 0.35 ? hot : warm;
      if (a > 0.75 && hash(i + 42) > (0.9 - a) * 6) continue;
      target.moveTo(x, y);
      target.lineTo(x - Math.cos(ang) * tail * 1.6, y - Math.sin(ang) * tail);
    }
  }
  return [hot.build(), warm.build()];
}

/** Soft smoke puffs rising from the burnt cord (drawn blurred, as one path). */
export function smokePath(f: Fuse, pose: Pose) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const amount = Math.min(1, pose.b * 8) * (1 - pose.w);
  if (amount < 0.01) return b.build();
  for (let j = 0; j < 9; j += 1) {
    const k = 3 + Math.floor(hash(j + 60) * 3);
    const life = LOOP / k;
    const phase = (pose.time / life + hash(j + 61)) % 1;
    // Born along the burnt part, nearer the spark.
    const u = pose.b * (1 - hash(j + 62) * 0.5 * (1 - phase * 0.3));
    const [x, y] = fuseAt(f, u);
    const rise = phase * 70;
    const drift = Math.sin(phase * 5 + j) * 10 * phase;
    const r = (6 + phase * 12) * Math.sin(Math.PI * phase) * amount;
    if (r > 0.5) b.addCircle(x + drift, y - rise, r);
  }
  return b.build();
}

/** Opacity of the smoke (fades in with the burn, out with the re-weave). */
export function smokeOpacity(pose: Pose) {
  'worklet';
  return 0.2 * Math.min(1, pose.b * 8) * (1 - pose.w);
}

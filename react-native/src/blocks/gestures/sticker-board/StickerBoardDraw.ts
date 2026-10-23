import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  StrokeCap,
  StrokeJoin,
  TileMode,
  vec,
} from '@shopify/react-native-skia';

import { CREASES, FACET_STEP, RING, type Sticker } from './StickerBoardShapes';

export type Loaded = { image: SkImage; at: number };
export type Fonts = { bold: SkFont; small: SkFont };

// A photo fades in over this long once it has loaded (s).
const IMAGE_FADE = 0.3;
// The shadow a sticker casts lying down and lifted: x, y offset and blur (pt).
const SHADOW_NEAR = [1, 2, 2.2];
const SHADOW_FAR = [6, 16, 11];

export function clamp01(x: number) {
  'worklet';
  if (!Number.isFinite(x)) return 0;
  return Math.min(1, Math.max(0, x));
}

export function easeInOut(p: number) {
  'worklet';
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}

export function easeOut(p: number) {
  'worklet';
  return 1 - Math.pow(1 - p, 3);
}

/** Ease-out that runs a little past 1 and comes back. */
export function easeOutBack(p: number) {
  'worklet';
  const c = 1.7;
  const q = p - 1;
  return 1 + (c + 1) * q * q * q + c * q * q;
}

export function rubber(x: number, min: number, max: number, band: number) {
  'worklet';
  if (x < min) return min - band * (1 - 1 / ((min - x) / band + 1));
  if (x > max) return max + band * (1 - 1 / ((x - max) / band + 1));
  return x;
}

/** One step of a damped spring, in small sub-steps so a long frame cannot blow it up. */
export function spring(x: number, v: number, target: number, stiffness: number, damping: number, dt: number) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt * 240));
  const h = dt / steps;
  let nx = x;
  let nv = v;
  for (let i = 0; i < steps; i += 1) {
    nv += (-stiffness * (nx - target) - damping * nv) * h;
    nx += nv * h;
  }
  if (!Number.isFinite(nx) || !Number.isFinite(nv)) return [target, 0];
  return [nx, nv];
}

/** A sticker's place and turn, as the drawing and hit tests need them. */
export type Placed = { x: number; y: number; turn: number; scale: number };

/** A screen point in a sticker's own units. */
export function toLocal(s: Placed, k: number, x: number, y: number) {
  'worklet';
  const a = (-s.turn * Math.PI) / 180;
  const dx = x - s.x;
  const dy = y - s.y;
  const scale = Math.max(0.01, k * s.scale);
  return [(dx * Math.cos(a) - dy * Math.sin(a)) / scale, (dx * Math.sin(a) + dy * Math.cos(a)) / scale];
}

/** A point of a sticker's own units on the screen. */
export function toScreen(s: Placed, k: number, x: number, y: number) {
  'worklet';
  const a = (s.turn * Math.PI) / 180;
  const scale = k * s.scale;
  return [s.x + (x * Math.cos(a) - y * Math.sin(a)) * scale, s.y + (x * Math.sin(a) + y * Math.cos(a)) * scale];
}

/** The reflection across the fold through `m` with unit normal `n`, as a 3×3 matrix. */
function reflection(mx: number, my: number, nx: number, ny: number) {
  'worklet';
  const d = mx * nx + my * ny;
  return [1 - 2 * nx * nx, -2 * nx * ny, 2 * d * nx, -2 * nx * ny, 1 - 2 * ny * ny, 2 * d * ny, 0, 0, 1];
}

/** The half of the plane on the finger's side of the fold. */
export function keepSide(mx: number, my: number, nx: number, ny: number) {
  'worklet';
  const far = 2000;
  const px = -ny;
  const py = nx;
  return Skia.PathBuilder.Make()
    .addPoly(
      [
        vec(mx + px * far, my + py * far),
        vec(mx - px * far, my - py * far),
        vec(mx - px * far + nx * far, my - py * far + ny * far),
        vec(mx + px * far + nx * far, my + py * far + ny * far),
      ],
      true,
    )
    .build();
}

/** The sticker's face in its own units: white border, art, and a gloss that stays put as it turns. */
export function drawFace(canvas: SkCanvas, def: Sticker, art: SkPath, outline: SkPath, extent: number, loaded: Loaded | null, now: number, turn: number, fonts: Fonts) {
  'worklet';
  const paper = Skia.Paint();
  paper.setAntiAlias(true);
  paper.setColor(Skia.Color('#fbfaf6'));
  canvas.drawPath(outline, paper);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(1);
  edge.setStrokeWidth(0.8);
  edge.setColor(Skia.Color('rgba(70, 45, 20, 0.18)'));
  canvas.drawPath(outline, edge);

  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  if (def.photo) {
    fill.setColor(Skia.Color('#d8cfc2'));
    canvas.drawPath(art, fill);
    if (loaded) {
      const iw = loaded.image.width();
      const ih = loaded.image.height();
      const r = art.getBounds();
      if (iw > 0 && ih > 0 && r.width > 0 && r.height > 0) {
        canvas.save();
        canvas.clipPath(art, ClipOp.Intersect, true);
        const fit = Math.max(r.width / iw, r.height / ih);
        const sw = r.width / fit;
        const sh = r.height / fit;
        const p = Skia.Paint();
        p.setAntiAlias(true);
        p.setAlphaf(clamp01((now - loaded.at) / IMAGE_FADE));
        canvas.drawImageRectOptions(loaded.image, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), r, FilterMode.Linear, MipmapMode.Linear, p);
        canvas.restore();
      }
    }
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(1);
    rim.setStrokeWidth(1);
    rim.setColor(Skia.Color('rgba(0, 0, 0, 0.12)'));
    canvas.drawPath(art, rim);
  } else {
    fill.setColor(Skia.Color(def.color ?? '#ffc531'));
    canvas.drawPath(art, fill);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    if (def.kind === 'star') {
      // Round the star's points with a stroke of its own colour.
      fill.setStyle(1);
      fill.setStrokeWidth(5);
      fill.setStrokeJoin(StrokeJoin.Round);
      canvas.drawPath(art, fill);
    } else if (def.kind === 'smiley') {
      const r = def.size[0];
      ink.setColor(Skia.Color('#3b2a12'));
      canvas.drawOval(Skia.XYWHRect(-r * 0.38, -r * 0.42, r * 0.2, r * 0.3), ink);
      canvas.drawOval(Skia.XYWHRect(r * 0.18, -r * 0.42, r * 0.2, r * 0.3), ink);
      ink.setStyle(1);
      ink.setStrokeWidth(r * 0.1);
      ink.setStrokeCap(StrokeCap.Round);
      canvas.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(-r * 0.52, -r * 0.42, r * 1.04, r * 0.96), 25, 130).build(), ink);
      const cheek = Skia.Paint();
      cheek.setAntiAlias(true);
      cheek.setColor(Skia.Color('rgba(255, 110, 90, 0.45)'));
      canvas.drawCircle(-r * 0.56, r * 0.14, r * 0.13, cheek);
      canvas.drawCircle(r * 0.56, r * 0.14, r * 0.13, cheek);
    } else if (def.kind === 'bubble' || def.kind === 'badge') {
      const text = def.text ?? '';
      const font = def.kind === 'bubble' ? fonts.bold : fonts.small;
      const w = font.measureText(text).width;
      ink.setColor(Skia.Color('#ffffff'));
      if (def.kind === 'badge') {
        const ring = Skia.Paint();
        ring.setAntiAlias(true);
        ring.setStyle(1);
        ring.setStrokeWidth(1.4);
        ring.setColor(Skia.Color('rgba(255, 255, 255, 0.75)'));
        const circle = Skia.PathBuilder.Make().addCircle(0, 0, def.size[0] * 0.78).build();
        canvas.drawPath(Skia.Path.Dash(circle, 3, 2.5, 0) ?? circle, ring);
        canvas.drawText(text, -w / 2, 5, ink, font);
      } else {
        canvas.drawText(text, -w / 2, 6, ink, font);
      }
    }
  }
  // The vinyl gloss: a soft band along a fixed screen diagonal, whatever the sticker's turn.
  const a = (-turn * Math.PI) / 180;
  const dx = Math.cos(a) * extent - Math.sin(a) * extent;
  const dy = Math.sin(a) * extent + Math.cos(a) * extent;
  const gloss = Skia.Paint();
  gloss.setAntiAlias(true);
  gloss.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(-dx, -dy),
      vec(dx, dy),
      [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.26)'), Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,255,255,0.08)')],
      [0.3, 0.42, 0.55, 0.8],
      TileMode.Clamp,
    ),
  );
  canvas.save();
  canvas.clipPath(outline, ClipOp.Intersect, true);
  canvas.drawRect(Skia.XYWHRect(-extent, -extent, extent * 2, extent * 2), gloss);
  canvas.restore();
}

/** The shadow a sticker casts, offset in screen terms however it is turned or scaled. */
export function drawShadow(canvas: SkCanvas, outline: SkPath, lift: number, turn: number, scale: number, alpha: number) {
  'worklet';
  const l = Math.min(Math.max(lift, 0), 1.6);
  const s = Math.max(0.01, scale);
  const ox = SHADOW_NEAR[0] + (SHADOW_FAR[0] - SHADOW_NEAR[0]) * l;
  const oy = SHADOW_NEAR[1] + (SHADOW_FAR[1] - SHADOW_NEAR[1]) * l;
  const blur = SHADOW_NEAR[2] + (SHADOW_FAR[2] - SHADOW_NEAR[2]) * l;
  const a = (-turn * Math.PI) / 180;
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('#2a1505'));
  shadow.setAlphaf(clamp01(alpha * (0.42 - 0.1 * Math.min(l, 1))));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur / s, true));
  canvas.save();
  canvas.translate((ox * Math.cos(a) - oy * Math.sin(a)) / s, (ox * Math.sin(a) + oy * Math.cos(a)) / s);
  canvas.drawPath(outline, shadow);
  canvas.restore();
}

/**
 * A sticker with a corner lifted. The fold is the line halfway between the corner and the finger,
 * square to the pull: the face is kept on the finger's side, and the part beyond the fold is
 * mirrored across it to show its pale backing, lit at the crease, casting a shadow on the rest.
 */
export function drawPeeled(canvas: SkCanvas, outline: SkPath, cx: number, cy: number, fx: number, fy: number, face: () => void, scale: number) {
  'worklet';
  const nx0 = fx - cx;
  const ny0 = fy - cy;
  const len = Math.hypot(nx0, ny0);
  if (!(len >= 0.5)) {
    face();
    return;
  }
  const nx = nx0 / len;
  const ny = ny0 / len;
  const mx = (cx + fx) / 2;
  const my = (cy + fy) / 2;
  const keep = keepSide(mx, my, nx, ny);
  const mirror = Skia.Matrix(reflection(mx, my, nx, ny));

  canvas.save();
  canvas.clipPath(keep, ClipOp.Intersect, true);
  face();
  // The flap's shadow on the face below it, nudged away from the crease.
  const shade = Skia.Paint();
  shade.setAntiAlias(true);
  shade.setColor(Skia.Color('#1e1004'));
  shade.setAlphaf(0.34);
  shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5 / Math.max(0.01, scale), true));
  canvas.save();
  canvas.translate(nx * 4, ny * 4 + 2);
  canvas.concat(mirror);
  canvas.drawPath(outline, shade);
  canvas.restore();
  // The flap: the backing paper, bright along the crease and greyer towards its tip.
  canvas.saveLayer();
  canvas.save();
  canvas.concat(mirror);
  const backing = Skia.Paint();
  backing.setAntiAlias(true);
  backing.setColor(Skia.Color('#ece7dc'));
  canvas.drawPath(outline, backing);
  canvas.restore();
  const light = Skia.Paint();
  light.setBlendMode(BlendMode.SrcATop);
  light.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(mx, my),
      vec(mx + (nx * len) / 2, my + (ny * len) / 2),
      [Skia.Color('#ffffff'), Skia.Color('#f1ece2'), Skia.Color('#c9c1b2')],
      [0, 0.18, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(mx - 600, my - 600, 1200, 1200), light);
  canvas.restore();
  canvas.restore();
}

/** Coordinate `axis` (0 for x, 1 for y) of ring point `i`, part way from the outline to the ball. */
function ringAt(ring: number[], ball: number[], i: number, axis: number, c: number, from: number, radius: number) {
  'worklet';
  const n = Math.floor(Math.min(ring.length, ball.length) / 2);
  if (n <= 0) return 0;
  const j = ((i % n) + n) % n;
  return ring[2 * j + axis] * from * (1 - c) + ball[2 * j + axis] * radius * c;
}

/** The ring of a sticker part way to a paper ball: its outline at `c` = 0, the ball at `c` = 1 (pt). */
export function crumplePath(ring: number[], ball: number[], c: number, from: number, radius: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const n = Math.floor(Math.min(ring.length, ball.length) / 2);
  for (let i = 0; i < n; i += 1) {
    const x = ringAt(ring, ball, i, 0, c, from, radius);
    const y = ringAt(ring, ball, i, 1, c, from, radius);
    if (i === 0) b.moveTo(x, y);
    else b.lineTo(x, y);
  }
  return b.close().build();
}

/**
 * A sticker crumpling into a paper ball, in screen points around its middle. Its face shrinks inside
 * the crumpling outline, so its colours show between the creases at first; then facets of light and
 * shade, a paler paper and a few sharp creases make it read as a ball.
 */
export function drawCrumpled(
  canvas: SkCanvas,
  def: Sticker,
  art: SkPath,
  outline: SkPath,
  extent: number,
  ring: number[],
  ball: number[],
  shades: number[],
  loaded: Loaded | null,
  now: number,
  fonts: Fonts,
  c: number,
  from: number,
  radius: number,
  turn: number,
) {
  'worklet';
  const n = Math.floor(Math.min(ring.length, ball.length) / 2);
  if (n < 3) return;
  const poly = crumplePath(ring, ball, c, from, radius);
  canvas.save();
  canvas.clipPath(poly, ClipOp.Intersect, true);
  const face = from + ((radius * 1.35) / extent - from) * c;
  canvas.save();
  canvas.scale(face, face);
  drawFace(canvas, def, art, outline, extent, loaded, now, turn, fonts);
  canvas.restore();
  // The paper shows more and more as it balls up.
  const paper = Skia.Paint();
  paper.setColor(Skia.Color('#f4efe4'));
  paper.setAlphaf(0.5 * c * c);
  canvas.drawPaint(paper);
  // Facets fanned from a point off the middle, each tilted to the light or away from it.
  const qx = radius * 0.24 * c;
  const qy = -radius * 0.18 * c;
  const facet = Skia.Paint();
  facet.setAntiAlias(true);
  for (let j = 0; j < shades.length; j += 1) {
    const i0 = j * FACET_STEP;
    const i1 = i0 + FACET_STEP;
    const s = shades[j];
    facet.setColor(Skia.Color(s >= 0 ? '#ffffff' : '#5a3f22'));
    facet.setAlphaf(clamp01(Math.abs(s) * (s >= 0 ? 0.42 : 0.32) * c));
    const tri = Skia.PathBuilder.Make().moveTo(qx, qy).lineTo(ringAt(ring, ball, i0, 0, c, from, radius), ringAt(ring, ball, i0, 1, c, from, radius)).lineTo(ringAt(ring, ball, i1, 0, c, from, radius), ringAt(ring, ball, i1, 1, c, from, radius)).close().build();
    canvas.drawPath(tri, facet);
  }
  // Sharp creases: a dark fold with a lit edge beside it.
  const dark = Skia.Paint();
  dark.setAntiAlias(true);
  dark.setStyle(1);
  dark.setStrokeWidth(0.9);
  dark.setStrokeCap(StrokeCap.Round);
  dark.setColor(Skia.Color('#4a3218'));
  dark.setAlphaf(0.45 * c);
  const lit = Skia.Paint();
  lit.setAntiAlias(true);
  lit.setStyle(1);
  lit.setStrokeWidth(0.8);
  lit.setStrokeCap(StrokeCap.Round);
  lit.setColor(Skia.Color('#ffffff'));
  lit.setAlphaf(0.7 * c);
  for (let j = 0; j < CREASES.length; j += 1) {
    const a = CREASES[j][0] % RING;
    const b = CREASES[j][1] % RING;
    const ax = ringAt(ring, ball, a, 0, c, from, radius);
    const ay = ringAt(ring, ball, a, 1, c, from, radius);
    const bx = ringAt(ring, ball, b, 0, c, from, radius);
    const by = ringAt(ring, ball, b, 1, c, from, radius);
    const crease = Skia.PathBuilder.Make()
      .moveTo(ax, ay)
      .lineTo((ax + bx) / 2 + qx * 0.5, (ay + by) / 2 + qy * 0.5)
      .lineTo(bx, by)
      .build();
    canvas.drawPath(crease, dark);
    canvas.save();
    canvas.translate(0.7, 0.7);
    canvas.drawPath(crease, lit);
    canvas.restore();
  }
  canvas.restore();
  // A crisp paper edge.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(0.8);
  rim.setStrokeJoin(StrokeJoin.Round);
  rim.setColor(Skia.Color('#6b4f30'));
  rim.setAlphaf(0.2 + 0.25 * c);
  canvas.drawPath(poly, rim);
}

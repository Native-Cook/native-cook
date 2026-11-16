// Woven Speaker Player — shapes. Everything on the display is drawn from these: the continuous-corner cover, the play ⇄ pause glyph
// (two quads that morph, so the bars grow out of the triangle's left edge), the chevrons, the cast icon, the waveform, the arc and
// the cover's figure (three flat variants, no portrait gloss). All in design pt, centred on 0, 0 unless said otherwise.
import { Skia, type SkPath, type SkPathBuilder } from '@shopify/react-native-skia';

const SMOOTHING = 0.6;

function rad(deg: number) {
  'worklet';
  return (deg * Math.PI) / 180;
}

/** Appends a continuous-corner rectangle (the iOS squircle: corner smoothing 0.6). */
export function addSquircle(pb: SkPathBuilder, x: number, y: number, w: number, h: number, radius: number) {
  'worklet';
  const budget = Math.max(Math.min(w, h) / 2, 0.01);
  const R = Math.min(radius, budget);
  let xi = SMOOTHING;
  if ((1 + xi) * R > budget) xi = Math.max(budget / Math.max(R, 0.01) - 1, 0);
  const p = (1 + xi) * R;
  const arcMeasure = 90 * (1 - xi);
  const arcLen = Math.sin(rad(arcMeasure / 2)) * R * Math.SQRT2;
  const alpha = (90 - arcMeasure) / 2;
  const p3p4 = R * Math.tan(rad(alpha / 2));
  const beta = 45 * xi;
  const c = p3p4 * Math.cos(rad(beta));
  const d = c * Math.tan(rad(beta));
  const b = (p - arcLen - c - d) / 3;
  const a = 2 * b;
  pb.moveTo(x + w - p, y);
  pb.rCubicTo(a, 0, a + b, 0, a + b + c, d);
  pb.rArcTo(R, R, 0, true, false, arcLen, arcLen);
  pb.rCubicTo(d, c, d, b + c, d, a + b + c);
  pb.lineTo(x + w, y + h - p);
  pb.rCubicTo(0, a, 0, a + b, -d, a + b + c);
  pb.rArcTo(R, R, 0, true, false, -arcLen, arcLen);
  pb.rCubicTo(-c, d, -(b + c), d, -(a + b + c), d);
  pb.lineTo(x + p, y + h);
  pb.rCubicTo(-a, 0, -(a + b), 0, -(a + b + c), -d);
  pb.rArcTo(R, R, 0, true, false, -arcLen, -arcLen);
  pb.rCubicTo(-d, -c, -d, -(b + c), -d, -(a + b + c));
  pb.lineTo(x, y + p);
  pb.rCubicTo(0, -a, 0, -(a + b), d, -(a + b + c));
  pb.rArcTo(R, R, 0, true, false, arcLen, -arcLen);
  pb.rCubicTo(c, -d, b + c, -d, a + b + c, -d);
  pb.close();
}

export function squirclePath(x: number, y: number, w: number, h: number, radius: number): SkPath {
  'worklet';
  const pb = Skia.PathBuilder.Make();
  addSquircle(pb, x, y, w, h, radius);
  return pb.build();
}

// the play ⇄ pause glyph: top-left, top-right, bottom-right, bottom-left of each of two quads
const PAUSE_L = [-6.5, -7.4, -2.1, -7.4, -2.1, 7.4, -6.5, 7.4];
const PAUSE_R = [2.1, -7.4, 6.5, -7.4, 6.5, 7.4, 2.1, 7.4];
const PLAY_L = [-4.8, -8.2, 2, -4.04, 2, 4.04, -4.8, 8.2];
const PLAY_R = [2, -4.04, 8.6, 0, 8.6, 0, 2, 4.04];

/** g = 0 the play triangle, 1 the pause bars; in between the bars grow out of the triangle's left edge. */
export function glyphPath(g: number): SkPath {
  'worklet';
  const pb = Skia.PathBuilder.Make();
  for (let q = 0; q < 2; q++) {
    const a = q === 0 ? PLAY_L : PLAY_R;
    const b = q === 0 ? PAUSE_L : PAUSE_R;
    pb.moveTo(a[0] + (b[0] - a[0]) * g, a[1] + (b[1] - a[1]) * g);
    for (let i = 1; i < 4; i++) pb.lineTo(a[i * 2] + (b[i * 2] - a[i * 2]) * g, a[i * 2 + 1] + (b[i * 2 + 1] - a[i * 2 + 1]) * g);
    pb.close();
  }
  return pb.build();
}

/** A double chevron (previous: dir −1, next: dir 1) in a 9 pt box. */
export function chevronPath(dir: number, size: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  const h = size * 0.5;
  const w = size * 0.5;
  for (let k = 0; k < 2; k++) {
    const x0 = dir * (k * w - w);
    pb.moveTo(x0 - dir * w * 0.5, -h);
    pb.lineTo(x0 + dir * w * 0.5, 0);
    pb.lineTo(x0 - dir * w * 0.5, h);
    pb.close();
  }
  return pb.build();
}

/** The cast icon: two arcs over a small triangle (an invented one, not the system's). */
export function castPath(size: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  const r1 = size * 0.46;
  const r2 = size * 0.28;
  pb.addArc(Skia.XYWHRect(-r1, -r1 - size * 0.06, r1 * 2, r1 * 2), 150, 240);
  pb.addArc(Skia.XYWHRect(-r2, -r2 - size * 0.06, r2 * 2, r2 * 2), 150, 240);
  return pb.build();
}

export function castFoot(size: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(0, -size * 0.06 + size * 0.02);
  pb.lineTo(size * 0.42, size * 0.5);
  pb.lineTo(-size * 0.42, size * 0.5);
  pb.close();
  return pb.build();
}

/** Five rounded bars of different heights. */
export function wavePath(size: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  const hs = [0.3, 0.62, 1, 0.55, 0.28];
  for (let i = 0; i < hs.length; i++) {
    const x = (i - 2) * size * 0.22;
    const h = hs[i] * size * 0.5;
    pb.moveTo(x, -h);
    pb.lineTo(x, h);
  }
  return pb.build();
}

/** The arc round the display from 3 o'clock, clockwise, `sweep` degrees (at least a 4° head); the caller rotates it to 12 o'clock. */
export function arcPath(r: number, sweep: number): SkPath {
  'worklet';
  const pb = Skia.PathBuilder.Make();
  pb.addArc(Skia.XYWHRect(-r, -r, r * 2, r * 2), 0, Math.max(sweep, 4));
  return pb.build();
}

/** A full ring of radius r. */
export function ringPath(r: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  pb.addCircle(0, 0, r);
  return pb.build();
}

/** The invented mark at the foot of the cover: a ring with a rising line. */
export function markPath(size: number): SkPath {
  const pb = Skia.PathBuilder.Make();
  const r = size * 0.5;
  pb.addCircle(0, 0, r);
  pb.moveTo(-r * 0.55, r * 0.35);
  pb.lineTo(-r * 0.1, -r * 0.1);
  pb.lineTo(r * 0.15, r * 0.15);
  pb.lineTo(r * 0.6, -r * 0.4);
  return pb.build();
}

// The cover: a duotone poster in the cover's own 68 × 68 box (centred on 0, 0). A head and shoulders in profile stands in front of a
// low sun; the sun's side of the silhouette carries a thin rim of light. Three variants turn the profile, move the sun and add a bun.
const PROFILE =
  'M-34 34 C-33 25 -28 20 -18 17 C-14 16 -12 14 -12 11 L-12 8 C-17 4 -20 -3 -19 -10 C-18 -20 -10 -26 -2 -25 C7 -24 12 -18 12 -12 ' +
  'L15 -5.5 C15.5 -4.4 14.8 -3.8 13.2 -3.4 L12.8 -1 C13.6 -0.2 13.3 0.8 12.4 1.2 C12.9 2.4 12.6 4 11 4.6 C10 6.4 7.6 7 4.6 6.6 ' +
  'L4.4 10 C6 13.5 11 15.5 18 17 C27 19 32 25 34 34 Z';
const BUN = 'M-16 -16 C-22 -16 -25 -22 -21.5 -26.5 C-17.5 -30.5 -10.5 -28.5 -10.5 -22.5 Z';
export type CoverArt = { figure: SkPath; sun: { x: number; y: number; r: number }; rim: { dx: number; dy: number } };
const ART = [
  { mirror: false, bun: true, sun: { x: 14, y: -8, r: 19 } },
  { mirror: true, bun: false, sun: { x: -12, y: -12, r: 16 } },
  { mirror: false, bun: false, sun: { x: 2, y: 12, r: 25 } },
];

/** Mirror an M/L/C path left to right: every x (each first number of a pair) changes sign. */
function mirrorX(svg: string) {
  let k = 0;
  return svg.replace(/[MLCZ]|-?\d*\.?\d+/g, (t) => {
    if (/[MLCZ]/.test(t)) {
      k = 0;
      return t;
    }
    const v = k++ % 2 === 0 ? -Number(t) : Number(t);
    return String(v);
  });
}

export function coverArt(variant: number): CoverArt {
  const a = ART[((Math.round(variant) % ART.length) + ART.length) % ART.length];
  const svg = a.bun ? `${PROFILE} ${BUN}` : PROFILE;
  const figure = Skia.Path.MakeFromSVGString(a.mirror ? mirrorX(svg) : svg) ?? Skia.Path.Make();
  // the rim shows where the silhouette is nudged toward the sun
  const len = Math.hypot(a.sun.x, a.sun.y) || 1;
  return { figure, sun: a.sun, rim: { dx: (a.sun.x / len) * 0.9, dy: (a.sun.y / len) * 0.9 - 0.3 } };
}

/** An arc of radius r centred on `centerDeg` (degrees clockwise from 3 o'clock, as Skia counts), `span` degrees wide. */
export function arcSegmentPath(r: number, centerDeg: number, span: number): SkPath {
  'worklet';
  const pb = Skia.PathBuilder.Make();
  pb.addArc(Skia.XYWHRect(-r, -r, r * 2, r * 2), centerDeg - span / 2, span);
  return pb.build();
}

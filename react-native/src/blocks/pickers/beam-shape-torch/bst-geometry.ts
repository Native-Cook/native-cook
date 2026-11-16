// Beam Shape Torch — shapes. The capsule is a continuous-corner rectangle (squircle, corner smoothing 0.6, the
// iOS / Figma construction) whose size morphs between the island and the panel; the torch and the sun are built
// once at their own size and placed with transforms; the arc is rebuilt per frame from `arcGeometry`.
import { Skia, type SkPath, type SkPathBuilder } from '@shopify/react-native-skia';

import { arcGeometry } from './bst-motion';
import { ref } from './bst-theme';

const SMOOTHING = 0.6;

function rad(deg: number) {
  'worklet';
  return (deg * Math.PI) / 180;
}

/** Appends a continuous-corner rectangle. The smoothing gives way where the sides are too short (a pill has none). */
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

/**
 * The torch, in design pt inside its 28 × 79 box (row glyph): a cap 8 pt tall with rounded top corners, a 3 pt
 * gap, then the head that tapers from the cap's width to the 16.6 pt handle, which ends in 3.5 pt corners.
 */
export function buildGlyphCap(): SkPath {
  const g = ref.glyph;
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(0, 8);
  pb.lineTo(0, 2.6);
  pb.quadTo(0, 0, 2.6, 0);
  pb.lineTo(g.w - 2.6, 0);
  pb.quadTo(g.w, 0, g.w, 2.6);
  pb.lineTo(g.w, 8);
  pb.close();
  return pb.build();
}

export function buildGlyphBody(): SkPath {
  const g = ref.glyph;
  const l = 5.7;
  const r = g.w - 5.7;
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(0.3, 11);
  pb.lineTo(g.w - 0.3, 11);
  pb.lineTo(g.w - 0.8, 21);
  pb.cubicTo(g.w - 1.2, 25.5, r + 0.2, 28, r, 32.5);
  pb.lineTo(r, g.h - 3.5);
  pb.quadTo(r, g.h, r - 3.5, g.h);
  pb.lineTo(l + 3.5, g.h);
  pb.quadTo(l, g.h, l, g.h - 3.5);
  pb.lineTo(l, 32.5);
  pb.cubicTo(l - 0.2, 28, 1.2, 25.5, 0.8, 21);
  pb.close();
  return pb.build();
}

/** The sun at the arc's end, centred on 0, 0 (row sun): a 4 pt ring and eight short rays. */
export function buildSun(): SkPath {
  const s = ref.sun;
  const pb = Skia.PathBuilder.Make();
  pb.addCircle(0, 0, s.ring);
  for (let i = 0; i < s.rays; i++) {
    const a = (i / s.rays) * Math.PI * 2;
    const cx = Math.cos(a);
    const sy = Math.sin(a);
    pb.moveTo(cx * s.rayIn, sy * s.rayIn);
    pb.lineTo(cx * s.rayOut, sy * s.rayOut);
  }
  return pb.build();
}

/** The arc (solid or dotted): through (cx, apexY), half-width hw, ends `sag` lower. Straight when sag is ~0. */
export function arcPath(cx: number, apexY: number, hw: number, sag: number): SkPath {
  'worklet';
  const g = arcGeometry(cx, apexY, hw, sag);
  const pb = Skia.PathBuilder.Make();
  if (g.straight) {
    pb.moveTo(cx - hw, apexY);
    pb.lineTo(cx + hw, apexY);
  } else {
    const deg = (g.half * 180) / Math.PI;
    pb.addArc(Skia.XYWHRect(cx - g.r, g.cy - g.r, 2 * g.r, 2 * g.r), -90 - deg, 2 * deg);
  }
  return pb.build();
}

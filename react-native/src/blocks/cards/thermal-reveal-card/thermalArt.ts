import {
  BlurStyle,
  matchFont,
  PaintStyle,
  Skia,
  type SkCanvas,
  type SkImage,
  type SkPath,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';

// The secret map under the ink: drawn once in code into an image the size of the card (at the
// screen's pixel ratio), then sampled by the card's shader. Positions are fractions of the card,
// so the map fits any card size. A teal sea, three islands (reef glow, sand, jungle, contour
// rings), a dashed route between three coves marked with an X, a compass rose and hand-lettered
// place names.

/** The three coves (fractions of the card), in route order. The autoPlay thumb passes over them. */
export const COVES = [
  [0.28, 0.24],
  [0.71, 0.52],
  [0.33, 0.8],
];

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

function hash(i: number) {
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/** A soft irregular island outline: a ring of points whose radius wobbles, joined with quads. */
function islandPath(cx: number, cy: number, rx: number, ry: number, seed: number, scale = 1): SkPath {
  const path = Skia.PathBuilder.Make();
  const n = 18;
  const pts: number[][] = [];
  for (let i = 0; i < n; i += 1) {
    const a = (i / n) * Math.PI * 2;
    const wobble =
      1 + 0.16 * Math.sin(a * 3 + seed * 1.7) + 0.09 * Math.sin(a * 5 + seed * 3.1) + 0.06 * (hash(seed * 13 + i) - 0.5);
    pts.push([cx + Math.cos(a) * rx * wobble * scale, cy + Math.sin(a) * ry * wobble * scale]);
  }
  const mid = (i: number) => {
    const a = pts[i % n];
    const b = pts[(i + 1) % n];
    return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2];
  };
  const m0 = mid(0);
  path.moveTo(m0[0], m0[1]);
  for (let i = 1; i <= n; i += 1) {
    const c = pts[i % n];
    const m = mid(i);
    path.quadTo(c[0], c[1], m[0], m[1]);
  }
  path.close();
  return path.build();
}

function paint(color: string, alpha = 1) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

function stroke(color: string, width: number, alpha = 1) {
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function drawIsland(c: SkCanvas, cx: number, cy: number, rx: number, ry: number, seed: number) {
  // Reef: a pale glow in the shallows around the island.
  const reef = paint('#8fe6d6', 0.55);
  reef.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.32), reef);
  c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.16), stroke('#d9fff6', 1, 0.45));
  // Sand, then jungle, then contour rings up to a small peak.
  c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.04), paint('#f1d39a'));
  const jungle = paint('#3f8a57');
  jungle.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx - rx * 0.2, cy - ry * 0.25),
      Math.max(rx, ry),
      [Skia.Color('#5ea866'), Skia.Color('#3c8152'), Skia.Color('#2b6243')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(islandPath(cx, cy, rx, ry, seed, 0.86), jungle);
  for (let k = 1; k <= 3; k += 1) {
    c.drawPath(islandPath(cx + rx * 0.05 * k, cy - ry * 0.04 * k, rx, ry, seed + k, 0.86 - k * 0.17), stroke('#1f4b33', 1, 0.45));
  }
  c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.04), stroke('#b78b4a', 1.2, 0.6));
}

function drawX(c: SkCanvas, x: number, y: number, s: number) {
  const under = stroke('#fff4dc', 7, 0.85);
  const ink = stroke('#c8302b', 4);
  c.drawLine(x - s, y - s, x + s, y + s, under);
  c.drawLine(x - s, y + s, x + s, y - s, under);
  c.drawLine(x - s, y - s, x + s, y + s, ink);
  c.drawLine(x - s, y + s, x + s, y - s, ink);
  c.drawCircle(x, y, s * 1.9, stroke('#c8302b', 1.2, 0.55));
}

function drawCompass(c: SkCanvas, x: number, y: number, r: number, font: ReturnType<typeof matchFont>) {
  c.drawCircle(x, y, r * 1.05, stroke('#fff1d0', 1.2, 0.8));
  c.drawCircle(x, y, r * 0.82, stroke('#fff1d0', 0.8, 0.5));
  for (let i = 0; i < 4; i += 1) {
    const a = (i * Math.PI) / 2 - Math.PI / 2;
    const tip = [x + Math.cos(a) * r, y + Math.sin(a) * r];
    const left = [x + Math.cos(a - Math.PI / 4) * r * 0.22, y + Math.sin(a - Math.PI / 4) * r * 0.22];
    const right = [x + Math.cos(a + Math.PI / 4) * r * 0.22, y + Math.sin(a + Math.PI / 4) * r * 0.22];
    const lit = Skia.PathBuilder.Make().moveTo(x, y).lineTo(tip[0], tip[1]).lineTo(left[0], left[1]).close().build();
    const dark = Skia.PathBuilder.Make().moveTo(x, y).lineTo(tip[0], tip[1]).lineTo(right[0], right[1]).close().build();
    c.drawPath(lit, paint(i === 0 ? '#ffe9b8' : '#fff4dc'));
    c.drawPath(dark, paint(i === 0 ? '#c8302b' : '#8a6a3c'));
  }
  const n = 'N';
  const nw = font.getGlyphWidths(font.getGlyphIDs(n)).reduce((a, b) => a + b, 0);
  c.drawText(n, x - nw / 2, y - r * 1.2, paint('#fff4dc'), font);
}

/** Draws the map into an image of w × h points at `scale` pixels per point. */
export function makeMapImage(w: number, h: number, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);

  // Sea: deep at the top, turquoise lower down, with lighter shallows.
  const sea = Skia.Paint();
  sea.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(w * 0.3, h),
      [Skia.Color('#0b4f6c'), Skia.Color('#127f97'), Skia.Color('#2bb3b0')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), sea);
  // Wave hatching and a chart grid.
  for (let i = 0; i < 70; i += 1) {
    const x = hash(i * 3 + 1) * w;
    const y = hash(i * 5 + 2) * h;
    const p = Skia.PathBuilder.Make().moveTo(x, y).quadTo(x + 5, y - 3, x + 10, y).quadTo(x + 15, y + 3, x + 20, y).build();
    c.drawPath(p, stroke('#bff3ee', 1, 0.22));
  }
  const grid = stroke('#e8fffb', 0.6, 0.12);
  for (let x = 28; x < w; x += 34) c.drawLine(x, 0, x, h, grid);
  for (let y = 28; y < h; y += 34) c.drawLine(0, y, w, y, grid);

  drawIsland(c, w * 0.27, h * 0.23, w * 0.2, h * 0.11, 1);
  drawIsland(c, w * 0.74, h * 0.5, w * 0.17, h * 0.13, 2);
  drawIsland(c, w * 0.3, h * 0.79, w * 0.22, h * 0.1, 3);
  drawIsland(c, w * 0.83, h * 0.85, w * 0.07, h * 0.04, 4);

  // The route: white underlay, then red dashes, from the top-left corner through the coves.
  const pts = [[0.08, 0.06], ...COVES, [0.9, 0.95]].map(([x, y]) => [x * w, y * h]);
  const routeBuilder = Skia.PathBuilder.Make();
  routeBuilder.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i += 1) {
    const a = pts[i - 1];
    const b = pts[i];
    const bend = i % 2 === 0 ? 0.28 : -0.28;
    routeBuilder.cubicTo(
      a[0] + (b[0] - a[0]) * 0.3 + (b[1] - a[1]) * bend,
      a[1] + (b[1] - a[1]) * 0.3 - (b[0] - a[0]) * bend,
      a[0] + (b[0] - a[0]) * 0.7 + (b[1] - a[1]) * bend,
      a[1] + (b[1] - a[1]) * 0.7 - (b[0] - a[0]) * bend,
      b[0],
      b[1],
    );
  }
  const route = routeBuilder.build();
  c.drawPath(route, stroke('#fff4dc', 5, 0.55));
  const dashes = stroke('#d23a2f', 2.6);
  dashes.setPathEffect(Skia.PathEffect.MakeDash([9, 7], 0));
  c.drawPath(route, dashes);

  for (const [x, y] of COVES) drawX(c, x * w, y * h, 8);

  const label = matchFont({ fontFamily: SERIF, fontSize: 12, fontStyle: 'italic', fontWeight: '600' });
  const small = matchFont({ fontFamily: SERIF, fontSize: 10, fontStyle: 'italic', fontWeight: '400' });
  const names = [
    ['Cove of Lanterns', COVES[0][0] * w + 16, COVES[0][1] * h - 14],
    ['Seal Rock', COVES[1][0] * w - 78, COVES[1][1] * h + 26],
    ['Glass Lagoon', COVES[2][0] * w + 18, COVES[2][1] * h + 4],
  ] as const;
  const ink = paint('#fff4dc');
  const shadow = paint('#06303f', 0.7);
  for (const [text, x, y] of names) {
    const tw = label.getGlyphWidths(label.getGlyphIDs(text)).reduce((a, b) => a + b, 0);
    const lx = Math.min(Math.max(x, 10), w - tw - 10);
    c.drawText(text, lx + 0.6, y + 0.8, shadow, label);
    c.drawText(text, lx, y, ink, label);
  }
  c.drawText('Aruna Sound', w * 0.52, h * 0.31, paint('#d6fbf4', 0.7), small);
  c.drawText('depth 12 fathoms', w * 0.06, h * 0.62, paint('#d6fbf4', 0.55), small);
  drawCompass(c, w - 40, 54, 20, small);

  // An aged border: darker at the very edge.
  const edge = Skia.Paint();
  edge.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(w / 2, h / 2),
      Math.hypot(w, h) * 0.62,
      [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(4,24,32,0.45)')],
      [0, 0.72, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), edge);

  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

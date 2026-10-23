/**
 * The two screens, drawn once at mount into images: the first screen (cream, the wordmark and the headline) and the map
 * screen under it (a neighbourhood map with a park, a canal and streets, a search bar, a back button and a route card).
 * The glass refracts the map image; the route itself is drawn live, so it can draw in when the map opens.
 */
import { BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont, type SkImage, type SkPath, type SkTypeface } from '@shopify/react-native-skia';

import { COLOR, LAYOUT, TYPE, type Layout, type TypeRole } from './theme';

export type Faces = { regular: SkTypeface; medium: SkTypeface; semibold: SkTypeface };
type Fonts = Record<TypeRole, SkFont>;

function makeFonts(faces: Faces): Fonts {
  const f = {} as Fonts;
  for (const role of Object.keys(TYPE) as TypeRole[]) f[role] = Skia.Font(faces[TYPE[role].face], TYPE[role].size);
  return f;
}

function width(font: SkFont, s: string): number {
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

function fill(color: string) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function stroke(color: string, w: number) {
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: string, align = 0) {
  const w = width(font, s);
  c.drawText(s, align === 0 ? x : align === 1 ? x - w / 2 : x - w, y, fill(color), font);
}

/** A soft drop shadow under a rounded box. */
function shadowBox(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, blur = 16, dy = 6, a = COLOR.shadow) {
  const p = fill(a);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur / 2, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + dy, w, h), r, r), p);
}

/** The route: a loop through the park, along the canal and back up the lane. In screen points. */
export function routePath(lay: Layout): SkPath {
  const { width: W, height: H } = lay;
  const b = Skia.PathBuilder.Make();
  b.moveTo(W * 0.2, H * 0.33);
  b.cubicTo(W * 0.3, H * 0.27, W * 0.46, H * 0.25, W * 0.56, H * 0.31);
  b.cubicTo(W * 0.66, H * 0.37, W * 0.62, H * 0.45, W * 0.74, H * 0.5);
  b.cubicTo(W * 0.86, H * 0.55, W * 0.78, H * 0.62, W * 0.6, H * 0.6);
  b.cubicTo(W * 0.42, H * 0.58, W * 0.3, H * 0.6, W * 0.24, H * 0.52);
  b.cubicTo(W * 0.18, H * 0.45, W * 0.12, H * 0.4, W * 0.2, H * 0.33);
  return b.build();
}

/** Deterministic noise for the trees, so every mount draws the same park. */
function rand(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

function drawMap(c: SkCanvas, lay: Layout, f: Fonts) {
  const { width: W, height: H } = lay;
  c.drawRect(Skia.XYWHRect(0, 0, W, H), fill(COLOR.block));
  // Streets on a grid turned a little, as old towns are; a white street over a darker casing.
  c.save();
  c.translate(W / 2, H / 2);
  c.rotate(-14, 0, 0);
  const span = Math.hypot(W, H);
  const streets = Skia.PathBuilder.Make();
  for (let k = -12; k <= 12; k++) {
    const x = k * 52 + ((k * 7) % 11);
    streets.moveTo(x, -span);
    streets.lineTo(x, span);
    const y = k * 46 + ((k * 5) % 9);
    streets.moveTo(-span, y);
    streets.lineTo(span, y);
  }
  const grid = streets.build();
  c.drawPath(grid, stroke(COLOR.streetCase, 8.5));
  c.drawPath(grid, stroke(COLOR.street, 6.5));
  c.restore();

  // The park, its edge and its trees.
  const park = Skia.PathBuilder.Make();
  park.moveTo(W * 0.04, H * 0.23);
  park.cubicTo(W * 0.2, H * 0.17, W * 0.46, H * 0.18, W * 0.62, H * 0.24);
  park.cubicTo(W * 0.7, H * 0.27, W * 0.66, H * 0.36, W * 0.52, H * 0.37);
  park.cubicTo(W * 0.36, H * 0.38, W * 0.2, H * 0.4, W * 0.08, H * 0.37);
  park.cubicTo(W * -0.02, H * 0.34, W * -0.02, H * 0.26, W * 0.04, H * 0.23);
  const parkPath = park.build();
  c.drawPath(parkPath, fill(COLOR.park));
  c.drawPath(parkPath, stroke(COLOR.parkEdge, 1.5));
  const r = rand(7);
  const tree = fill('#B5CF9C');
  c.save();
  c.clipPath(parkPath, ClipOp.Intersect, true);
  for (let i = 0; i < 70; i++) c.drawCircle(W * (0.04 + 0.62 * r()), H * (0.18 + 0.2 * r()), 3 + 4 * r(), tree);
  c.restore();
  // A pond in the park.
  c.drawOval(Skia.XYWHRect(W * 0.3, H * 0.27, W * 0.16, H * 0.045), fill(COLOR.water));

  // The canal, a wide band across the lower half, with two bridges.
  const canal = Skia.PathBuilder.Make();
  canal.moveTo(-20, H * 0.66);
  canal.cubicTo(W * 0.3, H * 0.6, W * 0.62, H * 0.7, W + 20, H * 0.56);
  const canalPath = canal.build();
  c.drawPath(canalPath, stroke(COLOR.waterEdge, 44));
  c.drawPath(canalPath, stroke(COLOR.water, 40));
  // Two main roads, warm, crossing the canal on bridges.
  const roads = Skia.PathBuilder.Make();
  roads.moveTo(W * 0.86, -20);
  roads.cubicTo(W * 0.8, H * 0.3, W * 0.88, H * 0.6, W * 0.7, H + 20);
  roads.moveTo(-20, H * 0.47);
  roads.cubicTo(W * 0.3, H * 0.44, W * 0.6, H * 0.47, W + 20, H * 0.42);
  const roadPath = roads.build();
  c.drawPath(roadPath, stroke(COLOR.majorCase, 13));
  c.drawPath(roadPath, stroke(COLOR.major, 11));

  // Names, small and quiet.
  text(c, 'KESTREL PARK', W * 0.22, H * 0.215, f.street, '#6F8D5B');
  text(c, 'Wend Canal', W * 0.08, H * 0.645, f.street, '#5E8AA0');
  text(c, 'Marlow Road', W * 0.42, H * 0.452, f.street, COLOR.label);
  c.save();
  c.translate(W * 0.84, H * 0.2);
  c.rotate(-82, 0, 0);
  text(c, 'Hatch Lane', 0, 0, f.street, COLOR.label);
  c.restore();
  text(c, 'Corn Exchange', W * 0.5, H * 0.79, f.street, COLOR.label);
}

/** A small glyph stroked in the map's ink: a back chevron or a magnifier. */
function drawChrome(c: SkCanvas, lay: Layout, f: Fonts) {
  const { width: W, top } = lay;
  const y = top + LAYOUT.searchY;
  const h = LAYOUT.searchH;
  // Back, a glass circle.
  shadowBox(c, LAYOUT.edge, y, h, h, h / 2, 14, 4);
  c.drawCircle(LAYOUT.edge + h / 2, y + h / 2, h / 2, fill(COLOR.glassFill));
  c.drawCircle(LAYOUT.edge + h / 2, y + h / 2, h / 2 - 0.5, stroke('rgba(255,255,255,0.9)', 1));
  const chev = Skia.PathBuilder.Make();
  chev.moveTo(LAYOUT.edge + h / 2 + 3, y + h / 2 - 7);
  chev.lineTo(LAYOUT.edge + h / 2 - 4, y + h / 2);
  chev.lineTo(LAYOUT.edge + h / 2 + 3, y + h / 2 + 7);
  c.drawPath(chev.build(), stroke(COLOR.ink, 2.2));
  // Search.
  const sx = LAYOUT.edge + h + 8;
  const sw = W - LAYOUT.edge - sx;
  shadowBox(c, sx, y, sw, h, h / 2, 14, 4);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx, y, sw, h), h / 2, h / 2), fill(COLOR.glassFill));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx + 0.5, y + 0.5, sw - 1, h - 1), h / 2, h / 2), stroke('rgba(255,255,255,0.9)', 1));
  c.drawCircle(sx + 22, y + h / 2 - 1.5, 6, stroke(COLOR.mute, 1.8));
  const handle = Skia.PathBuilder.Make();
  handle.moveTo(sx + 26.5, y + h / 2 + 3);
  handle.lineTo(sx + 30, y + h / 2 + 6.5);
  c.drawPath(handle.build(), stroke(COLOR.mute, 1.8));
  text(c, 'Search walks near you', sx + 40, y + h / 2 + 5, f.search, COLOR.mute);

  // The route card.
  const cx = LAYOUT.edge;
  const cy = lay.cardY;
  const cw = W - LAYOUT.edge * 2;
  const ch = LAYOUT.cardH;
  shadowBox(c, cx, cy, cw, ch, 24, 24, 8);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx, cy, cw, ch), 24, 24), fill(COLOR.card));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 0.5, cy + 0.5, cw - 1, ch - 1), 24, 24), stroke(COLOR.cardEdge, 1));
  text(c, 'Canal loop', cx + 20, cy + 36, f.cardTitle, COLOR.ink);
  text(c, '3.2 km · 45 min · Easy', cx + 20, cy + 58, f.cardMeta, COLOR.mute);
  // A small swatch of the route's colour, top right.
  c.drawCircle(cx + cw - 30, cy + 30, 6, fill(COLOR.route));
  c.drawCircle(cx + cw - 30, cy + 30, 9, stroke('rgba(232,100,46,0.25)', 3));
  let x = cx + 20;
  for (const chip of ['Shade', 'Coffee stop', 'Step-free']) {
    const w = width(f.chip, chip) + 20;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, cy + 72, w, 26), 13, 13), fill('#EEF1EC'));
    text(c, chip, x + 10, cy + 89, f.chip, COLOR.brand);
    x += w + 8;
  }
  const by = cy + ch - 20 - 44;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 20, by, cw - 40, 44), 22, 22), fill(COLOR.go));
  text(c, 'Start walk', cx + cw / 2, by + 27, f.button, COLOR.onGo, 1);
}

function drawIntro(c: SkCanvas, lay: Layout, f: Fonts) {
  const { width: W, height: H, top } = lay;
  c.drawRect(Skia.XYWHRect(0, 0, W, H), fill(COLOR.page));
  // A faint warmth from the bottom, where the glass is.
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: H * 0.5 }, { x: 0, y: H }, [Skia.Color('rgba(232,100,46,0)'), Skia.Color('rgba(232,100,46,0.06)')], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), glow);
  // The mark: a green disc with a looped path in it, and the wordmark.
  const m = LAYOUT.margin;
  const by = top + LAYOUT.brandY;
  c.drawCircle(m + 14, by + 14, 14, fill(COLOR.brand));
  const loop = Skia.PathBuilder.Make();
  loop.moveTo(m + 7, by + 18);
  loop.cubicTo(m + 9, by + 6, m + 21, by + 6, m + 20, by + 13);
  loop.cubicTo(m + 19, by + 20, m + 11, by + 19, m + 13, by + 13);
  loop.cubicTo(m + 15, by + 8, m + 23, by + 12, m + 21, by + 20);
  c.drawPath(loop.build(), stroke('#F4EFE6', 2));
  text(c, 'strolls', m + 36, by + 21, f.word, COLOR.ink);
  // The headline and its line.
  const hy = top + LAYOUT.headlineY;
  const lines = ['Walks worth taking,', 'five minutes from', 'your door.'];
  lines.forEach((l, i) => text(c, l, m, hy + i * TYPE.headline.line, f.headline, COLOR.ink));
  const body = ['Routes drawn by people who live there,', 'with the coffee stop marked.'];
  body.forEach((l, i) => text(c, l, m, hy + 3 * TYPE.headline.line + 8 + i * TYPE.body.line, f.body, COLOR.mute));
}

function snapshot(lay: Layout, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const W = Math.round(lay.width * scale);
  const H = Math.round(lay.height * scale);
  const s = Skia.Surface.MakeOffscreen(W, H) ?? Skia.Surface.Make(W, H);
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  draw(c);
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

export type Pages = { intro: SkImage; map: SkImage; fonts: Fonts };

export function makePages(lay: Layout, faces: Faces, scale: number): Pages | null {
  const fonts = makeFonts(faces);
  const intro = snapshot(lay, scale, (c) => drawIntro(c, lay, fonts));
  const map = snapshot(lay, scale, (c) => {
    drawMap(c, lay, fonts);
    drawChrome(c, lay, fonts);
  });
  return intro && map ? { intro, map, fonts } : null;
}

import { BlendMode, BlurStyle, ClipOp, matchFont, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage, type SkPath } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

/*
 Paper Cut Diorama Splash — layout and the six layers of cut paper, each baked once into its own
 image (with the soft shadow it throws on the layer behind), plus the bare paper backing and the
 light bloom of the cut-out logo.

 Back to front: a dusk sky (deep blue → violet → peach, a paper moon, a few paper stars), far
 lavender hills, a row of teal pines, green meadow with a winding river, a dark grass edge with
 blades, and the cream front frame with an arched window, the logo (a little arched door) and
 the wordmark "Fable" cut right through it. Layers are 30 pt wider than the screen on each side
 so tilting never shows an edge, and run 40 pt past the bottom.
 */

export const LAYERS = 6;
/** Depth of each layer for parallax and zoom: sky … frame. */
export const DEPTH = [0.42, 0.5, 0.58, 0.68, 0.8, 1];
const PAD = 30;

export type DioramaLayout = {
  W: number;
  H: number;
  win: { x0: number; x1: number; top: number; bottom: number; r: number };
  emblem: { x: number; y: number; w: number; h: number };
  wordY: number;
  tagY: number;
};

export function layoutDiorama(W: number, H: number, top: number): DioramaLayout {
  const x0 = 26;
  const x1 = W - 26;
  const r = (x1 - x0) / 2;
  const wTop = top + 34;
  const bottom = Math.max(wTop + r + 120, H * 0.67);
  const ew = 50;
  const eh = 68;
  const ey = bottom + 26;
  return { W, H, win: { x0, x1, top: wTop, bottom, r }, emblem: { x: W / 2 - ew / 2, y: ey, w: ew, h: eh }, wordY: ey + eh + 60, tagY: ey + eh + 92 };
}

const col = (c: string) => Skia.Color(c);

function hash(n: number) {
  const v = Math.sin(n * 91.345 + 7.13) * 43758.5453;
  return v - Math.floor(v);
}

export type LayerArt = { image: SkImage; x: number; y: number; w: number; h: number };

function surfaceFor(L: DioramaLayout, y: number, scale: number) {
  const w = L.W + PAD * 2;
  const h = L.H + 40 - y;
  const s = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  c.translate(PAD, -y);
  return { s, c, x: -PAD, y, w, h };
}

function finish(o: NonNullable<ReturnType<typeof surfaceFor>>): LayerArt {
  o.s.flush();
  return { image: o.s.makeImageSnapshot(), x: o.x, y: o.y, w: o.w, h: o.h };
}

/** Fill a cut-paper shape: its shadow on the layer behind, the paper, grain, a lit top edge. */
function paper(c: SkCanvas, path: SkPath, fill: ReturnType<typeof Skia.Paint>, L: DioramaLayout, shadow = 0.32, lit = true) {
  const sh = Skia.Paint();
  sh.setColor(col(`rgba(24,16,30,${shadow})`));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
  c.save();
  c.translate(0, -3);
  c.drawPath(path, sh);
  c.restore();
  c.drawPath(path, fill);
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 3, 17, 0, 0));
  g.setAlphaf(0.1);
  g.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(-PAD, 0, L.W + PAD * 2, L.H + 40), g);
  if (lit) {
    const edge = Skia.Paint();
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1.6);
    edge.setColor(col('rgba(255,248,236,0.22)'));
    c.drawPath(path, edge);
  }
  c.restore();
}

function ridge(L: DioramaLayout, y0: (x: number) => number, step = 6) {
  const b = Skia.PathBuilder.Make().moveTo(-PAD, L.H + 40);
  for (let x = -PAD; x <= L.W + PAD; x += step) b.lineTo(x, y0(x));
  b.lineTo(L.W + PAD, L.H + 40).close();
  return b.build();
}

function vgrad(y0: number, y1: number, cs: string[], pos: number[]) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y1 }, cs.map(col), pos, TileMode.Clamp));
  return p;
}

/** The six layers, back to front, and the logo's bloom (aligned with the frame layer). */
export function bakeDiorama(L: DioramaLayout, scale: number): { layers: LayerArt[]; bloom: LayerArt } | null {
  const { W, win } = L;
  const span = win.bottom - win.top;
  const at = (f: number) => win.top + span * f;
  const layers: LayerArt[] = [];

  // 1. Sky with a paper moon and stars.
  const sky = surfaceFor(L, win.top - 60, scale);
  if (!sky) return null;
  {
    const c = sky.c;
    const p = vgrad(win.top, win.bottom, ['#26315A', '#5B4F86', '#B07A9C', '#F0AE88', '#F7D3A4'], [0, 0.32, 0.55, 0.78, 1]);
    c.drawRect(Skia.XYWHRect(-PAD, win.top - 60, W + PAD * 2, L.H + 100 - win.top), p);
    const moon = Skia.PathBuilder.Make().addCircle(W * 0.67, at(0.4), 30).build();
    paper(c, moon, vgrad(at(0.4) - 30, at(0.4) + 30, ['#FFF4DC', '#FBE3BC', '#F4D2A4'], [0, 0.6, 1]), L, 0.25);
    const starPaint = Skia.Paint();
    starPaint.setColor(col('#F7E7C6'));
    for (let i = 0; i < 9; i += 1) {
      const x = 46 + hash(i) * (W - 92);
      const y = win.top + 30 + hash(i + 9) * span * 0.32;
      const r = 2.2 + hash(i + 19) * 3.2;
      const s = Skia.PathBuilder.Make()
        .moveTo(x, y - r * 2)
        .quadTo(x, y, x + r * 2, y)
        .quadTo(x, y, x, y + r * 2)
        .quadTo(x, y, x - r * 2, y)
        .quadTo(x, y, x, y - r * 2)
        .close()
        .build();
      paper(c, s, starPaint, L, 0.2);
    }
  }
  layers.push(finish(sky));

  // 2. Far hills.
  const hillsTop = at(0.5);
  const hills = surfaceFor(L, hillsTop - 20, scale);
  if (!hills) return null;
  paper(hills.c, ridge(L, (x) => at(0.6) + 16 * Math.sin(x / 52 + 1) + 9 * Math.sin(x / 23 + 0.4) - 22 * Math.exp(-((x - W * 0.3) ** 2) / 3000)), vgrad(at(0.55), at(0.8), ['#9A8BC0', '#7F74A8', '#6A6394'], [0, 0.5, 1]), L);
  layers.push(finish(hills));

  // 3. A row of pines on a low ridge.
  const trees = surfaceFor(L, at(0.52), scale);
  if (!trees) return null;
  {
    const base = (x: number) => at(0.71) + 9 * Math.sin(x / 41 + 2.2);
    const b = Skia.PathBuilder.Make().moveTo(-PAD, L.H + 40);
    for (let x = -PAD; x <= W + PAD; x += 4) b.lineTo(x, base(x));
    b.lineTo(W + PAD, L.H + 40).close();
    for (let i = 0; i * 23 - PAD < W + PAD; i += 1) {
      const x = -PAD + i * 23 + hash(i + 3) * 8;
      const h = 34 + hash(i + 31) * 52 * (x > W * 0.38 && x < W * 0.62 ? 0.55 : 1);
      const y = base(x) + 4;
      const w = 9 + h * 0.2;
      // Three tiers of a paper pine.
      for (let k = 0; k < 3; k += 1) {
        const ty = y - h + k * h * 0.28;
        const tw = w * (0.55 + k * 0.28);
        b.moveTo(x, ty).lineTo(x + tw, ty + h * 0.42).lineTo(x - tw, ty + h * 0.42).close();
      }
      b.addRect(Skia.XYWHRect(x - 1.5, y - 6, 3, 8));
    }
    // Overlapping tiers: no lit edge, or every tier's outline would show.
    paper(trees.c, b.build(), vgrad(at(0.55), at(0.8), ['#3F6470', '#34545F', '#2B4652'], [0, 0.5, 1]), L, 0.32, false);
  }
  layers.push(finish(trees));

  // 4. Meadow with a winding river.
  const meadowTop = at(0.735);
  const meadow = surfaceFor(L, meadowTop - 20, scale);
  if (!meadow) return null;
  {
    const c = meadow.c;
    const edge = (x: number) => meadowTop + 7 * Math.sin(x / 60 + 2) + 4 * Math.sin(x / 19);
    paper(c, ridge(L, edge), vgrad(meadowTop, win.bottom + 60, ['#7E9C68', '#678A57', '#4F7347'], [0, 0.5, 1]), L);
    const vx = W * 0.44;
    const vy = edge(vx) + 2;
    const yb = L.H + 40;
    const river = Skia.PathBuilder.Make()
      .moveTo(vx - 3, vy)
      .cubicTo(vx - 26, vy + 20, W * 0.7, vy + 40, W * 0.5, vy + 70)
      .cubicTo(W * 0.3, vy + 100, W * 0.18, vy + 120, W * 0.08, yb)
      .lineTo(W * 0.62, yb)
      .cubicTo(W * 0.5, vy + 130, W * 0.6, vy + 100, W * 0.74, vy + 66)
      .cubicTo(W * 0.86, vy + 38, vx + 26, vy + 18, vx + 3, vy)
      .close()
      .build();
    paper(c, river, vgrad(vy, win.bottom, ['#B4D5D9', '#86B7C6', '#5F94AB'], [0, 0.4, 1]), L, 0.22);
    const ripple = Skia.Paint();
    ripple.setStyle(PaintStyle.Stroke);
    ripple.setStrokeWidth(1.3);
    ripple.setColor(col('rgba(255,250,238,0.55)'));
    c.save();
    c.clipPath(river, ClipOp.Intersect, true);
    for (let i = 0; i < 7; i += 1) {
      const y = vy + 18 + i * 16;
      const x = W * (0.48 + 0.12 * Math.sin(i * 1.7) - i * 0.03);
      c.drawLine(x - 14, y, x + 14, y + 1, ripple);
    }
    c.restore();
  }
  layers.push(finish(meadow));

  // 5. Grass edge with blades.
  const grassTop = at(0.925);
  const grass = surfaceFor(L, grassTop - 40, scale);
  if (!grass) return null;
  {
    const b = Skia.PathBuilder.Make().moveTo(-PAD, L.H + 40);
    let x = -PAD;
    let i = 0;
    while (x <= W + PAD) {
      const g = grassTop + 10 * Math.sin(x / 70 + 0.6);
      const h = 8 + hash(i + 7) * 20;
      const lean = (hash(i + 77) - 0.5) * 7;
      b.lineTo(x, g).lineTo(x + 4.5 + lean, g - h).lineTo(x + 9, g);
      x += 9;
      i += 1;
    }
    b.lineTo(W + PAD, L.H + 40).close();
    paper(grass.c, b.build(), vgrad(grassTop - 20, L.H, ['#3E6545', '#2F5137', '#22402B'], [0, 0.3, 1]), L, 0.38);
  }
  layers.push(finish(grass));

  // 6. The front frame: cream paper with the arched window, the logo door and the wordmark cut out.
  const frame = surfaceFor(L, 0, scale);
  if (!frame) return null;
  const family = Platform.select({ ios: 'Georgia', default: 'serif' });
  const word = matchFont({ fontFamily: family, fontSize: 46, fontWeight: '700' });
  const tag = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: 13, fontWeight: '600' });
  const width = (f: typeof word, s: string) => f.getGlyphWidths(f.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
  const E = L.emblem;
  const door = Skia.PathBuilder.Make()
    .moveTo(E.x, E.y + E.h)
    .lineTo(E.x, E.y + E.w / 2)
    .arcToOval(Skia.XYWHRect(E.x, E.y, E.w, E.w), 180, 180, false)
    .lineTo(E.x + E.w, E.y + E.h)
    .close()
    .build();
  const windowPath = Skia.PathBuilder.Make()
    .moveTo(win.x0, win.bottom)
    .lineTo(win.x0, win.top + win.r)
    .arcToOval(Skia.XYWHRect(win.x0, win.top, win.r * 2, win.r * 2), 180, 180, false)
    .lineTo(win.x1, win.bottom)
    .close()
    .build();
  {
    const c = frame.c;
    const cream = vgrad(0, L.H, ['#F6EEDF', '#F1E6D3', '#E8DAC2'], [0, 0.6, 1]);
    c.drawRect(Skia.XYWHRect(-PAD, 0, W + PAD * 2, L.H + 40), cream);
    const g = Skia.Paint();
    g.setShader(Skia.Shader.MakeFractalNoise(0.85, 0.85, 3, 23, 0, 0));
    g.setAlphaf(0.09);
    g.setBlendMode(BlendMode.Multiply);
    c.drawRect(Skia.XYWHRect(-PAD, 0, W + PAD * 2, L.H + 40), g);
    const cut = Skia.Paint();
    cut.setBlendMode(BlendMode.Clear);
    c.drawPath(windowPath, cut);
    c.drawPath(door, cut);
    c.drawText('Fable', W / 2 - width(word, 'Fable') / 2, L.wordY, cut, word);
    // The frame's own shadow falling inwards on the scene, and a lit lip on the cut edge.
    c.save();
    c.clipPath(windowPath, ClipOp.Intersect, true);
    const inner = Skia.Paint();
    inner.setStyle(PaintStyle.Stroke);
    inner.setStrokeWidth(14);
    inner.setColor(col('rgba(30,18,24,0.42)'));
    inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
    c.save();
    c.translate(0, 4);
    c.drawPath(windowPath, inner);
    c.restore();
    c.restore();
    const lip = Skia.Paint();
    lip.setStyle(PaintStyle.Stroke);
    lip.setStrokeWidth(1.2);
    lip.setColor(col('rgba(255,252,244,0.85)'));
    c.drawPath(windowPath, lip);
    c.drawPath(door, lip);
    const ink = Skia.Paint();
    ink.setColor(col('rgba(120,96,70,0.8)'));
    const tagline = 'STORIES FOR SMALL HOURS';
    const tw = width(tag, tagline) + tagline.length * 2.2;
    let tx = W / 2 - tw / 2;
    for (const ch of tagline) {
      c.drawText(ch, tx, L.tagY, ink, tag);
      tx += width(tag, ch) + 2.2;
    }
  }
  layers.push(finish(frame));

  // The light through the cut-outs, blurred, to be added over the frame.
  const glow = surfaceFor(L, 0, scale);
  if (!glow) return null;
  {
    const c = glow.c;
    const warm = Skia.Paint();
    warm.setColor(col('rgba(255,170,90,0.85)'));
    warm.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
    c.drawPath(door, warm);
    c.drawText('Fable', W / 2 - width(word, 'Fable') / 2, L.wordY, warm, word);
  }
  return { layers, bloom: finish(glow) };
}

/** Blank paper: the backing board the layers rise onto. */
export function bakeBacking(L: DioramaLayout, scale: number): SkImage | null {
  const s = Skia.Surface.Make(Math.ceil(L.W * scale), Math.ceil(L.H * scale));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  c.drawRect(Skia.XYWHRect(0, 0, L.W, L.H), vgrad(0, L.H, ['#F3EBDD', '#EDE3D2', '#E4D7C2'], [0, 0.6, 1]));
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 29, 0, 0));
  g.setAlphaf(0.1);
  g.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, L.W, L.H), g);
  const v = Skia.Paint();
  v.setShader(Skia.Shader.MakeRadialGradient({ x: L.W / 2, y: L.H * 0.45 }, L.H * 0.7, [col('rgba(80,50,20,0)'), col('rgba(80,50,20,0.12)')], [0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, L.W, L.H), v);
  s.flush();
  return s.makeImageSnapshot();
}

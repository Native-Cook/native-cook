import { BlendMode, BlurStyle, ClipOp, matchFont, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

/*
 Viewmaster Reel — layout, the reel's slides, and the static art drawn once into images: the
 room (slate gradient, grain, vignette), the red bakelite viewer bezel around the window, and the
 cardboard reel itself (cream card, printed ring, seven film chips, rim notches, centre hole),
 which the screen then only turns.
 */

export type Slide = { photo: number; title: string; place: string; crop: [number, number, number, number] };

/** Seven slides from six photos (800 × 600 px); the last is a closer crop of the first valley. */
export const SLIDES: Slide[] = [
  { photo: 0, title: 'Vestrahorn at dusk', place: 'Stokksnes · 64.25° N 14.97° W', crop: [0, 0, 800, 600] },
  { photo: 1, title: 'Seljalandsfoss', place: 'South Coast · 63.62° N 19.99° W', crop: [0, 0, 800, 600] },
  { photo: 2, title: 'Kirkjufell, first light', place: 'Snæfellsnes · 64.94° N 23.31° W', crop: [0, 0, 800, 600] },
  { photo: 3, title: 'Braided rivers', place: 'Fjallabak · 63.98° N 19.06° W', crop: [0, 0, 800, 600] },
  { photo: 4, title: 'Route 1 after midnight', place: 'Eastfjords · 65.26° N 14.39° W', crop: [0, 0, 800, 600] },
  { photo: 5, title: 'Behind the falls', place: 'Gljúfrabúi trail · 63.62° N 19.98° W', crop: [0, 0, 800, 600] },
  { photo: 3, title: 'The golden ridge', place: 'Landmannalaugar · 63.99° N 19.06° W', crop: [240, 40, 480, 360] },
];

export type ReelLayout = {
  W: number;
  H: number;
  /** The viewer's bezel and the photo window inside it. */
  bezel: { x: number; y: number; w: number; h: number };
  win: { x: number; y: number; w: number; h: number };
  captionY: number;
  disc: { cx: number; cy: number; r: number };
  lever: { px: number; py: number; len: number };
};

export const HEADER_H = 92;
export const CAPTION_H = 60;
export const LEVER_REST = -110;
export const LEVER_PULLED = -176;

export function layoutReel(W: number, H: number, top: number): ReelLayout {
  const bx = 20;
  const bw = W - 40;
  const bh = Math.round(bw * 0.75);
  const by = top + 12 + HEADER_H;
  const pad = 12;
  const captionY = by + bh + 12;
  const r = Math.min(170, W * 0.43);
  const cy = Math.max(captionY + CAPTION_H + 18 + r, H - r - 10);
  return {
    W,
    H,
    bezel: { x: bx, y: by, w: bw, h: bh },
    win: { x: bx + pad, y: by + pad, w: bw - pad * 2, h: bh - pad * 2 },
    captionY,
    disc: { cx: W / 2, cy, r },
    lever: { px: W - 18, py: cy - r * 0.26, len: Math.min(86, r * 0.52) },
  };
}

const col = (c: string) => Skia.Color(c);

function grain(c: SkCanvas, w: number, h: number, alpha: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.85, 0.85, 2, 3, 0, 0));
  p.setAlphaf(alpha);
  p.setBlendMode(BlendMode.Overlay);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), p);
}

/** Room background, the viewer bezel, and the reel's shadow, at `scale` px per pt. */
export function bakeRoom(L: ReelLayout, scale: number): SkImage | null {
  const s = Skia.Surface.Make(Math.ceil(L.W * scale), Math.ceil(L.H * scale));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  const { W, H, bezel: B, win: Wn, disc: D } = L;
  const bg = Skia.Paint();
  bg.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: W * 0.3, y: H }, [col('#1A2731'), col('#121C23'), col('#0D1418')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), bg);
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: W / 2, y: B.y + B.h * 0.5 }, W * 0.9, [col('rgba(120,150,170,0.16)'), col('rgba(80,110,130,0.05)'), col('rgba(0,0,0,0)')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), glow);

  // Reel shadow on the table: soft ambient + tight contact.
  const amb = Skia.Paint();
  amb.setColor(col('rgba(0,0,0,0.5)'));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawCircle(D.cx + 2, D.cy + 14, D.r, amb);
  const con = Skia.Paint();
  con.setColor(col('rgba(0,0,0,0.55)'));
  con.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  c.drawCircle(D.cx, D.cy + 2, D.r, con);

  // Viewer bezel: red bakelite, two shadows, a lit top edge, a black recess around the window.
  const rr = (x: number, y: number, w: number, h: number, r: number) => Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
  const bAmb = Skia.Paint();
  bAmb.setColor(col('rgba(0,0,0,0.5)'));
  bAmb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 20, true));
  c.drawRRect(rr(B.x, B.y + 16, B.w, B.h, 30), bAmb);
  const bCon = Skia.Paint();
  bCon.setColor(col('rgba(0,0,0,0.6)'));
  bCon.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawRRect(rr(B.x, B.y + 2, B.w, B.h, 30), bCon);
  const red = Skia.Paint();
  red.setShader(Skia.Shader.MakeLinearGradient({ x: B.x, y: B.y }, { x: B.x + B.w * 0.4, y: B.y + B.h }, [col('#D24A3C'), col('#A9322A'), col('#7E2019')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(rr(B.x, B.y, B.w, B.h, 30), red);
  const sheen = Skia.Paint();
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: B.y }, { x: 0, y: B.y + B.h }, [col('rgba(255,230,220,0.28)'), col('rgba(255,230,220,0)'), col('rgba(0,0,0,0.18)')], [0, 0.35, 1], TileMode.Clamp));
  c.drawRRect(rr(B.x, B.y, B.w, B.h, 30), sheen);
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(col('rgba(255,215,200,0.35)'));
  c.drawRRect(rr(B.x + 0.5, B.y + 0.5, B.w - 1, B.h - 1, 29.5), edge);
  const recess = Skia.Paint();
  recess.setColor(col('#0B0A0A'));
  c.drawRRect(rr(Wn.x - 3, Wn.y - 3, Wn.w + 6, Wn.h + 6, 21), recess);
  const lip = Skia.Paint();
  lip.setStyle(PaintStyle.Stroke);
  lip.setStrokeWidth(1.2);
  lip.setColor(col('rgba(255,190,170,0.22)'));
  c.drawRRect(rr(Wn.x - 3.6, Wn.y - 3.6, Wn.w + 7.2, Wn.h + 7.2, 21.6), lip);
  // Two screw heads, for scale.
  const screw = Skia.Paint();
  screw.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, 4, [col('#F0E4DA'), col('#8E7F76')], [0, 1], TileMode.Clamp));
  for (const sx of [B.x + B.w / 2 - 34, B.x + B.w / 2 + 34]) {
    c.save();
    c.translate(sx, B.y + 6);
    c.drawCircle(0, 0, 2.6, screw);
    c.restore();
  }

  grain(c, W, H, 0.05);
  const vig = Skia.Paint();
  vig.setShader(Skia.Shader.MakeRadialGradient({ x: W / 2, y: H * 0.45 }, H * 0.75, [col('rgba(0,0,0,0)'), col('rgba(0,0,0,0.15)'), col('rgba(0,0,0,0.55)')], [0, 0.6, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), vig);
  s.flush();
  return s.makeImageSnapshot();
}

/** The reel, centred in a square of side 2r, slot 0 at 12 o'clock and the rest clockwise. */
export function bakeReel(r: number, photos: SkImage[], scale: number): SkImage | null {
  const side = Math.ceil(r * 2 * scale);
  const s = Skia.Surface.Make(side, side);
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  c.translate(r, r);
  const card = Skia.Paint();
  card.setShader(Skia.Shader.MakeRadialGradient({ x: -r * 0.3, y: -r * 0.35 }, r * 1.5, [col('#F6EEDD'), col('#ECE1CA'), col('#DCCDB1')], [0, 0.55, 1], TileMode.Clamp));
  c.drawCircle(0, 0, r, card);
  // Paper grain on the card only.
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(0, 0, r).build(), ClipOp.Intersect, true);
  const g = Skia.Paint();
  g.setShader(Skia.Shader.MakeFractalNoise(1.1, 1.1, 3, 11, 0, 0));
  g.setAlphaf(0.09);
  g.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(-r, -r, r * 2, r * 2), g);
  c.restore();
  // Printed rings.
  const ink = Skia.Paint();
  ink.setStyle(PaintStyle.Stroke);
  ink.setColor(col('rgba(122,32,30,0.85)'));
  ink.setStrokeWidth(1.4);
  c.drawCircle(0, 0, r * 0.93, ink);
  ink.setStrokeWidth(0.8);
  c.drawCircle(0, 0, r * 0.81, ink);
  c.drawCircle(0, 0, r * 0.23, ink);

  const family = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
  const ringFont = matchFont({ fontFamily: family, fontSize: Math.max(7, r * 0.06), fontWeight: '700' });
  const numFont = matchFont({ fontFamily: family, fontSize: Math.max(8, r * 0.065), fontWeight: '700' });
  const phrase = 'ICELAND · RING ROAD · JUNE 2025 · REEL 01 · ';
  const phraseW = ringFont.getGlyphWidths(ringFont.getGlyphIDs(phrase)).reduce((a, b) => a + b, 0);
  const tr = r * 0.85;
  // As many copies of the phrase as fit with a little tracking, spread evenly round the ring.
  const copies = Math.max(1, Math.floor((2 * Math.PI * tr) / (phraseW * 1.18)));
  const text = phrase.repeat(copies);
  const widths = ringFont.getGlyphWidths(ringFont.getGlyphIDs(text));
  const total = widths.reduce((a, b) => a + b, 0);
  const spread = (2 * Math.PI * tr) / total;
  const tp = Skia.Paint();
  tp.setColor(col('rgba(122,32,30,0.9)'));
  let acc = 0;
  for (let i = 0; i < text.length; i += 1) {
    const w = widths[i] * spread;
    const a = (acc + w / 2) / tr - Math.PI / 2;
    c.save();
    c.translate(Math.cos(a) * tr, Math.sin(a) * tr);
    c.rotate((a * 180) / Math.PI + 90, 0, 0);
    c.drawText(text[i], -widths[i] / 2, ringFont.getSize() * 0.36, tp, ringFont);
    c.restore();
    acc += w;
  }

  // Seven film chips, image top towards the rim, with a number on the inner side.
  const cw = r * 0.3;
  const ch = cw * 0.75;
  const rc = r * 0.56;
  const chipShadow = Skia.Paint();
  chipShadow.setColor(col('rgba(60,40,20,0.35)'));
  chipShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
  const frame = Skia.Paint();
  frame.setColor(col('#2A2421'));
  const shine = Skia.Paint();
  shine.setShader(Skia.Shader.MakeLinearGradient({ x: -cw / 2, y: -ch / 2 }, { x: cw / 2, y: ch / 2 }, [col('rgba(255,255,255,0.28)'), col('rgba(255,255,255,0)'), col('rgba(255,255,255,0.08)')], [0, 0.5, 1], TileMode.Clamp));
  const np = Skia.Paint();
  np.setColor(col('rgba(122,32,30,0.95)'));
  const imgPaint = Skia.Paint();
  for (let k = 0; k < SLIDES.length; k += 1) {
    const a = (k * 2 * Math.PI) / SLIDES.length;
    c.save();
    c.rotate((a * 180) / Math.PI, 0, 0);
    c.translate(0, -rc);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-cw / 2 - 2, -ch / 2 - 1, cw + 4, ch + 4), 2, 2), chipShadow);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-cw / 2 - 2, -ch / 2 - 2, cw + 4, ch + 4), 2, 2), frame);
    const sl = SLIDES[k];
    const img = photos[sl.photo];
    const [x, y, w, h] = sl.crop;
    c.drawImageRect(img, Skia.XYWHRect(x, y, w, h), Skia.XYWHRect(-cw / 2, -ch / 2, cw, ch), imgPaint);
    c.drawRect(Skia.XYWHRect(-cw / 2, -ch / 2, cw, ch), shine);
    const label = `${k + 1}`;
    const lw = numFont.getGlyphWidths(numFont.getGlyphIDs(label)).reduce((p, q) => p + q, 0);
    c.drawText(label, -lw / 2, ch / 2 + r * 0.13, np, numFont);
    c.restore();
  }
  // Notches in the rim between the chips (cut through the card).
  const cut = Skia.Paint();
  cut.setBlendMode(BlendMode.Clear);
  for (let k = 0; k < SLIDES.length; k += 1) {
    const a = ((k + 0.5) * 2 * Math.PI) / SLIDES.length - Math.PI / 2;
    c.drawCircle(Math.cos(a) * r, Math.sin(a) * r, r * 0.035, cut);
  }
  // Centre hole with a pressed rim.
  const rimP = Skia.Paint();
  rimP.setColor(col('rgba(110,80,50,0.35)'));
  c.drawCircle(0, 0, r * 0.085, rimP);
  c.drawCircle(0, 0, r * 0.065, cut);
  s.flush();
  return s.makeImageSnapshot();
}

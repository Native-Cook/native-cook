import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';

/*
 Film Negative Card — layout and the static art (the desk and the light table), drawn once into
 an image.

 Layout (pt), from the screen size and safe area: a header block, then the strip resting on the
 desk, the light table under it, and a footer row. The strip is 35 mm film: frame height = strip
 height × 24 / 35, frame width = 1.5 × frame height, perforation pitch = frame width / 7.58
 (4.75 mm on a 36 mm frame), holes 2.8 × 2 mm. On 393 × 852 the strip is 361 × 250.
 */

export const HEADER_H = 112;
export const FOOTER_H = 56;
const SIDE = 16;
const TABLE_PAD = 18;
const STRIP_MAX_H = 250;

export type FilmLayout = {
  W: number;
  H: number;
  stripX: number;
  stripW: number;
  stripH: number;
  /** Strip top on the desk and on the light. */
  restY: number;
  onY: number;
  table: { x: number; y: number; w: number; h: number };
  /** The lit panel inside the table's bezel (screen space). */
  panel: { x: number; y: number; w: number; h: number };
  frame: { x: number; y: number; w: number; h: number };
  gap: number;
  perf: { pitch: number; w: number; h: number; phase: number; top: number; bottom: number };
  footerY: number;
};

export function layoutFilm(W: number, H: number, top: number, bottom: number): FilmLayout {
  const a = top + 12 + HEADER_H;
  const footerY = H - bottom - 16 - FOOTER_H;
  const b = footerY - 16;
  const stripW = W - SIDE * 2;
  const stripH = Math.min(STRIP_MAX_H, stripW * 0.7, (b - a - TABLE_PAD * 2 - 22) / 2);
  const tableH = stripH + TABLE_PAD * 2;
  const tableY = b - tableH;
  const restY = a + Math.max(0, (tableY - 14 - a - stripH) * 0.5);
  const fh = (stripH * 24) / 35;
  const fw = fh * 1.5;
  const mm = fw / 36;
  const gap = 2 * mm;
  const fx = (stripW - fw) / 2;
  const fy = (stripH - fh) / 2;
  const pitch = 4.75 * mm;
  const holeH = 2 * mm;
  const margin = fy;
  return {
    W,
    H,
    stripX: SIDE,
    stripW,
    stripH,
    restY,
    onY: tableY + TABLE_PAD,
    table: { x: 10, y: tableY, w: W - 20, h: tableH },
    panel: { x: 20, y: tableY + 10, w: W - 40, h: tableH - 20 },
    frame: { x: fx, y: fy, w: fw, h: fh },
    gap,
    perf: {
      pitch,
      w: 2.8 * mm,
      h: holeH,
      phase: fx + pitch * 0.5,
      top: margin * 0.36 + holeH * 0.5 + 2,
      bottom: stripH - (margin * 0.36 + holeH * 0.5 + 2),
    },
    footerY,
  };
}

function color(c: string) {
  return Skia.Color(c);
}

function fillRRect(c: SkCanvas, x: number, y: number, w: number, h: number, r: number, paint: ReturnType<typeof Skia.Paint>) {
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), paint);
}

/** The desk (warm near-black, safelight glow, grain, vignette) and the light table, at `scale` px per pt. */
export function bakeDesk(L: FilmLayout, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(L.W * scale), Math.ceil(L.H * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const { W, H, table: T, panel: P } = L;

  // Desk: three-stop vertical gradient, a dim red-amber safelight glow at the top right.
  const desk = Skia.Paint();
  desk.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: H }, [color('#1C1512'), color('#16110F'), color('#110D0C')], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), desk);
  const safelight = Skia.Paint();
  safelight.setShader(
    Skia.Shader.MakeRadialGradient({ x: W * 0.92, y: -20 }, W * 0.95, [color('rgba(196,74,42,0.22)'), color('rgba(150,52,32,0.08)'), color('rgba(0,0,0,0)')], [0, 0.45, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), safelight);

  // Light spilling out of the table onto the desk.
  const spill = Skia.Paint();
  spill.setColor(color('rgba(255,214,150,0.24)'));
  spill.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 30, true));
  fillRRect(c, T.x - 6, T.y - 4, T.w + 12, T.h + 14, 22, spill);

  // The table: two shadows, an aluminium bezel with a lit top edge.
  const amb = Skia.Paint();
  amb.setColor(color('rgba(0,0,0,0.45)'));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
  fillRRect(c, T.x, T.y + 14, T.w, T.h, 18, amb);
  const contact = Skia.Paint();
  contact.setColor(color('rgba(0,0,0,0.6)'));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  fillRRect(c, T.x, T.y + 1.5, T.w, T.h, 18, contact);
  const bezel = Skia.Paint();
  bezel.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: T.y }, { x: 0, y: T.y + T.h }, [color('#4A4541'), color('#2B2725'), color('#38332F')], [0, 0.5, 1], TileMode.Clamp),
  );
  fillRRect(c, T.x, T.y, T.w, T.h, 18, bezel);
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1);
  rim.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: T.y }, { x: 0, y: T.y + T.h }, [color('rgba(255,244,228,0.30)'), color('rgba(255,244,228,0.04)'), color('rgba(0,0,0,0.35)')], [0, 0.3, 1], TileMode.Clamp),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(T.x + 0.5, T.y + 0.5, T.w - 1, T.h - 1), 17.5, 17.5), rim);

  // The panel: opal glass lit from below, warm in the middle, a touch cooler at the edges.
  const glass = Skia.Paint();
  glass.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: P.x + P.w / 2, y: P.y + P.h / 2 },
      Math.max(P.w, P.h) * 0.62,
      [color('#FFFBF2'), color('#FBF1DE'), color('#EADCC2')],
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  fillRRect(c, P.x, P.y, P.w, P.h, 9, glass);
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(P.x, P.y, P.w, P.h), 9, 9), ClipOp.Intersect, true);
  // A faint printed grid on the glass.
  const grid = Skia.Paint();
  grid.setColor(color('rgba(150,120,80,0.13)'));
  grid.setStrokeWidth(0.6);
  for (let x = P.x + 12; x < P.x + P.w; x += 20) c.drawLine(x, P.y, x, P.y + P.h, grid);
  for (let y = P.y + 10; y < P.y + P.h; y += 20) c.drawLine(P.x, y, P.x + P.w, y, grid);
  // Inner shade along the bezel.
  const inner = Skia.Paint();
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(6);
  inner.setColor(color('rgba(120,90,50,0.16)'));
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(P.x, P.y, P.w, P.h), 9, 9), inner);
  c.restore();

  // Grain over everything, then a vignette.
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 7, 0, 0));
  grain.setAlphaf(0.05);
  grain.setBlendMode(BlendMode.Overlay);
  c.drawRect(Skia.XYWHRect(0, 0, W, H), grain);
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient({ x: W / 2, y: H * 0.46 }, H * 0.72, [color('rgba(0,0,0,0)'), color('rgba(0,0,0,0.12)'), color('rgba(0,0,0,0.5)')], [0, 0.6, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), vignette);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image;
}

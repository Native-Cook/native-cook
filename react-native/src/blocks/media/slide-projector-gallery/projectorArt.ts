import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage, type SkPath } from '@shopify/react-native-skia';

/*
 Slide Projector Gallery — layout, the slides, and the art drawn once: the atlas of slides at
 three focus levels, and the projector (a walnut-and-cream body, the lens barrel, the round
 carousel tray's walls and hub) as an image with transparency over the wall. The tray's slide
 mounts turn, so they are rebuilt as one path per frame (`trayMounts`).
 */

export type Slide = { title: string; meta: string };

export const SLIDES: Slide[] = [
  { title: 'Aunt Ivy at Crescent Point', meta: 'July 1987 · Kodachrome-style transparency' },
  { title: 'Under the striped umbrellas', meta: 'Pine Cove · August 1987' },
  { title: 'Lake day, watermelon for lunch', meta: 'Lake Wren · July 1987' },
  { title: "The lifeguard's chair", meta: 'Long Strand · August 1987' },
  { title: "Dad's blue coupe", meta: 'Driveway, Elm Street · June 1987' },
];

export const TILE_W = 600;
export const TILE_H = 400;

export type ProjLayout = {
  W: number;
  H: number;
  proj: { cx: number; top: number; w: number; h: number };
  captionY: number;
  lens: { x: number; y: number };
  tray: { cx: number; cy: number; rx: number; ry: number; side: number };
  bodyTop: number;
};

export const HEADER_H = 92;
export const KEY_TOP = 1.045;
export const KEY_BOTTOM = 0.955;

export function layoutProjector(W: number, H: number, top: number, bottom: number): ProjLayout {
  const projTop = top + 12 + HEADER_H;
  const pw = W - 40;
  const ph = pw / 1.5;
  const rx = Math.min(W * 0.42, 170);
  const ry = rx * 0.34;
  const cy = H - bottom - ry - 52;
  const bodyTop = cy - ry - 40;
  return {
    W,
    H,
    proj: { cx: W / 2, top: projTop, w: pw, h: ph },
    captionY: projTop + ph + 16,
    lens: { x: W / 2, y: bodyTop - 20 },
    tray: { cx: W / 2, cy, rx, ry, side: 18 },
    bodyTop,
  };
}

const col = (c: string) => Skia.Color(c);

/** Five slides × (sharp, soft, softer) in a 3 × 5 grid of TILE_W × TILE_H tiles. */
export function bakeAtlas(photos: SkImage[]): SkImage | null {
  const s = Skia.Surface.Make(TILE_W * 3, TILE_H * photos.length);
  if (!s) return null;
  const c = s.getCanvas();
  const sigmas = [0, 4, 12];
  photos.forEach((img, row) => {
    sigmas.forEach((sig, colIdx) => {
      const dst = Skia.XYWHRect(colIdx * TILE_W, row * TILE_H, TILE_W, TILE_H);
      const p = Skia.Paint();
      if (sig > 0) p.setImageFilter(Skia.ImageFilter.MakeBlur(sig, sig, TileMode.Clamp, null));
      c.save();
      c.clipRect(dst, ClipOp.Intersect, false);
      c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), dst, p);
      c.restore();
    });
  });
  s.flush();
  return s.makeImageSnapshot();
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function oval(c: SkCanvas, cx: number, cy: number, rx: number, ry: number, p: ReturnType<typeof Skia.Paint>) {
  c.drawOval(Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2), p);
}

/** The projector and the tray's static parts, transparent elsewhere. */
export function bakeProjector(L: ProjLayout, scale: number): SkImage | null {
  const s = Skia.Surface.Make(Math.ceil(L.W * scale), Math.ceil(L.H * scale));
  if (!s) return null;
  const c = s.getCanvas();
  c.scale(scale, scale);
  const { W, H, tray: T, lens, bodyTop } = L;

  // Body: two shadows on the wall-side, then a walnut shell with a cream top deck.
  const amb = Skia.Paint();
  amb.setColor(col('rgba(0,0,0,0.55)'));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 22, true));
  c.drawRRect(rr(10, bodyTop - 10, W - 20, H - bodyTop + 40, 34), amb);
  const shell = Skia.Paint();
  shell.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: bodyTop }, { x: 0, y: H }, [col('#5A3E2C'), col('#3E2A1E'), col('#2A1C14')], [0, 0.4, 1], TileMode.Clamp));
  c.drawRRect(rr(14, bodyTop, W - 28, H - bodyTop + 30, 30), shell);
  const deck = Skia.Paint();
  deck.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: bodyTop + 6 }, { x: 0, y: T.cy + T.ry + 30 }, [col('#D9CBB2'), col('#C4B394'), col('#A8977A')], [0, 0.55, 1], TileMode.Clamp));
  c.drawRRect(rr(22, bodyTop + 6, W - 44, T.cy + T.ry + 24 - bodyTop, 24), deck);
  const deckEdge = Skia.Paint();
  deckEdge.setStyle(PaintStyle.Stroke);
  deckEdge.setStrokeWidth(1);
  deckEdge.setColor(col('rgba(255,250,236,0.55)'));
  c.drawRRect(rr(22.5, bodyTop + 6.5, W - 45, T.cy + T.ry + 23 - bodyTop, 23.5), deckEdge);
  // Vent slots either side of the tray.
  const vent = Skia.Paint();
  vent.setColor(col('rgba(60,44,30,0.55)'));
  for (let i = 0; i < 6; i += 1) {
    const y = bodyTop + 22 + i * 7;
    c.drawRRect(rr(34, y, 26, 2.6, 1.3), vent);
    c.drawRRect(rr(W - 60, y, 26, 2.6, 1.3), vent);
  }
  // A chrome trim along the shell.
  const trim = Skia.Paint();
  trim.setShader(Skia.Shader.MakeLinearGradient({ x: 14, y: 0 }, { x: W - 14, y: 0 }, [col('#8F8A84'), col('#F2EEE8'), col('#8F8A84')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(18, T.cy + T.ry + 36, W - 36, 2), trim);

  // Lens barrel: a short black cylinder with a chrome ring, pointing at the wall.
  const barrel = Skia.Paint();
  barrel.setShader(Skia.Shader.MakeLinearGradient({ x: lens.x - 24, y: 0 }, { x: lens.x + 24, y: 0 }, [col('#1A1716'), col('#4A4542'), col('#121010')], [0, 0.4, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(lens.x - 24, lens.y, 48, bodyTop + 12 - lens.y), barrel);
  oval(c, lens.x, bodyTop + 12, 24, 8, barrel);
  const ring = Skia.Paint();
  ring.setShader(Skia.Shader.MakeLinearGradient({ x: lens.x - 26, y: 0 }, { x: lens.x + 26, y: 0 }, [col('#7F7A75'), col('#F4F0EA'), col('#6E6964')], [0, 0.45, 1], TileMode.Clamp));
  oval(c, lens.x, lens.y, 26, 9, ring);
  const glass = Skia.Paint();
  glass.setShader(Skia.Shader.MakeRadialGradient({ x: lens.x - 5, y: lens.y - 2 }, 22, [col('#6E7C86'), col('#1D2328'), col('#0B0D0F')], [0, 0.5, 1], TileMode.Clamp));
  oval(c, lens.x, lens.y, 20, 6.5, glass);

  // Tray: drop shadow, the outer wall (a cylinder band), the top rim and the dark slot well.
  const trayShadow = Skia.Paint();
  trayShadow.setColor(col('rgba(30,20,10,0.45)'));
  trayShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  oval(c, T.cx, T.cy + T.side + 6, T.rx + 4, T.ry + 4, trayShadow);
  const wall = Skia.Paint();
  wall.setShader(Skia.Shader.MakeLinearGradient({ x: T.cx - T.rx, y: 0 }, { x: T.cx + T.rx, y: 0 }, [col('#1E1B1A'), col('#4B4644'), col('#2A2625'), col('#141211')], [0, 0.35, 0.7, 1], TileMode.Clamp));
  const band = Skia.PathBuilder.Make()
    .moveTo(T.cx - T.rx, T.cy)
    .lineTo(T.cx - T.rx, T.cy + T.side)
    .arcToOval(Skia.XYWHRect(T.cx - T.rx, T.cy + T.side - T.ry, T.rx * 2, T.ry * 2), 180, -180, false)
    .lineTo(T.cx + T.rx, T.cy)
    .close()
    .build();
  c.drawPath(band, wall);
  const top = Skia.Paint();
  top.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: T.cy - T.ry }, { x: 0, y: T.cy + T.ry }, [col('#3A3532'), col('#2A2624'), col('#3C3734')], [0, 0.5, 1], TileMode.Clamp));
  oval(c, T.cx, T.cy, T.rx, T.ry, top);
  const well = Skia.Paint();
  well.setColor(col('#141110'));
  oval(c, T.cx, T.cy, T.rx * 0.92, T.ry * 0.92, well);
  const rim = Skia.Paint();
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.2);
  rim.setColor(col('rgba(255,240,220,0.25)'));
  oval(c, T.cx, T.cy, T.rx - 0.6, T.ry - 0.6, rim);

  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 5, 0, 0));
  grain.setAlphaf(0.06);
  grain.setBlendMode(BlendMode.Overlay);
  c.save();
  c.clipRRect(rr(14, bodyTop, W - 28, H - bodyTop + 30, 30), ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, bodyTop, W, H - bodyTop), grain);
  c.restore();
  s.flush();
  return s.makeImageSnapshot();
}

/** The hub, drawn over the mounts. */
export function hubPath(L: ProjLayout): SkPath {
  const T = L.tray;
  return Skia.PathBuilder.Make()
    .addOval(Skia.XYWHRect(T.cx - T.rx * 0.42, T.cy - T.ry * 0.42, T.rx * 0.84, T.ry * 0.84))
    .build();
}

/**
 * The cardboard slide mounts standing in the tray at `pos` (slots turned), as two paths: the
 * near half (lit) and the far half (in shade). The slot at the gate (far side, 12 o'clock) is
 * empty: its slide is in the projector.
 */
export function trayMounts(T: ProjLayout['tray'], pos: number, slots: number): SkPath[] {
  'worklet';
  const near = Skia.PathBuilder.Make();
  const far = Skia.PathBuilder.Make();
  const step = (2 * Math.PI) / slots;
  for (let k = 0; k < slots; k += 1) {
    const a = -Math.PI / 2 + (k - pos) * step;
    const d = Math.abs(Math.atan2(Math.sin(a + Math.PI / 2), Math.cos(a + Math.PI / 2)));
    if (d < step * 0.5) continue;
    const ca = Math.cos(a);
    const sa = Math.sin(a);
    const r0 = 0.5;
    const r1 = 0.88;
    const x0 = T.cx + ca * T.rx * r0;
    const y0 = T.cy + sa * T.ry * r0;
    const x1 = T.cx + ca * T.rx * r1;
    const y1 = T.cy + sa * T.ry * r1;
    const up = 3 + 4 * (sa + 1) * 0.5;
    // Half the card's thickness, across the radius on screen.
    const nx = -sa * T.ry;
    const ny = ca * T.rx;
    const nl = Math.max(1e-3, Math.hypot(nx, ny));
    const tx = (nx / nl) * 0.9;
    const ty = (ny / nl) * 0.9;
    const b = sa > 0 ? near : far;
    b.moveTo(x0 - tx, y0 - ty - up)
      .lineTo(x1 - tx, y1 - ty - up)
      .lineTo(x1 + tx, y1 + ty - up)
      .lineTo(x1 + tx, y1 + ty)
      .lineTo(x0 + tx, y0 + ty)
      .close();
  }
  return [far.build(), near.build()];
}

/*
 Slot Reel List — the art drawn once at layout time:
 - the reel strip: the three lists (mains, sides, drinks) printed on cream paper, one column per
   reel, one row per dish, at the screen's pixel ratio; the shader wraps it round the cylinders;
 - the back: the dark diner wall, the cherry-enamel cabinet with its chrome rim, the marquee, the
   reel labels, the lever's hub — one screen-sized image;
 - the front: the window's inner shadows, the glass streak and the brass payline arrows.
*/
import { BlurStyle, ClipOp, PaintStyle, Skia, TileMode, matchFont, type SkCanvas, type SkFont, type SkImage, type SkPaint } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { ROWS } from './slotReelMotion';

export const MENU: { name: string; meta: string }[][] = [
  [
    { name: 'Miso\nSalmon', meta: '25 MIN' },
    { name: 'Lemon\nChicken', meta: '40 MIN' },
    { name: 'Mushroom\nRisotto', meta: '35 MIN' },
    { name: 'Beef\nTacos', meta: '20 MIN' },
    { name: 'Gnocchi\nPesto', meta: '15 MIN' },
    { name: 'Green\nCurry', meta: '30 MIN' },
    { name: 'Shakshuka', meta: '25 MIN' },
    { name: 'Crispy\nTofu', meta: '20 MIN' },
  ],
  [
    { name: 'Charred\nGreens', meta: '90 KCAL' },
    { name: 'Garlic\nRice', meta: '210 KCAL' },
    { name: 'Smashed\nPotatoes', meta: '240 KCAL' },
    { name: 'Corn\nElote', meta: '180 KCAL' },
    { name: 'Kimchi\nSlaw', meta: '70 KCAL' },
    { name: 'Herb\nCouscous', meta: '190 KCAL' },
    { name: 'Roast\nCarrots', meta: '110 KCAL' },
    { name: 'Miso\nSoup', meta: '60 KCAL' },
  ],
  [
    { name: 'Yuzu\nSpritz', meta: 'ZERO PROOF' },
    { name: 'Iced\nHojicha', meta: 'TEA' },
    { name: 'Hibiscus\nCooler', meta: 'ZERO PROOF' },
    { name: 'Ginger\nBeer', meta: 'FIZZ' },
    { name: 'Mint\nLemonade', meta: 'FIZZ' },
    { name: 'Cold\nBrew', meta: 'COFFEE' },
    { name: 'Sparkling\nPlum', meta: 'FIZZ' },
    { name: 'Chai\nLatte', meta: 'TEA' },
  ],
];
/** Minutes and kcal of each main and side, for the result card. */
export const MINUTES = [25, 40, 35, 20, 15, 30, 25, 20];
export const KCAL = [[520, 610, 560, 640, 590, 540, 430, 470], [90, 210, 240, 180, 70, 190, 110, 60], [80, 20, 60, 120, 110, 10, 90, 150]];

export const INK = {
  cream: '#F5EDE0',
  creamSoft: 'rgba(245,237,224,0.62)',
  creamFaint: 'rgba(245,237,224,0.4)',
  gold: '#F0B54A',
  goldDeep: '#C98A2A',
  wall: '#1B120E',
  card: '#2A1B16',
};

export type Layout = {
  W: number;
  H: number;
  machine: { x: number; y: number; w: number; h: number };
  win: { x: number; y: number; w: number; h: number };
  colW: number;
  gap: number;
  rowH: number;
  radius: number;
  pivot: { x: number; y: number };
  arm: number;
  cardY: number;
  /** Text scale for the strip (1 at the 393 × 852 design). */
  k: number;
};

/** Each row turns this much of the cylinder (28°). */
export const ROW_ANGLE = (28 * Math.PI) / 180;

export function makeLayout(W: number, H: number, top: number, bottom: number): Layout {
  const headerH = 112;
  const cardH = 214;
  const avail = H - top - bottom - headerH - cardH - 36;
  const winH = Math.max(170, Math.min(290, avail - 96));
  const mx = 14;
  const mw = W - mx - 50;
  const machine = { x: mx, y: top + headerH, w: mw, h: winH + 96 };
  const win = { x: mx + 18, y: machine.y + 70, w: mw - 36, h: winH };
  const gap = 5;
  const colW = (win.w - gap * 2) / 3;
  // The window shows ±72° of the cylinder.
  const radius = winH / 2 / Math.sin((72 * Math.PI) / 180);
  const rowH = radius * ROW_ANGLE;
  return {
    W,
    H,
    machine,
    win,
    colW,
    gap,
    rowH,
    radius,
    pivot: { x: W - 30, y: win.y + win.h * 0.56 },
    arm: winH * 0.42,
    cardY: machine.y + machine.h + 16,
    k: Math.min(1, rowH / 72),
  };
}

function paint(color: string, alpha = 1): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function blur(p: SkPaint, sigma: number) {
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function stroke(p: SkPaint, w: number) {
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  return p;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function hash(n: number) {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

function snapshot(w: number, h: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(w), Math.ceil(h)) ?? Skia.Surface.Make(Math.ceil(w), Math.ceil(h));
  if (!surface) return null;
  draw(surface.getCanvas());
  surface.flush();
  const img = surface.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

function font(size: number, weight: 'bold' | '600' | '800' | 'normal', mono = false): SkFont {
  const family = mono ? Platform.select({ ios: 'Menlo', default: 'monospace' }) : Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
  return matchFont({ fontFamily: family, fontSize: size, fontWeight: weight });
}

function textWidth(f: SkFont, s: string, track = 0) {
  if (!s) return 0;
  const ws = f.getGlyphWidths(f.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w + track * Math.max(0, s.length - 1);
}

function centred(c: SkCanvas, s: string, cx: number, y: number, f: SkFont, p: SkPaint, track = 0) {
  if (track === 0) {
    c.drawText(s, cx - textWidth(f, s) / 2, y, p, f);
    return;
  }
  let x = cx - textWidth(f, s, track) / 2;
  for (const ch of s) {
    c.drawText(ch, x, y, p, f);
    x += textWidth(f, ch) + track;
  }
}

/** The three printed lists, side by side: colW × (ROWS · rowH) each, at px pixels per point. */
export function bakeStrip(L: Layout, px: number): SkImage | null {
  const { colW, rowH, k } = L;
  const name = font(14.5 * k, 'bold');
  const meta = font(9.5 * k, '600');
  return snapshot(colW * 3 * px, ROWS * rowH * px, (c) => {
    c.scale(px, px);
    for (let col = 0; col < 3; col++) {
      const x = col * colW;
      for (let r = 0; r < ROWS; r++) {
        const y = r * rowH;
        // Paper: cream, a touch warmer in alternate rows, with fibre grain.
        c.drawRect(Skia.XYWHRect(x, y, colW, rowH), linear(0, y, 0, y + rowH, r % 2 ? ['#F7EFE2', '#F1E6D4', '#EADCC6'] : ['#FAF4E9', '#F4EBDC', '#ECE0CD'], [0, 0.6, 1]));
        for (let g = 0; g < 70; g++) {
          c.drawRect(Skia.XYWHRect(x + hash(g + r * 91 + col * 7) * colW, y + hash(g * 3.3 + r * 13 + col) * rowH, 1.2, 0.5), paint('#8C6A4A', 0.06));
        }
        const item = MENU[col][r];
        const lines = item.name.split('\n');
        const lh = 16.5 * k;
        const block = lines.length * lh + 14 * k;
        let ty = y + (rowH - block) / 2 + 12.5 * k;
        for (const line of lines) {
          centred(c, line, x + colW / 2, ty, name, paint('#2B1A14'));
          ty += lh;
        }
        centred(c, item.meta, x + colW / 2, ty + 2 * k, meta, paint('#A0623C'), 1.1);
        // The seam between rows: a fine dark line and a light one under it.
        c.drawRect(Skia.XYWHRect(x, y, colW, 0.8), paint('#B89A78', 0.7));
        c.drawRect(Skia.XYWHRect(x, y + 0.8, colW, 0.8), paint('#FFFFFF', 0.6));
      }
    }
  });
}

/** The wall, the cabinet, its marquee, the reel labels and the lever's hub, at px pixels per point. */
export function bakeBack(L: Layout, px: number): SkImage | null {
  const { W, H, machine: m, win } = L;
  return snapshot(W * px, H * px, (c) => {
    c.scale(px, px);
    // A warm dark wall, a pool of light behind the machine, a vignette, grain.
    c.drawRect(Skia.XYWHRect(0, 0, W, H), linear(0, 0, 0, H, ['#2A1A13', '#1E140F', '#140D0A'], [0, 0.5, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, W, H), radial(W / 2, m.y + m.h * 0.45, W * 0.95, ['rgba(214,120,70,0.26)', 'rgba(150,70,40,0.08)', 'rgba(0,0,0,0)'], [0, 0.55, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, W, H), radial(W / 2, H * 0.45, H * 0.75, ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(6,3,2,0.55)'], [0, 0.6, 1]));
    for (let g = 0; g < 4200; g++) c.drawRect(Skia.XYWHRect(hash(g * 1.3) * W, hash(g * 2.7 + 5) * H, 0.8, 0.8), paint(hash(g * 4.1) > 0.5 ? '#FFE8D0' : '#000000', 0.035));

    // The cabinet: two shadows, cherry enamel with three stops, a chrome rim, an edge highlight.
    c.drawRRect(rr(m.x, m.y + 18, m.w, m.h, 32), blur(paint('#000000', 0.45), 22));
    c.drawRRect(rr(m.x, m.y + 2, m.w, m.h, 32), blur(paint('#000000', 0.5), 3));
    c.drawRRect(rr(m.x, m.y, m.w, m.h, 32), linear(m.x, m.y, m.x + m.w * 0.3, m.y + m.h, ['#D5463A', '#A9232A', '#7A141C'], [0, 0.5, 1]));
    c.drawRRect(rr(m.x, m.y, m.w, m.h, 32), radial(m.x + m.w * 0.3, m.y + 10, m.w * 0.7, ['rgba(255,200,170,0.32)', 'rgba(255,170,140,0)'], null));
    c.drawRRect(rr(m.x + 1.5, m.y + 1.5, m.w - 3, m.h - 3, 30.5), stroke(linear(0, m.y, 0, m.y + m.h, ['#FFF6EA', '#C9B9A9', '#8D7B6E', '#E8DCCF'], [0, 0.3, 0.7, 1]), 3));
    c.drawRRect(rr(m.x + 4, m.y + 4, m.w - 8, m.h - 8, 28), stroke(paint('#3A0A0E', 0.55), 1));

    // Marquee: a cream sign with small warm bulbs and the name.
    const sx = m.x + 18;
    const sy = m.y + 14;
    const sw = m.w - 36;
    c.drawRRect(rr(sx, sy, sw, 30, 15), linear(0, sy, 0, sy + 30, ['#FFF4DF', '#F1DDB8', '#DCC092'], [0, 0.6, 1]));
    c.drawRRect(rr(sx, sy, sw, 30, 15), stroke(paint('#7A4A22', 0.5), 1));
    for (let b = 0; b < 2; b++) {
      const bx = b === 0 ? sx + 16 : sx + sw - 16;
      c.drawCircle(bx, sy + 15, 9, radial(bx, sy + 15, 9, ['rgba(255,214,140,0.9)', 'rgba(255,170,80,0)'], null));
      c.drawCircle(bx, sy + 15, 4, radial(bx - 1, sy + 14, 4, ['#FFFBEA', '#FFC86A', '#D88A2A'], [0, 0.5, 1]));
    }
    centred(c, 'SUPPER SLOTS', sx + sw / 2, sy + 20.5, font(13, '800'), paint('#7A1C1E'), 2.2);

    // Labels over the reels.
    const lab = font(10, 'bold');
    ['MAIN', 'SIDE', 'DRINK'].forEach((s, i) => centred(c, s, win.x + i * (L.colW + L.gap) + L.colW / 2, win.y - 9, lab, paint('#FFE3CF', 0.75), 1.6));

    // The window's well, sunk into the enamel.
    c.drawRRect(rr(win.x - 6, win.y - 6, win.w + 12, win.h + 12, 16), linear(0, win.y - 6, 0, win.y + win.h + 6, ['#5E0E14', '#2C0709', '#6F1A1E'], [0, 0.5, 1]));
    c.drawRRect(rr(win.x - 6, win.y - 6, win.w + 12, win.h + 12, 16), stroke(linear(0, win.y, 0, win.y + win.h, ['#F6EADB', '#9C8A7C', '#F0E2D2'], [0, 0.5, 1]), 2.2));
    c.drawRRect(rr(win.x, win.y, win.w, win.h, 10), paint('#170A08'));

    // Under the window: a small brass plaque.
    const py = win.y + win.h + 12;
    c.drawRRect(rr(m.x + m.w / 2 - 54, py, 108, 16, 8), linear(0, py, 0, py + 16, ['#F7D58C', '#C8913C', '#8E5E22'], [0, 0.5, 1]));
    centred(c, 'PULL TO PICK', m.x + m.w / 2, py + 11.5, font(8.5, 'bold'), paint('#4A2A0E'), 1.4);

    // The lever's hub on the cabinet's flank: a chrome boss with a dark socket.
    const { x: hx, y: hy } = L.pivot;
    c.drawRRect(rr(hx - 18, hy - 42, 26, 84, 13), blur(paint('#000000', 0.45), 6));
    c.drawRRect(rr(hx - 16, hy - 40, 24, 80, 12), linear(hx - 16, 0, hx + 8, 0, ['#6A1016', '#B8323A', '#5C0C12'], [0, 0.45, 1]));
    c.drawCircle(hx, hy, 15, blur(paint('#000000', 0.5), 3));
    c.drawCircle(hx, hy, 13, radial(hx - 4, hy - 5, 16, ['#FFFFFF', '#C9CED2', '#6B7176', '#2C3034'], [0, 0.3, 0.75, 1]));
    c.drawCircle(hx, hy, 5, paint('#1A1C1F'));
  });
}

/** Over the reels: the window's inner shadows, a glass streak, the payline's brass arrows. */
export function bakeFront(L: Layout, px: number): SkImage | null {
  const { win } = L;
  const pad = 14;
  const w = win.w + pad * 2;
  const h = win.h + pad * 2;
  return snapshot(w * px, h * px, (c) => {
    c.scale(px, px);
    c.translate(pad, pad);
    c.save();
    c.clipRRect(rr(0, 0, win.w, win.h, 10), ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(0, 0, win.w, 26), linear(0, 0, 0, 26, ['rgba(20,6,4,0.75)', 'rgba(20,6,4,0)'], null));
    c.drawRect(Skia.XYWHRect(0, win.h - 30, win.w, 30), linear(0, win.h - 30, 0, win.h, ['rgba(20,6,4,0)', 'rgba(20,6,4,0.8)'], null));
    // Glass: a soft diagonal streak and a sharp thin one.
    c.save();
    c.rotate(-24, win.w / 2, win.h / 2);
    c.drawRect(Skia.XYWHRect(-win.w, win.h * 0.12, win.w * 3, win.h * 0.16), blur(paint('#FFFFFF', 0.09), 16));
    c.drawRect(Skia.XYWHRect(-win.w, win.h * 0.33, win.w * 3, 2.5), blur(paint('#FFFFFF', 0.12), 1.5));
    c.restore();
    c.restore();
    // Payline arrows on both sides, pointing in.
    const y = win.h / 2;
    for (const side of [-1, 1]) {
      const x = side < 0 ? -5 : win.w + 5;
      const b = Skia.PathBuilder.Make();
      b.moveTo(x - side * 7, y - 8);
      b.lineTo(x + side * 4, y);
      b.lineTo(x - side * 7, y + 8);
      b.close();
      const tri = b.build();
      c.drawPath(tri, blur(paint('#000000', 0.5), 2));
      c.drawPath(tri, linear(0, y - 8, 0, y + 8, ['#FFE6A6', '#E0A548', '#9B6522'], [0, 0.5, 1]));
    }
  });
}

export const FRONT_PAD = 14;

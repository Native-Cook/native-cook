import { Platform } from 'react-native';
import { ClipOp, FillType, matchFont, PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkColor, type SkFont, type SkImage, type SkPath } from '@shopify/react-native-skia';

/** One card of the flow: its column, its place in the 393-wide design space and its size. */
export type Card = { column: number; row: number; x: number; width: number; height: number; baseY: number; artIndex: number };

export const CARD_RADIUS = 20;
/** Upward drift in points per second: the left column runs faster than the right (parallax). */
export const CARD_SPEEDS = [44, 30];
/**
 * [x, y, width, height] per row, placed by hand: 100–150 pt wide (25–38 % of the screen), staggered,
 * some bleeding past the screen edge, with 114–138 pt of black between cards of a column.
 */
const LEFT = [[-26, 40, 150, 196], [30, 360, 118, 150], [-14, 640, 144, 188], [46, 960, 100, 132], [-30, 1210, 150, 196], [24, 1530, 124, 162]];
const RIGHT = [[262, 110, 146, 186], [236, 420, 140, 182], [290, 740, 118, 150], [226, 1010, 150, 196], [280, 1330, 104, 136], [244, 1580, 146, 190]];

// Which of a category's ten artworks a card shows: ten cards in a row from top to bottom never repeat one.
export const CARDS: Card[] = [LEFT, RIGHT].flatMap((rows, column) =>
  rows.map(([x, baseY, width, height], row) => ({ column, row, x, width, height, baseY, artIndex: (row * 2 + column) % 10 })));

type Kind = 'orb' | 'blob' | 'flower' | 'poster' | 'landscape' | 'arches' | 'checker' | 'pills' | 'ring';
/** A code-drawn artwork: `kind` picks the drawing, `colors` its palette (0xRRGGBB). */
type Spec = { kind: Kind; colors: number[]; words?: string[]; caption?: string; seed?: number };

/** Four categories of ten artworks, in the order the placeholder names them: anything, illustration, typography, 3D shapes. */
export const CATEGORIES: Spec[][] = [
  [
    { kind: 'blob', colors: [0x1e3bff, 0x6f8dff, 0xffd1ee, 0xff5fc8, 0xb01682], seed: 0.7 },
    { kind: 'flower', colors: [0xf3efea, 0xff8ad0, 0xff2ea6, 0xb0005e, 0xfff3a0, 0xffd23f, 0xe09400], seed: 0.2 },
    { kind: 'landscape', colors: [0xff4fd8, 0xffc86b, 0xc8f56a, 0xfff7d6, 0x7bd88f, 0x3fae6a, 0x1f7a4d], seed: 0.3 },
    { kind: 'poster', colors: [0xff4a1c, 0xffe9d6], words: ['Yes.'], caption: 'Edition 01' },
    { kind: 'orb', colors: [0x7cf0c8, 0x22a893, 0xffe2b8, 0xff8a3d, 0xb33f00], seed: 0.8 },
    { kind: 'arches', colors: [0xffe3c2, 0xff4a1c, 0xff8a3d, 0xffc23d, 0x3db2ff, 0x2346ff] },
    { kind: 'pills', colors: [0x8e7bff, 0x4b2ae8, 0xfff6b0, 0xffd23f, 0xd69a00, 0xffc4e6, 0xff5fc8, 0xb01682, 0xffffff, 0xe6e6f0, 0x9a9ab8] },
    { kind: 'checker', colors: [0xff8a3d, 0xffb067, 0xfffbd6, 0xffe14d] },
    { kind: 'ring', colors: [0x2346ff, 0x111a66, 0xfff3c4, 0xffc23d, 0xc27a00] },
    { kind: 'landscape', colors: [0x1bc7ff, 0xffb6e1, 0xffe9a8, 0xffffff, 0xff8fb8, 0xe0457b, 0x8e1f4f], seed: 0.35 },
  ],
  [
    { kind: 'landscape', colors: [0x3a1c71, 0xd76d77, 0xffaf7b, 0xffe8c8, 0x9b4f96, 0x5e2d79, 0x2e1450], seed: 0.45 },
    { kind: 'flower', colors: [0x3b5bff, 0xffffff, 0xe8ecff, 0xa9b6ff, 0xffd9a0, 0xff8a3d, 0xc2410c], seed: 0.5 },
    { kind: 'arches', colors: [0x2b2d6e, 0xffb3c7, 0xffd6a5, 0xfdffb6, 0xcaffbf, 0x9bf6ff] },
    { kind: 'landscape', colors: [0x5ccbff, 0xb7f0ff, 0xfff4c2, 0xffffff, 0x7ee08a, 0x2fb36b, 0x147a4a], seed: 0.25 },
    { kind: 'flower', colors: [0xffd6e8, 0xffc27a, 0xff7a00, 0xc24a00, 0xc6a8ff, 0x6b2bff, 0x3a0ca3], seed: 0 },
    { kind: 'checker', colors: [0x1bb55c, 0x139447, 0xffc4f0, 0xff4fd8] },
    { kind: 'landscape', colors: [0x0b1340, 0x24308a, 0x5e4bc9, 0xfff6c8, 0x2a2f7a, 0x1a1d52, 0x0d0f33], seed: 0.22 },
    { kind: 'arches', colors: [0xff5b3a, 0xffe14d, 0xffffff, 0x2346ff, 0x7c3aed, 0xffb3c7] },
    { kind: 'flower', colors: [0x1e1b3a, 0xffe38a, 0xffc23d, 0xc27a00, 0xffb3c7, 0xff4f8b, 0xa3164a], seed: 0.3 },
    { kind: 'checker', colors: [0x2346ff, 0x1b37cc, 0xfffbd6, 0xffc23d] },
  ],
  [
    { kind: 'poster', colors: [0xff2d2d, 0xfff1e0], words: ['BOLD'], caption: 'Weight 900' },
    { kind: 'poster', colors: [0x22c55e, 0x0b3d1e], words: ['Aa'], caption: 'Specimen' },
    { kind: 'poster', colors: [0x2346ff, 0xffe14d], words: ['GO!'], caption: 'Vol. 03' },
    { kind: 'poster', colors: [0xff8a00, 0x1a1a1a], words: ['47'], caption: 'No. 47' },
    { kind: 'poster', colors: [0xf5f0e6, 0xe63b2e], words: ['Big', 'Idea'], caption: 'Display' },
    { kind: 'poster', colors: [0xff5fc8, 0x2a0a3c], words: ['ZINE'], caption: 'Issue 12' },
    { kind: 'poster', colors: [0xffe14d, 0x1a1a1a], words: ['Type', 'Set'], caption: 'Grotesk' },
    { kind: 'poster', colors: [0x7c3aed, 0xf5f0e6], words: ['Néo'], caption: 'Studio' },
    { kind: 'poster', colors: [0x00c2a8, 0x062a26], words: ['Hello'], caption: 'Sans Serif' },
    { kind: 'poster', colors: [0xffb3c7, 0xb0005e], words: ['Wow'], caption: 'Headline' },
  ],
  [
    { kind: 'orb', colors: [0xff7ac6, 0xffb8dd, 0xc9e4ff, 0x3d7bff, 0x1238a8], seed: 0.2 },
    { kind: 'ring', colors: [0x1c1c3a, 0x3a2b8f, 0xfff0c2, 0xffb547, 0xb8620b] },
    { kind: 'blob', colors: [0xf2f2f5, 0xd5d5e0, 0xf3c8ff, 0xb15cff, 0x5b1aa8], seed: 2.1 },
    { kind: 'pills', colors: [0x22d3ee, 0x0284c7, 0xffffff, 0xf0f0f5, 0xa0a0b8, 0xffe1a8, 0xff8a3d, 0xb33f00, 0xc4f5d0, 0x34d399, 0x047857] },
    { kind: 'ring', colors: [0xffd84d, 0xff9f1c, 0xffd1ee, 0xff4fb3, 0xa3006b] },
    { kind: 'orb', colors: [0x4b1fd1, 0x7b5cff, 0xf4ffc2, 0xb8f02a, 0x5a8a00], seed: 0.9 },
    { kind: 'blob', colors: [0x0f172a, 0x1e293b, 0xc2fff2, 0x2ee6c5, 0x0b7a6a], seed: 4.0 },
    { kind: 'pills', colors: [0xff5b3a, 0xff9575, 0xfff6b0, 0xffd23f, 0xd69a00, 0xc9e4ff, 0x3d7bff, 0x1238a8, 0xffffff, 0xededf5, 0x9a9ab8] },
    { kind: 'orb', colors: [0xffe14d, 0xffb800, 0xffd1ee, 0xff4fb3, 0xa3006b], seed: 0.7 },
    { kind: 'blob', colors: [0xff6a3d, 0xff9e7a, 0xfff6b0, 0xffd23f, 0xc28e00], seed: 5.3 },
  ],
];

/** A CSS hex colour with alpha, which Skia.Color reads. */
export function hexColor(hex: number, alpha = 1) {
  'worklet';
  const a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
  return '#' + (hex & 0xffffff).toString(16).padStart(6, '0') + a.toString(16).padStart(2, '0');
}
/** The colour with its channels multiplied by `factor` (a darker shade). */
function shade(hex: number, factor: number) {
  const r = Math.floor(((hex >> 16) & 255) * factor), g = Math.floor(((hex >> 8) & 255) * factor), b = Math.floor((hex & 255) * factor);
  return (r << 16) | (g << 8) | b;
}

const CAPTION_FAMILY = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
const POSTER_FAMILY = Platform.OS === 'ios' ? 'Avenir Next' : 'sans-serif';

function textWidth(font: SkFont, text: string) {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, n) => sum + n, 0);
}

/** Skia drawing of the artworks, in card coordinates (0…w, 0…h, y down). */
function paintArt(c: SkCanvas, spec: Spec, w: number, h: number) {
  const m = Math.min(w, h);
  const k = spec.colors;
  const seed = spec.seed ?? 0;
  const col = (hex: number, alpha = 1) => Skia.Color(hexColor(hex, alpha));
  const paint = () => { const p = Skia.Paint(); p.setAntiAlias(true); p.setDither(true); return p; };
  const linear = (path: SkPath, colors: SkColor[], stops: number[] | null, x0: number, y0: number, x1: number, y1: number) => {
    const p = paint();
    p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, stops, TileMode.Clamp));
    c.drawPath(path, p);
  };
  const radial = (path: SkPath, colors: SkColor[], stops: number[] | null, x: number, y: number, r: number) => {
    const p = paint();
    p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, Math.max(r, 0.01), colors, stops, TileMode.Clamp));
    c.drawPath(path, p);
  };
  const fill = (path: SkPath, color: SkColor) => { const p = paint(); p.setColor(color); c.drawPath(path, p); };
  const circle = (x: number, y: number, r: number) => { const p = Skia.Path.Make(); p.addCircle(x, y, r); return p; };
  const oval = (x: number, y: number, rx: number, ry: number) => { const p = Skia.Path.Make(); p.addOval(Skia.XYWHRect(x - rx, y - ry, 2 * rx, 2 * ry)); return p; };
  const rect = (x: number, y: number, rw: number, rh: number) => { const p = Skia.Path.Make(); p.addRect(Skia.XYWHRect(x, y, rw, rh)); return p; };
  const roundRect = (x: number, y: number, rw: number, rh: number, r: number) => { const p = Skia.Path.Make(); p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, rw, rh), r, r)); return p; };
  const all = () => rect(0, 0, w, h);
  const shadow = (x: number, y: number, rx: number, ry: number, alpha: number) => radial(oval(x, y, rx, ry), [col(0, alpha), col(0, 0)], null, x, y, rx);
  const white = (x: number, y: number, r: number, alpha: number) => radial(circle(x, y, r), [col(0xffffff, alpha), col(0xffffff, 0)], null, x, y, r);
  // A glossy sphere lit from the upper left.
  const sphere = (x: number, y: number, r: number, hi: number, base: number, deep: number) => {
    radial(circle(x, y, r), [col(hi), col(base), col(deep)], [0, 0.5, 1], x - 0.35 * r, y - 0.4 * r, 1.45 * r);
    white(x - 0.36 * r, y - 0.42 * r, 0.26 * r, 0.85);
  };

  switch (spec.kind) {
    case 'orb': {
      linear(all(), [col(k[0]), col(k[1])], null, 0, 0, 0, h);
      const r = m * 0.32, x = w * 0.5, y = h * 0.5;
      shadow(x, y + r * 1.04, r * 1.05, r * 0.18, 0.38);
      sphere(x, y, r, k[2], k[3], k[4]);
      if (seed > 0.5) sphere(w * 0.78, h * 0.2, m * 0.12, k[2], k[3], k[4]);
      break;
    }
    case 'blob': {
      linear(all(), [col(k[0]), col(k[1])], null, 0, 0, w, h);
      const r = m * 0.36, x = w * 0.5, y = h * 0.5;
      const path = Skia.Path.Make();
      for (let i = 0; i < 120; i++) {
        const a = (i / 120) * 2 * Math.PI;
        const rr = r * (1 + 0.13 * Math.sin(3 * a + seed) + 0.07 * Math.sin(5 * a + 2 * seed) + 0.04 * Math.cos(2 * a));
        if (i === 0) path.moveTo(x + rr * Math.cos(a), y + rr * Math.sin(a)); else path.lineTo(x + rr * Math.cos(a), y + rr * Math.sin(a));
      }
      path.close();
      shadow(x, y + r * 1.12, r * 0.9, r * 0.14, 0.3);
      radial(path, [col(k[2]), col(k[3]), col(k[4])], [0, 0.48, 1], x - 0.3 * r, y - 0.38 * r, 1.5 * r);
      white(x - 0.34 * r, y - 0.4 * r, 0.3 * r, 0.7);
      break;
    }
    case 'flower': {
      fill(all(), col(k[0]));
      const x = w * 0.5, y = h * 0.5, l = m * 0.34;
      white(x, y, m * 0.6, 0.35);
      radial(circle(x + l * 0.12, y + l * 0.18, l * 1.15), [col(0, 0.22), col(0, 0)], null, x + l * 0.12, y + l * 0.18, l * 1.15);
      for (let i = 0; i < 6; i++) {
        c.save();
        c.translate(x, y);
        c.rotate(seed * 60 + i * 60, 0, 0);
        const petal = Skia.Path.Make();
        petal.addOval(Skia.XYWHRect(-0.31 * l, -1.02 * l, 0.62 * l, 0.84 * l));
        radial(petal, [col(k[3]), col(k[2]), col(k[1])], [0, 0.45, 1], 0, -0.12 * l, 1.0 * l);
        c.restore();
      }
      const r = 0.3 * l;
      radial(circle(x, y, r), [col(k[4]), col(k[5]), col(k[6])], [0, 0.5, 1], x - 0.1 * l, y - 0.12 * l, 0.42 * l);
      white(x - 0.32 * r, y - 0.36 * r, 0.3 * r, 0.7);
      break;
    }
    case 'poster': {
      fill(all(), col(k[0]));
      fill(circle(w * 0.86, h * 0.07 + 6, 5), col(k[1]));
      const caption = matchFont({ fontFamily: CAPTION_FAMILY, fontSize: 10, fontWeight: '600' });
      const ink = paint(); ink.setColor(col(k[1], 0.85));
      c.drawText(spec.caption ?? '', w * 0.07, h * 0.07 + 10, ink, caption);
      fill(rect(w * 0.07, h * 0.07 + 17, w * 0.86, 1), col(k[1], 0.5));
      const words = spec.words ?? [];
      const probe = matchFont({ fontFamily: POSTER_FAMILY, fontSize: 100, fontWeight: '900' });
      const widest = Math.max(...words.map(word => textWidth(probe, word)), 1);
      const n = words.length;
      const size = Math.min((100 * w * 0.86) / widest, (h * 0.62) / (n * 0.82));
      const font = matchFont({ fontFamily: POSTER_FAMILY, fontSize: size, fontWeight: '900' });
      const big = paint(); big.setColor(col(k[1]));
      const last = h - w * 0.08;
      words.forEach((word, i) => c.drawText(word, w * 0.07, last - (n - 1 - i) * size * 0.84, big, font));
      break;
    }
    case 'landscape': {
      linear(all(), [col(k[0]), col(k[1]), col(k[2])], null, 0, 0, 0, h);
      const sx = w * 0.64, sy = h * seed;
      radial(circle(sx, sy, w * 0.42), [col(k[3], 0.55), col(k[3], 0)], null, sx, sy, w * 0.42);
      fill(circle(sx, sy, w * 0.13), col(k[3]));
      for (let layer = 0; layer < 3; layer++) {
        const top = h * (0.56 + 0.12 * layer), amp = h * 0.035 * (1 - 0.2 * layer);
        const freq = 1.2 + 0.5 * layer, phase = layer * 1.7 + seed * 3;
        const path = Skia.Path.Make();
        path.moveTo(0, h);
        for (let j = 0; j <= 40; j++) {
          const u = j / 40;
          path.lineTo(w * u, top + amp * Math.sin(u * 2 * Math.PI * freq + phase));
        }
        path.lineTo(w, h); path.close();
        const hill = k[4 + layer];
        linear(path, [col(hill), col(shade(hill, 0.7))], null, 0, top - amp, 0, h);
      }
      break;
    }
    case 'arches': {
      fill(all(), col(k[0]));
      fill(circle(w * 0.24, h * 0.2, m * 0.08), col(k[1]));
      const r0 = w * 0.62;
      for (let i = 0; i < 5; i++) fill(circle(w * 0.5, h * 0.97, r0 * (1 - i * 0.17)), col(k[1 + i]));
      fill(circle(w * 0.5, h * 0.97, r0 * 0.15), col(k[0]));
      break;
    }
    case 'checker': {
      fill(all(), col(k[0]));
      const s = w / 6;
      for (let row = 0; row < Math.ceil(h / s); row++) for (let column = 0; column < 6; column++) {
        if ((row + column) % 2 === 0) fill(rect(column * s, row * s, s, s), col(k[1]));
      }
      const x = w * 0.5, y = h * 0.46, r = w * 0.3;
      radial(circle(x, y, r * 1.6), [col(k[3], 0.5), col(k[3], 0)], null, x, y, r * 1.6);
      radial(circle(x, y, r), [col(k[2]), col(k[3])], null, x - 0.3 * r, y - 0.35 * r, 1.4 * r);
      break;
    }
    case 'pills': {
      linear(all(), [col(k[0]), col(k[1])], null, 0, 0, 0, h);
      const l = w * 0.64, t = w * 0.22;
      [[0.4, 0.28], [0.6, 0.52], [0.4, 0.76]].forEach(([px, py], i) => {
        c.save();
        c.translate(w * px, h * py);
        c.rotate(-35, 0, 0);
        fill(roundRect(-l / 2 + t * 0.12, -t / 2 + t * 0.28, l, t, t / 2), col(0, 0.2));
        linear(roundRect(-l / 2, -t / 2, l, t, t / 2), [col(k[2 + i * 3]), col(k[3 + i * 3]), col(k[4 + i * 3])], [0, 0.45, 1], 0, -t / 2, 0, t / 2);
        fill(roundRect(-l / 2 + t * 0.4, -t * 0.33, l - t * 0.8, t * 0.13, t * 0.065), col(0xffffff, 0.6));
        c.restore();
      });
      break;
    }
    case 'ring': {
      linear(all(), [col(k[0]), col(k[1])], null, 0, 0, 0, h);
      const x = w * 0.5, y = h * 0.5, ro = m * 0.36, ri = m * 0.17;
      shadow(x, y + ro * 1.02, ro * 0.95, ro * 0.16, 0.3);
      const ring = Skia.Path.Make();
      ring.setFillType(FillType.EvenOdd);
      ring.addCircle(x, y, ro); ring.addCircle(x, y, ri);
      linear(ring, [col(k[2]), col(k[3]), col(k[4])], [0, 0.5, 1], x - ro, y - ro, x + ro, y + ro);
      const inner = paint(); inner.setStyle(PaintStyle.Stroke); inner.setStrokeWidth((ro - ri) * 0.25); inner.setColor(col(k[4], 0.35));
      c.drawCircle(x, y, ri + (ro - ri) * 0.12, inner);
      const arc = Skia.Path.Make(), mid = (ro + ri) / 2;
      for (let j = 0; j <= 20; j++) {
        const a = ((200 + (55 * j) / 20) * Math.PI) / 180;
        if (j === 0) arc.moveTo(x + mid * Math.cos(a), y + mid * Math.sin(a)); else arc.lineTo(x + mid * Math.cos(a), y + mid * Math.sin(a));
      }
      const shine = paint(); shine.setStyle(PaintStyle.Stroke); shine.setStrokeWidth((ro - ri) * 0.2); shine.setStrokeCap(StrokeCap.Round); shine.setColor(col(0xffffff, 0.65));
      c.drawPath(arc, shine);
      break;
    }
  }
}

function renderCard(spec: Spec, card: Card, scale: number): SkImage {
  const w = card.width, h = card.height;
  const pw = Math.max(1, Math.ceil(w * scale)), ph = Math.max(1, Math.ceil(h * scale));
  const surface = Skia.Surface.Make(pw, ph)!;
  const c = surface.getCanvas();
  c.scale(pw / w, ph / h);
  // Paint the artwork clipped to the rounded card (a DstIn shape would leave the corners outside it untouched).
  c.save();
  c.clipRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), CARD_RADIUS, CARD_RADIUS), ClipOp.Intersect, true);
  paintArt(c, spec, w, h);
  c.restore();
  const border = Skia.Paint(); border.setAntiAlias(true); border.setStyle(PaintStyle.Stroke); border.setStrokeWidth(1); border.setColor(Skia.Color(hexColor(0xffffff, 0.08)));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.5, 0.5, w - 1, h - 1), CARD_RADIUS - 0.5, CARD_RADIUS - 0.5), border);
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

/**
 * The card images, drawn once: a sharp image per card and category, plus a tiny copy of the first category's,
 * which drawn large reads as a blur while the cards bloom in.
 */
export type Art = { sharp: SkImage[][]; soft: SkImage[] };

export function bakeArt(scale: number): Art {
  const sharp = CATEGORIES.map(specs => CARDS.map(card => renderCard(specs[card.artIndex], card, scale)));
  const soft = CARDS.map(card => renderCard(CATEGORIES[0][card.artIndex], card, 0.09));
  return { sharp, soft };
}

export function disposeArt(art: Art) {
  art.sharp.forEach(row => row.forEach(image => image.dispose()));
  art.soft.forEach(image => image.dispose());
}

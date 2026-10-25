/*
 Magnetic Letters Name — everything drawn once, at layout time, on the JS thread:
 - the fridge door (brushed white steel, reflections, the freezer seam, two chrome handles, the
   engraved name line) as one screen-sized image;
 - an atlas of the magnets' faces (glossy plastic with a 3 pt extruded side, a bevel and a gloss
   spot), one cell per magnet, at the screen's pixel ratio;
 - an atlas of glyph-shaped shadows, four per letter (contact → lifted: more blur, less alpha).
 A frame then only places sprites (two <Atlas> draws).
*/
import { BlurStyle, ClipOp, PaintStyle, Skia, TileMode, matchFont, type SkCanvas, type SkFont, type SkImage, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import { MAX_LETTERS, SCRIPT, type Pose } from './magneticLettersMotion';

/** The letters on the door: A–Z and a spare E and A. */
const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZEA';
/** Plastic colours: light (top of the face), base, deep (bottom), side (the extrusion). */
const PLASTIC = [
  ['#FF8A7A', '#E8483C', '#BC2C26', '#8A1D1A'],
  ['#FFBE6E', '#F28A2E', '#CA641B', '#954310'],
  ['#FFE893', '#F5C232', '#D59A16', '#9F6F0B'],
  ['#86D896', '#3FA45B', '#2A7B43', '#1C5630'],
  ['#86B9FF', '#3076D2', '#1F53A2', '#163B74'],
  ['#C29DF4', '#8858CB', '#603C9C', '#452A71'],
];
/** The loop's name gets its own colours (red, yellow, blue, green) so it reads as a set. */
const SCRIPT_COLORS = [0, 2, 4, 3];

export const DOOR = {
  hi: '#F6F6F3',
  mid: '#E8E9E5',
  low: '#D7D9D5',
  ink: '#22262B',
  inkSoft: 'rgba(34,38,43,0.56)',
  inkFaint: 'rgba(34,38,43,0.34)',
  blue: '#2350A8',
};

/** Shadow levels: blur sigma and alpha from resting flat on the steel (0) to held up (3). */
const SHADOW_SIGMA = [1.4, 3.2, 5.6, 8.5];
const SHADOW_ALPHA = [0.46, 0.32, 0.23, 0.17];
const SHADOW_SCALE = 1.5;

export type Magnet = { letter: string; color: number; home: Pose; w: number; r: number };

export type Layout = {
  W: number;
  H: number;
  /** Magnet scale against the 393 × 852 design. */
  s: number;
  seamY: number;
  lineY: number;
  letterY: number;
  lineX0: number;
  lineX1: number;
  pile: { x0: number; y0: number; x1: number; y1: number };
  magnets: Magnet[];
  /** Index of the magnet each letter of SCRIPT uses. */
  script: number[];
  /** Base draw order: higher on the pile first, so lower magnets overlap them. */
  order: number[];
  cell: number;
  shadowCell: number;
  capH: number;
  gap: number;
};

function hash(n: number) {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
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

function snapshot(w: number, h: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(w), Math.ceil(h)) ?? Skia.Surface.Make(Math.ceil(w), Math.ceil(h));
  if (!surface) return null;
  draw(surface.getCanvas());
  surface.flush();
  const img = surface.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

/** A rounded, heavy face for the magnets; falls back to a bold system face if it is missing. */
export function magnetFont(size: number): SkFont {
  const rounded = matchFont({ fontFamily: Platform.select({ ios: 'Arial Rounded MT Bold', default: 'sans-serif' }), fontSize: size, fontWeight: 'bold' });
  const probe = Skia.Path.MakeFromText('A', 0, 0, rounded);
  if (probe && probe.computeTightBounds().width > 1) return rounded;
  return matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: size, fontWeight: 'bold' });
}

export function glyphPaths(font: SkFont) {
  const paths: SkPath[] = [];
  for (let i = 0; i < LETTERS.length; i++) {
    paths.push(Skia.Path.MakeFromText(LETTERS[i], 0, 0, font) ?? Skia.Path.Make());
  }
  return paths;
}

function frame(W: number, H: number, top: number, bottom: number) {
  const seamY = top + 152;
  const lineY = seamY + 128;
  const pile = { x0: 22, y0: lineY + 46, x1: W - 50, y1: H - bottom - 104 };
  const s = Math.max(0.74, Math.min(1, W / 393, (pile.y1 - pile.y0) / 330));
  return { seamY, lineY, pile, s };
}

/** The magnets' scale for a screen (1 at 393 × 852); the font is sized from it before the layout. */
export function magnetScale(W: number, H: number, top: number, bottom: number) {
  return frame(W, H, top, bottom).s;
}

export function makeLayout(W: number, H: number, top: number, bottom: number, paths: SkPath[]): Layout {
  const { seamY, lineY, pile, s } = frame(W, H, top, bottom);
  const capH = paths[LETTERS.indexOf('H')].computeTightBounds().height;
  const letterY = lineY - 5 - capH / 2;
  const cell = Math.ceil(capH * 1.9);
  const shadowCell = cell + 28;

  const magnets: Magnet[] = [];
  const script: number[] = [];
  for (let i = 0; i < LETTERS.length; i++) {
    const b = paths[i].computeTightBounds();
    const k = SCRIPT.indexOf(LETTERS[i]);
    const isScript = k >= 0 && script[k] === undefined;
    if (isScript) script[k] = i;
    magnets.push({
      letter: LETTERS[i],
      color: isScript ? SCRIPT_COLORS[k] : (i * 5 + 1) % PLASTIC.length,
      home: { x: 0, y: 0, rot: 0, lift: 0, scale: 1 },
      w: b.width + 4,
      r: Math.max(b.width, capH) * 0.58,
    });
  }

  // A loose heap: a jittered grid, denser toward the bottom, then pushed apart a few times so
  // magnets overlap only at their edges. Seeded, so the heap is the same on every launch.
  const n = magnets.length;
  const cols = 6;
  const rows = Math.ceil(n / cols);
  const cw = (pile.x1 - pile.x0) / cols;
  const ch = (pile.y1 - pile.y0) / rows;
  const slots = Array.from({ length: cols * rows }, (_, i) => i).sort((a, b) => hash(a * 7.3) - hash(b * 7.3));
  const pts = magnets.map((m, i) => {
    const slot = slots[i];
    const c = slot % cols;
    const r = Math.floor(slot / cols);
    return {
      x: pile.x0 + (c + 0.5 + (hash(i * 3.1) - 0.5) * 0.6) * cw,
      y: pile.y0 + (r + 0.5 + (hash(i * 5.7) - 0.5) * 0.55) * ch,
    };
  });
  const minD = capH * 0.95;
  for (let pass = 0; pass < 6; pass++) {
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const dx = pts[j].x - pts[i].x;
        const dy = pts[j].y - pts[i].y;
        const d = Math.sqrt(dx * dx + dy * dy) || 1;
        if (d < minD) {
          const push = (minD - d) / 2;
          pts[i].x -= (dx / d) * push; pts[i].y -= (dy / d) * push;
          pts[j].x += (dx / d) * push; pts[j].y += (dy / d) * push;
        }
      }
    }
    for (const p of pts) {
      p.x = Math.min(pile.x1 - capH * 0.5, Math.max(pile.x0 + capH * 0.5, p.x));
      p.y = Math.min(pile.y1 - capH * 0.4, Math.max(pile.y0 + capH * 0.5, p.y));
    }
  }
  magnets.forEach((m, i) => {
    m.home = { x: pts[i].x, y: pts[i].y, rot: (hash(i * 9.1) - 0.5) * 0.9, lift: 0, scale: 1 };
  });
  const order = magnets.map((_, i) => i).sort((a, b) => magnets[a].home.y - magnets[b].home.y);

  return { W, H, s, seamY, lineY, letterY, lineX0: 30, lineX1: W - 72, pile, magnets, script, order, cell, shadowCell, capH, gap: 5 };
}

/** The x of each letter's centre on the line for a name made of these magnets. */
export function slotXs(L: Layout, name: number[]) {
  const xs: number[] = [];
  let x = L.lineX0;
  for (let k = 0; k < name.length; k++) {
    const w = L.magnets[name[k]].w;
    xs.push(x + w / 2);
    x += w + L.gap;
  }
  return xs;
}

export function maxLettersFit(L: Layout) {
  return Math.min(MAX_LETTERS, Math.floor((L.lineX1 - L.lineX0) / (L.capH * 0.95 + L.gap)));
}

/** The fridge door, the size of the screen, in pixels (scaled by `px`). */
export function bakeDoor(L: Layout, top: number, px: number): SkImage | null {
  const { W, H, seamY, lineY } = L;
  return snapshot(W * px, H * px, (c) => {
    c.scale(px, px);
    // Steel: a soft fall-off from the kitchen light above.
    c.drawRect(Skia.XYWHRect(0, 0, W, H), linear(0, 0, 0, H, [DOOR.hi, DOOR.mid, '#DFE1DD', DOOR.low], [0, 0.35, 0.7, 1]));
    // Brushing: thousands of horizontal hairlines, a little lighter or darker than the steel.
    for (let k = 0; k < H * 3; k++) {
      const y = hash(k + 1) * H;
      const x0 = hash(k * 3 + 2) * W * 0.8 - 40;
      const len = 40 + hash(k * 7 + 3) * W * 0.9;
      const light = hash(k * 11 + 4) > 0.45;
      c.drawRect(Skia.XYWHRect(x0, y, len, 0.4), paint(light ? '#FFFFFF' : '#8E9290', 0.05 + hash(k * 13 + 5) * (light ? 0.16 : 0.06)));
    }
    // Reflections: a cool window light from the left and a broad soft band across the upper door.
    c.drawRect(Skia.XYWHRect(0, 0, W * 0.4, H), linear(0, 0, W * 0.4, 0, ['rgba(196,212,228,0.42)', 'rgba(214,224,234,0.12)', 'rgba(230,236,240,0)'], [0, 0.5, 1]));
    c.save();
    c.rotate(-16, W / 2, H / 2);
    c.drawRect(Skia.XYWHRect(-W, H * 0.18, W * 3, H * 0.13), blur(paint('#FFFFFF', 0.5), 34));
    c.drawRect(Skia.XYWHRect(-W, H * 0.36, W * 3, H * 0.025), blur(paint('#FFFFFF', 0.4), 8));
    c.drawRect(Skia.XYWHRect(-W, H * 0.62, W * 3, H * 0.09), blur(paint('#B9BDBA', 0.16), 30));
    c.restore();
    // Warm bounce from the kitchen floor.
    c.drawRect(Skia.XYWHRect(0, H * 0.72, W, H * 0.28), linear(0, H * 0.72, 0, H, ['rgba(232,214,190,0)', 'rgba(228,206,178,0.24)'], null));
    // The door's rolled edges, left and right.
    c.drawRect(Skia.XYWHRect(0, 0, 10, H), linear(0, 0, 10, 0, ['rgba(120,124,122,0.28)', 'rgba(255,255,255,0.5)', 'rgba(255,255,255,0)'], [0, 0.35, 1]));
    c.drawRect(Skia.XYWHRect(W - 8, 0, 8, H), linear(W - 8, 0, W, 0, ['rgba(255,255,255,0)', 'rgba(110,114,112,0.3)'], null));

    // The gap between the freezer and fridge doors: a dark seam, the freezer's shaded lower lip,
    // the fridge door's lit top edge.
    c.drawRect(Skia.XYWHRect(0, seamY - 14, W, 14), linear(0, seamY - 14, 0, seamY, ['rgba(140,144,142,0)', 'rgba(120,124,122,0.32)'], null));
    c.drawRect(Skia.XYWHRect(0, seamY, W, 5), linear(0, seamY, 0, seamY + 5, ['#55595A', '#2F3335', '#4A4E4F'], [0, 0.5, 1]));
    c.drawRect(Skia.XYWHRect(0, seamY + 5, W, 1.2), paint('#FFFFFF', 0.95));
    c.drawRect(Skia.XYWHRect(0, seamY + 6, W, 18), linear(0, seamY + 6, 0, seamY + 24, ['rgba(255,255,255,0.55)', 'rgba(255,255,255,0)'], null));

    // Chrome handles on the right: the freezer's short one, the fridge's long one (below the name
    // line, so the backspace key has the door to itself).
    handle(c, W - 25, top + 26, seamY - 26);
    handle(c, W - 25, lineY + 44, Math.min(lineY + 300, H * 0.7));

    // The name line: an engraved groove (dark line, light line under it) and the strip's soft shade.
    c.drawRect(Skia.XYWHRect(18, lineY - L.capH - 30, L.lineX1 - 4, L.capH + 38), blur(paint('#9EA3A0', 0.1), 14));
    c.drawRect(Skia.XYWHRect(L.lineX0 - 6, lineY, L.lineX1 - L.lineX0 + 12, 1), paint('#6A6F6E', 0.5));
    c.drawRect(Skia.XYWHRect(L.lineX0 - 6, lineY + 1, L.lineX1 - L.lineX0 + 12, 1), paint('#FFFFFF', 0.95));

    // Grain, so the large pale flat does not read as vector.
    for (let k = 0; k < 5200; k++) {
      c.drawRect(Skia.XYWHRect(hash(k * 1.7 + 9) * W, hash(k * 2.3 + 4) * H, 0.7, 0.7), paint(hash(k * 3.9) > 0.5 ? '#FFFFFF' : '#6C706E', 0.07));
    }
  });
}

/** A chrome bar handle standing off the door on two posts, with its contact shadow. */
function handle(c: SkCanvas, x: number, y0: number, y1: number) {
  const w = 12;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 7, y0 + 6, w + 2, y1 - y0), 7, 7), blur(paint('#3A3E40', 0.3), 6));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 1, y0 + 2, w, y1 - y0), 6, 6), blur(paint('#3A3E40', 0.28), 1.6));
  for (const py of [y0 + 8, y1 - 14]) {
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 2, py, w + 4, 8), 3, 3), linear(x - 2, 0, x + w + 2, 0, ['#8D9294', '#E9ECEC', '#6F7476'], [0, 0.45, 1]));
  }
  c.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(x, y0, w, y1 - y0), 6, 6),
    linear(x, 0, x + w, 0, ['#6E7375', '#C9CDCE', '#FBFCFC', '#9EA3A5', '#5C6163'], [0, 0.28, 0.42, 0.7, 1]),
  );
  const rim = paint('#FFFFFF', 0.6);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(0.7);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.4, y0 + 0.4, w - 0.8, y1 - y0 - 0.8), 6, 6), rim);
}

/** Draws path p so the cap box of its glyph is centred on (cx, cy). */
function placeGlyph(c: SkCanvas, p: SkPath, L: Layout, cx: number, cy: number) {
  const b = p.computeTightBounds();
  c.translate(cx - (b.x + b.width / 2), cy + L.capH / 2);
}

/** The magnets' faces: one `cell` square per magnet, `cols` across, at `px` pixels per point. */
export function bakeFaces(L: Layout, paths: SkPath[], px: number, cols: number): SkImage | null {
  const rows = Math.ceil(L.magnets.length / cols);
  const cell = L.cell;
  return snapshot(cols * cell * px, rows * cell * px, (c) => {
    c.scale(px, px);
    L.magnets.forEach((m, i) => {
      const p = paths[i];
      const col = PLASTIC[m.color];
      const cx = (i % cols) * cell + cell / 2;
      const cy = Math.floor(i / cols) * cell + cell / 2;
      c.save();
      placeGlyph(c, p, L, cx, cy);
      const b = p.computeTightBounds();
      // The extruded side: the glyph stacked down and to the right, ~3 pt deep.
      for (let k = 6; k >= 1; k--) {
        c.save();
        c.translate(k * 0.3, k * 0.5);
        c.drawPath(p, k === 6 ? paint(col[3]) : linear(0, b.y, 0, b.y + b.height, [col[2], col[3]], null));
        c.restore();
      }
      // The face: three stops, lighter at the top where the room light lands.
      c.drawPath(p, linear(0, b.y, 0, b.y + b.height, [col[0], col[1], col[2]], [0, 0.45, 1]));
      c.save();
      c.clipPath(p, ClipOp.Intersect, true);
      // Bevel: a bright inner edge along the top, a darker one along the bottom.
      const hi = paint('#FFFFFF', 0.5);
      hi.setStyle(PaintStyle.Stroke);
      hi.setStrokeWidth(3);
      c.save();
      c.translate(0, 1.4);
      c.drawPath(p, blur(hi, 0.6));
      c.restore();
      const lo = paint(col[3], 0.45);
      lo.setStyle(PaintStyle.Stroke);
      lo.setStrokeWidth(3);
      c.save();
      c.translate(0, -1.4);
      c.drawPath(p, blur(lo, 0.8));
      c.restore();
      // Gloss: a soft specular spot up and to the left, and a thin sharp glint inside it.
      const gx = b.x + b.width * 0.32;
      const gy = b.y + b.height * 0.22;
      c.drawOval(Skia.XYWHRect(gx - b.width * 0.45, gy - b.height * 0.2, b.width * 0.9, b.height * 0.4), radial(gx, gy, b.width * 0.5, ['rgba(255,255,255,0.62)', 'rgba(255,255,255,0.18)', 'rgba(255,255,255,0)'], [0, 0.45, 1]));
      c.drawOval(Skia.XYWHRect(gx - b.width * 0.16, gy - 2.2, b.width * 0.26, 3.4), blur(paint('#FFFFFF', 0.75), 0.9));
      c.restore();
      const rim = paint(col[3], 0.35);
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(0.8);
      c.drawPath(p, rim);
      c.restore();
    });
  });
}

/** Glyph-shaped shadows: `shadowCell` squares, one row per magnet, one column per lift level. */
export function bakeShadows(L: Layout, paths: SkPath[]): SkImage | null {
  const cell = L.shadowCell;
  const px = SHADOW_SCALE;
  return snapshot(SHADOW_SIGMA.length * cell * px, L.magnets.length * cell * px, (c) => {
    c.scale(px, px);
    L.magnets.forEach((_, i) => {
      for (let lv = 0; lv < SHADOW_SIGMA.length; lv++) {
        c.save();
        placeGlyph(c, paths[i], L, lv * cell + cell / 2, i * cell + cell / 2);
        c.drawPath(paths[i], blur(paint('#2C2A26', SHADOW_ALPHA[lv]), SHADOW_SIGMA[lv]));
        c.restore();
      }
    });
  });
}

export const SHADOW_PX = SHADOW_SCALE;
export const SHADOW_LEVELS = SHADOW_SIGMA.length;
export const LETTER_SET = LETTERS;

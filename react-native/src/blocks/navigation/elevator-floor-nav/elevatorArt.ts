import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { FLOORS, type Pose, clamp01 } from './elevatorMotion';

/*
 Elevator Floor Nav — the drawing. One Skia canvas over baked images: the lobby (green marble, the
 floor dial's face, the button panel), one poster per floor (the store's section, drawn in code),
 the two brushed-steel doors (with their glass vision panels cut out), and the brass surround.
 Every frame: the posters slide behind the doorway (blurred with the car's speed), the doors slide,
 a highlight sweeps their brushed steel, then the needle, the lanterns and the lit buttons.
*/

export type Floor = { name: string; tagline: string; colors: string[]; items: { name: string; price: string }[] };

export const STORE: Floor[] = [
  {
    name: 'Shoes',
    tagline: 'Loafers, boots & the cobbler’s bench',
    colors: ['#6B3127', '#8C4434', '#4A211B'],
    items: [
      { name: 'Oxford loafer', price: '£185' },
      { name: 'Chelsea boot', price: '£240' },
      { name: 'Suede mule', price: '£150' },
    ],
  },
  {
    name: 'Fashion',
    tagline: 'Autumn tailoring & knitwear',
    colors: ['#24324F', '#344870', '#18223A'],
    items: [
      { name: 'Wool overcoat', price: '£420' },
      { name: 'Merino crew', price: '£95' },
      { name: 'Pleated trouser', price: '£140' },
    ],
  },
  {
    name: 'Beauty',
    tagline: 'The fragrance hall & apothecary',
    colors: ['#7D4B56', '#9E6673', '#523039'],
    items: [
      { name: 'Fig eau de parfum', price: '£88' },
      { name: 'Rose balm', price: '£24' },
      { name: 'Cedar candle', price: '£42' },
    ],
  },
  {
    name: 'Home',
    tagline: 'Linen, stoneware & lamps',
    colors: ['#46523D', '#5F7052', '#2E3628'],
    items: [
      { name: 'Stoneware jug', price: '£48' },
      { name: 'Linen throw', price: '£120' },
      { name: 'Brass lamp', price: '£210' },
    ],
  },
  {
    name: 'Café',
    tagline: 'The rooftop café · open till nine',
    colors: ['#73492A', '#946238', '#4B2F1A'],
    items: [
      { name: 'Flat white', price: '£4' },
      { name: 'Pear tart', price: '£7' },
      { name: 'Afternoon tea', price: '£32' },
    ],
  },
];

export type Fonts = { title: SkFont; caps: SkFont; body: SkFont; item: SkFont; price: SkFont; numeral: SkFont; button: SkFont };

export type Scene = {
  w: number;
  h: number;
  dialX: number;
  dialY: number;
  dialR: number;
  doorX: number;
  doorY: number;
  doorW: number;
  doorH: number;
  panelY: number;
  panelH: number;
  buttonX: number[];
  buttonY: number;
  buttonR: number;
};

export function makeScene(w: number, h: number, top: number, bottom: number): Scene {
  const dialR = Math.min(90, (h - top - bottom) * 0.11);
  const dialY = top + 86 + dialR;
  const doorX = 20;
  const doorY = dialY + 26;
  const doorW = w - 40;
  const panelH = 112;
  const panelY = h - bottom - panelH - 10;
  const doorH = panelY - 18 - doorY;
  const buttonR = 25;
  const span = doorW - 44;
  const buttonX: number[] = [];
  for (let i = 0; i < FLOORS; i += 1) buttonX.push(doorX + 22 + buttonR + ((span - buttonR * 2) * i) / (FLOORS - 1));
  return { w, h, dialX: w / 2, dialY, dialR, doorX, doorY, doorW, doorH, panelY, panelH, buttonX, buttonY: panelY + 44, buttonR };
}

/** The needle's angle (degrees from straight up) for a floor. */
export function needleAngle(floor: number) {
  'worklet';
  return -66 + ((floor - 1) * 132) / (FLOORS - 1);
}

/** Draws a baked image with linear filtering (it moves, rotates and lands between pixels). */
function drawImg(c: SkCanvas, img: SkImage, src: ReturnType<typeof Skia.XYWHRect>, dst: ReturnType<typeof Skia.XYWHRect>, p: SkPaint) {
  'worklet';
  c.drawImageRectOptions(img, src, dst, FilterMode.Linear, MipmapMode.None, p);
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(Math.max(0, alpha));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function shaded(shader: ReturnType<typeof linear>) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(shader);
  return p;
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function grain(alpha: number, fx: number, fy: number, seed: number, mode = BlendMode.Overlay) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(mode);
  return p;
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
}

function bake(w: number, h: number, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  return surface.makeImageSnapshot();
}

const BRASS = ['#6A4B1F', '#C59E57', '#F1DCA4', '#B48A42', '#5A3F18'];
const BRASS_POS = [0, 0.3, 0.46, 0.7, 1];

function hash(n: number) {
  const s = Math.sin(n * 12.9898 + 4.1414) * 43758.5453;
  return s - Math.floor(s);
}

/** Marble lobby, the floor dial's face and numerals, and the button panel with unlit buttons. */
export function bakeLobby(s: Scene, fonts: Fonts, scale: number) {
  return bake(s.w, s.h, scale, (c) => {
    const { w, h } = s;
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(linear(0, 0, w * 0.3, h, ['#17312B', '#12271F', '#0E1F19', '#0A1813'], [0, 0.4, 0.75, 1])));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.2, 0.012, 0.02, 51));
    // Marble veins: a few long seeded curves, pale and soft.
    for (let i = 0; i < 7; i += 1) {
      const b = Skia.PathBuilder.Make();
      let x = hash(i) * w;
      let y = -20;
      b.moveTo(x, y);
      while (y < h + 20) {
        const nx = x + (hash(i * 13 + y) - 0.5) * 90;
        const ny = y + 60 + hash(i * 7 + y) * 60;
        b.quadTo(x + (hash(i + y * 3) - 0.5) * 60, (y + ny) / 2, nx, ny);
        x = nx;
        y = ny;
      }
      const vein = paint('#CFE3D8', 0.05 + 0.05 * hash(i * 5));
      vein.setStyle(PaintStyle.Stroke);
      vein.setStrokeWidth(0.8 + hash(i * 9) * 1.4);
      vein.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.8, true));
      c.drawPath(b.build(), vein);
    }
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(radial(w / 2, h * 0.45, h * 0.75, ['rgba(0,0,0,0)', 'rgba(1,6,4,0.25)', 'rgba(1,6,4,0.6)'], [0.4, 0.75, 1])));

    // Sunburst rays behind the dial.
    const ray = paint('#C59E57', 0.22);
    ray.setStrokeWidth(1);
    for (let i = 0; i <= 16; i += 1) {
      const a = (-80 + (160 * i) / 16) * (Math.PI / 180);
      c.drawLine(s.dialX + Math.sin(a) * (s.dialR + 6), s.dialY - Math.cos(a) * (s.dialR + 6), s.dialX + Math.sin(a) * (s.dialR + 26), s.dialY - Math.cos(a) * (s.dialR + 26), ray);
    }
    // Dial: a brass half-moon bezel round a cream enamel face, numerals on the arc.
    const R = s.dialR;
    const half = Skia.PathBuilder.Make();
    half.addArc(Skia.XYWHRect(s.dialX - R - 8, s.dialY - R - 8, (R + 8) * 2, (R + 8) * 2), 180, 180);
    half.close();
    const halfPath = half.build();
    c.save();
    c.translate(0, 8);
    c.drawPath(halfPath, blurred('#000000', 0.5, 10));
    c.restore();
    c.drawPath(halfPath, shaded(linear(s.dialX - R, 0, s.dialX + R, 0, BRASS, BRASS_POS)));
    const face = Skia.PathBuilder.Make();
    face.addArc(Skia.XYWHRect(s.dialX - R, s.dialY - R, R * 2, R * 2), 180, 180);
    face.close();
    c.drawPath(face.build(), shaded(radial(s.dialX, s.dialY - R * 0.6, R * 1.3, ['#F6EDD8', '#EADCBE', '#D5C29E'])));
    const tick = paint('#5A4526', 0.7);
    tick.setStrokeWidth(1);
    tick.setStrokeCap(StrokeCap.Round);
    for (let i = 0; i <= 16; i += 1) {
      const a = (-66 + (132 * i) / 16) * (Math.PI / 180);
      const long = i % 4 === 0;
      const r0 = R - 6;
      const r1 = R - (long ? 16 : 11);
      tick.setStrokeWidth(long ? 1.6 : 0.9);
      c.drawLine(s.dialX + Math.sin(a) * r0, s.dialY - Math.cos(a) * r0, s.dialX + Math.sin(a) * r1, s.dialY - Math.cos(a) * r1, tick);
    }
    for (let f = 1; f <= FLOORS; f += 1) {
      const a = (needleAngle(f) * Math.PI) / 180;
      const label = String(f);
      const r = R - 31;
      const tw = textWidth(fonts.numeral, label);
      c.drawText(label, s.dialX + Math.sin(a) * r - tw / 2, s.dialY - Math.cos(a) * r + 6, paint('#3A2C18'), fonts.numeral);
    }
    c.drawLine(s.dialX - R - 8, s.dialY, s.dialX + R + 8, s.dialY, paint('#3A2C18', 0.5));

    // Button panel: a brass plate with a bevel, five recessed rings, the sections' names.
    const { panelY: py, panelH: ph } = s;
    const plate = Skia.RRectXY(Skia.XYWHRect(s.doorX, py, s.doorW, ph), 18, 18);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.doorX, py + 10, s.doorW, ph), 18, 18), blurred('#000000', 0.5, 14));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.doorX, py + 1.5, s.doorW, ph), 18, 18), blurred('#000000', 0.6, 1.5));
    c.drawRRect(plate, shaded(linear(s.doorX, py, s.doorX + s.doorW * 0.6, py + ph, ['#B08944', '#8C6A31', '#6E5124', '#5A4220'], [0, 0.35, 0.7, 1])));
    c.save();
    c.clipRRect(plate, ClipOp.Intersect, true);
    c.drawRect(Skia.XYWHRect(s.doorX, py, s.doorW, ph), grain(0.3, 0.9, 0.01, 61));
    c.restore();
    const bevel = paint('#FFE7B0', 0.45);
    bevel.setStyle(PaintStyle.Stroke);
    bevel.setStrokeWidth(1);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.doorX + 0.5, py + 0.5, s.doorW - 1, ph - 1), 17.5, 17.5), bevel);
    for (let i = 0; i < FLOORS; i += 1) {
      const x = s.buttonX[i];
      const y = s.buttonY;
      const r = s.buttonR;
      c.drawCircle(x, y + 1, r + 4, blurred('#2A1C08', 0.6, 2));
      c.drawCircle(x, y, r + 3, shaded(linear(x - r, y - r, x + r, y + r, ['#F5E0A8', '#B28B45', '#6B4E1F'])));
      c.drawCircle(x, y, r - 1, paint('#2A2116'));
      const label = STORE[i].name;
      const lw = textWidth(fonts.caps, label.toUpperCase());
      c.drawText(label.toUpperCase(), x - lw / 2, y + r + 22, paint('#2B1E0C', 0.82), fonts.caps);
    }
  });
}

/** One floor of the store, as the doorway shows it. */
export function bakePoster(s: Scene, f: number, fonts: Fonts, scale: number) {
  const fl = STORE[f];
  const W = s.doorW;
  const H = s.doorH;
  return bake(W, H, scale, (c) => {
    c.drawRect(Skia.XYWHRect(0, 0, W, H), shaded(linear(0, 0, W * 0.4, H, fl.colors)));
    c.drawRect(Skia.XYWHRect(0, 0, W, H), shaded(radial(W * 0.3, H * 0.15, H * 0.8, ['rgba(255,240,215,0.18)', 'rgba(255,240,215,0)'])));
    c.drawRect(Skia.XYWHRect(0, 0, W, H), grain(0.12, 1.1, 1.1, 70 + f));
    const ink = '#F7EEDD';
    const pad = 24;
    c.drawText(`FLOOR ${f + 1}`, pad, 40, paint(ink, 0.7), fonts.caps);
    c.drawText(fl.name, pad - 2, 92, paint(ink), fonts.title);
    c.drawText(fl.tagline, pad, 118, paint(ink, 0.75), fonts.body);
    // Three arched display windows with a still life each.
    const gap = 10;
    const tw = (W - pad * 2 - gap * 2) / 3;
    const ty = 146;
    const th = Math.max(110, H - ty - 64);
    for (let i = 0; i < 3; i += 1) {
      const x = pad + i * (tw + gap);
      const arch = Skia.PathBuilder.Make();
      arch.moveTo(x, ty + th);
      arch.lineTo(x, ty + tw / 2);
      arch.arcToOval(Skia.XYWHRect(x, ty, tw, tw), 180, 180, false);
      arch.lineTo(x + tw, ty + th);
      arch.close();
      const path = arch.build();
      c.drawPath(path, shaded(linear(0, ty, 0, ty + th, ['rgba(255,244,226,0.16)', 'rgba(255,244,226,0.08)', 'rgba(0,0,0,0.12)'])));
      const rim = paint('#F7E3B8', 0.35);
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(1);
      c.drawPath(path, rim);
      c.save();
      c.clipPath(path, ClipOp.Intersect, true);
      const cx = x + tw / 2;
      const floorY = ty + th - 18;
      // A plinth and an object: shapes vary by floor and window, lit from the upper left.
      c.drawRect(Skia.XYWHRect(cx - tw * 0.32, floorY - 10, tw * 0.64, 28), shaded(linear(0, floorY - 10, 0, floorY + 18, ['#F2E6D0', '#CDBB9A', '#A8926E'])));
      c.drawOval(Skia.XYWHRect(cx - tw * 0.3, floorY - 16, tw * 0.6, 10), blurred('#000000', 0.25, 4));
      const kind = (f + i) % 4;
      const o = shaded(radial(cx - tw * 0.12, floorY - tw * 0.45, tw * 0.95, ['#FFF7EA', '#E6D2B2', '#A88E6A', fl.colors[2]], [0, 0.35, 0.75, 1]));
      if (kind === 0) c.drawCircle(cx, floorY - tw * 0.27, tw * 0.25, o);
      else if (kind === 1) {
        const v = Skia.PathBuilder.Make();
        const vh = th * 0.5;
        v.moveTo(cx - tw * 0.08, floorY - 10);
        v.cubicTo(cx - tw * 0.36, floorY - vh * 0.45, cx - tw * 0.1, floorY - vh * 0.75, cx - tw * 0.07, floorY - vh);
        v.lineTo(cx + tw * 0.07, floorY - vh);
        v.cubicTo(cx + tw * 0.1, floorY - vh * 0.75, cx + tw * 0.36, floorY - vh * 0.45, cx + tw * 0.08, floorY - 10);
        v.close();
        c.drawPath(v.build(), o);
      } else if (kind === 2) {
        for (let k = 0; k < 3; k += 1) {
          const r = tw * (0.3 - k * 0.07);
          c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - r, floorY - 10 - (k + 1) * th * 0.13, r * 2, th * 0.13), 4, 4), o);
        }
      } else {
        c.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - tw * 0.13, floorY - th * 0.55, tw * 0.26, th * 0.55 - 10), tw * 0.13, tw * 0.13), o);
        c.drawCircle(cx, floorY - th * 0.55, tw * 0.16, o);
      }
      c.restore();
      const item = fl.items[i];
      c.drawText(item.name, x, ty + th + 22, paint(ink, 0.92), fonts.item);
      c.drawText(item.price, x, ty + th + 40, paint(ink, 0.6), fonts.price);
    }
  });
}

/** One brushed-steel door (left or right), its vision panel cut out. */
export function bakeDoor(s: Scene, left: boolean, scale: number) {
  const W = s.doorW / 2;
  const H = s.doorH;
  return bake(W, H, scale, (c) => {
    c.drawRect(Skia.XYWHRect(0, 0, W, H), shaded(linear(0, 0, W, 0, ['#7E858B', '#B6BCC1', '#9DA4AA', '#868D93'], [0, 0.35, 0.7, 1])));
    // Brushing: long horizontal streaks.
    c.drawRect(Skia.XYWHRect(0, 0, W, H), grain(0.5, 0.004, 0.9, left ? 81 : 83));
    // Deco engraving: nested chevrons in the middle, a brass trim on the meeting edge.
    const edgeX = left ? W : 0;
    const dir = left ? -1 : 1;
    const eng = paint('#4E555B', 0.55);
    eng.setStyle(PaintStyle.Stroke);
    eng.setStrokeWidth(1);
    const hi = paint('#FFFFFF', 0.25);
    hi.setStyle(PaintStyle.Stroke);
    hi.setStrokeWidth(1);
    for (let k = 0; k < 4; k += 1) {
      const b = Skia.PathBuilder.Make();
      const y = H * 0.56 + k * 12;
      b.moveTo(edgeX + dir * 22, y);
      b.lineTo(edgeX + dir * (W * 0.55), y - 26);
      b.lineTo(edgeX + dir * (W - 18), y - 26);
      c.drawPath(b.build(), eng);
      c.save();
      c.translate(0, 1);
      c.drawPath(b.build(), hi);
      c.restore();
    }
    c.drawRect(Skia.XYWHRect(left ? W - 5 : 0, 0, 5, H), shaded(linear(left ? W - 5 : 0, 0, left ? W : 5, 0, BRASS, BRASS_POS)));
    c.drawRect(Skia.XYWHRect(left ? 0 : W - 1, 0, 1, H), paint('#2C3135', 0.8));
    // Vision panel: a tall slot near the meeting edge.
    const vx = left ? W - 30 : 14;
    const vy = H * 0.12;
    const vh = H * 0.34;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(vx - 3, vy - 3, 22, vh + 6), 4, 4), shaded(linear(vx - 3, 0, vx + 19, 0, BRASS, BRASS_POS)));
    const hole = Skia.Paint();
    hole.setBlendMode(BlendMode.Clear);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(vx, vy, 16, vh), 2, 2), hole);
  });
}

/** The doors' vision panels, relative to each door (for the glass tint). */
function visionRects(s: Scene) {
  'worklet';
  const W = s.doorW / 2;
  return { lx: W - 30, rx: 14, y: s.doorH * 0.12, h: s.doorH * 0.34 };
}

/** Where the frame image sits (it only covers what it draws). */
export function frameRect(s: Scene) {
  'worklet';
  return { x: s.doorX - 60, y: s.doorY - 50, w: s.doorW + 120, h: s.doorH + 110 };
}

/** The brass surround of the doorway, transparent inside. */
export function bakeFrame(s: Scene, scale: number) {
  const r = frameRect(s);
  return bake(r.w, r.h, scale, (c) => {
    c.translate(-r.x, -r.y);
    const x = s.doorX;
    const y = s.doorY;
    const w = s.doorW;
    const h = s.doorH;
    const t = 10;
    const outer = Skia.RRectXY(Skia.XYWHRect(x - t, y - t, w + t * 2, h + t * 2), 8, 8);
    const inner = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 2, 2);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - t, y - t + 12, w + t * 2, h + t * 2), 8, 8), blurred('#000000', 0.5, 14));
    c.drawDRRect(outer, inner, shaded(linear(x, y, x + w, y + h, BRASS, BRASS_POS)));
    const line = paint('#FFF0C8', 0.4);
    line.setStyle(PaintStyle.Stroke);
    line.setStrokeWidth(1);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - t + 0.5, y - t + 0.5, w + t * 2 - 1, h + t * 2 - 1), 7.5, 7.5), line);
    const dark = paint('#2A1D0A', 0.7);
    dark.setStyle(PaintStyle.Stroke);
    dark.setStrokeWidth(1.5);
    c.drawRRect(inner, dark);
    // An inner shadow where the frame meets the doorway.
    c.save();
    c.clipRRect(inner, ClipOp.Intersect, true);
    const sh = blurred('#000000', 0.55, 6);
    sh.setStyle(PaintStyle.Stroke);
    sh.setStrokeWidth(8);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 3, y - 3, w + 6, h + 6), 4, 4), sh);
    c.restore();
    // Stepped lintel ornament at the top centre.
    for (let k = 0; k < 3; k += 1) {
      const sw = 70 - k * 20;
      c.drawRect(Skia.XYWHRect(s.w / 2 - sw / 2, y - t - 6 - k * 5, sw, 6), shaded(linear(s.w / 2 - sw / 2, 0, s.w / 2 + sw / 2, 0, BRASS, BRASS_POS)));
    }
  });
}

/** Posters other than the first are baked after the first paint, so one may still be null. */
export type Layers = { lobby: SkImage; posters: (SkImage | null)[]; doorL: SkImage; doorR: SkImage; frame: SkImage };

function drawLantern(c: SkCanvas, x: number, y: number, up: boolean, glow: number) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const d = up ? -1 : 1;
  b.moveTo(x, y + d * 9);
  b.lineTo(x - 10, y - d * 7);
  b.lineTo(x + 10, y - d * 7);
  b.close();
  const path = b.build();
  c.drawPath(path, paint('#3A2E1E'));
  if (glow > 0.01) {
    const g = blurred('#FFB547', 0.9 * glow, 9);
    g.setBlendMode(BlendMode.Plus);
    c.drawCircle(x, y, 18, g);
    c.drawPath(path, paint('#FFD58A', glow));
  }
  const rim = paint('#C59E57');
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.2);
  c.drawPath(path, rim);
}

/** One frame. */
export function drawFrame(c: SkCanvas, s: Scene, layers: Layers, pose: Pose, fonts: Fonts) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, s.w, s.h);
  const ip = Skia.Paint();
  drawImg(c, layers.lobby, Skia.XYWHRect(0, 0, layers.lobby.width(), layers.lobby.height()), full, ip);
  const door = Skia.XYWHRect(s.doorX, s.doorY, s.doorW, s.doorH);

  // The store behind the doorway: each floor a poster stacked by height, moving with the car.
  c.save();
  c.clipRect(door, ClipOp.Intersect, true);
  const step = s.doorH + 30;
  const blur = Math.min(16, pose.speed * 9);
  const pp = Skia.Paint();
  if (blur > 0.3) pp.setImageFilter(Skia.ImageFilter.MakeBlur(0.01, blur, TileMode.Clamp, null));
  for (let f = 0; f < layers.posters.length; f += 1) {
    const y = s.doorY + (pose.car - (f + 1)) * step;
    if (y > s.doorY + s.doorH || y + s.doorH < s.doorY) continue;
    const img = layers.posters[f];
    if (!img) {
      c.drawRect(Skia.XYWHRect(s.doorX, y, s.doorW, s.doorH), paint(STORE[f].colors[1]));
      continue;
    }
    drawImg(c, img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(s.doorX, y, s.doorW, s.doorH), pp);
  }
  // The shaft between floors: a dark gap with a brass floor sill.
  for (let f = 0; f < layers.posters.length - 1; f += 1) {
    const y = s.doorY + (pose.car - (f + 1)) * step - 30;
    if (y > s.doorY + s.doorH || y + 30 < s.doorY) continue;
    c.drawRect(Skia.XYWHRect(s.doorX, y, s.doorW, 30), paint('#0B0D0C'));
    c.drawRect(Skia.XYWHRect(s.doorX, y + 26, s.doorW, 4), shaded(linear(0, y + 26, 0, y + 30, BRASS, BRASS_POS)));
  }
  // The doors: open, each hides behind its jamb; closed, they meet in the middle.
  const half = s.doorW / 2;
  const travel = (1 - pose.doors) * (half + 6);
  if (pose.doors > 0.002) {
    const lx = s.doorX - travel;
    const rx = s.doorX + half + travel;
    c.drawRect(Skia.XYWHRect(lx + half - 2, s.doorY, 10, s.doorH), blurred('#000000', 0.35, 6));
    for (const [img, x] of [
      [layers.doorL, lx],
      [layers.doorR, rx],
    ] as const) {
      drawImg(c, img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x, s.doorY, half, s.doorH), ip);
    }
    // Glass in the vision panels: a smoky tint with a sliver of reflection.
    const v = visionRects(s);
    for (const gx of [lx + v.lx, rx + v.rx]) {
      c.drawRect(Skia.XYWHRect(gx, s.doorY + v.y, 16, v.h), paint('#1A2A26', 0.35));
      c.drawRect(Skia.XYWHRect(gx + 3, s.doorY + v.y, 3, v.h), paint('#FFFFFF', 0.12));
    }
    // A broad highlight fixed in the room, so it sweeps across the steel as the doors move.
    const spec = Skia.Paint();
    spec.setShader(linear(s.doorX + s.doorW * 0.2, 0, s.doorX + s.doorW * 0.6, 0, ['rgba(255,255,255,0)', 'rgba(255,255,255,0.22)', 'rgba(255,255,255,0)']));
    c.save();
    c.clipRect(Skia.XYWHRect(lx, s.doorY, half, s.doorH), ClipOp.Intersect, true);
    c.drawRect(door, spec);
    c.restore();
    c.save();
    c.clipRect(Skia.XYWHRect(rx, s.doorY, half, s.doorH), ClipOp.Intersect, true);
    c.drawRect(door, spec);
    c.restore();
  }
  c.restore();
  const frameAt = frameRect(s);
  drawImg(c, layers.frame, Skia.XYWHRect(0, 0, layers.frame.width(), layers.frame.height()), Skia.XYWHRect(frameAt.x, frameAt.y, frameAt.w, frameAt.h), ip);

  // Needle, with its shadow on the enamel and a brass cap.
  const a = (needleAngle(pose.needle) * Math.PI) / 180;
  const R = s.dialR;
  const tipX = s.dialX + Math.sin(a) * (R - 12);
  const tipY = s.dialY - Math.cos(a) * (R - 12);
  const sh = blurred('#000000', 0.35, 2);
  sh.setStrokeWidth(3);
  sh.setStrokeCap(StrokeCap.Round);
  c.drawLine(s.dialX + 2, s.dialY + 3, tipX + 2, tipY + 3, sh);
  const needle = paint('#1E1812');
  needle.setStrokeWidth(2.6);
  needle.setStrokeCap(StrokeCap.Round);
  c.drawLine(s.dialX - Math.sin(a) * 10, s.dialY + Math.cos(a) * 10, tipX, tipY, needle);
  c.drawCircle(s.dialX, s.dialY, 8, shaded(radial(s.dialX - 2, s.dialY - 2, 9, ['#F7E4B0', '#B68C42', '#5E4219'])));
  // Hall lanterns either side of the dial.
  drawLantern(c, s.dialX - R - 30, s.dialY - 22, true, pose.ding[0]);
  drawLantern(c, s.dialX + R + 30, s.dialY - 22, false, pose.ding[1]);
  // A ring of light from the dial when the bell rings.
  const bell = Math.max(pose.ding[0], pose.ding[1]);
  if (bell > 0.01) {
    const ring = paint('#FFC867', 0.5 * bell);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(2);
    ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
    ring.setBlendMode(BlendMode.Plus);
    c.drawCircle(s.dialX, s.dialY, R + 14 + (1 - bell) * 40, ring);
  }

  // Buttons: glass discs that dip when pressed and glow amber while their floor is called.
  for (let i = 0; i < FLOORS; i += 1) {
    const x = s.buttonX[i];
    const y = s.buttonY;
    const lit = clamp01(pose.lit[i]);
    const r = (s.buttonR - 3) * (1 - 0.06 * pose.dip[i]);
    if (lit > 0.01) {
      const halo = blurred('#FFB547', 0.6 * lit, 12);
      halo.setBlendMode(BlendMode.Plus);
      c.drawCircle(x, y, s.buttonR + 8, halo);
    }
    const unlit = ['#EFE6D4', '#D4C6AA', '#A8977A'];
    const on = ['#FFF4D6', '#FFC66B', '#E08A2E'];
    const by = y + 1.5 * pose.dip[i];
    c.drawCircle(x, by, r, shaded(radial(x - r * 0.35, y - r * 0.4, r * 1.6, unlit)));
    if (lit > 0.01) {
      const glass = shaded(radial(x - r * 0.35, y - r * 0.4, r * 1.6, on));
      glass.setAlphaf(lit);
      c.drawCircle(x, by, r, glass);
    }
    c.drawOval(Skia.XYWHRect(x - r * 0.6, y - r * 0.8, r * 0.9, r * 0.45), paint('#FFFFFF', 0.35));
    const label = String(i + 1);
    const tw = textWidth(fonts.button, label);
    c.drawText(label, x - tw / 2, y + 7 + 1.5 * pose.dip[i], paint(lit > 0.5 ? '#5A2E05' : '#3A2C18'), fonts.button);
  }
}

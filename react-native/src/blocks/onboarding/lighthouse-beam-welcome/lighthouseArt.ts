import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
} from '@shopify/react-native-skia';

import { type Geo, PAGES } from './lighthouseMotion';

/*
 Drawing for Lighthouse Beam Welcome: the tower on its rocks (recorded once into a Picture), the
 three pages of onboarding text drawn once into an image the shader reveals, and the per-frame
 bits (the lamp's glow and flare, the paging dots).
 */

export const PAGE_COPY = [
  {
    kicker: 'SAFE PASSAGE',
    title: 'Light the way home',
    body: 'Safe routes around shoals and wrecks, even in fog.',
  },
  {
    kicker: 'ONE-TAP SOS',
    title: 'Raise the alarm fast',
    body: 'One tap sends your position to every boat nearby.',
  },
  {
    kicker: 'TIDES & SWELL',
    title: 'Read the water first',
    body: 'Live tide, wind and swell from 1,200 stations.',
  },
];

export type Fonts = { kicker: SkFont; title: SkFont; body: SkFont };

function paint(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function line(color: string, width: number) {
  'worklet';
  const p = paint(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

function widthOf(font: SkFont, text: string) {
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (const w of widths) sum += w;
  return sum;
}

/** Greedy word wrap with measured glyph widths. */
function wrap(font: SkFont, text: string, max: number) {
  const words = text.split(' ');
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    const next = current ? `${current} ${word}` : word;
    if (current && widthOf(font, next) > max) {
      lines.push(current);
      current = word;
    } else {
      current = next;
    }
  }
  if (current) lines.push(current);
  return lines;
}

/** The page icons, 30 pt boxes: a compass rose, a life ring, a tide wave. */
function icon(c: SkCanvas, kind: number, x: number, y: number) {
  const ink = line('#FFFFFF', 2);
  const cx = x + 15;
  const cy = y + 15;
  if (kind === 0) {
    c.drawCircle(cx, cy, 13, ink);
    const needle = Skia.PathBuilder.Make();
    needle.moveTo(cx, cy - 9);
    needle.lineTo(cx + 4, cy);
    needle.lineTo(cx, cy + 9);
    needle.lineTo(cx - 4, cy);
    needle.close();
    c.drawPath(needle.build(), paint('#FFFFFF'));
  } else if (kind === 1) {
    c.drawCircle(cx, cy, 13, ink);
    c.drawCircle(cx, cy, 6, ink);
    const bands = line('#FFFFFF', 5);
    for (let i = 0; i < 4; i += 1) {
      const arc = Skia.PathBuilder.Make();
      arc.addArc(Skia.XYWHRect(cx - 9.5, cy - 9.5, 19, 19), 45 + i * 90 - 14, 28);
      c.drawPath(arc.build(), bands);
    }
  } else {
    for (let r = 0; r < 2; r += 1) {
      const wave = Skia.PathBuilder.Make();
      const wy = cy - 2 + r * 8;
      wave.moveTo(x + 1, wy);
      for (let i = 0; i < 3; i += 1) {
        const sx = x + 1 + i * 9.4;
        wave.cubicTo(sx + 2.4, wy - 5, sx + 4.7, wy - 5, sx + 4.7, wy);
        wave.cubicTo(sx + 4.7, wy + 5, sx + 7, wy + 5, sx + 9.4, wy);
      }
      c.drawPath(wave.build(), ink);
    }
    c.drawCircle(cx + 7, y + 3, 3, ink);
  }
}

/**
 * The three pages side by side (page i starts at i × width), white on transparent, at the screen's
 * pixel ratio. The shader reads only its alpha.
 */
export function buildContent(g: Geo, fonts: Fonts, ratio: number): SkImage | null {
  const pw = Math.ceil(g.w * PAGES * ratio);
  const ph = Math.ceil(g.contentH * ratio);
  const surface = Skia.Surface.Make(pw, ph);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.clear(Skia.Color('transparent'));
  c.scale(ratio, ratio);
  const white = paint('#FFFFFF');
  const soft = paint('rgba(255,255,255,0.78)');
  for (let i = 0; i < PAGES; i += 1) {
    const copy = PAGE_COPY[i];
    const x = i * g.w + g.textX;
    let y = g.textY - g.contentY;
    icon(c, i, x, y);
    c.drawText(`0${i + 1}  ·  ${copy.kicker}`, x + 40, y + 19, soft, fonts.kicker);
    y += 40;
    for (const text of wrap(fonts.title, copy.title, g.textW)) {
      y += fonts.title.getSize() * 1.18;
      c.drawText(text, x, y, white, fonts.title);
    }
    y += 6;
    for (const text of wrap(fonts.body, copy.body, g.textW)) {
      y += fonts.body.getSize() * 1.4;
      c.drawText(text, x, y, soft, fonts.body);
    }
  }
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** The lighthouse on its rocks, seen at dusk: rim light from the sky on the right, lamp-lit top. */
export function drawTower(c: SkCanvas, g: Geo) {
  const u = g.u;
  const x = g.lampX;
  const baseW = 25 * u;
  const topW = 15 * u;
  const top = g.galleryY + 6 * u;
  const base = g.baseY;

  // Rocks: a low dark mass with a lighter top edge.
  const rocks = Skia.PathBuilder.Make();
  rocks.moveTo(x - 92 * u, g.horizon + 34 * u);
  rocks.cubicTo(x - 80 * u, g.horizon + 4 * u, x - 52 * u, g.horizon - 6 * u, x - 30 * u, base - 8 * u);
  rocks.cubicTo(x - 10 * u, base - 16 * u, x + 22 * u, base - 14 * u, x + 38 * u, base - 4 * u);
  rocks.cubicTo(x + 58 * u, g.horizon + 6 * u, x + 78 * u, g.horizon + 18 * u, x + 96 * u, g.horizon + 34 * u);
  rocks.close();
  const rockPaint = paint('#10152E');
  rockPaint.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x, base - 16 * u),
      Skia.Point(x, g.horizon + 34 * u),
      ['#2A2B4D', '#141936', '#0A0E22'].map((h) => Skia.Color(h)),
      [0, 0.4, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(rocks.build(), rockPaint);

  // Tower: tapered, shaded as a cylinder (dark left, dusk-lit right edge), two dim red bands.
  const tower = Skia.PathBuilder.Make();
  tower.moveTo(x - baseW, base);
  tower.lineTo(x - topW, top);
  tower.lineTo(x + topW, top);
  tower.lineTo(x + baseW, base);
  tower.close();
  const body = paint('#3A3C5E');
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x - baseW, 0),
      Skia.Point(x + baseW, 0),
      ['#1E2140', '#45466C', '#6E6A92', '#8D7FA6'].map((h) => Skia.Color(h)),
      [0, 0.45, 0.82, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(tower.build(), body);
  c.save();
  c.clipPath(tower.build(), ClipOp.Intersect, true);
  const band = paint('rgba(150,52,72,0.55)');
  const span = base - top;
  for (const f of [0.22, 0.56]) c.drawRect(Skia.XYWHRect(x - baseW - 2, top + span * f, baseW * 2 + 4, span * 0.14), band);
  // Lamp light falling down the tower from the gallery.
  const wash = paint('#FFFFFF');
  wash.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, top),
      Skia.Point(0, top + span * 0.4),
      [Skia.Color('rgba(255,214,150,0.28)'), Skia.Color('rgba(255,214,150,0)')],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(x - baseW, top, baseW * 2, span * 0.4), wash);
  c.restore();
  // Two small lit windows.
  for (const f of [0.42, 0.74]) {
    const wy = top + span * f;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 2.2 * u, wy, 4.4 * u, 7 * u), 2 * u, 2 * u), paint('rgba(255,206,130,0.85)'));
  }

  // Gallery deck and railing.
  const deckY = g.galleryY;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 23 * u, deckY + 3 * u, 46 * u, 4 * u), 2 * u, 2 * u), paint('#24264A'));
  const rail = line('rgba(170,160,205,0.75)', 1.1 * u);
  const railTop = Skia.PathBuilder.Make();
  railTop.moveTo(x - 21 * u, deckY - 4 * u);
  railTop.lineTo(x + 21 * u, deckY - 4 * u);
  c.drawPath(railTop.build(), rail);
  for (let i = 0; i <= 6; i += 1) {
    const rx = x - 21 * u + i * 7 * u;
    const post = Skia.PathBuilder.Make();
    post.moveTo(rx, deckY - 4 * u);
    post.lineTo(rx, deckY + 3 * u);
    c.drawPath(post.build(), rail);
  }

  // Lantern room: glass panes around the lamp, then the cap and a vane.
  const roomTop = g.lampY - 13 * u;
  c.drawRect(Skia.XYWHRect(x - 11 * u, roomTop, 22 * u, deckY - roomTop - 4 * u), paint('rgba(255,222,160,0.18)'));
  const mullion = line('rgba(30,32,60,0.9)', 1.4 * u);
  for (const dx of [-11, -4, 4, 11]) {
    const m = Skia.PathBuilder.Make();
    m.moveTo(x + dx * u, roomTop);
    m.lineTo(x + dx * u, deckY - 4 * u);
    c.drawPath(m.build(), mullion);
  }
  const cap = Skia.PathBuilder.Make();
  cap.moveTo(x - 15 * u, roomTop);
  cap.cubicTo(x - 12 * u, roomTop - 12 * u, x + 12 * u, roomTop - 12 * u, x + 15 * u, roomTop);
  cap.close();
  const capPaint = paint('#262848');
  capPaint.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x - 15 * u, 0),
      Skia.Point(x + 15 * u, 0),
      ['#15172F', '#2E3056', '#6A5F8C'].map((h) => Skia.Color(h)),
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(cap.build(), capPaint);
  const vane = line('#2A2C4E', 1.4 * u);
  const v = Skia.PathBuilder.Make();
  v.moveTo(x, roomTop - 9 * u);
  v.lineTo(x, roomTop - 18 * u);
  c.drawPath(v.build(), vane);
  c.drawCircle(x, roomTop - 18 * u, 1.6 * u, paint('#2A2C4E'));
}

/** The lamp's steady glow and its flare when the beam swings towards the viewer. */
export function drawLamp(c: SkCanvas, g: Geo, flare: number) {
  'worklet';
  const u = g.u;
  const glow = paint('rgba(255,224,160,0.9)');
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5 * u, true));
  c.drawCircle(g.lampX, g.lampY, 5.5 * u, glow);
  c.drawCircle(g.lampX, g.lampY, 3 * u, paint('#FFF6E2'));
  if (flare > 0.01) {
    const halo = paint('#FFFFFF');
    const r = (40 + 90 * flare) * u;
    halo.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(g.lampX, g.lampY),
        r,
        [Skia.Color('rgba(255,236,196,0.95)'), Skia.Color('rgba(255,214,150,0.35)'), Skia.Color('rgba(255,200,140,0)')],
        [0, 0.25, 1],
        TileMode.Clamp,
      ),
    );
    halo.setAlphaf(Math.min(1, flare * 1.1));
    c.drawCircle(g.lampX, g.lampY, r, halo);
    // A short horizontal streak, like a lens flare.
    const streak = paint('rgba(255,236,200,0.6)');
    streak.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2 * u, false));
    streak.setAlphaf(flare * 0.7);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.lampX - r * 1.4, g.lampY - 1.2 * u, r * 2.8, 2.4 * u), u, u), streak);
  }
}

/** Paging dots: the active one is a pill; `page` is fractional while sliding. */
export function drawDots(c: SkCanvas, cx: number, y: number, page: number) {
  'worklet';
  const gap = 8;
  const dot = 7;
  const wide = 22;
  let total = 0;
  const widths: number[] = [];
  for (let i = 0; i < PAGES; i += 1) {
    const near = Math.max(0, 1 - Math.abs(page - i));
    const wdt = dot + (wide - dot) * near;
    widths.push(wdt);
    total += wdt + (i > 0 ? gap : 0);
  }
  let x = cx - total / 2;
  for (let i = 0; i < PAGES; i += 1) {
    const near = Math.max(0, 1 - Math.abs(page - i));
    const p = paint('#F2E6C8');
    p.setAlphaf(0.32 + 0.63 * near);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y - dot / 2, widths[i], dot), dot / 2, dot / 2), p);
    x += widths[i] + gap;
  }
}

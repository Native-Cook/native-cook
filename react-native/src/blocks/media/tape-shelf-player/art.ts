import { BlurStyle, ClipOp, type SkCanvas, type SkFont, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';

import { type Pose, clamp01, easeOut, hash, smoothstep } from './motion';

/** The tapes, the cassette, the player and the home video on its screen, drawn in code. Ported from TapeShelfPlayerArt.swift. */

export const PAGE = '#F4F2EE';
export const INK = '#141414';
export const MAGENTA = '#C4125A';

export type Tape = { label: string; brand: string; color: number; brandInk: number; scene: number[] };

export const TAPES: Tape[] = [
  { label: "Summer '98", brand: 'KORAL', color: 0xe5352f, brandInk: 0xffffff, scene: [0xff8a5b, 0xffd08a, 0xfff3c4, 0x2c6e8f] },
  { label: 'Lake house', brand: 'STRATA', color: 0xf2ebd3, brandInk: 0x1c1c1c, scene: [0x7fb7d9, 0xd9eef7, 0xffffff, 0x2f5d50] },
  { label: 'First snow', brand: 'NOVAVIEW', color: 0x1f2f6e, brandInk: 0xffffff, scene: [0xb8c4d6, 0xe9eef5, 0xfff7e0, 0xc9d2df] },
  { label: 'Road trip', brand: 'SOLACE', color: 0x9b87c8, brandInk: 0xffffff, scene: [0xf2a65a, 0xf7d9a8, 0xfff1c9, 0x7a5a3a] },
  { label: 'Sunset pier', brand: 'DRIFT', color: 0x1a1a1c, brandInk: 0xffffff, scene: [0x5b3a8c, 0xf28c8c, 0xffd58a, 0x2a2350] },
  { label: "Nana's 70th", brand: 'STRATA HQ', color: 0xe6efc4, brandInk: 0x1e5e3a, scene: [0xe9c46a, 0xf4e3b5, 0xffffff, 0xa0522d] },
  { label: 'Beach day', brand: 'KORAL', color: 0xf08a24, brandInk: 0xffffff, scene: [0x4fc3f7, 0xb3e5fc, 0xfff59d, 0x0277bd] },
  { label: 'New puppy', brand: 'NOVAVIEW', color: 0x2f7f86, brandInk: 0xffffff, scene: [0xa5d6a7, 0xe8f5e9, 0xffffff, 0x558b2f] },
  { label: 'Night drive', brand: 'SOLACE', color: 0xc9a27a, brandInk: 0x2a1a0e, scene: [0x0b1026, 0x2b2f77, 0xf5f3ce, 0x05060f] },
];
const RAINBOW = [0xe53935, 0xfb8c00, 0xfdd835, 0x43a047, 0x1e88e5];

export function hexA(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

function white(a: number) {
  'worklet';
  return Float32Array.of(1, 1, 1, a);
}

function black(a: number) {
  'worklet';
  return Float32Array.of(0, 0, 0, a);
}

export type Rect = { x: number; y: number; w: number; h: number };

/** The screen's geometry, from its size and safe area. */
export type Geo = {
  w: number;
  h: number;
  top: number;
  bottom: number;
  shelfTop: number;
  shelfBottom: number;
  body: Rect;
  screen: Rect;
  slot: Rect;
  gridTop: number;
  cellW: number;
  cellH: number;
  cassetteL: number;
  cassetteT: number;
  pillY: number;
  transportY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const shelfTop = top + 44;
  const shelfBottom = shelfTop + 226;
  const body = { x: (w - 0.52 * w) / 2, y: top + 58, w: 0.52 * w, h: 188 };
  const screen = { x: body.x + 14, y: body.y + 14, w: body.w - 28, h: 112 };
  const sw = 0.62 * body.w;
  const slot = { x: w / 2 - sw / 2, y: screen.y + screen.h + 20, w: sw, h: 9 };
  const cellW = (w - 48 - 28) / 3;
  const cellH = cellW * 1.3;
  const cassetteL = cellH * 0.88;
  return {
    w,
    h,
    top,
    bottom,
    shelfTop,
    shelfBottom,
    body,
    screen,
    slot,
    gridTop: shelfBottom + 58,
    cellW,
    cellH,
    cassetteL,
    cassetteT: cassetteL / 1.83,
    pillY: h - Math.max(bottom, 16) - 12 - 44,
    transportY: h - Math.max(bottom, 16) - 8 - 60,
  };
}

/** A string's advance. SkFont.getTextWidth rounds every glyph's advance to whole points, which puts centred and right-aligned text off by several points; the glyph widths are exact. */
export function textWidth(font: SkFont, s: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

export function cell(g: Geo, i: number): Rect {
  'worklet';
  return { x: 24 + (i % 3) * (g.cellW + 14), y: g.gridTop + Math.floor(i / 3) * (g.cellH + 14), w: g.cellW, h: g.cellH };
}

export type Fonts = { hand: SkFont; handSmall: SkFont; brand: SkFont; small: SkFont; badge: SkFont; mono9: SkFont; mono8: SkFont };

function paintOf(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

/**
 * Text placed like SwiftUI's anchors: alignX 0 left, 1 centre, 2 right; anchorY 0 centre, 1 top, 2 bottom. A line
 * wider than maxWidth is drawn smaller, about (x, y), so that it fits.
 */
function text(
  canvas: SkCanvas,
  s: string,
  x: number,
  y: number,
  font: SkFont,
  color: Float32Array,
  alignX: number,
  anchorY: number,
  maxWidth = Infinity,
) {
  'worklet';
  const m = font.getMetrics();
  const width = textWidth(font, s);
  const left = alignX === 0 ? x : alignX === 1 ? x - width / 2 : x - width;
  const baseline = anchorY === 0 ? y - (m.ascent + m.descent) / 2 : anchorY === 1 ? y - m.ascent : y - m.descent;
  const k = width > maxWidth && width > 0 ? maxWidth / width : 1;
  if (k < 1) {
    canvas.save();
    canvas.translate(x, y);
    canvas.scale(k, k);
    canvas.translate(-x, -y);
  }
  canvas.drawText(s, left, baseline, paintOf(color), font);
  if (k < 1) canvas.restore();
}

function softRect(canvas: SkCanvas, r: Rect, radius: number, sigma: number, alpha: number) {
  'worklet';
  const p = paintOf(black(alpha));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(r.x, r.y, r.w, r.h), radius, radius), p);
}

// Tapes on the shelf.

/** Sleeve i drawn in its own square (0, 0, w, h), recorded once. */
function drawSleeveFace(canvas: SkCanvas, i: number, w: number, h: number, fonts: Fonts) {
  const t = TAPES[i];
  const box = Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 4, 4);
  canvas.drawRRect(box, paintOf(hexA(t.color)));
  canvas.save();
  canvas.clipRRect(box, ClipOp.Intersect, true);
  // Spine.
  canvas.drawRect(Skia.XYWHRect(0, 0, w * 0.11, h), paintOf(black(0.18)));
  // Brand and length.
  text(canvas, t.brand, w * 0.17, h * 0.05, fonts.brand, hexA(t.brandInk), 0, 1);
  text(canvas, 'T-120', w * 0.17, h * 0.05 + w * 0.17, fonts.small, hexA(t.brandInk, 0.75), 0, 1);
  // Rainbow band.
  RAINBOW.forEach((hex, k) => canvas.drawRect(Skia.XYWHRect(w * 0.11, h * (0.66 + k * 0.026), w * 0.89, h * 0.026), paintOf(hexA(hex))));
  // The sticker with the handwritten title.
  canvas.save();
  canvas.translate(w / 2 + w * 0.04, h * 0.44);
  canvas.rotate(-4, 0, 0);
  const sx = -w * 0.37;
  const sy = -h * 0.12;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx + 0.5, sy + 1, w * 0.74, h * 0.24), 3, 3), paintOf(black(0.12)));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(sx, sy, w * 0.74, h * 0.24), 3, 3), paintOf(hexA(0xfbfaf6)));
  canvas.drawRect(Skia.XYWHRect(sx + 5, sy + h * 0.24 - h * 0.055, w * 0.74 - 10, 0.6), paintOf(hexA(0x8fb5d9)));
  text(canvas, t.label, 0, -h * 0.005, fonts.hand, hexA(0x1d2a6b), 1, 0, w * 0.74 - 8);
  canvas.restore();
  // A small format badge.
  const bx = w - w * 0.27;
  const by = h - h * 0.11;
  const stroke = paintOf(hexA(t.brandInk, 0.7));
  stroke.setStyle(1);
  stroke.setStrokeWidth(0.8);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, by, w * 0.2, h * 0.07), 2, 2), stroke);
  text(canvas, 'VHS', bx + w * 0.1, by + h * 0.035, fonts.badge, hexA(t.brandInk, 0.8), 1, 0);
  // A faint sheen.
  const sheen = Skia.Paint();
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w * 0.6, y: h * 0.5 }, [white(0.14), white(0)], null, TileMode.Clamp));
  canvas.drawRRect(box, sheen);
  canvas.restore();
}

export function makeSleeves(g: Geo, fonts: Fonts): SkPicture[] {
  return TAPES.map((_, i) => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, g.cellW, g.cellH));
    drawSleeveFace(canvas, i, g.cellW, g.cellH, fonts);
    return recorder.finishRecordingAsPicture();
  });
}

export function drawSleeve(canvas: SkCanvas, face: SkPicture, r: Rect, lift: number) {
  'worklet';
  const s = 1 + 0.05 * lift;
  canvas.save();
  canvas.translate(r.x + r.w / 2, r.y + r.h / 2);
  canvas.scale(s, s);
  canvas.translate(-(r.x + r.w / 2), -(r.y + r.h / 2));
  softRect(canvas, { x: r.x, y: r.y + 2 + 8 * lift, w: r.w, h: r.h }, 4, 3 + 8 * lift, 0.16 + 0.12 * lift);
  canvas.translate(r.x, r.y);
  canvas.drawPicture(face);
  canvas.restore();
}

// The cassette.

/** A VHS cassette seen from above, centred at the origin, horizontal, length l and thickness t. */
function drawCassette(canvas: SkCanvas, l: number, t: number, label: string, reels: number, fonts: Fonts) {
  'worklet';
  const body = Skia.RRectXY(Skia.XYWHRect(-l / 2, -t / 2, l, t), l * 0.05, l * 0.05);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: -t / 2 }, { x: 0, y: t / 2 }, [hexA(0x2a2a2e), hexA(0x141416)], null, TileMode.Clamp));
  canvas.drawRRect(body, fill);
  const edge = paintOf(white(0.12));
  edge.setStyle(1);
  edge.setStrokeWidth(1);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-l / 2 + 0.5, -t / 2 + 0.5, l - 1, t - 1), l * 0.05, l * 0.05), edge);
  // Label.
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-l * 0.36, -t * 0.4, l * 0.72, t * 0.32), 2, 2), paintOf(hexA(0xf7f5ee)));
  text(canvas, label, 0, -t * 0.24, fonts.handSmall, hexA(0x1d2a6b), 1, 0, l * 0.72 - 6);
  // Window with the two reels.
  const win = Skia.RRectXY(Skia.XYWHRect(-l * 0.27, 0, l * 0.54, t * 0.34), 3, 3);
  canvas.drawRRect(win, paintOf(hexA(0x0a0a0b)));
  canvas.save();
  canvas.clipRRect(win, ClipOp.Intersect, true);
  const tooth = paintOf(hexA(0x555555));
  tooth.setStyle(1);
  tooth.setStrokeWidth(1);
  [-1, 1].forEach((side, k) => {
    const cx = side * l * 0.15;
    const cy = t * 0.17;
    canvas.drawCircle(cx, cy, t * (k === 0 ? 0.2 : 0.13), paintOf(hexA(0x4a2e1a)));
    const hub = t * 0.07;
    canvas.drawCircle(cx, cy, hub, paintOf(hexA(0xededed)));
    for (let s = 0; s < 6; s++) {
      const a = (s * Math.PI) / 3 + reels;
      canvas.drawLine(cx, cy, cx + Math.cos(a) * hub * 0.9, cy + Math.sin(a) * hub * 0.9, tooth);
    }
  });
  canvas.restore();
  canvas.drawCircle(-l * 0.44, t * 0.3, 1.5, paintOf(white(0.25)));
  canvas.drawCircle(l * 0.44, t * 0.3, 1.5, paintOf(white(0.25)));
}

/** The cassette on its way: beside its sleeve, in flight to the slot, or squashing into it. */
export function drawFlyingCassette(canvas: SkCanvas, p: Pose, g: Geo, fonts: Fonts) {
  'worklet';
  if (p.cassetteAlpha <= 0.001) return;
  const box = cell(g, p.tape);
  const l = g.cassetteL;
  const t = g.cassetteT;
  const k = g.slot.w / l;
  const slotMid = g.slot.y + g.slot.h / 2;
  const p0x = box.x + box.w / 2 + p.slide * g.cellW * 0.62;
  const p0y = box.y + box.h / 2;
  const p2x = g.w / 2;
  const p2y = slotMid - (t * k) / 2 - 4;
  const p1x = (p0x + p2x) / 2 + 40;
  const p1y = Math.min(p0y, p2y) - 70;
  const f = p.flight;
  const x = (1 - f) * (1 - f) * p0x + 2 * (1 - f) * f * p1x + f * f * p2x;
  let y = (1 - f) * (1 - f) * p0y + 2 * (1 - f) * f * p1y + f * f * p2y;
  const lift = Math.sin(Math.PI * f);
  const rot = 90 * (1 - f) + 14 * lift;
  const sc = 1 + (k - 1) * f + 0.12 * lift;
  y += (slotMid - y) * p.squash;
  const sy = sc * (1 - 0.9 * p.squash);
  const layer = Skia.Paint();
  layer.setAlphaf(p.cassetteAlpha);
  canvas.saveLayer(layer);
  // Its shadow, further below while it flies.
  canvas.save();
  canvas.translate(x, y + 4 + 14 * lift);
  canvas.rotate(rot, 0, 0);
  canvas.scale(sc, sy);
  softRect(canvas, { x: -l / 2, y: -t / 2, w: l, h: t }, l * 0.05, 4 + 8 * lift, 0.3);
  canvas.restore();
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(rot, 0, 0);
  canvas.scale(sc, sy);
  drawCassette(canvas, l, t, TAPES[p.tape].label, f * 2 * Math.PI, fonts);
  canvas.restore();
  canvas.restore();
}

// The player.

/** The home video: a sky, a sun, the water with moving glints, the camcorder's OSD and a rolling tracking band. */
function drawVideo(canvas: SkCanvas, r: Rect, tape: number, time: number, fonts: Fonts) {
  'worklet';
  const sc = TAPES[tape].scene;
  const horizon = r.y + r.h * 0.62;
  const sky = Skia.Paint();
  sky.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: r.y }, { x: 0, y: horizon }, [hexA(sc[0]), hexA(sc[1])], null, TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(r.x, r.y, r.w, r.h), sky);
  // The sun, cut by bands, slowly sinking.
  const sunR = r.h * 0.2;
  const sunX = r.x + r.w * 0.66;
  const sunY = horizon - sunR * 0.55 + 3 * Math.sin(time * 0.6);
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(r.x, r.y, r.w, horizon - r.y), ClipOp.Intersect, true);
  canvas.drawCircle(sunX, sunY, sunR, paintOf(hexA(sc[2])));
  for (let k = 0; k < 3; k++) canvas.drawRect(Skia.XYWHRect(sunX - sunR, sunY + sunR * (0.15 + k * 0.28), 2 * sunR, 1.2 + k), paintOf(hexA(sc[1])));
  canvas.restore();
  // The water, with glints drifting.
  canvas.drawRect(Skia.XYWHRect(r.x, horizon, r.w, r.y + r.h - horizon), paintOf(hexA(sc[3])));
  const glint = paintOf(hexA(sc[2], 0.75));
  for (let k = 0; k < 9; k++) {
    const row = k % 3;
    const y = horizon + 3 + (row * (r.y + r.h - horizon - 6)) / 3 + 2;
    const span = r.w * 0.9;
    const x = r.x + ((k * 37 + time * (14 + row * 6)) % span) + r.w * 0.05;
    const len = 6 + 10 * clamp01(1 - Math.abs(x - sunX) / (r.w * 0.5));
    canvas.drawRect(Skia.XYWHRect(x - len / 2, y, len, 1.2), glint);
  }
  // A dark pier reaching into the water.
  canvas.drawRect(Skia.XYWHRect(r.x, horizon - 3, r.w * 0.34, 3), paintOf(black(0.55)));
  for (let k = 0; k < 4; k++) canvas.drawRect(Skia.XYWHRect(r.x + k * r.w * 0.1 + 4, horizon, 2, 7 + k), paintOf(black(0.5)));
  // The rolling tracking band.
  const bandY = r.y + ((time * 0.35) % 1.3) * r.h - r.h * 0.15;
  const frame = Math.floor(time * 12);
  const noise = paintOf(white(0.35));
  for (let k = 0; k < 7; k++) {
    canvas.drawRect(Skia.XYWHRect(r.x + hash(k, frame) * r.w, bandY + k * 2.2, 10 + 30 * hash(k, frame + 7), 1), noise);
  }
  // OSD.
  text(canvas, time < 2.5 && time % 0.8 < 0.5 ? 'PLAY ▶' : 'PLAY', r.x + 10, r.y + 8, fonts.mono8, hexA(0x9cffb0), 0, 1);
  text(canvas, 'SP', r.x + r.w - 10, r.y + 8, fonts.mono8, white(0.85), 2, 1);
  text(canvas, 'JUL 14 1998', r.x + r.w - 10, r.y + r.h - 8, fonts.mono8, hexA(0xffb347), 2, 2);
}

/** The CRT: idle text, the power line, the home video, scanlines and glass. */
function drawScreen(canvas: SkCanvas, r: Rect, p: Pose, clock: number, still: boolean, fonts: Fonts) {
  'worklet';
  const glass = Skia.RRectXY(Skia.XYWHRect(r.x, r.y, r.w, r.h), 16, 16);
  const cx = r.x + r.w / 2;
  const cy = r.y + r.h / 2;
  canvas.save();
  canvas.clipRRect(glass, ClipOp.Intersect, true);
  canvas.drawRRect(glass, paintOf(hexA(0x0e0f11)));
  if (p.screen <= 0.001) {
    // Idle: a blinking prompt, once a second.
    if (still || clock % 1 < 0.6) text(canvas, 'INSERT TAPE', cx, cy, fonts.mono9, white(0.45), 1, 0);
  } else {
    const u = p.screen;
    const band = still ? r.h : p.powering > 0 ? r.h * easeOut((u - 0.15) / 0.85) : r.h * smoothstep(0.35, 1, u);
    if (band > 0.5) {
      const layer = Skia.Paint();
      layer.setAlphaf(still ? u : 1);
      canvas.saveLayer(layer);
      canvas.clipRect(Skia.XYWHRect(r.x, cy - band / 2, r.w, band), ClipOp.Intersect, true);
      drawVideo(canvas, r, p.tape, p.video, fonts);
      canvas.restore();
    }
    // The power line: a bright horizontal streak growing (on) or shrinking to a dot (off).
    if (!still) {
      if (p.powering > 0 && u < 0.4) {
        const lw = r.w * Math.min(1, u / 0.15);
        canvas.drawRect(Skia.XYWHRect(cx - lw / 2, cy - 1, lw, 2), paintOf(white(1 - u / 0.4)));
      } else if (p.powering < 0 && u < 0.35) {
        const lw = Math.max(3, (r.w * u) / 0.35);
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - lw / 2, cy - 1.5, lw, 3), 1.5, 1.5), paintOf(white(Math.min(1, u / 0.05))));
      }
    }
  }
  // Scanlines, vignette, glass highlight.
  const line = paintOf(black(0.18));
  for (let y = r.y; y < r.y + r.h; y += 3) canvas.drawRect(Skia.XYWHRect(r.x, y, r.w, 1), line);
  const vignette = Skia.Paint();
  vignette.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r.w * 0.72, [black(0), black(0), black(0.45)], [0, 0.4167, 1], TileMode.Clamp));
  canvas.drawRRect(glass, vignette);
  const hi = Skia.Paint();
  hi.setShader(Skia.Shader.MakeLinearGradient({ x: r.x, y: r.y }, { x: r.x + r.w * 0.45, y: cy }, [white(0.14), white(0)], null, TileMode.Clamp));
  canvas.drawRRect(glass, hi);
  canvas.restore();
  const frame = paintOf(black(0.8));
  frame.setStyle(1);
  frame.setStrokeWidth(2);
  canvas.drawRRect(glass, frame);
}

export function drawPlayer(canvas: SkCanvas, g: Geo, p: Pose, clock: number, still: boolean, fonts: Fonts) {
  'worklet';
  canvas.save();
  canvas.translate(0, p.bounce);
  const b = g.body;
  // Shadow on the shelf, then the body.
  softRect(canvas, { x: b.x + 8, y: b.y + 14, w: b.w - 16, h: b.h - 8 }, 26, 14, 0.35);
  const body = Skia.RRectXY(Skia.XYWHRect(b.x, b.y, b.w, b.h), 26, 26);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: b.y }, { x: 0, y: b.y + b.h }, [hexA(0x2b2b2e), hexA(0x19191b)], null, TileMode.Clamp));
  canvas.drawRRect(body, fill);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(1);
  rim.setStrokeWidth(1);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: b.y }, { x: 0, y: b.y + b.h }, [white(0.22), white(0.04)], null, TileMode.Clamp));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x + 0.5, b.y + 0.5, b.w - 1, b.h - 1), 25.5, 25.5), rim);
  drawScreen(canvas, g.screen, p, clock, still, fonts);
  // Slot, LED, buttons.
  const s = g.slot;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.x - 4, s.y - 4, s.w + 8, s.h + 8), 8, 8), paintOf(hexA(0x111113)));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(s.x, s.y, s.w, s.h), 4, 4), paintOf(black(1)));
  canvas.drawRect(Skia.XYWHRect(s.x + 4, s.y + s.h - 1.5, s.w - 8, 1), paintOf(white(0.15)));
  const lx = b.x + b.w - 22;
  const ly = b.y + b.h - 20;
  const led = p.led > 0.5 ? 0x3be37a : 0xff3b5c;
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: lx, y: ly }, 10, [hexA(led, 0.7), hexA(led, 0)], null, TileMode.Clamp));
  canvas.drawCircle(lx, ly, 10, glow);
  canvas.drawCircle(lx, ly, 3.5, paintOf(hexA(led)));
  const ring = paintOf(white(0.12));
  ring.setStyle(1);
  ring.setStrokeWidth(0.8);
  for (let k = 0; k < 3; k++) {
    const bx = b.x + 22 + k * 16;
    canvas.drawCircle(bx, ly, 5, paintOf(hexA(0x0f0f11)));
    canvas.drawCircle(bx, ly, 4.6, ring);
  }
  canvas.restore();
}

/** The transport glyphs, about 16 points, centred on (cx, cy). */
export function drawTransportIcon(canvas: SkCanvas, i: number, paused: boolean, cx: number, cy: number, alpha: number) {
  'worklet';
  const ink = paintOf(white(alpha));
  const tri = (x0: number, x1: number) => {
    const path = Skia.Path.Make();
    path.moveTo(cx + x0, cy - 6.5);
    path.lineTo(cx + x1, cy);
    path.lineTo(cx + x0, cy + 6.5);
    path.close();
    canvas.drawPath(path, ink);
  };
  if (i === 0) {
    const path = Skia.Path.Make();
    path.moveTo(cx - 7, cy + 1);
    path.lineTo(cx, cy - 7);
    path.lineTo(cx + 7, cy + 1);
    path.close();
    canvas.drawPath(path, ink);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 7, cy + 3.5, 14, 3), 1, 1), ink);
  } else if (i === 1) {
    tri(0, -8);
    tri(8, 0);
  } else if (i === 2) {
    if (paused) tri(-5, 7);
    else {
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 5.5, cy - 7, 4, 14), 1, 1), ink);
      canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx + 1.5, cy - 7, 4, 14), 1, 1), ink);
    }
  } else if (i === 3) {
    tri(-8, 0);
    tri(0, 8);
  } else {
    // Shuffle: two crossing arrows.
    const st = paintOf(white(alpha));
    st.setStyle(1);
    st.setStrokeWidth(1.8);
    st.setStrokeCap(1);
    const a = Skia.Path.Make();
    a.moveTo(cx - 8, cy - 5);
    a.cubicTo(cx - 2, cy - 5, cx + 1, cy + 5, cx + 7, cy + 5);
    a.moveTo(cx - 8, cy + 5);
    a.cubicTo(cx - 2, cy + 5, cx + 1, cy - 5, cx + 7, cy - 5);
    for (const y of [-5, 5]) {
      a.moveTo(cx + 4.5, cy + y - 2.5);
      a.lineTo(cx + 7.5, cy + y);
      a.lineTo(cx + 4.5, cy + y + 2.5);
    }
    canvas.drawPath(a, st);
  }
}

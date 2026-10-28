import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
  type SkPathBuilder,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  BG_LAYOUT,
  bgMix,
  type Driver,
  driverBeginDrag,
  driverDrag,
  driverEndDrag,
  driverFrame,
  driverTap,
  emptyFrame,
  fade,
  type Frame,
  freqMix,
  gapMix,
  lerp,
  localCode,
  makeDriver,
  pageMix,
  pressScale,
  rowP,
  type Station,
  STATIONS,
  tiles,
  titleIn,
  titleOut,
  wordIn,
  wordsOut,
} from './tileDiscModel';

/*
 Tile Disc Radio — the now-playing screen of an invented radio app ("parquet.fm")
 (a port of TileDiscRadio.swift).

 Look: the page is the station's colour. A square cover of rounded "petal" tiles fills the top, and on
 it a big disc made of the same kind of tiles in another palette, separated by thin coloured lines, with
 the station's words set over it like a poster. Under it the station, the show, a tuner dial and the
 transport. Everything is drawn in code into one Skia picture per frame.

 Hero: changing station turns the disc a quarter turn (a 4° lean back, then a spring with a little
 overshoot) while each row of tiles runs like a conveyor, carrying the old station's tiles out and the
 new station's in, rows alternating direction with a 60 ms stagger and the gap lines widening as they
 slide. The background tiles recolour in a diagonal wave, the page colour follows, the words re-set
 one by one and the tuner needle scans to the new frequency.

 autoPlay: LOOP = 9 s. Rest on Lumen Sounds, "next" at 1.5 s, 4.3 s and 7.1 s (three stations), so
 the loop ends where it began; the disc has turned three quarter turns, and since every station's
 layout is drawn turned with it, the last frame is the first.
 Touch: swipe sideways on the cover to turn the disc with the finger: past 22° (or thrown) it changes
 station that way, else it springs back. Tap ⏮ / ⏭ to change, the middle button to pause. 2 s after
 the last touch it goes back to the first station and the loop restarts.
 Reduced motion: no turn and no conveyor; a change crossfades in 0.3 s, and the loop is off.
*/

type Props = {
  /** Plays the 9 s station-change loop until touched. */
  autoPlay?: boolean;
};

const INK = 0xfff8f2;
const COVER_INK = 0x160d22;
const LIVE_RED = 0xff3b4a;

type Fonts = {
  brand: SkFont;
  pill: SkFont;
  cover: SkFont;
  over: SkFont;
  title: SkFont;
  sub: SkFont;
  tick: SkFont;
};

type Geo = {
  w: number;
  h: number;
  top: number;
  cx: number;
  coverX: number;
  coverY: number;
  side: number;
  bgCell: number;
  discY: number;
  radius: number;
  cell: number;
  y0: number;
  transportY: number;
  tunerY: number;
};

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const side = w - 32;
  const coverY = top + 52;
  const radius = side * 0.45;
  const y0 = coverY + side;
  const transportY = h - Math.max(bottom, 16) - 62;
  return {
    w,
    h,
    top,
    cx: w / 2,
    coverX: 16,
    coverY,
    side,
    bgCell: (side - 14) / 4,
    discY: coverY + side / 2,
    radius,
    cell: (radius * 2) / 3.4,
    y0,
    transportY,
    tunerY: (y0 + 97 + transportY) / 2 + 4,
  };
}

/** 1 disc, 2 previous, 3 next, 4 play, 0 nothing. */
function targetAt(g: Geo, x: number, y: number) {
  'worklet';
  if (y >= g.coverY && y <= g.coverY + g.side) return 1;
  if (Math.abs(y - g.transportY) <= 40) {
    if (Math.abs(x - g.cx) <= 40) return 4;
    if (Math.abs(x - (g.cx - 108)) <= 34) return 2;
    if (Math.abs(x - (g.cx + 108)) <= 34) return 3;
  }
  return 0;
}

// Drawing helpers

function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

/** Two colours mixed in sRGB. */
function mix(a: number, b: number, t: number, alpha: number): SkColor {
  'worklet';
  return Float32Array.of(
    lerp((a >> 16) & 0xff, (b >> 16) & 0xff, t) / 255,
    lerp((a >> 8) & 0xff, (b >> 8) & 0xff, t) / 255,
    lerp(a & 0xff, b & 0xff, t) / 255,
    alpha,
  );
}

function fill(color: SkColor): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: SkColor, width: number): SkPaint {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

/** SwiftUI's blur radius as a Skia sigma. */
function sigma(r: number) {
  'worklet';
  return r * 1.5 * 0.57735 + 0.5;
}

function roundRect(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}

function oval(cx: number, cy: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make()
    .addOval(Skia.XYWHRect(cx - r, cy - r, r * 2, r * 2))
    .detach();
}

/** Draws a line of text vertically centred on y: starting at x (anchor −1) or centred on it (0). */
function label(c: SkCanvas, s: string, font: SkFont, x: number, y: number, color: SkColor, anchor: number, tracking: number) {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  const m = font.getMetrics();
  const x0 = anchor < 0 ? x : x - w / 2;
  c.drawGlyphs(ids, positions, x0, y - (m.ascent + m.descent) / 2, font, fill(color));
}

/** Adds a cell's tiles: rounded rects with a big radius on the masked corners. */
function addCell(b: SkPathBuilder, code: number, x: number, y: number, size: number, inset: number, small: number) {
  'worklet';
  const ts = tiles(code);
  for (let n = 0; n < ts.length; n++) {
    const t = ts[n];
    const l = x + t.x * size + inset;
    const tp = y + t.y * size + inset;
    const w = t.w * size - 2 * inset;
    const h = t.h * size - 2 * inset;
    const big = Math.max(0, Math.min(w, h) / 2);
    const sm = Math.min(small, big);
    const r1 = (t.mask & 1) !== 0 ? big : sm;
    const r2 = (t.mask & 2) !== 0 ? big : sm;
    const r4 = (t.mask & 4) !== 0 ? big : sm;
    const r8 = (t.mask & 8) !== 0 ? big : sm;
    b.addRRect({
      rect: Skia.XYWHRect(l, tp, w, h),
      topLeft: { x: r1, y: r1 },
      topRight: { x: r2, y: r2 },
      bottomRight: { x: r4, y: r4 },
      bottomLeft: { x: r8, y: r8 },
    });
  }
}

// Screen parts

function drawHeader(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const y = g.top + 24;
  label(c, 'parquet.fm', fonts.brand, 20, y, hex(INK, 1), -1, -0.4);
  const w = 84;
  const x = g.w - 20 - w;
  c.drawPath(roundRect(x + 0.75, y - 14 + 0.75, w - 1.5, 26.5, 13.25), stroke(hex(INK, 0.35), 1.5));
  // The dot breathes once a second (nine times per loop).
  const pulse = f.playing ? 0.55 + 0.45 * Math.pow(Math.cos(Math.PI * f.clock), 2) : 1;
  if (f.playing) {
    c.drawCircle(x + 14, y, 8, fill(hex(LIVE_RED, 0.25 * (1 - pulse))));
    c.drawCircle(x + 14, y, 4, fill(hex(LIVE_RED, pulse)));
  } else {
    c.drawCircle(x + 14, y, 4, fill(hex(INK, 0.45)));
  }
  label(c, f.playing ? 'LIVE' : 'PAUSED', fonts.pill, x + 26, y, hex(INK, 1), -1, 1.2);
}

function drawBackground(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  for (let j = 0; j < 4; j++) {
    for (let i = 0; i < 4; i++) {
      const t = f.reduced ? fade(f.ct) : bgMix(f.ct, i, j, f.ch.dir);
      const pb = Skia.PathBuilder.Make();
      addCell(pb, BG_LAYOUT[j * 4 + i], g.coverX + 7 + i * g.bgCell, g.coverY + 7 + j * g.bgCell, g.bgCell, 1.5, 6);
      c.drawPath(pb.detach(), fill(mix(a.bgTile, b.bgTile, t, 1)));
    }
  }
}

function drawDisc(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  const circle = oval(g.cx, g.discY, g.radius);
  // Two shadows: a soft ambient one and a tight contact one.
  const s1 = fill(hex(0x000000, 0.22));
  s1.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(18), true));
  c.drawCircle(g.cx, g.discY + 10, g.radius, s1);
  const s2 = fill(hex(0x000000, 0.25));
  s2.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(3), true));
  c.drawCircle(g.cx, g.discY + 2, g.radius, s2);

  c.save();
  c.clipPath(circle, ClipOp.Intersect, true);
  const gap = f.reduced ? fade(f.ct) : gapMix(f.ct);
  c.drawPath(circle, fill(mix(a.line, b.line, gap, 1)));
  c.translate(g.cx, g.discY);
  c.rotate(f.angle, 0, 0);
  const cell = g.cell;
  const oldB = Skia.PathBuilder.Make();
  const newB = Skia.PathBuilder.Make();
  if (f.reduced) {
    for (let r = 0; r < 4; r++) {
      for (let col = 0; col < 4; col++) {
        addCell(oldB, localCode(f.ch.from, f.ch.kFrom, col, r), (col - 2) * cell, (r - 2) * cell, cell, 1.5, 5);
        addCell(newB, localCode(f.ch.to, f.ch.kFrom + f.ch.dir, col, r), (col - 2) * cell, (r - 2) * cell, cell, 1.5, 5);
      }
    }
    c.drawPath(oldB.detach(), fill(hex(a.tile, 1)));
    c.drawPath(newB.detach(), fill(hex(b.tile, fade(f.ct))));
  } else {
    for (let r = 0; r < 4; r++) {
      const p = f.ch.dir === 0 ? 1 : rowP(f.ct, r);
      const sgn = (r % 2 === 0 ? 1 : -1) * (f.ch.dir === 0 ? 1 : f.ch.dir);
      const inset = 1.5 + 2.5 * Math.sin(Math.PI * p);
      const y = (r - 2) * cell;
      for (let col = 0; col < 4; col++) {
        const x = (col - 2) * cell;
        if (p < 1) addCell(oldB, localCode(f.ch.from, f.ch.kFrom, col, r), x + sgn * 4 * cell * p, y, cell, inset, 5);
        if (p > 0) {
          addCell(newB, localCode(f.ch.to, f.ch.kFrom + f.ch.dir, col, r), x - sgn * 4 * cell * (1 - p), y, cell, inset, 5);
        }
      }
    }
    c.drawPath(oldB.detach(), fill(hex(a.tile, 1)));
    c.drawPath(newB.detach(), fill(hex(b.tile, 1)));
  }
  c.restore();

  // The disc's edge catches a little light at the top.
  const rim = stroke(hex(0xffffff, 1), 1);
  rim.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: g.cx, y: g.discY - g.radius },
      { x: g.cx, y: g.discY + g.radius },
      [hex(0xffffff, 0.35), hex(0xffffff, 0), hex(0x000000, 0.12)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(g.cx, g.discY, g.radius - 0.5, rim);
}

/** The poster's words of a station: [text, x, y] with x and y as fractions of the cover. */
function posterWords(s: Station): [string, number, number][] {
  'worklet';
  const parts = s.name.toUpperCase().split(' ');
  return [
    [parts[0], 0.05, 0.355],
    [parts.length > 1 ? parts[1] : '', 0.05, 0.395],
    ['×', 0.29, 0.355],
    [s.call, 0.52, 0.355],
    [s.freq.toFixed(1) + ' FM', 0.755, 0.355],
    [s.vol, 0.8, 0.935],
  ];
}

function putWords(c: SkCanvas, g: Geo, s: Station, font: SkFont, ct: number, mode: number) {
  'worklet';
  // mode 0: fully shown, 1: going out, 2: coming in (staggered), 3: reduced-motion out, 4: reduced-motion in.
  const ws = posterWords(s);
  for (let n = 0; n < ws.length; n++) {
    const w = ws[n];
    let al = 1;
    let rise = 0;
    if (mode === 1) al = wordsOut(ct);
    else if (mode === 2) {
      al = wordIn(ct, n);
      rise = 6 * (1 - al);
    } else if (mode === 3) al = 1 - fade(ct);
    else if (mode === 4) al = fade(ct);
    if (al <= 0.001 || w[0].length === 0) continue;
    label(c, w[0], font, g.coverX + w[1] * g.side, g.coverY + w[2] * g.side + rise, hex(COVER_INK, al), -1, 0.2);
  }
}

function drawWords(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  if (f.ch.from === f.ch.to) {
    putWords(c, g, b, fonts.cover, f.ct, 0);
  } else if (f.reduced) {
    putWords(c, g, a, fonts.cover, f.ct, 3);
    putWords(c, g, b, fonts.cover, f.ct, 4);
  } else {
    putWords(c, g, a, fonts.cover, f.ct, 1);
    putWords(c, g, b, fonts.cover, f.ct, 2);
  }
}

function titleBlock(c: SkCanvas, g: Geo, s: Station, fonts: Fonts, dx: number, alpha: number) {
  'worklet';
  if (alpha <= 0.001) return;
  label(c, 'NOW ON AIR · ' + s.call + ' ' + s.freq.toFixed(1) + ' FM', fonts.over, 20 + dx, g.y0 + 34, hex(INK, 0.72 * alpha), -1, 1.4);
  label(c, s.name, fonts.title, 20 + dx, g.y0 + 66, hex(INK, alpha), -1, -0.6);
  label(c, s.show + ' · ' + s.listening + ' listening', fonts.sub, 20 + dx, g.y0 + 97, hex(INK, 0.72 * alpha), -1, 0);
}

function drawTitles(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  if (f.ch.from === f.ch.to) {
    titleBlock(c, g, b, fonts, 0, 1);
    return;
  }
  if (f.reduced) {
    const t = fade(f.ct);
    titleBlock(c, g, a, fonts, 0, 1 - t);
    titleBlock(c, g, b, fonts, 0, t);
    return;
  }
  const o = titleOut(f.ct);
  titleBlock(c, g, a, fonts, -20 * f.ch.dir * o, 1 - o);
  const i = titleIn(f.ct);
  titleBlock(c, g, b, fonts, 24 * f.ch.dir * (1 - i), i);
}

function drawTuner(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  const t = f.reduced ? fade(f.ct) : freqMix(f.ct);
  const freq = lerp(a.freq, b.freq, t);
  const y = g.tunerY;
  const half = g.w / 2 - 20;
  const perMHz = 40;
  const lo = Math.floor((freq - half / perMHz) * 5) - 1;
  const hi = Math.ceil((freq + half / perMHz) * 5) + 1;
  for (let n = lo; n <= hi; n++) {
    const mhz = n / 5;
    if (mhz < 87.5 || mhz > 108.0) continue;
    const dx = (mhz - freq) * perMHz;
    if (Math.abs(dx) > half) continue;
    const fd = 1 - Math.pow(Math.abs(dx) / half, 3);
    const major = n % 5 === 0;
    const h = major ? 16 : 8;
    c.drawRect(Skia.XYWHRect(g.cx + dx - 0.75, y + 8 - h, 1.5, h), fill(hex(INK, (major ? 0.8 : 0.4) * fd)));
    if (major && (n / 5) % 2 === 0) {
      label(c, String(n / 5), fonts.tick, g.cx + dx, y + 24, hex(INK, 0.6 * fd), 0, 0);
    }
  }
  // The needle in the disc's tile colour.
  const needle = fill(mix(a.tile, b.tile, t, 1));
  c.drawPath(roundRect(g.cx - 1, y - 20, 2, 32, 1), needle);
  c.drawCircle(g.cx, y - 20, 4, needle);
}

/** ⏭ (side +1) or ⏮ (side −1), centred on 0. */
function skipPath(side: number): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let k = 0; k < 2; k++) {
    const x0 = (-13 + k * 12) * side;
    p.moveTo(x0, -8);
    p.lineTo(x0 + 12 * side, 0);
    p.lineTo(x0, 8);
    p.close();
  }
  p.addRect(Skia.XYWHRect(side > 0 ? 11 : -13, -8, 2.5, 16));
  return p.detach();
}

function drawTransport(c: SkCanvas, g: Geo, f: Frame, page: number) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  const y = g.transportY;
  c.drawCircle(g.cx, y, 36, fill(hex(INK, 1)));
  const glyph = fill(mix(a.frame, b.frame, page, 1));
  if (f.playing) {
    c.drawPath(roundRect(g.cx - 9, y - 12, 6, 24, 2), glyph);
    c.drawPath(roundRect(g.cx + 3, y - 12, 6, 24, 2), glyph);
  } else {
    const p = Skia.PathBuilder.Make();
    p.moveTo(g.cx - 8, y - 13);
    p.lineTo(g.cx + 13, y);
    p.lineTo(g.cx - 8, y + 13);
    p.close();
    c.drawPath(p.detach(), glyph);
  }
  for (let side = -1; side <= 1; side += 2) {
    const s = f.reduced || f.ch.press !== side ? 1 : pressScale(f.ct);
    c.save();
    c.translate(g.cx + side * 108, y);
    c.scale(s, s);
    c.drawPath(skipPath(side), fill(hex(INK, 1)));
    c.restore();
  }
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const a = STATIONS[f.ch.from];
  const b = STATIONS[f.ch.to];
  const page = f.reduced ? fade(f.ct) : pageMix(f.ct);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(mix(a.frame, b.frame, page, 1)));
  drawHeader(c, g, f, fonts);
  drawBackground(c, g, f);
  drawDisc(c, g, f);
  drawWords(c, g, f, fonts);
  drawTitles(c, g, f, fonts);
  drawTuner(c, g, f, fonts);
  drawTransport(c, g, f, page);
}

const FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

function font(size: number, weight: '400' | '500' | '600' | '700' | '800'): SkFont {
  return matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
}

export function TileDiscRadio({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(
    () => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const coverSize = Math.max(10, geo.side * 0.034);
  const fonts = useMemo<Fonts>(
    () => ({
      brand: font(20, '800'),
      pill: font(12, '700'),
      cover: font(coverSize, '500'),
      over: font(12, '600'),
      title: font(32, '700'),
      sub: font(15, '400'),
      tick: font(11, '600'),
    }),
    [coverSize],
  );

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame());
  const now = useSharedValue(0);
  const grab = useSharedValue(0);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(driverFrame(driver.get(), t, auto, reduced));
  });

  // grab: 0 nothing, 1 the disc, 2 previous, 3 next, 4 play.
  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      let target = targetAt(geo, e.x, e.y);
      if (target === 1 && (reduced || !driverBeginDrag(driver.get(), now.get(), auto))) target = 0;
      grab.set(target);
    })
    .onUpdate((e) => {
      if (grab.get() === 1 && driverDrag(driver.get(), e.translationX, geo.radius, now.get())) scheduleOnRN(feel);
    })
    .onFinalize((e) => {
      const g = grab.get();
      grab.set(0);
      const moved = Math.hypot(e.translationX, e.translationY);
      if (g === 1) {
        if (driverEndDrag(driver.get(), e.velocityX, now.get())) scheduleOnRN(feel);
      } else if (g >= 2 && moved < 12) {
        const button = g === 2 ? -1 : g === 3 ? 1 : 0;
        if (driverTap(driver.get(), button, now.get(), auto, reduced)) scheduleOnRN(feel);
      }
    });

  const tap = (button: number) => {
    scheduleOnUI(() => {
      driverTap(driver.get(), button, now.get(), auto, reduced);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawScreen(canvas, geo, frame.get(), fonts);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityLabel="Now playing. Swipe the cover or use the buttons to change station."
        accessibilityActions={[
          { name: 'next', label: 'Next station' },
          { name: 'previous', label: 'Previous station' },
          { name: 'play', label: 'Play or pause' },
        ]}
        onAccessibilityAction={(e) => {
          const n = e.nativeEvent.actionName;
          tap(n === 'next' ? 1 : n === 'previous' ? -1 : 0);
        }}
      >
        <StatusBar style="light" />
        {size ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#8A08FD' },
});

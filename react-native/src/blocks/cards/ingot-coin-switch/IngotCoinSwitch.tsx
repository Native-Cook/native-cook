import { Canvas, ClipOp, Picture, Skia, type SkCanvas, type SkFont, type SkPicture, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  blurred,
  fill,
  font,
  glyphWidth,
  hex,
  label,
  makeMarks,
  type Marks,
  oval,
  polygon,
  rgba,
  roundRect,
  stroke,
} from './ingotCoinArt';
import {
  CAP_STOPS,
  COIN_HEX,
  copyFade,
  type Driver,
  driverBeginDrag,
  driverDrag,
  driverEndDrag,
  driverFrame,
  driverTap,
  emptyFrame,
  type Frame,
  frontAlpha,
  frontIndex,
  INGOT_HEX,
  lerp,
  makeDriver,
  METAL_MOVES,
  METAL_PRICES,
  METALS,
  mix,
  type Piece,
  pieces,
  rgb,
  STOCK_MOVES,
  STOCK_PRICES,
  STYLES,
  TICKERS,
} from './ingotCoinModel';

/*
 Ingot Coin Switch — the Explore screen of an invented investing app ("kesta")
 (a port of IngotCoinSwitch.swift).

 Look: a warm off-white page, a headline and a subline, and in the middle a ring of ten metal ingots
 (gold, copper, silver, platinum, palladium) standing on edge and leaning outward like a crown, turning
 slowly. Under it a ticker for the piece in front, the "Stocks / Commodities" switch and a dark CTA.
 Everything is drawn in code into one Skia picture per frame: each piece is a 3D body projected with
 perspective and shaded face by face, with a highlight band that slides across its face as it turns
 (ingotCoinModel.ts).

 Hero: switching to "Stocks" morphs every ingot into a thick frosted coin with an embossed letter: the
 outline goes from a rounded bar to a circle, the body thins, and each piece turns a quarter turn so the
 coins stand like the pages of a book, going translucent at the peak. The morph runs as a wave from the
 front piece to the sides; switching back undoes it.

 autoPlay: LOOP = 10 s. Rest on ingots (2.4 s), tap "Stocks" and morph, hold the coins, tap
 "Commodities" at 6.4 s and morph back, rest. The ring turns half a turn per loop (its pieces repeat
 every five), so the last frame is the first.
 Touch: tap a pill to switch; drag sideways to spin the ring, it keeps the finger's speed and slows
 down. 2 s after the last touch the ring turns home, the pieces go back to ingots and the loop restarts.
 Reduced motion: the ring stands still; the switch crossfades the shapes and the copy.
*/

type Props = {
  /** Plays the 10 s switch loop until touched. */
  autoPlay?: boolean;
};

const PAPER = 0xf5f4f1;
const INK = 0x16181b;
const MUTED = 0x6f6b65;

const HEADLINES = [
  ['Own real metal,', 'from $1.'],
  ['A slice of 2,000+', 'companies.'],
];
const SUBLINES = [
  ['Gold, silver, copper and more, held in', 'insured vaults. Capital at risk.'],
  ['Fractional shares from $1 and no fees on', 'your first 50 trades. Capital at risk.'],
];
const CTAS = ['Explore commodities', 'Explore stocks'];

type Fonts = {
  brand: SkFont;
  avatar: SkFont;
  eyebrow: SkFont;
  headline: SkFont;
  subline: SkFont;
  cta: SkFont;
  pill: SkFont;
  tickerBold: SkFont;
  ticker: SkFont;
};

type Geo = { w: number; h: number; top: number; cx: number; ringY: number; tickerY: number; toggleY: number; ctaY: number };

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  return {
    w,
    h,
    top,
    cx: w / 2,
    ringY: top + 386,
    tickerY: top + 562,
    toggleY: top + 620,
    ctaY: h - Math.max(bottom, 16) - 30,
  };
}

/** 1 for "Stocks", 0 for "Commodities", −1 elsewhere. */
function pillAt(g: Geo, x: number, y: number) {
  'worklet';
  if (y < g.toggleY - 30 || y > g.toggleY + 30) return -1;
  if (x >= g.cx - 138 && x <= g.cx - 22) return 1;
  if (x >= g.cx - 20 && x <= g.cx + 138) return 0;
  return -1;
}

function drawHeader(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  const y = g.top + 26;
  label(c, 'kesta', fonts.brand, 24, y, hex(INK, 1), -1, -0.6);
  const ax = g.w - 41;
  c.drawCircle(ax, y, 17, fill(hex(0xe7e4de, 1)));
  label(c, 'AL', fonts.avatar, ax, y, hex(INK, 1), 0, 0);
  label(c, 'EXPLORE', fonts.eyebrow, g.cx, g.top + 74, hex(0x8b8780, 1), 0, 1.6);
}

/** The visible copy layers: mode, opacity and rise of each. */
function copyLayers(f: Frame) {
  'worklet';
  const out: { mode: number; alpha: number; rise: number }[] = [];
  if (f.copyFrom === f.copyTo) {
    out.push({ mode: f.copyTo, alpha: 1, rise: 0 });
    return out;
  }
  const k = copyFade(f.copyU);
  if (k.out > 0.001) out.push({ mode: f.copyFrom, alpha: k.out, rise: 0 });
  if (k.inn > 0.001) out.push({ mode: f.copyTo, alpha: k.inn, rise: k.rise });
  return out;
}

function drawCopy(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const layers = copyLayers(f);
  for (let n = 0; n < layers.length; n++) {
    const l = layers[n];
    const y = g.top + l.rise;
    label(c, HEADLINES[l.mode][0], fonts.headline, g.cx, y + 106, hex(INK, l.alpha), 0, -0.6);
    label(c, HEADLINES[l.mode][1], fonts.headline, g.cx, y + 142, hex(INK, l.alpha), 0, -0.6);
    label(c, SUBLINES[l.mode][0], fonts.subline, g.cx, y + 184, hex(MUTED, l.alpha), 0, 0);
    label(c, SUBLINES[l.mode][1], fonts.subline, g.cx, y + 204, hex(MUTED, l.alpha), 0, 0);
  }
}

/** A soft shadow on the floor under the ring. */
function drawFloor(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  let m = 0;
  for (let i = 0; i < f.m.length; i++) m += f.m[i];
  m /= f.m.length;
  const y = g.ringY + lerp(124, 100, m);
  const w = lerp(310, 340, m);
  blurred(c, oval(g.cx - w / 2, y - 16, w, 32), hex(0x000000, 0.13), 14, 0);
  blurred(c, oval(g.cx - w * 0.36, y - 5, w * 0.72, 10), hex(0x000000, 0.1), 5, 0);
}

function drawMark(c: SkCanvas, t: number[], pic: SkPicture, alpha: number) {
  'worklet';
  const layer = Skia.Paint();
  layer.setAlphaf(alpha);
  c.save();
  c.concat(Skia.Matrix([t[0], t[2], t[4], t[1], t[3], t[5], 0, 0, 1]));
  c.saveLayer(layer);
  c.drawPicture(pic);
  c.restore();
  c.restore();
}

function drawPiece(c: SkCanvas, p: Piece, marks: Marks) {
  'worklet';
  if (p.silhouette.length >= 6) c.drawPath(polygon(p.silhouette), fill(rgba(p.silhouetteColor, 1)));
  for (let n = 0; n < p.quads.length; n++) {
    const q = p.quads[n];
    c.drawPath(polygon(q.points), fill(rgba(q.color, 1)));
  }
  const style = p.index % STYLES;
  for (let n = 0; n < p.caps.length; n++) {
    const cap = p.caps[n];
    const path = polygon(cap.points);
    const span = Math.hypot(cap.toX - cap.fromX, cap.toY - cap.fromY);
    if (span > 0.5) {
      const colors = [];
      for (let s = 0; s < cap.colors.length; s++) colors.push(rgba(cap.colors[s], 1));
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setShader(
        Skia.Shader.MakeLinearGradient({ x: cap.fromX, y: cap.fromY }, { x: cap.toX, y: cap.toY }, colors, CAP_STOPS, TileMode.Clamp),
      );
      c.drawPath(path, paint);
    } else {
      c.drawPath(path, fill(rgba(cap.colors[4], 1)));
    }
    if (Math.hypot(cap.bottomX - cap.topX, cap.bottomY - cap.topY) > 0.5) {
      const sheen = Skia.Paint();
      sheen.setAntiAlias(true);
      sheen.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: cap.topX, y: cap.topY },
          { x: cap.bottomX, y: cap.bottomY },
          [hex(0xffffff, 0.14), hex(0xffffff, 0), hex(0x000000, 0.07)],
          [0, 0.45, 1],
          TileMode.Clamp,
        ),
      );
      c.drawPath(path, sheen);
    }
    if (cap.ingotAlpha > 0.01) drawMark(c, cap.transform, marks.ingot[style], cap.ingotAlpha);
    if (cap.coinAlpha > 0.01) drawMark(c, cap.transform, marks.coin[style], cap.coinAlpha);
  }
}

function drawRing(c: SkCanvas, g: Geo, f: Frame, marks: Marks) {
  'worklet';
  const list = pieces(f, g.cx, g.ringY);
  for (let n = 0; n < list.length; n++) {
    const p = list[n];
    if (p.opacity < 0.999) {
      const layer = Skia.Paint();
      layer.setAlphaf(p.opacity);
      c.saveLayer(layer);
      drawPiece(c, p, marks);
      c.restore();
    } else {
      drawPiece(c, p, marks);
    }
  }
}

/** The price of the piece in front. */
function drawTicker(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const y = g.tickerY;
  const w = 214;
  const chip = roundRect(g.cx - w / 2, y - 16, w, 32, 16);
  c.drawPath(chip, fill(hex(0xffffff, 0.85)));
  c.drawPath(chip, stroke(hex(0xe3dfd8, 1), 1));
  const i = frontIndex(f);
  const alpha = frontAlpha(f);
  if (alpha < 0.005) return;
  const style = i % STYLES;
  const stock = f.m[i] >= 0.5;
  const base = rgb(stock ? COIN_HEX[style] : INGOT_HEX[style]);
  const name = stock ? TICKERS[style] : METALS[style];
  const price = stock ? STOCK_PRICES[style] : METAL_PRICES[style];
  const move = stock ? STOCK_MOVES[style] : METAL_MOVES[style];
  const wn = glyphWidth(fonts.tickerBold, name, 0);
  const wp = glyphWidth(fonts.ticker, price, 0);
  const wm = glyphWidth(fonts.tickerBold, move, 0);
  const total = 8 + 7 + wn + 9 + wp + 9 + wm;
  let x = g.cx - total / 2;
  c.drawCircle(x + 4, y, 4, fill(rgba(mix(base, { r: 0, g: 0, b: 0 }, 0.08), alpha)));
  x += 15;
  label(c, name, fonts.tickerBold, x, y, hex(INK, alpha), -1, 0);
  x += wn + 9;
  label(c, price, fonts.ticker, x, y, hex(MUTED, alpha), -1, 0);
  x += wp + 9;
  label(c, move, fonts.tickerBold, x, y, hex(0x2f9e6a, alpha), -1, 0);
}

/** "Stocks / Commodities": outlined pills with a dark capsule sliding between them. */
function drawToggle(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const top = g.toggleY - 22;
  const sx = g.cx - 134;
  const sw = 108;
  const kx = g.cx - 16;
  const kw = 150;
  c.drawPath(roundRect(sx + 0.75, top + 0.75, sw - 1.5, 42.5, 21.25), stroke(hex(INK, 1), 1.5));
  c.drawPath(roundRect(kx + 0.75, top + 0.75, kw - 1.5, 42.5, 21.25), stroke(hex(INK, 1), 1.5));
  const x = lerp(kx, sx, f.sel);
  const w = Math.max(44, lerp(kw, sw, f.sel));
  const capsule = roundRect(x, top, w, 44, 22);
  // Dark labels everywhere, white ones only inside the capsule.
  label(c, 'Stocks', fonts.pill, sx + sw / 2, g.toggleY, hex(INK, 1), 0, 0);
  label(c, 'Commodities', fonts.pill, kx + kw / 2, g.toggleY, hex(INK, 1), 0, 0);
  c.drawPath(capsule, fill(hex(INK, 1)));
  c.save();
  c.clipPath(capsule, ClipOp.Intersect, true);
  label(c, 'Stocks', fonts.pill, sx + sw / 2, g.toggleY, hex(0xffffff, 1), 0, 0);
  label(c, 'Commodities', fonts.pill, kx + kw / 2, g.toggleY, hex(0xffffff, 1), 0, 0);
  c.restore();
}

function drawCta(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const pill = roundRect(24, g.ctaY - 28, g.w - 48, 56, 28);
  blurred(c, pill, hex(0x000000, 0.18), 14, 8);
  c.drawPath(pill, fill(hex(INK, 1)));
  c.drawPath(roundRect(24.5, g.ctaY - 27, g.w - 49, 55, 27.5), stroke(hex(0xffffff, 0.12), 1));
  const layers = copyLayers(f);
  for (let n = 0; n < layers.length; n++) {
    const l = layers[n];
    label(c, CTAS[l.mode], fonts.cta, g.cx, g.ctaY + l.rise, hex(0xffffff, l.alpha), 0, 0);
  }
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts, marks: Marks) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(PAPER, 1)));
  drawHeader(c, g, fonts);
  drawCopy(c, g, f, fonts);
  drawFloor(c, g, f);
  drawRing(c, g, f, marks);
  drawTicker(c, g, f, fonts);
  drawToggle(c, g, f, fonts);
  drawCta(c, g, f, fonts);
}

export function IngotCoinSwitch({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(
    () => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts>(
    () => ({
      brand: font(22, '900'),
      avatar: font(13, '600'),
      eyebrow: font(12, '600'),
      headline: font(31, '700'),
      subline: font(15, '400'),
      cta: font(17, '600'),
      pill: font(16, '600'),
      tickerBold: font(14, '600'),
      ticker: font(14, '400'),
    }),
    [],
  );
  const marks = useMemo(() => makeMarks(), []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame());
  const now = useSharedValue(0);
  const grab = useSharedValue(-2);

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

  // grab: −2 idle, −1 spinning the ring, 0/1 a pill.
  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const pill = pillAt(geo, e.x, e.y);
      if (pill >= 0) {
        grab.set(pill);
      } else if (!reduced) {
        grab.set(-1);
        driverBeginDrag(driver.get(), now.get(), auto, reduced);
      } else {
        grab.set(-2);
      }
    })
    .onUpdate((e) => {
      if (grab.get() === -1) driverDrag(driver.get(), e.translationX, now.get());
    })
    .onFinalize((e) => {
      const g = grab.get();
      grab.set(-2);
      if (g === -1) {
        driverEndDrag(driver.get(), e.velocityX, now.get());
      } else if (g >= 0 && Math.abs(e.translationX) < 12 && Math.abs(e.translationY) < 12) {
        if (driverTap(driver.get(), g, now.get(), auto, reduced)) scheduleOnRN(feel);
      }
    });

  const tap = (mode: number) => {
    scheduleOnUI(() => {
      driverTap(driver.get(), mode, now.get(), auto, reduced);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawScreen(canvas, geo, frame.get(), fonts, marks);
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
        accessibilityLabel="Explore. Switch between commodities and stocks."
        accessibilityActions={[
          { name: 'stocks', label: 'Show stocks' },
          { name: 'commodities', label: 'Show commodities' },
        ]}
        onAccessibilityAction={(e) => tap(e.nativeEvent.actionName === 'stocks' ? 1 : 0)}
      >
        <StatusBar style="dark" />
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
  screen: { flex: 1, backgroundColor: '#F5F4F1' },
});

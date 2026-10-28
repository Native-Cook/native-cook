import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
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
  COUNT,
  type Driver,
  driverBackspace,
  driverBeginDrag,
  driverCancelDrag,
  driverDrag,
  driverEndDrag,
  driverFrame,
  driverPressDown,
  driverPressKey,
  driverScroll,
  driverTapSwatch,
  driverTouch,
  easeInOutCubic,
  emptyScene,
  fillOf,
  inkOf,
  isKey,
  KEY_RADIUS,
  keyState,
  LABELS,
  lerp,
  look,
  makeDriver,
  PITCH,
  press,
  REDUCED_DUR,
  reveal,
  REVEAL_DUR,
  role,
  roundHalf,
  type Scene,
  seg,
  swatchX,
  type Theme,
  THEMES,
  wrap,
} from './keypadThemeWaveModel';

/*
 Keypad Theme Wave — the theme step of an invented calculator app's onboarding ("tallo")
 (a port of KeypadThemeWave.swift).

 Look: a cream page; at the top a preview card of the calculator (display "0", a 4 × 5 keypad of round keys
 and the app's wordmark); under it a strip of swatches, each a tiny striped thumbnail of one theme, whose
 centred swatch is bigger, framed in white and is the selection; then "Customize your calculator", a line
 of help and a dark Continue button. Everything is drawn in code into one Skia picture per frame
 (keypadThemeWaveModel.ts holds the ten palettes, the timing and the driver).

 Hero: when the selection changes, the new theme sweeps across the preview. The card's colour is revealed
 by a circle from the corner the new swatch came from, and the keys follow on anti-diagonals from the same
 corner, 60 ms apart: each key drains its old colour into its centre, stands for a beat as a hollow ring in
 the new theme's line colour, then the new fill blooms out of the centre with a small overshoot.

 autoPlay: LOOP = 10 s. Rest on Classic; the strip glides to Marigold (1.0 s), Lilac (3.2 s) and Mint
 (5.2 s), each sending a wave from the right, then sweeps back five swatches to Classic (7.2 s), whose
 wave comes from the left; rest from 8.5 s. Every frame is a function of the loop time.
 Touch: drag or flick the strip, it snaps to the centred swatch and sends a wave; tap a swatch to pick it;
 tap the keys to calculate (the delete tag removes a digit). 2 s after the last touch the display clears,
 the strip glides home to Classic and the loop restarts.
 Reduced motion: no loop; a change crossfades the card and keys in 0.25 s.
*/

type Props = {
  /** Plays the 10 s theme loop until touched. */
  autoPlay?: boolean;
};

const PAGE = 0xfaf6f0;
const INK = 0x2a2622;
const MUTED = 0x77716a;

const TITLE = ['Customize', 'your calculator'];
const SUBTITLE = 'Swipe the strip to pick colors you love.';
const CTA = 'Continue';

// Paint helpers

function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, Math.max(0, Math.min(1, a)));
}

function mixHex(a: number, b: number, t: number): SkColor {
  'worklet';
  return Float32Array.of(
    lerp(((a >> 16) & 0xff) / 255, ((b >> 16) & 0xff) / 255, t),
    lerp(((a >> 8) & 0xff) / 255, ((b >> 8) & 0xff) / 255, t),
    lerp((a & 0xff) / 255, (b & 0xff) / 255, t),
    1,
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

function blurred(c: SkCanvas, path: SkPath, color: SkColor, radius: number, dy: number, width: number) {
  'worklet';
  const p = fill(color);
  if (width > 0) {
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(width);
  }
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(radius), true));
  c.save();
  c.translate(0, dy);
  c.drawPath(path, p);
  c.restore();
}

function roundRect(x: number, y: number, w: number, h: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
}

function circle(x: number, y: number, r: number): SkPath {
  'worklet';
  return Skia.PathBuilder.Make()
    .addOval(Skia.XYWHRect(x - r, y - r, 2 * r, 2 * r))
    .detach();
}

/** Draws a line of text vertically centred on y: anchor −1 starts at x, 0 centres on x, 1 ends at x. */
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
  const x0 = anchor < 0 ? x : anchor > 0 ? x - w : x - w / 2;
  c.drawGlyphs(ids, positions, x0, y - (m.ascent + m.descent) / 2, font, fill(color));
}

const FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

type Weight = '400' | '500' | '600' | '700' | '900';

function font(size: number, weight: Weight): SkFont {
  return matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
}

type Fonts = {
  ac: SkFont;
  op: SkFont;
  fn: SkFont;
  digit: SkFont;
  display: SkFont;
  displaySmall: SkFont;
  brand: SkFont;
  brandSub: SkFont;
  title: SkFont;
  subtitle: SkFont;
  cta: SkFont;
};

// Geometry

type Geo = {
  w: number;
  h: number;
  cx: number;
  cardX: number;
  cardW: number;
  cardTop: number;
  cardH: number;
  displayY: number;
  displayRight: number;
  stripY: number;
  titleY: number;
  buttonY: number;
  revealMax: number;
};

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const cardTop = top + 45;
  const cardH = 352;
  const stripY = cardTop + cardH + 64;
  return {
    w,
    h,
    cx: w / 2,
    cardX: 24,
    cardW: w - 48,
    cardTop,
    cardH,
    displayY: cardTop + 52,
    displayRight: w / 2 + 117,
    stripY,
    titleY: stripY + 98,
    buttonY: h - Math.max(bottom, 16) - 41,
    revealMax: Math.hypot(w - 48, cardH) + 24,
  };
}

function keyX(g: Geo, c: number) {
  'worklet';
  return g.cx + (c - 2) * PITCH;
}

function keyY(g: Geo, r: number) {
  'worklet';
  return g.cardTop + 124 + r * PITCH;
}

// Grab codes: −1 none, −2 strip, −3 delete, −4 elsewhere, r * 5 + c a key.
const GRAB_NONE = -1;
const GRAB_STRIP = -2;
const GRAB_DELETE = -3;
const GRAB_OTHER = -4;

function hit(g: Geo, x: number, y: number) {
  'worklet';
  if (Math.abs(y - g.stripY) < 34 && x > g.cardX - 8 && x < g.cardX + g.cardW + 8) return GRAB_STRIP;
  for (let r = 0; r < 4; r++) {
    for (let c = 0; c < 5; c++) {
      if (!isKey(r, c)) continue;
      if (Math.hypot(x - keyX(g, c), y - keyY(g, r)) < PITCH / 2) return r * 5 + c;
    }
  }
  if (Math.hypot(x - (g.cx + 134), y - g.displayY) < 22) return GRAB_DELETE;
  return GRAB_OTHER;
}

/** Which swatch (in steps from the centred one) sits under x. */
function swatchOffset(g: Geo, x: number, scroll: number) {
  'worklet';
  const base = roundHalf(scroll);
  let best = 0;
  let bestDist = Infinity;
  for (let k = -6; k <= 6; k++) {
    const d = k + base - scroll;
    const dist = Math.abs(g.cx + swatchX(d) - x);
    if (dist < bestDist) {
      bestDist = dist;
      best = k;
    }
  }
  return best;
}

// Drawing

/** One theme's card: background, display and wordmark, at an opacity (reduced motion fades them). */
function drawCardFace(c: SkCanvas, g: Geo, s: Scene, t: Theme, alpha: number, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(g.cardX, g.cardTop, g.cardW, g.cardH), fill(hex(t.bg, alpha)));
  const df = s.display.length > 7 ? fonts.displaySmall : fonts.display;
  label(c, s.display, df, g.displayRight, g.displayY, hex(t.display, alpha), 1, -0.5);
  // The delete tag: a label shape pointing left with a small cross.
  const x = g.cx + 134;
  const y = g.displayY;
  const tag = Skia.PathBuilder.Make()
    .moveTo(x - 10, y)
    .lineTo(x - 5, y - 6.5)
    .lineTo(x + 8, y - 6.5)
    .quadTo(x + 9.5, y - 6.5, x + 9.5, y - 5)
    .lineTo(x + 9.5, y + 5)
    .quadTo(x + 9.5, y + 6.5, x + 8, y + 6.5)
    .lineTo(x - 5, y + 6.5)
    .close()
    .detach();
  const faint = stroke(hex(t.display, 0.45 * alpha), 1.4);
  faint.setStrokeJoin(StrokeJoin.Round);
  faint.setStrokeCap(StrokeCap.Round);
  c.drawPath(tag, faint);
  c.drawLine(x - 1, y - 2.6, x + 4.2, y + 2.6, faint);
  c.drawLine(x + 4.2, y - 2.6, x - 1, y + 2.6, faint);
  // The wordmark in the keypad's empty cell.
  const px = keyX(g, 0);
  const py = keyY(g, 3);
  label(c, 'tallo', fonts.brand, px, py - 4, hex(t.display, 0.45 * alpha), 0, -0.3);
  label(c, 'CALC', fonts.brandSub, px + 0.8, py + 8, hex(t.display, 0.4 * alpha), 0, 1.6);
}

/** The card: its shadow, the base theme and a revealed circle for each change. */
function drawCard(c: SkCanvas, g: Geo, s: Scene, fonts: Fonts) {
  'worklet';
  const shape = roundRect(g.cardX, g.cardTop, g.cardW, g.cardH, 26);
  blurred(c, shape, hex(0x000000, 0.08), 15, 14, 0);
  blurred(c, shape, hex(0x000000, 0.06), 1.5, 1, 0);
  c.save();
  c.clipPath(shape, ClipOp.Intersect, true);
  drawCardFace(c, g, s, THEMES[s.base], 1, fonts);
  for (let i = 0; i < s.changes.length; i++) {
    const ch = s.changes[i];
    if (s.now < ch.at) continue;
    const tau = s.now - ch.at;
    const theme = THEMES[ch.to];
    if (s.reduced) {
      drawCardFace(c, g, s, theme, easeInOutCubic(Math.min(1, tau / REDUCED_DUR)), fonts);
      continue;
    }
    const p = reveal(tau);
    if (p <= 0) continue;
    const ox = ch.dir > 0 ? g.cardX + g.cardW : g.cardX;
    const oy = g.cardTop + g.cardH;
    const r = p * g.revealMax;
    c.save();
    c.clipPath(circle(ox, oy, r), ClipOp.Intersect, true);
    drawCardFace(c, g, s, theme, 1, fonts);
    // A soft light band just inside the moving edge, gone as the reveal completes.
    const rim = 1 - seg(tau, REVEAL_DUR * 0.6, REVEAL_DUR);
    if (rim > 0 && r > 6) blurred(c, circle(ox, oy, r - 6), hex(0xffffff, 0.22 * rim), 5, 0, 8);
    c.restore();
  }
  c.restore();
}

function keyFont(fonts: Fonts, r: number, col: number) {
  'worklet';
  const ro = role(r, col);
  if (LABELS[r][col] === 'AC') return fonts.ac;
  if (ro === 2) return fonts.op;
  if (ro === 1) return fonts.fn;
  return fonts.digit;
}

function drawKeys(c: SkCanvas, g: Geo, s: Scene, fonts: Fonts) {
  'worklet';
  const R = KEY_RADIUS;
  for (let r = 0; r < 4; r++) {
    for (let col = 0; col < 5; col++) {
      if (!isKey(r, col)) continue;
      const ro = role(r, col);
      const st = keyState(s, r, col);
      const from = THEMES[st.from];
      const to = THEMES[st.to];
      const l = look(st.u, s.reduced);
      const pr = press(s.now - s.presses[r * 5 + col]);
      const text = LABELS[r][col];
      const f = keyFont(fonts, r, col);
      const sc = l.scale * (1 - 0.09 * pr);
      c.save();
      c.translate(keyX(g, col), keyY(g, r));
      c.scale(sc, sc);
      if (s.reduced) {
        c.drawCircle(0, 0, R, fill(mixHex(fillOf(from, ro), fillOf(to, ro), l.mix)));
        if (1 - l.mix > 0.004) label(c, text, f, 0, 0, hex(inkOf(from, ro), 1 - l.mix), 0, 0);
        if (l.mix > 0.004) label(c, text, f, 0, 0, hex(inkOf(to, ro), l.mix), 0, 0);
      } else {
        if (l.oldR > 0.2) c.drawCircle(0, 0, l.oldR, fill(hex(fillOf(from, ro), 1)));
        if (l.oldLabel > 0.004) label(c, text, f, 0, 0, hex(inkOf(from, ro), l.oldLabel), 0, 0);
        if (l.ring > 0.004) c.drawCircle(0, 0, R - 0.7, stroke(hex(to.ring, l.ring), 1.4));
        if (l.ringLabel > 0.004) label(c, text, f, 0, 0, hex(to.ring, l.ringLabel), 0, 0);
        if (l.newR > 0.2) c.drawCircle(0, 0, l.newR, fill(hex(fillOf(to, ro), 1)));
        if (l.newLabel > 0.004) label(c, text, f, 0, 0, hex(inkOf(to, ro), l.newLabel), 0, 0);
      }
      if (pr > 0.004) c.drawCircle(0, 0, R, fill(hex(inkOf(to, ro), 0.16 * pr)));
      c.restore();
    }
  }
}

/** The swatch strip: an endless row of striped thumbnails, the centred one larger and framed. */
function drawStrip(c: SkCanvas, g: Geo, s: Scene) {
  'worklet';
  const n = COUNT;
  const items: { i: number; d: number }[] = [];
  for (let i = 0; i < COUNT; i++) {
    const d0 = wrap(i - s.scroll);
    for (let k = -1; k <= 1; k++) {
      const d = d0 + k * n;
      if (Math.abs(swatchX(d)) < g.cardW / 2 + 24) items.push({ i, d });
    }
  }
  items.sort((a, b) => Math.abs(b.d) - Math.abs(a.d));
  c.save();
  c.clipRect(Skia.XYWHRect(g.cardX, g.stripY - 40, g.cardW, 80), ClipOp.Intersect, true);
  for (let n2 = 0; n2 < items.length; n2++) {
    const it = items[n2];
    const a = Math.min(Math.abs(it.d), 1);
    const big = 1 - a;
    const w = 32 + 12 * big;
    const h = 30 + 12 * big;
    const rad = 5 + 7 * big;
    const x = g.cx + swatchX(it.d);
    const left = x - w / 2;
    const top = g.stripY - h / 2;
    const shape = roundRect(left, top, w, h, rad);
    if (big > 0.01) blurred(c, shape, hex(0x000000, 0.2 * big), 6, 3, 0);
    const t = THEMES[it.i];
    c.save();
    c.clipPath(shape, ClipOp.Intersect, true);
    const colors = [t.bg, t.op, t.fn, t.digit];
    const fracs = [0.34, 0.22, 0.22, 0.22];
    let y = top;
    for (let b = 0; b < 4; b++) {
      c.drawRect(Skia.XYWHRect(left, y, w, h * fracs[b] + 0.5), fill(hex(colors[b], 1)));
      y += h * fracs[b];
    }
    c.restore();
    if (big > 0.01) c.drawPath(roundRect(left + 1.5, top + 1.5, w - 3, h - 3, rad - 1.5), stroke(hex(0xffffff, big), 3));
  }
  // The ends fade into the page.
  const fade = 30;
  const left = g.cardX;
  const right = g.cardX + g.cardW;
  const top = g.stripY - 40;
  const lp = Skia.Paint();
  lp.setShader(
    Skia.Shader.MakeLinearGradient({ x: left, y: 0 }, { x: left + fade, y: 0 }, [hex(PAGE, 1), hex(PAGE, 0)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(left, top, fade, 80), lp);
  const rp = Skia.Paint();
  rp.setShader(
    Skia.Shader.MakeLinearGradient({ x: right - fade, y: 0 }, { x: right, y: 0 }, [hex(PAGE, 0), hex(PAGE, 1)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(right - fade, top, fade, 80), rp);
  c.restore();
}

function drawCopy(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  label(c, TITLE[0], fonts.title, g.cardX + 2, g.titleY, hex(INK, 1), -1, -0.6);
  label(c, TITLE[1], fonts.title, g.cardX + 2, g.titleY + 41, hex(INK, 1), -1, -0.6);
  label(c, SUBTITLE, fonts.subtitle, g.cardX + 2, g.titleY + 84, hex(MUTED, 1), -1, 0);
  const pill = roundRect(g.cardX, g.buttonY - 27, g.cardW, 54, 27);
  blurred(c, pill, hex(0x000000, 0.14), 10, 6, 0);
  c.drawPath(pill, fill(hex(INK, 1)));
  label(c, CTA, fonts.cta, g.cx, g.buttonY, hex(PAGE, 1), 0, 0);
}

function drawScreen(c: SkCanvas, g: Geo, s: Scene, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(PAGE, 1)));
  drawCard(c, g, s, fonts);
  drawKeys(c, g, s, fonts);
  drawStrip(c, g, s);
  drawCopy(c, g, fonts);
}

export function KeypadThemeWave({ autoPlay = false }: Props) {
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
      ac: font(16, '600'),
      op: font(23, '500'),
      fn: font(18, '500'),
      digit: font(21, '500'),
      display: font(40, '500'),
      displaySmall: font(34, '500'),
      brand: font(13, '900'),
      brandSub: font(7.5, '600'),
      title: font(32, '700'),
      subtitle: font(15, '400'),
      cta: font(17, '600'),
    }),
    [],
  );

  const driver = useSharedValue<Driver>(makeDriver());
  const scene = useSharedValue<Scene>(emptyScene());
  const now = useSharedValue(0);
  const grab = useSharedValue(GRAB_NONE);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const impact = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const soft = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    scene.set(driverFrame(driver.get(), t, auto, reduced));
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const g = hit(geo, e.x, e.y);
      grab.set(g);
      const d = driver.get();
      if (g === GRAB_STRIP) driverBeginDrag(d, now.get(), auto, reduced);
      else if (g >= 0) driverPressDown(d, Math.floor(g / 5), g % 5, now.get(), auto);
      else if (g === GRAB_DELETE) driverTouch(d, now.get(), auto);
    })
    .onUpdate((e) => {
      if (grab.get() === GRAB_STRIP && driverDrag(driver.get(), e.translationX, now.get())) scheduleOnRN(tick);
    })
    .onFinalize((e) => {
      const g = grab.get();
      grab.set(GRAB_NONE);
      const d = driver.get();
      const t = now.get();
      const moved = Math.hypot(e.translationX, e.translationY);
      if (g === GRAB_STRIP) {
        if (moved < 8) {
          driverCancelDrag(d, t);
          const k = swatchOffset(geo, e.x, driverScroll(d, t, reduced));
          if (k !== 0 && driverTapSwatch(d, k, t, auto, reduced)) scheduleOnRN(impact);
        } else if (driverEndDrag(d, e.velocityX, t, reduced)) {
          scheduleOnRN(impact);
        }
      } else if (g >= 0 && moved < 14) {
        driverPressKey(d, Math.floor(g / 5), g % 5, t);
        scheduleOnRN(soft);
      } else if (g === GRAB_DELETE && moved < 14) {
        driverBackspace(d, t);
        scheduleOnRN(soft);
      }
    });

  const step = (by: number) => {
    scheduleOnUI(() => {
      driverTapSwatch(driver.get(), by, now.get(), auto, reduced);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawScreen(canvas, geo, scene.get(), fonts);
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
        accessibilityLabel="Customize your calculator. Swipe the strip to pick a color theme."
        accessibilityActions={[
          { name: 'next', label: 'Next theme' },
          { name: 'previous', label: 'Previous theme' },
        ]}
        onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'next' ? 1 : -1)}
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
  screen: { flex: 1, backgroundColor: '#FAF6F0' },
});

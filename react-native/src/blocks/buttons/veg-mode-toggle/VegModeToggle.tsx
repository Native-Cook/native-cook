import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawBroccoli, drawDrumstick, hex, makePen } from './vegModeArt';
import {
  clamp01,
  type Driver,
  driverFrame,
  driverSession,
  driverTouched,
  ENTER_DELAY,
  type Frame,
  makeDriver,
  makeSim,
  simBeginDrag,
  simDragTo,
  simFrame,
  simRelease,
  simTap,
  smooth,
  smoothRange,
} from './vegModeMotion';

/*
 Veg Mode Toggle — the diet switch of an invented food-delivery menu ("Tiffin Lane")
 (a port of VegModeToggle.swift).

 Look: a warm white screen, "What's for dinner?", and a big pill toggle: a light grey track with a
 black knob covering two thirds of it. The free end holds the mode's dish, drawn in code, popping out
 of the toggle like a sticker: a glossy broccoli bunch for Veg, a drumstick and a halved egg for
 Non veg. The labels sit in a strip that slides the other way inside the knob, so the knob is a
 window that swaps them. Below, "24 veg dishes" rolls to "38 non-veg dishes" and the three dish rows
 swap with a stagger. Everything is drawn into one Skia picture per frame.

 Flip: the knob slides on a UI spring and stretches in travel, the old dish sinks below the track's
 bottom line, the new one rises from it with overshoot, a lean that settles and a squash; its egg or
 floret follows 60 ms late (vegModeMotion.ts has the springs and the script).

 autoPlay: LOOP = 7 s (tap to Non veg, hold, drag back, release to Veg, rest).
 Touch: tap the toggle to flip; drag the knob to scrub between the two dishes, release to snap to
 the nearer end (with the finger's speed). A light haptic on each commit. 2 s after the last touch
 it springs back to Veg and the loop restarts.
 Reduced motion: the Veg state, still; a tap switches with short springs and the dishes fade.
*/

type Props = {
  /** Plays the 7 s flip-and-drag loop until touched. */
  autoPlay?: boolean;
};

const PAPER = 0xf6f5f1;
const INK = 0x171717;
const MUTED = 0x8a877f;
const RULE = 0xe6e3dc;

const TRACK_W = 252;
const TRACK_H = 104;
const KNOB_W = 168;

type Geo = { w: number; h: number; top: number; cx: number; trackX: number; trackY: number; headerY: number; rowsY: number };

function makeGeo(w: number, h: number, top: number): Geo {
  return { w, h, top, cx: w / 2, trackX: (w - TRACK_W) / 2, trackY: top + 248, headerY: top + 440, rowsY: top + 470 };
}

type Fonts = { r13: SkFont; s16: SkFont; s12: SkFont; b30: SkFont; m25: SkFont; s17: SkFont; r14: SkFont };

// SF Pro's own tracking at these sizes (Core Text applies it, Skia does not), so the copy keeps
// the Swift widths on iOS.
const IOS = Platform.OS === 'ios';
const TRACK = { r13: IOS ? -0.08 : 0, s16: IOS ? -0.31 : 0, s17: IOS ? -0.43 : 0, r14: IOS ? -0.15 : 0 };

const DISHES: [string, string, string][][] = [
  [
    ['Paneer tikka bowl', 'Charred paneer, mint rice', '$9.50'],
    ['Garden pulao', 'Saffron rice, peas, cashew', '$8.00'],
    ['Masala corn cakes', 'Crisp, with green chutney', '$6.50'],
  ],
  [
    ['Smoky drumsticks', 'Two pieces, tandoor spice', '$11.00'],
    ['Egg curry bowl', 'Two eggs, onion gravy', '$9.00'],
    ['Chicken pulao', 'Saffron rice, fried onion', '$10.50'],
  ],
];

function textWidth(font: SkFont, s: string, tracking = 0): number {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w + tracking * Math.max(0, widths.length - 1);
}

/** Draws a line of text vertically centred on y: centred on x, starting at x (-1) or ending at x (1). */
function label(c: SkCanvas, s: string, font: SkFont, x: number, y: number, color: SkColor, anchor = 0, tracking = 0) {
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
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  c.drawGlyphs(ids, positions, x0, y - (m.ascent + m.descent) / 2, font, p);
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
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

/** Android's blur radius as a Skia sigma, so the stacks soften alike. */
function sigma(r: number) {
  'worklet';
  return r * 0.57735 + 0.5;
}

function pill(x: number, y: number, w: number, h: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), h / 2, h / 2));
  return p;
}

function drawHeader(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  label(c, 'Deliver to', fonts.r13, 24, g.top + 18, hex(MUTED), -1, TRACK.r13);
  label(c, 'Home · 2.1 km', fonts.s16, 24, g.top + 38, hex(INK), -1, TRACK.s16);
  const sx = g.w - 44;
  const sy = g.top + 28;
  c.drawCircle(sx, sy, 20, stroke(hex(0xdedbd4), 1));
  c.drawCircle(sx - 1.5, sy - 1.5, 6.5, stroke(hex(INK), 1.8));
  c.drawLine(sx + 3.3, sy + 3.3, sx + 7, sy + 7, stroke(hex(INK), 1.8));
  label(c, 'TIFFIN LANE', fonts.s12, g.cx, g.top + 104, hex(0xa09c93), 0, 1.6);
  label(c, "What's for dinner?", fonts.b30, g.cx, g.top + 136, hex(INK), 0, IOS ? 0 : -0.4);
}

/** The dishes on one side of the knob, cut by the track's bottom line, with their soft shadow. */
function drawDishes(c: SkCanvas, f: Frame, front: boolean) {
  'worklet';
  const H = TRACK_H;
  c.save();
  c.clipRect(Skia.XYWHRect(-400, -400, 1200, 400 + H), ClipOp.Intersect, true);
  for (let i = 0; i < 2; i++) {
    const veg = i === 0;
    const on = (veg ? f.bFront : f.mFront) === front;
    const a = veg ? f.b : f.m;
    if (!on || a <= 0.002) continue;
    const v = veg ? f.bv : f.mv;
    const a2 = veg ? f.b2 : f.m2;
    const alpha = f.reduced ? clamp01(a) : 1;

    const shadow = Skia.Paint();
    shadow.setAlphaf(0.1 * alpha);
    c.saveLayer(shadow);
    c.translate(0, 4);
    c.scale(H, H);
    const ink = makePen(c, 6 / H);
    if (veg) drawBroccoli(ink, a, v, a2, f.reduced);
    else drawDrumstick(ink, a, v, a2, f.reduced);
    c.restore();

    if (alpha < 0.999) {
      const layer = Skia.Paint();
      layer.setAlphaf(alpha);
      c.saveLayer(layer);
    } else {
      c.save();
    }
    c.scale(H, H);
    const pen = makePen(c, 0);
    if (veg) drawBroccoli(pen, a, v, a2, f.reduced);
    else drawDrumstick(pen, a, v, a2, f.reduced);
    c.restore();
  }
  c.restore();
}

function drawToggle(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const H = TRACK_H;
  const W = TRACK_W;
  const K = KNOB_W;
  c.save();
  c.translate(g.trackX, g.trackY);

  // Track, with a soft inner shadow along its top.
  const track = pill(0, 0, W, H);
  c.drawPath(track, fill(hex(0xe7e5df)));
  c.save();
  c.clipPath(track, ClipOp.Intersect, true);
  const inner = stroke(hex(0x000000, 0.1), 6);
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(4), true));
  c.drawPath(pill(0, 3, W, H), inner);
  c.restore();

  // Knob: slides by p, stretches with its speed, shrinks a little while pressed.
  const travel = W - K;
  const kc = W - K / 2 - travel * f.p;
  const kw = K * (1 + 0.1 * Math.min(1, Math.abs(f.pv) / 5));
  const ps = 1 - 0.03 * f.press;
  const left = Math.min(Math.max(kc - kw / 2, -10), W - kw + 10);
  const rx = left + (kw * (1 - ps)) / 2;
  const ry = (H * (1 - ps)) / 2;
  const rw = kw * ps;
  const rh = H * ps;
  const knob = pill(rx, ry, rw, rh);

  drawDishes(c, f, false);

  const cast = fill(hex(0x1b1b1b));
  cast.setImageFilter(Skia.ImageFilter.MakeDropShadow(0, 2, sigma(5), sigma(5), hex(0x000000, 0.16), null));
  c.drawPath(knob, cast);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: ry }, { x: 0, y: ry + rh }, [hex(0x2b2b2b), hex(0x111111)], null, TileMode.Clamp),
  );
  c.drawPath(knob, body);

  c.save();
  c.clipPath(knob, ClipOp.Intersect, true);
  const hl = Skia.Paint();
  hl.setAntiAlias(true);
  hl.setStyle(PaintStyle.Stroke);
  hl.setStrokeWidth(1.5);
  hl.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: ry + 1.2 },
      { x: 0, y: ry + 1.2 + H * 0.45 },
      [hex(0xffffff, 0.14), hex(0xffffff, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.save();
  c.translate(0, 1.2);
  c.drawPath(knob, hl);
  c.restore();
  // The labels ride a strip that moves against the knob: the knob is their window.
  // Each label fades as it leaves the middle, so a half-way knob is not two cut words.
  const labels: [string, number][] = [
    ['Veg', f.p * K],
    ['Non veg', -(1 - f.p) * K],
  ];
  for (let i = 0; i < 2; i++) {
    const dx = labels[i][1];
    const a = 1 - smoothRange(0.25, 0.6, Math.abs(dx) / K);
    if (a > 0.001) label(c, labels[i][0], fonts.m25, kc + dx, H / 2, hex(0xfafaf7, a));
  }
  c.restore();

  drawDishes(c, f, true);
  c.restore();
}

/** How far row i has swapped to Non veg (the top row leads both ways). */
function rowSwap(f: Frame, i: number) {
  'worklet';
  const d = 0.12 * i;
  return f.qTarget >= 0.5 ? smooth((f.q - d) / 0.64) : 1 - smooth((1 - f.q - d) / 0.64);
}

function drawMark(c: SkCanvas, x: number, y: number, veg: boolean, alpha: number) {
  'worklet';
  const tone = hex(veg ? 0x2e9e47 : 0xb5442c, alpha);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - 8, y - 8, 16, 16), 3, 3), stroke(tone, 1.5));
  if (veg) {
    c.drawCircle(x, y, 3.6, fill(tone));
  } else {
    const tri = Skia.Path.Make();
    tri.moveTo(x, y - 4.3);
    tri.lineTo(x + 4, y + 2.6);
    tri.lineTo(x - 4, y + 2.6);
    tri.close();
    c.drawPath(tri, fill(tone));
  }
}

function drawList(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  // "24 veg dishes": the count rolls like an odometer, the words swap with row 0.
  const y = g.headerY;
  const numW = textWidth(fonts.s17, '38', TRACK.s17);
  const v = 24 + 14 * clamp01(f.q);
  const n = Math.floor(v);
  const fr = v - n;
  c.save();
  c.clipRect(Skia.XYWHRect(0, y - 13, g.w, 26), ClipOp.Intersect, true);
  label(c, `${n}`, fonts.s17, 24, y - 20 * fr, hex(INK, 1 - fr), -1, TRACK.s17);
  if (fr > 0.001) label(c, `${n + 1}`, fonts.s17, 24, y + 20 * (1 - fr), hex(INK, fr), -1, TRACK.s17);
  const l0 = rowSwap(f, 0);
  if (1 - l0 > 0.001) label(c, 'veg dishes', fonts.s17, 24 + numW + 5, y - 10 * l0, hex(INK, 1 - l0), -1, TRACK.s17);
  if (l0 > 0.001) label(c, 'non-veg dishes', fonts.s17, 24 + numW + 5, y + 10 * (1 - l0), hex(INK, l0), -1, TRACK.s17);
  c.restore();
  label(c, 'Sort · Popular', fonts.r14, g.w - 24, y, hex(MUTED), 1, TRACK.r14);
  c.drawRect(Skia.XYWHRect(24, y + 26, g.w - 48, 1), fill(hex(RULE)));

  for (let i = 0; i < 3; i++) {
    const top = g.rowsY + 76 * i;
    const l = rowSwap(f, i);
    c.save();
    c.clipRect(Skia.XYWHRect(0, top + 4, g.w, 68), ClipOp.Intersect, true);
    for (let k = 0; k < 2; k++) {
      const a = k === 0 ? 1 - l : l;
      if (a <= 0.001) continue;
      const dish = DISHES[k][i];
      const dy = k === 0 ? -10 * l : 10 * (1 - l);
      drawMark(c, 32, top + 24 + dy, k === 0, a);
      label(c, dish[0], fonts.s17, 52, top + 24 + dy, hex(INK, a), -1, TRACK.s17);
      label(c, dish[1], fonts.r14, 52, top + 47 + dy, hex(MUTED, a), -1, TRACK.r14);
      label(c, dish[2], fonts.s16, g.w - 24, top + 24 + dy, hex(INK, a), 1, TRACK.s16);
    }
    c.restore();
    c.drawRect(Skia.XYWHRect(24, top + 75, g.w - 48, 1), fill(hex(RULE)));
  }
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(PAPER)));
  drawHeader(c, g, fonts);
  drawToggle(c, g, f, fonts);
  drawList(c, g, f, fonts);
}

/** Past either end the knob gives a little, then holds. */
function rubber(raw: number) {
  'worklet';
  if (raw < 0) {
    const o = -raw;
    return (-0.5 * o) / (1 + o / 0.25);
  }
  if (raw > 1) {
    const o = raw - 1;
    return 1 + (0.5 * o) / (1 + o / 0.25);
  }
  return raw;
}

export function VegModeToggle({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(() => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top), [size, insets.top]);
  const fonts = useMemo<Fonts>(() => {
    const family = Platform.select({ ios: 'System', default: 'sans-serif' });
    const f = (fontSize: number, fontWeight: '400' | '500' | '600' | '700') => matchFont({ fontFamily: family, fontSize, fontWeight });
    return { r13: f(13, '400'), s16: f(16, '600'), s12: f(12, '600'), b30: f(30, '700'), m25: f(25, '500'), s17: f(17, '600'), r14: f(14, '400') };
  }, []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(simFrame(makeSim(), 0));
  const now = useSharedValue(0);
  const grab = useSharedValue({ on: false, p0: 0, mode0: 0 });

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

  const travel = TRACK_W - KNOB_W;
  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const inside =
        e.x >= geo.trackX - 24 && e.x <= geo.trackX + TRACK_W + 24 && e.y >= geo.trackY - 36 && e.y <= geo.trackY + TRACK_H + 24;
      if (!inside) {
        grab.set({ on: false, p0: 0, mode0: 0 });
        return;
      }
      const d = driver.get();
      const t = now.get();
      const s = driverSession(d, t, auto, reduced);
      d.touching = true;
      simBeginDrag(s, t);
      grab.set({ on: true, p0: s.drag ? s.drag.p : s.mode, mode0: s.mode });
    })
    .onUpdate((e) => {
      const gr = grab.get();
      const d = driver.get();
      if (!gr.on || !d.live) return;
      simDragTo(d.live, rubber(gr.p0 - e.translationX / travel));
      driverTouched(d, now.get());
    })
    .onFinalize((e) => {
      const gr = grab.get();
      const d = driver.get();
      if (!gr.on || !d.live) return;
      const t = now.get();
      if (Math.hypot(e.translationX, e.translationY) < 8) {
        simRelease(d.live, t, 0, 1 - gr.mode0, ENTER_DELAY);
      } else {
        simRelease(d.live, t, Math.min(12, Math.max(-12, -e.velocityX / travel)));
      }
      d.touching = false;
      driverTouched(d, t);
      grab.set({ on: false, p0: 0, mode0: 0 });
      scheduleOnRN(feel);
    });

  const onAccessibilityTap = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const t = now.get();
      simTap(driverSession(d, t, auto, reduced), t);
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
        accessibilityRole="switch"
        accessibilityLabel="Veg mode"
        accessibilityHint="Switches the menu between veg and non-veg dishes."
        onAccessibilityTap={onAccessibilityTap}
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
  screen: { flex: 1, backgroundColor: '#F6F5F1' },
});

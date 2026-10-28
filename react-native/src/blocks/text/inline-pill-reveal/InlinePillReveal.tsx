import { Canvas, ClipOp, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode, useFont, type SkCanvas, type SkFont, type SkImage, type SkPoint } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { blurred, grainPaint, hex, makeGrain, scene, solid } from './inlinePillArt';
import {
  accepts,
  BASELINE_DROP,
  begin,
  BRAND,
  CARET_H,
  CARET_PART,
  CARET_W,
  CREAM,
  CTA,
  drag,
  type Driver,
  driverFrame,
  easeOut,
  emptyFrame,
  end,
  FONT_SIZE,
  type Frame,
  INK,
  lerp,
  LINE1,
  LINE2,
  makeDriver,
  PAGE,
  PILL_H,
  PILL_MARGIN,
  reveal,
  seg,
  shownAmount,
  SKIP,
  toggleReduced,
  TRACKING,
  WORD_GAP,
} from './inlinePillModel';

/*
 Inline Pill Reveal — the welcome screen of an invented slow-travel stays brand ("slowfield")
 (a port of InlinePillReveal.swift).

 Look: a flat terracotta screen with "Slow mornings," in Outfit, the brand mark and Skip at the
 top, page dots at the bottom. Hero: a caret blinks between the two words, then a pill opens in
 that gap and pushes the words apart by its real width until they slide off both edges; the pill
 drifts to the centre and grows into a full-bleed window (capsule corners → 0). Inside it is a
 sunrise over layered hills drawn in code (inlinePillArt.ts): as the pill grows the hills rise
 into frame and the sun climbs over the ridge. "made yours" rises over the scene, then a Find
 your stay button; the page dots move on. It holds, then plays back to the text line.

 autoPlay: LOOP = 8 s (timeline in inlinePillModel.ts); frame 8 s equals frame 0.
 Touch: drag sideways on the line (or anywhere once open) to scrub the reveal; release snaps open
 or closed with the finger's speed; tap toggles. 2 s after the last touch it eases closed and the
 loop starts again.
 Reduced motion: no loop; the revealed screen shows still, a tap crossfades to the text line.
*/

type Props = { autoPlay?: boolean };

/** Glyphs laid out with tracking; widths from getGlyphWidths (measureText rounds per glyph). */
type Run = { ids: number[]; pos: SkPoint[]; w: number };

type Texts = {
  big: SkFont;
  brand: SkFont;
  skip: SkFont;
  cta: SkFont;
  left: Run;
  right: Run;
  line1: Run;
  made: Run;
  yours: Run;
  line2: Run;
  brandRun: Run;
  skipRun: Run;
  ctaRun: Run;
};

function run(font: SkFont, s: string, track: number): Run {
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i] + track;
  }
  return { ids, pos, w: x };
}

function put(c: SkCanvas, r: Run, font: SkFont, x: number, baseline: number, color: number, a: number) {
  'worklet';
  c.drawGlyphs(r.ids, r.pos, x, baseline, font, solid(color, a));
}

function roundRect(x: number, y: number, w: number, h: number, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

/** Brand mark, Skip, page dots and the call to action. */
function chrome(c: SkCanvas, w: number, h: number, top: number, bottom: number, f: Frame, tx: Texts) {
  'worklet';
  const markY = top + 24;
  const sun = Skia.Path.Make();
  sun.addArc(Skia.XYWHRect(25, markY + 3 - 7, 14, 14), 180, 180);
  sun.close();
  c.drawPath(sun, solid(CREAM, 1));
  c.drawRRect(roundRect(22, markY + 5, 20, 1.8, 0.9), solid(CREAM, 1));
  put(c, tx.brandRun, tx.brand, 48, markY + 6, CREAM, 1);
  put(c, tx.skipRun, tx.skip, w - 24 - tx.skipRun.w, markY + 5, CREAM, 0.8);

  // Page dots.
  const dotY = h - bottom - 26;
  const act = [1 - f.dots, f.dots, 0];
  const widths = [6 + 14 * act[0], 6 + 14 * act[1], 6 + 14 * act[2]];
  let x = (w - (widths[0] + widths[1] + widths[2] + 16)) / 2;
  for (let i = 0; i < 3; i++) {
    c.drawRRect(roundRect(x, dotY - 3, widths[i], 6, 3), solid(CREAM, 0.45 + 0.55 * act[i]));
    x += widths[i] + 8;
  }

  // Call to action.
  const a = f.ctaIn * (1 - f.ctaOut);
  if (a > 0.001) {
    const dy = 16 * (1 - f.ctaIn) + 10 * f.ctaOut;
    const bx = 24;
    const by = h - bottom - 84 - 27 + dy;
    const bw = w - 48;
    const layer = Skia.Paint();
    layer.setAlphaf(a);
    c.saveLayer(layer);
    const shadow = blurred(solid(0x140805, 0.25), 12);
    c.drawRRect(roundRect(bx, by + 6, bw, 54, 27), shadow);
    c.drawRRect(roundRect(bx, by, bw, 54, 27), solid(CREAM, 1));
    const lw = tx.ctaRun.w;
    const gx = bx + bw / 2 - (lw + 10 + 14) / 2;
    const my = by + 27;
    put(c, tx.ctaRun, tx.cta, gx, my + 6, INK, 1);
    const ax = gx + lw + 10;
    const arrow = Skia.Path.Make();
    arrow.moveTo(ax, my);
    arrow.lineTo(ax + 14, my);
    arrow.moveTo(ax + 9, my - 5);
    arrow.lineTo(ax + 14, my);
    arrow.lineTo(ax + 9, my + 5);
    const ap = solid(INK, 1);
    ap.setStyle(PaintStyle.Stroke);
    ap.setStrokeWidth(1.8);
    ap.setStrokeCap(StrokeCap.Round);
    ap.setStrokeJoin(StrokeJoin.Round);
    c.drawPath(arrow, ap);
    c.restore();
  }
}

function drawScreen(c: SkCanvas, w: number, h: number, top: number, bottom: number, f: Frame, tx: Texts) {
  'worklet';
  const cy = h / 2;
  const baseline = cy + BASELINE_DROP;
  const wl = tx.left.w;
  const space = Math.max(6, tx.line1.w - wl - tx.right.w);
  const startX = (w - tx.line1.w) / 2;
  const gapX = startX + wl + space / 2;

  // The pill: inline (width from the gap, drifting to the centre), then full bleed.
  const o = f.open;
  const inlineW = lerp(0, w - 2 * PILL_MARGIN, o);
  const inlineX = lerp(gapX, w / 2, o);
  const inlineH = lerp(CARET_H, PILL_H, easeOut(seg(o, 0, 0.2)));
  const full = f.full;
  const rx = lerp(inlineX - inlineW / 2, 0, full);
  const ry = lerp(cy - inlineH / 2, 0, full);
  const rw = lerp(inlineW, w, full);
  const rh = lerp(inlineH, h, full);

  // Words ride the pill's edges.
  const restHalf = space / 2 + CARET_PART * f.part;
  const half = lerp(restHalf, WORD_GAP, easeOut(seg(o, 0, 0.15)));
  const leftEnd = (o > 0 ? rx : gapX) - half;
  const rightStart = (o > 0 ? rx + rw : gapX) + half;

  if (o > 0) {
    const dw = Math.max(rw, CARET_W);
    const dx = rx + rw / 2 - dw / 2;
    const radius = (Math.min(dw, rh) / 2) * (1 - full);
    const shape = roundRect(dx, ry, dw, rh, radius);
    const lift = 1 - full;
    if (lift > 0.01) {
      c.drawRRect(roundRect(dx, ry + 12, dw, rh, radius), blurred(solid(0x3a1a10, 0.2 * lift), 11));
      c.drawRRect(roundRect(dx, ry + 2, dw, rh, radius), blurred(solid(0x3a1a10, 0.18 * lift), 1.5));
    }
    c.save();
    if (full < 1) c.clipRRect(shape, ClipOp.Intersect, true);
    scene(c, w, h, cy, o, full, f.t);
    if (lift > 0.01) {
      const rim = blurred(solid(0x2a1208, 0.22 * lift), 4);
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(6);
      c.drawRRect(shape, rim);
    }
    c.restore();
    if (lift > 0.01) {
      const hi = Skia.Paint();
      hi.setAntiAlias(true);
      hi.setStyle(PaintStyle.Stroke);
      hi.setStrokeWidth(1);
      hi.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: ry }, { x: 0, y: ry + 20 }, [hex(0xffffff, 0.24 * lift), hex(0xffffff, 0)], null, TileMode.Clamp),
      );
      c.drawRRect(roundRect(dx + 0.5, ry + 0.5, dw - 1, rh - 1, Math.max(0, radius - 0.5)), hi);
    }
  }

  put(c, tx.left, tx.big, leftEnd - wl, baseline, CREAM, 1);
  put(c, tx.right, tx.big, rightStart, baseline, CREAM, 1);

  if (f.caret > 0.001) {
    c.drawRRect(roundRect(gapX - CARET_W / 2, cy - CARET_H / 2, CARET_W, CARET_H, CARET_W / 2), solid(CREAM, f.caret));
  }

  // Second line over the scene.
  const l2Start = (w - tx.line2.w) / 2;
  const l2Space = Math.max(6, tx.line2.w - tx.made.w - tx.yours.w);
  for (let i = 0; i < 2; i++) {
    const a = f.wordIn[i] * (1 - f.wordOut[i]);
    if (a < 0.001) continue;
    const dy = 18 * (1 - f.wordIn[i]) + 10 * f.wordOut[i];
    const r = i === 0 ? tx.made : tx.yours;
    const x = i === 0 ? l2Start : l2Start + tx.made.w + l2Space;
    c.drawGlyphs(r.ids, r.pos, x, baseline + dy + 2, tx.big, blurred(solid(0x2a1410, 0.55 * a), 10));
    put(c, r, tx.big, x, baseline + dy, CREAM, a);
  }

  chrome(c, w, h, top, bottom, f, tx);
}

export function InlinePillReveal({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 393;
  const h = size?.height ?? 852;
  const top = insets.top;
  const bottom = insets.bottom;

  const big = useFont(require('./assets/Outfit-Regular.ttf'), FONT_SIZE);
  const brand = useFont(require('./assets/Outfit-Medium.ttf'), 18);
  const skip = useFont(require('./assets/Outfit-Regular.ttf'), 15);
  const cta = useFont(require('./assets/Outfit-Medium.ttf'), 17);
  const texts = useMemo<Texts | null>(() => {
    if (!big || !brand || !skip || !cta) return null;
    return {
      big,
      brand,
      skip,
      cta,
      left: run(big, LINE1[0], TRACKING),
      right: run(big, LINE1[1], TRACKING),
      line1: run(big, LINE1.join(' '), TRACKING),
      made: run(big, LINE2[0], TRACKING),
      yours: run(big, LINE2[1], TRACKING),
      line2: run(big, LINE2.join(' '), TRACKING),
      brandRun: run(brand, BRAND, 0.2),
      skipRun: run(skip, SKIP, 0),
      ctaRun: run(cta, CTA, 0),
    };
  }, [big, brand, skip, cta]);
  const grain = useMemo<SkImage | null>(() => makeGrain(), []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame(0));
  const shown = useSharedValue(1);
  const now = useSharedValue(0);
  const grab = useSharedValue(false);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    const d = driver.get();
    frame.set(driverFrame(d, t, auto, reduced));
    shown.set(reduced ? shownAmount(d, t) : 1);
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (reduced) return;
      const d = driver.get();
      const t = now.get();
      if (!accepts(d, e.y, h / 2, t, auto)) {
        grab.set(false);
        return;
      }
      begin(d, t, auto);
      grab.set(true);
    })
    .onUpdate((e) => {
      if (!grab.get()) return;
      if (Math.abs(e.translationX) > 6) drag(driver.get(), e.translationX, w, now.get());
    })
    .onFinalize((e) => {
      const d = driver.get();
      const t = now.get();
      const moved = Math.hypot(e.translationX, e.translationY);
      if (reduced) {
        if (moved < 10) {
          toggleReduced(d, t);
          scheduleOnRN(feel);
        }
        return;
      }
      if (!grab.get()) return;
      grab.set(false);
      if (end(d, e.translationX, e.velocityX, moved < 8, w, t)) scheduleOnRN(feel);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), solid(PAGE, 1));
    if (texts) {
      const f = frame.get();
      if (reduced) {
        const s = shown.get();
        const a = Skia.Paint();
        a.setAlphaf(1 - s);
        c.saveLayer(a);
        drawScreen(c, w, h, top, bottom, reveal(0, 0), texts);
        c.restore();
        const b = Skia.Paint();
        b.setAlphaf(s);
        c.saveLayer(b);
        drawScreen(c, w, h, top, bottom, f, texts);
        c.restore();
      } else {
        drawScreen(c, w, h, top, bottom, f, texts);
      }
    }
    if (grain) {
      c.save();
      c.scale(0.5, 0.5);
      c.drawRect(Skia.XYWHRect(0, 0, w * 2, h * 2), grainPaint(grain, 0.045));
      c.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const toggle = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const t = now.get();
      if (reduced) {
        toggleReduced(d, t);
      } else {
        begin(d, t, auto);
        end(d, 0, 0, true, w, t);
      }
    });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Slow mornings, made yours. Find your stay."
        accessibilityHint="Opens or closes the morning view."
        onAccessibilityTap={toggle}
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
  screen: { flex: 1, backgroundColor: hexCss(PAGE) },
});

function hexCss(v: number) {
  return `#${v.toString(16).padStart(6, '0')}`;
}

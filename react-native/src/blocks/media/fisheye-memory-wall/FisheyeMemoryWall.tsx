import {
  BlurStyle,
  Canvas,
  FilterMode,
  ImageShader,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Rect,
  Shader,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeAtlas } from './atlas';
import { LENS_EFFECT } from './lensShader';
import {
  ATLAS_SCALE,
  CAPTIONS,
  clamp,
  driverBeginDrag,
  driverBeginPinch,
  driverDrag,
  driverEndDrag,
  driverEndPinch,
  driverPinch,
  driverPose,
  makeDriver,
  makePose,
  memoryAt,
  MONTHS,
  PERIOD_H,
  PERIOD_W,
  REST_X,
  REST_Y,
  TILES,
  wrap,
} from './model';

/*
 Fisheye Memory Wall — the year view of an invented journal app ("Keepsake"): "2026 in moments"
 (a port of FisheyeMemoryWall.swift).

 Look: an endless dark wall of small rounded tiles, each a tiny memory drawn in code (a pan of
 shakshuka, a sunset over the sea, a ridge, a flat white, a birthday cake, a skyline at night, a
 tulip, a beach umbrella, a hot-air balloon, a lemon tart, a tent under the stars, gradients and date
 cards), seen through a strong fisheye lens that fills the screen: tiles in the middle are 1.6 times
 their size, tiles near the rim shrink and curve away, and the rim falls into a dark vignette.

 Hero: the bulge. One Skia runtime effect traces every point back to the wall through a barrel
 function and samples a pre-rendered atlas of one wall period (repeat tiling), so the wall never ends
 and the tiles stay crisp. The caption card names the memory under the centre, ringed in white at rest.

 autoPlay: LOOP = 9 s. Rest 0–1 s on the birthday cake with a focus ring. 1.0–7.6 s the wall pans one
 whole period sideways (soft start, cruise, soft stop) with an S-shaped bob (90 pt · sin 2πe); the
 bulge grows from k 1 to 1.42 (1.0–2.8 s), relaxes (5.6–7.6 s) a little past rest and settles by
 8.6 s; the ring fades out at 1.0–1.4 s and back at 7.8–8.4 s. One period on, the wall is where it
 began, so the last frame is the first.
 Touch: drag to wander (the centre tile follows the finger 1 : 1, a throw keeps momentum), pinch to
 strengthen or relax the bulge. A light tick when a new memory reaches the centre. 2 s after the last
 touch the wall eases to the nearest copy of rest (1.2 s) and the loop restarts.
 Reduced motion: the still rest frame (the lens stays); drag still pans, no loop.
*/

type Props = {
  /** Plays the 9 s pan-and-bulge loop until touched. */
  autoPlay?: boolean;
};

const BACKGROUND = 0x07090d;
const INK = 0xf5f3ee;
const ACCENT = 0xff9a6b;

type Fonts = {
  brand: SkFont;
  title: SkFont;
  sub: SkFont;
  month: SkFont;
  day: SkFont;
  caption: SkFont;
  place: SkFont;
  hint: SkFont;
};

// Drawing helpers (worklets, declared above their callers).

function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

function fill(color: SkColor): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

/** SwiftUI's blur radius as a Skia sigma. */
function sigma(r: number) {
  'worklet';
  return r * 1.5 * 0.57735 + 0.5;
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

function scrim(c: SkCanvas, y0: number, y1: number, w: number, a0: number, am: number, a1: number) {
  'worklet';
  const p = Skia.Paint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: y0 },
      { x: 0, y: y1 },
      [hex(BACKGROUND, a0), hex(BACKGROUND, am), hex(BACKGROUND, a1)],
      [0, a0 > a1 ? 0.45 : 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, y0, w, y1 - y0), p);
}

function drawHeader(c: SkCanvas, top: number, fonts: Fonts) {
  'worklet';
  const y = top + 12;
  label(c, 'KEEPSAKE', fonts.brand, 24, y + 8, hex(INK, 0.55), -1, 2.2);
  label(c, '2026 in moments', fonts.title, 24, y + 38, hex(INK, 1), -1, -0.6);
  label(c, '1,284 memories · 212 days', fonts.sub, 24, y + 66, hex(INK, 0.6), -1, 0);
}

function drawCaption(c: SkCanvas, w: number, h: number, bottom: number, tile: number, depth: number, fonts: Fonts) {
  'worklet';
  const x = 20;
  const y = h - Math.max(bottom, 16) - 46 - 64;
  const cw = w - 40;
  const ch = 64;
  const card = Skia.RRectXY(Skia.XYWHRect(x, y, cw, ch), 22, 22);
  const shadow = fill(hex(0x000000, 0.35));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(16), true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 8, cw, ch), 22, 22), shadow);
  c.drawRRect(card, fill(hex(0x0f131a, 0.82)));
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y }, { x: 0, y: y + ch }, [hex(0xffffff, 0.16), hex(0xffffff, 0.04)], null, TileMode.Clamp),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, cw - 1, ch - 1), 21.5, 21.5), edge);
  // The memory under the centre; its text dims while the centre crosses a gutter.
  const t = TILES[tile];
  const a = 0.3 + 0.7 * clamp((depth + 2.5) / 8, 0, 1);
  const cap = CAPTIONS[t.design];
  label(c, MONTHS[t.month], fonts.month, x + 36, y + 21, hex(ACCENT, a), 0, 1.4);
  label(c, `${t.day}`, fonts.day, x + 36, y + 41, hex(INK, a), 0, 0);
  c.drawRect(Skia.XYWHRect(x + 70, y + 14, 1, 36), fill(hex(0xffffff, 0.12)));
  label(c, cap[0], fonts.caption, x + 86, y + 24, hex(INK, a), -1, 0);
  label(c, cap[1], fonts.place, x + 86, y + 44, hex(INK, 0.55 * a), -1, 0);
  // An open chevron.
  const b = Skia.PathBuilder.Make();
  b.moveTo(x + cw - 26, y + ch / 2 - 6);
  b.lineTo(x + cw - 20, y + ch / 2);
  b.lineTo(x + cw - 26, y + ch / 2 + 6);
  const stroke = fill(hex(INK, 0.45));
  stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(1.8);
  stroke.setStrokeCap(StrokeCap.Round);
  stroke.setStrokeJoin(StrokeJoin.Round);
  c.drawPath(b.detach(), stroke);
  label(c, 'Drag to wander · pinch to bulge', fonts.hint, w / 2, y + ch + 18, hex(INK, 0.4), 0, 0);
}

const FAMILY = Platform.select({ ios: 'System', default: 'sans-serif' });

function font(size: number, weight: '500' | '600' | '700'): SkFont {
  return matchFont({ fontFamily: FAMILY, fontSize: size, fontWeight: weight });
}

export function FisheyeMemoryWall({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 393;
  const h = size?.height ?? 852;
  const top = insets.top;
  const bottom = insets.bottom;
  const scale = PixelRatio.get();
  const atlas = useMemo(() => bakeAtlas(), []);
  const fonts = useMemo<Fonts>(
    () => ({
      brand: font(11, '600'),
      title: font(30, '700'),
      sub: font(13, '500'),
      month: font(10, '700'),
      day: font(22, '700'),
      caption: font(16, '600'),
      place: font(12, '500'),
      hint: font(11, '500'),
    }),
    [],
  );

  const driver = useSharedValue(makeDriver());
  const pose = useSharedValue(makePose(0, 0, 1, 1));
  const now = useSharedValue(0);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    pose.set(driverPose(driver.get(), t, auto));
  });

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      driverBeginDrag(driver.get(), now.get(), auto);
    })
    .onUpdate((e) => {
      if (driverDrag(driver.get(), e.translationX, e.translationY)) scheduleOnRN(feel);
    })
    .onFinalize((e) => {
      driverEndDrag(driver.get(), e.velocityX, e.velocityY, now.get());
    });
  const pinch = Gesture.Pinch()
    .onStart(() => {
      driverBeginPinch(driver.get(), now.get(), auto);
    })
    .onUpdate((e) => {
      driverPinch(driver.get(), e.scale);
    })
    .onFinalize(() => {
      driverEndPinch(driver.get(), now.get());
    });
  const touch = Gesture.Simultaneous(pan, pinch);

  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    // The memory under the centre gets a focus ring; it dims while the centre crosses a gutter.
    const m = memoryAt(REST_X + p.x, REST_Y + p.y);
    const t = TILES[m.tile];
    const ring = p.ring * clamp((m.depth + 2.5) / 8, 0, 1);
    return {
      lens: [w / 2, h / 2, w * 0.53, h * 0.5],
      view: [wrap(REST_X + p.x, PERIOD_W), wrap(REST_Y + p.y, PERIOD_H), p.k, ATLAS_SCALE / scale],
      wall: [PERIOD_W, PERIOD_H, 1 / scale, ATLAS_SCALE],
      focus: [t.x, t.y, t.width, t.height],
      ring: [t.radius, ring],
      background: [7 / 255, 9 / 255, 13 / 255, 1],
    };
  });

  const overlay = useDerivedValue(() => {
    const p = pose.get();
    const m = memoryAt(REST_X + p.x, REST_Y + p.y);
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    // Scrims keep the header and the caption readable over the rim.
    scrim(c, 0, top + 170, w, 0.94, 0.7, 0);
    scrim(c, h - bottom - 210, h, w, 0, 0.75, 0.94);
    drawHeader(c, top, fonts);
    drawCaption(c, w, h, bottom, m.tile, m.depth, fonts);
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
        accessibilityLabel="2026 in moments. A wall of memories under a fisheye lens. Drag to wander, pinch to change the bulge."
      >
        <StatusBar style="light" />
        {size && atlas && LENS_EFFECT ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={w} height={h}>
              <Shader source={LENS_EFFECT} uniforms={uniforms}>
                <ImageShader
                  image={atlas}
                  fit="none"
                  tx="repeat"
                  ty="repeat"
                  sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
                />
              </Shader>
            </Rect>
            <Picture picture={overlay} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#07090D' },
});

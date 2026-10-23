import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, type GestureType } from 'react-native-gesture-handler';
import {
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { DRIFT, LOOP, type Icon, cycles, drawIcon, drawPhoto, easeInOut, grade, label, rgb, scripted, smoothstep, waveHeight } from './wave-model';

/*
 Wave Scrub Slider — a photo editor's contrast control drawn as a living wave. A port of the
 SwiftUI block (WaveScrubSlider.swift); the numbers are the same.

 Look: a dusk lake (drawn in code) fills the top 60 % of the screen; a near-black panel
 (#0E0E0E, top corners 24) slides over its foot with an orange toolbar (back, undo, redo, more,
 SHARE), a strip of seven tools with Contrast selected, and a card (#161616, corners 18) holding
 a perforated grille, the label CONTRAST, the value, and the slider. The slider is one line:
 left of the thumb an orange (#F39A1C) wave with a soft glow, right of it a grey (#4A4A4A)
 ripple. The waves tighten towards the right (2 to 15 cycles per track width) and tighten more
 as the value rises; the orange part's height grows with the value up to 15 pt (it rises from
 the line over the first 30 % of the track), the grey ripple is 1.2 + 5 × value pt; both meet
 the line at the thumb (a 14 × 42 pill). The crests always travel right at 1 cycle a second, so
 the line wiggles even when the thumb is still; at 0 only the faint grey ripple is left.
 The photo is graded live: contrast 1 → 1.45, saturation 1 → 1.3.

 autoPlay timeline (LOOP = 8 s; the scripted finger, eased in and out between keys):
   0.0 – 0.6   rest at 0: a flat line and the grey ripple.
   0.6 – 2.2   up to +62.
   2.2 – 2.9   hold (the wave keeps travelling).
   2.9 – 3.9   up to +100.
   3.9 – 5.0   hold.
   5.0 – 6.6   back to 0.
   6.6 – 8.0   rest. The drift is a whole number of cycles per loop, so 8.0 is the frame at 0.0.

 Touch: press anywhere on the card and drag; the thumb glides to the finger (0.18 s) and then
 follows it, grows 12 % and gets an orange halo; a selection haptic at each wave cycle passed.
 2 s after release the value eases back to 0 (0.8 s) and the loop starts again (on a whole
 second of the drift, so the wave never jumps).
 Reduced motion: no loop and no drift; the screen shows +60 still, and dragging still works.
 */

const ORANGE = 0xf39a1c;
const INK = '#0E0E0E';
const REDUCED_VALUE = 0.6;
const CARD_H = 128;
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
const THUMB_SPRING = { mass: 1, stiffness: 440, damping: 25 };

type Layout = {
  w: number;
  h: number;
  rowTop: number;
  cardTop: number;
  cardX: number;
  cardW: number;
  stripTop: number;
  row1: number;
  panelTop: number;
  photoH: number;
  trackX0: number;
  trackW: number;
  waveY: number;
};

function makeLayout(w: number, h: number, bottom: number): Layout {
  const rowTop = h - Math.max(bottom, 16) - 48;
  const cardTop = rowTop - 20 - CARD_H;
  const cardX = 16;
  const cardW = w - 32;
  const stripTop = cardTop - 20 - 52;
  const row1 = stripTop - 12 - 16;
  const panelTop = row1 - 30;
  return {
    w,
    h,
    rowTop,
    cardTop,
    cardX,
    cardW,
    stripTop,
    row1,
    panelTop,
    photoH: panelTop + 24,
    trackX0: cardX + 22,
    trackW: cardW - 44,
    waveY: cardTop + 82,
  };
}

function selection() {
  Haptics.selectionAsync().catch(() => {});
}

/** The card's label and value, and the slider's touch area (whose accessibility value is the same text). */
function ContrastReadout({ value, initial, gesture, l }: { value: SharedValue<number>; initial: number; gesture: GestureType; l: Layout }) {
  const [text, setText] = useState({ value: label(initial), on: initial > 0.004 });
  useAnimatedReaction(
    () => {
      const v = value.get();
      return `${label(v)}|${v > 0.004 ? 1 : 0}`;
    },
    (key, previous) => {
      if (key !== previous) {
        const [t, on] = key.split('|');
        scheduleOnRN(setText, { value: t, on: on === '1' });
      }
    },
  );
  return (
    <>
      <View style={[styles.cardHead, { left: l.cardX + 18, top: l.cardTop + 14, width: l.cardW - 36 }]} pointerEvents="none">
        <Text style={styles.cardLabel}>CONTRAST</Text>
        <Text style={[styles.value, { color: text.on ? '#F39A1C' : 'rgba(255,255,255,0.7)' }]}>{text.value}</Text>
      </View>
      <GestureDetector gesture={gesture}>
        <View
          style={[styles.touch, { left: l.cardX, top: l.cardTop, width: l.cardW }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Contrast"
          accessibilityValue={{ text: text.value }}
        />
      </GestureDetector>
    </>
  );
}

export function WaveScrubSlider({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => makeLayout(size.width, size.height, insets.bottom), [size.width, size.height, insets.bottom]);

  // Times are seconds on the frame clock; -1 means "not set".
  const clock = useSharedValue(-1);
  /** The drift's t = 0; the loop always starts a whole number of seconds after it. */
  const origin = useSharedValue(-1);
  const autoStart = useSharedValue(-1);
  const manual = useSharedValue(reduced ? REDUCED_VALUE : 0);
  const glideAt = useSharedValue(-1);
  const glideFrom = useSharedValue(0);
  const returnAt = useSharedValue(-1);
  const returnFrom = useSharedValue(0);
  const resumeAt = useSharedValue(-1);
  const lastTick = useSharedValue(0);
  const thumbScale = useSharedValue(1);
  const halo = useSharedValue(0);

  const valueAt = (at: number) => {
    'worklet';
    const start = autoStart.get();
    if (start >= 0 && at >= start && !reduced) return scripted((at - start) % LOOP);
    if (returnAt.get() >= 0) return returnFrom.get() * (1 - easeInOut((at - returnAt.get()) / 0.8));
    if (glideAt.get() >= 0) {
      const u = (at - glideAt.get()) / 0.18;
      if (u < 1) return manual.get() + (glideFrom.get() - manual.get()) * Math.pow(1 - u, 3);
    }
    return manual.get();
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (origin.get() < 0) {
      origin.set(now);
      if (autoPlay && !reduced) autoStart.set(now);
    }
    clock.set(now);
    // 2 s after a touch: ease back to 0 over 0.8 s, then the loop, on a whole second of the drift.
    const r = resumeAt.get();
    if (r >= 0 && now >= r) {
      resumeAt.set(-1);
      returnAt.set(now);
      returnFrom.set(manual.get());
      glideAt.set(-1);
      autoStart.set(origin.get() + Math.ceil(now - origin.get() + 0.8));
      manual.set(0);
    }
  });

  // The value's text changes a hundred times a sweep at most; it is text on the JS side, in its own component
  // (ContrastReadout) so the whole screen does not re-render with it.
  const value = useDerivedValue(() => valueAt(clock.get()));

  const gesture = useMemo(() => {
    const target = (x: number) => {
      'worklet';
      return Math.min(Math.max((x - 22) / l.trackW, 0), 1);
    };
    const follow = (x: number) => {
      'worklet';
      const t = target(x);
      manual.set(t);
      const tick = Math.floor(cycles(t, t));
      if (tick !== lastTick.get()) {
        lastTick.set(tick);
        scheduleOnRN(selection);
      }
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = clock.get();
        resumeAt.set(-1);
        const current = valueAt(now);
        autoStart.set(-1);
        returnAt.set(-1);
        lastTick.set(Math.floor(cycles(current, current)));
        if (Math.abs(target(e.x) - current) * l.trackW > 12) {
          glideAt.set(now);
          glideFrom.set(current);
        } else {
          glideAt.set(-1);
        }
        thumbScale.set(withSpring(1.12, THUMB_SPRING));
        halo.set(withSpring(1, THUMB_SPRING));
        follow(e.x);
      })
      .onUpdate((e) => follow(e.x))
      .onFinalize(() => {
        thumbScale.set(withSpring(1, THUMB_SPRING));
        halo.set(withSpring(0, THUMB_SPRING));
        if (autoPlay && !reduced) resumeAt.set(clock.get() + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [l, reduced, autoPlay]);

  /** The photo, recorded once per size. */
  const photo = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.w, l.photoH));
    if (l.w > 0) drawPhoto(canvas, l.w, l.photoH);
    return recorder.finishRecordingAsPicture();
  });

  /** The panel's still parts: its sheet, the toolbar, the tool strip, the card, its grille and the bottom bar. */
  const panel = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.w, l.h));
    if (l.w <= 0) return recorder.finishRecordingAsPicture();
    const paint = (color: Float32Array) => {
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setColor(color);
      return p;
    };
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, l.panelTop, l.w, l.h - l.panelTop + 40), 24, 24), paint(rgb(0x0e0e0e)));
    // Grabber.
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(l.w / 2 - 18, l.panelTop + 8, 36, 4), 2, 2), paint(Float32Array.of(1, 1, 1, 0.14)));
    // Toolbar.
    drawIcon(canvas, 'back', 28, l.row1, rgb(ORANGE));
    drawIcon(canvas, 'undo', 70, l.row1, rgb(ORANGE));
    drawIcon(canvas, 'redo', 108, l.row1, rgb(ORANGE, 0.35));
    drawIcon(canvas, 'more', l.w - 104, l.row1, rgb(ORANGE));
    // Tool strip.
    const tools: Icon[] = ['auto', 'light', 'contrast', 'color', 'vignette', 'grain', 'sharpen'];
    const cell = (l.w - 32) / tools.length;
    tools.forEach((t, i) => {
      const cx = 16 + cell * (i + 0.5);
      const cy = l.stripTop + 22;
      if (i === 2) canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 22, cy - 22, 44, 44), 12, 12), paint(rgb(0x242424)));
      drawIcon(canvas, t, cx, cy, i === 2 ? Float32Array.of(1, 1, 1, 1) : Float32Array.of(1, 1, 1, 0.38), 22);
      if (i === 1) canvas.drawCircle(cx, l.stripTop + 48, 2, paint(rgb(ORANGE)));
    });
    // Card.
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(l.cardX, l.cardTop, l.cardW, CARD_H), 18, 18), paint(rgb(0x161616)));
    // The card's perforated grille: dots every 8 pt, fading out at the card's edges.
    const gw = l.cardW;
    const gh = CARD_H - 34;
    for (let y = 6; y < gh; y += 8) {
      for (let x = 6 + (Math.floor(y / 8) % 2 === 0 ? 0 : 4); x < gw; x += 8) {
        const edge = Math.min(x, gw - x, y, gh - y);
        canvas.drawCircle(l.cardX + x, l.cardTop + 34 + y, 1.1, paint(Float32Array.of(1, 1, 1, 0.1 * smoothstep(0, 28, edge))));
      }
    }
    // Bottom bar.
    const bar: Icon[] = ['crop', 'brush', 'dial', 'layers', 'sky'];
    const bc = (l.w - 32) / bar.length;
    bar.forEach((t, i) =>
      drawIcon(canvas, t, 16 + bc * (i + 0.5), l.rowTop + 22, i === 2 ? Float32Array.of(1, 1, 1, 1) : Float32Array.of(1, 1, 1, 0.45)),
    );
    canvas.drawCircle(16 + bc * 2.5, l.rowTop + 44, 2, paint(Float32Array.of(1, 1, 1, 1)));
    return recorder.finishRecordingAsPicture();
  });

  const scene = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.w, l.h));
    if (l.w <= 0) return recorder.finishRecordingAsPicture();
    const now = clock.get();
    const v = valueAt(now);
    const phase = reduced || origin.get() < 0 ? 0 : (now - origin.get()) * DRIFT;
    // The photo, graded.
    const layer = Skia.Paint();
    layer.setColorFilter(Skia.ColorFilter.MakeMatrix(grade(v)));
    canvas.saveLayer(layer, Skia.XYWHRect(0, 0, l.w, l.photoH));
    canvas.drawPicture(photo.get());
    canvas.restore();
    // The status bar reads on a light shade at the top.
    const shade = Skia.Paint();
    shade.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 120 }, [Float32Array.of(0, 0, 0, 0.4), Float32Array.of(0, 0, 0, 0)], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, l.w, 120), shade);
    canvas.drawPicture(panel.get());

    // The wave.
    const tw = l.trackW;
    const thumb = v * tw;
    const y = (x: number) => l.waveY - waveHeight(x / tw, v, phase, tw);
    const active = Skia.Path.Make();
    const rest = Skia.Path.Make();
    let x = 0;
    active.moveTo(l.trackX0, y(0));
    while (x < thumb) {
      x = Math.min(x + 1.5, thumb);
      active.lineTo(l.trackX0 + x, y(x));
    }
    rest.moveTo(l.trackX0 + thumb, y(thumb));
    while (x < tw) {
      x = Math.min(x + 1.5, tw);
      rest.lineTo(l.trackX0 + x, y(x));
    }
    const stroke = (width: number, color: Float32Array) => {
      const p = Skia.Paint();
      p.setAntiAlias(true);
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeWidth(width);
      p.setStrokeCap(StrokeCap.Round);
      p.setStrokeJoin(StrokeJoin.Round);
      p.setColor(color);
      return p;
    };
    canvas.drawPath(rest, stroke(2, rgb(0x4a4a4a)));
    if (thumb > 0.5) {
      const glow = stroke(4, rgb(ORANGE, 0.55));
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      canvas.drawPath(active, glow);
      canvas.drawPath(active, stroke(2.2, rgb(ORANGE)));
    }
    // The tool strip's dot under Contrast: on once the value leaves 0.
    const dot = Skia.Paint();
    dot.setAntiAlias(true);
    dot.setColor(rgb(ORANGE));
    if (v > 0.004) canvas.drawCircle(16 + ((l.w - 32) / 7) * 2.5, l.stripTop + 48, 2, dot);

    // The thumb.
    const cx = l.trackX0 + thumb;
    const cy = l.waveY;
    const k = thumbScale.get();
    canvas.save();
    canvas.translate(cx, cy);
    canvas.scale(k, k);
    canvas.translate(-cx, -cy);
    const h = Math.min(Math.max(halo.get(), 0), 1);
    if (h > 0.01) {
      const hp = Skia.Paint();
      hp.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, 34, [rgb(ORANGE, 0.35 * h), rgb(ORANGE, 0)], null, TileMode.Clamp));
      canvas.drawCircle(cx, cy, 34, hp);
    }
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    shadow.setColor(Float32Array.of(0, 0, 0, 0.55));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 7, cy - 21 + 2, 14, 42), 7, 7), shadow);
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setColor(rgb(0x1e1e1e));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 7, cy - 21, 14, 42), 7, 7), body);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 6.5, cy - 20.5, 13, 41), 6.5, 6.5), stroke(1, Float32Array.of(1, 1, 1, 0.28)));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - 1.5, cy - 12, 3, 24), 1.5, 1.5), dot);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {size.width > 0 ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={scene} />
          </Canvas>
          <Text style={[styles.share, { left: l.w - 20 - 80, top: l.row1 - 16 }]}>SHARE</Text>
          <ContrastReadout value={value} initial={reduced ? REDUCED_VALUE : 0} gesture={gesture} l={l} />
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: INK },
  share: {
    position: 'absolute',
    width: 80,
    height: 32,
    lineHeight: 32,
    textAlign: 'right',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.6,
    color: '#F39A1C',
  },
  cardHead: { position: 'absolute', height: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardLabel: { fontSize: 11, fontWeight: '600', letterSpacing: 1.4, color: '#8C8C8C' },
  value: { fontSize: 13, fontWeight: '500', fontFamily: MONO, fontVariant: ['tabular-nums'] },
  touch: { position: 'absolute', height: CARD_H },
});

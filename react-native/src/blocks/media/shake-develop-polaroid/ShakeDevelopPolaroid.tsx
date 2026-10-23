import {
  BlurStyle,
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  type SkImage,
  TileMode,
  useImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP, type Scene, autoScene, colorMatrix, easeInOut, haze, restScene, smoothstep } from './motion';

/*
 Shake Develop Polaroid — a freshly ejected instant print that develops as you shake it. A port of the
 SwiftUI block (ShakeDevelopPolaroid.swift); the numbers are the same.

 Look: a dark aubergine viewer (#1F161C, a soft warm lift #3A2632 behind the print). Header: a back
 chevron and a "•••" in 36 pt translucent circles, "Today" over "10:10 AM" in the middle. The print: a
 warm white instant film (#F6F3ED → #ECE8E0, corner 4 pt) 80 % of the width (at most 330 pt), a square
 photo inset 16 pt with a 64 pt bottom margin, centred at 45 % of the height, with a deep shadow that
 slides against the shake. Under it a phone glyph and "Shake to develop" (or a check and "Developed"),
 and a 140 pt bar for how far it has developed. A bottom bar: share, the roll (a 40 pt thumbnail of the
 developed photo, white 2 pt border), trash.

 Development and the autoPlay timeline (LOOP = 8 s): motion.ts.

 Touch: drag the print back and forth: it follows the finger (90 % across, 25 % down, tilting 12° per
 314 pt) and every point of sideways travel develops it (1/1600 per point); on release it springs back
 (e^(−5 s), 2.2 Hz). A light haptic every tenth, a success haptic when developed, then the glint. 2 s after
 the last touch the print finishes developing by itself (0.8 s) and the loop carries on from the drop into
 the roll. (The Swift and Kotlin versions also develop when the phone itself is shaken; this one has no
 motion sensor package, so it takes the drag only.)
 Reduced motion: no loop and no shake; the print shows developed, with "Developed".
 */

const PHOTO = require('./assets/lake.jpg');
const MINT = '#8CE6CC';

type Layout = {
  width: number;
  height: number;
  cardX: number;
  cardY: number;
  cardW: number;
  cardH: number;
  photo: number;
  hintY: number;
  dockY: number;
};

function layoutFor(width: number, height: number, bottomInset: number): Layout {
  const cardW = Math.min(width * 0.8, 330);
  const cardH = 16 + (cardW - 32) + 64;
  const cardY = height * 0.45 - cardH / 2;
  return {
    width,
    height,
    cardX: (width - cardW) / 2,
    cardY,
    cardW,
    cardH,
    photo: cardW - 32,
    hintY: cardY + cardH + 36,
    dockY: height - Math.max(bottomInset, 20) - 30,
  };
}

/** The block's state on the UI thread; times are seconds on the frame clock, -1 for "not set". */
type Motion = {
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  dev: SharedValue<number>;
  dragging: SharedValue<boolean>;
  dragX: SharedValue<number>;
  dragY: SharedValue<number>;
  lastX: SharedValue<number>;
  /** [date, x, y, rotation] or []. */
  release: SharedValue<number[]>;
  /** [date, amplitude] or []. */
  kick: SharedValue<number[]>;
  developed: SharedValue<boolean>;
  developedAt: SharedValue<number>;
  /** [date, from] or []. */
  finishing: SharedValue<number[]>;
  holding: SharedValue<boolean>;
  active: SharedValue<boolean>;
  resumeAt: SharedValue<number>;
  finishEnd: SharedValue<number>;
};

function sceneAt(m: Motion, at: number, reduced: boolean): Scene {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) return autoScene((at - start) % LOOP);
  const s = restScene();
  s.develop = m.dev.get();
  const f = m.finishing.get();
  if (f.length === 2) {
    const u = at - f[0];
    s.develop = f[1] + (1 - f[1]) * easeInOut(u / 0.8);
    if (f[1] < 1) s.glint = u < 0.75 ? u / 0.75 : -1;
    s.label = f[1] >= 1 ? 1 : smoothstep(0.5, 0.8, u);
  } else if (m.developed.get()) {
    const u = at - m.developedAt.get();
    s.label = smoothstep(0, 0.3, u);
    if (u >= 0 && u < 0.7) s.glint = u / 0.7;
  }
  const r = m.release.get();
  if (m.dragging.get()) {
    s.x = m.dragX.get() * 0.9;
    s.y = m.dragY.get() * 0.25;
    s.rotation = (s.x / 314) * 12;
  } else if (r.length === 4 && !reduced) {
    const tau = at - r[0];
    const k = Math.exp(-5 * tau) * Math.cos(2 * Math.PI * 2.2 * tau);
    s.x = r[1] * k;
    s.y = r[2] * k;
    s.rotation = r[3] * k;
  }
  const kk = m.kick.get();
  if (kk.length === 2 && !reduced) {
    const tau = at - kk[0];
    const k = Math.exp(-6 * tau) * Math.sin(2 * Math.PI * 3 * tau);
    s.x += kk[1] * k;
    s.rotation += (kk[1] / 314) * 40 * k;
  }
  return s;
}

function tactile(kind: 'tick' | 'done') {
  if (kind === 'done') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function drawPrint(s: Scene, l: Layout, image: SkImage) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
  if (s.alpha <= 0) return recorder.finishRecordingAsPicture();
  const w = l.cardW;
  const h = l.cardH;
  const p = l.photo;
  const midX = l.cardX + w / 2;
  const midY = l.cardY + h / 2;
  const cx = midX + (l.width / 2 - midX) * s.toRoll + s.x;
  const cy = midY + (l.dockY - midY) * s.toRoll + s.y;
  const rotation = s.rotation * (1 - s.toRoll);

  const layer = Skia.Paint();
  layer.setAlphaf(s.alpha);
  canvas.saveLayer(layer);
  // The shadow's offset is on screen, so it is drawn before the print is turned.
  const shadow = Skia.Paint();
  shadow.setColor(Float32Array.of(0, 0, 0, 0.55));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12 * s.scale, true));
  canvas.save();
  canvas.translate(cx - s.x * 0.35, cy + 16 + s.y * 0.3);
  canvas.rotate(rotation, 0, 0);
  canvas.scale(s.scale, s.scale);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-w / 2, -h / 2, w, h), 4, 4), shadow);
  canvas.restore();

  canvas.translate(cx, cy);
  canvas.rotate(rotation, 0, 0);
  canvas.scale(s.scale, s.scale);
  canvas.translate(-w / 2, -h / 2);
  const paper = Skia.Paint();
  paper.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h }, [Skia.Color('#F6F3ED'), Skia.Color('#ECE8E0')], null, TileMode.Clamp),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 0, w, h), 4, 4), paper);

  const rect = Skia.XYWHRect(16, 16, p, p);
  const photo = Skia.Paint();
  photo.setColorFilter(Skia.ColorFilter.MakeMatrix(colorMatrix(s.develop)));
  canvas.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), rect, photo);
  // The veil, as nine stops from the top (v = 1) to the bottom (v = 0).
  const colors: Float32Array[] = [];
  const stops: number[] = [];
  for (let i = 0; i <= 8; i++) {
    colors.push(Float32Array.of(0.843, 0.867, 0.855, haze(s.develop, 1 - i / 8)));
    stops.push(i / 8);
  }
  const veil = Skia.Paint();
  veil.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 16 }, { x: 0, y: 16 + p }, colors, stops, TileMode.Clamp));
  canvas.drawRect(rect, veil);
  if (s.glint >= 0) {
    const g = -0.2 + 1.4 * s.glint;
    const a = 0.35 * Math.sin(Math.PI * s.glint);
    const glint = Skia.Paint();
    glint.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 16, y: 16 },
        { x: 16 + p, y: 16 + p },
        [Float32Array.of(1, 1, 1, 0), Float32Array.of(1, 1, 1, a), Float32Array.of(1, 1, 1, 0)],
        [Math.max(0, g - 0.15), Math.min(1, Math.max(0, g)), Math.min(1, g + 0.15)],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(rect, glint);
  }
  const edge = Skia.Paint();
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.5);
  edge.setColor(Float32Array.of(0, 0, 0, 0.15));
  canvas.drawRect(Skia.XYWHRect(16.25, 16.25, p - 0.5, p - 0.5), edge);
  canvas.restore();
  return recorder.finishRecordingAsPicture();
}

export function ShakeDevelopPolaroid({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const image = useImage(PHOTO);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const l = useMemo(() => layoutFor(size.width, size.height, insets.bottom), [size.width, size.height, insets.bottom]);

  const m: Motion = {
    clock: useSharedValue(-1),
    autoStart: useSharedValue(-1),
    dev: useSharedValue(reduced ? 1 : 0),
    dragging: useSharedValue(false),
    dragX: useSharedValue(0),
    dragY: useSharedValue(0),
    lastX: useSharedValue(0),
    release: useSharedValue<number[]>([]),
    kick: useSharedValue<number[]>([]),
    developed: useSharedValue(reduced),
    developedAt: useSharedValue(-1e9),
    finishing: useSharedValue<number[]>([]),
    holding: useSharedValue(false),
    active: useSharedValue(false),
    resumeAt: useSharedValue(-1),
    finishEnd: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.clock.get() < 0) {
      if (autoPlay && !reduced) m.autoStart.set(now);
      m.clock.set(now);
    }
    // 2 s after the last touch the print finishes developing by itself (0.8 s), then the loop carries on
    // from the drop into the roll.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      m.resumeAt.set(-1);
      if (reduced) {
        m.dragging.set(false);
        m.active.set(false);
      } else {
        m.finishing.set([now, m.dev.get()]);
        m.release.set([]);
        m.kick.set([]);
        m.finishEnd.set(now + 0.8);
      }
    }
    const finishEnd = m.finishEnd.get();
    if (finishEnd >= 0 && now >= finishEnd) {
      m.finishEnd.set(-1);
      m.finishing.set([]);
      if (autoPlay) {
        m.dev.set(0);
        m.developed.set(false);
        m.autoStart.set(now - 5.9);
      } else {
        m.dev.set(1);
        m.developed.set(true);
        m.developedAt.set(-1e9);
        m.active.set(false);
      }
    }
    if ((m.autoStart.get() >= 0 && !reduced) || m.active.get()) m.clock.set(now);
  });

  const scene = useDerivedValue(() => sceneAt(m, m.clock.get(), reduced));
  const print = useDerivedValue(() => {
    if (!image || size.width === 0) {
      // An empty picture: finishing a recorder that never began recording crashes the app.
      const recorder = Skia.PictureRecorder();
      recorder.beginRecording(Skia.XYWHRect(0, 0, 1, 1));
      return recorder.finishRecordingAsPicture();
    }
    return drawPrint(scene.get(), l, image);
  });

  const gesture = useMemo(() => {
    const takeOver = (now: number) => {
      'worklet';
      m.resumeAt.set(-1);
      m.finishEnd.set(-1);
      m.active.set(true);
      const start = m.autoStart.get();
      if (start >= 0 && !reduced) {
        const t = (now - start) % LOOP;
        if (t >= 5.9 && t < 7.5) return false;
        const s = autoScene(t);
        m.dev.set(s.develop);
        m.developed.set(s.develop >= 1);
        m.developedAt.set(now - 1);
        m.release.set([now, s.x, s.y, s.rotation]);
        m.autoStart.set(-1);
      }
      if (m.finishing.get().length === 2) {
        const d = sceneAt(m, now, reduced).develop;
        m.dev.set(d);
        if (d >= 1) {
          m.developed.set(true);
          m.developedAt.set(now - 1);
        }
        m.finishing.set([]);
      }
      return true;
    };
    const develop = (amount: number, now: number) => {
      'worklet';
      const before = m.dev.get();
      if (before >= 1) return;
      const after = Math.min(1, before + amount);
      m.dev.set(after);
      if (Math.floor(after * 10) > Math.floor(before * 10) && after < 1) scheduleOnRN(tactile, 'tick');
      if (after >= 1) {
        m.developed.set(true);
        m.developedAt.set(now);
        scheduleOnRN(tactile, 'done');
      }
    };
    return Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .onBegin((e) => {
        const now = m.clock.get();
        const s0 = sceneAt(m, now, reduced);
        const cx = l.cardX + l.cardW / 2 + s0.x;
        const cy = l.cardY + l.cardH / 2 + s0.y;
        const inside = s0.toRoll === 0 && Math.abs(e.x - cx) <= l.cardW / 2 && Math.abs(e.y - cy) <= l.cardH / 2;
        if (!inside || !takeOver(now)) return;
        m.holding.set(true);
        m.lastX.set(0);
        m.release.set([]);
        m.dragX.set(0);
        m.dragY.set(0);
        m.dragging.set(true);
      })
      .onUpdate((e) => {
        if (!m.holding.get()) return;
        if (reduced) {
          m.dragX.set(e.translationX * 0.25);
          m.dragY.set(e.translationY * 0.25);
          return;
        }
        m.dragX.set(e.translationX);
        m.dragY.set(e.translationY);
        const dx = Math.abs(e.translationX - m.lastX.get());
        m.lastX.set(e.translationX);
        develop(dx / 1600, m.clock.get());
      })
      .onFinalize(() => {
        if (!m.holding.get()) return;
        m.holding.set(false);
        const now = m.clock.get();
        const s = sceneAt(m, now, reduced);
        m.dragging.set(false);
        m.release.set(reduced ? [] : [now, s.x, s.y, s.rotation]);
        m.resumeAt.set(now + 2);
      });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [l, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const shakeStyle = useAnimatedStyle(() => ({ opacity: 1 - scene.get().label }));
  const phoneStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return { transform: [{ rotate: `${reduced || s.label >= 1 ? 0 : (s.x / 17) * 14}deg` }] };
  });
  const doneStyle = useAnimatedStyle(() => ({ opacity: scene.get().label }));
  const barStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, scene.get().bar)) }));
  const fillStyle = useAnimatedStyle(() => ({ width: Math.max(3, 140 * scene.get().develop) }));
  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + scene.get().bump }] }));

  const ready = size.width > 0 && !!image;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {ready ? (
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill}>
            <Backdrop l={l} />
            <View style={[styles.header, { top: insets.top + 6 }]} pointerEvents="none">
              <View style={styles.circle}>
                <Glyph d={BACK} color="rgba(255,255,255,0.85)" width={1.8} size={18} />
              </View>
              <View style={styles.titles}>
                <Text style={styles.title}>Today</Text>
                <Text style={styles.subtitle}>10:10 AM</Text>
              </View>
              <View style={styles.circle}>
                <Svg width={18} height={18} viewBox="0 0 24 24">
                  {[5, 12, 19].map((x) => (
                    <Circle key={x} cx={x} cy={12} r={1.8} fill="rgba(255,255,255,0.85)" />
                  ))}
                </Svg>
              </View>
            </View>

            <View style={[styles.hint, { top: l.hintY - 10 }]} pointerEvents="none">
              <Animated.View style={[styles.hintRow, shakeStyle]}>
                <Animated.View style={phoneStyle}>
                  <Glyph d={PHONE} color="rgba(255,255,255,0.75)" width={1.6} size={20} />
                </Animated.View>
                <Text style={styles.hintText}>Shake to develop</Text>
              </Animated.View>
              <Animated.View style={[styles.hintRow, styles.hintOver, doneStyle]}>
                <Glyph d={CHECK} color={MINT} width={1.8} size={20} />
                <Text style={styles.doneText}>Developed</Text>
              </Animated.View>
              <Animated.View style={[styles.bar, barStyle]}>
                <Animated.View style={[styles.barFill, fillStyle]} />
              </Animated.View>
            </View>

            <View style={[styles.dock, { top: l.dockY - 22 }]} pointerEvents="none">
              <Glyph d={SHARE} color="rgba(255,255,255,0.75)" width={1.6} size={22} />
              <Animated.View style={[styles.thumbFrame, thumbStyle]}>
                <Image source={PHOTO} style={styles.thumb} contentFit="cover" />
              </Animated.View>
              <Glyph d={TRASH} color="rgba(255,255,255,0.75)" width={1.6} size={22} />
            </View>

            <Canvas
              style={StyleSheet.absoluteFill}
              pointerEvents="none"
              accessible
              accessibilityLabel="Instant photo of a turquoise mountain lake at sunrise"
            >
              <Picture picture={print} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}
    </View>
  );
}

function Backdrop({ l }: { l: Layout }) {
  const picture = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
    const ground = Skia.Paint();
    ground.setColor(Skia.Color('#1F161C'));
    canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), ground);
    const lift = Skia.Paint();
    lift.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: l.cardX + l.cardW / 2, y: l.cardY + l.cardH / 2 },
        l.width * 0.85,
        [Skia.Color('#3A2632'), Skia.Color('rgba(58,38,50,0)')],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), lift);
    return recorder.finishRecordingAsPicture();
  }, [l]);
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}

/** Line glyphs on a 24 × 24 grid. */
const BACK = 'M14.5 5L7.5 12L14.5 19';
const PHONE = 'M9.5 3H14.5A2.5 2.5 0 0 1 17 5.5V18.5A2.5 2.5 0 0 1 14.5 21H9.5A2.5 2.5 0 0 1 7 18.5V5.5A2.5 2.5 0 0 1 9.5 3Z M10.5 18H13.5 M3.5 8Q1.5 12 3.5 16 M20.5 8Q22.5 12 20.5 16';
const CHECK = 'M5 12.5L10 17.5L19 7';
const SHARE = 'M12 3.5V15 M7.5 8L12 3.5L16.5 8 M8 11H5V21H19V11H16';
const TRASH = 'M4 6.5H20 M9.5 6.5V4H14.5V6.5 M6 6.5L7 20H17L18 6.5';

function Glyph({ d, color, width, size }: { d: string; color: string; width: number; size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={(width * 24) / size} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1F161C' },
  header: { position: 'absolute', left: 20, right: 20, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  circle: { width: 36, height: 36, borderRadius: 18, backgroundColor: 'rgba(255,255,255,0.08)', alignItems: 'center', justifyContent: 'center' },
  titles: { alignItems: 'center', gap: 1 },
  title: { fontSize: 15, fontWeight: '600', color: '#FFFFFF' },
  subtitle: { fontSize: 12, color: 'rgba(255,255,255,0.55)' },
  hint: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  hintRow: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 20 },
  hintOver: { position: 'absolute', top: 0 },
  hintText: { fontSize: 14, fontWeight: '500', color: 'rgba(255,255,255,0.75)' },
  doneText: { fontSize: 14, fontWeight: '500', color: 'rgba(255,255,255,0.9)' },
  bar: { position: 'absolute', top: 28, width: 140, height: 3, borderRadius: 1.5, backgroundColor: 'rgba(255,255,255,0.12)', overflow: 'hidden' },
  barFill: { height: 3, borderRadius: 1.5, backgroundColor: MINT },
  dock: { position: 'absolute', left: 32, right: 32, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  thumbFrame: { padding: 2, borderRadius: 8, backgroundColor: '#F5F2EC' },
  thumb: { width: 36, height: 36, borderRadius: 6 },
});

import {
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  Skia,
  TileMode,
  useImage,
  type SkCanvas,
  type SkImage,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type FrameCallback,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 7.5;
const TAU = Math.PI * 2;
const GLASS_SOURCE = require('./assets/glass-shell.png');
const METAL_SOURCE = require('./assets/metal-bolt.png');

type Props = { autoPlay?: boolean; previewTime?: number };
type Painter = { canvas: SkCanvas; paint: SkPaint };

function smooth(value: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, value));
  return x * x * (3 - 2 * x);
}

function color(hex: number, alpha = 1) {
  'worklet';
  return Skia.Color(`rgba(${(hex >> 16) & 255},${(hex >> 8) & 255},${hex & 255},${Math.min(1, Math.max(0, alpha))})`);
}

function reset(p: Painter, hex = 0xffffff, alpha = 1, stroke = 0) {
  'worklet';
  p.paint.setShader(null);
  p.paint.setColorFilter(null);
  p.paint.setMaskFilter(null);
  p.paint.setAlphaf(1);
  p.paint.setColor(color(hex, alpha));
  p.paint.setStyle(stroke > 0 ? PaintStyle.Stroke : PaintStyle.Fill);
  p.paint.setStrokeWidth(stroke);
  p.paint.setAntiAlias(true);
}

function gradient(
  p: Painter,
  colors: number[],
  positions: number[],
  x: number,
  y: number,
  endX: number,
  endY: number,
  alphas?: number[],
  stroke = 0,
) {
  'worklet';
  reset(p, 0xffffff, 1, stroke);
  p.paint.setShader(Skia.Shader.MakeLinearGradient(
    { x, y },
    { x: endX, y: endY },
    colors.map((value, index) => color(value, alphas?.[index] ?? 1)),
    positions,
    TileMode.Clamp,
    undefined,
    // Match Swift and Android by interpolating premultiplied gradient endpoints.
    1,
  ));
}

function image(p: Painter, asset: SkImage, x: number, y: number, width: number, height: number, tinted = false) {
  'worklet';
  reset(p);
  if (tinted) {
    p.paint.setColorFilter(Skia.ColorFilter.MakeMatrix([
      181 / 255, 0, 0, 0, 0,
      0, 1, 0, 0, 0,
      0, 0, 208 / 255, 0, 0,
      0, 0, 0, 1, 0,
    ]));
  }
  p.canvas.drawImageRect(
    asset,
    Skia.XYWHRect(0, 0, asset.width(), asset.height()),
    Skia.XYWHRect(x, y, width, height),
    p.paint,
  );
  p.paint.setColorFilter(null);
}

function wavePath(surface: (x: number) => number, startX: number, count: number): SkPath {
  'worklet';
  const path = Skia.PathBuilder.Make().moveTo(startX, surface(startX));
  for (let index = 1; index <= count; index++) {
    const x = startX + index * 2;
    path.lineTo(x, surface(x));
  }
  path.lineTo(136, 262).lineTo(4, 262).close();
  return path.detach();
}

function drawArt(p: Painter, phase: number, tilt: number, level: number, glass: SkImage, metal: SkImage) {
  'worklet';
  const { canvas } = p;
  const shell = Skia.RRectXY(Skia.XYWHRect(1, 1, 138, 260), 69, 69);
  const inside = Skia.RRectXY(Skia.XYWHRect(6, 6, 128, 250), 64, 64);

  gradient(p, [0x121715, 0x000000, 0x111713], [0, 0.5, 1], 0, 0, 140, 262, [0.5, 1, 0.24]);
  canvas.drawRRect(shell, p.paint);
  reset(p, 0xc6d4cc, 0.17, 0.85);
  canvas.drawRRect(shell, p.paint);

  canvas.save();
  canvas.clipRRect(inside, ClipOp.Intersect, true);
  const surface = (x: number) => {
    'worklet';
    const u = (x - 6) / 128;
    return 121 + level + 2.3 * Math.sin(u * TAU)
      - 14 * Math.sin(u * TAU + phase)
      + 3.2 * Math.sin(u * Math.PI * 4 - phase * 2)
      + tilt * (u - 0.5);
  };
  const water = wavePath(surface, 4, 66);
  gradient(p, [0x78ff00, 0x03ca08, 0x00530c, 0x000301], [0, 0.18, 0.4, 1], 70, 111 + level, 70, 260);
  canvas.drawPath(water, p.paint);

  const top = Skia.PathBuilder.Make().moveTo(5, surface(5));
  for (let index = 1; index <= 65; index++) {
    const x = 5 + index * 2;
    top.lineTo(x, surface(x));
  }
  for (let index = 65; index >= 0; index--) {
    const x = 5 + index * 2;
    const u = (x - 5) / 130;
    top.lineTo(x, 141 + level + 8 * Math.sin(u * TAU + phase) + tilt * (u - 0.5));
  }
  top.close();
  gradient(p, [0x64fa00, 0xffff0a, 0xd9ff00, 0x5ffc00], [0, 1 / 3, 2 / 3, 1], 14, 110, 128, 139);
  canvas.drawPath(top.detach(), p.paint);

  image(p, metal, -8.15, 3.9, 154.9, 280.13);

  const frontSurface = (x: number) => {
    'worklet';
    const u = (x - 5) / 130;
    return 141 + level + 8 * Math.sin(u * TAU + phase) + tilt * (u - 0.5);
  };
  // The first front-water point remains at its mean level, as in the Swift reference.
  const front = Skia.PathBuilder.Make().moveTo(4, 141 + level);
  for (let index = 1; index <= 66; index++) {
    const x = 4 + index * 2;
    front.lineTo(x, frontSurface(x));
  }
  front.lineTo(136, 262).lineTo(4, 262).close();
  canvas.save();
  canvas.clipPath(front.detach(), ClipOp.Intersect, true);
  canvas.translate(Math.sin(phase) * 1.2, 0);
  image(p, metal, -8.15, 3.9, 154.9, 280.13, true);
  gradient(p, [0x03f000, 0x001703], [0, 1], 70, 120, 70, 262, [0.1, 0.5]);
  canvas.drawPath(water, p.paint);
  canvas.restore();

  for (let index = 0; index < 7; index++) {
    const hash = (Math.sin((index + 1) * 12.9898) * 43758.5453) % 1;
    const x = 25 + Math.abs(hash) * 70;
    const cycle = phase / TAU + index * 0.173;
    const rise = cycle - Math.floor(cycle);
    const alpha = Math.min(1, rise / 0.12) * Math.min(1, (1 - rise) / 0.22);
    const y = 212 - rise * 67;
    const radius = 1.1 + (index % 3) * 0.55;
    reset(p);
    p.paint.setShader(Skia.Shader.MakeRadialGradient(
      { x: x + radius, y: y + radius },
      radius * 1.4,
      [color(0x68ff40, alpha * 0.35), color(0x00a313, 0)],
      [0, 1],
      TileMode.Clamp,
      undefined,
      1,
    ));
    canvas.drawOval(Skia.XYWHRect(x, y, radius * 2, radius * 2), p.paint);
  }
  canvas.restore();

  image(p, glass, -27.2, -26.4, 196.5, 317);

  gradient(p, [0xe3f3e8, 0xb1c6b8, 0x44e84e, 0xa5c7b0], [0, 1 / 3, 2 / 3, 1], 4, 50, 4, 209, [0, 0.18, 0.27, 0]);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(3, 50, 4, 159), 2, 2), p.paint);
  gradient(p, [0xf3fff3, 0x000000, 0x1dfb20, 0x000000], [0, 1 / 3, 2 / 3, 1], 0, 0, 140, 262, [0.05, 0, 0.24, 0], 1);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(7, 7, 126, 248), 63, 63), p.paint);
}

function samplePhase(
  now: number,
  epoch: number,
  lastTouch: number,
  held: boolean,
  touchStarted: number,
  touchPhase: number,
  returnPhase: number,
  autoPlay: boolean,
  reduced: boolean,
  previewTime?: number,
) {
  'worklet';
  if (reduced) return { phase: 0, gain: 0 };
  const t = previewTime !== undefined
    ? Math.max(0, previewTime) % LOOP
    : autoPlay && epoch >= 0 ? Math.max(0, now - epoch) % LOOP : 0;
  const automaticPhase = smooth((t - 0.75) / 6) * TAU;
  const age = lastTouch >= 0 ? Math.max(0, now - lastTouch) : 99;
  const gain = held ? 1 : 1 - smooth((age - 2) / 0.55);
  const livePhase = touchPhase + (now - touchStarted) * TAU / LOOP;
  return {
    phase: lastTouch >= 0 ? livePhase * gain + returnPhase * (1 - gain) : automaticPhase,
    gain,
  };
}

function stirHaptic() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

/** A glass cell whose liquid and submerged bolt share one deterministic wave field. */
export default function LiquidChargeCapsule({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const startupReduced = useReducedMotion();
  const [reduced, setReduced] = useState(startupReduced);
  const glass = useImage(GLASS_SOURCE);
  const metal = useImage(METAL_SOURCE);
  const clock = useSharedValue(0);
  const epoch = useSharedValue(-1);
  const lastTouch = useSharedValue(-100);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const held = useSharedValue(false);
  const touchStarted = useSharedValue(0);
  const touchPhase = useSharedValue(0);
  const returnPhase = useSharedValue(0);
  const active = useSharedValue(AppState.currentState === 'active');
  const ready = useSharedValue(false);
  const reductionEnabled = useSharedValue(startupReduced);
  const animationAllowed = useSharedValue(!startupReduced && previewTime === undefined);
  const driverRef = useRef<FrameCallback | null>(null);
  const setDriverRunning = useCallback((running: boolean) => {
    driverRef.current?.setActive(running && active.get() && ready.get() && animationAllowed.get() && !reductionEnabled.get());
  }, [active, ready, animationAllowed, reductionEnabled]);

  useEffect(() => {
    let mounted = true;
    let revision = 0;
    const update = (value: boolean) => {
      revision++;
      reductionEnabled.set(value);
      setReduced(value);
    };
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', update);
    const initialRevision = revision;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (mounted && revision === initialRevision) update(value);
    }).catch(() => {
      // The synchronous system value remains valid if the native query is unavailable.
    });
    return () => {
      mounted = false;
      subscription.remove();
    };
  }, [reductionEnabled]);

  const driver = useFrameCallback(frame => {
    'worklet';
    if (!active.get() || !ready.get() || !animationAllowed.get() || reductionEnabled.get()) return;
    if (!autoPlay && lastTouch.get() < 0) { scheduleOnRN(setDriverRunning, false); return; }
    const now = frame.timestamp / 1000;
    if (epoch.get() < 0) epoch.set(now);
    clock.set(now);
    if (!held.get() && lastTouch.get() >= 0 && now - lastTouch.get() > 2.55) {
      lastTouch.set(-100);
      dragX.set(0); dragY.set(0); epoch.set(now);
      if (!autoPlay) scheduleOnRN(setDriverRunning, false);
    }
  }, autoPlay && !reduced && previewTime === undefined && Boolean(glass && metal) && AppState.currentState === 'active');

  useEffect(() => { driverRef.current = driver; return () => { driverRef.current = null; }; }, [driver]);
  useEffect(() => {
    ready.set(Boolean(glass && metal));
    animationAllowed.set(!reduced && previewTime === undefined);
    epoch.set(-1); lastTouch.set(-100); held.set(false); dragX.set(0); dragY.set(0); clock.set(0);
    setDriverRunning(autoPlay);
  }, [glass, metal, ready, animationAllowed, reduced, previewTime, autoPlay, epoch, lastTouch, held, dragX, dragY, clock, setDriverRunning]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      active.set(state === 'active');
      epoch.set(-1); lastTouch.set(-100); held.set(false); dragX.set(0); dragY.set(0); clock.set(0);
      setDriverRunning(state === 'active' && autoPlay);
    });
    return () => { subscription.remove(); driver.setActive(false); };
  }, [active, epoch, lastTouch, held, dragX, dragY, clock, autoPlay, driver, setDriverRunning]);

  /* eslint-disable react-hooks/refs, react-hooks/purity -- RNGH stores callbacks for later native/UI-thread input events. */
  const pan = Gesture.Pan()
    .minDistance(3)
    .enabled(!reduced && previewTime === undefined && Boolean(glass && metal))
    .onStart(() => {
      if (!active.get() || !ready.get() || !animationAllowed.get() || reductionEnabled.get()) return;
      const now = performance.now() / 1000;
      const pose = samplePhase(clock.get(), epoch.get(), lastTouch.get(), held.get(), touchStarted.get(), touchPhase.get(), returnPhase.get(), autoPlay, reduced, previewTime);
      touchPhase.set(pose.phase);
      touchStarted.set(now); lastTouch.set(now); clock.set(now); held.set(true);
      if (epoch.get() < 0) epoch.set(now);
      scheduleOnRN(setDriverRunning, true);
    })
    .onUpdate(event => {
      if (!held.get()) return;
      const scale = width / 393;
      dragX.set(Math.min(110, Math.max(-110, event.translationX / scale)));
      dragY.set(Math.min(130, Math.max(-130, event.translationY / scale)));
    })
    .onFinalize(() => {
      if (!held.get()) return;
      const now = performance.now() / 1000;
      clock.set(now);
      const future = touchPhase.get() + (now - touchStarted.get() + 2.55) * TAU / LOOP;
      returnPhase.set(Math.ceil(future / TAU) * TAU);
      lastTouch.set(now);
      held.set(false);
      if (!reductionEnabled.get()) scheduleOnRN(stirHaptic);
    });
  /* eslint-enable react-hooks/refs, react-hooks/purity */

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.clear(color(0x000000));
    if (glass && metal) {
      const pose = samplePhase(clock.get(), epoch.get(), lastTouch.get(), held.get(), touchStarted.get(), touchPhase.get(), returnPhase.get(), autoPlay, reduced, previewTime);
      const scale = width / 393;
      const artWidth = 138 * scale;
      const artHeight = 259 * scale;
      canvas.translate(width / 2 - 2.5 * scale - artWidth / 2, height / 2 + 19 * scale - artHeight / 2);
      canvas.scale(artWidth / 140, artHeight / 262);
      drawArt({ canvas, paint: Skia.Paint() }, pose.phase, dragX.get() * pose.gain * 0.13, dragY.get() * pose.gain * 0.16, glass, metal);
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, glass, metal, autoPlay, reduced, previewTime]);

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen} accessible accessibilityLabel="Charge reserve" accessibilityValue={{ text: 'Half full' }} accessibilityHint="Drag to stir the liquid">
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#000000' } });

import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, matchFont, useImage, type SkCanvas } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  BROWN,
  FLOOR,
  GREEN,
  INK,
  ITEM_OFFSETS,
  IMAGE_W,
  MUTED,
  SUITCASE,
  SUITCASE_XRAY,
  clipWindow,
  drawAgainGlyph,
  drawBackground,
  drawBag,
  drawBagShadow,
  drawBar,
  drawBelt,
  drawBeltXray,
  drawBird,
  drawBoxes,
  drawCardBack,
  drawCurtains,
  drawGantry,
  drawStatusIcon,
  drawWindowBack,
  drawWindowFront,
  hexColor,
} from './xrayArt';
import {
  LEG1,
  REST_X,
  SPACING,
  STATUSES,
  WIN_L,
  WIN_R,
  beacon,
  beginTouch,
  beltAt,
  boxes,
  checkScale,
  checkStroke,
  clamp,
  cleared,
  curtain,
  endTouch,
  fill,
  initialTouch,
  leanOf,
  loopTime,
  mod,
  moveTouch,
  restartTouch,
  restsAfter,
  scanning,
  spin,
  stage,
  sweepAlpha,
  sweepY,
} from './xrayMotion';

/*
 X-Ray Bag Scan — the bag-drop step of an invented airline (Halcyon Air). A lilac carry-on rides the
 belt through a scanner gantry; the gantry's window is a mask that shows the bag as its x-ray (cyan on
 navy, contents visible) while the rest of the bag stays a photo. Light curtains glow on the window's
 edges, a sweep passes while the bag dwells, detection boxes pop on the liquids, headphones and laptop,
 and the card steps Ready to scan → Scanning… → Checking contents → Bag cleared. The bag leaves on the
 right while the next one arrives at the rest spot, so the loop (LOOP = 9 s) is seamless.

 Drag the bag along the belt to scrub the scan; release and the belt carries on. "Scan again"
 restarts the pass. Reduced motion: the cleared state, still.
 */

const FAMILY = Platform.OS === 'ios' ? 'System' : 'sans-serif';
const CARD_H = 210;

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A small static glyph drawn in units. */
function Glyph({ size, k, draw }: { size: number; k: number; draw: (c: SkCanvas) => void }) {
  const picture = useMemo(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size * k, size * k));
    canvas.scale(k, k);
    draw(canvas);
    return recorder.finishRecordingAsPicture();
  }, [size, k, draw]);
  return (
    <Canvas style={{ width: size * k, height: size * k }}>
      <Picture picture={picture} />
    </Canvas>
  );
}

const drawAgain = (c: SkCanvas) => drawAgainGlyph(c, 0xfff3e3);

type Props = {
  /** Plays the scan pass on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function XrayBagScan({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const [base, setBase] = useState(0);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const designHeight = height / k;
  const top = insets.top / k;
  const sy = top - 59;
  const cardBottom = Math.min(818 + sy, designHeight - Math.max(insets.bottom / k, 12));
  const cardTop = cardBottom - CARD_H;

  const photo = useImage(SUITCASE);
  const xray = useImage(SUITCASE_XRAY);
  const fonts = useMemo(
    () => ({
      sign: matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '700' }),
      poster: matchFont({ fontFamily: FAMILY, fontSize: 13, fontWeight: '800' }),
      label: matchFont({ fontFamily: FAMILY, fontSize: 9.5, fontWeight: '600' }),
    }),
    [],
  );

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const s = touch.get();
    if (!autoPlay && !asleep.get() && !s.touching && now > restsAfter(s)) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = !reduced && active && (autoPlay || awake);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const time = useDerivedValue(() => (reduced ? 5.6 : loopTime(touch.get(), clock.get(), autoPlay)));
  const stageV = useDerivedValue(() => stage(time.get()));

  useAnimatedReaction(
    () => Math.floor(mod(stageV.get(), 4)) % 4,
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setBase, value);
    },
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const c = clock.get();
    const s = touch.get();
    const t = time.get();
    const d = reduced ? LEG1 : beltAt(s, c, autoPlay);
    const lean = reduced ? 0 : leanOf(s, c, t);
    canvas.save();
    canvas.scale(k, k);
    drawBackground(canvas, 393, designHeight, sy, fonts);

    const xs: number[] = [];
    for (let n = -1; n <= 1; n++) {
      const x = REST_X + d - SPACING * n;
      if (Math.abs(x - 196.5) < 196.5 + IMAGE_W / 2 + 12) xs.push(x);
    }

    canvas.save();
    clipWindow(canvas, sy, false);
    drawBelt(canvas, 393, d, sy);
    for (let i = 0; i < xs.length; i++) drawBagShadow(canvas, xs[i], sy);
    for (let i = 0; i < xs.length; i++) drawBag(canvas, photo, xs[i], lean, sy);
    canvas.restore();

    canvas.save();
    clipWindow(canvas, sy, true);
    drawWindowBack(canvas, sy);
    drawBeltXray(canvas, d, sy);
    for (let i = 0; i < xs.length; i++) {
      const x = xs[i];
      if (x + IMAGE_W / 2 <= WIN_L - 8 || x - IMAGE_W / 2 >= WIN_R + 8) continue;
      drawBag(canvas, xray, x, lean, sy, true);
      drawBag(canvas, xray, x, lean, sy);
    }
    const bagX = REST_X + d;
    drawBoxes(canvas, bagX, lean, sy, boxes(t, bagX, s.touching, ITEM_OFFSETS), cleared(t), fonts);
    drawWindowFront(canvas, sy, sweepY(t), s.touching ? 0 : sweepAlpha(t));
    canvas.restore();

    let left = 0.35;
    let right = 0.35;
    for (let i = 0; i < xs.length; i++) {
      left = Math.max(left, curtain(WIN_L, xs[i]));
      right = Math.max(right, curtain(WIN_R, xs[i]));
    }
    drawGantry(canvas, sy, beacon(t), scanning(t), cleared(t), t);
    drawCurtains(canvas, sy, left, right);

    drawCardBack(canvas, cardTop);
    drawStatusIcon(canvas, 37, cardTop + 21, stageV.get(), spin(t), checkScale(t), checkStroke(t));
    drawBar(canvas, 36, cardTop + 90, fill(t), cleared(t));
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX([-4, 4])
        .onStart(() => {
          if (reduced) return;
          touch.set(beginTouch(touch.get(), clock.get(), autoPlay));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          if (reduced || !touch.get().touching) return;
          const result = moveTouch(touch.get(), e.translationX / k, e.velocityX / k);
          touch.set(result.next);
          if (result.hit) scheduleOnRN(haptic);
        })
        .onFinalize(() => {
          if (!touch.get().touching) return;
          touch.set(endTouch(touch.get(), clock.get()));
        }),
    [reduced, autoPlay, k, touch, clock, wake, asleep],
  );

  const again = useCallback(() => {
    haptic();
    if (reduced) return;
    touch.set(restartTouch(touch.get(), clock.get()));
    if (!autoPlay) {
      asleep.set(false);
      setAwake(true);
    }
  }, [reduced, autoPlay, touch, clock, asleep]);

  const fadeOut = useAnimatedStyle(() => {
    const s = mod(stageV.get(), 4);
    const f = s - Math.floor(s);
    return { opacity: 1 - f, transform: [{ translateY: -8 * f * k }] };
  });
  const fadeIn = useAnimatedStyle(() => {
    const s = mod(stageV.get(), 4);
    const f = s - Math.floor(s);
    return { opacity: f, transform: [{ translateY: 8 * (1 - f) * k }] };
  });
  const label0 = useAnimatedStyle(() => ({
    color: interpolateColor(clamp((fill(time.get()) - 1 / 3 + 0.04) * 25), [0, 1], [hexColor(0xb8a08a), hexColor(INK)]),
  }));
  const label1 = useAnimatedStyle(() => ({
    color: interpolateColor(clamp((fill(time.get()) - 2 / 3 + 0.04) * 25), [0, 1], [hexColor(0xb8a08a), hexColor(INK)]),
  }));
  const label2 = useAnimatedStyle(() => ({
    color: interpolateColor(clamp((fill(time.get()) - 1 + 0.04) * 25), [0, 1], [hexColor(0xb8a08a), hexColor(GREEN)]),
  }));
  const labelStyles = [label0, label1, label2];

  const u = (v: number) => v * k;
  const shown = STATUSES[base];

  return (
    <View style={[styles.screen, { backgroundColor: hexColor(FLOOR) }]}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel="A lilac suitcase passing through the bag scanner">
        <Picture picture={picture} />
      </Canvas>

      <GestureDetector gesture={gesture}>
        <View style={[styles.abs, { left: 0, top: u(252 + sy), width: u(393), height: u(360) }]} />
      </GestureDetector>

      {/* Header */}
      <View
        style={[styles.pill, { left: u(20), top: u(top + 7), height: u(30), paddingHorizontal: u(12), backgroundColor: hexColor(0xfff1de, 0.8) }]}
        accessible
        accessibilityLabel="Halcyon Air"
      >
        <Glyph size={16} k={k} draw={drawBird} />
        <Text style={[styles.pillText, { fontSize: u(14), marginLeft: u(7), color: hexColor(INK) }]} numberOfLines={1} allowFontScaling={false}>
          Halcyon Air
        </Text>
      </View>
      <View style={[styles.pill, { right: u(20), top: u(top + 7), height: u(30), paddingHorizontal: u(12), backgroundColor: hexColor(INK, 0.08) }]}>
        <Text style={[styles.pillText, { fontSize: u(13), color: hexColor(BROWN) }]} numberOfLines={1} allowFontScaling={false}>
          Step 2 of 3
        </Text>
      </View>
      <Text
        style={[styles.title, { left: u(20), top: u(top + 68 - 21), fontSize: u(34), lineHeight: u(42), color: hexColor(INK) }]}
        accessibilityRole="header"
        numberOfLines={1}
        allowFontScaling={false}
      >
        Bag check
      </Text>
      <Text
        style={[styles.sub, { left: u(20), top: u(top + 102 - 10), fontSize: u(15), lineHeight: u(20), color: hexColor(BROWN) }]}
        numberOfLines={1}
        allowFontScaling={false}
      >
        HA 418 · Lisbon → Oslo · Gate E7
      </Text>

      {/* Card text */}
      <View style={[styles.abs, { left: u(94), top: u(cardTop + 21), width: u(260), height: u(28) }]} accessible accessibilityLabel={shown}>
        <Animated.Text style={[styles.status, { fontSize: u(20), lineHeight: u(28), color: hexColor(INK) }, fadeOut]} numberOfLines={1} allowFontScaling={false}>
          {STATUSES[base]}
        </Animated.Text>
        <Animated.Text style={[styles.status, { fontSize: u(20), lineHeight: u(28), color: hexColor(INK) }, fadeIn]} numberOfLines={1} allowFontScaling={false}>
          {STATUSES[(base + 1) % 4]}
        </Animated.Text>
      </View>
      <Text
        style={[styles.abs, { left: u(94), top: u(cardTop + 58 - 9), fontSize: u(14), lineHeight: u(18), color: hexColor(MUTED) }]}
        numberOfLines={1}
        allowFontScaling={false}
      >
        Carry-on · 7.4 kg · Tag 418 207
      </Text>
      {['Scan', 'Contents', 'Cleared'].map((label, i) => (
        <Animated.Text
          key={label}
          style={[styles.stepLabel, { left: u(36 + i * 109), top: u(cardTop + 114 - 9), width: u(107), fontSize: u(13), lineHeight: u(18) }, labelStyles[i]]}
          numberOfLines={1}
          allowFontScaling={false}
          importantForAccessibility="no"
        >
          {label}
        </Animated.Text>
      ))}

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Scan again"
        onPress={again}
        style={({ pressed }) => [
          styles.button,
          {
            left: u(36),
            top: u(cardTop + 142),
            width: u(321),
            height: u(48),
            borderRadius: u(24),
            backgroundColor: hexColor(INK),
            transform: [{ scale: pressed ? 0.97 : 1 }],
          },
        ]}
      >
        <Glyph size={18} k={k} draw={drawAgain} />
        <Text style={[styles.buttonText, { fontSize: u(16), marginLeft: u(8), color: hexColor(0xfff3e3) }]} numberOfLines={1} allowFontScaling={false}>
          Scan again
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  abs: { position: 'absolute' },
  pill: { position: 'absolute', flexDirection: 'row', alignItems: 'center', borderRadius: 999 },
  pillText: { fontWeight: '600' },
  title: { position: 'absolute', fontWeight: '800' },
  sub: { position: 'absolute', fontWeight: '500' },
  status: { position: 'absolute', left: 0, top: 0, fontWeight: '700' },
  stepLabel: { position: 'absolute', textAlign: 'center', fontWeight: '600' },
  button: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontWeight: '600' },
});

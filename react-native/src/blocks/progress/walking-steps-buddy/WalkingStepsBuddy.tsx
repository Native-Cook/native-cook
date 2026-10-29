import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, Text, useWindowDimensions, View, type TextStyle } from 'react-native';
import { Canvas, Picture, Skia, type SkCanvas, type SkPicture } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  drawBar,
  drawBubble,
  drawCalendar,
  drawChevron,
  drawCoin,
  drawConfetti,
  drawGear,
  drawGift,
  drawPlane,
  drawScene,
  drawShare,
  drawShoe,
  hexString,
  INK,
  LABEL,
  MUTED,
  PAGE,
} from './walkArt';
import {
  beginTouch,
  bubble,
  COUNT_STRINGS,
  countPhase,
  distanceAt,
  endTouch,
  ground,
  happyAt,
  hopLift,
  hopSquash,
  initialTouch,
  loopTimeAt,
  MILESTONE_LABELS,
  moveTouch,
  pillMix,
  pose,
  restsAfter,
  REWARDS,
  steps,
  TICK_X,
} from './walkMotion';

/*
 Walking Steps Buddy — the step-rewards screen of an invented neighbourhood-walk app. Bao, a round
 steamed-bun buddy with a tote and a baguette, walks without end while the street scrolls by in
 layers: clouds drift and dissolve, hills and trees slide slowly, the bench, the walk sign, the
 planter and the grass pass fast. Every footfall adds a chunk to today's count (it rolls like a drum)
 and the goal bar advances; passing 1,000 steps pops confetti, a check badge and a "+10 coins"
 bubble. The sun sets, then rises again as the count rolls back to 0: a fresh day, and the loop
 starts over (LOOP = 9 s).

 Drag sideways to walk Bao yourself (back and forth, the world and the legs move together); on
 release the walk carries on at its own pace. Tap Bao for a hop. Reduced motion: the rest frame.
 */

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Records a static icon drawn in points into a picture at the screen scale. */
function iconPicture(w: number, h: number, k: number, draw: (c: SkCanvas) => void) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, w * k, h * k));
  canvas.scale(k, k);
  draw(canvas);
  return recorder.finishRecordingAsPicture();
}

function Icon({ picture, w, h, k, style }: { picture: SkPicture; w: number; h: number; k: number; style?: object }) {
  return (
    <Canvas style={[{ width: w * k, height: h * k }, style]} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}

/** One row of the count drum: it rolls up with the phase and fades away from the middle. */
function DrumRow({ text, index, phase, rowPx, style }: { text: string; index: number; phase: SharedValue<number>; rowPx: number; style: TextStyle }) {
  const animated = useAnimatedStyle(() => {
    const d = index - phase.get();
    return { transform: [{ translateY: d * rowPx }], opacity: Math.max(0, 1 - 1.6 * Math.abs(d)) };
  });
  return (
    <Animated.View style={[styles.drumRow, animated]}>
      <Text style={style} numberOfLines={1} allowFontScaling={false}>
        {text}
      </Text>
    </Animated.View>
  );
}

function CountDrum({ phase, k, left, top, style }: { phase: SharedValue<number>; k: number; left: number; top: number; style: TextStyle }) {
  const [base, setBase] = useState(0);
  useAnimatedReaction(
    () => Math.floor(phase.get()),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setBase, value);
    },
  );
  const bump = useAnimatedStyle(() => {
    const p = phase.get();
    const s = 1 + 0.05 * Math.sin(Math.PI * (p - Math.floor(p)));
    return { transform: [{ translateX: (-220 * k * (1 - s)) / 2 }, { scale: s }] };
  });
  const rows = [base, base + 1].filter((j) => j >= 0 && j < COUNT_STRINGS.length);
  return (
    <Animated.View style={[styles.drum, { left: left * k, top: top * k, width: 220 * k, height: 52 * k }, bump]} pointerEvents="none">
      {rows.map((j) => (
        <DrumRow key={j} text={COUNT_STRINGS[j]} index={j} phase={phase} rowPx={52 * k} style={style} />
      ))}
    </Animated.View>
  );
}

const LABEL_COLORS = [hexString(MUTED), hexString(INK)];

function Milestone({ i, t, k, s }: { i: number; t: SharedValue<number>; k: number; s: number }) {
  const mixV = useDerivedValue(() => pillMix(i, t.get()));
  const labelStyle = useAnimatedStyle(() => ({ color: interpolateColor(mixV.get(), [0, 1], LABEL_COLORS) }));
  const pillStyle = useAnimatedStyle(() => {
    const m = mixV.get();
    return {
      backgroundColor: interpolateColor(m, [0, 1], ['#F1F3F5', '#FFEFD2']),
      transform: [{ scale: 1 + 0.08 * Math.sin(Math.PI * m) }],
    };
  });
  const textStyle = useAnimatedStyle(() => ({ color: interpolateColor(mixV.get(), [0, 1], ['#B4BCC6', '#E07A12']) }));
  const coin = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 14 * k, 14 * k));
    canvas.scale(k, k);
    drawCoin(canvas, 7, 7, 7, mixV.get());
    return recorder.finishRecordingAsPicture();
  });
  const reached = useDerivedValue(() => mixV.get() > 0.5);
  const [isReached, setReached] = useState(false);
  useAnimatedReaction(
    () => reached.get(),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setReached, value);
    },
  );
  const u = (v: number) => v * k;
  return (
    <>
      <Animated.Text
        style={[styles.milestone, { left: u(TICK_X[i] - 40), top: u(s + 44 - 9), width: u(80), fontSize: u(13), lineHeight: u(18) }, labelStyle]}
        allowFontScaling={false}
        numberOfLines={1}
        importantForAccessibility="no"
        accessibilityElementsHidden
      >
        {MILESTONE_LABELS[i]}
      </Animated.Text>
      <Animated.View
        style={[styles.pill, { left: u(TICK_X[i] - 36), top: u(s + 76 - 15), width: u(72), height: u(30), borderRadius: u(15), gap: u(5) }, pillStyle]}
        accessible
        accessibilityLabel={`${MILESTONE_LABELS[i]}, ${REWARDS[i]} coins, ${isReached ? 'reached' : 'not reached'}`}
      >
        <Canvas style={{ width: u(14), height: u(14) }} pointerEvents="none">
          <Picture picture={coin} />
        </Canvas>
        <Animated.Text style={[styles.reward, { fontSize: u(13) }, textStyle]} allowFontScaling={false} numberOfLines={1}>
          {REWARDS[i]}
        </Animated.Text>
      </Animated.View>
    </>
  );
}

type Props = {
  /** Plays the walk and the compressed day on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function WalkingStepsBuddy({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const [shown, setShown] = useState(COUNT_STRINGS[0]);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const top = insets.top / k;
  const g = ground(top);
  const s = g + 38;
  const u = (v: number) => v * k;

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);
  const t = useSharedValue(0);
  const d = useSharedValue(0);
  const phase = useSharedValue(0);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const state = touch.get();
    const lt = loopTimeAt(state, now, autoPlay);
    t.set(lt);
    d.set(distanceAt(state, now, autoPlay));
    phase.set(countPhase(lt));
    if (!autoPlay && !asleep.get() && !state.touching && now > restsAfter(state)) {
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
  useEffect(() => {
    if (!reduced) return;
    t.set(0);
    d.set(0);
    phase.set(0);
  }, [reduced, t, d, phase]);

  useAnimatedReaction(
    () => Math.min(Math.round(phase.get()), COUNT_STRINGS.length - 1),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setShown, COUNT_STRINGS[value]);
    },
  );

  const scene = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.scale(k, k);
    const lt = t.get();
    const state = touch.get();
    const tau = clock.get() - state.hopClock;
    const p = pose(d.get());
    const squash = reduced ? 1 : hopSquash(tau);
    p.lift = reduced ? 0 : hopLift(tau);
    p.sy *= squash;
    p.sx *= 2 - squash;
    const happy = !reduced && (happyAt(lt) || tau < 0.75);
    drawScene(canvas, d.get(), lt, g, p, happy);
    drawBar(canvas, lt, steps(phase.get()), s + 10);
    return recorder.finishRecordingAsPicture();
  });

  const celebration = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.scale(k, k);
    drawConfetti(canvas, t.get(), s + 16);
    drawBubble(canvas, t.get(), s + 16);
    return recorder.finishRecordingAsPicture();
  });

  const bubbleText = useAnimatedStyle(() => {
    const b = bubble(t.get());
    return { opacity: b.alpha, transform: [{ translateY: b.y * k }, { translateX: -6 * k * (1 - b.scale) }, { scale: b.scale }] };
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin(() => {
          if (reduced) {
            scheduleOnRN(haptic);
            return;
          }
          touch.set(beginTouch(touch.get(), clock.get(), autoPlay));
          if (!autoPlay) {
            asleep.set(false);
            scheduleOnRN(wake);
          }
        })
        .onUpdate((e) => {
          if (reduced) return;
          touch.set(moveTouch(touch.get(), e.translationX / k, e.translationY / k));
        })
        .onFinalize((e) => {
          if (reduced || !touch.get().touching) return;
          const result = endTouch(touch.get(), clock.get(), e.velocityX / k, e.x / k, e.y / k, g, autoPlay);
          touch.set(result.next);
          if (result.hopped) scheduleOnRN(haptic);
        }),
    [reduced, autoPlay, k, g, touch, clock, wake, asleep],
  );

  const icons = useMemo(
    () => ({
      shoe: iconPicture(36, 28, k, drawShoe),
      chevron: iconPicture(12, 18, k, drawChevron),
      share: iconPicture(22, 22, k, drawShare),
      gear: iconPicture(22, 22, k, drawGear),
      calendar: iconPicture(44, 44, k, drawCalendar),
      gift: iconPicture(44, 44, k, drawGift),
      plane: iconPicture(44, 44, k, drawPlane),
      coin: iconPicture(16, 16, k, (c) => drawCoin(c, 8, 8, 8, 1)),
    }),
    [k],
  );

  const rows = [
    { icon: icons.calendar, title: 'Check in every day', detail: '1 coin a day', claim: true },
    { icon: icons.gift, title: 'Open the lucky box', detail: 'Up to 200 coins a day', claim: false },
    { icon: icons.plane, title: 'Walk with a friend', detail: 'Up to 1,000 coins each', claim: false },
  ];

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={scene} />
      </Canvas>
      <GestureDetector gesture={gesture}>
        <View
          style={[styles.touchArea, { height: u(g + 40) }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel="Bao walking down a sunny street"
        />
      </GestureDetector>

      {/* Navigation bar */}
      <View style={[styles.nav, { top: u(top), height: u(44) }]} pointerEvents="box-none">
        <Text style={[styles.title, { fontSize: u(17) }]} accessibilityRole="header" allowFontScaling={false}>
          Neighborhood walk
        </Text>
        <View style={[styles.navIcon, { left: u(24), top: u(13) }]} accessible accessibilityLabel="Back" accessibilityRole="button">
          <Icon picture={icons.chevron} w={12} h={18} k={k} />
        </View>
        <View style={[styles.navIcon, { left: u(315), top: u(11) }]} accessible accessibilityLabel="Share" accessibilityRole="button">
          <Icon picture={icons.share} w={22} h={22} k={k} />
        </View>
        <View style={[styles.navIcon, { left: u(353), top: u(11) }]} accessible accessibilityLabel="Settings" accessibilityRole="button">
          <Icon picture={icons.gear} w={22} h={22} k={k} />
        </View>
      </View>

      <Text
        style={[styles.stepsLabel, { left: u(20), top: u(top + 68 - 9), fontSize: u(14), lineHeight: u(18) }]}
        allowFontScaling={false}
        importantForAccessibility="no"
        accessibilityElementsHidden
      >
        Steps today
      </Text>
      <Icon picture={icons.shoe} w={36} h={28} k={k} style={[styles.abs, { left: u(20), top: u(top + 106 - 14) }]} />
      <CountDrum
        phase={phase}
        k={k}
        left={62}
        top={top + 106 - 26}
        style={{ color: hexString(INK), fontSize: u(44), fontWeight: '800', fontVariant: ['tabular-nums'] }}
      />
      <View
        style={[styles.abs, { left: u(20), top: u(top + 57), width: u(240), height: u(70) }]}
        accessible
        accessibilityLabel={`Steps today, ${shown}`}
        pointerEvents="none"
      />

      {[0, 1, 2, 3].map((i) => (
        <Milestone key={i} i={i} t={t} k={k} s={s} />
      ))}

      {/* Reward card */}
      <View style={[styles.card, { left: u(16), top: u(s + 116), width: u(361), borderRadius: u(22), paddingVertical: u(8) }]}>
        {rows.map((row) => (
          <View key={row.title} style={[styles.row, { height: u(70), paddingHorizontal: u(18), gap: u(14) }]} accessible>
            <Icon picture={row.icon} w={44} h={44} k={k} />
            <View style={[styles.rowText, { gap: u(3) }]}>
              <Text style={[styles.rowTitle, { fontSize: u(16) }]} numberOfLines={1} allowFontScaling={false}>
                {row.title}
              </Text>
              <Text style={[styles.rowDetail, { fontSize: u(13) }]} numberOfLines={1} allowFontScaling={false}>
                {row.detail}
              </Text>
            </View>
            {row.claim ? (
              <View style={[styles.claim, { height: u(32), borderRadius: u(16), paddingHorizontal: u(12), gap: u(5) }]} accessibilityRole="button">
                <Icon picture={icons.coin} w={16} h={16} k={k} />
                <Text style={[styles.claimText, { fontSize: u(13) }]} allowFontScaling={false}>
                  Claim
                </Text>
              </View>
            ) : null}
          </View>
        ))}
      </View>

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={celebration} />
      </Canvas>
      <Animated.View
        style={[styles.bubbleText, { left: u(TICK_X[0] + 22 - 30), top: u(s + 16 - 13), width: u(72), height: u(26) }, bubbleText]}
        pointerEvents="none"
        importantForAccessibility="no-hide-descendants"
        accessibilityElementsHidden
      >
        <Text style={[styles.bubbleLabel, { fontSize: u(12) }]} allowFontScaling={false} numberOfLines={1}>
          +10 coins
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: hexString(PAGE) },
  abs: { position: 'absolute' },
  touchArea: { position: 'absolute', left: 0, right: 0, top: 0 },
  nav: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  title: { color: hexString(INK), fontWeight: '600' },
  navIcon: { position: 'absolute' },
  stepsLabel: { position: 'absolute', color: hexString(LABEL), fontWeight: '500' },
  drum: { position: 'absolute', overflow: 'hidden' },
  drumRow: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, justifyContent: 'center' },
  milestone: { position: 'absolute', textAlign: 'center', fontWeight: '500' },
  pill: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  reward: { fontWeight: '700', fontVariant: ['tabular-nums'] },
  card: {
    position: 'absolute',
    backgroundColor: '#FFFFFF',
    shadowColor: '#3A4A5C',
    shadowOpacity: 0.09,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 4,
  },
  row: { flexDirection: 'row', alignItems: 'center' },
  rowText: { flex: 1 },
  rowTitle: { color: hexString(INK), fontWeight: '600' },
  rowDetail: { color: hexString(MUTED) },
  claim: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#F3F4F6' },
  claimText: { color: hexString(INK), fontWeight: '700' },
  bubbleText: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  bubbleLabel: { color: '#FFFFFF', fontWeight: '700' },
});

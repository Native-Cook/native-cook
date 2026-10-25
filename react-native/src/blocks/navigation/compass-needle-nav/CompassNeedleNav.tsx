import { Canvas, Picture, Skia, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type Fonts, type Layers, TABS, bakeCompass, bakeGlass, bakeGround, bakePages, drawFrame, makeScene } from './compassArt';
import { BACK_FOR, IDLE_BEFORE_BACK, LOOP, type Pose, type Sim, clamp01, loopPose, makeSim, simPose, stepSim, wrap } from './compassMotion';

/*
 Compass Needle Nav — "Northbound", a hiking app whose bottom navigation is a liquid compass.

 Look: a night-green ground; the page (Map with a contour map and the day's trail, Trips, Gear
 with a pack list, Me with stats and badges); a dock with a compass: a knurled anodised housing,
 a bezel with degrees every 30° and the cardinals (N in red), a well of tinted damping fluid with
 a faint card, a red-and-cream faceted needle floating in it with a shadow, an air bubble and a
 glass dome. Map, Trips, Gear and Me sit round it at N, E, S and W.

 Tap a tab: the needle swings to it through the liquid (an underdamped spring, ω 7 rad/s,
 ζ 0.4 — it overshoots 25 % and settles in ~1.4 s), the bubble drifts against the spin, and the
 pages, which hang on a wheel centred on the compass one every 90°, swing round so the new page
 comes in from the side its tab sits on (critically damped, ω 5.5 rad/s), fading with the angle.
 Drag round the bezel and the needle and the wheel follow the finger (a bead marks it, a light
 tick at every quarter turn); let go and it settles on the nearest tab. 2 s after the last touch
 everything eases back to N (0.9 s) and the loop (compassMotion.ts, LOOP = 8 s: E, S, then on
 round to N) starts again. Reduced motion: no loop, no swing; a tab shows its page at once.
*/

type Props = {
  /** Plays the 8 s loop (N → E → S → round to N) until touched. */
  autoPlay?: boolean;
};

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const ACCENT = '#F07A3A';
const INK = '#EEF1E8';
const MUTE = 'rgba(238,241,232,0.55)';

const AUTO = 0;
const TOUCH = 1;
const BACK = 2;

function tick(kind: 'detent' | 'select') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'detent') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function TabIcon({ index, color }: { index: number; color: string }) {
  const common = { stroke: color, strokeWidth: 1.7, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      {index === 0 ? <Path d="M2 4.5L6.5 2.5L11.5 4.5L16 2.5V13.5L11.5 15.5L6.5 13.5L2 15.5Z M6.5 2.5V13.5 M11.5 4.5V15.5" {...common} /> : null}
      {index === 1 ? (
        <>
          <Path d="M4 14C4 9 14 10 14 5" {...common} />
          <Circle cx={4} cy={14.5} r={1.8} fill={color} />
          <Circle cx={14} cy={3.5} r={1.8} fill={color} />
        </>
      ) : null}
      {index === 2 ? <Path d="M5 6.5C5 4 6.8 2.5 9 2.5C11.2 2.5 13 4 13 6.5 M4 7H14V15.5H4Z M7 10.5H11" {...common} /> : null}
      {index === 3 ? (
        <>
          <Circle cx={9} cy={6} r={3.2} {...common} />
          <Path d="M3 16C3.8 12.6 6.2 11 9 11C11.8 11 14.2 12.6 15 16" {...common} />
        </>
      ) : null}
    </Svg>
  );
}

/** A tab round the compass: muted, with an accent copy over it that shows while its page is in. */
function Tab({ index, wheel, onPress, style }: { index: number; wheel: SharedValue<number>; onPress: () => void; style: object }) {
  const on = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(wrap(wheel.get() - index * 90)) / 45) }));
  const vertical = index === 1 || index === 3;
  const inner = (color: string) => (
    <View style={[styles.tabInner, vertical ? styles.tabColumn : styles.tabRow]}>
      <TabIcon index={index} color={color} />
      <Text style={[styles.tabText, { color }]}>{TABS[index]}</Text>
    </View>
  );
  return (
    <Pressable accessibilityRole="tab" accessibilityLabel={TABS[index]} onPress={onPress} hitSlop={10} style={[styles.tab, style]}>
      {inner(MUTE)}
      <Animated.View style={[StyleSheet.absoluteFill, styles.center, on]}>{inner(ACCENT)}</Animated.View>
    </Pressable>
  );
}

export function CompassNeedleNav({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  const scene = useMemo(() => (size ? makeScene(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const fonts = useMemo<Fonts>(
    () => ({
      title: matchFont({ fontFamily: SANS, fontSize: 32, fontWeight: '700' }),
      body: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '400' }),
      bodyBold: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 13, fontWeight: '400' }),
      tiny: matchFont({ fontFamily: SANS, fontSize: 10, fontWeight: '700' }),
      degree: matchFont({ fontFamily: SANS, fontSize: 8.5, fontWeight: '600' }),
      cardinal: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '700' }),
      big: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '700' }),
    }),
    [],
  );
  const layers = useMemo<Layers | null>(() => {
    if (!scene) return null;
    const scale = Math.min(3, PixelRatio.get());
    const ground = bakeGround(scene, scale);
    const pages = bakePages(scene, fonts, scale);
    const compass = bakeCompass(scene, fonts, scale);
    const glass = bakeGlass(scene, scale);
    if (!ground || !compass || !glass || pages.some((p) => !p)) return null;
    return { ground, pages: pages as NonNullable<(typeof pages)[number]>[], compass, glass };
  }, [scene, fonts]);

  const now = useSharedValue(0);
  /** The frame clock, kept running for gestures even while nothing is drawn. */
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const mode = useSharedValue(AUTO);
  const lastTouch = useSharedValue(0);
  const dragging = useSharedValue(0);
  const sim = useSharedValue<Sim>(makeSim());
  /** Under reduced motion a tab simply shows its page. */
  const still = useSharedValue(0);

  const pose = useDerivedValue<Pose>(() => {
    const at = now.get();
    if (reduced) return { needle: still.get(), wheel: still.get(), spin: 0, finger: NaN, target: still.get() };
    if (mode.get() !== AUTO) return simPose(sim.get(), at);
    if (!auto) return loopPose(0);
    return loopPose((((at - loopStart.get()) % LOOP) + LOOP) % LOOP);
  });
  const wheel = useDerivedValue(() => pose.get().wheel);

  useFrameCallback((frame) => {
    const at = frame.timestamp / 1000;
    clock.set(at);
    if (loopStart.get() < 0) loopStart.set(at);
    if (!auto && mode.get() === AUTO && now.get() > 0) return;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const m = mode.get();
    if (m !== AUTO) {
      const s = sim.get();
      if (m === TOUCH) {
        stepSim(s, dt);
        if (dragging.get() === 0 && at - lastTouch.get() > IDLE_BEFORE_BACK) {
          s.from = [s.needle, s.wheel, 0];
          s.target = 360 * Math.round(s.wheel / 360);
          s.backAt = at;
          mode.set(BACK);
        }
      } else if (at >= s.backAt + BACK_FOR) {
        sim.set(makeSim());
        mode.set(AUTO);
        loopStart.set(at);
      }
    }
    now.set(at);
  });

  function takeOver() {
    'worklet';
    if (mode.get() === TOUCH) return;
    const p = pose.get();
    const s = makeSim();
    s.needle = p.needle;
    s.wheel = p.wheel;
    s.vel = p.spin;
    s.target = p.target;
    sim.set(s);
    mode.set(TOUCH);
  }

  const select = (index: number) => {
    tick('select');
    scheduleOnUI(() => {
      'worklet';
      lastTouch.set(clock.get());
      if (reduced) {
        still.set(index * 90);
        return;
      }
      takeOver();
      const s = sim.get();
      // The shortest way round to the tab's heading.
      s.target = s.needle + wrap(index * 90 - s.needle);
    });
  };

  const fingerAngle = (x: number, y: number) => {
    'worklet';
    if (!scene) return 0;
    return (Math.atan2(x - scene.cx, -(y - scene.cy)) * 180) / Math.PI;
  };

  const drag = Gesture.Pan()
    .minDistance(2)
    .onBegin((e) => {
      if (!scene || reduced) return;
      const d = Math.hypot(e.x - scene.cx, e.y - scene.cy);
      if (d < scene.r * 0.45 || d > scene.r + 40) return;
      takeOver();
      dragging.set(1);
      const s = sim.get();
      s.finger = fingerAngle(e.x, e.y);
      lastTouch.set(clock.get());
    })
    .onUpdate((e) => {
      if (dragging.get() === 0) return;
      const s = sim.get();
      const a = fingerAngle(e.x, e.y);
      const before = Math.round(s.target / 90);
      s.target += wrap(a - s.finger);
      s.finger = a;
      if (Math.round(s.target / 90) !== before) scheduleOnRN(tick, 'detent');
      lastTouch.set(clock.get());
    })
    .onFinalize(() => {
      if (dragging.get() === 0) return;
      dragging.set(0);
      const s = sim.get();
      s.target = 90 * Math.round(s.target / 90);
      s.finger = NaN;
      lastTouch.set(clock.get());
      scheduleOnRN(tick, 'select');
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size?.width ?? 1, size?.height ?? 1));
    if (scene && layers) {
      const at = now.get();
      const time = mode.get() === AUTO ? (((at - loopStart.get()) % LOOP) + LOOP) % LOOP : at;
      // The needle bobs twice a loop, so the loop still closes.
      drawFrame(canvas, scene, layers, pose.get(), reduced ? 0 : (2 * Math.PI * 2 * time) / LOOP);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {scene && layers ? (
        <GestureDetector gesture={drag}>
          <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Compass navigation. Drag round the bezel to turn, or tap Map, Trips, Gear or Me.">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          </View>
        </GestureDetector>
      ) : null}

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 8 }]}>
        <Text style={styles.kicker}>NORTHBOUND</Text>
        <View style={styles.weather}>
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <Circle cx={7} cy={7} r={3} fill="#F5C26B" />
            <Path d="M7 1V2.6M7 11.4V13M1 7H2.6M11.4 7H13M2.8 2.8L3.9 3.9M10.1 10.1L11.2 11.2M2.8 11.2L3.9 10.1M10.1 3.9L11.2 2.8" stroke="#F5C26B" strokeWidth={1.3} strokeLinecap="round" />
          </Svg>
          <Text style={styles.weatherText}>6° · Clear</Text>
        </View>
      </View>

      {scene ? (
        <>
          <Tab index={0} wheel={wheel} onPress={() => select(0)} style={{ left: scene.cx - 50, top: scene.cy - scene.r - 44, width: 100, height: 32 }} />
          <Tab index={1} wheel={wheel} onPress={() => select(1)} style={{ left: scene.cx + scene.r + 18, top: scene.cy - 26, width: 56, height: 52 }} />
          <Tab index={2} wheel={wheel} onPress={() => select(2)} style={{ left: scene.cx - 50, top: scene.cy + scene.r + 10, width: 100, height: 32 }} />
          <Tab index={3} wheel={wheel} onPress={() => select(3)} style={{ left: scene.cx - scene.r - 74, top: scene.cy - 26, width: 56, height: 52 }} />
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0F1C18', overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 3 },
  weather: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 28,
    paddingHorizontal: 10,
    borderRadius: 14,
    backgroundColor: 'rgba(238,241,232,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(238,241,232,0.16)',
  },
  weatherText: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  tab: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  center: { alignItems: 'center', justifyContent: 'center' },
  tabInner: { alignItems: 'center', justifyContent: 'center' },
  tabRow: { flexDirection: 'row', gap: 6 },
  tabColumn: { flexDirection: 'column', gap: 3 },
  tabText: { fontSize: 13, fontWeight: '600' },
});

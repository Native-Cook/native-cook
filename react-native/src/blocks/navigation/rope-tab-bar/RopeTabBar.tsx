import { Canvas, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawRope, recordBar, ROPE_LINE, ROPE_SAMPLES, TOP_PAD } from './ropeTabBarArt';
import { LOOP, LOOP_KICKS, LOOP_MOVES, SAG, TABS, KNOT_W, KNOT_Z, easeInOut, loopKnot, rippleAt, ropeAt, seg, tabU } from './ropeTabBarMotion';
import { INK, RopeTabBarPages } from './RopeTabBarPages';

/*
 Rope Tab Bar — "Basecamp", an outdoor app whose tab indicator is a knot on a climbing rope tied
 across the top of the tab bar. Change tab and the knot slides along: the rope sags under it,
 lifts where it left, whips and rings down; the icons it passes are tugged down a few points.
 The pages cross-slide with a parallax and the large title cross-fades as it slides.

 One Skia canvas over the bar (ropeTabBarArt.ts): the bar recorded once, the rope (48 samples, a
 braid of ~85 strands in two paths), the knot and the tap ripple drawn per frame. The rope is a
 closed-form function of time in every mode (ropeTabBarMotion.ts): a sag, the knot's weight and
 four string modes rung by "kicks". autoPlay: LOOP = 8 s, Explore → Gear → Routes → Explore.
 Under the finger: tap a tab, or drag the knot along the rope (a tick per tab, snaps on release).
 React renders only when the active tab changes.

 Modes: 0 loop · 1 touch (knot on a spring toward its tab) · 2 easing back to Explore 2 s after
 the last touch (0.9 s), then the loop restarts.
 */

type Props = {
  /** Plays the 8 s demo: Explore → Gear → Routes → Explore. */
  autoPlay?: boolean;
};

const BACKGROUND = '#13201a';
const BAR_CONTENT = 78;
const TAB_NAMES = ['Explore', 'Routes', 'Gear', 'Profile'];
const TITLES = ['Explore', 'Routes', 'Gear', 'Profile'];
const ICONS = [
  { ios: 'mountain.2.fill', android: 'landscape' },
  { ios: 'map.fill', android: 'map' },
  { ios: 'backpack.fill', android: 'backpack' },
  { ios: 'person.crop.circle.fill', android: 'account_circle' },
] as const;
const ACTIVE = '#f4ead8';
const IDLE = '#7f9387';
const RETURN_AFTER = 2;
const RETURN_FOR = 0.9;
const MAX_KICKS = 8;

function feel(kind: 'tap' | 'tick') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One tab: an icon and a label in idle and active colours, cross-faded, tugged by the rope. */
function Tab({ index, width, p, tug }: { index: number; width: number; p: SharedValue<number>; tug: SharedValue<number[]> }) {
  const on = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(p.get() - index)) }));
  const off = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.abs(p.get() - index)) }));
  const move = useAnimatedStyle(() => ({ transform: [{ translateY: tug.get()[index] ?? 0 }] }));
  const fallback = <View style={[styles.fallback, { backgroundColor: IDLE }]} />;
  return (
    <View style={[styles.tab, { left: index * width, width }]} pointerEvents="none">
      <Animated.View style={[styles.icon, move]}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.center, off]}>
          <SymbolView name={ICONS[index]} size={24} tintColor={IDLE} fallback={fallback} />
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, styles.center, on]}>
          <SymbolView name={ICONS[index]} size={24} tintColor={ACTIVE} fallback={<View style={[styles.fallback, { backgroundColor: ACTIVE }]} />} />
        </Animated.View>
      </Animated.View>
      <View style={styles.labelBox}>
        <Animated.Text style={[styles.label, { color: IDLE }, off]}>{TAB_NAMES[index]}</Animated.Text>
        <Animated.Text style={[styles.label, styles.labelOn, on]}>{TAB_NAMES[index]}</Animated.Text>
      </View>
    </View>
  );
}

function Title({ index, p }: { index: number; p: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = p.get() - index;
    const a = Math.abs(d);
    return {
      opacity: Math.max(0, 1 - a * 1.6),
      transform: [{ translateX: -d * 26 }, { scale: 1 - 0.06 * Math.min(1, a) }],
    };
  });
  return <Animated.Text style={[styles.title, style]}>{TITLES[index]}</Animated.Text>;
}

export function RopeTabBar({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [activeTab, setActiveTab] = useState(0);
  const w = size.width;
  const barH = BAR_CONTENT + insets.bottom;
  const bar = useMemo(() => (w > 0 ? recordBar(w, barH) : null), [w, barH]);

  const now = useSharedValue(-1);
  const loopStart = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const p = useSharedValue(0);
  const pVel = useSharedValue(0);
  const target = useSharedValue(0);
  const dragging = useSharedValue(0);
  /** Kicks in the touch timeline: [time, u, size] × n. */
  const touchKicks = useSharedValue<number[]>([]);
  /** What the rope reads: kicks, the clock they are timed on, and their gain (fades on return). */
  const kicks = useSharedValue<number[]>(LOOP_KICKS);
  const kickClock = useSharedValue(0);
  const gain = useSharedValue(1);
  /** [start time, tab] of the latest tap ripple, on the kick clock. */
  const ripple = useSharedValue<number[]>([-10, 0]);
  const lastTouch = useSharedValue(0);
  const back = useSharedValue<number[]>([0, 0]);
  const tug = useSharedValue<number[]>([0, 0, 0, 0]);

  useFrameCallback((info) => {
    const t0 = info.timestamp / 1000;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 1 / 30);
    if (now.get() < 0) loopStart.set(t0);
    now.set(t0);
    const m = mode.get();
    if (m === 0) {
      const t = (t0 - loopStart.get()) % LOOP;
      p.set(loopKnot(t));
      kicks.set(LOOP_KICKS);
      kickClock.set(t);
      gain.set(1);
      let r = [-10, 0];
      for (let i = 0; i < LOOP_MOVES.length; i++) if (rippleAt(t, LOOP_MOVES[i][0]) >= 0) r = [LOOP_MOVES[i][0], LOOP_MOVES[i][2]];
      ripple.set(r);
    } else {
      kicks.set(touchKicks.get());
      kickClock.set(t0);
      if (m === 1) {
        gain.set(1);
        if (dragging.get() === 0) {
          // The knot rides a spring to its tab (ω 7.5, ζ 0.78), carrying the release velocity.
          let x = p.get();
          let v = pVel.get();
          const h = dt / 3;
          for (let k = 0; k < 3; k++) {
            v += (-KNOT_W * KNOT_W * (x - target.get()) - 2 * KNOT_Z * KNOT_W * v) * h;
            x += v * h;
          }
          if (reduced) {
            x = target.get();
            v = 0;
          }
          p.set(x);
          pVel.set(v);
          if (auto && t0 - lastTouch.get() > RETURN_AFTER && Math.abs(x - target.get()) < 0.005) {
            mode.set(2);
            back.set([t0, x]);
          }
        }
      } else {
        const b = back.get();
        const k = easeInOut(seg(t0 - b[0], 0, RETURN_FOR));
        p.set(b[1] * (1 - k));
        gain.set(1 - k);
        if (k >= 1) {
          mode.set(0);
          loopStart.set(t0);
          touchKicks.set([]);
        }
      }
    }
    // Icons are tugged by how far the rope hangs below its slack line at their x.
    const uk = tabU(p.get());
    const out = [0, 0, 0, 0];
    for (let i = 0; i < TABS; i++) {
      const u = tabU(i);
      const y = ropeAt(u, uk, kicks.get(), kickClock.get(), gain.get()) - SAG * Math.sin(Math.PI * u);
      out[i] = Math.min(6, Math.max(-3, y * 0.45));
    }
    tug.set(out);
  });

  useAnimatedReaction(
    () => Math.round(Math.min(TABS - 1, Math.max(0, p.get()))),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setActiveTab, v);
    },
  );

  const picture = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const H = barH + TOP_PAD;
    const canvas = rec.beginRecording(Skia.XYWHRect(0, 0, w, H));
    if (bar && w > 0) {
      canvas.drawPicture(bar);
      const base = TOP_PAD + ROPE_LINE;
      const uk = tabU(p.get());
      const ks = kicks.get();
      const tk = kickClock.get();
      const gn = gain.get();
      const ys: number[] = [];
      for (let i = 0; i <= ROPE_SAMPLES; i++) ys.push(base + ropeAt(i / ROPE_SAMPLES, uk, ks, tk, gn));
      const ky = base + ropeAt(uk, uk, ks, tk, gn);
      const slope = (ropeAt(uk + 0.01, uk, ks, tk, gn) - ropeAt(uk - 0.01, uk, ks, tk, gn)) / (0.02 * w);
      const r = ripple.get();
      const life = rippleAt(tk, r[0]);
      drawRope(canvas, w, ys, uk * w, ky + 1, slope, life, tabU(r[1]) * w, TOP_PAD + 40);
    }
    return rec.finishRecordingAsPicture();
  });

  const gesture = useMemo(() => {
    const cell = w / TABS;
    function addKick(at: number, u: number, size: number) {
      'worklet';
      const k = touchKicks.get().slice(-3 * (MAX_KICKS - 1));
      k.push(at, u, size);
      touchKicks.set(k);
    }
    function takeOver() {
      'worklet';
      if (mode.get() !== 0) return;
      // Freeze the loop's place and ringing into the touch timeline.
      const t = (now.get() - loopStart.get()) % LOOP;
      const shift = now.get() - t;
      const k: number[] = [];
      for (let i = 0; i < LOOP_KICKS.length; i += 3) k.push(LOOP_KICKS[i] + shift, LOOP_KICKS[i + 1], LOOP_KICKS[i + 2]);
      touchKicks.set(k);
      pVel.set(0);
      target.set(Math.round(p.get()));
      mode.set(1);
    }
    const tap = Gesture.Tap()
      .maxDistance(12)
      .onEnd((e) => {
        if (cell <= 0) return;
        takeOver();
        mode.set(1);
        const tab = Math.min(TABS - 1, Math.max(0, Math.floor(e.x / cell)));
        const from = p.get();
        const d = Math.pow(Math.abs(tab - from), 0.6);
        const t = now.get();
        if (!reduced && Math.abs(tab - from) > 0.05) {
          addKick(t + 0.05, tabU(from), -3.2 * d);
          addKick(t + 0.42, tabU(tab), 6 * d);
        }
        target.set(tab);
        ripple.set([t, tab]);
        lastTouch.set(t);
        scheduleOnRN(feel, 'tap');
      });
    const pan = Gesture.Pan()
      .manualActivation(true)
      .onTouchesDown((e, manager) => {
        const touch = e.changedTouches[0];
        const kx = tabU(p.get()) * w;
        if (!touch || Math.abs(touch.x - kx) > 40 || touch.y > TOP_PAD + 40) manager.fail();
      })
      .onTouchesMove((e, manager) => {
        const touch = e.changedTouches[0];
        if (touch && Math.abs(touch.x - tabU(p.get()) * w) > 4) manager.activate();
      })
      .onStart(() => {
        takeOver();
        mode.set(1);
        dragging.set(1);
        pVel.set(0);
      })
      .onUpdate((e) => {
        if (cell <= 0) return;
        let q = e.x / cell - 0.5;
        if (q < 0) q = -(-q / (1 - q / 0.6)) * 0.55;
        if (q > TABS - 1) {
          const o = q - (TABS - 1);
          q = TABS - 1 + (o / (1 + o / 0.6)) * 0.55;
        }
        const before = Math.round(p.get());
        const vel = e.velocityX / cell;
        pVel.set(vel);
        p.set(q);
        const after = Math.round(Math.min(TABS - 1, Math.max(0, q)));
        if (after !== before) {
          addKick(now.get(), tabU(after), Math.min(4, Math.abs(vel) * 0.8));
          scheduleOnRN(feel, 'tick');
        }
        lastTouch.set(now.get());
      })
      .onFinalize(() => {
        if (dragging.get() === 0) return;
        dragging.set(0);
        const q = p.get();
        const v = reduced ? 0 : Math.max(-6, Math.min(6, pVel.get()));
        const tab = Math.min(TABS - 1, Math.max(0, Math.round(q + v * 0.15)));
        target.set(tab);
        pVel.set(v);
        if (!reduced) addKick(now.get() + 0.3, tabU(tab), 2 + 3.5 * Math.pow(Math.abs(tab - q), 0.6));
        lastTouch.set(now.get());
        scheduleOnRN(feel, 'tap');
      });
    return Gesture.Race(pan, tap);
    // Shared values are stable; the gesture depends on the width and reduced motion.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [w, reduced]);

  const pages = useAnimatedStyle(() => ({ transform: [{ translateX: -p.get() * w }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  const barTop = size.height - barH;
  const pagesTop = insets.top + 108;
  return (
    <View style={styles.root} onLayout={onLayout}>
      {bar && w > 0 ? (
        <>
          <View style={[styles.header, { top: insets.top + 10 }]} pointerEvents="none">
            <View style={styles.topRow}>
              <Text style={styles.kicker}>BASECAMP</Text>
              <View style={styles.weather}>
                <View style={styles.sun} />
                <Text style={styles.weatherText}>14° Clear</Text>
              </View>
            </View>
            <View style={styles.titles}>
              {TITLES.map((_, i) => (
                <Title key={i} index={i} p={p} />
              ))}
            </View>
          </View>

          <View style={[styles.pagesClip, { top: pagesTop, height: Math.max(0, barTop - pagesTop) }]}>
            <Animated.View style={[styles.pages, { width: w * TABS }, pages]}>
              <RopeTabBarPages p={p} width={w} />
            </Animated.View>
          </View>

          <GestureDetector gesture={gesture}>
            <View
              style={[styles.bar, { top: barTop - TOP_PAD, height: barH + TOP_PAD }]}
              accessible
              accessibilityRole="tabbar"
              accessibilityLabel={`Tabs, ${TAB_NAMES[activeTab]} selected`}
            >
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
              {TAB_NAMES.map((_, i) => (
                <Tab key={i} index={i} width={w / TABS} p={p} tug={tug} />
              ))}
            </View>
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: BACKGROUND, overflow: 'hidden' },
  header: { position: 'absolute', left: 20, right: 20, gap: 8 },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 32 },
  kicker: { color: '#e8743b', fontSize: 12, fontWeight: '800', letterSpacing: 2.6 },
  weather: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 30, paddingHorizontal: 12, borderRadius: 15, backgroundColor: 'rgba(242,235,221,0.07)' },
  sun: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#f2b766', boxShadow: '0 0 8px rgba(242,183,102,0.7)' },
  weatherText: { color: INK, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  titles: { height: 44 },
  title: { position: 'absolute', left: 0, top: 0, color: INK, fontSize: 34, fontWeight: '700', letterSpacing: -0.6, transformOrigin: 'left center' },
  pagesClip: { position: 'absolute', left: 0, right: 0, overflow: 'hidden' },
  pages: { flexDirection: 'row', paddingTop: 2 },
  bar: { position: 'absolute', left: 0, right: 0 },
  tab: { position: 'absolute', top: TOP_PAD + 26, height: 52, alignItems: 'center' },
  icon: { width: 30, height: 28 },
  center: { alignItems: 'center', justifyContent: 'center' },
  fallback: { width: 18, height: 18, borderRadius: 5 },
  labelBox: { height: 16, marginTop: 6, alignSelf: 'stretch' },
  label: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 11, fontWeight: '600' },
  labelOn: { color: ACTIVE },
});


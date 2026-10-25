import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { DARK, LOOP, type Scene, blendScene, copyScene, easeInOut, loopScene, makeLayout, makeScene } from './stainedGlassMotion';
import { STAINED_SKSL } from './stainedGlassShader';

/*
 Stained Glass Splash — the launch of "Chapel", a meditation app. A rose window waits dim in a
 stone wall; the sun crosses it and the panes light one by one (outer rings first, the gold rose
 of the app's mark last), throwing their colours down a dusty shaft onto the flagstones. Then the
 light floods out of the window into cream and the home screen rises through it, its content arriving with the cream.

 Drawing: one Canvas with one RuntimeEffect (stainedGlassShader.ts) for wall, window, beams, dust
 and floor, and the flood. Uniforms come from one scene object written on the UI thread
 (stainedGlassMotion.ts). The wordmark and the home screen are views whose opacity and offset read
 the same shared value: React renders them once.

 Touch: during the splash, drag across the window to move the sun: the beam follows the finger and
 every pane it passes lights and stays lit. Light all of it and the splash carries on into the
 flood and the home screen; otherwise, 2 s after the finger lifts, the panes go dark again (0.9 s)
 and the loop restarts. The home screen itself does not take the gesture.
 Reduced motion: no loop; the home screen on cream.
*/

type Props = {
  /** Plays the 9 s light-floor-flood-home loop. */
  autoPlay?: boolean;
};

const MODE_AUTO = 0;
const MODE_USER = 1;
const MODE_RETURN = 2;
const IDLE = 2;
const RETURN = 0.9;
/** Where the loop picks up after a finger has lit every pane: the floor's peak. */
const RESUME_LIT = 4.4;
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });

const INK = '#2B2119';
const INK_SOFT = 'rgba(43,33,25,0.62)';
const RUBY = '#9B2D2B';
const CREAM_CARD = 'rgba(255,251,243,0.78)';
const effect = Skia.RuntimeEffect.Make(STAINED_SKSL);

function feel() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

export function StainedGlassSplash({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const win = useWindowDimensions();
  const [size, setSize] = useState({ width: win.width, height: win.height });
  const L = useMemo(() => makeLayout(size.width, size.height, insets.top), [size.width, size.height, insets.top]);

  const mode = useSharedValue(MODE_AUTO);
  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const holding = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const scene = useSharedValue<Scene>(makeScene());
  const from = useSharedValue<Scene>(makeScene());
  // The finger's sun: [lo, hi, beam, keyMix], and whether the full window was lit once.
  const hand = useSharedValue<number[]>([DARK, DARK, -0.2, 1]);
  const full = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min((f.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const at = now.get() + dt;
    now.set(at);
    const s = makeScene();
    const m = mode.get();
    if (reduced) {
      loopScene(7.0, s);
    } else if (m === MODE_AUTO) {
      const t = auto ? (clock.get() + dt) % LOOP : 0;
      clock.set(t);
      loopScene(t, s);
    } else if (m === MODE_USER) {
      const h = hand.get();
      // The key eases from the loop's order to plain left to right (0.3 s).
      const km = Math.max(0, h[3] - dt / 0.3);
      hand.set([h[0], h[1], h[2], km]);
      s.lo = h[0];
      s.hi = h[1];
      s.beam = h[2];
      s.keyMix = km;
      s.mark = 0.55 + 0.45 * Math.min(1, Math.max(0, (h[1] - h[0]) / 1.0));
      if (!holding.get() && full.get()) {
        // The whole window is lit: hand back to the loop at the floor's peak.
        const g = makeScene();
        loopScene(RESUME_LIT, g);
        from.set(copyScene(s));
        returnAt.set(at);
        clock.set(RESUME_LIT);
        mode.set(MODE_RETURN);
      } else if (!holding.get() && at - lastTouch.get() > IDLE) {
        from.set(copyScene(s));
        returnAt.set(at);
        clock.set(0);
        mode.set(MODE_RETURN);
      }
    } else {
      // Ease into the loop's frame at the clock it will resume from.
      const e = easeInOut((at - returnAt.get()) / RETURN);
      const target = makeScene();
      loopScene(clock.get(), target);
      blendScene(from.get(), target, e, s);
      if (e >= 1) mode.set(MODE_AUTO);
    }
    scene.set(s);
  });

  /** The finger's x as a place across the window (0 left edge … 1 right edge). */
  function across(x: number) {
    'worklet';
    return (x - (L.cx - L.R)) / (2 * L.R);
  }

  function spread(x: number) {
    'worklet';
    const h = hand.get();
    const b = across(x);
    const lo = Math.min(h[0], b - 0.06);
    const hi = Math.max(h[1], b + 0.06);
    hand.set([lo, hi, b, h[3]]);
    if (!full.get() && lo < 0.04 && hi > 0.96) {
      full.set(1);
      scheduleOnRN(feel);
    }
  }

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      // The home screen and the flood do not take the finger.
      if (scene.get().flood > 0.05) return;
      holding.set(1);
      lastTouch.set(now.get());
      if (mode.get() !== MODE_USER) {
        const s = scene.get();
        const b = across(e.x);
        // A lit range carries over in plain left-to-right keys; a dark window starts at the finger.
        const lit = s.hi - s.lo > 0.05;
        hand.set(lit ? [Math.max(-0.05, s.lo), Math.min(1.05, s.hi), b, s.keyMix] : [b - 0.06, b + 0.06, b, s.keyMix]);
        full.set(0);
        mode.set(MODE_USER);
      }
      spread(e.x);
    })
    .onUpdate((e) => {
      if (mode.get() !== MODE_USER || !holding.get()) return;
      spread(e.x);
      lastTouch.set(now.get());
    })
    .onFinalize(() => {
      holding.set(0);
      lastTouch.set(now.get());
    });

  const uniforms = useDerivedValue(() => {
    const s = scene.get();
    return {
      size: [size.width, size.height],
      win: [L.cx, L.cy, L.R],
      sweep: [s.lo, s.hi, s.beam],
      keyMix: s.keyMix,
      flood: s.flood,
      phase: clock.get() / LOOP,
    };
  });

  const markStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return { opacity: s.mark * (1 - Math.min(1, s.flood * 2.5)), transform: [{ translateY: 6 * (1 - s.mark) }] };
  });
  const homeStyle = useAnimatedStyle(() => {
    const s = scene.get();
    const shown = s.home * (1 - s.homeOut);
    return { opacity: shown, transform: [{ translateY: 12 * (1 - s.home) - 6 * s.homeOut }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (Math.abs(width - size.width) > 0.5 || Math.abs(height - size.height) > 0.5) setSize({ width, height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A rose window. Drag across it to move the sun and light the panes.">
          {effect ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms} />
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.mark, { top: L.cy + L.R * 1.04 + 18 }, markStyle]}>
        <Text style={styles.wordmark}>Chapel</Text>
        <Text style={styles.tagline}>QUIET MINUTES, EVERY DAY</Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, homeStyle]}>
        <Home top={insets.top} bottom={insets.bottom} />
      </Animated.View>
    </View>
  );
}

function Tile({ title, detail, colors }: { title: string; detail: string; colors: [string, string, string] }) {
  return (
    <View style={styles.tile}>
      <LinearGradient colors={colors} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.tileArt} />
      <Text style={styles.tileTitle} numberOfLines={1}>{title}</Text>
      <Text style={styles.tileDetail} numberOfLines={1}>{detail}</Text>
    </View>
  );
}

function Tab({ icon, label, on }: { icon: SymbolViewProps['name']; label: string; on?: boolean }) {
  const tint = on ? RUBY : 'rgba(43,33,25,0.45)';
  return (
    <View style={styles.tab}>
      <SymbolView name={icon} size={20} tintColor={tint} fallback={<View style={[styles.tabDot, { backgroundColor: tint }]} />} />
      <Text style={[styles.tabLabel, { color: tint }]}>{label}</Text>
    </View>
  );
}

const WEEK = [
  { day: 'M', v: 0.55 },
  { day: 'T', v: 0.8 },
  { day: 'W', v: 0.35 },
  { day: 'T', v: 0.95 },
  { day: 'F', v: 0.7 },
  { day: 'S', v: 0.0 },
  { day: 'S', v: 0.0 },
];

/** The home screen the splash opens onto (static: it only fades and rises). */
function Home({ top, bottom }: { top: number; bottom: number }) {
  return (
    <View style={[styles.home, { paddingTop: top + 18, paddingBottom: bottom + 10 }]}>
      <View style={styles.homeHead}>
        <Text style={styles.kicker}>CHAPEL</Text>
        <Text style={styles.greeting}>Good evening, Mira</Text>
        <Text style={styles.homeSub}>Day 14 · 86 minutes this week</Text>
      </View>
      <LinearGradient colors={['#2B3F8F', '#4B2A78', '#7A2440']} locations={[0, 0.55, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.today}>
        <Text style={styles.todayKicker}>TODAY&apos;S SIT</Text>
        <Text style={styles.todayTitle}>Stillwater</Text>
        <Text style={styles.todayDetail}>12 min · Breath and stillness</Text>
        <View style={styles.todayRow}>
          <View style={styles.play}>
            <SymbolView name={{ ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }} size={18} tintColor="#2B3F8F" fallback={<View style={styles.playFallback} />} />
          </View>
          <Text style={styles.todayMeta}>Guided by Ines Varga</Text>
        </View>
      </LinearGradient>
      <Text style={styles.section}>Keep going</Text>
      <View style={styles.tiles}>
        <Tile title="Wind down" detail="8 min" colors={['#E7B45A', '#C9733A', '#8E3A2E']} />
        <Tile title="Morning light" detail="10 min" colors={['#9FCB9A', '#3E8A6A', '#22524E']} />
        <Tile title="Steady focus" detail="5 min" colors={['#B9A3E0', '#6C55B0', '#352A6E']} />
      </View>
      <View style={styles.week}>
        <View style={styles.weekHead}>
          <Text style={styles.weekTitle}>Your week</Text>
          <Text style={styles.weekTotal}>86 min</Text>
        </View>
        <View style={styles.bars}>
          {WEEK.map((d, i) => (
            <View key={i} style={styles.barCol}>
              <View style={styles.barTrack}>
                <View style={[styles.bar, { height: `${d.v * 100}%`, backgroundColor: i === 4 ? RUBY : 'rgba(155,45,43,0.32)' }]} />
              </View>
              <Text style={[styles.barDay, i === 4 && { color: RUBY }]}>{d.day}</Text>
            </View>
          ))}
        </View>
      </View>
      <View style={styles.spacer} />
      <View style={styles.tabs}>
        <Tab icon={{ ios: 'sun.horizon.fill', android: 'wb_twilight', web: 'wb_twilight' }} label="Today" on />
        <Tab icon={{ ios: 'square.grid.2x2', android: 'grid_view', web: 'grid_view' }} label="Library" />
        <Tab icon={{ ios: 'book.closed', android: 'book', web: 'book' }} label="Journal" />
        <Tab icon={{ ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' }} label="You" />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#15110E' },
  mark: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 6 },
  wordmark: { color: '#F4E4C8', fontFamily: SERIF, fontSize: 44, fontStyle: 'italic', letterSpacing: 0.5 },
  tagline: { color: 'rgba(244,228,200,0.62)', fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  home: { flex: 1, paddingHorizontal: 20, gap: 16 },
  homeHead: { gap: 4 },
  kicker: { color: RUBY, fontSize: 11, fontWeight: '800', letterSpacing: 2.2 },
  greeting: { color: INK, fontFamily: SERIF, fontSize: 32, letterSpacing: -0.3 },
  homeSub: { color: INK_SOFT, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  today: { borderRadius: 26, borderCurve: 'continuous', padding: 20, gap: 4, overflow: 'hidden', boxShadow: '0 1px 2px rgba(60,30,20,0.18), 0 16px 34px rgba(60,30,20,0.22)' },
  todayKicker: { color: 'rgba(255,240,220,0.72)', fontSize: 11, fontWeight: '800', letterSpacing: 2 },
  todayTitle: { color: '#FFF6E8', fontFamily: SERIF, fontSize: 30 },
  todayDetail: { color: 'rgba(255,240,220,0.8)', fontSize: 14, fontWeight: '500' },
  todayRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 14 },
  play: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#FFF6E8', alignItems: 'center', justifyContent: 'center' },
  playFallback: { width: 0, height: 0, borderLeftWidth: 12, borderTopWidth: 8, borderBottomWidth: 8, borderLeftColor: '#2B3F8F', borderTopColor: 'transparent', borderBottomColor: 'transparent', marginLeft: 3 },
  todayMeta: { color: 'rgba(255,240,220,0.8)', fontSize: 13, fontWeight: '600' },
  section: { color: INK, fontSize: 17, fontWeight: '700', marginTop: 4 },
  tiles: { flexDirection: 'row', gap: 10 },
  tile: { flex: 1, padding: 10, gap: 4, borderRadius: 18, borderCurve: 'continuous', backgroundColor: CREAM_CARD, boxShadow: '0 1px 2px rgba(60,30,20,0.1), 0 8px 20px rgba(60,30,20,0.08)' },
  tileArt: { height: 64, borderRadius: 12, borderCurve: 'continuous', marginBottom: 4 },
  tileTitle: { color: INK, fontSize: 14, fontWeight: '700' },
  tileDetail: { color: INK_SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  week: { flexShrink: 1, minHeight: 0, padding: 14, gap: 10, borderRadius: 20, borderCurve: 'continuous', backgroundColor: CREAM_CARD, boxShadow: '0 1px 2px rgba(60,30,20,0.1), 0 8px 20px rgba(60,30,20,0.08)' },
  weekHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  weekTitle: { color: INK, fontSize: 15, fontWeight: '700' },
  weekTotal: { color: INK_SOFT, fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  bars: { flex: 1, minHeight: 28, maxHeight: 120, flexDirection: 'row', gap: 10 },
  barCol: { flex: 1, alignItems: 'center', gap: 6 },
  barTrack: { flex: 1, width: 12, borderRadius: 6, backgroundColor: 'rgba(155,45,43,0.08)', justifyContent: 'flex-end', overflow: 'hidden' },
  bar: { width: 12, borderRadius: 6 },
  barDay: { color: INK_SOFT, fontSize: 11, fontWeight: '700' },
  spacer: { flex: 1 },
  tabs: { flexDirection: 'row', height: 60, borderRadius: 30, backgroundColor: CREAM_CARD, alignItems: 'center', justifyContent: 'space-around', boxShadow: '0 1px 2px rgba(60,30,20,0.1), 0 10px 24px rgba(60,30,20,0.1)' },
  tab: { alignItems: 'center', gap: 2, minWidth: 60 },
  tabDot: { width: 8, height: 8, borderRadius: 4 },
  tabLabel: { fontSize: 11, fontWeight: '700' },
});

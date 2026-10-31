import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState, type ReactNode } from 'react';
import { AppState, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Line, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

// A home screen being laid out in an invented launcher, Tiles, over a blue drafting grid: a map
// strip, a weather tile, four app icons and a black dot-matrix calendar for October whose days are
// round dots — a three-day streak in green, today in red. The widgets fold away into the grid,
// then pop back in one by one, each swinging up out of the page in 3D and settling with a spring,
// and the calendar's streak lights up dot by dot. Drag to tilt the board.
//
// Drawn with views and SVG (map, icons, grid).
// Layout (393 x 852 pt): 34 pt top row; 23 pt margins, 17 pt gaps: map 347 x 136; weather 165 x
// 165 next to four 74 pt icons (2 x 2, 17 pt apart); calendar 347 x 230 (7 x 6 dots of 30 pt);
// heading under the stack.
//
// autoPlay timeline (LOOP = 8 s):
//   0.0-1.0  rest: the finished screen
//   1.0-1.9  the widgets fold away (scale 1 -> 0.2, tip 40 deg, fade), last first, 0.08 s apart
//   1.9-2.3  the empty grid
//   2.3-5.0  they pop back in (0.6 s each, 0.42 s apart: calendar, weather, icons, map; ease-out
//            back on scale 0.2 -> 1, tip 40 -> 0 deg, rise 40 -> 0 pt)
//   2.7-3.5  the calendar's streak lights day by day (green, 0.2 s apart), then today turns red
//   5.0-8.0  rest, identical to t = 0
// Dragging tilts the board (8 deg per 100 pt, both axes); the loop waits while you hold it and
// for 2 s after, while the board eases back.

/** Seconds. */
export const LOOP = 8;
const M = 23;
const G = 17;
const HOLD = 2;
// Pop-in order: calendar, weather, four icons, map.
const ORDER = ['calendar', 'weather', 'i0', 'i1', 'i2', 'i3', 'map'];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutBack(x: number) {
  'worklet';
  const k = 1.6;
  return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
}
function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

/** 0 folded away .. 1 in place (may overshoot), for widget n of the order, `t` s into the loop. */
function shownAt(n: number, t: number) {
  'worklet';
  const away = easeInCubic(seg(t, 1.0 + (ORDER.length - 1 - n) * 0.08, 1.4 + (ORDER.length - 1 - n) * 0.08));
  if (t < 1.9) return 1 - away;
  const start = 2.3 + n * 0.42;
  return easeOutBack(seg(t, start, start + 0.6));
}

function Pop({ n, clock, style, children }: { n: number; clock: SharedValue<number>; style: object; children: ReactNode }) {
  const anim = useAnimatedStyle(() => {
    const s = shownAt(n, clock.get());
    const k = clamp01(s);
    return {
      opacity: clamp01(s * 2.5),
      transform: [{ perspective: 700 }, { translateY: 40 * (1 - k) }, { rotateX: `${40 * (1 - s)}deg` }, { rotateY: `${-22 * (1 - s)}deg` }, { scale: 0.2 + 0.8 * s }],
    };
  });
  return <Animated.View style={[styles.abs, style, anim]}>{children}</Animated.View>;
}

function Day({ d, col, clock }: { d: number; col: number; clock: SharedValue<number> }) {
  const streak = d >= 12 && d <= 14;
  const today = d === 15;
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    if (streak) {
      // Lit in turn after the calendar pops in; dark while it is away.
      const on = t >= 2.3 && t < 2.7 + (d - 12) * 0.2 ? 0 : t >= 1.0 && t < 2.3 ? 0 : 1;
      return { backgroundColor: on ? '#43D86B' : '#2E2E33' };
    }
    if (today) {
      const on = t >= 2.3 && t < 3.5 ? 0 : 1;
      return { backgroundColor: on ? '#FF3B4E' : '#2E2E33' };
    }
    return { backgroundColor: '#2E2E33' };
  });
  return (
    <Animated.View style={[styles.day, { left: col * 44 }, style]}>
      <Text style={[styles.dayText, (streak || today) && styles.dayHot]}>{d}</Text>
    </Animated.View>
  );
}

function Calendar({ clock }: { clock: SharedValue<number> }) {
  // October 2026 starts on a Thursday (Monday-first week, column 3).
  const cells: { d: number; row: number; col: number }[] = [];
  for (let d = 1; d <= 31; d++) {
    const i = d + 2;
    cells.push({ d, row: Math.floor(i / 7), col: i % 7 });
  }
  return (
    <View style={styles.calendar}>
      <View style={styles.calHead}>
        <Text style={styles.calMonth}>OCTOBER</Text>
        <Text style={styles.calYear}>2026</Text>
      </View>
      {[0, 1, 2, 3, 4].map((row) => (
        <View key={row} style={[styles.calRow, { top: 50 + row * 34 }]}>
          {row === 0 && [0, 1, 2].map((c) => <View key={`e${c}`} style={[styles.day, styles.dayEmpty, { left: c * 44 }]} />)}
          {cells
            .filter((c) => c.row === row)
            .map((c) => (
              <Day key={c.d} d={c.d} col={c.col} clock={clock} />
            ))}
        </View>
      ))}
    </View>
  );
}

function Weather() {
  return (
    <LinearGradient colors={['#4FA3FF', '#1F5BEA']} style={styles.weather}>
      <Text style={styles.wCity}>Harbourview ◂</Text>
      <Text style={styles.wTemp}>18°</Text>
      <View style={styles.wSun} />
      <Text style={styles.wLine}>Mostly sunny</Text>
      <Text style={styles.wRange}>H:21° L:12°</Text>
    </LinearGradient>
  );
}

function MapStrip() {
  return (
    <View style={styles.map}>
      <Svg width="100%" height="100%" viewBox="0 0 347 136">
        <Rect x={0} y={0} width={347} height={136} fill="#EEF0EC" />
        <Path d="M0 96 C80 80 140 120 220 98 S320 70 347 82 L347 104 C300 96 250 126 200 120 S80 104 0 116 Z" fill="#9CCBF0" />
        <Rect x={240} y={14} width={80} height={46} rx={10} fill="#CDE8C3" />
        <Line x1={0} y1={40} x2={347} y2={30} stroke="#FFFFFF" strokeWidth={9} />
        <Line x1={120} y1={0} x2={150} y2={136} stroke="#FFFFFF" strokeWidth={11} />
        <Line x1={0} y1={64} x2={347} y2={60} stroke="#FFFFFF" strokeWidth={5} />
        <Line x1={210} y1={0} x2={196} y2={136} stroke="#FFD27A" strokeWidth={7} />
        <Circle cx={140} cy={52} r={11} fill="#1F7BFF" stroke="#FFFFFF" strokeWidth={3} />
      </Svg>
      <View style={styles.mapChip}>
        <Text style={styles.mapChipText}>12 min to Studio</Text>
      </View>
    </View>
  );
}

function Icon({ kind }: { kind: number }) {
  return (
    <View style={[styles.icon, kind === 3 && styles.iconDark]}>
      <Svg width={46} height={46} viewBox="-1 -1 2 2">
        {kind === 0 && (
          <>
            <Circle cx={0} cy={0} r={0.85} stroke="#D7D9DE" strokeWidth={0.08} fill="none" />
            <Path d="M0.5 -0.5 L0.08 0.08 L-0.08 -0.08 Z" fill="#E8344A" />
            <Path d="M-0.5 0.5 L-0.08 -0.08 L0.08 0.08 Z" fill="#2F6BFF" />
          </>
        )}
        {kind === 1 && (
          <>
            <Rect x={-0.85} y={-0.55} width={1.7} height={1.2} rx={0.25} fill="#1C1C1E" />
            <Circle cx={0} cy={0.05} r={0.38} fill="#FFFFFF" />
            <Circle cx={0} cy={0.05} r={0.24} fill="#1C1C1E" />
          </>
        )}
        {kind === 2 && (
          <>
            <Rect x={-0.85} y={-0.6} width={1.7} height={1.2} rx={0.2} fill="#2F80FF" />
            <Path d="M-0.85 -0.5 L0 0.15 L0.85 -0.5" stroke="#FFFFFF" strokeWidth={0.12} fill="none" />
          </>
        )}
        {kind === 3 && <Path d="M-0.2 0.6 L-0.2 -0.6 L0.6 -0.8 L0.6 0.4 M-0.2 0.6 A0.25 0.25 0 1 1 -0.21 0.59 M0.6 0.4 A0.25 0.25 0 1 1 0.59 0.39" stroke="#FF4F7B" strokeWidth={0.14} fill="none" />}
      </Svg>
    </View>
  );
}

type Props = {
  /** Lays the screen out by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function DotMatrixWidgets({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const clock = useSharedValue(0);
  const tilt = useSharedValue({ x: 0, y: 0 });
  const held = useSharedValue({ x: 0, y: 0 });
  const touching = useSharedValue(false);
  const released = useSharedValue(-10);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get() || now - released.get() < HOLD) {
      // The loop waits.
      origin.set(origin.get() + dt);
      if (!touching.get()) {
        const k = Math.exp(-6 * (now - released.get()));
        tilt.set({ x: held.get().x * k, y: held.get().y * k });
      }
    }
    if (autoPlay) clock.set((((now - origin.get()) % LOOP) + LOOP) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const boardStyle = useAnimatedStyle(() => ({ transform: [{ perspective: 900 }, { rotateX: `${tilt.get().x}deg` }, { rotateY: `${tilt.get().y}deg` }] }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      touching.set(true);
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => {
      const v = { x: Math.max(-14, Math.min(14, -e.translationY * 0.08)), y: Math.max(-14, Math.min(14, e.translationX * 0.08)) };
      tilt.set(v);
      held.set(v);
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  const top = insets.top + 64;
  const half = (width - 2 * M - G) / 2;
  const full = width - 2 * M;
  const icon = (half - G) / 2;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <LinearGradient colors={['#2C7FB8', '#1B5D8E', '#123F63']} style={StyleSheet.absoluteFill} />
      <Svg style={StyleSheet.absoluteFill} pointerEvents="none">
        {Array.from({ length: 30 }, (_, i) => (
          <Line key={`h${i}`} x1={0} y1={i * 30} x2={width} y2={i * 30} stroke="rgba(255,255,255,0.12)" strokeWidth={1} />
        ))}
        {Array.from({ length: 15 }, (_, i) => (
          <Line key={`v${i}`} x1={i * 30} y1={0} x2={i * 30} y2={900} stroke="rgba(255,255,255,0.12)" strokeWidth={1} />
        ))}
      </Svg>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <Text style={styles.brand}>Tiles</Text>
            <Text style={styles.meta}>Edit home screen</Text>
          </View>
          <Animated.View style={[StyleSheet.absoluteFill, boardStyle]} pointerEvents="none">
            <Pop n={6} clock={clock} style={{ left: M, top, width: full, height: 136 }}>
              <MapStrip />
            </Pop>
            <Pop n={1} clock={clock} style={{ left: M, top: top + 136 + G, width: half, height: half }}>
              <Weather />
            </Pop>
            {[0, 1, 2, 3].map((k) => (
              <Pop key={k} n={2 + k} clock={clock} style={{ left: M + half + G + (k % 2) * (icon + G), top: top + 136 + G + Math.floor(k / 2) * (icon + G), width: icon, height: icon }}>
                <Icon kind={k} />
              </Pop>
            ))}
            <Pop n={0} clock={clock} style={{ left: M, top: top + 136 + G + half + G, width: full, height: 230 }}>
              <Calendar clock={clock} />
            </Pop>
          </Animated.View>
          <View style={[styles.foot, { paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
            <Text style={styles.footTitle}>Your stack is ready</Text>
            <Text style={styles.footLine}>Hold any tile to resize it, or drag it somewhere new.</Text>
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#1B5D8E' },
  abs: { position: 'absolute' },
  head: { position: 'absolute', left: M, right: M, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '900', color: '#FFFFFF' },
  meta: { fontSize: 14, fontWeight: '600', color: 'rgba(255,255,255,0.75)' },
  map: { flex: 1, borderRadius: 26, overflow: 'hidden', backgroundColor: '#EEF0EC' },
  mapChip: { position: 'absolute', left: 12, bottom: 10, paddingHorizontal: 10, height: 26, borderRadius: 13, backgroundColor: '#FFFFFF', justifyContent: 'center' },
  mapChipText: { fontSize: 12, fontWeight: '700', color: '#1C1C1E' },
  weather: { flex: 1, borderRadius: 26, padding: 14 },
  wCity: { fontSize: 13, fontWeight: '700', color: '#FFFFFF' },
  wTemp: { fontSize: 44, fontWeight: '300', color: '#FFFFFF', marginTop: 2 },
  wSun: { width: 16, height: 16, borderRadius: 8, backgroundColor: '#FFD34D', marginTop: 4 },
  wLine: { marginTop: 6, fontSize: 13, fontWeight: '700', color: '#FFFFFF' },
  wRange: { fontSize: 12, fontWeight: '600', color: 'rgba(255,255,255,0.8)' },
  icon: { flex: 1, borderRadius: 18, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  iconDark: { backgroundColor: '#1C1C1E' },
  calendar: { flex: 1, borderRadius: 30, backgroundColor: '#0B0B0D', paddingHorizontal: 20 },
  calHead: { height: 50, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  calMonth: { fontSize: 18, fontWeight: '300', color: '#E8E8EC', letterSpacing: 1 },
  calYear: { fontSize: 22, fontWeight: '300', color: '#E8E8EC' },
  calRow: { position: 'absolute', left: 20, right: 20, height: 30 },
  day: { position: 'absolute', top: 0, width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  dayEmpty: { backgroundColor: '#1E1E22' },
  dayText: { fontSize: 12, fontWeight: '600', color: '#C9C9CF' },
  dayHot: { color: '#0B0B0D', fontWeight: '800' },
  foot: { position: 'absolute', left: M, right: M, bottom: 0 },
  footTitle: { fontSize: 26, fontWeight: '900', color: '#FFFFFF', letterSpacing: -0.6 },
  footLine: { marginTop: 6, fontSize: 15, lineHeight: 21, color: 'rgba(255,255,255,0.8)' },
});

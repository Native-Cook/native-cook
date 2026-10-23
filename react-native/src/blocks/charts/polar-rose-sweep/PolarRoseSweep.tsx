import {
  Canvas,
  PaintStyle,
  Picture,
  RadialGradient,
  Rect,
  Skia,
  StrokeCap,
  createPicture,
  matchFont,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

export type RoseRound = {
  /** The toggle's label. */
  name: string;
  /** Per idea, in percent of the people asked; what is left over did not answer. */
  likely: number[];
  unsure: number[];
  unlikely: number[];
};

type Props = {
  title?: string;
  caption?: string;
  /** One wedge per idea, clockwise from the top. */
  ideas?: string[];
  /** Two or more survey rounds over the same ideas; the toggle switches between them. */
  rounds?: RoseRound[];
  legend?: [string, string, string];
  /** Tours the wedges and sweeps to the next round and back every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number) => void;
  onRoundChange?: (index: number) => void;
};

const IDEAS = [
  'Layout refiner',
  'Sketch converter',
  'Prompt builder',
  'Search assistant',
  'Anomaly detector',
  'Group finder',
  'Price predictor',
  'Diagram seeker',
  'Graph copilot',
  'Link analyst',
];

const ROUNDS: RoseRound[] = [
  {
    name: 'March',
    likely: [77, 71, 67, 56, 54, 46, 46, 46, 46, 31],
    unsure: [13, 14, 10, 16, 12, 18, 14, 20, 12, 22],
    unlikely: [8, 9, 15, 18, 22, 20, 26, 18, 30, 28],
  },
  {
    name: 'June',
    likely: [82, 64, 72, 49, 61, 58, 40, 52, 38, 44],
    unsure: [10, 17, 11, 20, 13, 14, 18, 16, 15, 19],
    unlikely: [6, 12, 12, 22, 18, 20, 28, 22, 30, 26],
  },
];

const CENTRE = '#3a1a57';
const EDGE = '#0e0516';
const TITLE = '#f1d9ff';
const SOFT = '#a57bc0';
const LABEL = '#b58ad1';
const GRID = '#5c3380';
// The inner sector's colour follows its value: dark plum for the least likely, lavender for the most.
const LIKELY_STOPS: [number, string][] = [
  [30, '#6f4687'],
  [45, '#9568ab'],
  [65, '#cf97e2'],
  [80, '#eeb3f7'],
];
const UNSURE = '#6b2bbf';
const UNLIKELY = '#2b1345';
const NUMBER = '#3b1c57';
const NUMBER_SELECTED = '#111111';
const UNSURE_NUMBER = '#e3c4f7';
const LEGEND = ['#eeb3f7', '#7b2fd2', '#2b1345'];
const PILL = 'rgba(26,10,40,0.8)';
const THUMB = 'rgba(241,217,255,0.16)';
const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

// The chart. Radius is linear in percent (not area): 100 % is the outer ring.
const MAX_RADIUS = 170;
const LABEL_ROOM = 32;
const LABEL_GAP = 15;
const RINGS = [0.2, 0.4, 0.6, 0.8, 1];
const GRID_WIDTH = 0.75;
const GRID_ALPHA = 0.55;
// The selected wedge pushes out and brightens; the others dim a little.
const PUSH = 10;
const BRIGHTEN = 0.14;
const DIM = 0.78;
const NUMBER_SIZE = 15;
const NUMBER_SELECTED_SIZE = 30;
const NUMBER_AT = 0.66;
const NUMBER_OUT = 0.1;
const LABEL_SIZE = 12;
const TAP_ROOM = 36;

// Seconds. A spring step (damping ratio and angular frequency) for pushes and re-flows.
const ZETA = 0.62;
const OMEGA = 12;
const SETTLED = 0.9;
// A radar line sweeps clockwise; each wedge re-flows to the new round as it passes.
const SWEEP_FOR = 1.5;
const TRAIL = 32;
const TRAIL_STEPS = 12;
const TRAIL_ALPHA = 0.16;
const REDUCED_FADE = 0.18;
const TOGGLE_FOR = 240;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 10;
const TOUR = [
  [0.8, 1],
  [1.6, 2],
  [2.4, 3],
  [5.6, 0],
];
const NEXT_ROUND_AT = 3.4;
const BACK_AT = 6.4;
const USER_HOLD = 3;
const SETTLE = 2;

function clamp(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

/** A damped spring's step response at `t` seconds: 0 before, about 1 once settled. */
function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= SETTLED * 2) return 1;
  const decay = ZETA * OMEGA;
  const damped = OMEGA * Math.sqrt(1 - ZETA * ZETA);
  return 1 - Math.exp(-decay * t) * (Math.cos(damped * t) + (decay / damped) * Math.sin(damped * t));
}

function rgb(color: string) {
  return [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
}

/** A colour as a 0xAARRGGBB number, mixed toward white by `light`, with `alpha`. */
function argb(c: number[], lighten: number, opacity: number) {
  'worklet';
  // A spring overshoots past 1: colours stop at the ends.
  const light = clamp(lighten);
  const alpha = clamp(opacity);
  const r = Math.round(c[0] + (255 - c[0]) * light);
  const g = Math.round(c[1] + (255 - c[1]) * light);
  const b = Math.round(c[2] + (255 - c[2]) * light);
  return ((Math.round(alpha * 255) << 24) | (r << 16) | (g << 8) | b) >>> 0;
}

/** An annular sector between two radii and two angles (degrees, clockwise from +x). */
function sector(cx: number, cy: number, r0: number, r1: number, a0: number, sweep: number) {
  'worklet';
  const path = Skia.PathBuilder.Make();
  if (r1 <= r0) return path.build();
  path.arcToOval({ x: cx - r1, y: cy - r1, width: r1 * 2, height: r1 * 2 }, a0, sweep, true);
  if (r0 < 0.5) path.lineTo(cx, cy);
  else path.arcToOval({ x: cx - r0, y: cy - r0, width: r0 * 2, height: r0 * 2 }, a0 + sweep, -sweep, false);
  return path.close().build();
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A survey as a polar-area chart: one wedge per idea, each three stacked sectors (likely, hard to
 * say, not likely) whose radii read in percent. A touch selects the wedge under it, which pushes out
 * on a spring; the round toggle sends a radar line round the chart, each wedge re-flowing to the
 * new answers as the line passes while its number rolls.
 */
export function PolarRoseSweep({
  title = 'How likely are you to use it?',
  caption,
  ideas = IDEAS,
  rounds = ROUNDS,
  legend = ['Likely', 'Hard to say', 'Not likely'],
  autoPlay = false,
  onSelect,
  onRoundChange,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [area, setArea] = useState({ y: 0, width: 0, height: 0 });
  const count = ideas.length;
  const step = 360 / count;
  const radius = Math.max(60, Math.min(MAX_RADIUS, area.width / 2 - LABEL_ROOM, area.height / 2 - LABEL_ROOM));
  const cx = area.width / 2;
  const cy = area.height / 2;

  const fonts = useMemo(
    () => ({
      number: matchFont({ fontFamily: FONT, fontSize: NUMBER_SIZE, fontWeight: '700' }),
      unsure: matchFont({ fontFamily: FONT, fontSize: 14, fontWeight: '600' }),
      label: matchFont({ fontFamily: FONT, fontSize: LABEL_SIZE, fontWeight: '400' }),
    }),
    [],
  );
  const labelWidths = useMemo(() => ideas.map((idea) => fonts.label.measureText(idea).width), [ideas, fonts]);
  // Cumulative percent per round and wedge: the outer edge of each of the three sectors.
  const table = useMemo(
    () =>
      rounds.map((r) =>
        ideas.map((_, i) => {
          const a = r.likely[i] ?? 0;
          const b = a + (r.unsure[i] ?? 0);
          return [a, b, b + (r.unlikely[i] ?? 0)];
        }),
      ),
    [rounds, ideas],
  );
  const colors = useMemo(
    () => ({
      stops: LIKELY_STOPS.map(([at, color]) => [at, ...rgb(color)]),
      unsure: rgb(UNSURE),
      unlikely: rgb(UNLIKELY),
      grid: rgb(GRID),
      label: rgb(LABEL),
      number: rgb(NUMBER),
      selected: rgb(NUMBER_SELECTED),
      unsureNumber: rgb(UNSURE_NUMBER),
    }),
    [],
  );

  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const drawTime = useSharedValue(0);
  const busyUntil = useSharedValue(0);
  const selected = useSharedValue(0);
  const previous = useSharedValue(-1);
  const selectedAt = useSharedValue(-100);
  const round = useSharedValue(0);
  const fromRound = useSharedValue(0);
  const sweptAt = useSharedValue(-100);
  const toggle = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(-USER_HOLD);
  const previousLocal = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const [shownRound, setShownRound] = useState(0);
  const [toggleWidths, setToggleWidths] = useState<number[]>([]);

  const select = (index: number, byHand: boolean, t: number) => {
    'worklet';
    if (index === selected.get() || index < 0 || index >= count) return;
    previous.set(selected.get());
    selected.set(index);
    selectedAt.set(t);
    busyUntil.set(Math.max(busyUntil.get(), t + SETTLED * 2));
    drawTime.set(t);
    if (byHand) {
      scheduleOnRN(tick);
      if (onSelect) scheduleOnRN(onSelect, index);
    }
  };

  const switchRound = (index: number, byHand: boolean, t: number) => {
    'worklet';
    if (index === round.get()) return;
    fromRound.set(round.get());
    round.set(index);
    sweptAt.set(t);
    toggle.set(reduced ? index : withTiming(index, { duration: TOGGLE_FOR, easing: Easing.out(Easing.cubic) }));
    busyUntil.set(Math.max(busyUntil.get(), t + SWEEP_FOR + SETTLED * 2));
    drawTime.set(t);
    scheduleOnRN(setShownRound, index);
    if (byHand) {
      scheduleOnRN(tick);
      if (onRoundChange) scheduleOnRN(onRoundChange, index);
    }
  };

  const touch = () => {
    'worklet';
    loopStart.set(-1);
    lastTouch.set(clock.get());
  };

  useFrameCallback((frame) => {
    if (origin.get() < 0) origin.set(frame.timestamp);
    const t = (frame.timestamp - origin.get()) / 1000;
    clock.set(t);
    if (autoPlay && !dragging.get()) {
      let start = loopStart.get();
      if (start < 0 && t - lastTouch.get() >= USER_HOLD) {
        const atRest = round.get() === 0 && selected.get() === 0;
        switchRound(0, false, t);
        select(0, false, t);
        start = t + (atRest ? 0 : SETTLE);
        loopStart.set(start);
        previousLocal.set(-1);
      }
      if (start >= 0 && t >= start) {
        const local = (t - start) % LOOP;
        const before = previousLocal.get();
        // A step runs when the loop's time passes it, the wrap from the end to the start included.
        const passed = (at: number) => (before <= local ? before < at && at <= local : before < at || at <= local);
        for (let i = 0; i < TOUR.length; i++) if (passed(TOUR[i][0])) select(TOUR[i][1] % count, false, t);
        if (passed(NEXT_ROUND_AT)) switchRound(1 % rounds.length, false, t);
        if (passed(BACK_AT)) switchRound(0, false, t);
        previousLocal.set(local);
      }
    }
    if (t <= busyUntil.get()) drawTime.set(t);
  });

  const picture = useDerivedValue(() => {
    const now = drawTime.get();
    const current = selected.get();
    const before = previous.get();
    const pushed = reduced ? clamp((now - selectedAt.get()) / REDUCED_FADE) : spring(now - selectedAt.get());
    const target = table[round.get()];
    const source = table[fromRound.get()];
    const since = now - sweptAt.get();
    return createPicture((canvas) => {
      if (area.width <= 0) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const unit = radius / 100;

      // Wedges, the selected one last so its push is drawn over its neighbours.
      const drawWedge = (i: number) => {
        const flow = reduced ? clamp((since - SWEEP_FOR / 2) / REDUCED_FADE) : spring(since - (i / count) * SWEEP_FOR);
        const value = [0, 0, 0];
        for (let k = 0; k < 3; k++) value[k] = source[i][k] + (target[i][k] - source[i][k]) * flow;
        const pop = i === current ? pushed : i === before ? 1 - pushed : 0;
        const a0 = -90 + i * step;
        const middle = ((a0 + step / 2) * Math.PI) / 180;
        const push = reduced ? 0 : PUSH * pop;
        const x = cx + Math.cos(middle) * push;
        const y = cy + Math.sin(middle) * push;
        // The push may overshoot; colours and sizes follow the settled amount.
        const tone = clamp(pop);
        const light = BRIGHTEN * tone;
        const alpha = DIM + (1 - DIM) * tone;

        // The inner sector's colour from its value along the stops.
        const stops = colors.stops;
        let s = 0;
        while (s < stops.length - 2 && value[0] > stops[s + 1][0]) s++;
        const u = clamp((value[0] - stops[s][0]) / (stops[s + 1][0] - stops[s][0]));
        const inner = [0, 0, 0];
        for (let k = 0; k < 3; k++) inner[k] = stops[s][k + 1] + (stops[s + 1][k + 1] - stops[s][k + 1]) * u;

        paint.setColor(Skia.Color(argb(colors.unlikely, light, alpha)));
        canvas.drawPath(sector(x, y, value[1] * unit, value[2] * unit, a0, step), paint);
        paint.setColor(Skia.Color(argb(colors.unsure, light, alpha)));
        canvas.drawPath(sector(x, y, value[0] * unit, value[1] * unit, a0, step), paint);
        paint.setColor(Skia.Color(argb(inner, light, alpha)));
        canvas.drawPath(sector(x, y, 0, value[0] * unit, a0, step), paint);

        // The likely share inside the inner sector, larger on the selected wedge.
        const text = `${Math.round(value[0])}%`;
        const scale = 1 + (NUMBER_SELECTED_SIZE / NUMBER_SIZE - 1) * tone;
        const width = fonts.number.measureText(text).width;
        // The larger number moves out to where the wedge is wide enough for it.
        const at = value[0] * unit * (NUMBER_AT + NUMBER_OUT * tone);
        const ink = [0, 0, 0];
        for (let k = 0; k < 3; k++) ink[k] = colors.number[k] + (colors.selected[k] - colors.number[k]) * tone;
        paint.setColor(Skia.Color(argb(ink, 0, 0.82 + 0.18 * tone)));
        canvas.save();
        canvas.translate(x + Math.cos(middle) * at, y + Math.sin(middle) * at);
        canvas.scale(scale, scale);
        canvas.drawText(text, -width / 2, NUMBER_SIZE * 0.36, paint, fonts.number);
        canvas.restore();

        // On the selected wedge, the "hard to say" share sits in its band.
        if (tone > 0.02 && value[1] - value[0] >= 6) {
          const band = `${Math.round(value[1] - value[0])}%`;
          const bandAt = ((value[0] + value[1]) / 2) * unit;
          const bandWidth = fonts.unsure.measureText(band).width;
          paint.setColor(Skia.Color(argb(colors.unsureNumber, 0, tone)));
          canvas.drawText(band, x + Math.cos(middle) * bandAt - bandWidth / 2, y + Math.sin(middle) * bandAt + 5, paint, fonts.unsure);
        }
      };
      for (let i = 0; i < count; i++) if (i !== current && i !== before) drawWedge(i);
      if (before >= 0 && before !== current) drawWedge(before);
      drawWedge(current);

      // Reference rings and spokes over the wedges, faint.
      const grid = Skia.Paint();
      grid.setAntiAlias(true);
      grid.setStyle(PaintStyle.Stroke);
      grid.setStrokeWidth(GRID_WIDTH);
      grid.setColor(Skia.Color(argb(colors.grid, 0, GRID_ALPHA)));
      for (let k = 0; k < RINGS.length; k++) canvas.drawCircle(cx, cy, RINGS[k] * radius, grid);
      for (let i = 0; i < count; i++) {
        const a = ((-90 + i * step) * Math.PI) / 180;
        canvas.drawLine(cx, cy, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, grid);
      }

      // The radar line and its fading trail while a new round sweeps in.
      if (!reduced && since >= 0 && since <= SWEEP_FOR) {
        const p = since / SWEEP_FOR;
        const fade = clamp(p / 0.08) * clamp((1 - p) / 0.08);
        const head = -90 + 360 * p;
        const slice = TRAIL / TRAIL_STEPS;
        for (let k = 0; k < TRAIL_STEPS; k++) {
          paint.setColor(Skia.Color(argb([255, 255, 255], 0, TRAIL_ALPHA * fade * (1 - k / TRAIL_STEPS))));
          canvas.drawPath(sector(cx, cy, 0, radius, head - (k + 1) * slice, slice), paint);
        }
        const line = Skia.Paint();
        line.setAntiAlias(true);
        line.setStyle(PaintStyle.Stroke);
        line.setStrokeWidth(1.5);
        line.setStrokeCap(StrokeCap.Round);
        line.setColor(Skia.Color(argb([255, 255, 255], 0, 0.9 * fade)));
        const a = (head * Math.PI) / 180;
        canvas.drawLine(cx, cy, cx + Math.cos(a) * (radius + 6), cy + Math.sin(a) * (radius + 6), line);
      }

      // Idea names along the circle, turned so that none reads upside down.
      for (let i = 0; i < count; i++) {
        const pop = i === current ? pushed : i === before ? 1 - pushed : 0;
        const middle = -90 + (i + 0.5) * step;
        const a = (middle * Math.PI) / 180;
        const r = radius + LABEL_GAP + (reduced ? 0 : PUSH * pop);
        const lower = Math.sin(a) > 0.01;
        paint.setColor(Skia.Color(argb(colors.label, pop, 1)));
        canvas.save();
        canvas.translate(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
        canvas.rotate(lower ? middle - 90 : middle + 90, 0, 0);
        canvas.drawText(ideas[i], -labelWidths[i] / 2, LABEL_SIZE * 0.36, paint, fonts.label);
        canvas.restore();
      }
    });
  });

  /** The wedge under a point of the chart, or -1 away from it. */
  const wedgeAt = (x: number, y: number) => {
    'worklet';
    const dx = x - cx;
    const dy = y - cy;
    if (Math.hypot(dx, dy) > radius + TAP_ROOM) return -1;
    const degrees = (Math.atan2(dy, dx) * 180) / Math.PI + 90;
    return Math.floor((((degrees % 360) + 360) % 360) / step) % count;
  };

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      dragging.set(true);
      touch();
      select(wedgeAt(e.x, e.y), true, clock.get());
    })
    .onUpdate((e) => {
      touch();
      select(wedgeAt(e.x, e.y), true, clock.get());
    })
    .onFinalize(() => {
      dragging.set(false);
      touch();
    });

  const pickRound = (index: number) => {
    scheduleOnUI(() => {
      'worklet';
      touch();
      switchRound(index, true, clock.get());
    });
  };

  const thumbStyle = useAnimatedStyle(() => {
    const v = toggle.get();
    const i = Math.min(Math.floor(v), toggleWidths.length - 1);
    const j = Math.min(i + 1, toggleWidths.length - 1);
    const u = v - Math.floor(v);
    let left = 0;
    for (let k = 0; k < i; k++) left += toggleWidths[k] ?? 0;
    const width = (toggleWidths[i] ?? 0) + ((toggleWidths[j] ?? 0) - (toggleWidths[i] ?? 0)) * u;
    return { width, transform: [{ translateX: left + (toggleWidths[i] ?? 0) * u }], opacity: toggleWidths.length ? 1 : 0 };
  });

  const onArea = (event: LayoutChangeEvent) => {
    const { y, width, height } = event.nativeEvent.layout;
    setArea({ y, width, height });
  };

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={area.width || 1} height={4000}>
          <RadialGradient
            c={vec(cx, area.y + cy)}
            r={Math.max(area.width, area.height) * 0.85 || 1}
            colors={[CENTRE, EDGE]}
          />
        </Rect>
      </Canvas>

      <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
        <Text style={styles.title} lineBreakStrategyIOS="standard">
          {title}
        </Text>
        <Text style={styles.caption}>{caption ?? `Survey, ${count} ideas`}</Text>
        <View style={styles.toggle} accessibilityRole="tablist">
          <Animated.View style={[styles.thumb, thumbStyle]} />
          {rounds.map((r, i) => (
            <Pressable
              key={r.name}
              onPress={() => pickRound(i)}
              onLayout={(e) => {
                const width = e.nativeEvent.layout.width;
                setToggleWidths((list) => {
                  const next = list.slice();
                  next[i] = width;
                  return next;
                });
              }}
              style={styles.toggleItem}
              accessibilityRole="tab"
              accessibilityState={{ selected: i === shownRound }}
            >
              <Animated.Text
                style={[
                  styles.toggleLabel,
                  { color: i === shownRound ? TITLE : SOFT, transitionProperty: 'color', transitionDuration: TOGGLE_FOR },
                ]}
              >
                {r.name}
              </Animated.Text>
            </Pressable>
          ))}
        </View>
      </View>

      <GestureDetector gesture={gesture}>
        <View
          style={styles.chart}
          onLayout={onArea}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Survey chart"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => {
            const direction = e.nativeEvent.actionName === 'increment' ? 1 : -1;
            scheduleOnUI(() => {
              'worklet';
              touch();
              select((selected.get() + direction + count) % count, true, clock.get());
            });
          }}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 16) + 8 }]}>
        <View style={styles.legend}>
          {legend.map((name, i) => (
            <View key={name} style={styles.legendItem}>
              <View style={[styles.dot, { backgroundColor: LEGEND[i] }, i === 2 && styles.dotRing]} />
              <Text style={styles.legendLabel}>{name}</Text>
            </View>
          ))}
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: EDGE,
  },
  header: {
    paddingHorizontal: 24,
  },
  title: {
    color: TITLE,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '600',
  },
  caption: {
    marginTop: 4,
    color: SOFT,
    fontSize: 13,
    lineHeight: 18,
  },
  toggle: {
    marginTop: 16,
    alignSelf: 'flex-start',
    flexDirection: 'row',
    padding: 3,
    borderRadius: 18,
    backgroundColor: PILL,
  },
  thumb: {
    position: 'absolute',
    left: 3,
    top: 3,
    bottom: 3,
    borderRadius: 15,
    backgroundColor: THUMB,
  },
  toggleItem: {
    height: 30,
    paddingHorizontal: 16,
    justifyContent: 'center',
  },
  toggleLabel: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
  },
  chart: {
    flex: 1,
  },
  footer: {
    alignItems: 'center',
  },
  legend: {
    height: 36,
    paddingHorizontal: 16,
    borderRadius: 18,
    backgroundColor: PILL,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  dotRing: {
    borderWidth: 1,
    borderColor: '#5c3380',
  },
  legendLabel: {
    color: TITLE,
    fontSize: 12,
    lineHeight: 16,
  },
});

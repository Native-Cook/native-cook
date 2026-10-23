import { Canvas, ClipOp, Picture, Skia, createPicture, useClock, useFont } from '@shopify/react-native-skia';
import type { SkCanvas, SkFont, SkPaint, SkPath } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A workout interval timer on near-black: a huge "01:02" in a heavy condensed face counts down.
// Each digit that changes rolls in its own window: the old digit slides up and out of the window
// while the new one rises from below into place (1.12 windows of travel), stretched up to 10 %
// while it moves (a hint of motion blur), the old one fading to 0.5 and the new one in from 0.5;
// when several digits change they go from the right, 0.05 s apart. The colon dips on every tick,
// and a lime bar under the time drains with it. Going up (a drag, or the restart) rolls the other
// way: old digits drop out of the bottom, new ones come from above. A port of the SwiftUI block,
// number for number; the digits are a Skia picture made on the UI thread every frame.
//
// autoPlay timeline (LOOP = 9 s), in seconds shown:
//   0.0-0.6   rest at 01:02
//   0.6, 1.6, 2.6, 3.6, 4.6, 5.6   ticks: 01:01, 01:00, 00:59 (three digits roll), 00:58, 00:57,
//             00:56; each roll 0.42 s, ease-out cubic; the colon dips to 0.45 over 0.5 s (a sine bump)
//   5.6-7.4   holds at 00:56
//   7.4-8.3   restart: back to 01:02 in one roll the other way (0.7 s, ease-in-out cubic; three
//             digits, 0.05 s apart)
//   8.3-9.0   rest at 01:02
// The lime dot beside the round breathes (0.35-1, a cosine every 1.5 s, full at 0), so the
// screen is never quite still.
//
// Touch: dragging up adds a second per 24 pt, dragging down takes one away (0:00 to 9:59); every
// step rolls the digits that change (0.3 s, ease-out cubic) with a selection tick. 2 s after the
// finger lifts the time rolls back to 01:02 like the restart and the loop starts over.

/** Seconds. */
export const LOOP = 9;
const REST = 62;
/** The value the bar is full at. */
const TOTAL = 90;
const TICK_DURATION = 0.42;
const STEP_DURATION = 0.3;
const RESTART_DURATION = 0.7;
const STAGGER = 0.05;
const COLON_DIP = 0.55;
const COLON_DURATION = 0.5;
/** A roll moves each digit this many windows; the digits stretch by up to STRETCH mid-roll. */
const TRAVEL = 1.12;
const STRETCH = 0.1;
/** The live dot breathes once every PULSE seconds (six times a loop). */
const PULSE = 1.5;
/** Points of drag per second. */
const DRAG_STEP = 24;
const USER_HOLD = 2;
const SIZE_LIMIT = 180;

const C = {
  background: '#0B0B0C',
  ink: '#F5F5F2',
  lime: '#D4FF3F',
};

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** `inOut`: ease-in-out cubic, else ease-out cubic. */
function ease(t: number, inOut: boolean) {
  'worklet';
  if (!inOut) return 1 - Math.pow(1 - t, 3);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** A change of the shown value, starting at `time` (seconds into the loop, or a clock time in ms for touch). */
type Change = { time: number; from: number; to: number; duration: number; inOut: boolean };

/** The autoPlay changes, in order. */
const CHANGES: Change[] = [
  ...Array.from({ length: 6 }, (_, i) => ({ time: 0.6 + i, from: REST - i, to: REST - i - 1, duration: TICK_DURATION, inOut: false })),
  { time: 7.4, from: REST - 6, to: REST, duration: RESTART_DURATION, inOut: true },
];

/** The four digits of `value` seconds as m:ss with a leading zero: [m tens, m ones, s tens, s ones]. */
function digitsOf(value: number) {
  'worklet';
  const m = Math.floor(value / 60);
  const s = value % 60;
  return [Math.floor(m / 10), m % 10, Math.floor(s / 10), s % 10];
}

/** One digit window: rolling from `from` to `to`, `progress` 0-1, `up` when the value grows. */
type Cell = { from: number; to: number; progress: number; up: boolean };
function still(d: number): Cell {
  'worklet';
  return { from: d, to: d, progress: 1, up: false };
}

/**
 * The window `cell` (0-3) under a change that started `elapsed` seconds ago, or null when the
 * change leaves that digit alone. Changed digits start from the right, STAGGER apart.
 */
function cellOf(cell: number, change: Change, elapsed: number): Cell | null {
  'worklet';
  const a = digitsOf(change.from);
  const b = digitsOf(change.to);
  if (a[cell] === b[cell]) return null;
  let rank = 0;
  for (let i = cell + 1; i < 4; i++) if (a[i] !== b[i]) rank++;
  const p = clamp01((elapsed - rank * STAGGER) / change.duration);
  return { from: a[cell], to: b[cell], progress: ease(p, change.inOut), up: change.to > change.from };
}

/** The four windows `t` seconds into the loop. */
function cellsAt(t: number): Cell[] {
  'worklet';
  const cells = digitsOf(REST).map((d) => still(d));
  for (let k = 0; k < CHANGES.length; k++) {
    const change = CHANGES[k];
    if (change.time > t) continue;
    for (let i = 0; i < 4; i++) {
      const c = cellOf(i, change, t - change.time);
      if (c) cells[i] = c;
    }
  }
  return cells;
}

/** The colon's opacity `t` seconds into the loop: it dips on every tick. */
function colonAt(t: number) {
  'worklet';
  let dip = 0;
  for (let k = 0; k < CHANGES.length; k++) {
    const change = CHANGES[k];
    if (change.inOut) continue;
    const u = (t - change.time) / COLON_DURATION;
    if (u > 0 && u < 1) dip = Math.max(dip, Math.sin(Math.PI * u));
  }
  return 1 - COLON_DIP * dip;
}

/** The bar's value `t` seconds into the loop: the shown value, moving with the ones digit's roll. */
function valueAt(t: number) {
  'worklet';
  let v = REST;
  for (let k = 0; k < CHANGES.length; k++) {
    const change = CHANGES[k];
    if (change.time > t) continue;
    const p = ease(clamp01((t - change.time) / change.duration), change.inOut);
    v = change.from + (change.to - change.from) * p;
  }
  return v;
}

type DigitType = {
  digits: SkPath[];
  advances: number[];
  colon: SkPath;
  colonAdvance: number;
  /** Every digit window is this wide (the widest digit's advance). */
  cell: number;
  /** The window, from the baseline: its top (negative) and its height. */
  top: number;
  height: number;
  width: number;
};

/** A digit centred in its window, stretched vertically by up to 10 % mid-roll. */
function drawDigit(canvas: SkCanvas, paint: SkPaint, type: DigitType, digit: number, x: number, baseline: number, p: number, opacity: number) {
  'worklet';
  const s = 1 + STRETCH * Math.sin(Math.PI * p);
  const middle = baseline + type.top + type.height / 2;
  canvas.save();
  canvas.translate(x + (type.cell - type.advances[digit]) / 2, middle);
  canvas.scale(1, s);
  canvas.translate(0, baseline - middle);
  paint.setAlphaf(opacity);
  canvas.drawPath(type.digits[digit], paint);
  canvas.restore();
}

type Props = {
  round?: string;
  session?: string;
  title?: string;
  next?: string;
  nextTime?: string;
  pause?: string;
  skip?: string;
  hint?: string;
  /** Counts down and restarts by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

export function RollingCountdownDigits({
  round = 'Round 3 of 5',
  session = 'Core · 20 min',
  title = 'PLANK HOLD',
  next = 'Next: Side plank',
  nextTime = '0:45',
  pause = 'Pause',
  skip = 'Skip',
  hint = 'Drag the time up or down to change it',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const probe = useFont(require('./assets/Anton-Regular.ttf'), 100);

  // The size at which "00:00" spans the screen less 24 pt a side (at most 180 pt).
  const type = useMemo<DigitType | null>(() => {
    if (!probe) return null;
    // Advances from the glyph widths: `getTextWidth` comes back rounded.
    const advance = (f: SkFont, s: string) => f.getGlyphWidths(f.getGlyphIDs(s))[0] ?? 0;
    const probeCell = Math.max(...Array.from({ length: 10 }, (_, i) => advance(probe, `${i}`)));
    const probeWidth = 4 * probeCell + advance(probe, ':');
    const size = Math.min(SIZE_LIMIT, Math.floor((100 * (width - 48)) / probeWidth));
    const font = Skia.Font(probe.getTypeface() ?? undefined, size);
    const path = (s: string) => Skia.Path.MakeFromText(s, 0, 0, font) ?? Skia.Path.Make();
    const digits = Array.from({ length: 10 }, (_, i) => path(`${i}`));
    const advances = Array.from({ length: 10 }, (_, i) => advance(font, `${i}`));
    const colonAdvance = advance(font, ':');
    const cell = Math.max(...advances);
    const ink = digits[0].computeTightBounds();
    const pad = 0.04 * size;
    return { digits, advances, colon: path(':'), colonAdvance, cell, top: ink.y - pad, height: ink.height + 2 * pad, width: 4 * cell + colonAdvance };
  }, [probe, width]);

  const clock = useClock();
  const origin = useSharedValue(0);
  const manual = useSharedValue(false);
  const dragging = useSharedValue(false);
  // The value set by touch, and the last change of each digit window (its time is the clock's, ms).
  const shown = useSharedValue(REST);
  const changes = useSharedValue<(Change | null)[]>([null, null, null, null]);
  const dragFrom = useSharedValue(REST);
  // Changes on every touch, which restarts the loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The loop starts when the screen appears.
  useEffect(() => {
    origin.set(clock.get());
  }, [origin, clock]);

  const loopTime = useDerivedValue(() => Math.max(0, ((clock.get() - origin.get()) / 1000) % LOOP));

  const cells = useDerivedValue<Cell[]>(() => {
    const now = clock.get();
    if (manual.get()) {
      const start = digitsOf(shown.get());
      const list = changes.get();
      const out: Cell[] = [];
      for (let i = 0; i < 4; i++) {
        const last = list[i];
        out.push(last ? (cellOf(i, last, (now - last.time) / 1000) ?? still(start[i])) : still(start[i]));
      }
      return out;
    }
    if (!autoPlay || reduced) return cellsAt(0);
    return cellsAt(loopTime.get());
  });

  const picture = useDerivedValue(() => {
    const list = cells.get();
    const colon = manual.get() || !autoPlay || reduced ? 1 : colonAt(loopTime.get());
    return createPicture((canvas) => {
      if (!type) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(Skia.Color(C.ink));
      const left = (width - type.width) / 2;
      const baseline = -type.top;
      for (let i = 0; i < 4; i++) {
        const cell = list[i];
        const x = left + i * type.cell + (i >= 2 ? type.colonAdvance : 0);
        canvas.save();
        canvas.clipRect(Skia.XYWHRect(x, 0, type.cell, type.height), ClipOp.Intersect, true);
        const p = cell.progress;
        // The old digit leaves upwards (downwards when the value grows); the new one follows a
        // little more than a window behind, so the stretched digits never overlap.
        const travel = TRAVEL * type.height;
        const dir = cell.up ? 1 : -1;
        if (p < 1) drawDigit(canvas, paint, type, cell.from, x, baseline + dir * p * travel, p, 1 - 0.5 * p);
        drawDigit(canvas, paint, type, cell.to, x, baseline - dir * (1 - p) * travel, p, 0.5 + 0.5 * p);
        canvas.restore();
      }
      canvas.save();
      canvas.translate(left + 2 * type.cell, baseline);
      paint.setAlphaf(colon);
      canvas.drawPath(type.colon, paint);
      canvas.restore();
    });
  });

  // The live dot: 0.35-1, a cosine that starts full at the loop's start.
  const liveStyle = useAnimatedStyle(() => ({
    opacity: !autoPlay || reduced ? 1 : 0.35 + 0.65 * (0.5 + 0.5 * Math.cos((2 * Math.PI * loopTime.get()) / PULSE)),
  }));

  const barStyle = useAnimatedStyle(() => {
    let v: number;
    if (manual.get()) {
      // The ones digit's roll carries the bar between whole seconds.
      const last = changes.get()[3];
      if (!last) v = shown.get();
      else v = last.from + (last.to - last.from) * ease(clamp01((clock.get() - last.time) / 1000 / last.duration), last.inOut);
    } else if (!autoPlay || reduced) v = REST;
    else v = valueAt(loopTime.get());
    return { width: `${clamp01(v / TOTAL) * 100}%` };
  });

  /** Rolls every digit window that changes from the shown value to `value`. */
  const roll = useCallback(
    (value: number, at: number, duration: number, inOut: boolean) => {
      'worklet';
      const from = shown.get();
      if (value === from) return;
      const change: Change = { time: at, from, to: value, duration, inOut };
      const a = digitsOf(from);
      const b = digitsOf(value);
      const list = changes.get().slice();
      // A window still rolling starts again from the digit it is heading for.
      for (let i = 0; i < 4; i++) if (a[i] !== b[i]) list[i] = change;
      // The ones digit always carries the bar.
      list[3] = change;
      changes.set(list);
      shown.set(value);
    },
    [shown, changes],
  );

  // 2 s after the last touch the time rolls back to 01:02, and the loop starts over.
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useEffect(() => {
    if (touches === 0) return;
    const list = timers.current;
    list.push(
      setTimeout(() => {
        if (dragging.get()) return;
        roll(REST, clock.get(), RESTART_DURATION, true);
        list.push(
          setTimeout(() => {
            if (dragging.get()) return;
            origin.set(clock.get());
            manual.set(false);
          }, (RESTART_DURATION + 3 * STAGGER) * 1000),
        );
      }, USER_HOLD * 1000),
    );
    return () => {
      list.forEach(clearTimeout);
      timers.current = [];
    };
  }, [touches, clock, dragging, origin, manual, roll]);

  const touched = () => setTouches((n) => n + 1);

  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin(() => {
      // Take over from the loop at the value it shows (a roll in flight lands at once).
      const current = cells.get();
      const start = current[0].to * 600 + current[1].to * 60 + current[2].to * 10 + current[3].to;
      manual.set(true);
      dragging.set(true);
      shown.set(start);
      changes.set([null, null, null, null]);
      dragFrom.set(start);
      scheduleOnRN(touched);
      scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      const steps = Math.trunc(-e.translationY / DRAG_STEP);
      const target = Math.min(599, Math.max(0, dragFrom.get() + steps));
      if (target !== shown.get()) {
        roll(target, clock.get(), STEP_DURATION, false);
        scheduleOnRN(tick);
      }
    })
    .onFinalize(() => {
      dragging.set(false);
      scheduleOnRN(touched);
    });

  const typeHeight = type ? type.height : 0;
  const typeWidth = type ? type.width : 0;

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <GestureDetector gesture={pan}>
        <View style={styles.screen}>
          <View style={[styles.top, { paddingTop: insets.top + 14 }]}>
            <Animated.View style={[styles.dot, liveStyle]} />
            <Text style={[styles.topText, styles.round]}>{round}</Text>
            <View style={styles.flex} />
            <Text style={[styles.topText, styles.session]}>{session}</Text>
          </View>

          <View style={styles.flex} />

          <Text style={styles.title}>{title}</Text>

          <View style={{ marginTop: 20, height: typeHeight }} accessible accessibilityLabel="Time left">
            <Canvas pointerEvents="none" style={{ width, height: typeHeight }}>
              <Picture picture={picture} />
            </Canvas>
          </View>

          <View style={[styles.below, { width: typeWidth }]}>
            <View style={styles.track}>
              <Animated.View style={[styles.fill, barStyle]} />
            </View>
            <View style={styles.nextRow}>
              <Text style={styles.next}>{next}</Text>
              <Text style={styles.next}>{nextTime}</Text>
            </View>
          </View>

          <View style={styles.flex} />

          <View style={styles.buttons}>
            <View style={[styles.button, styles.pause]} accessible accessibilityRole="button">
              <Text style={[styles.buttonLabel, { color: C.background }]}>{pause}</Text>
            </View>
            <View style={[styles.button, styles.skip]} accessible accessibilityRole="button">
              <Text style={[styles.buttonLabel, { color: C.ink }]}>{skip}</Text>
            </View>
          </View>

          <Text style={[styles.hint, { paddingBottom: insets.bottom + 12 }]}>{hint}</Text>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.background },
  flex: { flex: 1 },
  top: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 24 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: C.lime },
  topText: { fontSize: 15, fontWeight: '600' },
  round: { marginLeft: 8, color: C.ink },
  session: { color: C.ink, opacity: 0.5 },
  title: { alignSelf: 'center', fontSize: 13, fontWeight: '700', letterSpacing: 2.4, color: C.lime },
  below: { alignSelf: 'center', marginTop: 28, gap: 12 },
  track: { height: 4, borderRadius: 2, backgroundColor: 'rgba(245,245,242,0.14)', overflow: 'hidden' },
  fill: { height: 4, borderRadius: 2, backgroundColor: C.lime },
  nextRow: { flexDirection: 'row', justifyContent: 'space-between' },
  next: { fontSize: 15, fontWeight: '500', color: C.ink, opacity: 0.55, fontVariant: ['tabular-nums'] },
  buttons: { flexDirection: 'row', gap: 12, paddingHorizontal: 24 },
  button: { flex: 1, height: 56, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  pause: { backgroundColor: C.ink },
  skip: { borderWidth: 1.5, borderColor: 'rgba(245,245,242,0.25)' },
  buttonLabel: { fontSize: 17, fontWeight: '600' },
  hint: { alignSelf: 'center', marginTop: 16, fontSize: 13, fontWeight: '500', color: C.ink, opacity: 0.4 },
});

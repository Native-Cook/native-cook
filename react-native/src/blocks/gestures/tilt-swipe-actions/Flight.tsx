/**
 * The people a bill is split with, flying between the row and the toast. When the seams heal, the avatars of the
 * shares that become requests lift off their pieces and fly to the toast, where they stay as its avatars; Undo flies
 * them back into the pieces. Four flyers are mounted from the start (person k is always PEOPLE[k]), so the swap
 * between a piece's avatar and its flyer happens on the UI thread in one frame.
 */
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { cancelAnimation, Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { Monogram } from './Monogram';
import { SHEET, TIMING } from './motion';
import { MONO } from './split-geometry';
import { COLOR, LAYOUT } from './theme';
import { PEOPLE } from './transactions';

export type Point = { x: number; y: number; s: number };

const TOAST_AVATAR = MONO; // 28 pt, the same monogram in the shares and in the toast
const NONE: Point = { x: 0, y: 0, s: 1 };

/** The overlap between the toast's avatars: 4 pt for one or two people, tighter for more. */
export function toastOverlap(requested: number) {
  'worklet';
  return requested <= 2 ? 4 : 10;
}

/** Centre of the toast's k-th requested avatar (k from 1), on the screen. */
export function toastAvatar(k: number, requested: number, toastTop: number): Point {
  'worklet';
  const x0 = LAYOUT.gutter + 12 + TOAST_AVATAR / 2;
  return { x: x0 + (k - 1) * (TOAST_AVATAR - toastOverlap(requested)), y: toastTop + LAYOUT.toast / 2, s: 1 };
}

const SHEET_CURVE = Easing.bezierFn(...SHEET);

function ease(v: number) {
  'worklet';
  return SHEET_CURVE(Math.min(Math.max(v, 0), 1));
}

export type FlightApi = ReturnType<typeof useFlight>;

export function useFlight(toastTop: number, reduceMotion: boolean) {
  const key = useSharedValue('');
  const on = useSharedValue(0); // 1: the requested avatars are with the flyers (in the air or in the toast)
  const n = useSharedValue(0);
  const from = useSharedValue<Point[]>([]);
  const to = useSharedValue<Point[]>([]);
  const t = useSharedValue(0); // ms into the flight
  const shown = useSharedValue(0); // the flyers' opacity (with the toast)

  return useMemo(() => {
    const total = (count: number) => {
      'worklet';
      return TIMING.flight + Math.max(count - 2, 0) * TIMING.flightStagger;
    };

    /** Row → toast. `pieces` are the avatars' centres on the screen right now (index = person). */
    const launch = (rowKey: string, count: number, pieces: Point[]) => {
      'worklet';
      const targets: Point[] = [NONE];
      for (let k = 1; k < count; k++) targets.push(toastAvatar(k, count - 1, toastTop));
      cancelAnimation(t);
      key.set(rowKey);
      n.set(count);
      from.set(pieces);
      to.set(targets);
      on.set(1);
      cancelAnimation(shown);
      if (reduceMotion) {
        t.set(total(count));
        shown.set(0);
        shown.set(withTiming(1, { duration: TIMING.reduced, reduceMotion: ReduceMotion.Never }));
        return;
      }
      shown.set(1);
      t.set(0);
      t.set(withTiming(total(count), { duration: total(count), easing: Easing.linear }));
    };

    /** The toast timed out: the avatars go with it. */
    const fade = () => {
      'worklet';
      shown.set(withTiming(0, { duration: TIMING.toastOut }, (done) => {
        if (done) on.set(0);
      }));
    };

    const reset = () => {
      'worklet';
      cancelAnimation(t);
      cancelAnimation(shown);
      on.set(0);
      shown.set(0);
      key.set('');
    };

    return { key, on, n, from, to, t, shown, launch, fade, reset };
  }, [from, key, n, on, reduceMotion, shown, t, to, toastTop]);
}

/** The four requested people, above everything (each later one over the one before, as in the toast). */
export function Flyers({ flight }: { flight: FlightApi }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {[1, 2, 3, 4].map((k) => <Flyer key={k} k={k} flight={flight} />)}
    </View>
  );
}

function Flyer({ k, flight }: { k: number; flight: FlightApi }) {
  const style = useAnimatedStyle(() => {
    const a = flight.from.get()[k];
    const b = flight.to.get()[k];
    if (flight.on.get() === 0 || k >= flight.n.get() || !a || !b) {
      return { opacity: 0, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }] };
    }
    const p = ease((flight.t.get() - (k - 1) * TIMING.flightStagger) / TIMING.flight);
    // Along the row, then down the icon column (and back the same way): the avatars cross the plum icon circles,
    // never another transaction's text.
    const down = b.y > a.y;
    const cx = down ? b.x : a.x;
    const cy = down ? a.y : b.y;
    const u = 1 - p;
    const x = u * u * a.x + 2 * u * p * cx + p * p * b.x;
    const y = u * u * a.y + 2 * u * p * cy + p * p * b.y;
    const s = a.s + (b.s - a.s) * p;
    return { opacity: flight.shown.get(), transform: [{ translateX: x - TOAST_AVATAR / 2 }, { translateY: y - TOAST_AVATAR / 2 }, { scale: s }] };
  });
  // On the toast the monogram turns to the toast's graphite and gains an ink ring that cuts the overlap.
  const toastSide = useAnimatedStyle(() => {
    const a = flight.from.get()[k];
    const b = flight.to.get()[k];
    if (!a || !b) return { opacity: 0 };
    const p = ease((flight.t.get() - (k - 1) * TIMING.flightStagger) / TIMING.flight);
    const down = b.y > a.y;
    return { opacity: Math.min(Math.max(down ? p : 1 - p, 0), 1) };
  });
  return (
    <Animated.View style={[styles.flyer, style]}>
      <Monogram person={PEOPLE[k]} />
      <Animated.View style={[StyleSheet.absoluteFill, toastSide]}>
        <Monogram person={PEOPLE[k]} fill={COLOR.monoToast} ring={COLOR.ink} />
      </Animated.View>
    </Animated.View>
  );
}

/** A share's monogram and the toast's are the same size. */
export const PIECE_SCALE = MONO / TOAST_AVATAR;

const styles = StyleSheet.create({
  flyer: { position: 'absolute', left: 0, top: 0, width: TOAST_AVATAR, height: TOAST_AVATAR },
});

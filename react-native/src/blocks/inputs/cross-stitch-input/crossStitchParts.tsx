import { StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { type Geo, type RGB, THREADS, mixRGB, needlePoint } from './crossStitchArt';
import type { Layout } from './crossStitchFont';
import { easeInOut, seg } from './crossStitchMotion';

/* Cross Stitch Input — the user's name on the UI thread, and the rolling counter and thread name. */

export const GLIDE = 0.35;
const INK = '#2E2620';
const DIGIT_H = 22;

/** The name being typed by the user, as the UI thread sees it. */
export type UserName = {
  layout: Layout;
  typedAt: number[];
  /** Per glyph: where it was, in cells, relative to where it is now, and since when it glides. */
  fromX: number[];
  fromY: number[];
  movedAt: number;
  thread: number;
  threadFrom: RGB;
  threadAt: number;
};

export function stitchDuration(count: number) {
  'worklet';
  return 0.15 + 0.055 * count;
}

/** Per glyph, how many of its stitches are worked at `now`. */
export function userGlyphPresent(u: UserName, now: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < u.layout.count.length; i += 1) {
    const c = u.layout.count[i];
    out.push(c * Math.min(1, Math.max(0, (now - u.typedAt[i]) / stitchDuration(c))));
  }
  return out;
}

export function userShift(u: UserName, now: number, axis: number): number[] {
  'worklet';
  const k = 1 - easeInOut(seg(now, u.movedAt, u.movedAt + GLIDE));
  const src = axis === 0 ? u.fromX : u.fromY;
  const out: number[] = [];
  for (let i = 0; i < src.length; i += 1) out.push(src[i] * k);
  return out;
}

export function userColour(u: UserName, now: number): RGB {
  'worklet';
  return mixRGB(u.threadFrom, THREADS[u.thread].rgb, easeInOut(seg(now, u.threadAt, u.threadAt + 0.3)));
}

/** Total stitches on the cloth for the user's name. */
export function userWorked(u: UserName, now: number) {
  'worklet';
  const p = userGlyphPresent(u, now);
  let s = 0;
  for (let i = 0; i < p.length; i += 1) s += p[i];
  return s;
}

/**
 * The needle while the user types: [x, y, fromX, fromY, busy]. It follows the newest letter that is
 * still being worked; otherwise it rests.
 */
export function userNeedleTarget(u: UserName, g: Geo, now: number, sx: number[], sy: number[]): number[] {
  'worklet';
  const L = u.layout;
  const present = userGlyphPresent(u, now);
  const h = g.cell * 0.4;
  for (let i = L.count.length - 1; i >= 0; i -= 1) {
    if (L.count[i] > 0 && present[i] < L.count[i]) {
      const k = Math.min(L.count[i] - 1, Math.floor(present[i]));
      const s = L.first[i] + k;
      const x = g.cx + (L.cx[s] + sx[i]) * g.cell;
      const y = g.cy + (L.cy[s] + sy[i]) * g.cell;
      const p = needlePoint(x, y, h, present[i] - k);
      return [p[0], p[1], x - h, y + h, 1];
    }
  }
  if (L.total === 0) return [g.parkX, g.parkY, g.parkX - 30, g.parkY + 6, 0];
  const last = L.total - 1;
  const gi = L.glyphOf[last];
  return [g.parkX, g.parkY, g.cx + (L.cx[last] + sx[gi]) * g.cell - h, g.cy + (L.cy[last] + sy[gi]) * g.cell - h, 0];
}

export function zeros(n: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < n; i += 1) out.push(0);
  return out;
}

export function digitPosition(v: number, k: number) {
  'worklet';
  const p = Math.pow(10, k);
  const d = Math.floor(v / p) % 10;
  if (k === 0) return v - Math.floor(v / 10) * 10;
  const lower = v - Math.floor(v / p) * p;
  return d + Math.max(0, lower - (p - 1));
}

export function Digits({ value, places }: { value: SharedValue<number>; places: number }) {
  const cols = [];
  for (let k = places - 1; k >= 0; k -= 1) cols.push(k);
  return (
    <View style={styles.digits}>
      {cols.map((k) => (
        <DigitColumn key={k} value={value} place={k} />
      ))}
    </View>
  );
}

export function DigitColumn({ value, place }: { value: SharedValue<number>; place: number }) {
  const style = useAnimatedStyle(() => {
    const v = Math.max(0, value.get());
    return {
      transform: [{ translateY: -digitPosition(v, place) * DIGIT_H }],
      opacity: place === 0 ? 1 : Math.min(1, Math.max(0, v - Math.pow(10, place) + 1)),
    };
  });
  return (
    <View style={styles.digitBox}>
      <Animated.Text style={[styles.digitStrip, style]}>{'0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n0'}</Animated.Text>
    </View>
  );
}

export function ThreadName({ position }: { position: SharedValue<number> }) {
  return (
    <View style={styles.nameBox}>
      {THREADS.map((th, i) => (
        <ThreadNameRow key={th.name} index={i} position={position} label={th.name} />
      ))}
    </View>
  );
}

export function ThreadNameRow({ index, position, label }: { index: number; position: SharedValue<number>; label: string }) {
  const style = useAnimatedStyle(() => {
    const d = index - position.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.2), transform: [{ translateY: d * DIGIT_H }] };
  });
  return <Animated.Text style={[styles.threadName, style]}>{label}</Animated.Text>;
}

const styles = StyleSheet.create({
  digits: { flexDirection: 'row' },
  digitBox: { height: DIGIT_H, width: 12, overflow: 'hidden' },
  // Absolute with its full height: inside the one-line box a plain Text is measured to one line
  // and the other digits are cut off.
  digitStrip: { position: 'absolute', left: 0, top: 0, width: 12, height: DIGIT_H * 11, fontSize: 18, lineHeight: DIGIT_H, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'], textAlign: 'center' },
  nameBox: { height: DIGIT_H, width: 96, overflow: 'hidden' },
  threadName: { position: 'absolute', right: 0, top: 0, fontSize: 17, lineHeight: DIGIT_H, fontWeight: '700', color: INK },
});

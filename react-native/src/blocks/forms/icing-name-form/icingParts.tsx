import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { ICINGS, strokePoint } from './icingArt';
import type { Lettering } from './icingFont';
import { easeInOut, seg } from './icingMotion';

/* Icing Name Form — the user's lettering on the UI thread, and the rolling price and icing name. */

export const GLIDE = 0.35;
const INK = '#3B2418';
const DIGIT_H = 30;

export type UserWriting = {
  L: Lettering;
  typedAt: number[];
  fromX: number[];
  fromY: number[];
  movedAt: number;
  colorFrom: number;
  colorTo: number;
  colorAt: number;
};

export function letterCount(text: string) {
  return Array.from(text).filter((c) => c !== ' ').length;
}

export function priceFor(text: string, colour: number) {
  return 3800 + 50 * letterCount(text) + (colour === 0 ? 0 : 100);
}

export function pipeDuration(len: number) {
  'worklet';
  return 0.22 + len / 200;
}

/** Per stroke, how much is piped (0..1) for the user's lettering at `now`. */
export function userFracs(u: UserWriting, now: number, instant: boolean): number[] {
  'worklet';
  const out: number[] = [];
  const L = u.L;
  for (let g = 0; g < L.firstStroke.length; g += 1) {
    const len = L.glyphLength[g];
    const p = instant ? 1 : Math.min(1, Math.max(0, (now - u.typedAt[g]) / pipeDuration(len)));
    let ink = p * len;
    for (let k = 0; k < L.strokeCount[g]; k += 1) {
      const s = L.strokes[L.firstStroke[g] + k];
      const full = Math.max(0.5, s.len[s.len.length - 1]);
      out.push(Math.min(1, Math.max(0, ink / full)));
      ink -= full;
    }
  }
  return out;
}

export function userShift(u: UserWriting, now: number, axis: number): number[] {
  'worklet';
  const k = 1 - easeInOut(seg(now, u.movedAt, u.movedAt + GLIDE));
  const src = axis === 0 ? u.fromX : u.fromY;
  const out: number[] = [];
  for (let i = 0; i < src.length; i += 1) out.push(src[i] * k);
  return out;
}

/** The nozzle's target while the user types: [x, y (cake-local), busy]. */
export function userPen(u: UserWriting, fr: number[], sx: number[], sy: number[], park: number[]): number[] {
  'worklet';
  const L = u.L;
  for (let i = L.strokes.length - 1; i >= 0; i -= 1) {
    if (fr[i] > 0 && fr[i] < 1) {
      const s = L.strokes[i];
      const q = strokePoint(s.pts, s.len, fr[i] * s.len[s.len.length - 1]);
      return [q[0] + sx[s.glyph], q[1] + sy[s.glyph], 1];
    }
  }
  return [park[0], park[1], 0];
}

export function zeros(n: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < n; i += 1) out.push(0);
  return out;
}

export function priceDigit(cents: number, k: number) {
  'worklet';
  return Math.floor(cents / Math.pow(10, k)) % 10;
}

export function PriceColumn({ price, place }: { price: SharedValue<number[]>; place: number }) {
  const style = useAnimatedStyle(() => {
    const p = price.get();
    const a = priceDigit(p[0], place);
    const b = priceDigit(p[1], place);
    return { transform: [{ translateY: -(a + (b - a) * p[2]) * DIGIT_H }] };
  });
  return (
    <View style={styles.digitBox}>
      <Animated.Text style={[styles.digitStrip, style]}>{'0\n1\n2\n3\n4\n5\n6\n7\n8\n9'}</Animated.Text>
    </View>
  );
}

export function Price({ price }: { price: SharedValue<number[]> }) {
  return (
    <View style={styles.priceRow} accessible accessibilityLabel="Total price">
      <Text style={styles.priceGlyph}>$</Text>
      <PriceColumn price={price} place={3} />
      <PriceColumn price={price} place={2} />
      <Text style={styles.priceGlyph}>.</Text>
      <PriceColumn price={price} place={1} />
      <PriceColumn price={price} place={0} />
    </View>
  );
}

export function IcingName({ position }: { position: SharedValue<number> }) {
  return (
    <View style={styles.nameBox}>
      {ICINGS.map((ic, i) => (
        <IcingNameRow key={ic.name} index={i} position={position} label={ic.name} />
      ))}
    </View>
  );
}

export function IcingNameRow({ index, position, label }: { index: number; position: SharedValue<number>; label: string }) {
  const style = useAnimatedStyle(() => {
    const d = index - position.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.2), transform: [{ translateY: d * 20 }] };
  });
  return <Animated.Text style={[styles.icingName, style]}>{label}</Animated.Text>;
}

const styles = StyleSheet.create({
  nameBox: { height: 20, width: 92, overflow: 'hidden' },
  icingName: { position: 'absolute', left: 0, top: 0, fontSize: 15, lineHeight: 20, fontWeight: '700', color: INK },
  priceRow: { flexDirection: 'row', alignItems: 'center' },
  priceGlyph: { fontSize: 26, lineHeight: DIGIT_H, fontWeight: '700', color: INK },
  digitBox: { height: DIGIT_H, width: 17, overflow: 'hidden' },
  // Absolute with its full height: inside the one-line box a plain Text is measured to one line
  // and the other digits are cut off.
  digitStrip: { position: 'absolute', left: 0, top: 0, width: 17, height: DIGIT_H * 10, fontSize: 26, lineHeight: DIGIT_H, fontWeight: '700', color: INK, fontVariant: ['tabular-nums'], textAlign: 'center' },
});

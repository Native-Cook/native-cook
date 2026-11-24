// Split Amount Pad — the amount: a pool of digit tiles that live entirely on the UI thread.
//
// No digit is React state. The pad's worklet writes one table (K numbers per pooled tile: state,
// glyph, centre, birth number, stagger order) and every tile reacts to its own row of it: a newly
// born tile takes its glyph and drops in on a spring, a living one slides to its new centre, a
// removed one is flicked away. A tile shows its glyph by sliding a column of eleven one-line rows
// ("0" … "9", ".") inside a one-line
// window, so even the character never crosses to JS: a burst of key presses costs twelve small
// reactions each and no render.

import { StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, type SharedValue, useAnimatedReaction, useAnimatedStyle, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';

import { DROP, FLICK, KEY_DOT, SPRING, TIME } from './sap-motion';
import { C, DOLLAR_GAP, DOLLAR_SCALE, POOL } from './sap-theme';

/** numbers per tile in the table */
export const K = 5;
export const TILE = { state: 0, glyph: 1, centre: 2, born: 3, order: 4 } as const;
export const HIDDEN = 0;
export const LIVE = 1;
export const LEAVING = 2;

/** the amount's glyph widths, pt */
export type Metrics = { digit: number; dot: number; comma: number; dollar: number };

/** the layout's head: the dollar's left edge, the comma's centre, whether there is a comma; then one centre per typed code */
export const LAY = { dollar: 0, comma: 1, hasComma: 2, first: 3 } as const;

/** where everything sits for these typed codes, x relative to the row's middle */
export function amountLayout(codes: number[], m: Metrics) {
  'worklet';
  let whole = codes.length;
  for (let i = 0; i < codes.length; i++) {
    if (codes[i] === KEY_DOT) {
      whole = i;
      break;
    }
  }
  const hasComma = whole >= 4;
  let total = m.dollar + DOLLAR_GAP + (hasComma ? m.comma : 0);
  // an empty amount shows a placeholder zero
  if (codes.length === 0) total += m.digit;
  for (let i = 0; i < codes.length; i++) total += codes[i] === KEY_DOT ? m.dot : m.digit;
  const out: number[] = [-total / 2, 0, hasComma ? 1 : 0];
  let x = -total / 2 + m.dollar + DOLLAR_GAP;
  for (let i = 0; i < codes.length; i++) {
    if (hasComma && i === whole - 3) {
      out[LAY.comma] = x + m.comma / 2;
      x += m.comma;
    }
    const w = codes[i] === KEY_DOT ? m.dot : m.digit;
    out.push(x + w / 2);
    x += w;
  }
  return out;
}

// One row per glyph, each its own view of exactly one line's height. (A single multi-line Text does
// not work here: Yoga measures a child at most as tall as its fixed-height parent, so only the first
// line was laid out. Every size below is explicit for the same reason.)
const GLYPHS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.'] as const;

type TileProps = {
  index: number;
  table: SharedValue<number[]>;
  /** the row's middle, pt from its left edge */
  mid: number;
  width: number;
  size: number;
  line: number;
  instant: boolean;
};

function DigitTile({ index, table, mid, width, size, line, instant }: TileProps) {
  const glyph = useSharedValue(0);
  const x = useSharedValue(0);
  const enter = useSharedValue(0);
  const leave = useSharedValue(0);
  const turn = useSharedValue(0);
  const seenBorn = useSharedValue(-1);
  const seenState = useSharedValue(HIDDEN);
  const seenCentre = useSharedValue(0);

  useAnimatedReaction(
    () => table.get(),
    (rows) => {
      const o = index * K;
      const state = rows[o + TILE.state];
      const centre = rows[o + TILE.centre];
      if (state === LIVE) {
        if (rows[o + TILE.born] !== seenBorn.get()) {
          // born: take the glyph, stand at the final place, drop in
          const born = rows[o + TILE.born];
          seenBorn.set(born);
          glyph.set(rows[o + TILE.glyph]);
          x.set(centre);
          leave.set(0);
          turn.set((born % 2 === 0 ? 1 : -1) * (DROP.turnMin + ((born * 13) % DROP.turnSpan)));
          if (instant) {
            enter.set(1);
          } else {
            enter.set(0);
            enter.set(withDelay(rows[o + TILE.order] * TIME.dropStagger, withSpring(1, SPRING.drop)));
          }
        } else if (centre !== seenCentre.get()) {
          x.set(instant ? centre : withSpring(centre, SPRING.slide));
        }
        seenCentre.set(centre);
      } else if (state === LEAVING && seenState.get() === LIVE) {
        leave.set(instant ? 1 : withDelay(rows[o + TILE.order] * TIME.flickStagger, withTiming(1, { duration: TIME.flick, easing: Easing.out(Easing.quad) })));
      }
      seenState.set(state);
    },
  );

  const tile = useAnimatedStyle(() => {
    const e = enter.get();
    const l = leave.get();
    return {
      opacity: Math.min(1, Math.max(0, e * 3)) * (1 - l * l),
      transform: [
        { translateX: mid + x.get() - width / 2 + FLICK.x * l },
        { translateY: DROP.rise * (1 - e) + FLICK.up * l + FLICK.fall * l * l },
        { rotate: `${turn.get() * (1 - e) + FLICK.turn * l}deg` },
        { scale: (DROP.scale + (1 - DROP.scale) * e) * (1 - FLICK.shrink * l) },
      ],
    };
  });
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -glyph.get() * line }] }));

  return (
    <Animated.View pointerEvents="none" style={[styles.tile, { width, height: line }, tile]}>
      <Animated.View style={[styles.strip, { width, height: line * GLYPHS.length }, strip]}>
        {GLYPHS.map((g) => (
          <View key={g} style={{ width, height: line }}>
            <Text allowFontScaling={false} numberOfLines={1} style={[styles.digit, { width, height: line, fontSize: size, lineHeight: line }]}>
              {g}
            </Text>
          </View>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

type Props = {
  table: SharedValue<number[]>;
  /** the dollar sign's left edge and the comma's centre, relative to the middle */
  dollarX: SharedValue<number>;
  commaX: SharedValue<number>;
  /** 0…1: the comma shown, the placeholder zero shown */
  commaOn: SharedValue<number>;
  empty: SharedValue<number>;
  shake: SharedValue<number>;
  width: number;
  size: number;
  line: number;
  metrics: Metrics;
  instant: boolean;
};

const TILES = Array.from({ length: POOL }, (_, i) => i);

export function SapAmount({ table, dollarX, commaX, commaOn, empty, shake, width, size, line, metrics, instant }: Props) {
  const mid = width / 2;
  const row = useAnimatedStyle(() => ({ transform: [{ translateX: shake.get() }] }));
  const dollar = useAnimatedStyle(() => ({ transform: [{ translateX: mid + dollarX.get() }] }));
  const comma = useAnimatedStyle(() => ({
    opacity: commaOn.get(),
    transform: [{ translateX: mid + commaX.get() - metrics.comma / 2 }, { translateY: 6 * (1 - commaOn.get()) }, { scale: 0.6 + 0.4 * commaOn.get() }],
  }));
  // the placeholder zero stands where an empty amount's first digit would
  const zero = useAnimatedStyle(() => ({
    opacity: empty.get() * 0.24,
    transform: [{ translateX: mid + (metrics.dollar + DOLLAR_GAP) / 2 - metrics.digit / 2 }],
  }));
  const digit = [styles.digit, { fontSize: size, lineHeight: line }];

  return (
    <Animated.View style={[{ width, height: line }, row]}>
      <Animated.Text allowFontScaling={false} style={[styles.dollar, { fontSize: size * DOLLAR_SCALE, lineHeight: line, top: -size * 0.14 }, dollar]}>
        $
      </Animated.Text>
      <Animated.Text allowFontScaling={false} style={[styles.abs, digit, { width: metrics.digit }, zero]}>
        0
      </Animated.Text>
      <Animated.Text allowFontScaling={false} style={[styles.abs, digit, { width: metrics.comma }, comma]}>
        ,
      </Animated.Text>
      {TILES.map((i) => (
        <DigitTile key={i} index={i} table={table} mid={mid} width={metrics.digit} size={size} line={line} instant={instant} />
      ))}
    </Animated.View>
  );
}

/** the glyphs measured once, off screen, in the amount's own type */
export function SapMeasure({ size, line, onMetrics }: { size: number; line: number; onMetrics: (key: keyof Metrics, value: number) => void }) {
  const digit = [styles.digit, { fontSize: size, lineHeight: line }];
  return (
    <View pointerEvents="none" style={styles.measure}>
      <Text allowFontScaling={false} style={digit} onLayout={(e) => onMetrics('digit', e.nativeEvent.layout.width)}>
        0
      </Text>
      <Text allowFontScaling={false} style={digit} onLayout={(e) => onMetrics('dot', e.nativeEvent.layout.width)}>
        .
      </Text>
      <Text allowFontScaling={false} style={digit} onLayout={(e) => onMetrics('comma', e.nativeEvent.layout.width)}>
        ,
      </Text>
      <Text allowFontScaling={false} style={[styles.dollar, { position: 'relative', fontSize: size * DOLLAR_SCALE, lineHeight: line }]} onLayout={(e) => onMetrics('dollar', e.nativeEvent.layout.width)}>
        $
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  tile: { position: 'absolute', left: 0, top: 0, overflow: 'hidden' },
  strip: { position: 'absolute', left: 0, top: 0 },
  abs: { position: 'absolute', left: 0, top: 0 },
  digit: { color: C.ink, fontFamily: 'ui-rounded', fontWeight: '700', fontVariant: ['tabular-nums'], textAlign: 'center', letterSpacing: 0 },
  dollar: { position: 'absolute', left: 0, color: C.mute, fontFamily: 'ui-rounded', fontWeight: '600' },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0, flexDirection: 'row', alignItems: 'flex-start' },
});

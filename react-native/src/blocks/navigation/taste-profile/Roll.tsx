import { Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { One } from './icons';
import { type Change, clamp01, REDUCED, reducedDip, rollQ } from './motion';
import { ASCENT, GLYPH_EM, type GlyphRun } from './theme';

/**
 * A value that rolls between layers (one layer per distinct value), like an odometer: the leaving
 * glyphs slide out and fade, the entering ones slide in, right to left with a small stagger, inside
 * a clip window. Which layers leave and enter comes from the Change: `layerOf[city]`.
 */
export type RollGroup = {
  layerOf: readonly number[];
  /** Per layer, the number it shows (sets the direction: a larger value rolls up). */
  vals: readonly number[];
  start: number;
  dur: number;
  stagger: number;
  /** Travel in ref px. */
  travel: number;
};

type Geo = { U: number; FS: number; y: (v: number) => number };

export function rollStyle(c: Change, g: RollGroup, layer: number, slot: number) {
  'worklet';
  const lf = g.layerOf[c.from];
  const lt = g.layerOf[c.to];
  if (c.reduced) {
    const show = c.tau >= REDUCED.fade / 2 ? lt : lf;
    return { y: 0, o: layer === show ? (lf === lt ? 1 : reducedDip(c.tau)) : 0 };
  }
  if (lf === lt) return { y: 0, o: layer === lt ? 1 : 0 };
  if (layer !== lf && layer !== lt) return { y: 0, o: 0 };
  const dir = g.vals[lt] >= g.vals[lf] ? 1 : -1;
  const q = rollQ(c.tau - g.start, slot, g.dur, g.stagger);
  // The leaving glyph is gone by 60 % of its roll, the entering one appears from 30 %: never two legible glyphs at once.
  if (layer === lf) return { y: -dir * q * g.travel, o: 1 - clamp01(q / 0.6) };
  return { y: dir * (1 - q) * g.travel, o: clamp01((q - 0.3) / 0.7) };
}

/** One glyph of a GlyphRun, placed relative to a window whose top-left is (wx, wy) in ref px. */
export function GlyphView({ ch, inkL, inkW, run, geo, wx, wy }: { ch: string; inkL: number; inkW: number; run: GlyphRun; geo: Geo; wx: number; wy: number }) {
  const { U, FS } = geo;
  if (ch === '1') {
    return (
      <View style={{ position: 'absolute', left: (inkL - wx) * U, top: (run.baseline - run.digitH - wy) * U }}>
        <One w={inkW} h={run.digitH} u={U} color={run.color} />
      </View>
    );
  }
  const [bearing, natural] = GLYPH_EM[ch];
  const fontSize = run.size * FS;
  const sx = inkW / (natural * run.size * 3);
  return (
    <Text
      allowFontScaling={false}
      style={{
        position: 'absolute',
        left: (inkL - wx) * U - bearing * fontSize * sx,
        top: (run.baseline - wy) * U - ASCENT * fontSize,
        fontFamily: run.font,
        fontSize,
        color: run.color,
        includeFontPadding: false,
        transformOrigin: 'left center',
        transform: [{ scaleX: sx }],
      }}
    >
      {ch}
    </Text>
  );
}

function RollingGlyph(props: {
  change: SharedValue<Change>;
  group: RollGroup;
  layer: number;
  slot: number;
  glyph: readonly [string, number, number];
  run: GlyphRun;
  geo: Geo;
  wx: number;
  wy: number;
}) {
  const { change, group, layer, slot, glyph, run, geo, wx, wy } = props;
  const U = geo.U;
  const style = useAnimatedStyle(() => {
    const r = rollStyle(change.get(), group, layer, slot);
    return { opacity: r.o, transform: [{ translateY: r.y * U }] };
  });
  return (
    <Animated.View style={[{ position: 'absolute', left: 0, top: 0 }, style]}>
      <GlyphView ch={glyph[0]} inkL={glyph[1]} inkW={glyph[2]} run={run} geo={geo} wx={wx} wy={wy} />
    </Animated.View>
  );
}

/** A number with one layer per value, clipped to the window x0…x1 around the digits. */
export function RollNumber(props: {
  change: SharedValue<Change>;
  group: RollGroup;
  layers: readonly (readonly (readonly [string, number, number])[])[];
  run: GlyphRun;
  geo: Geo;
  x0: number;
  x1: number;
  pad: number;
}) {
  const { change, group, layers, run, geo, x0, x1, pad } = props;
  const { U, y } = geo;
  const wy = run.baseline - run.digitH - pad;
  return (
    <View
      pointerEvents="none"
      style={{ position: 'absolute', left: x0 * U, top: y(wy), width: (x1 - x0) * U, height: (run.digitH + 2 * pad) * U, overflow: 'hidden' }}
    >
      {layers.map((glyphs, li) =>
        glyphs.map((g, gi) => (
          <RollingGlyph
            key={`${li}-${gi}`}
            change={change}
            group={group}
            layer={li}
            slot={glyphs.length - 1 - gi}
            glyph={g}
            run={run}
            geo={geo}
            wx={x0}
            wy={wy}
          />
        )),
      )}
    </View>
  );
}

/** A one-line word with one layer per value (the rank's city). */
function RollingWord(props: { change: SharedValue<Change>; group: RollGroup; layer: number; children: React.ReactNode; style: object; U: number }) {
  const { change, group, layer, children, style: base, U } = props;
  const style = useAnimatedStyle(() => {
    const r = rollStyle(change.get(), group, layer, 0);
    return { opacity: r.o, transform: [{ translateY: r.y * U }] };
  });
  return (
    <Animated.Text allowFontScaling={false} numberOfLines={1} style={[base, style]}>
      {children}
    </Animated.Text>
  );
}

export function RollWords(props: { change: SharedValue<Change>; group: RollGroup; words: readonly string[]; style: object; U: number }) {
  const { change, group, words, style, U } = props;
  return (
    <>
      {words.map((w, i) => (
        <RollingWord key={w} change={change} group={group} layer={i} style={style} U={U}>
          {w}
        </RollingWord>
      ))}
    </>
  );
}

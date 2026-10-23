import { BlurView } from 'expo-blur';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { interpolateColor, type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { STAT_GLYPHS } from './Board';
import { Meh, Star, ThumbUp } from './icons';
import { clamp01, EXPAND, lerp, MONTHS, monthBars, spring } from './motion';
import { GlyphView } from './Roll';
import { box, chart, colors, FONT, radius, statNumbers, text, type TextSpec } from './theme';

/** What the screen shows of a hold: which tile, press (0…1), growth e (0…1), s since it grew, highlighted bar. */
export type HoldView = { tile: number; press: number; e: number; since: number; hl: number };
export const HOLD_REST: HoldView = { tile: -1, press: 0, e: 0, since: 0, hl: 5 };

type Geo = { U: number; FS: number; y: (v: number) => number };

const LABELS = [text.lfav, text.lrec, text.lok, text.ldis] as const;

/** The card's rect in ref px: tiles 0–2 grow to the right, the last one to the left. */
export function chartRect(tile: number) {
  const x0 = box.tiles[tile];
  const x = tile === 3 ? x0 + box.tileW - chart.w : x0;
  return { x, y: box.tileY, w: chart.w, h: chart.h };
}

/** Bar i's left edge inside the card (ref px). */
function barX(i: number) {
  const gap = (chart.w - 2 * chart.pad - 6 * chart.barW) / 5;
  return chart.pad + i * (chart.barW + gap);
}

function TileIcon({ tile, U }: { tile: number; U: number }) {
  const ic = box.tileIcons[tile];
  if (tile === 0) return <Star w={ic.w} h={ic.h} u={U} />;
  if (tile === 2) return <Meh w={ic.w} h={ic.h} u={U} />;
  return <ThumbUp w={ic.w} h={ic.h} u={U} down={tile === 3} />;
}

function Bar(props: { hold: SharedValue<HoldView>; i: number; value: number; max: number; left: number; U: number; FS: number; reduced: boolean }) {
  const { hold, i, value, max, left, U, FS, reduced } = props;
  const h = Math.max(6, (value / max) * chart.barMax);
  const room = h + 24; // room above for the spring's overshoot
  const grow = useAnimatedStyle(() => {
    const v = hold.get();
    const B = EXPAND.bars;
    const p = reduced ? 1 : spring(v.since - B.delay - i * B.stagger, B.spring);
    const on = clamp01(1 - Math.abs(v.hl - i));
    return {
      transform: [{ translateY: (1 - p) * (h + 4) * U }],
      backgroundColor: interpolateColor(on, [0, 1], [chart.barColor, chart.barOn]),
    };
  });
  const label = useAnimatedStyle(() => {
    const v = hold.get();
    const B = EXPAND.bars;
    const p = reduced ? 1 : spring(v.since - B.delay - i * B.stagger, B.spring);
    const on = clamp01(1 - Math.abs(v.hl - i) * 1.6);
    return { opacity: on * clamp01(p * 1.4 - 0.4), transform: [{ translateY: (1 - p) * (h + 4) * U }] };
  });
  return (
    <>
      <View style={{ position: 'absolute', left: left * U, top: (chart.barsBottom - room) * U, width: chart.barW * U, height: room * U, overflow: 'hidden' }}>
        <Animated.View style={[{ position: 'absolute', left: 0, bottom: 0, width: chart.barW * U, height: h * U, borderRadius: (chart.barW / 2.6) * U }, grow]} />
      </View>
      <Animated.Text
        allowFontScaling={false}
        style={[
          {
            position: 'absolute',
            left: (left - 20) * U,
            width: (chart.barW + 40) * U,
            top: (chart.barsBottom - h - chart.valueGap - 34) * U,
            textAlign: 'center',
            fontFamily: FONT.extra,
            fontSize: 12.3 * FS,
            color: colors.ink,
            fontVariant: ['tabular-nums'],
          },
          label,
        ]}
      >
        {value}
      </Animated.Text>
      <Text
        allowFontScaling={false}
        style={{
          position: 'absolute',
          left: (left - 20) * U,
          width: (chart.barW + 40) * U,
          top: (chart.monthBaseline - 28) * U,
          textAlign: 'center',
          fontFamily: FONT.body,
          fontSize: 10.2 * FS,
          color: chart.caption,
        }}
      >
        {MONTHS[i]}
      </Text>
    </>
  );
}

/**
 * The held tile grows into a bar chart for the shown city: a light veil and blur over the screen,
 * the tile's card growing to two tiles by three (its header stays where it was), six months of bars
 * rising in a stagger, the highlighted bar following the finger.
 */
export function TileChart(props: {
  hold: SharedValue<HoldView>;
  tile: number;
  city: number;
  cityName: string;
  geo: Geo;
  fontsReady: boolean;
  lineStyle: (s: TextSpec) => object;
  reduced: boolean;
  width: number;
  height: number;
}) {
  const { hold, tile, city, cityName, geo, fontsReady, lineStyle, reduced, width, height } = props;
  const { U, FS, y } = geo;
  const r = chartRect(tile);
  const t0 = { x: box.tiles[tile], y: box.tileY, w: box.tileW, h: box.tileH };
  const cx = (t0.x + t0.w / 2) * U;
  const cy = y(t0.y + t0.h / 2);
  const bars = monthBars(city, tile);
  const max = Math.max(...bars);

  // Screen values for the worklets (they cannot call `y`).
  const top0 = y(t0.y);
  const top1 = y(r.y);
  const veil = useAnimatedStyle(() => ({ opacity: hold.get().e }));
  const card = useAnimatedStyle(() => {
    const v = hold.get();
    // Reduce Motion: the card is at its full size at once and only fades.
    const e = reduced ? (v.e > 0 ? 1 : 0) : v.e;
    const s = 1 - 0.03 * v.press;
    // An absolutely placed, childless view: its box may animate (no siblings re-lay out).
    return {
      left: lerp(t0.x, r.x, e) * U,
      top: lerp(top0, top1, e),
      width: lerp(t0.w, r.w, e) * U,
      height: lerp(t0.h, r.h, e) * U,
      opacity: reduced && v.press === 0 ? v.e : 1,
      transform: [{ scale: s }],
    };
  });
  // The lifted shadow is a second, pre-shadowed layer that fades in (shadows are not animated).
  const lift = useAnimatedStyle(() => {
    const v = hold.get();
    const e = reduced ? (v.e > 0 ? 1 : 0) : v.e;
    return {
      left: lerp(t0.x, r.x, e) * U,
      top: lerp(top0, top1, e),
      width: lerp(t0.w, r.w, e) * U,
      height: lerp(t0.h, r.h, e) * U,
      opacity: reduced ? v.e : clamp01(v.e),
      transform: [{ scale: 1 - 0.03 * v.press }],
    };
  });
  const header = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * hold.get().press }] }));
  const content = useAnimatedStyle(() => {
    const e = hold.get().e;
    return { opacity: clamp01((e - 0.45) / 0.4), transform: [{ translateY: (1 - e) * 24 * U }] };
  });

  const run = statNumbers[tile];
  const glyphs = STAT_GLYPHS[tile][city];
  const L = LABELS[tile];
  const ic = box.tileIcons[tile];

  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill]}>
      <Animated.View style={[StyleSheet.absoluteFill, veil]}>
        <BlurView intensity={14} tint="light" style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, { backgroundColor: chart.veil }]} />
      </Animated.View>
      <Animated.View
        style={[
          styles.card,
          {
            position: 'absolute',
            borderRadius: radius.tile * U,
            shadowOpacity: EXPAND.shadow.to,
            shadowRadius: 16 * FS,
            shadowOffset: { width: 0, height: 6 * FS },
          },
          lift,
        ]}
      />
      <Animated.View
        style={[
          styles.card,
          {
            position: 'absolute',
            borderRadius: radius.tile * U,
            borderWidth: Math.max(1, 2.4 * U),
            shadowOpacity: EXPAND.shadow.from,
            shadowRadius: 4 * FS,
            shadowOffset: { width: 0, height: 2 * FS },
          },
          card,
        ]}
      />
      <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width, height, transformOrigin: [cx, cy, 0] }, header]}>
        <View style={{ position: 'absolute', left: ic.x * U, top: y(ic.y) }}>
          <TileIcon tile={tile} U={U} />
        </View>
        {fontsReady ? (
          <>
            {glyphs.map(([ch, inkL, inkW], i) => (
              <GlyphView key={i} ch={ch} inkL={inkL} inkW={inkW} run={run} geo={geo} wx={0} wy={-y(0) / U} />
            ))}
            <Text allowFontScaling={false} numberOfLines={1} style={lineStyle(L)}>
              {L.text}
            </Text>
          </>
        ) : null}
      </Animated.View>
      <Animated.View style={[{ position: 'absolute', left: r.x * U, top: y(r.y), width: r.w * U, height: r.h * U }, content]}>
        {fontsReady ? (
          <Text
            allowFontScaling={false}
            numberOfLines={1}
            style={{
              position: 'absolute',
              left: chart.pad * U,
              top: (chart.captionBaseline - 30) * U,
              fontFamily: FONT.body,
              fontSize: 10.2 * FS,
              color: chart.caption,
            }}
          >
            {`last 6 months · ${cityName}`}
          </Text>
        ) : null}
        {fontsReady
          ? bars.map((v, i) => <Bar key={i} hold={hold} i={i} value={v} max={max} left={barX(i)} U={U} FS={FS} reduced={reduced} />)
          : null}
      </Animated.View>
    </View>
  );
}

/** Which bar the finger is over (ref px from the card's left), as a whole index 0…5. */
export function barUnder(xRef: number) {
  'worklet';
  const gap = (chart.w - 2 * chart.pad - 6 * chart.barW) / 5;
  const i = Math.round((xRef - chart.pad - chart.barW / 2) / (chart.barW + gap));
  return Math.max(0, Math.min(5, i));
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.tileFill,
    borderColor: colors.tileHairline,
    shadowColor: '#000',
  },
});

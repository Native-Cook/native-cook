/** The people a pull can choose, and the activity list: people by their photos, merchants by their own marks. */
import { useMemo, type ReactNode } from 'react';
import { Image, StyleSheet, Text, View, type ImageSourcePropType } from 'react-native';
import Animated, {
  FadeOut, LayoutAnimationConfig, LinearTransition, ReduceMotion, interpolateColor, useAnimatedStyle, withDelay, withTiming,
  type EntryExitAnimationFunction,
} from 'react-native-reanimated';
import Svg, { Circle, G, Path, Rect } from 'react-native-svg';

import { ROWS, SEND, easeInOut, easeOut } from './motion';
import { COLORS, LAYOUT, PEOPLE, TYPE, formatMoney, type Layout, type Merchant, type Row } from './theme';
import type { SendValues } from './use-send-motion';

/** A round photo with a hairline inside its edge, so a light background never melts into the page. */
export function Avatar({ source, size }: { source: ImageSourcePropType; size: number }) {
  const round = { width: size, height: size, borderRadius: size / 2 };
  return (
    <View style={[round, styles.avatar]}>
      <Image source={source} style={round} fadeDuration={0} />
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.avatarEdge, { borderRadius: size / 2 }]} />
    </View>
  );
}

/**
 * Merchants' marks, invented for this demo: an espresso bean for the café, a leaf for the grocer, a tram for the
 * transit pass. One system for all three: a squircle (so a merchant never reads as a person) in a pale tint of the
 * merchant's hue, the mark in a deep shade of the same hue. Low chroma on purpose: the cobalt card and the chosen
 * person's ring stay the only saturated colour on the page.
 */
const MARKS: Record<Merchant, { tile: string; ink: string; draw: (tile: string, ink: string) => ReactNode }> = {
  coffee: {
    tile: '#F1E8DF',
    ink: '#5B3B27',
    draw: (tile, ink) => (
      <G transform="translate(20 20) rotate(-32)">
        <Path d="M0 -10.5 C 7.2 -10.5 8.6 -3.6 8.6 0 C 8.6 3.6 7.2 10.5 0 10.5 C -7.2 10.5 -8.6 3.6 -8.6 0 C -8.6 -3.6 -7.2 -10.5 0 -10.5 Z" fill={ink} />
        <Path d="M0.6 -9.6 C -3.6 -4.4 3.6 4.4 -0.6 9.6" stroke={tile} strokeWidth={1.7} strokeLinecap="round" fill="none" />
      </G>
    ),
  },
  grocer: {
    tile: '#E3EEE5',
    ink: '#2D5F40',
    draw: (tile, ink) => (
      <G transform="translate(20 20)">
        <Path d="M-8.5 8.5 C -9.5 -1.5 -3.5 -9.5 9 -9 C 9.5 3.5 1.5 9.5 -8.5 8.5 Z" fill={ink} />
        <Path d="M-8.5 8.5 L 4.5 -4.5" stroke={tile} strokeWidth={1.6} strokeLinecap="round" />
      </G>
    ),
  },
  transit: {
    tile: '#F3ECD8',
    ink: '#76560F',
    draw: (tile, ink) => (
      <G transform="translate(20 20)">
        <Path d="M-3 -11.5 L 3 -11.5 M 0 -11.5 L 0 -8.5" stroke={ink} strokeWidth={1.6} strokeLinecap="round" />
        <Rect x={-7.5} y={-8.5} width={15} height={15.5} rx={4} fill={ink} />
        <Rect x={-5.2} y={-6.2} width={10.4} height={5.6} rx={1.6} fill={tile} />
        <Circle cx={-3.7} cy={3} r={1.25} fill={tile} />
        <Circle cx={3.7} cy={3} r={1.25} fill={tile} />
        <Path d="M-4.8 7.5 L -6.8 11 M 4.8 7.5 L 6.8 11" stroke={ink} strokeWidth={1.6} strokeLinecap="round" />
      </G>
    ),
  },
};

function MerchantMark({ kind, size }: { kind: Merchant; size: number }) {
  const m = MARKS[kind];
  return (
    <Svg width={size} height={size} viewBox="0 0 40 40">
      <Rect x={0} y={0} width={40} height={40} rx={12} fill={m.tile} />
      {/* The same hairline edge as a photo's, so tiles and faces sit on the page alike. */}
      <Rect x={0.25} y={0.25} width={39.5} height={39.5} rx={11.75} fill="none" stroke="rgba(11,12,15,0.08)" strokeWidth={0.5} />
      {m.draw(m.tile, m.ink)}
    </Svg>
  );
}

function Person({ index, lay, v }: { index: number; lay: Layout; v: SendValues }) {
  const p = PEOPLE[index];
  // The one who catches the drop squashes, then rebounds once.
  const body = useAnimatedStyle(() => {
    const c = v.target.get() === index ? v.catchAmt.get() : 0;
    return { transform: [{ scaleX: 1 + 0.6 * SEND.catchSquash * c }, { scaleY: 1 - SEND.catchSquash * c }] };
  });
  // The name lights with the ring: as it arrives over this face, and dimming as it rings out after the catch.
  const x = lay.avatarX[index];
  const name = useAnimatedStyle(() => {
    const near = Math.max(0, 1 - Math.abs(v.ringX.get() - x) / lay.pitch);
    const on = v.ringOn.get() * (1 - v.ripple.get()) * near;
    return { color: interpolateColor(on, [0, 1], [COLORS.mute, COLORS.ink]) };
  });
  const w = lay.pitch;
  return (
    <View style={[styles.person, { top: lay.avatarTop, left: lay.avatarX[index] - w / 2, width: w }]}>
      <Animated.View style={body}><Avatar source={p.photo} size={LAYOUT.avatar} /></Animated.View>
      <Animated.Text style={[styles.name, TYPE.name, name]}>{p.name}</Animated.Text>
    </View>
  );
}

/**
 * One ring for the chosen person: it slides between people, and rings out (the ripple) when the drop lands.
 * Reduce Motion keeps its fades and drops its growth.
 */
function Ring({ lay, v, reduced }: { lay: Layout; v: SendValues; reduced: boolean }) {
  const d = (LAYOUT.avatar / 2 + LAYOUT.ringGap + LAYOUT.ringStroke) * 2;
  const grow = reduced ? 0 : 1;
  const style = useAnimatedStyle(() => {
    const k = v.ripple.get();
    const on = v.ringOn.get();
    return {
      opacity: on * (1 - k),
      transform: [{ translateX: v.ringX.get() - d / 2 }, { scale: (1 - grow * 0.08 * (1 - on)) * (1 + grow * SEND.ripple * k) }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.ring, { width: d, height: d, borderRadius: d / 2, top: lay.avatarY - d / 2 }, style]} />;
}

export function PeopleRow({ lay, v, reduced }: { lay: Layout; v: SendValues; reduced: boolean }) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Ring lay={lay} v={v} reduced={reduced} />
      {PEOPLE.map((p, i) => <Person key={p.id} index={i} lay={lay} v={v} />)}
    </View>
  );
}

/**
 * Rows after a send or an Undo: a removed one fades first, then the others make room (ease-in-out: they move on
 * screen), and a new one grows in from 0.97 once the room is nearly made, so it never sits on the row it pushes.
 * Reduce Motion: no movement (the system skips the reflow and the exit), the new row only fades in.
 */
const ROW_EXIT = FadeOut.duration(ROWS.exitMs).easing(easeOut);
const ROW_MOVE = LinearTransition.delay(ROWS.exitMs).duration(ROWS.moveMs).easing(easeInOut);
const rowIn = (reduced: boolean): EntryExitAnimationFunction => () => {
  'worklet';
  const t = { duration: ROWS.enterMs, easing: easeOut, reduceMotion: ReduceMotion.Never };
  const after = reduced ? 0 : ROWS.exitMs + ROWS.enterAfter;
  const from = reduced ? 1 : ROWS.enterScale;
  return {
    initialValues: { opacity: 0, transform: [{ scale: from }] },
    animations: { opacity: withDelay(after, withTiming(1, t), ReduceMotion.Never), transform: [{ scale: withDelay(after, withTiming(1, t), ReduceMotion.Never) }] },
  };
};

export function Activity({ rows, top, reduced }: { rows: Row[]; top: number; reduced: boolean }) {
  const entering = useMemo(() => rowIn(reduced), [reduced]);
  return (
    <View style={[styles.rows, { top }]}>
      <LayoutAnimationConfig skipEntering>
      {rows.slice(0, LAYOUT.rows).map((r) => (
        <Animated.View key={r.id} style={styles.row} entering={entering} exiting={ROW_EXIT} layout={ROW_MOVE} accessible
          accessibilityLabel={`${r.name}, ${r.meta}, ${r.cents > 0 ? 'received' : 'paid'} ${formatMoney(Math.abs(r.cents))}`}>
          {r.person !== undefined
            ? <Avatar source={PEOPLE[r.person].photo} size={LAYOUT.rowIcon} />
            : <MerchantMark kind={r.merchant ?? 'coffee'} size={LAYOUT.rowIcon} />}
          <View style={styles.rowText}>
            <Text style={[TYPE.row, styles.ink]} numberOfLines={1}>{r.name}</Text>
            <Text style={[TYPE.meta, styles.mute]} numberOfLines={1}>{r.meta}</Text>
          </View>
          <Text style={[TYPE.row, styles.amount, r.cents > 0 && { color: COLORS.accent }]}>
            {(r.cents > 0 ? '+' : '') + formatMoney(r.cents)}
          </Text>
        </Animated.View>
      ))}
      </LayoutAnimationConfig>
    </View>
  );
}

const styles = StyleSheet.create({
  avatar: { overflow: 'hidden', backgroundColor: '#DADDE3' },
  avatarEdge: { borderWidth: 0.5, borderColor: 'rgba(11,12,15,0.08)' },
  person: { position: 'absolute', alignItems: 'center' },
  name: { marginTop: LAYOUT.nameGap, textAlign: 'center' },
  ring: { position: 'absolute', left: 0, borderWidth: LAYOUT.ringStroke, borderColor: COLORS.accent },
  rows: { position: 'absolute', left: LAYOUT.edge, right: LAYOUT.edge },
  row: { height: LAYOUT.rowHeight, flexDirection: 'row', alignItems: 'center' },
  rowText: { flex: 1, marginLeft: 12, gap: 2 },
  ink: { color: COLORS.ink },
  mute: { color: COLORS.mute },
  amount: { color: COLORS.ink, fontVariant: ['tabular-nums'], marginLeft: 12 },
});

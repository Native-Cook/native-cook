// Flip Focus Badge — the screen's small parts, laid out from the measured boxes in ffb-theme.ts: the top row, the
// progress line's labels, the hint, the sheet (date, rolling total, caption, segmented control).
import { memo, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, withSpring } from 'react-native-reanimated';

import { rollAt, SPRING } from './ffb-motion';
import { FlameIcon, GearIcon, HelpIcon, TrophyIcon } from './ffb-icons';
import { COPY, comp, FONT, type Layout, ref, TYPE } from './ffb-theme';

/** the Inter metrics: ascent 0.969, descent 0.241 of the size (hhea) */
const ASC = 0.969;
const CONTENT = 1.21;
/** top of a text box of line height `lh` whose baseline falls at `baseline` */
export function textTop(baseline: number, size: number, lh: number) {
  return baseline - (lh - CONTENT * size) / 2 - ASC * size;
}

type Typo = (typeof TYPE)[keyof typeof TYPE];

function Line({
  typo,
  L,
  baseline,
  color,
  children,
  box,
  align = 'center',
}: {
  typo: Typo;
  L: Layout;
  baseline: number;
  color: string;
  children: string;
  box?: { left: number; width: number };
  align?: 'center' | 'left' | 'right';
}) {
  const size = typo.size * L.S;
  const lh = typo.lineHeight * L.S;
  return (
    <Text
      allowFontScaling={false}
      style={[
        {
          position: 'absolute',
          ...(box ?? { left: 0, right: 0 }),
          top: textTop(baseline, size, lh),
          height: lh,
          textAlign: align,
          fontFamily: typo.family,
          fontSize: size,
          lineHeight: lh,
          color,
          letterSpacing: 'tracking' in typo ? typo.tracking * size : 0,
          includeFontPadding: false,
        },
      ]}
    >
      {children}
    </Text>
  );
}
export { Line };

// ---- the top row ------------------------------------------------------------------------------------------------------
export const TopRow = memo(function TopRow({ L }: { L: Layout }) {
  const S = L.S;
  const chip = (b: { x: number; y: number; w: number; h: number }, pad: number, children: React.ReactNode, label: string) => (
    <View
      accessible
      accessibilityLabel={label}
      style={{
        position: 'absolute',
        left: b.x * S,
        top: L.yt(b.y),
        width: b.w * S,
        height: b.h * S,
        borderRadius: (b.h / 2) * S,
        borderCurve: 'continuous',
        backgroundColor: ref.color.chip,
        flexDirection: 'row',
        alignItems: 'center',
        paddingLeft: pad * S,
      }}
    >
      {children}
    </View>
  );
  const count = (n: number, gap: number) => (
    <Text
      allowFontScaling={false}
      style={{
        marginLeft: gap * S,
        fontFamily: TYPE.count.family,
        fontSize: TYPE.count.size * S,
        lineHeight: TYPE.count.lineHeight * S,
        color: ref.color.ink,
        includeFontPadding: false,
      }}
    >
      {n}
    </Text>
  );
  const disc = (b: { x: number; y: number; w: number; h: number }, icon: React.ReactNode, label: string) => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={4}
      style={({ pressed }) => ({
        position: 'absolute',
        left: b.x * S,
        top: L.yt(b.y),
        width: b.w * S,
        height: b.h * S,
        borderRadius: (b.h / 2) * S,
        backgroundColor: ref.color.disc,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: pressed ? 0.7 : 1,
      })}
    >
      {icon}
    </Pressable>
  );
  return (
    <>
      {chip(
        comp.chipFlame,
        6,
        <>
          <FlameIcon size={24 * S} />
          {count(COPY.streak, 3)}
        </>,
        `${COPY.streak} day streak`,
      )}
      {chip(
        comp.chipTrophy,
        8.8,
        <>
          <TrophyIcon size={24 * S} />
          {count(COPY.trophies, 5)}
        </>,
        `${COPY.trophies} trophies`,
      )}
      {disc(comp.discHelp, <HelpIcon size={22 * S} />, COPY.helpA11y)}
      {disc(comp.discGear, <GearIcon size={23 * S} />, COPY.settingsA11y)}
    </>
  );
});

// ---- crossfading text (labels and hints) ---------------------------------------------------------------------------------
export function Crossfade({
  a,
  b,
  k,
  typo,
  L,
  baseline,
  color,
  align = 'center',
  slide = 0,
  left,
  width,
}: {
  a: string;
  b: string;
  k: SharedValue<number>;
  typo: Typo;
  L: Layout;
  baseline: number;
  color: string;
  align?: 'center' | 'left' | 'right';
  slide?: number;
  left?: number;
  width?: number;
}) {
  // the two lines never overlap: the old one is gone by the half-way point, the new one arrives after it
  const styleA = useAnimatedStyle(() => ({ opacity: Math.max(1 - 2 * k.get(), 0), transform: [{ translateY: -slide * k.get() }] }));
  const styleB = useAnimatedStyle(() => ({ opacity: Math.max(2 * k.get() - 1, 0), transform: [{ translateY: slide * (1 - k.get()) }] }));
  const box = left === undefined || width === undefined ? undefined : { left, width };
  return (
    <>
      <Animated.View style={[StyleSheet.absoluteFill, styleA]} pointerEvents="none">
        <Line typo={typo} L={L} baseline={baseline} color={color} align={align} box={box}>
          {a}
        </Line>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, styleB]} pointerEvents="none">
        <Line typo={typo} L={L} baseline={baseline} color={color} align={align} box={box}>
          {b}
        </Line>
      </Animated.View>
    </>
  );
}

// ---- the rolling total ----------------------------------------------------------------------------------------------------
function RollChar({
  from,
  to,
  slot,
  k,
  size,
  lh,
  family,
  color,
}: {
  from: string;
  to: string;
  slot: number;
  k: SharedValue<number>;
  size: number;
  lh: number;
  family: string;
  color: string;
}) {
  const fromStyle = useAnimatedStyle(() => {
    const p = rollAt(k.get(), slot);
    return { transform: [{ translateY: -p * lh }], opacity: 1 - Math.min(p * 1.3, 1) };
  });
  const toStyle = useAnimatedStyle(() => {
    const p = rollAt(k.get(), slot);
    return { transform: [{ translateY: (1 - p) * lh }], opacity: Math.min(p * 1.3, 1) };
  });
  const text = {
    fontFamily: family,
    fontSize: size,
    lineHeight: lh,
    color,
    includeFontPadding: false,
    fontVariant: ['tabular-nums' as const],
  };
  return (
    <View style={{ height: lh, overflow: 'hidden' }}>
      <Text allowFontScaling={false} style={[text, { opacity: 0 }]}>
        {to}
      </Text>
      <Animated.Text allowFontScaling={false} style={[text, StyleSheet.absoluteFill, fromStyle]}>
        {from}
      </Animated.Text>
      <Animated.Text allowFontScaling={false} style={[text, StyleSheet.absoluteFill, toStyle]}>
        {to}
      </Animated.Text>
    </View>
  );
}

/** "10h 24m" → "11h 00m": only the digits that change roll, right to left, 90 ms apart */
export function RollTotal({
  from,
  to,
  k,
  L,
  baseline,
  color,
}: {
  from: string;
  to: string;
  k: SharedValue<number>;
  L: Layout;
  baseline: number;
  color: string;
}) {
  const typo = TYPE.total;
  const size = typo.size * L.S;
  const lh = typo.lineHeight * L.S;
  const len = Math.max(from.length, to.length);
  const cells = useMemo(() => {
    const a = from.padEnd(len, ' ');
    const b = to.padEnd(len, ' ');
    let slot = 0;
    const out: { from: string; to: string; slot: number; roll: boolean }[] = [];
    for (let i = len - 1; i >= 0; i--) {
      const roll = a[i] !== b[i];
      out.unshift({ from: a[i], to: b[i], slot: roll ? slot : -1, roll });
      if (roll) slot += 1;
    }
    return out;
  }, [from, to, len]);
  const text = {
    fontFamily: typo.family,
    fontSize: size,
    lineHeight: lh,
    color,
    includeFontPadding: false,
    letterSpacing: typo.tracking * size,
    fontVariant: ['tabular-nums' as const],
  };
  return (
    <View
      accessible
      accessibilityLabel={to.replace('h', ' hours ').replace('m', ' minutes')}
      style={{ position: 'absolute', left: 0, right: 0, top: textTop(baseline, size, lh), height: lh, alignItems: 'center' }}
    >
      <View style={{ flexDirection: 'row' }}>
        {cells.map((c, i) =>
          c.roll ? (
            <RollChar key={i} from={c.from} to={c.to} slot={c.slot} k={k} size={size} lh={lh} family={typo.family} color={color} />
          ) : (
            <Text key={i} allowFontScaling={false} style={text}>
              {c.to}
            </Text>
          ),
        )}
      </View>
    </View>
  );
}

// ---- the segmented control -------------------------------------------------------------------------------------------------
export function Segmented({ L, index, onChange, top }: { L: Layout; index: number; onChange: (i: number) => void; top: number }) {
  const S = L.S;
  const c = comp.seg;
  const itemW = (c.w - 2 * c.pad - 3 * c.gap) / 4;
  const x = (i: number) => (c.pad + i * (itemW + c.gap)) * S;
  const pillX = x(index);
  const pill = useAnimatedStyle(() => ({ transform: [{ translateX: withSpring(pillX, SPRING.snap) }] }), [pillX]);
  return (
    <View
      accessibilityRole="tablist"
      style={{
        position: 'absolute',
        left: c.x * S - comp.sheet.x * S,
        top,
        width: c.w * S,
        height: c.h * S,
        borderRadius: c.r * S,
        borderCurve: 'continuous',
        backgroundColor: ref.color.seg,
      }}
    >
      {[0, 1, 2, 3].map((i) => (
        <View
          key={i}
          pointerEvents="none"
          style={{
            position: 'absolute',
            left: x(i),
            top: c.pad * S + ((c.h - 2 * c.pad - c.itemH) * S) / 2,
            width: itemW * S,
            height: c.itemH * S,
            borderRadius: c.itemR * S,
            borderCurve: 'continuous',
            backgroundColor: ref.color.segOff,
          }}
        />
      ))}
      <Animated.View
        pointerEvents="none"
        style={[
          {
            position: 'absolute',
            left: 0,
            top: c.pad * S + ((c.h - 2 * c.pad - c.itemH) * S) / 2,
            width: itemW * S,
            height: c.itemH * S,
            borderRadius: c.itemR * S,
            borderCurve: 'continuous',
            backgroundColor: ref.color.segOn,
          },
          pill,
        ]}
      />
      {COPY.segments.map((s, i) => (
        <Pressable
          key={s}
          accessibilityRole="tab"
          accessibilityState={{ selected: i === index }}
          accessibilityLabel={['Day', 'Week', 'Month', 'Year'][i]}
          onPress={() => onChange(i)}
          style={{
            position: 'absolute',
            left: x(i),
            top: 0,
            width: itemW * S,
            height: c.h * S,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Text
            allowFontScaling={false}
            style={{
              fontFamily: TYPE.seg.family,
              fontSize: TYPE.seg.size * S,
              lineHeight: TYPE.seg.lineHeight * S,
              color: i === index ? ref.color.ink : ref.color.inkSeg,
              includeFontPadding: false,
            }}
          >
            {s}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

export const FONTS = {
  [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
  [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
};

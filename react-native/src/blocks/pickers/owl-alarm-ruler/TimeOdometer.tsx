/**
 * The big clock: four digit wheels that roll with the ruler like an odometer (forward in time
 * rolls up), and the AM / PM card that flips when the ruler crosses noon or midnight.
 * Only transforms change per frame; React renders this once the glyphs are measured.
 */
import { memo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { type Frame, wheels } from './motion';
import { ASCENT, layout, type as T } from './theme';

const ROUNDED = Platform.select({ ios: 'ui-rounded', default: undefined });

const HOURS = ['12', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'];
const TENS = ['0', '1', '2', '3', '4', '5', '0'];
const ONES = ['0', '5', '0'];

const digitFont = {
  fontFamily: ROUNDED,
  fontSize: T.time.size,
  fontWeight: T.time.weight,
  color: T.time.color,
  letterSpacing: T.time.ls,
  includeFontPadding: false,
} as const;

type Metrics = { cellH: number; hourW: number[]; digitW: number };

function Wheel({ cells, value, cellH, width, align }: { cells: string[]; value: SharedValue<number>; cellH: number; width: number; align: 'flex-end' | 'center' }) {
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -value.get() * cellH }] }));
  return (
    <View style={{ width, height: cellH, overflow: 'hidden' }}>
      <Animated.View style={[{ alignItems: align }, strip]}>
        {cells.map((c, i) => (
          <Text key={i} allowFontScaling={false} style={[digitFont, { height: cellH, fontVariant: align === 'center' ? ['tabular-nums'] : undefined }]}>
            {c}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

type Props = {
  frame: SharedValue<Frame>;
  /** Top safe-area edge (pt). */
  top: number;
  still: boolean;
};

export const TimeOdometer = memo(function TimeOdometer({ frame, top, still }: Props) {
  const [m, setM] = useState<Metrics | null>(null);
  const probe = useRef({ cellH: 0, hourW: new Array<number>(12).fill(0), digitW: 0, seen: 0 });

  const w = useDerivedValue(() => wheels(frame.get().pos));
  const hour = useDerivedValue(() => w.get().hour);
  const tens = useDerivedValue(() => w.get().tens);
  const ones = useDerivedValue(() => w.get().ones);

  // Keep the visible time centred: the hour wheel is as wide as "12", its current cell may be narrower.
  const row = useAnimatedStyle(() => {
    if (!m) return {};
    const h = hour.get();
    const i = Math.floor(h);
    const cur = m.hourW[i % 12] + (m.hourW[(i + 1) % 12] - m.hourW[i % 12]) * (h - i);
    return { transform: [{ translateX: -(Math.max(...m.hourW) - cur) / 2 }] };
  });

  // AM / PM: two faces of one card. Reduce Motion crossfades instead of turning it.
  const pm = useAnimatedStyle(() => {
    const a = frame.get().flip;
    const c = Math.cos((a * Math.PI) / 180);
    if (still) return { opacity: Math.max(0, c), transform: [] };
    return { opacity: c > 0 ? 1 : 0, transform: [{ perspective: 260 }, { rotateX: `${-a}deg` }] };
  });
  const am = useAnimatedStyle(() => {
    const a = frame.get().flip;
    const c = Math.cos((a * Math.PI) / 180);
    if (still) return { opacity: Math.max(0, -c), transform: [] };
    return { opacity: c < 0 ? 1 : 0, transform: [{ perspective: 260 }, { rotateX: `${180 - a}deg` }] };
  });

  const measureHour = (i: number) => (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    probe.current.hourW[i] = width;
    if (i === 0) probe.current.cellH = height;
    probe.current.seen |= 1 << i;
    commit();
  };
  const measureDigit = (e: LayoutChangeEvent) => {
    probe.current.digitW = e.nativeEvent.layout.width;
    probe.current.seen |= 1 << 12;
    commit();
  };
  const commit = () => {
    const p = probe.current;
    if (p.seen === (1 << 13) - 1 && !m) setM({ cellH: p.cellH, hourW: [...p.hourW], digitW: p.digitW });
  };

  const meridiemTop = top + layout.meridiem.base - ASCENT * T.meridiem.size;
  const meridiemFont = { fontSize: T.meridiem.size, fontWeight: T.meridiem.weight, color: T.meridiem.color, letterSpacing: T.meridiem.ls } as const;

  return (
    <>
      {!m && (
        <View style={styles.probe} pointerEvents="none">
          {HOURS.slice(0, 12).map((h, i) => (
            <Text key={h + i} allowFontScaling={false} onLayout={measureHour(i)} style={digitFont}>
              {h}
            </Text>
          ))}
          <Text allowFontScaling={false} onLayout={measureDigit} style={[digitFont, { fontVariant: ['tabular-nums'] }]}>
            0
          </Text>
        </View>
      )}
      {m && (
        <Animated.View
          pointerEvents="none"
          importantForAccessibility="no-hide-descendants"
          accessibilityElementsHidden
          style={[styles.row, { top: top + layout.time.base - ASCENT * T.time.size, left: layout.time.dx, right: -layout.time.dx }, row]}
        >
          <Wheel cells={HOURS} value={hour} cellH={m.cellH} width={Math.max(...m.hourW)} align="flex-end" />
          <Text allowFontScaling={false} style={[digitFont, styles.colon]}>
            :
          </Text>
          <View style={styles.tight}>
            <Wheel cells={TENS} value={tens} cellH={m.cellH} width={m.digitW} align="center" />
          </View>
          <View style={styles.tight}>
            <Wheel cells={ONES} value={ones} cellH={m.cellH} width={m.digitW} align="center" />
          </View>
        </Animated.View>
      )}
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.meridiem, { top: meridiemTop }]}>
        <Animated.Text allowFontScaling={false} style={[meridiemFont, styles.face, pm]}>
          PM
        </Animated.Text>
        <Animated.Text allowFontScaling={false} style={[meridiemFont, styles.face, styles.back, am]}>
          AM
        </Animated.Text>
      </View>
    </>
  );
});

const styles = StyleSheet.create({
  probe: { position: 'absolute', left: 0, top: 0, opacity: 0, flexDirection: 'row', alignItems: 'flex-start' },
  row: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center' },
  colon: { marginLeft: layout.time.colonGap, transform: [{ translateY: -layout.time.colonRise }] },
  tight: { marginLeft: layout.time.tight },
  meridiem: { position: 'absolute', left: 0, right: 0, alignItems: 'center', transform: [{ translateX: layout.meridiem.dx }] },
  face: { textAlign: 'center', backfaceVisibility: 'hidden' },
  back: { position: 'absolute', top: 0 },
});

import { LinearGradient as Fill } from 'expo-linear-gradient';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue, interpolate, useAnimatedProps, useAnimatedStyle, useSharedValue, withSpring,
} from 'react-native-reanimated';
import Svg, { ClipPath, Defs, G, Image as SvgImage, LinearGradient, Path, Stop } from 'react-native-svg';

import { Caret, SeatGlyph } from './icons';
import { BUILD, FOOTAGE, PAY, REDUCED_FADE_MS, SEAT_POP } from './motion';
import { DATE_LABEL, SCREEN_STILL, SEAT_PRICE, SEAT_ROWS, TIMES } from './movies';
import { COLORS, SANS, SEATS } from './theme';

const AnimatedPath = Animated.createAnimatedComponent(Path);
const AnimatedG = Animated.createAnimatedComponent(G);
const AnimatedImage = Animated.createAnimatedComponent(SvgImage);

function ramp(t: number, at: number, ms: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - at) / ms));
}

/** The cinema screen: a still of the film bent onto a curved band, which then dims to a curved glint. */
function CinemaScreen({ width, clock, reduced }: { width: number; clock: SharedValue<number>; reduced: boolean }) {
  const x0 = SEATS.screenInset;
  const x1 = width - SEATS.screenInset;
  const h = SEATS.screenH;
  const bow = 26;
  const band = `M${x0} 0 Q${width / 2} ${bow} ${x1} 0 L${x1 - 18} ${h} Q${width / 2} ${h - bow * 0.55} ${x0 + 18} ${h} Z`;
  // The dimmed screen's glint, measured on the rectified pin: ends 156 pt, middle 144 pt.
  const glint = `M${x0 + 6} 44 Q${width / 2} 20 ${x1 - 6} 44`;
  const film = useAnimatedProps(() => {
    if (reduced) return { opacity: 0 };
    const t = clock.get() - BUILD.delay;
    return { opacity: ramp(t, FOOTAGE.inAt, FOOTAGE.inMs) * (1 - ramp(t, FOOTAGE.outAt, FOOTAGE.outMs)) };
  });
  const drift = useAnimatedProps(() => {
    // The footage "plays": a slow push-in while it is on the screen.
    if (reduced) return { x: x0, y: -6, width: x1 - x0, height: h + 12 };
    const t = clock.get() - BUILD.delay - FOOTAGE.inAt;
    const k = 1 + 0.08 * ramp(t, 0, FOOTAGE.drift);
    const w = (x1 - x0) * k;
    const hh = (h + 12) * k;
    return { x: x0 - (w - (x1 - x0)) / 2, y: -6 - (hh - h - 12) / 2, width: w, height: hh };
  });
  const line = useAnimatedProps(() => {
    if (reduced) return { opacity: ramp(clock.get(), 0, REDUCED_FADE_MS) };
    const t = clock.get() - BUILD.delay;
    return { opacity: ramp(t, FOOTAGE.outAt, FOOTAGE.outMs) };
  });
  return (
    <Svg width={width} height={h + 20} style={{ position: 'absolute', top: SEATS.screenTop, left: 0 }}>
      <Defs>
        <ClipPath id="band"><Path d={band} /></ClipPath>
        <LinearGradient id="glint" x1="0" y1="0" x2="1" y2="0">
          <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0} />
          <Stop offset="0.5" stopColor="#FFFFFF" stopOpacity={0.9} />
          <Stop offset="1" stopColor="#FFFFFF" stopOpacity={0.1} />
        </LinearGradient>
      </Defs>
      <AnimatedG animatedProps={film} clipPath="url(#band)">
        <AnimatedImage animatedProps={drift} href={SCREEN_STILL} preserveAspectRatio="xMidYMid slice" />
      </AnimatedG>
      <AnimatedPath animatedProps={line} d={glint} stroke="url(#glint)" strokeWidth={2.4} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

function SeatRow({ row, r, clock, picked, onSeat, left, reduced }: {
  row: string; r: number; clock: SharedValue<number>; picked: Set<string>; onSeat: (id: string) => void; left: number; reduced: boolean;
}) {
  const style = useAnimatedStyle(() => {
    const p = reduced ? ramp(clock.get(), 0, REDUCED_FADE_MS) : ramp(clock.get(), BUILD.delay + r * BUILD.rowStagger, BUILD.rowMs);
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 14 }] };
  });
  let x = left;
  const cells: React.ReactNode[] = [];
  for (let c = 0; c < row.length; c++) {
    const ch = row[c];
    if (ch === ' ') { x += SEATS.aisle; continue; }
    const id = `${r}-${c}`;
    cells.push(<Seat key={id} id={id} x={x} taken={ch === 'T'} picked={picked.has(id)} onSeat={onSeat} reduced={reduced} />);
    x += SEATS.pitchX;
  }
  return <Animated.View style={[StyleSheet.absoluteFill, { top: SEATS.gridTop + r * SEATS.pitchY, height: SEATS.pitchY }, style]}>{cells}</Animated.View>;
}

function Seat({ id, x, taken, picked, onSeat, reduced }: { id: string; x: number; taken: boolean; picked: boolean; onSeat: (id: string) => void; reduced: boolean }) {
  const pop = useSharedValue(1);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: pop.get() }] }));
  const color = taken ? COLORS.seatTaken : picked ? COLORS.seatPicked : COLORS.seatFree;
  return (
    <Pressable disabled={taken} hitSlop={3}
      accessibilityRole="button" accessibilityLabel={`Seat ${id}`} accessibilityState={{ selected: picked, disabled: taken }}
      onPress={() => { if (!reduced) { pop.set(0.7); pop.set(withSpring(1, SEAT_POP)); } onSeat(id); }}
      style={{ position: 'absolute', left: x, top: 0, width: SEATS.seat, height: SEATS.seat }}>
      <Animated.View style={style}><SeatGlyph size={SEATS.seat} color={color} /></Animated.View>
    </Pressable>
  );
}

export function SeatScreen({ width, clock, payClock, picked, onSeat, onPay, time, onTime, active, reduced }: {
  width: number; clock: SharedValue<number>; payClock: SharedValue<number>; picked: Set<string>;
  onSeat: (id: string) => void; onPay: () => void; time: number; onTime: (i: number) => void; active: boolean; reduced: boolean;
}) {
  const aisles = SEAT_ROWS[0].split(' ').length - 1;
  const gridW = SEAT_ROWS[0].replace(/ /g, '').length * SEATS.pitchX - (SEATS.pitchX - SEATS.seat) + aisles * SEATS.aisle;
  const left = (width - gridW) / 2;
  const chrome = useAnimatedStyle(() => {
    const p = ramp(clock.get(), BUILD.delay + BUILD.chromeAt, BUILD.chromeMs);
    return { opacity: p * (1 - ramp(payClock.get(), 0, PAY.blackAt)), transform: [{ translateY: reduced ? 0 : (1 - p) * 16 }] };
  });
  const grid = useAnimatedStyle(() => ({ opacity: 1 - ramp(payClock.get(), 0, PAY.blackAt) }));
  const pay = useAnimatedStyle(() => {
    const p = ramp(clock.get(), BUILD.delay + BUILD.chromeAt, BUILD.chromeMs);
    const s = reduced ? 0 : interpolate(ramp(payClock.get(), 0, PAY.shrinkMs), [0, 1], [0, 1]);
    const w = SEATS.payW + (SEATS.payH - SEATS.payW) * s;
    return {
      left: (width - w) / 2, width: w,
      opacity: p * (1 - ramp(payClock.get(), PAY.blackAt, PAY.blackMs)),
      borderRadius: 12 + (SEATS.payH / 2 - 12) * s,
      transform: [{ translateY: reduced ? 0 : (1 - p) * 20 }],
    };
  });
  const label = useAnimatedStyle(() => ({ opacity: 1 - ramp(payClock.get(), 0, PAY.shrinkMs * 0.5) }));
  const total = picked.size * SEAT_PRICE;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents={active ? 'box-none' : 'none'}
      accessibilityElementsHidden={!active}
      importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}>
      <Animated.View style={[StyleSheet.absoluteFill, grid]} pointerEvents="box-none">
        <CinemaScreen width={width} clock={clock} reduced={reduced} />
        {SEAT_ROWS.map((row, r) => <SeatRow key={r} row={row} r={r} clock={clock} picked={picked} onSeat={onSeat} left={left} reduced={reduced} />)}
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, chrome]} pointerEvents="box-none">
        <View style={[styles.legend, { top: SEATS.legendY - 8 }]}>
          {[['Available', COLORS.seatFree], ['Taken', COLORS.seatTaken], ['Selected', COLORS.seatPicked]].map(([name, c]) => (
            <View key={name} style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: c }]} />
              <Text style={styles.legendText}>{name}</Text>
            </View>
          ))}
        </View>
        <View style={[styles.dateRow, { top: SEATS.dateY - 14 }]}>
          <Pressable style={styles.date} accessibilityRole="button" accessibilityLabel={`Date ${DATE_LABEL}`}>
            <Text style={styles.dateText}>{DATE_LABEL}</Text>
            <Caret size={14} color={COLORS.paper} />
          </Pressable>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.times}>
            {TIMES.map((t, i) => (
              <Pressable key={t} onPress={() => onTime(i)} accessibilityRole="button" accessibilityState={{ selected: i === time }}
                style={[styles.time, i === time && styles.timeOn]} hitSlop={8}>
                <Text style={styles.timeText}>{t}</Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      </Animated.View>
      <Animated.View style={[styles.pay, { top: SEATS.payY - SEATS.payH / 2 }, pay]}>
        <Fill colors={[COLORS.red, COLORS.redHot]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
        <Pressable style={styles.payHit} onPress={onPay} pressRetentionOffset={16} accessibilityRole="button" accessibilityLabel={total ? `Pay $${total}` : 'Pay'}>
          <Animated.Text style={[styles.payText, label]} numberOfLines={1}>{total ? `PAY $${total}` : 'PAY'}</Animated.Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  legend: { position: 'absolute', left: 64, right: 64, flexDirection: 'row', justifyContent: 'space-between' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  legendDot: { width: 6, height: 6, borderRadius: 3 },
  legendText: { fontFamily: SANS.regular, fontSize: 9.5, color: COLORS.legend },
  dateRow: { position: 'absolute', left: 64, right: 0, height: 28, flexDirection: 'row', alignItems: 'center' },
  date: { flexDirection: 'row', alignItems: 'center', gap: 4, marginRight: 22 },
  dateText: { fontFamily: SANS.medium, fontSize: 13, color: COLORS.paper },
  times: { alignItems: 'center', gap: 14, paddingRight: 24 },
  time: { height: 22, paddingHorizontal: 8, justifyContent: 'center', borderRadius: 2 },
  timeOn: { backgroundColor: COLORS.red },
  timeText: { fontFamily: SANS.medium, fontSize: 11.5, color: COLORS.paper },
  pay: { position: 'absolute', height: SEATS.payH, backgroundColor: COLORS.red, overflow: 'hidden' },
  payHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  payText: { fontFamily: SANS.semibold, fontSize: 12, letterSpacing: 0.8, color: COLORS.paper },
});

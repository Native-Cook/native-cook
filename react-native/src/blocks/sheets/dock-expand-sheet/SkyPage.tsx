import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Defs, Path, Stop, LinearGradient as SvgGradient } from 'react-native-svg';

const NIGHT = '#05060a';
const SKY = ['#0c1d45', '#173a72', '#2f6fa8', '#6fb7d2', '#a9d3dc'] as const;
const SKY_STOPS = [0, 0.38, 0.72, 0.94, 1] as const;
const GLOW = ['rgba(79,169,201,0)', '#4fa9c9', '#e8b56a', 'rgba(217,162,91,0)'] as const;
const TICK = '#2c3850';
const TICK_LIT = '#5aa9ff';
const LABEL = 'rgba(255,255,255,0.42)';
const VALUE = 'rgba(255,255,255,0.62)';
const TICKS = 41;
const SUN = 16;
const ROW = 52;
const HOURS = ['6 AM', 'Noon', '6 PM', '12 AM', '6 AM'];

const ICONS: (keyof typeof Ionicons.glyphMap)[] = ['sunny-outline', 'aperture-outline', 'partly-sunny-outline', 'moon-outline'];

type Props = {
  width: number;
  /** Where the sky ends and the horizon glows, from the top of the screen. */
  horizon: number;
  /** Top of the ruler of ticks, from the top of the screen. */
  rulerTop: number;
  /** 0 → 1: the ticks of the ruler light up from left to right. */
  ruler: SharedValue<number>;
  /** Rows under the ruler: a label and a time each. */
  times: string[][];
};

/**
 * The page behind the dock: a dawn sky with the sun on the arc it travels, a glow along the
 * horizon, a ruler of hours and the day's times under it.
 */
export function SkyPage({ width, horizon, rulerTop, ruler, times }: Props) {
  // The sun's path: a bell from below the horizon on the left, over the sun, down again on the right.
  const peak = horizon - 118;
  const low = horizon + 70;
  const arc = `M-30 ${low} C${width * 0.12} ${low} ${width * 0.26} ${peak} ${width / 2} ${peak} S${width * 0.88} ${low} ${width + 30} ${low}`;
  return (
    <View style={StyleSheet.absoluteFill}>
      <View style={[StyleSheet.absoluteFill, { backgroundColor: NIGHT }]} />
      <LinearGradient colors={SKY} locations={SKY_STOPS} style={{ position: 'absolute', left: 0, right: 0, top: 0, height: horizon }} />
      {/* The first light, held just over the horizon. */}
      <LinearGradient
        colors={['rgba(232,181,106,0)', 'rgba(232,181,106,0.28)']}
        style={{ position: 'absolute', left: 0, right: 0, top: horizon - 60, height: 60 }}
      />
      <LinearGradient colors={GLOW} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={{ position: 'absolute', left: 0, right: 0, top: horizon - 1, height: 2 }} />
      <LinearGradient colors={['rgba(79,169,201,0.18)', 'rgba(5,6,10,0)']} style={{ position: 'absolute', left: 0, right: 0, top: horizon + 1, height: 70 }} />

      <Svg width={width} height={low + 20} style={StyleSheet.absoluteFill}>
        <Defs>
          <SvgGradient id="arc" x1="0" y1="0" x2="0" y2="1">
            <Stop offset="0" stopColor="#ffffff" stopOpacity={0.4} />
            <Stop offset={String((horizon - peak) / (low - peak + 20))} stopColor="#ffffff" stopOpacity={0.2} />
            <Stop offset="1" stopColor="#ffffff" stopOpacity={0.04} />
          </SvgGradient>
        </Defs>
        <Path d={arc} stroke="url(#arc)" strokeWidth={1} fill="none" />
      </Svg>

      <View style={[styles.halo, { left: width / 2 - 36, top: peak - 36, width: 72, height: 72, borderRadius: 36 }]} />
      <View style={[styles.halo, { left: width / 2 - 20, top: peak - 20, width: 40, height: 40, borderRadius: 20, opacity: 0.22 }]} />
      <View style={[styles.sun, { left: width / 2 - SUN / 2, top: peak - SUN / 2 }]} />

      <View style={[styles.ruler, { top: rulerTop, width: width - 40 }]}>
        {Array.from({ length: TICKS }, (_, index) => (
          <Tick key={index} index={index} ruler={ruler} />
        ))}
      </View>
      <View style={[styles.hours, { top: rulerTop + 26, width: width - 40 }]}>
        {HOURS.map((hour, index) => (
          <Text key={index} style={styles.hour}>
            {hour}
          </Text>
        ))}
      </View>

      <View style={[styles.rows, { top: rulerTop + 58, width: width - 40 }]}>
        {times.map(([label, time], index) => (
          <View key={label} style={[styles.row, index > 0 && styles.rule]}>
            <View style={styles.labelGroup}>
              <Ionicons name={ICONS[index % ICONS.length]} size={16} color={LABEL} />
              <Text style={styles.label}>{label}</Text>
            </View>
            <Text style={styles.value}>{time}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

function Tick({ index, ruler }: { index: number; ruler: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, ruler.get() * (TICKS + 4) - index)) }));
  const major = index % 10 === 0;
  return (
    <View style={[styles.tick, major && styles.major]}>
      <Animated.View style={[styles.tickLit, style]} />
    </View>
  );
}

const styles = StyleSheet.create({
  halo: { position: 'absolute', backgroundColor: '#ffffff', opacity: 0.08 },
  sun: { position: 'absolute', width: SUN, height: SUN, borderRadius: SUN / 2, backgroundColor: '#ffffff', shadowColor: '#ffffff', shadowOpacity: 0.9, shadowRadius: 10, shadowOffset: { width: 0, height: 0 } },
  ruler: { position: 'absolute', left: 20, height: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  tick: { width: 2, height: 12, borderRadius: 1, backgroundColor: TICK, overflow: 'hidden' },
  major: { height: 20, backgroundColor: '#3b4a68' },
  tickLit: { flex: 1, borderRadius: 1, backgroundColor: TICK_LIT },
  hours: { position: 'absolute', left: 20, flexDirection: 'row', justifyContent: 'space-between' },
  hour: { fontSize: 11, lineHeight: 14, fontWeight: '600', letterSpacing: 0.4, color: 'rgba(255,255,255,0.3)' },
  rows: { position: 'absolute', left: 20 },
  row: { height: ROW, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  rule: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(255,255,255,0.08)' },
  labelGroup: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  label: { fontSize: 15, lineHeight: 20, fontWeight: '500', color: LABEL },
  value: { fontSize: 22, lineHeight: 28, fontWeight: '600', letterSpacing: -0.4, color: VALUE, fontVariant: ['tabular-nums'] },
});

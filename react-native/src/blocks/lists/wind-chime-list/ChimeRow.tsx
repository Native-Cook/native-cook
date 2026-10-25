import { LinearGradient } from 'expo-linear-gradient';
import { memo, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Extrapolation, type SharedValue, interpolate, useAnimatedStyle } from 'react-native-reanimated';

import { RING_LEN, ROW_H, STRING_H, SWAY, type Session, TUBE_H, VISUAL_TURN, barHeight, playEnvelope } from './windChimeMotion';

/*
 Wind Chime List — the list header (title and the oak beam) and one row: a brass or bamboo tube
 on two strings, swinging about the strings' top, with its contact ring and its waveform.
*/

const BARS = 16;

/** Large title, the oak beam the chime hangs from. Scrolls away with a little parallax. */
export const ListHeader = memo(function ListHeader({ insetTop, W, scrollY }: { insetTop: number; W: number; scrollY: SharedValue<number> }) {
  const titleStyle = useAnimatedStyle(() => ({
    opacity: interpolate(scrollY.get(), [0, 90], [1, 0], Extrapolation.CLAMP),
    transform: [{ translateY: interpolate(scrollY.get(), [-100, 0, 120], [-30, 0, 40], Extrapolation.CLAMP) }],
  }));
  return (
    <View style={{ height: insetTop + 168, paddingTop: insetTop + 20, paddingHorizontal: 24 }}>
      <Animated.View style={[{ gap: 4 }, titleStyle]}>
        <Text style={styles.kicker}>STILLWATER</Text>
        <Text style={styles.title}>Sound baths</Text>
        <Text style={styles.sub}>14 sessions · tuned to 432 Hz · tap a tube to hear it</Text>
      </Animated.View>
      <View style={[styles.beam, { width: W - 32 }]}>
        <LinearGradient colors={['#3c2412', '#8a5a31', '#b98352', '#7a4b26', '#2e1a0b']} locations={[0, 0.2, 0.45, 0.75, 1]} style={StyleSheet.absoluteFill} />
        {[0.12, 0.31, 0.55, 0.78].map((x) => (
          <View key={x} style={[styles.beamGrain, { left: `${x * 100}%` }]} />
        ))}
      </View>
    </View>
  );
});

type RowProps = {
  item: Session;
  index: number;
  W: number;
  swing: SharedValue<number[]>;
  rings: SharedValue<number[]>;
  play: SharedValue<number[]>;
  playing: boolean;
  onStrike: (i: number) => void;
};

const BRASS = ['#4f3512', '#a87a35', '#f1d690', '#d8ad5c', '#8a6025', '#3a2709'] as const;
const BAMBOO = ['#3f4a1e', '#7f8f45', '#cfd68f', '#a7b064', '#5e6a2c', '#2d3513'] as const;
const STOPS = [0, 0.16, 0.34, 0.52, 0.82, 1] as const;

/** One session: a tube on two strings, swinging about the strings' top. */
export const ChimeRow = memo(function ChimeRow({ item, index, W, swing, rings, play, playing, onStrike }: RowProps) {
  const tubeW = W - 32 - item.shrink * 2;
  const ink = item.brass ? '#2a1a07' : '#1d2408';
  const swingStyle = useAnimatedStyle(() => {
    const a = swing.get()[index] ?? 0;
    return {
      transform: [{ translateX: SWAY * a }, { translateY: -ROW_H / 2 }, { rotate: `${VISUAL_TURN * a}rad` }, { translateY: ROW_H / 2 }],
    };
  });
  const ringStyle = useAnimatedStyle(() => {
    const age = rings.get()[index * 2] ?? -1;
    const side = rings.get()[index * 2 + 1] ?? 1;
    if (age < 0) return { opacity: 0, transform: [{ translateX: 0 }, { scale: 0.5 }] };
    const u = age / RING_LEN;
    return { opacity: 0.85 * (1 - u) * (1 - u), transform: [{ translateX: side * (tubeW / 2 - 26) }, { scale: 0.5 + 1.8 * u }] };
  });
  const glowStyle = useAnimatedStyle(() => {
    const p = play.get();
    return { opacity: p[0] === index ? 0.55 * playEnvelope(p[1]) : 0 };
  });
  const bars = useMemo(() => Array.from({ length: BARS }, (_, k) => barHeight(item.seed, k)), [item.seed]);
  return (
    <View style={styles.row}>
      <Animated.View style={[styles.swing, swingStyle]}>
        <View style={[styles.string, { left: (W - tubeW) / 2 + tubeW * 0.24 }]} />
        <View style={[styles.string, { left: (W - tubeW) / 2 + tubeW * 0.76 }]} />
        <Pressable onPress={() => onStrike(index)} accessibilityRole="button" accessibilityLabel={`${item.title}, ${item.minutes} minutes, ${item.meta}`}>
          <View style={[styles.tube, { width: tubeW }]}>
            <LinearGradient colors={item.brass ? BRASS : BAMBOO} locations={STOPS} style={StyleSheet.absoluteFill} />
            <LinearGradient
              colors={['rgba(0,0,0,0.38)', 'rgba(0,0,0,0)', 'rgba(255,255,255,0.06)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0.38)']}
              locations={[0, 0.07, 0.5, 0.93, 1]}
              start={{ x: 0, y: 0.5 }}
              end={{ x: 1, y: 0.5 }}
              style={StyleSheet.absoluteFill}
            />
            {!item.brass ? <View style={[styles.node, { left: tubeW * 0.36 }]} /> : null}
            {!item.brass ? <View style={[styles.node, { left: tubeW * 0.7 }]} /> : null}
            <Animated.View style={[StyleSheet.absoluteFill, styles.playGlow, glowStyle]} />
            <View style={styles.tubeText}>
              <Text style={[styles.rowTitle, { color: ink }]} numberOfLines={1}>
                {item.title}
              </Text>
              <Text style={[styles.rowMeta, { color: ink }]} numberOfLines={1}>
                {item.minutes} min · {item.meta}
              </Text>
            </View>
            <View style={styles.wave}>
              {playing ? (
                <ActiveWave play={play} index={index} bars={bars} ink={ink} />
              ) : (
                bars.map((h, k) => <View key={k} style={[styles.bar, { height: 4 + 22 * h, backgroundColor: ink }]} />)
              )}
            </View>
          </View>
        </Pressable>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.ring, { left: W / 2 - 22 }, ringStyle]} />
    </View>
  );
});

/** The playing tube's waveform: each bar dances on a few whole-number sines of the time since the strike. */
function ActiveWave({ play, index, bars, ink }: { play: SharedValue<number[]>; index: number; bars: number[]; ink: string }) {
  return (
    <>
      {bars.map((h, k) => (
        <WaveBar key={k} play={play} index={index} k={k} h={h} ink={ink} />
      ))}
    </>
  );
}

function WaveBar({ play, index, k, h, ink }: { play: SharedValue<number[]>; index: number; k: number; h: number; ink: string }) {
  const style = useAnimatedStyle(() => {
    const p = play.get();
    const tau = p[0] === index ? p[1] : 0;
    const env = playEnvelope(tau);
    const dance = 0.5 + 0.5 * Math.abs(Math.sin(tau * (7 + k * 0.9) + k) * 0.7 + Math.sin(tau * (13 + (k % 4) * 2) + k * 2.1) * 0.3);
    return { transform: [{ scaleY: 1 + env * (dance * 1.6 - 0.2) }] };
  });
  return <Animated.View style={[styles.bar, { height: 4 + 22 * h, backgroundColor: ink }, style]} />;
}


const styles = StyleSheet.create({
  kicker: { color: '#d9b56a', fontSize: 11, fontWeight: '700', letterSpacing: 3 },
  title: { color: '#eef3ee', fontSize: 34, fontWeight: '700', letterSpacing: -0.5 },
  sub: { color: 'rgba(238,243,238,0.6)', fontSize: 15, fontWeight: '400' },
  beam: {
    position: 'absolute',
    left: 16,
    bottom: 0,
    height: 18,
    borderRadius: 6,
    borderCurve: 'continuous',
    overflow: 'hidden',
    boxShadow: '0 2px 3px rgba(0,0,0,0.45), 0 12px 24px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,220,170,0.35)',
  },
  beamGrain: { position: 'absolute', top: 3, width: 40, height: 1, backgroundColor: 'rgba(40,20,6,0.35)' },
  row: { height: ROW_H },
  swing: { height: ROW_H, paddingTop: STRING_H, alignItems: 'center' },
  string: { position: 'absolute', top: -2, width: 1.2, height: STRING_H + 4, backgroundColor: 'rgba(226,206,160,0.55)' },
  tube: {
    height: TUBE_H,
    borderRadius: TUBE_H / 2,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    gap: 12,
    boxShadow: '0 2px 3px rgba(0,0,0,0.45), 0 14px 26px rgba(0,0,0,0.38), inset 0 1px 0 rgba(255,250,225,0.5)',
  },
  node: { position: 'absolute', top: 0, bottom: 0, width: 3, backgroundColor: 'rgba(40,48,12,0.35)', borderRightWidth: 1, borderRightColor: 'rgba(255,255,220,0.2)' },
  playGlow: { backgroundColor: 'rgba(255,240,190,0.55)' },
  tubeText: { flex: 1, gap: 2 },
  rowTitle: { fontSize: 16, fontWeight: '600' },
  rowMeta: { fontSize: 13, fontWeight: '500', opacity: 0.72, fontVariant: ['tabular-nums'] },
  wave: { flexDirection: 'row', alignItems: 'center', gap: 2.5, height: 40 },
  bar: { width: 2.5, borderRadius: 1.25, opacity: 0.7 },
  ring: {
    position: 'absolute',
    top: ROW_H + STRING_H / 2 - 22,
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: 'rgba(255,226,150,0.95)',
    boxShadow: '0 0 14px rgba(255,210,120,0.8), inset 0 0 10px rgba(255,210,120,0.6)',
  },
});

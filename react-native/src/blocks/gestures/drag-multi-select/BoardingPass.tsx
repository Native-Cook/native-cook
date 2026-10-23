/**
 * The end of a download: a boarding pass for the episodes, in a dot-matrix face like a departures board. It rises from
 * the button on a spring while the scrim comes up under it, and leaves faster than it came (tap anywhere). It is a
 * receipt, not a modal: it carries no new action, only what is now on the phone for the flight.
 */
import { Canvas, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { EASE_OUT, PASS } from './motion';
import { COLOR, TYPE, type Layout } from './theme';

const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const SIDE = 28;
const HEIGHT = 560;
const PERF = 392;
const GLOW = 220;
const DOTS = Array.from({ length: 34 }, (_, k) => k);

type Props = { lay: Layout; open: boolean; episodes: number; minutes: string; size: string; reduced: boolean; onClose: () => void };

export function BoardingPass({ lay, open, episodes, minutes, size, reduced, onClose }: Props) {
  const p = useSharedValue(0); // the card's travel: 0 below, 1 in place
  const a = useSharedValue(0); // presence: scrim and card opacity
  useEffect(() => {
    if (open) {
      a.set(withTiming(1, { duration: PASS.scrimIn, easing: easeOut }));
      p.set(reduced ? 1 : withSpring(1, PASS.rise));
    } else {
      a.set(withTiming(0, { duration: PASS.out, easing: easeOut }));
      p.set(withTiming(reduced ? 1 : 0.85, { duration: PASS.out, easing: easeOut }));
    }
  }, [a, open, p, reduced]);

  const scrim = useAnimatedStyle(() => ({ opacity: a.get() }));
  const card = useAnimatedStyle(() => {
    const t = p.get();
    // Coming in it rises from the button; going out it only drops a little while it fades.
    const y = open ? (1 - t) * PASS.from : (1 - t) * (PASS.drop / 0.15);
    return { opacity: Math.min(1, a.get() * 1.4), transform: [{ translateY: y }, { scale: PASS.scale + (1 - PASS.scale) * Math.min(t, 1) }] };
  });

  const width = lay.width - SIDE * 2;
  const top = lay.safeTop + 52;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents={open ? 'auto' : 'none'}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, scrim]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="Close boarding pass" />
      </Animated.View>
      <Animated.View style={[styles.card, { top, width, left: SIDE }, card]} accessible accessibilityViewIsModal
        accessibilityLabel={`Ready for takeoff. ${episodes} episodes, ${minutes} offline, ${size} on this iPhone. United 1514, San Francisco to New York.`}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <Canvas style={[styles.glow, { width, height: GLOW }]} pointerEvents="none">
          <Rect x={0} y={0} width={width} height={GLOW}>
            <RadialGradient c={vec(width / 2, GLOW * 1.15)} r={GLOW * 1.05} colors={[COLOR.orange, COLOR.violet, 'rgba(165,123,255,0)']} positions={[0, 0.5, 1]} />
          </Rect>
        </Canvas>
        <View style={styles.head} pointerEvents="none">
          {[['FLIGHT', 'UA 1514'], ['SEAT', '23A'], ['BOARDING', '8:25 AM'], ['DATE', '23 Oct']].map(([k, v]) => (
            <View key={k}>
              <Text style={styles.key}>{k}</Text>
              <Text style={styles.value}>{v}</Text>
            </View>
          ))}
        </View>
        <Text style={[styles.code, { marginTop: 40 }]} pointerEvents="none">SFO</Text>
        <Text style={styles.city} pointerEvents="none">San Francisco International</Text>
        <Text style={[styles.code, { marginTop: 24 }]} pointerEvents="none">JFK</Text>
        <Text style={styles.city} pointerEvents="none">John F. Kennedy International</Text>
        <View style={[styles.perf, { top: PERF }]} pointerEvents="none">
          {DOTS.map((k) => <View key={k} style={styles.perfDot} />)}
        </View>
        <View style={[styles.notch, { top: PERF - 11, left: -11 }]} />
        <View style={[styles.notch, { top: PERF - 11, right: -11 }]} />
        <View style={[styles.foot, { top: PERF + 28 }]} pointerEvents="none">
          <View>
            <Text style={styles.show}>SECOND DRAFT</Text>
            <Text style={styles.note}>{`${episodes} episodes · ${minutes} offline\n${size} on this iPhone`}</Text>
          </View>
          <View style={styles.chip}>
            <Text style={styles.chipText}>Ready for takeoff</Text>
          </View>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  scrim: { backgroundColor: COLOR.scrim },
  card: {
    position: 'absolute', height: HEIGHT, borderRadius: 28, borderCurve: 'continuous', backgroundColor: COLOR.pass,
    borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.passEdge,
    shadowColor: '#000', shadowOpacity: 0.6, shadowRadius: 40, shadowOffset: { width: 0, height: 24 },
  },
  glow: { position: 'absolute', left: 0, bottom: 0, borderBottomLeftRadius: 28, borderBottomRightRadius: 28, overflow: 'hidden' },
  head: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 22, paddingTop: 22 },
  key: { ...TYPE.passKey, color: COLOR.passMute },
  value: { ...TYPE.passValue, color: COLOR.ink, marginTop: 4 },
  code: { ...TYPE.passCode, color: COLOR.ink, textAlign: 'center' },
  city: { ...TYPE.passCity, color: COLOR.passMute, textAlign: 'center', marginTop: 4 },
  perf: { position: 'absolute', left: 16, right: 16, height: 2, flexDirection: 'row', justifyContent: 'space-between' },
  perfDot: { width: 2, height: 2, borderRadius: 1, backgroundColor: 'rgba(255,255,255,0.32)' },
  notch: { position: 'absolute', width: 22, height: 22, borderRadius: 11, backgroundColor: '#050505' },
  foot: { position: 'absolute', left: 22, right: 22, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  show: { ...TYPE.passShow, color: COLOR.ink },
  note: { ...TYPE.passNote, color: COLOR.passText, marginTop: 4 },
  chip: { height: 28, borderRadius: 14, paddingHorizontal: 12, backgroundColor: COLOR.chip, justifyContent: 'center' },
  chipText: { ...TYPE.chip, color: COLOR.chipInk },
});

import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { cubicBezier, useAnimatedReaction, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
/** When the hint shows and for how long. */
const SHOW_AFTER = 1600;
const SHOW_FOR = 4200;

/**
 * The two hidden gestures, named once: a quiet pill under the stage that appears after the first
 * look, leaves on its own, and never comes back once the person has touched the specimen.
 */
export function HabitatHint({ top, scale, reduced, touched }: { top: number; scale: number; reduced: boolean; touched: SharedValue<boolean> }) {
  const [phase, setPhase] = useState<'waiting' | 'shown' | 'gone'>('waiting');
  useEffect(() => {
    if (phase === 'gone') return;
    const timer = setTimeout(() => setPhase(phase === 'waiting' ? 'shown' : 'gone'), phase === 'waiting' ? SHOW_AFTER : SHOW_FOR);
    return () => clearTimeout(timer);
  }, [phase]);
  useAnimatedReaction(() => touched.get(), (now, before) => { if (now && !before) scheduleOnRN(setPhase, 'gone'); });
  const shown = phase === 'shown';
  return <View pointerEvents="none" style={[styles.row, { top }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[styles.pill, { height: 22 * scale, paddingHorizontal: 10 * scale, gap: 6 * scale, borderRadius: 11 * scale }, {
      opacity: shown ? 1 : 0,
      transform: [{ translateY: shown || reduced ? 0 : 4 * scale }, { scale: shown || reduced ? 1 : 0.96 }],
      transitionProperty: ['opacity', 'transform'], transitionDuration: shown ? 300 : 200, transitionTimingFunction: EASE_OUT,
    }]}>
      <MaterialCommunityIcons name="gesture-tap" size={12 * scale} color="#D7E3D2" />
      <Text style={[styles.text, { fontSize: 10 * scale }]}>Tap for its move</Text>
      <View style={[styles.dot, { width: 3 * scale, height: 3 * scale, borderRadius: 1.5 * scale }]} />
      <MaterialCommunityIcons name="gesture-tap-hold" size={12 * scale} color="#D7E3D2" />
      <Text style={[styles.text, { fontSize: 10 * scale }]}>Hold to pet</Text>
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  row: { position: 'absolute', left: 0, right: 0, alignItems: 'center', zIndex: 11 },
  pill: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#0B141199', borderWidth: StyleSheet.hairlineWidth, borderColor: '#D4E4DA2E' },
  text: { color: '#D7E3D2', fontWeight: '500', letterSpacing: 0.2 },
  dot: { backgroundColor: '#D7E3D266', marginHorizontal: 2 },
});

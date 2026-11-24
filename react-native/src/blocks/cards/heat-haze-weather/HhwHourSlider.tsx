// Heat Haze Weather — the hour slider.
//
// Drawn rather than native: its thumb follows the hour on the UI thread every frame (the film and the
// finger's momentum both move it), which a native slider could only do through React state. A track
// that warms and cools along the afternoon, hour ticks, a thumb with a sun in it and a bubble with the
// time, rounded to the half hour. The ghost finger of the autoplay sits on the thumb.

import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { type GestureType, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import { LinearGradient } from 'expo-linear-gradient';

import { H_MAX, H_MIN, THUMB, clamp01, rolled } from './hhw-motion';

const MUTE = 'rgba(251,246,238,0.66)';
const BUBBLE = 76;
const GHOST = 44;
/** half hours from H_MIN to H_MAX */
const STEPS = Array.from({ length: (H_MAX - H_MIN) * 2 + 1 }, (_, i) => H_MIN + i / 2);
const TICKS = Array.from({ length: H_MAX - H_MIN + 1 }, (_, i) => i);
const MARKS = [12, 15, 18, 21];

function clock(h: number) {
  const whole = Math.floor(h);
  const hour12 = whole % 12 === 0 ? 12 : whole % 12;
  return `${hour12}:${h - whole >= 0.5 ? '30' : '00'} ${whole < 12 ? 'AM' : 'PM'}`;
}

function mark(h: number) {
  return `${h % 12 === 0 ? 12 : h % 12} ${h < 12 ? 'AM' : 'PM'}`;
}

type Props = {
  hour: SharedValue<number>;
  trackW: SharedValue<number>;
  ghostA: SharedValue<number>;
  ghostP: SharedValue<number>;
  gesture: GestureType;
  /** The half hour VoiceOver reads as the slider's value. */
  spoken: number;
};

function StepLabel({ hour, index, label }: { hour: SharedValue<number>; index: number; label: string }) {
  const style = useAnimatedStyle(() => {
    // the nearest half hour, rolling to the next one over the last part of each step
    // (held to the track's ends, so the bubble keeps 12 PM or 9 PM while the thumb is rubber-banded past them)
    const at = rolled((Math.min(Math.max(hour.get(), H_MIN), H_MAX) - H_MIN) * 2 + 0.5, 0.65);
    const d = index - at;
    return { opacity: clamp01(1 - Math.abs(d)), transform: [{ translateY: 9 * d }] };
  });
  return <Animated.Text style={[styles.bubbleText, style]}>{label}</Animated.Text>;
}

export function HhwHourSlider({ hour, trackW, ghostA, ghostP, gesture, spoken }: Props) {
  const onLayout = (e: LayoutChangeEvent) => trackW.set(e.nativeEvent.layout.width);

  const thumb = useAnimatedStyle(() => {
    const w = trackW.get();
    const u = (hour.get() - H_MIN) / (H_MAX - H_MIN);
    return { transform: [{ translateX: u * w - THUMB / 2 }] };
  });
  const bubble = useAnimatedStyle(() => {
    const w = trackW.get();
    const u = (hour.get() - H_MIN) / (H_MAX - H_MIN);
    // the bubble stays inside the track while the thumb reaches the ends
    const x = Math.min(Math.max(u * w - BUBBLE / 2, -10), w - BUBBLE + 10);
    return { transform: [{ translateX: x }] };
  });
  const sunDot = useAnimatedStyle(() => {
    const u = clamp01((hour.get() - H_MIN) / (H_MAX - H_MIN));
    return { backgroundColor: u < 0.55 ? '#FFC85A' : u < 0.8 ? '#FF9A57' : '#E8708A' };
  });
  const ghost = useAnimatedStyle(() => {
    const w = trackW.get();
    const u = (hour.get() - H_MIN) / (H_MAX - H_MIN);
    return {
      opacity: ghostA.get() * 0.9,
      transform: [{ translateX: u * w - GHOST / 2 }, { scale: 1 - 0.14 * ghostP.get() }],
    };
  });

  return (
    <View>
      <View style={styles.bubbleRow} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Animated.View style={[styles.bubble, bubble]}>
          {STEPS.map((h, i) => (
            <StepLabel key={h} hour={hour} index={i} label={clock(h)} />
          ))}
        </Animated.View>
      </View>
      <GestureDetector gesture={gesture}>
        <View style={styles.hit} onLayout={onLayout} accessibilityRole="adjustable" accessibilityLabel="Hour" accessibilityValue={{ text: clock(spoken) }}>
          <LinearGradient
            colors={['#FFD36B', '#FF9F5A', '#E8708A', '#5D5A9E']}
            locations={[0, 0.38, 0.72, 1]}
            start={{ x: 0, y: 0.5 }}
            end={{ x: 1, y: 0.5 }}
            style={styles.track}
          />
          <View style={styles.ticks} pointerEvents="none">
            {TICKS.map((i) => (
              <View key={i} style={[styles.tick, i % 3 === 0 && styles.tickMajor]} />
            ))}
          </View>
          <Animated.View pointerEvents="none" style={[styles.thumb, thumb]}>
            <Animated.View style={[styles.sun, sunDot]} />
          </Animated.View>
          <Animated.View pointerEvents="none" style={[styles.ghost, ghost]}>
            <View style={styles.ghostCore} />
          </Animated.View>
        </View>
      </GestureDetector>
      <View style={styles.marks}>
        {MARKS.map((h) => (
          <Text key={h} style={styles.mark}>
            {mark(h)}
          </Text>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bubbleRow: { height: 30 },
  bubble: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BUBBLE,
    height: 26,
    borderRadius: 13,
    backgroundColor: 'rgba(251,246,238,0.94)',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    boxShadow: '0 1px 2px rgba(20,12,8,0.25), 0 6px 16px rgba(20,12,8,0.18)',
  },
  bubbleText: { position: 'absolute', color: '#2A1F1A', fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] },
  hit: { height: 36, justifyContent: 'center' },
  track: { height: 6, borderRadius: 3 },
  ticks: { position: 'absolute', left: 0, right: 0, top: 24, flexDirection: 'row', justifyContent: 'space-between' },
  tick: { width: 1, height: 4, backgroundColor: 'rgba(251,246,238,0.35)' },
  tickMajor: { height: 6, backgroundColor: 'rgba(251,246,238,0.6)' },
  thumb: {
    position: 'absolute',
    left: 0,
    top: (36 - THUMB) / 2,
    width: THUMB,
    height: THUMB,
    borderRadius: THUMB / 2,
    backgroundColor: '#FBF6EE',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(20,12,8,0.35), 0 8px 18px rgba(20,12,8,0.28)',
  },
  sun: { width: 12, height: 12, borderRadius: 6 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: (36 - GHOST) / 2,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(251,246,238,0.16)',
    borderWidth: 1.5,
    borderColor: 'rgba(251,246,238,0.62)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(251,246,238,0.5)' },
  marks: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 },
  mark: { color: MUTE, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
});

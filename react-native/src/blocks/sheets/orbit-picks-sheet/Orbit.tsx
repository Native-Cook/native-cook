import { Ionicons } from '@expo/vector-icons';
import { StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle } from 'react-native-svg';

import { INDIGO } from './Badge';
import {
  DISC_FLY,
  DISC_IN,
  DISC_STAGGER,
  PERIOD,
  RING_DRAW,
  RING_IN,
  RING_OUT,
  SUCK_AT,
  SUCK_FOR,
  SUCK_STAGGER,
  easeIn,
  easeOut,
  ramp,
} from './motion';

export type DiscSpec = {
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
  size: number;
  /** Distance from the badge, in points. */
  radius: number;
  /** Turns per PERIOD. */
  speed: number;
  /** Where on its orbit it starts, in radians. */
  phase: number;
};

// Twelve discs, small and large, on orbits between 90 and 170 points.
export const DISCS: DiscSpec[] = [
  { icon: 'tennisball', color: '#1fc760', size: 40, radius: 150, speed: 1, phase: 0.4 },
  { icon: 'hardware-chip', color: '#f2994a', size: 32, radius: 170, speed: 0.8, phase: 1.5 },
  { icon: 'car', color: '#e5384a', size: 44, radius: 110, speed: 1.2, phase: 2.6 },
  { icon: 'paw', color: '#1fc760', size: 26, radius: 100, speed: 1.1, phase: 3.7 },
  { icon: 'game-controller', color: '#3b82f6', size: 22, radius: 135, speed: 0.9, phase: 4.8 },
  { icon: 'musical-note', color: '#e5384a', size: 24, radius: 160, speed: 1.3, phase: 5.6 },
  { icon: 'sparkles', color: '#f5c518', size: 38, radius: 120, speed: 1, phase: 1.0 },
  { icon: 'bicycle', color: '#26c6da', size: 24, radius: 170, speed: 0.85, phase: 2.2 },
  { icon: 'pizza', color: '#f2994a', size: 30, radius: 95, speed: 1.25, phase: 3.1 },
  { icon: 'heart', color: '#f2547d', size: 26, radius: 145, speed: 1.15, phase: 4.2 },
  { icon: 'planet', color: '#26c6da', size: 34, radius: 165, speed: 0.95, phase: 5.2 },
  { icon: 'football', color: '#f5c518', size: 28, radius: 125, speed: 1.05, phase: 0.1 },
];

// The orbits are ellipses, wider than tall.
const SQUASH = 0.85;
const TURN = Math.PI * 2;
const PULSE = 0.08;
const SPIRAL_IN = 1.4;
const FAR = 2.4;

/** One icon disc: it flies in from outside along a spiral, circles the badge, and is pulled into it. */
export function Disc({ spec, index, clock, cx, cy }: { spec: DiscSpec; index: number; clock: SharedValue<number>; cx: number; cy: number }) {
  const style = useAnimatedStyle(() => {
    const time = clock.get();
    const inAt = DISC_IN + index * DISC_STAGGER;
    const fly = easeOut(ramp(time, inAt, inAt + DISC_FLY));
    const suck = easeIn(ramp(time, SUCK_AT + index * SUCK_STAGGER, SUCK_AT + index * SUCK_STAGGER + SUCK_FOR));
    const orbit = Math.max(0, time - inAt);
    const angle = spec.phase + (spec.speed * TURN * orbit) / PERIOD + (1 - fly) * SPIRAL_IN + suck * Math.PI;
    const radius = spec.radius * (1 + FAR * (1 - fly)) * (1 - suck);
    const scale = (0.3 + 0.7 * fly) * (1 + PULSE * Math.sin(orbit * 2.4 + spec.phase)) * (1 - suck);
    return {
      opacity: ramp(time, inAt, inAt + 0.12) * (suck < 1 ? 1 : 0),
      transform: [
        { translateX: cx + Math.cos(angle) * radius - spec.size / 2 },
        { translateY: cy + Math.sin(angle) * radius * SQUASH - spec.size / 2 },
        { scale },
      ],
    };
  });
  return (
    <Animated.View style={[styles.disc, { width: spec.size, height: spec.size, borderRadius: spec.size / 2, backgroundColor: spec.color, shadowColor: spec.color }, style]}>
      <Ionicons name={spec.icon} size={spec.size * 0.55} color="#ffffff" />
    </Animated.View>
  );
}

const RING = 104;
const RING_STROKE = 5;
const RADIUS = (RING - RING_STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
// Three quarters of the circle are drawn.
const ARC = CIRCUMFERENCE * 0.75;
// Degrees a second.
const RING_SPIN = 360 / PERIOD;
const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** The arc around the badge: it draws itself, keeps turning, and fades when the discs are pulled in. */
export function Ring({ clock, cx, cy }: { clock: SharedValue<number>; cx: number; cy: number }) {
  const style = useAnimatedStyle(() => ({
    opacity: ramp(clock.get(), RING_IN, RING_IN + 0.2) * (1 - ramp(clock.get(), RING_OUT, RING_OUT + 0.5)),
    transform: [{ rotate: `${Math.max(0, clock.get() - RING_IN) * RING_SPIN}deg` }],
  }));
  const props = useAnimatedProps(() => ({ strokeDashoffset: ARC * (1 - easeOut(ramp(clock.get(), RING_IN, RING_IN + RING_DRAW))) }));
  return (
    <Animated.View pointerEvents="none" style={[styles.ring, { left: cx - RING / 2, top: cy - RING / 2 }, style]}>
      <Svg width={RING} height={RING}>
        <AnimatedCircle
          cx={RING / 2}
          cy={RING / 2}
          r={RADIUS}
          stroke={INDIGO}
          strokeWidth={RING_STROKE}
          strokeLinecap="round"
          fill="none"
          strokeDasharray={[ARC, CIRCUMFERENCE]}
          animatedProps={props}
        />
      </Svg>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  disc: {
    position: 'absolute',
    left: 0,
    top: 0,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2.5,
    borderColor: 'rgba(255,255,255,0.85)',
    shadowOpacity: 0.35,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
  },
  ring: { position: 'absolute', width: RING, height: RING },
});

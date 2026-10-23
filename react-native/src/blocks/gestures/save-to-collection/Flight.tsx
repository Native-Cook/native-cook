/** One card flying into the bookmark along a bowed curve, accelerating, with a short trail of ghosts. */
import { Image, type ImageSource } from 'expo-image';
import { useEffect, useState } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedReaction, useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { curve, type Rect } from './layout';
import { SAVE, TIMING } from './motion';
import { LAYOUT } from './theme';

type Props = { id: string; card: number; onStart: (card: number) => void; art: ImageSource; from: Rect; to: { x: number; y: number }; delay: number; duration: number; onLand: (id: string) => void; clock: SharedValue<number> };

function Ghost({ art, from, to, p, lag, smear = false }: { art: ImageSource; from: Rect; to: { x: number; y: number }; p: SharedValue<number>; lag: number; smear?: boolean }) {
  const style = useAnimatedStyle(() => {
    const t = ease(Math.max(p.get() - lag * SAVE.trailStep, 0));
    const pos = curve(from.x + from.w / 2, from.y + from.h / 2, to.x, to.y, SAVE.arc, t);
    const scale = 1 + (SAVE.endScale - 1) * t;
    const ahead = curve(from.x + from.w / 2, from.y + from.h / 2, to.x, to.y, SAVE.arc, Math.min(t + 0.02, 1));
    const angle = Math.atan2(ahead.y - pos.y, ahead.x - pos.x) * (180 / Math.PI) - 90;
    // Ghosts only appear once the card is moving, and fade as it reaches the bookmark.
    const alpha = lag === 0 ? 1 - Math.max(p.get() - 0.92, 0) / 0.08 : (0.55 / Math.pow(lag, 0.7)) * Math.min(p.get() * 4, 1) * (1 - p.get());
    // The source's rush is streaked: the faster a card goes (ease-in, so speed ∝ t²), the more it stretches
    // along its path; the trailing copies are blurred and stretched further, which reads as motion blur.
    const speed = easeSpeed(Math.max(p.get() - lag * SAVE.trailStep, 0)) / 3; // 0..1
    const stretch = 1 + SAVE.streak * speed * (lag === 0 ? (smear ? 1.2 : 0.5) : 1 + lag * 0.4);
    // The smear is a blurred copy over the card that takes over as it speeds up, so the card itself goes soft.
    return {
      opacity: smear ? Math.min(speed * 2.5, 1) * alpha : alpha,
      transform: [
        { translateX: pos.x - from.w / 2 },
        { translateY: pos.y - from.h / 2 },
        { rotate: `${angle}deg` },
        { scaleY: stretch },
        { rotate: `${-angle}deg` },
        { scale },
      ],
      // Hero morph: on the way in the card rounds into a drop the shape of the bookmark's mouth.
      borderRadius: LAYOUT.cardRadius + (Math.min(from.w, from.h) / 2 - LAYOUT.cardRadius) * t * t,
    };
  });
  return (
    <Animated.View style={[styles.card, { width: from.w, height: from.h }, style]} pointerEvents="none">
      <Image source={art} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} blurRadius={lag === 0 && !smear ? 0 : SAVE.streakBlur} />
    </Animated.View>
  );
}

/** Cubic in-out on the UI thread (animate-expo: never ease-in), and its speed for the streaks. */
function ease(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
function easeSpeed(u: number) {
  'worklet';
  return u < 0.5 ? 12 * u * u : 3 * Math.pow(-2 * u + 2, 2);
}

export function Flight({ id, card, onStart, art, from, to, delay, duration, onLand, clock }: Props) {
  // Progress runs on the block's clock (the demo's script clock), so a QA hold freezes a flight mid-air
  // and the loop repeats exactly; a clock that jumps back (a resumed demo) lands the card at once.
  const [start] = useState(() => clock.get() + delay / 1000);
  const p = useDerivedValue(() => {
    const e = clock.get() - start;
    return e < -delay / 1000 ? 1 : Math.min(Math.max(e / (duration / 1000), 0), 1);
  });
  useEffect(() => {
    onStart(card);
  }, [card, onStart]);
  useAnimatedReaction(
    () => p.get() >= 1,
    (done, was) => {
      if (done && !was) scheduleOnRN(onLand, id);
    },
  );
  return (
    <>
      {Array.from({ length: SAVE.trail }, (_, k) => SAVE.trail - k).map((lag) => (
        <Ghost key={lag} art={art} from={from} to={to} p={p} lag={lag} />
      ))}
      <Ghost art={art} from={from} to={to} p={p} lag={0} />
      <Ghost art={art} from={from} to={to} p={p} lag={0} smear />
    </>
  );
}

export const FLIGHT_MS = TIMING.flight;

const styles = StyleSheet.create({
  card: { position: 'absolute', left: 0, top: 0, borderRadius: LAYOUT.cardRadius, overflow: 'hidden' },
});

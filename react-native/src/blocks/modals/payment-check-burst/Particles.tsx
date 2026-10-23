import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { easeOut, hash, seg } from './motion';

// Green confetti falls from the top of the screen.
const CONFETTI = 42;
const CONFETTI_COLORS = ['#2fbf71', '#7be0a8', '#16a05a', '#b9f2d2'];
const CONFETTI_SPREAD = 1.2;
const FALL_MIN = 2.0;
const FALL_MAX = 2.8;
const SWAY = 18;
const SPIN = 540;

// Silver coins spill out of the reward bar and tumble down.
const COINS = 12;
const COIN_MIN = 16;
const COIN_MAX = 30;
const COIN_SPEED_MIN = 220;
const COIN_SPEED_MAX = 520;
const COIN_FAN = 62;
const COIN_GRAVITY = 1100;
const COIN_LIFE = 2.2;
const COIN_FADE_AT = 1.6;
const COIN_SPREAD = 0.45;

const CONFETTI_TABLE = Array.from({ length: CONFETTI }, (_, i) => ({
  color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
  // Every third piece is a thin strip.
  strip: i % 3 === 0,
  x: 0.04 + hash(i, 1) * 0.92,
  drift: (hash(i, 2) - 0.5) * 70,
  delay: hash(i, 3) * CONFETTI_SPREAD,
  fall: FALL_MIN + hash(i, 4) * (FALL_MAX - FALL_MIN),
  sway: 0.6 + hash(i, 5) * 1.2,
  phase: hash(i, 6) * Math.PI * 2,
  spin: (hash(i, 7) < 0.5 ? -1 : 1) * (0.4 + hash(i, 8) * 0.6) * SPIN,
}));

const COIN_TABLE = Array.from({ length: COINS }, (_, i) => {
  const angle = ((hash(i, 11) - 0.5) * 2 * COIN_FAN * Math.PI) / 180;
  const speed = COIN_SPEED_MIN + hash(i, 12) * (COIN_SPEED_MAX - COIN_SPEED_MIN);
  return {
    size: COIN_MIN + hash(i, 13) * (COIN_MAX - COIN_MIN),
    vx: Math.sin(angle) * speed,
    vy: -Math.cos(angle) * speed,
    delay: hash(i, 14) * COIN_SPREAD,
    spin: 3 + hash(i, 15) * 5,
    tilt: (hash(i, 16) - 0.5) * 70,
  };
});

type Common = { clock: SharedValue<number>; at: number };

function Square({ piece, clock, at, width, height }: Common & { piece: (typeof CONFETTI_TABLE)[number]; width: number; height: number }) {
  const style = useAnimatedStyle(() => {
    const age = clock.get() - at - piece.delay;
    if (age < 0 || age > piece.fall) return { opacity: 0 };
    const p = age / piece.fall;
    return {
      opacity: 1 - seg(p, 0.6, 1),
      transform: [
        { translateX: piece.x * width + piece.drift * p + SWAY * Math.sin(p * piece.sway * Math.PI * 2 + piece.phase) },
        { translateY: -10 + (height * 0.9) * Math.pow(p, 1.25) },
        { rotate: `${piece.spin * p}deg` },
        { scale: easeOut(seg(p, 0, 0.06)) },
      ],
    };
  });
  return <Animated.View pointerEvents="none" style={[piece.strip ? styles.strip : styles.square, { backgroundColor: piece.color }, style]} />;
}

/** Green squares and strips falling from the top of the screen. */
export function Confetti({ clock, at, width, height }: Common & { width: number; height: number }) {
  return (
    <>
      {CONFETTI_TABLE.map((piece, i) => (
        <Square key={i} piece={piece} clock={clock} at={at} width={width} height={height} />
      ))}
    </>
  );
}

function Coin({ coin, clock, at, x, y }: Common & { coin: (typeof COIN_TABLE)[number]; x: number; y: number }) {
  const style = useAnimatedStyle(() => {
    const age = clock.get() - at - coin.delay;
    if (age < 0 || age > COIN_LIFE) return { opacity: 0 };
    return {
      opacity: 1 - seg(age, COIN_FADE_AT, COIN_LIFE),
      transform: [
        { translateX: x + coin.vx * age },
        { translateY: y + coin.vy * age + 0.5 * COIN_GRAVITY * age * age },
        { rotate: `${coin.tilt}deg` },
        // Turning about its vertical axis: the coin's width follows the cosine.
        { scaleX: Math.max(0.12, Math.abs(Math.cos(age * coin.spin))) },
        { scale: easeOut(seg(age, 0, 0.1)) },
      ],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.coin, { width: coin.size, height: coin.size, borderRadius: coin.size / 2 }, style]}>
      <LinearGradient colors={['#f4f4f6', '#a9aaaf']} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={[styles.coinRing, { borderRadius: coin.size / 2 }]} />
      <Ionicons name="star" size={coin.size * 0.45} color="rgba(255,255,255,0.95)" />
    </Animated.View>
  );
}

/** Silver coins thrown out of one point. */
export function Coins({ clock, at, x, y }: Common & { x: number; y: number }) {
  return (
    <>
      {COIN_TABLE.map((coin, i) => (
        <Coin key={i} coin={coin} clock={clock} at={at} x={x} y={y} />
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  square: { position: 'absolute', left: 0, top: 0, width: 9, height: 9, borderRadius: 2 },
  strip: { position: 'absolute', left: 0, top: 0, width: 4, height: 16, borderRadius: 2 },
  coin: { position: 'absolute', left: 0, top: 0, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  coinRing: { position: 'absolute', left: 2, right: 2, top: 2, bottom: 2, borderWidth: 1, borderColor: 'rgba(120,120,126,0.6)' },
});

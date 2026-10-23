import { StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { easeOut, hash, seg } from './motion';

/** Pieces thrown out of the toast, and pieces that rain from the top edge a moment later. */
const BURST = 40;
const RAIN = 16;
const COLORS = ['#2b6bff', '#8fb3ff', '#f39a10', '#ffd23f', '#ff5b36', '#2b6bff', '#5ad19a'];
/** Width and height of the three shapes: a card, a ribbon and a dot. */
const SHAPES = [
  { w: 10, h: 14, r: 2 },
  { w: 6, h: 20, r: 3 },
  { w: 10, h: 10, r: 5 },
];
/** Burst speeds in points per second, the air drag that slows them, and the speed they settle to. */
const SPEED_MIN = 420;
const SPEED_MAX = 980;
const DRAG = 2.4;
const FALL_SPEED = 170;
const RAIN_DELAY = 0.25;
const LIFE_MIN = 2.7;
const LIFE_MAX = 3.3;
const SWAY = 16;

type Piece = {
  color: string;
  shape: number;
  /** Launch velocity, or for the rain the start position across the screen (a fraction). */
  vx: number;
  vy: number;
  x: number;
  rain: boolean;
  delay: number;
  life: number;
  swayRate: number;
  swayPhase: number;
  spin: number;
  flipRate: number;
};

/** One fixed table, so every loop throws the same confetti. */
const TABLE: Piece[] = Array.from({ length: BURST + RAIN }, (_, i) => {
  const rain = i >= BURST;
  // A fan from a little above the horizon on the left, down, to a little above it on the right.
  const angle = ((-14 + hash(i, 1) * 208) * Math.PI) / 180;
  const speed = SPEED_MIN + hash(i, 2) * (SPEED_MAX - SPEED_MIN);
  return {
    color: COLORS[i % COLORS.length],
    shape: Math.floor(hash(i, 3) * SHAPES.length),
    vx: rain ? 0 : Math.cos(angle) * speed,
    vy: rain ? FALL_SPEED : Math.sin(angle) * speed * 0.85,
    x: 0.04 + hash(i, 4) * 0.92,
    rain,
    delay: rain ? RAIN_DELAY + hash(i, 5) * 0.6 : hash(i, 5) * 0.08,
    life: LIFE_MIN + hash(i, 6) * (LIFE_MAX - LIFE_MIN),
    swayRate: 1.2 + hash(i, 7) * 1.4,
    swayPhase: hash(i, 8) * Math.PI * 2,
    spin: (hash(i, 9) < 0.5 ? -1 : 1) * (240 + hash(i, 10) * 480),
    flipRate: 3 + hash(i, 11) * 5,
  };
});

type Props = {
  clock: SharedValue<number>;
  /** Second of the loop the burst starts at. */
  at: number;
  /** Where the burst starts, and the screen the pieces fall through. */
  originX: number;
  originY: number;
  width: number;
  height: number;
};

function Bit({ piece, clock, at, originX, originY, width }: Props & { piece: Piece }) {
  const shape = SHAPES[piece.shape];
  const style = useAnimatedStyle(() => {
    const age = clock.get() - at - piece.delay;
    if (age < 0 || age > piece.life) return { opacity: 0 };
    const p = age / piece.life;
    // Thrown out with drag, then settling to a gentle fall: the closed form of v' = -k (v - v_end).
    const slow = (1 - Math.exp(-DRAG * age)) / DRAG;
    const x = piece.rain ? piece.x * width : originX + piece.vx * slow;
    const y = piece.rain ? -24 + FALL_SPEED * 1.4 * age : originY + FALL_SPEED * age + (piece.vy - FALL_SPEED) * slow;
    // The flutter grows in as the throw dies down.
    const flutter = Math.min(1, age / 0.7);
    const pop = easeOut(seg(age, 0, 0.12));
    return {
      opacity: 1 - seg(p, 0.75, 1),
      transform: [
        { translateX: x - shape.w / 2 + SWAY * flutter * Math.sin(age * piece.swayRate * Math.PI * 2 + piece.swayPhase) },
        { translateY: y - shape.h / 2 },
        { rotate: `${piece.spin * age}deg` },
        { scaleY: Math.cos(age * piece.flipRate + piece.swayPhase) },
        { scale: pop },
      ],
    };
  });
  return <Animated.View style={[styles.piece, { width: shape.w, height: shape.h, borderRadius: shape.r, backgroundColor: piece.color }, style]} />;
}

/** A burst of confetti out of the toast and a short rain from the top; none of it takes touches. */
export function Confetti(props: Props) {
  return (
    <>
      {TABLE.map((piece, i) => (
        <Bit key={i} piece={piece} {...props} />
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  piece: { position: 'absolute', left: 0, top: 0 },
});

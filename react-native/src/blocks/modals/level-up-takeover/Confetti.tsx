import { StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { easeOut, hash, seg } from './motion';

const PIECES = 48;
const COLORS = ['#ff4d5e', '#2f6bff', '#2fc96b', '#ffd21a', '#ff8a2a', '#b65cff'];
/** Width, height and corner of the three shapes: a card, a ribbon and a dot. */
const SHAPES = [
  { w: 9, h: 14, r: 2 },
  { w: 6, h: 20, r: 3 },
  { w: 10, h: 10, r: 5 },
];
// Launch speed in points a second, the fan's half-angle around straight up, gravity and air drag.
const SPEED_MIN = 520;
const SPEED_MAX = 1050;
const FAN = 78;
const GRAVITY = 900;
const DRAG = 1.8;
const LIFE = 2.4;
const FADE_AT = 2.0;
const SPIN = 900;

type Piece = { color: string; shape: number; vx: number; vy: number; spin: number; flutter: number };

/** One fixed table, so every burst, in every loop, is the same. */
const TABLE: Piece[] = Array.from({ length: PIECES }, (_, i) => {
  const angle = ((hash(i, 1) - 0.5) * 2 * FAN * Math.PI) / 180;
  const speed = SPEED_MIN + hash(i, 2) * (SPEED_MAX - SPEED_MIN);
  return {
    color: COLORS[i % COLORS.length],
    shape: Math.floor(hash(i, 6) * SHAPES.length),
    vx: Math.sin(angle) * speed,
    vy: -Math.cos(angle) * speed,
    spin: (hash(i, 3) < 0.5 ? -1 : 1) * (0.4 + hash(i, 4) * 0.6) * SPIN,
    flutter: 0.5 + hash(i, 5),
  };
});

type Props = {
  clock: SharedValue<number>;
  /** Second of the loop the burst fires at. */
  at: number;
  /** The point the pieces leave from. */
  x: number;
  y: number;
  /** How many of the table's pieces to fire. */
  count?: number;
};

function Square({ piece, clock, at, x, y }: Props & { piece: Piece }) {
  const shape = SHAPES[piece.shape];
  const style = useAnimatedStyle(() => {
    const age = clock.get() - at;
    if (age < 0 || age > LIFE) return { opacity: 0 };
    // Speed dies away to a steady fall: the usual drag-and-gravity path in closed form.
    const settle = (1 - Math.exp(-DRAG * age)) / DRAG;
    const fall = GRAVITY / DRAG;
    return {
      opacity: 1 - seg(age, FADE_AT, LIFE),
      transform: [
        { translateX: x - shape.w / 2 + piece.vx * settle },
        { translateY: y - shape.h / 2 + (piece.vy - fall) * settle + fall * age },
        { rotate: `${piece.spin * age}deg` },
        // Turning edge-on as it tumbles, like paper.
        { scaleY: 0.35 + 0.65 * Math.abs(Math.cos(age * piece.flutter * 6)) },
        { scale: easeOut(seg(age, 0, 0.08)) },
      ],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.piece, { width: shape.w, height: shape.h, borderRadius: shape.r, backgroundColor: piece.color }, style]} />;
}

/** Paper cards, ribbons and dots thrown up and out from one point. */
export function Confetti({ count = PIECES, ...props }: Props) {
  return (
    <>
      {TABLE.slice(0, count).map((piece, i) => (
        <Square key={i} piece={piece} {...props} />
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  piece: { position: 'absolute', left: 0, top: 0 },
});

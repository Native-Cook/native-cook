import { Ionicons } from '@expo/vector-icons';
import { StyleSheet } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

export const STAR_SIZE = 36;
export const STAR_GAP = 14;

const OUTLINE = '#8d939c';
const FILLED = '#ffbe2e';
const POP_SCALE = 1.3;
const POP_UP_MS = 90;
const POP_SETTLE = { duration: 260, dampingRatio: 0.55 };
const SPARK_MS = 380;
const SPARK_REACH = 22;
const SPARK_SIZE = 5;
// Four sparkles leave the star along these directions (unit vectors).
const SPARKS = [
  [0.71, -0.71],
  [0.71, 0.71],
  [-0.71, 0.71],
  [-0.71, -0.71],
];

type Props = {
  index: number;
  /** How many stars are lit: 0 to 5. */
  rating: SharedValue<number>;
  /** 0 until the star has appeared with the dialog, 1 when it is in place; may overshoot. */
  appear: SharedValue<number>;
  reduced: boolean;
};

/** One outline star. It fills yellow with a pop and a few sparkles when the rating reaches it. */
export function RatingStar({ index, rating, appear, reduced }: Props) {
  const pop = useSharedValue(1);
  const spark = useSharedValue(0);

  useAnimatedReaction(
    () => rating.get() >= index + 1,
    (lit, was) => {
      if (was === null || lit === was) return;
      if (lit && !reduced) {
        pop.set(withSequence(withTiming(POP_SCALE, { duration: POP_UP_MS }), withSpring(1, POP_SETTLE)));
        spark.set(0);
        spark.set(withTiming(1, { duration: SPARK_MS }));
      } else {
        pop.set(1);
      }
    },
  );

  const style = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, appear.get() * 2)),
    transform: [{ scale: Math.max(0, appear.get()) * pop.get() }],
  }));
  const fill = useAnimatedStyle(() => ({ opacity: rating.get() >= index + 1 ? 1 : 0 }));

  return (
    <Animated.View style={[styles.star, style]}>
      <Ionicons name="star-outline" size={STAR_SIZE} color={OUTLINE} />
      <Animated.View style={[StyleSheet.absoluteFill, fill]}>
        <Ionicons name="star" size={STAR_SIZE} color={FILLED} />
      </Animated.View>
      {SPARKS.map(([x, y], i) => (
        <Spark key={i} x={x} y={y} progress={spark} />
      ))}
    </Animated.View>
  );
}

function Spark({ x, y, progress }: { x: number; y: number; progress: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const p = progress.get();
    return {
      opacity: p <= 0 || p >= 1 ? 0 : 1 - p,
      transform: [{ translateX: x * SPARK_REACH * p }, { translateY: y * SPARK_REACH * p }, { rotate: '45deg' }, { scale: 1 - p * 0.4 }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.spark, style]} />;
}

const styles = StyleSheet.create({
  star: { width: STAR_SIZE, height: STAR_SIZE, alignItems: 'center', justifyContent: 'center' },
  spark: {
    position: 'absolute',
    left: STAR_SIZE / 2 - SPARK_SIZE / 2,
    top: STAR_SIZE / 2 - SPARK_SIZE / 2,
    width: SPARK_SIZE,
    height: SPARK_SIZE,
    borderRadius: 1,
    backgroundColor: FILLED,
  },
});

export const STARS_WIDTH = (count: number) => count * STAR_SIZE + (count - 1) * STAR_GAP;

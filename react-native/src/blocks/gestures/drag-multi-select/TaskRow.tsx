/**
 * One to-do. Its blue band grows out of a selected neighbour (scaleY from the shared edge) and rounds
 * only the corners at the ends of a run, so contiguous rows read as one block that stretches and shrinks.
 */
import { Feather } from '@expo/vector-icons';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, {
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { EASE, SPRING, TIMING } from './motion';
import type { Task } from './tasks';
import { COLOR, LAYOUT, SPACE, TYPE } from './theme';

type Props = {
  task: Task;
  index: number;
  selected: boolean; // for accessibility; the drawing reads `sel` on the UI thread
  selecting: boolean;
  sel: SharedValue<number[]>;
  mode: SharedValue<number>; // 0 browsing, 1 selecting
  pressRow: SharedValue<number>; // anticipation: the row a finger is holding before selection starts
  pressP: SharedValue<number>;
  reduceMotion: boolean;
};

const NEVER = { reduceMotion: ReduceMotion.Never };
const H = LAYOUT.row;
const GLOSS_W = 90;
const rowWidth = 402;

export const TaskRow = memo(function TaskRow({ task, index, selected, selecting, sel, mode, pressRow, pressP, reduceMotion }: Props) {
  const band = useSharedValue(0);
  const gloss = useSharedValue(1); // 0→1 sweeps a light across the band when the row joins
  const ring = useSharedValue(1); // 0→1 a ring leaves the circle when the row is picked
  const origin = useSharedValue(0); // -1 grow from the top edge, 1 from the bottom, 0 from the centre
  const top = useDerivedValue(() => {
    const s = sel.get();
    return s[index] === 1 && s[index - 1] === 1 ? 0 : LAYOUT.bandRadius;
  });
  const bottom = useDerivedValue(() => {
    const s = sel.get();
    return s[index] === 1 && s[index + 1] === 1 ? 0 : LAYOUT.bandRadius;
  });

  useAnimatedReaction(() => sel.get()[index] === 1, (on, was) => {
    if (on === was) return;
    const s = sel.get();
    // Grow out of (or shrink back into) the neighbour that is already selected.
    origin.set(s[index - 1] === 1 ? -1 : s[index + 1] === 1 ? 1 : 0);
    band.set(reduceMotion
      ? withTiming(on ? 1 : 0, { duration: TIMING.reduced, ...NEVER })
      : on ? withSpring(1, SPRING.band) : withTiming(0, { duration: TIMING.bandFade, easing: EASE.out }));
    if (on && !reduceMotion) {
      gloss.set(0);
      gloss.set(withTiming(1, { duration: TIMING.gloss, easing: EASE.inOut }));
      ring.set(0);
      ring.set(withTiming(1, { duration: TIMING.ripple, easing: EASE.out }));
    }
  });

  const bandStyle = useAnimatedStyle(() => {
    const p = band.get();
    const scale = reduceMotion ? 1 : 0.25 + 0.75 * Math.min(Math.max(p, 0), 1.05); // never from scale(0)
    return {
      opacity: reduceMotion ? p : Math.min(1, p * 2),
      borderTopLeftRadius: withTiming(top.get(), { duration: TIMING.corner, easing: EASE.inOut }),
      borderTopRightRadius: withTiming(top.get(), { duration: TIMING.corner, easing: EASE.inOut }),
      borderBottomLeftRadius: withTiming(bottom.get(), { duration: TIMING.corner, easing: EASE.inOut }),
      borderBottomRightRadius: withTiming(bottom.get(), { duration: TIMING.corner, easing: EASE.inOut }),
      transform: [{ translateY: origin.get() * -(1 - scale) * (H / 2) }, { scaleY: scale }],
    };
  });

  const circleIn = useDerivedValue(() => {
    const on = mode.get() === 1 ? 1 : 0;
    if (reduceMotion) return withTiming(on, { duration: TIMING.reduced, ...NEVER });
    // A short stagger down the list, capped so long lists do not wait.
    return withDelay(on ? Math.min(index, 12) * TIMING.circleStagger : 0, withSpring(on, SPRING.circles));
  });
  const circleStyle = useAnimatedStyle(() => ({
    opacity: Math.min(circleIn.get(), 1),
    transform: [{ translateX: reduceMotion ? 0 : (1 - circleIn.get()) * TIMING.circleSlide }],
  }));
  // Selected rows read as stacked highlights: a soft light seam where this row meets a selected row above.
  const seamStyle = useAnimatedStyle(() => ({ opacity: top.get() === 0 ? 1 : 0 }));
  const glossStyle = useAnimatedStyle(() => ({
    opacity: gloss.get() >= 1 ? 0 : Math.sin(Math.PI * gloss.get()),
    transform: [{ translateX: -GLOSS_W + (rowWidth + GLOSS_W) * gloss.get() }, { skewX: '-18deg' }],
  }));
  const ringStyle = useAnimatedStyle(() => ({
    opacity: ring.get() >= 1 ? 0 : 0.6 * (1 - ring.get()),
    transform: [{ scale: 1 + 0.9 * ring.get() }],
  }));
  // Anticipation: while a finger holds the row, a band grows in from the left; at 320 ms selection takes over.
  const preview = useAnimatedStyle(() => {
    const p = pressRow.get() === index ? pressP.get() : 0;
    return { opacity: 0.6 * p, transform: [{ scaleX: 0.15 + 0.85 * p }] };
  });
  const lean = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.015 * (pressRow.get() === index ? pressP.get() : 0) }] }));
  const checked = useAnimatedStyle(() => ({ opacity: band.get(), transform: [{ scale: reduceMotion ? 1 : 0.6 + 0.4 * Math.min(band.get(), 1) }] }));

  return (
    <Animated.View
      style={[styles.row, lean]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={task.title}
      accessibilityState={selecting ? { selected } : undefined}
      accessibilityHint={selecting ? 'Double tap to toggle' : undefined}
    >
      <Animated.View style={[styles.band, styles.preview, preview]} />
      <Animated.View style={[styles.band, bandStyle]}>
        <Animated.View style={[styles.seam, seamStyle]} />
        <Animated.View style={[styles.gloss, glossStyle]}>
          <LinearGradient colors={COLOR.gloss} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </Animated.View>
      <View style={styles.check} />
      <View style={[styles.text, selecting && styles.textSelecting]}>
        <Text style={styles.title} numberOfLines={1}>{task.title}</Text>
        {task.note && <Feather name="file-text" size={14} color={COLOR.note} style={styles.note} />}
      </View>
      <Animated.View style={[styles.circle, circleStyle]} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
        <Animated.View style={[styles.ripple, ringStyle]} />
        <Animated.View style={[styles.circleOn, checked]}>
          <View style={styles.ring} />
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  row: { height: H, flexDirection: 'row', alignItems: 'center', paddingLeft: LAYOUT.gutter },
  preview: { transformOrigin: 'left', borderRadius: LAYOUT.bandRadius },
  gloss: { position: 'absolute', left: 0, top: 0, bottom: 0, width: GLOSS_W },
  seam: { position: 'absolute', left: 0, right: 0, top: 0, height: 1.5, backgroundColor: COLOR.bandSeam },
  ripple: { position: 'absolute', left: -1.5, top: -1.5, right: -1.5, bottom: -1.5, borderRadius: LAYOUT.circle, borderWidth: 2, borderColor: COLOR.blue },
  band: { overflow: 'hidden', position: 'absolute', left: LAYOUT.bandInset, right: LAYOUT.bandInset, top: 0, height: H, backgroundColor: COLOR.band },
  check: {
    width: LAYOUT.check, height: LAYOUT.check, borderRadius: LAYOUT.checkRadius,
    borderWidth: 1.2, borderStyle: 'dashed', borderColor: COLOR.check,
  },
  text: { flex: 1, flexDirection: 'row', alignItems: 'center', marginLeft: LAYOUT.textLeft - LAYOUT.gutter - LAYOUT.check, paddingRight: LAYOUT.gutter },
  // The title stops SPACE.sm short of the circles' touch column.
  textSelecting: { paddingRight: LAYOUT.circleColumn - SPACE.sm },
  title: { ...TYPE.row, color: COLOR.ink, flexShrink: 1 },
  note: { marginLeft: 6 },
  circle: {
    position: 'absolute', right: LAYOUT.circleRight + LAYOUT.bandInset, top: (H - LAYOUT.circle) / 2,
    width: LAYOUT.circle, height: LAYOUT.circle, borderRadius: LAYOUT.circle / 2,
    borderWidth: 1.5, borderColor: COLOR.circle, backgroundColor: COLOR.white,
  },
  // Selected: blue with a white ring inside, as in the source.
  circleOn: {
    position: 'absolute', left: -1.5, top: -1.5, right: -1.5, bottom: -1.5, borderRadius: LAYOUT.circle / 2,
    backgroundColor: COLOR.blue,
  },
  ring: { position: 'absolute', left: 4, top: 4, right: 4, bottom: 4, borderRadius: LAYOUT.circle / 2, borderWidth: 2, borderColor: COLOR.white },
});

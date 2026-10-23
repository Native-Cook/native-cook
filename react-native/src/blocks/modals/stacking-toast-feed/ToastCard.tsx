import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useEffect } from 'react';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export type ToastKind = 'error' | 'warning' | 'info' | 'success';

export type ToastMessage = {
  kind: ToastKind;
  title: string;
  /** The text before the link, the link itself, and what comes after it. */
  message: string;
  link?: string;
  tail?: string;
};

export type ToastItem = {
  id: number;
  message: ToastMessage;
  /** Set while the toast leaves: 1 to the right, -1 to the left, 0 shrinks and fades. */
  leaving?: -1 | 0 | 1;
};

export const TOAST_HEIGHT = 84;
export const TOAST_GAP = 12;
export const SPREAD_TOP = 64;
// Collapsed, each toast behind the front one shows this much, and is this much smaller.
const STACK_STEP = 12;
const STACK_SHRINK = 0.04;
const STACK_DIM = 0.04;
const STACK_MIN_SCALE = 0.8;

const SLIDE_SPRING = { duration: 450, dampingRatio: 0.8, reduceMotion: ReduceMotion.Never };
const SHAKE_POINTS = 4;
const SHAKE_MS = 50;
const SWIPE_MS = 450;
const FADE_MS = 350;
const SHRINK_MS = 250;
const SHRINK_TO = 0.9;
const TILT_DEGREES = 12;
// A drag beyond this distance, or this fast, sends the toast away.
const SWIPE_DISTANCE = 90;
const SWIPE_SPEED = 800;

const LOOKS: Record<ToastKind, { band: string; ink: string; icon: keyof typeof Ionicons.glyphMap }> = {
  error: { band: '#ffdadd', ink: '#d6212a', icon: 'close-circle-outline' },
  warning: { band: '#fff8c4', ink: '#857400', icon: 'warning-outline' },
  info: { band: '#d8f1ff', ink: '#0b4c8c', icon: 'information-circle-outline' },
  success: { band: '#ddfbd7', ink: '#1e8a1e', icon: 'checkmark-circle-outline' },
};

type Props = {
  item: ToastItem;
  /** Place in the pile among the toasts that stay: 0 is the front. */
  index: number;
  /** 0 when the toasts are spread out, 1 when they are collapsed into a pile. */
  stack: SharedValue<number>;
  width: number;
  screenWidth: number;
  reduced: boolean;
  onLeave: (id: number, direction: -1 | 0 | 1) => void;
  onGone: (id: number) => void;
};

/** One toast. Its place comes from `index` alone: a spring moves `slot` there, so a toast that
 * enters or leaves lets the others reflow without separate animations. */
export function ToastCard({ item, index, stack, width, screenWidth, reduced, onLeave, onGone }: Props) {
  const { id, message, leaving } = item;
  const look = LOOKS[message.kind];
  const slot = useSharedValue(index);
  const enter = useSharedValue(0);
  const shake = useSharedValue(0);
  const drag = useSharedValue(0);
  const shrink = useSharedValue(1);
  const gone = useSharedValue(0);

  useEffect(() => {
    if (reduced) {
      enter.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
      return;
    }
    enter.set(withSpring(1, SLIDE_SPRING));
    shake.set(
      withSequence(
        withTiming(SHAKE_POINTS, { duration: SHAKE_MS }),
        withTiming(-SHAKE_POINTS, { duration: SHAKE_MS }),
        withTiming(SHAKE_POINTS, { duration: SHAKE_MS }),
        withTiming(-SHAKE_POINTS, { duration: SHAKE_MS }),
        withTiming(0, { duration: SHAKE_MS }),
      ),
    );
  }, [enter, shake, reduced]);

  useEffect(() => {
    if (leaving !== undefined) return;
    slot.set(reduced ? withTiming(index, { duration: SHRINK_MS, reduceMotion: ReduceMotion.Never }) : withSpring(index, SLIDE_SPRING));
  }, [index, leaving, reduced, slot]);

  useEffect(() => {
    if (leaving === undefined) return;
    const done = (finished?: boolean) => {
      'worklet';
      if (finished) scheduleOnRN(onGone, id);
    };
    if (leaving === 0 || reduced) {
      shrink.set(withTiming(reduced ? 1 : SHRINK_TO, { duration: reduced ? FADE_MS : SHRINK_MS, reduceMotion: ReduceMotion.Never }));
      gone.set(withTiming(1, { duration: reduced ? FADE_MS : SHRINK_MS, reduceMotion: ReduceMotion.Never }, done));
      return;
    }
    // The swipe starts as a finger would: slowly, then it lets go.
    drag.set(withTiming(leaving * screenWidth * 1.1, { duration: SWIPE_MS, easing: Easing.in(Easing.quad), reduceMotion: ReduceMotion.Never }));
    gone.set(withTiming(1, { duration: SWIPE_MS, easing: Easing.in(Easing.cubic), reduceMotion: ReduceMotion.Never }, done));
  }, [leaving, reduced, id, onGone, drag, shrink, gone, screenWidth]);

  useEffect(
    () => () => {
      for (const value of [slot, enter, shake, drag, shrink, gone]) cancelAnimation(value);
    },
    [slot, enter, shake, drag, shrink, gone],
  );

  const pan = Gesture.Pan()
    .enabled(leaving === undefined)
    .activeOffsetX([-10, 10])
    .failOffsetY([-14, 14])
    .onUpdate((event) => {
      drag.set(event.translationX);
    })
    .onEnd((event) => {
      if (Math.abs(event.translationX) > SWIPE_DISTANCE || Math.abs(event.velocityX) > SWIPE_SPEED) {
        const direction = (event.translationX > 0 ? 1 : -1) as 1 | -1;
        scheduleOnRN(onLeave, id, direction);
      } else {
        drag.set(withSpring(0, { duration: 350, dampingRatio: 0.8 }));
      }
    });

  const style = useAnimatedStyle(() => {
    const s = stack.get();
    const p = Math.max(slot.get(), 0);
    const spreadY = SPREAD_TOP + (TOAST_HEIGHT + TOAST_GAP) * p;
    const stackedY = SPREAD_TOP + STACK_STEP * p;
    const scale = (1 + (Math.max(STACK_MIN_SCALE, 1 - STACK_SHRINK * p) - 1) * s) * shrink.get();
    const y = spreadY + (stackedY - spreadY) * s - (1 - enter.get()) * (SPREAD_TOP + TOAST_HEIGHT + 24);
    const tilt = reduced ? 0 : (drag.get() / screenWidth) * TILT_DEGREES;
    return {
      opacity: reduced ? Math.min(1, enter.get()) * (1 - gone.get()) : (1 - gone.get() * (leaving === 0 ? 1 : 0.9)) * Math.min(1, enter.get() * 4),
      transform: [{ translateY: y }, { translateX: shake.get() + drag.get() }, { rotate: `${tilt}deg` }, { scale }],
    };
  });
  const dimStyle = useAnimatedStyle(() => ({ opacity: STACK_DIM * Math.max(slot.get(), 0) * stack.get() }));

  return (
    <GestureDetector gesture={pan}>
      <Animated.View style={[styles.toast, { width, zIndex: 100 - index }, style]}>
        <View style={[styles.band, { backgroundColor: look.band }]}>
          <Ionicons name={look.icon} size={25} color={look.ink} />
        </View>
        <View style={styles.copy}>
          <Text style={styles.title} numberOfLines={1}>
            {message.title}
          </Text>
          <Text style={styles.body} numberOfLines={2}>
            {message.message}
            {message.link ? <Text style={styles.link}>{message.link}</Text> : null}
            {message.tail}
          </Text>
        </View>
        <Pressable style={styles.close} hitSlop={8} onPress={() => onLeave(id, 0)} accessibilityRole="button" accessibilityLabel="Dismiss">
          <Ionicons name="close" size={20} color="#8a8d93" />
        </Pressable>
        <Animated.View pointerEvents="none" style={[styles.dim, dimStyle]} />
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute',
    left: 20,
    top: 0,
    height: TOAST_HEIGHT,
    flexDirection: 'row',
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    transformOrigin: 'top',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(17,19,24,0.08)',
    boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 3, color: 'rgba(17,19,24,0.08)' },
      { offsetX: 0, offsetY: 12, blurRadius: 28, color: 'rgba(17,19,24,0.14)' },
    ],
  },
  band: {
    width: 52,
    alignItems: 'center',
    justifyContent: 'center',
    borderTopLeftRadius: 14,
    borderBottomLeftRadius: 14,
  },
  copy: { flex: 1, paddingLeft: 14, paddingRight: 40, justifyContent: 'center' },
  title: { color: '#111318', fontSize: 17, lineHeight: 22, fontWeight: '700', letterSpacing: -0.3 },
  body: { color: '#5f636b', fontSize: 14, lineHeight: 19, marginTop: 2 },
  link: { color: '#2b2e35', textDecorationLine: 'underline' },
  close: { position: 'absolute', right: 10, top: 10, width: 28, height: 28, alignItems: 'center', justifyContent: 'center' },
  dim: { ...StyleSheet.absoluteFill, borderRadius: 14, backgroundColor: '#000000' },
});

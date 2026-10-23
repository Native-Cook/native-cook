import { BlurMask, Canvas, Group, RoundedRect, rect, rrect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cubicBezier,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  /** Shown next to the two avatars once they have met. */
  message?: string;
  /** Initials on the host's avatar; the other one always reads "YOU". */
  initials?: string;
  onPress?: () => void;
  /** Plays the whole greeting by itself with a pause between runs. */
  autoPlay?: boolean;
};

// The pill has a fixed size so the avatars have known places to travel between.
const WIDTH = 164;
const HEIGHT = 52;
const AVATAR = 38;
// The canvas overflows the button by this much on every side to fit the shadow.
const BLEED = 26;

// Avatar centres, measured from the pill's left edge: where they fade in, where they pause as
// "host + you", and where they end up overlapping.
const HOST_X = [26, 48, 27];
const GUEST_X = [138, 116, 53];
const MESSAGE_X = 80;
const MESSAGE_WIDTH = 80;

const OPEN_MS = 380;
const PAIR_HOLD_MS = 420;
const JOIN_MS = 520;
const CLOSE_MS = 320;
// How long a tap keeps the greeting up, and the rhythm of the autoplay loop.
const TAP_HOLD_MS = 1300;
const AUTOPLAY_PAUSE_MS = 1500;
const AUTOPLAY_HOLD_MS = 1700;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function Surface() {
  const x = BLEED;
  const y = BLEED;
  const r = HEIGHT / 2;
  const pill = rrect(rect(x, y, WIDTH, HEIGHT), r, r);
  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <RoundedRect x={x + 2} y={y + 8} width={WIDTH - 4} height={HEIGHT - 2} r={r} color="rgba(0,0,0,0.34)">
        <BlurMask blur={9} style="normal" />
      </RoundedRect>
      <RoundedRect rect={pill} color="#030303" />
      {/* A soft light hugging the inside of the edge, as if the pill were slightly domed. */}
      <Group clip={pill}>
        <RoundedRect rect={pill} style="stroke" strokeWidth={5} color="rgba(255,255,255,0.46)">
          <BlurMask blur={3.5} style="normal" />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Black "BOOK A CALL" pill. While pressed (or by itself with `autoPlay`) the label slides away,
 * two avatars swing in as "host + you", then slide together and a short message wipes in beside
 * them. A tap plays the whole greeting once.
 */
export function AvatarCallButton({
  label = 'BOOK A CALL',
  message = "Let's talk!",
  initials = 'JD',
  onPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);

  // 0 → 1: the label makes way for the two avatars.
  const open = useSharedValue(0);
  // 0 → 1: the avatars slide together and the message appears.
  const join = useSharedValue(0);

  const held = useRef(false);
  const auto = useRef(false);
  const tapped = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pressedAt = useRef(0);
  const greeting = useRef(false);
  const run = useRef(0);

  /** Starts or ends the greeting so it matches what press, tap and autoplay currently ask for. */
  const sync = useCallback(async () => {
    const wanted = held.current || auto.current || tapped.current !== null;
    if (wanted === greeting.current) return;
    greeting.current = wanted;
    const id = ++run.current;
    // Reduced motion keeps the fades (see the styles below), so they must still run.
    const never = { reduceMotion: ReduceMotion.Never };
    if (!wanted) {
      join.set(withTiming(0, { duration: CLOSE_MS * 0.7, easing: EASE_OUT, ...never }));
      open.set(withTiming(0, { duration: CLOSE_MS, easing: EASE_OUT, ...never }));
      return;
    }
    open.set(withTiming(1, { duration: OPEN_MS, easing: EASE_OUT, ...never }));
    await sleep(OPEN_MS + PAIR_HOLD_MS);
    if (run.current !== id) return;
    join.set(withTiming(1, { duration: JOIN_MS, easing: EASE_IN_OUT, ...never }));
  }, [open, join]);

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) break;
        auto.current = true;
        sync();
        await sleep(OPEN_MS + PAIR_HOLD_MS + JOIN_MS + AUTOPLAY_HOLD_MS);
        auto.current = false;
        sync();
        await sleep(CLOSE_MS);
      }
    })();
    return () => {
      cancelled = true;
      auto.current = false;
      sync();
    };
  }, [autoPlay, sync]);

  useEffect(
    () => () => {
      if (tapped.current) clearTimeout(tapped.current);
    },
    [],
  );

  const labelStyle = useAnimatedStyle(() => {
    const o = open.get();
    return {
      opacity: 1 - Math.min(1, o * 1.8),
      transform: [{ translateY: reducedMotion ? 0 : -22 * o }],
    };
  });
  const hostStyle = useAnimatedStyle(() => {
    const o = open.get();
    if (reducedMotion) return { opacity: o, transform: [{ translateX: HOST_X[2] - AVATAR / 2 }] };
    const j = join.get();
    const x = HOST_X[0] + (HOST_X[1] - HOST_X[0]) * o + (HOST_X[2] - HOST_X[1]) * j;
    return {
      opacity: Math.min(1, o * 1.5),
      transform: [{ translateX: x - AVATAR / 2 }, { rotate: `${-40 * (1 - o)}deg` }, { scale: 0.8 + 0.2 * o }],
    };
  });
  const guestStyle = useAnimatedStyle(() => {
    const o = open.get();
    if (reducedMotion) return { opacity: o, transform: [{ translateX: GUEST_X[2] - AVATAR / 2 }] };
    const j = join.get();
    const x = GUEST_X[0] + (GUEST_X[1] - GUEST_X[0]) * o + (GUEST_X[2] - GUEST_X[1]) * j;
    return {
      opacity: Math.min(1, o * 1.5),
      transform: [{ translateX: x - AVATAR / 2 }, { rotate: `${40 * (1 - o)}deg` }, { scale: 0.8 + 0.2 * o }],
    };
  });
  const plusStyle = useAnimatedStyle(() => ({
    opacity: reducedMotion ? 0 : open.get() * (1 - Math.min(1, join.get() * 2.5)),
  }));
  // The message is wiped in from the left: its window slides right while the text inside slides
  // back by the same amount, so the letters stay put.
  const wipe = useAnimatedStyle(() => {
    if (reducedMotion) return { opacity: open.get(), transform: [{ translateX: 0 }] };
    const t = Math.max(0, (join.get() - 0.35) / 0.65);
    return { opacity: Math.min(1, t * 2), transform: [{ translateX: -MESSAGE_WIDTH * (1 - t) }] };
  });
  const unwipe = useAnimatedStyle(() => {
    if (reducedMotion) return { transform: [{ translateX: 0 }] };
    const t = Math.max(0, (join.get() - 0.35) / 0.65);
    return { transform: [{ translateX: MESSAGE_WIDTH * (1 - t) }] };
  });

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={() => {
        onPress?.();
        // A tap is too short to watch the greeting, so it keeps it up for a moment. A long hold
        // has already shown it.
        if (Date.now() - pressedAt.current > OPEN_MS + PAIR_HOLD_MS) return;
        if (tapped.current) clearTimeout(tapped.current);
        tapped.current = setTimeout(
          () => {
            tapped.current = null;
            sync();
          },
          OPEN_MS + PAIR_HOLD_MS + JOIN_MS + TAP_HOLD_MS,
        );
        sync();
      }}
      onPressIn={() => {
        setPressed(true);
        held.current = true;
        pressedAt.current = Date.now();
        sync();
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => {
        setPressed(false);
        held.current = false;
        // `onPress` follows straight after and may want the greeting kept, so decide after it.
        setTimeout(sync, 0);
      }}
      pressRetentionOffset={16}
    >
      <Animated.View
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface />
        </View>
        <View style={styles.content} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
            {label}
          </Animated.Text>
          <Animated.Text style={[styles.plus, plusStyle]}>+</Animated.Text>
          <Animated.View style={[styles.avatar, styles.host, hostStyle]}>
            <Text style={styles.initials}>{initials}</Text>
          </Animated.View>
          <Animated.View style={[styles.avatar, styles.guest, guestStyle]}>
            <Text style={styles.you}>YOU</Text>
          </Animated.View>
          <View style={styles.messageSlot}>
            <Animated.View style={[styles.messageWindow, wipe]}>
              <Animated.Text style={[styles.message, unwipe]} numberOfLines={1}>
                {message}
              </Animated.Text>
            </Animated.View>
          </View>
        </View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  content: {
    ...StyleSheet.absoluteFill,
    borderRadius: HEIGHT / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: 0.2,
  },
  plus: {
    position: 'absolute',
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '500',
  },
  avatar: {
    position: 'absolute',
    left: 0,
    top: (HEIGHT - AVATAR) / 2,
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  host: {
    backgroundColor: '#ff7a59',
  },
  guest: {
    backgroundColor: '#ffffff',
  },
  initials: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
  },
  you: {
    color: '#000000',
    fontSize: 9.5,
    fontWeight: '800',
  },
  messageSlot: {
    position: 'absolute',
    left: MESSAGE_X,
    top: 0,
    width: MESSAGE_WIDTH,
    height: HEIGHT,
    overflow: 'hidden',
  },
  messageWindow: {
    width: MESSAGE_WIDTH,
    height: HEIGHT,
    overflow: 'hidden',
    justifyContent: 'center',
  },
  message: {
    width: MESSAGE_WIDTH,
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '500',
  },
});

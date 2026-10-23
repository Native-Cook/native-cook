import { BlurMask, Canvas, Group, matchFont, Path, Text as SkiaText } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolate,
  interpolateColor,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The words on the drum, in order. Twelve or more keep a word from showing twice. */
  words?: string[];
  /** The line above the drum. */
  lead?: string;
  /** What follows the chosen word once it is chosen. */
  promise?: string;
  /** Turns the drum a word at a time, chooses one, goes back and starts over. */
  autoPlay?: boolean;
  background?: string;
  /** The word in front, and the rest of the drum. */
  activeColor?: string;
  idleColor?: string;
  textColor?: string;
  /** The stripes that sweep across when a word is chosen, darkest first. */
  stripes?: string[];
  /** Called with a word when it is chosen. */
  onChoose?: (word: string) => void;
};

const WORDS = [
  'Visibility',
  'Strategy',
  'Growth',
  'Solutions',
  'Progress',
  'Momentum',
  'Boost',
  'Scaling',
  'Innovation',
  'Clarity',
  'Reach',
  'Impact',
];

// A geometric sans close to the reference: Avenir Next on iOS, the system sans elsewhere.
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const FONT_SIZE = 40;
// The word's baseline sits this share of the size below the drum's axis, so the letters straddle it.
const BASELINE = 0.34;

// The words are the spokes of a large wheel lying flat on the screen, its hub far off to the
// right. Each word sits on the rim, SPACING apart, and turns with its spoke, so the words above
// the front rise to the right and the words below fall away, each one a little further left.
// Words are drawn up to MAX_ROWS away from the front.
const SPACING = 60;
const RADIUS = 760;
const ANGLE = SPACING / RADIUS;
const MAX_ROWS = 3.8;
// The word in front grows a little and steps right, away from the arrow.
const FRONT_GROW = 0.07;
const FRONT_SHIFT = 6;
// Left edge of the words and of the arrow, from the left of the screen.
const WORD_LEFT = 72;
const ARROW_LEFT = 28;
const ARROW_WIDTH = 26;
const ARROW_HEAD = 9;
const ARROW_STROKE = 3.2;
// Each word that reaches the front kicks the arrow, and the word with it, to the right like a
// ratchet's pawl: a push of KICK_SPEED pt/s into a critically damped spring that peaks about
// 13 pt over 80 ms and settles back over 300 ms. A fast spin kicks it once per word, up to KICK_MAX.
const KICK_SPEED = 430;
const KICK_STIFFNESS = 150;
const KICK_DAMPING = 24.5;
const KICK_MAX = 18;
// Height of the screen, as a share, where the drum's axis sits.
const AXIS_AT = 0.47;

// Blur grows with a word's distance from the front and with the drum's speed.
const BLUR_PER_ROW = 1.4;
const BLUR_PER_SPEED = 0.55;
const BLUR_MAX = 9;
const FADE_PER_ROW = 0.16;
const SPEED_RATE = 10;

// Finger travel that turns the wheel by one word: the words follow the finger.
const DRAG_STEP = SPACING;
const DRAG_SLOP = 6;
// The snap after a release: a spring on the drum's position, in words.
const MASS = 1;
const STIFFNESS = 300;
const DAMPING = 32;
// Seconds of speed a release throws the drum ahead by: a hard flick spins it like a reel.
const THROW_TIME = 0.3;

// Choosing a word: four stripes sweep across from the left, staggered, over WIPE_TIME ms. The
// drum and the chosen word swap while the first stripe covers the screen.
const WIPE_TIME = 1150;
const STRIPE_LAG = 0.08;
const STRIPE_SPAN = 0.56;
const SWAP_AT = STRIPE_SPAN / 2;
// Each stripe is this many screens wide, so neighbours always overlap and nothing shows between
// them while they move fastest.
const STRIPE_LENGTH = 2;
const EASE_IN_OUT = Easing.bezierFn(0.77, 0, 0.175, 1);
const REVEAL_RISE = 26;

// autoPlay loop (seconds): five turns of one word, a word chosen, the drum again, a rest.
const LOOP = 10;
const TURNS = [0.7, 1.55, 2.4, 3.25, 4.1];
const CHOOSE_AT = 5.0;
const RETURN_AT = 7.6;
// After a touch the loop waits this long before it starts over; longer after a word is chosen,
// so it can be read.
const USER_HOLD = 3;
const CHOSEN_HOLD = 5;

/** `x` moved by whole turns of `n` into [-n/2, n/2). */
function centred(x: number, n: number) {
  'worklet';
  return x - n * Math.floor(x / n + 0.5);
}

function snapTarget(scroll: number, velocity: number) {
  'worklet';
  return Math.round(scroll + velocity * THROW_TIME);
}

/** Where a stripe's left edge is, in screens: -STRIPE_LENGTH (all to the left) … 1 (all to the right). */
function stripeAt(wipe: number, k: number) {
  'worklet';
  const p = Math.min(1, Math.max(0, (wipe - k * STRIPE_LAG) / STRIPE_SPAN));
  return -STRIPE_LENGTH + (STRIPE_LENGTH + 1) * EASE_IN_OUT(p);
}

type RowProps = {
  word: string;
  index: number;
  count: number;
  scroll: SharedValue<number>;
  speed: SharedValue<number>;
  kick: SharedValue<number>;
  font: ReturnType<typeof matchFont>;
  axisY: number;
  activeColor: string;
  idleColor: string;
};

/** One word on the wheel's rim, turned with its spoke and blurred by its distance from the front. */
function Row({ word, index, count, scroll, speed, kick, font, axisY, activeColor, idleColor }: RowProps) {
  const distance = useDerivedValue(() => centred(index - scroll.get(), count));
  const transform = useDerivedValue(() => {
    const d = distance.get();
    const angle = d * ANGLE;
    // 1 for the word in front, 0 from one row away.
    const front = Math.max(0, 1 - Math.abs(d));
    return [
      { translateX: WORD_LEFT - RADIUS * (1 - Math.cos(angle)) + front * (FRONT_SHIFT + kick.get()) },
      { translateY: axisY + RADIUS * Math.sin(angle) },
      { rotate: angle },
      { scale: 1 + front * FRONT_GROW },
    ];
  });
  const opacity = useDerivedValue(() => {
    const d = Math.abs(distance.get());
    return d > MAX_ROWS ? 0 : Math.max(0, 1 - d * FADE_PER_ROW) * Math.min(1, (MAX_ROWS - d) * 1.5);
  });
  const blur = useDerivedValue(() =>
    Math.max(0.01, Math.min(BLUR_MAX, Math.abs(distance.get()) * BLUR_PER_ROW + speed.get() * BLUR_PER_SPEED)),
  );
  const color = useDerivedValue(() =>
    interpolateColor(Math.min(1, Math.abs(distance.get())), [0, 1], [activeColor, idleColor]),
  );
  return (
    <Group transform={transform} opacity={opacity}>
      <SkiaText x={0} y={FONT_SIZE * BASELINE} text={word} font={font} color={color}>
        <BlurMask blur={blur} style="normal" respectCTM={false} />
      </SkiaText>
    </Group>
  );
}

/**
 * A wheel of words turning in the dark. The word in front is bright, with an arrow pointing at
 * it; the others fan away above and below like spokes of a wheel whose hub is off the right edge,
 * dimmer and more blurred the further they are, and the whole wheel blurs while it spins. Swiping turns it a word at a time; tapping the word in
 * front chooses it, and a run of blue stripes sweeps across to show it on its own.
 */
export function WordDrum({
  words = WORDS,
  lead = 'What we bring',
  promise = 'built into everything we ship.',
  autoPlay = false,
  background = '#000000',
  activeColor = '#8cc4ff',
  idleColor = '#3f6fae',
  textColor = '#e9f2ff',
  stripes = ['#1f63d8', '#3f86ee', '#7cb4f7', '#c3defc'],
  onChoose,
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = words.length;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [chosen, setChosen] = useState(0);
  const font = matchFont({ fontFamily: FAMILY, fontSize: FONT_SIZE, fontWeight: '700' });
  const axisY = (size?.height ?? 0) * AXIS_AT;

  const scroll = useSharedValue(0);
  const seen = useSharedValue(0);
  const velocity = useSharedValue(0);
  const target = useSharedValue(0);
  // |velocity| smoothed, in words per second.
  const speed = useSharedValue(0);
  // The arrow's kick to the right, in pt, and its speed.
  const kick = useSharedValue(0);
  const kickVelocity = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  // 0…1 through a sweep of the stripes; `showing` flips while they cover the screen.
  const wipe = useSharedValue(0);
  const showing = useSharedValue(0);
  const wiping = useSharedValue(false);
  const byUser = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const chose = (index: number, user: boolean) => {
    setChosen(index);
    if (!user) return;
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onChoose?.(words[index]);
  };

  /** Sweeps the stripes across; while they cover the screen the drum and the word swap. */
  const sweep = () => {
    'worklet';
    if (wiping.get()) return;
    wiping.set(true);
    wipe.set(0);
    if (reducedMotion) {
      showing.set(withTiming(showing.get() > 0.5 ? 0 : 1, { duration: 220 }, () => wiping.set(false)));
      return;
    }
    wipe.set(withTiming(1, { duration: WIPE_TIME, easing: Easing.linear }, () => wiping.set(false)));
  };
  const choose = (user: boolean) => {
    'worklet';
    const front = Math.round(scroll.get());
    target.set(front);
    scheduleOnRN(chose, front - count * Math.floor(front / count), user);
    sweep();
  };

  // The swap happens under the first stripe, so neither side is seen changing.
  useAnimatedReaction(
    () => wipe.get() >= SWAP_AT,
    (covered, before) => {
      if (covered && before === false) showing.set(showing.get() > 0.5 ? 0 : 1);
    },
  );

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    if (dragging.get()) {
      velocity.set(velocity.get() + ((scroll.get() - seen.get()) / dt - velocity.get()) * 0.5);
    } else {
      if (hold.get() > 0) {
        hold.set(hold.get() - dt);
        if (hold.get() <= 0) {
          clock.set(0);
          // A word left chosen goes back to the wheel before the loop starts over.
          if (looping.get() && showing.get() > 0.5) sweep();
        }
      } else if (looping.get()) {
        const before = clock.get();
        const t = (before + dt) % LOOP;
        clock.set(t);
        const passed = (at: number) => (before < at && t >= at) || (t < before && (at > before || at <= t));
        for (let k = 0; k < TURNS.length; k += 1) {
          if (passed(TURNS[k])) {
            byUser.set(false);
            target.set(Math.round(target.get()) + 1);
          }
        }
        if (passed(CHOOSE_AT) && showing.get() < 0.5) choose(false);
        if (passed(RETURN_AT) && showing.get() > 0.5) sweep();
      }
      if (reducedMotion) {
        scroll.set(target.get());
        velocity.set(0);
      } else {
        const v = velocity.get() + ((STIFFNESS * (target.get() - scroll.get()) - DAMPING * velocity.get()) / MASS) * dt;
        velocity.set(v);
        scroll.set(scroll.get() + v * dt);
      }
      // Whole turns of the drum change nothing on screen, so the numbers are kept small.
      const turns = Math.floor(target.get() / count) * count;
      if (turns !== 0) {
        scroll.set(scroll.get() - turns);
        target.set(target.get() - turns);
      }
    }
    seen.set(scroll.get());
    const fast = reducedMotion ? 0 : Math.abs(velocity.get());
    speed.set(speed.get() + (fast - speed.get()) * (1 - Math.exp(-dt * SPEED_RATE)));
    const pull = -KICK_STIFFNESS * kick.get() - KICK_DAMPING * kickVelocity.get();
    const kv = kickVelocity.get() + pull * dt;
    const next = kick.get() + kv * dt;
    // Held between rest and KICK_MAX; pressed against either end, it stops there.
    if (next >= KICK_MAX) {
      kick.set(KICK_MAX);
      kickVelocity.set(Math.min(kv, 0));
    } else if (next <= 0) {
      kick.set(0);
      kickVelocity.set(Math.max(kv, 0));
    } else {
      kick.set(next);
      kickVelocity.set(kv);
    }
  });

  // A selection tick each time another word reaches the front under the user's hand.
  useAnimatedReaction(
    () => Math.round(scroll.get()),
    (now, before) => {
      if (before === null || now === before) return;
      if (!reducedMotion) kickVelocity.set(Math.min(KICK_SPEED, kickVelocity.get() + KICK_SPEED));
      if (byUser.get()) scheduleOnRN(tick);
    },
  );

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    byUser.set(true);
  };

  const pan = Gesture.Pan()
    .minDistance(DRAG_SLOP)
    .onStart(() => {
      if (showing.get() > 0.5 || wiping.get()) return;
      touched();
      dragFrom.set(scroll.get());
      seen.set(scroll.get());
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      scroll.set(dragFrom.get() - event.translationY / DRAG_STEP);
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      dragging.set(false);
      // The finger's own speed at release, in words per second, carries into the spring.
      velocity.set(-event.velocityY / DRAG_STEP);
      target.set(snapTarget(scroll.get(), velocity.get()));
    });

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success || wiping.get()) return;
      touched();
      if (showing.get() > 0.5) {
        sweep();
        return;
      }
      // The word under the finger: rows sit RADIUS·sin(d·ANGLE) from the axis.
      let hit = 0;
      let best = Infinity;
      for (let d = -3; d <= 3; d += 1) {
        const y = axisY + RADIUS * Math.sin(d * ANGLE);
        const off = Math.abs(event.y - y);
        if (off < best) {
          best = off;
          hit = d;
        }
      }
      const front = Math.round(scroll.get()) + hit;
      if (hit === 0) {
        hold.set(CHOSEN_HOLD);
        choose(true);
      }
      else target.set(front);
    });

  // The arrow stays on the front row; each word arriving there kicks it to the right.
  const arrowTransform = useDerivedValue(() => [{ translateX: kick.get() }]);

  const drumStyle = useAnimatedStyle(() => ({ opacity: 1 - showing.get() }));
  const chosenStyle = useAnimatedStyle(() => ({
    opacity: showing.get(),
    transform: [{ translateY: reducedMotion ? 0 : interpolate(wipe.get(), [0.45, 1], [REVEAL_RISE, 0], 'clamp') }],
  }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: background }]}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`${lead}: ${words.join(', ')}`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width, height } = event.nativeEvent.layout;
          setSize({ width, height });
        }}
      >
        <Animated.View style={[StyleSheet.absoluteFill, drumStyle]}>
          <Text style={[styles.lead, { top: insets.top + 32, color: idleColor }]}>{lead}</Text>
          {size && (
            <Canvas style={StyleSheet.absoluteFill}>
              {words.map((word, index) => (
                <Row
                  key={index}
                  word={word}
                  index={index}
                  count={count}
                  scroll={scroll}
                  speed={speed}
                  kick={kick}
                  font={font}
                  axisY={axisY}
                  activeColor={activeColor}
                  idleColor={idleColor}
                />
              ))}
              <Group transform={arrowTransform}>
                <Path
                  path={`M ${ARROW_LEFT} ${axisY} L ${ARROW_LEFT + ARROW_WIDTH} ${axisY} M ${ARROW_LEFT + ARROW_WIDTH - ARROW_HEAD} ${axisY - ARROW_HEAD} L ${ARROW_LEFT + ARROW_WIDTH} ${axisY} L ${ARROW_LEFT + ARROW_WIDTH - ARROW_HEAD} ${axisY + ARROW_HEAD}`}
                  style="stroke"
                  strokeWidth={ARROW_STROKE}
                  strokeCap="round"
                  strokeJoin="round"
                  color={activeColor}
                />
              </Group>
            </Canvas>
          )}
          <Text style={[styles.hint, { bottom: insets.bottom + 28, color: idleColor }]}>Swipe to turn · Tap to choose</Text>
        </Animated.View>

        <Animated.View pointerEvents="none" style={[styles.chosen, chosenStyle]}>
          <Text style={[styles.chosenWord, { color: activeColor }]}>{words[chosen]}</Text>
          <Text style={[styles.promise, { color: textColor }]}>{promise}</Text>
          <Text style={[styles.back, { color: idleColor }]}>Tap to turn again</Text>
        </Animated.View>

        {size &&
          stripes.map((color, k) => (
            <Stripe key={k} index={k} color={color} width={size.width} wipe={wipe} />
          ))}
      </View>
    </GestureDetector>
  );
}

type StripeProps = { index: number; color: string; width: number; wipe: SharedValue<number> };

/** One full-height stripe crossing the screen from left to right. */
function Stripe({ index, color, width, wipe }: StripeProps) {
  const style = useAnimatedStyle(() => ({
    transform: [{ translateX: stripeAt(wipe.get(), index) * width }],
  }));
  return <Animated.View pointerEvents="none" style={[styles.stripe, { backgroundColor: color, width: width * STRIPE_LENGTH }, style]} />;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  lead: {
    position: 'absolute',
    left: ARROW_LEFT,
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: 0.4,
  },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
  },
  chosen: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    justifyContent: 'center',
    paddingHorizontal: ARROW_LEFT,
    gap: 10,
  },
  chosenWord: {
    fontFamily: FAMILY,
    fontSize: 56,
    fontWeight: '700',
    letterSpacing: -1,
  },
  promise: {
    fontFamily: FAMILY,
    fontSize: 24,
    fontWeight: '500',
    lineHeight: 31,
    maxWidth: 260,
  },
  back: {
    marginTop: 28,
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
  },
  stripe: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
  },
});

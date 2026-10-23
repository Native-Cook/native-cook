import { Canvas, Circle, Path, RoundedRect, interpolateColors } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type StepIcon = 'sparkles' | 'bell' | 'seal';

export type Step = {
  title: string;
  /** Two lines, split with a line break. */
  body: string;
  icon: StepIcon;
  /** The icon's colour and the soft colour of the tile behind it. */
  tint: string;
  tile: string;
};

type Props = {
  /** The pages, two or more; the last one turns the main button into "Finish". */
  steps?: Step[];
  continueLabel?: string;
  backLabel?: string;
  finishLabel?: string;
  /** Goes through the steps and back by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the step the flow moved to. */
  onStep?: (index: number) => void;
  /** Called when "Finish" is pressed. */
  onDone?: () => void;
};

const STEPS: Step[] = [
  { title: 'Set it up once', body: 'Three quick steps and\neverything is ready for you.', icon: 'sparkles', tint: '#0b6cf9', tile: '#e8f1ff' },
  { title: 'Stay in the loop', body: 'Only the nudges you ask for,\nnever the noise.', icon: 'bell', tint: '#1cbd51', tile: '#e7f8ee' },
  { title: "You're all set", body: 'Tap finish and\nstart exploring.', icon: 'seal', tint: '#f59e0b', tile: '#fff4e5' },
];

const BACKGROUND = '#fdfdfd';
const INK = '#111111';
const MUTE = '#6b7280';
const GREEN = '#1cbd51';
// The pill for a moment when the flow is finished.
const GREEN_BRIGHT = '#3ec76b';
const DOT = '#d9d9d9';
const BLUE = '#0b6cf9';
const BLUE_PRESSED = '#0957c8';
const GREY = '#f2f2f2';
const GREY_PRESSED = '#e2e2e2';

// The pager: dots SPACING apart, and one green pill that reaches from the first to the current one.
const PILL = 24;
const DOT_SIZE = 8;
const SPACING = 32;
// Room round the pill for the spring's overshoot.
const PAGER_PAD = 8;
// From the pager's middle down to the buttons.
const PAGER_GAP = 49;
const BUTTON = 56;
const SIDE = 26;
const BOTTOM = 22;
const BACK_WIDTH = 90;
const BUTTON_GAP = 8;
// "Back" comes out from under the main button at this size.
const BACK_SCALE = 0.6;
const CHECK_DISC = 18;
const CHECK = 'M5.2 9.3L7.9 12L12.9 6.5';
// A label leaves upwards and its successor arrives from below, both through a blur.
const LABEL_SLIDE = 6;
const LABEL_BLUR = 6;
const TILE = 168;
const TILE_RADIUS = 44;
const ICON = 72;
// A page next to the current one sits this far to its side, and its icon is this much smaller
// (after it) or larger (before it).
const SLIDE = 40;
const ZOOM = 0.28;
// The whole pager hops when the flow is finished.
const HOP = 10;

// The icons, in a 72 × 72 box. Sparkles: three four-point stars, each x, y and radius; PINCH is how
// far the sides of a star are pulled in.
const STARS = [
  [30, 41, 25],
  [55, 18, 11],
  [57, 55, 8],
];
const PINCH = 0.16;
const BELL =
  'M36 10.5C25.2 10.5 18 18.6 18 28.8L18 39.6C18 42.9 16.5 45.6 13.8 48.3C12.3 49.8 13.2 52.5 15.6 52.5L56.4 52.5C58.8 52.5 59.7 49.8 58.2 48.3C55.5 45.6 54 42.9 54 39.6L54 28.8C54 18.6 46.8 10.5 36 10.5Z';
const CLAPPER = 'M29.4 57C30.3 60 33 61.8 36 61.8C39 61.8 41.7 60 42.6 57Z';
// The bell's badge: a dot with a gap of the tile's colour round it.
const BADGE = [51, 19, 8.5, 12.5];
// The seal: a disc with ten bumps on its edge, and a check cut out of it.
const SEAL_CORE = 24;
const SEAL_BUMPS = 10;
const SEAL_BUMP = 8.6;
const SEAL_BUMP_AT = 21.5;
const SEAL_CHECK = 'M25 37.5L32.5 45L47.5 28.5';

/** autoPlay: "Continue" at FIRST and every BETWEEN after it, HOLD on the last step, then back to the first. */
export const LOOP = 8;
const FIRST = 1.6;
const BETWEEN = 2;
const HOLD = 2.4;
// What is left of the loop once it is back on the first step.
const AFTER = 2;
// A button the loop presses looks pressed this long before it acts.
const PRESS_FOR = 0.14;
// After a touch autoPlay waits this long before it goes back to the first step.
const USER_HOLD = 3;

const NEVER = ReduceMotion.Never;
/** The pill and the pages: it overshoots by about 3 points of the 32 between two dots. */
const DOTS_SPRING = { mass: 1, stiffness: 170, damping: 16, reduceMotion: NEVER };
/** The main button's left edge and "Back": the same pace, less bounce over the longer way. */
const BAR_SPRING = { mass: 1, stiffness: 170, damping: 19.5, reduceMotion: NEVER };
/** The check's disc pops to 1.15 before it settles. */
const POP_SPRING = { mass: 1, stiffness: 300, damping: 18, reduceMotion: NEVER };
const POP_OUT = 150;
// The labels change over SWAP: the old one is gone by SWAP_OUT of it, the new one starts at SWAP_IN.
const SWAP = 300;
const SWAP_OUT = 0.5;
const SWAP_IN = 1 / 3;
const PARTY = 500;
// Under reduced motion everything changes in one short step.
const JUMP = 200;
const SWIPE = 40;
const SWIPE_SPEED = 500;
// The blur of a changing label is a filter, which Android has from version 12 and iOS has not.
const CAN_BLUR = Platform.OS === 'android' && Number(Platform.Version) >= 31;

function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

function star(cx: number, cy: number, r: number) {
  const k = r * PINCH;
  return `M${cx} ${cy - r}Q${cx + k} ${cy - k} ${cx + r} ${cy}Q${cx + k} ${cy + k} ${cx} ${cy + r}Q${cx - k} ${cy + k} ${cx - r} ${cy}Q${cx - k} ${cy - k} ${cx} ${cy - r}Z`;
}

const SPARKLES = STARS.map(([cx, cy, r]) => star(cx, cy, r)).join('');

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Icon({ kind, tint, tile }: { kind: StepIcon; tint: string; tile: string }) {
  return (
    <Canvas style={styles.icon}>
      {kind === 'sparkles' && <Path path={SPARKLES} color={tint} />}
      {kind === 'bell' && (
        <>
          <Path path={BELL} color={tint} />
          <Path path={CLAPPER} color={tint} />
          <Circle cx={BADGE[0]} cy={BADGE[1]} r={BADGE[3]} color={tile} />
          <Circle cx={BADGE[0]} cy={BADGE[1]} r={BADGE[2]} color={tint} />
        </>
      )}
      {kind === 'seal' && (
        <>
          <Circle cx={ICON / 2} cy={ICON / 2} r={SEAL_CORE} color={tint} />
          {Array.from({ length: SEAL_BUMPS }, (_, bump) => {
            const angle = (bump / SEAL_BUMPS) * 2 * Math.PI;
            return <Circle key={bump} cx={ICON / 2 + SEAL_BUMP_AT * Math.cos(angle)} cy={ICON / 2 + SEAL_BUMP_AT * Math.sin(angle)} r={SEAL_BUMP} color={tint} />;
          })}
          <Path path={SEAL_CHECK} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={tile} />
        </>
      )}
    </Canvas>
  );
}

/** One page: it is in place when the progress is on its index, and fades out half a step away. */
function Page({ step, index, current, progress, still }: { step: Step; index: number; current: boolean; progress: SharedValue<number>; still: boolean }) {
  const style = useAnimatedStyle(() => {
    const by = index - progress.get();
    return { opacity: clamp01(1 - 2 * Math.abs(by)), transform: [{ translateX: still ? 0 : SLIDE * by }] };
  });
  const iconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: still ? 1 : 1 - ZOOM * Math.max(-1, Math.min(1, index - progress.get())) }],
  }));
  return (
    <Animated.View
      style={[styles.page, style]}
      accessibilityElementsHidden={!current}
      importantForAccessibility={current ? 'auto' : 'no-hide-descendants'}
    >
      <View style={[styles.tile, { backgroundColor: step.tile }]}>
        <Animated.View style={iconStyle}>
          <Icon kind={step.icon} tint={step.tint} tile={step.tile} />
        </Animated.View>
      </View>
      <Text style={styles.title} numberOfLines={1}>
        {step.title}
      </Text>
      <Text style={styles.body} numberOfLines={2}>
        {step.body}
      </Text>
    </Animated.View>
  );
}

/** A dot of the pager: grey until the pill reaches it, white inside the pill. */
function Dot({ index, progress }: { index: number; progress: SharedValue<number> }) {
  // The pill's end is half a step short of the next dot when it sets off, and covers it a quarter later.
  const color = useDerivedValue(() => interpolateColors(index === 0 ? 1 : clamp01((progress.get() - index + 0.5) / 0.25), [0, 1], [DOT, '#ffffff']));
  return <Circle cx={PAGER_PAD + PILL / 2 + SPACING * index} cy={PAGER_PAD + PILL / 2} r={DOT_SIZE / 2} color={color} />;
}

/**
 * The controls of an onboarding flow, with a page per step above them. The pager is one green pill
 * that stretches from the first dot to the current one, swallowing the dots on its way; "Back"
 * comes out from under the main button, which gives it room, and on the last step the button's
 * label changes to "Finish" behind a check that pops in.
 */
export function MergingStepDots({
  steps = STEPS,
  continueLabel = 'Continue',
  backLabel = 'Back',
  finishLabel = 'Finish',
  autoPlay = false,
  onStep,
  onDone,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const last = steps.length - 1;

  const [step, setStep] = useState(0);
  // A button held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState<'next' | 'back' | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The step, on its way from one to the next: the pill and the pages follow it.
  const progress = useSharedValue(0);
  // 0 with the main button alone, 1 once it has made room for "Back".
  const bar = useSharedValue(0);
  // 0 for "Continue", 1 for "Finish".
  const swap = useSharedValue(0);
  const check = useSharedValue(0);
  // 0 to 1 once after "Finish": the pill flashes and the pager hops.
  const party = useSharedValue(1);

  const bottom = Math.max(insets.bottom, 12) + BOTTOM;
  const pagerWidth = PILL + SPACING * last + PAGER_PAD * 2;
  const pagerHeight = PILL + PAGER_PAD * 2;

  const goTo = (to: number) => {
    const next = Math.min(last, Math.max(0, to));
    if (next === step) return;
    setStep(next);
    const open = next > 0 ? 1 : 0;
    const done = next === last ? 1 : 0;
    const jump = { duration: JUMP, reduceMotion: NEVER };
    progress.set(reducedMotion ? withTiming(next, jump) : withSpring(next, DOTS_SPRING));
    bar.set(reducedMotion ? withTiming(open, jump) : withSpring(open, BAR_SPRING));
    swap.set(withTiming(done, { duration: SWAP, easing: Easing.linear, reduceMotion: NEVER }));
    check.set(done && !reducedMotion ? withSpring(1, POP_SPRING) : withTiming(done, { duration: POP_OUT, reduceMotion: NEVER }));
    onStep?.(next);
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(null);
    setTouches((n) => n + 1);
  };

  const forward = () => {
    touched();
    if (step < last) {
      tick();
      goTo(step + 1);
      return;
    }
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    if (!reducedMotion) {
      party.set(0);
      party.set(withTiming(1, { duration: PARTY, easing: Easing.linear, reduceMotion: NEVER }));
    }
    onDone?.();
  };

  const back = () => {
    touched();
    tick();
    goTo(step - 1);
  };

  /** A swipe over the page: on to the next step, or back; it does not finish the flow. */
  const swiped = (by: number) => {
    touched();
    if (step + by < 0 || step + by > last) return;
    tick();
    goTo(step + by);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ goTo });
  useEffect(() => {
    latest.current = { goTo };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // One round, timed from `origin` so that rounds do not drift: each button is pressed, then acts.
    const cycle = (origin: number) => {
      const at = (seconds: number) => origin + seconds * 1000 - Date.now();
      for (let k = 0; k < last; k++) {
        after(at(FIRST + k * BETWEEN - PRESS_FOR), () => setAutoPressed('next'));
        after(at(FIRST + k * BETWEEN), () => {
          setAutoPressed(null);
          latest.current.goTo(k + 1);
        });
      }
      const home = FIRST + (last - 1) * BETWEEN + HOLD;
      after(at(home - PRESS_FOR), () => setAutoPressed('back'));
      after(at(home), () => {
        setAutoPressed(null);
        latest.current.goTo(0);
      });
      after(at(home + AFTER), () => cycle(origin + (home + AFTER) * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        latest.current.goTo(0);
        cycle(Date.now());
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, last]);

  const pan = Gesture.Pan()
    .activeOffsetX([-16, 16])
    .failOffsetY([-24, 24])
    .onEnd((event) => {
      if (event.translationX < -SWIPE || event.velocityX < -SWIPE_SPEED) scheduleOnRN(swiped, 1);
      else if (event.translationX > SWIPE || event.velocityX > SWIPE_SPEED) scheduleOnRN(swiped, -1);
    });

  const pillWidth = useDerivedValue(() => Math.max(DOT_SIZE * 2, PILL + SPACING * progress.get()));
  const pillColor = useDerivedValue(() => interpolateColors(Math.sin(Math.PI * party.get()), [0, 1], [GREEN, GREEN_BRIGHT]));
  const pagerStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -HOP * Math.sin(Math.PI * party.get()) }] }));

  const backStyle = useAnimatedStyle(() => ({
    opacity: clamp01(bar.get() * 1.5),
    transform: [{ scale: BACK_SCALE + (1 - BACK_SCALE) * bar.get() }],
  }));
  const nextStyle = useAnimatedStyle(() => ({ left: (BACK_WIDTH + BUTTON_GAP) * bar.get() }));
  const continueStyle = useAnimatedStyle(() => {
    const gone = clamp01(swap.get() / SWAP_OUT);
    const style = { opacity: 1 - gone, transform: [{ translateY: -LABEL_SLIDE * gone }] };
    return CAN_BLUR ? { ...style, filter: [{ blur: LABEL_BLUR * gone }] as unknown as string } : style;
  });
  const finishStyle = useAnimatedStyle(() => {
    const here = clamp01((swap.get() - SWAP_IN) / (1 - SWAP_IN));
    const style = { opacity: here, transform: [{ translateY: LABEL_SLIDE * (1 - here) }] };
    return CAN_BLUR ? { ...style, filter: [{ blur: LABEL_BLUR * (1 - here) }] as unknown as string } : style;
  });
  const checkStyle = useAnimatedStyle(() => ({ transform: [{ scale: Math.max(0, check.get()) }] }));

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />

      <GestureDetector gesture={pan}>
        <View style={[styles.pages, { top: insets.top, bottom: bottom + BUTTON + PAGER_GAP + PILL / 2 }]}>
          {steps.map((item, index) => (
            <Page key={item.title} step={item} index={index} current={index === step} progress={progress} still={reducedMotion} />
          ))}
        </View>
      </GestureDetector>

      <Animated.View
        style={[styles.pager, { width: pagerWidth, height: pagerHeight, bottom: bottom + BUTTON + PAGER_GAP - pagerHeight / 2 }, pagerStyle]}
        accessible
        accessibilityRole="progressbar"
        accessibilityValue={{ text: `Step ${step + 1} of ${steps.length}` }}
      >
        <Canvas style={styles.fill}>
          <RoundedRect x={PAGER_PAD} y={PAGER_PAD} width={pillWidth} height={PILL} r={PILL / 2} color={pillColor} />
          {steps.map((item, index) => (
            <Dot key={item.title} index={index} progress={progress} />
          ))}
        </Canvas>
      </Animated.View>

      <View style={[styles.row, { left: SIDE, right: SIDE, bottom }]}>
        {/* Under the main button, which hides it until it has moved over. */}
        <Animated.View style={[styles.backSlot, backStyle]} pointerEvents={step > 0 ? 'auto' : 'none'}>
          <Pressable
            accessibilityRole="button"
            accessibilityElementsHidden={step === 0}
            importantForAccessibility={step > 0 ? 'auto' : 'no-hide-descendants'}
            onPress={back}
          >
            {({ pressed }) => (
              <Animated.View
                style={[
                  styles.button,
                  { backgroundColor: pressed || autoPressed === 'back' ? GREY_PRESSED : GREY, transitionProperty: 'backgroundColor', transitionDuration: 100 },
                ]}
              >
                <Text style={[styles.label, styles.backLabel]} numberOfLines={1}>
                  {backLabel}
                </Text>
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>

        <Animated.View style={[styles.nextSlot, nextStyle]}>
          <Pressable accessibilityRole="button" accessibilityLabel={step === last ? finishLabel : continueLabel} onPress={forward}>
            {({ pressed }) => (
              <Animated.View
                style={[
                  styles.button,
                  { backgroundColor: pressed || autoPressed === 'next' ? BLUE_PRESSED : BLUE, transitionProperty: 'backgroundColor', transitionDuration: 100 },
                ]}
              >
                <Animated.Text style={[styles.label, styles.nextLabel, continueStyle]} numberOfLines={1}>
                  {continueLabel}
                </Animated.Text>
                <Animated.View style={[styles.finish, finishStyle]}>
                  <Animated.View style={[styles.checkDisc, checkStyle]}>
                    <Canvas style={styles.fill}>
                      <Path path={CHECK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={BLUE} />
                    </Canvas>
                  </Animated.View>
                  <Text style={[styles.label, styles.nextLabel]} numberOfLines={1}>
                    {finishLabel}
                  </Text>
                </Animated.View>
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  pages: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  page: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tile: {
    width: TILE,
    height: TILE,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    width: ICON,
    height: ICON,
  },
  title: {
    marginTop: 36,
    color: INK,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '700',
    textAlign: 'center',
    includeFontPadding: false,
  },
  body: {
    marginTop: 12,
    color: MUTE,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
    includeFontPadding: false,
  },
  pager: {
    position: 'absolute',
    alignSelf: 'center',
  },
  row: {
    position: 'absolute',
    height: BUTTON,
  },
  backSlot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BACK_WIDTH,
  },
  nextSlot: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  button: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
  backLabel: {
    color: INK,
  },
  nextLabel: {
    color: '#ffffff',
  },
  finish: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
  },
  checkDisc: {
    width: CHECK_DISC,
    height: CHECK_DISC,
    borderRadius: CHECK_DISC / 2,
    marginRight: 8,
    backgroundColor: '#ffffff',
  },
});

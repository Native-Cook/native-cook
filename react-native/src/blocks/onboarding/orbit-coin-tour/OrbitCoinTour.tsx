import { Canvas, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CLOSE_AT,
  COAST,
  COAST_REACH,
  COIN,
  COINS,
  COIN_TOP,
  CX,
  CY,
  FADE,
  LOOP,
  OPEN_AT,
  PHASE_AT_START,
  PUSH,
  REVEAL,
  REVEAL_AFTER,
  SECOND_AT,
  SETTLE,
  SLIDE,
  STAGE_HEIGHT,
  STAGE_WIDTH,
  THIRD_AT,
  USER_HOLD,
  coinPicture,
  coinPlace,
  orbitPicture,
  smooth,
} from './Orbit';
import { Insights, PayCard, Savings, type SceneText } from './Scenes';

type Props = {
  /** Under the orbit. */
  caption?: string;
  /** The title and the line under it of the three scenes: savings, card, insights. */
  scenes?: SceneText[];
  /** The balance on the savings and the insights card. */
  balance?: number;
  /** Written before an amount. */
  symbol?: string;
  /** Plays the orbit and the three scenes in turn, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with 0 for the orbit and 1 to 3 for a scene when it comes to the front. */
  onScene?: (scene: number) => void;
  /** Called when the savings card's button is pressed. */
  onAction?: () => void;
};

const SCENES: SceneText[] = [
  { title: 'Savings Account', subtitle: 'Pay. Track. Save. Invest.' },
  { title: 'The 3-in-1 Credit Card', subtitle: 'Assured 2% cashback' },
  { title: 'Get powerful Insights', subtitle: 'Track all your spends' },
];

const SAND = '#EDE4D9';
const INK = '#37342E';
const GROUNDS = [SAND, '#367168', '#C9BEB2'];

const NEVER = ReduceMotion.Never;
const LINEAR = Easing.linear;
const IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const OUT = Easing.bezier(0.23, 1, 0.32, 1);
const COAST_CURVE = Easing.bezier(0.16, 1, 0.3, 1);
// As long as anyone will watch: the orbit's clock.
const FOREVER = 3600;
// A swipe down by this much leaves the tour; a flick carries a page on by this many seconds of its speed.
const LEAVE = 80;
const FLICK = 0.2;
// The first and the last scene give this much of a page when pulled further.
const OVER = 0.2;
const UNSPIN = 600;
const COIN_BOX = (COIN + 8) * 2;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type PageProps = {
  index: number;
  page: SharedValue<number>;
  width: number;
  scale: number;
  still: boolean;
  children: ReactNode;
};

/** One scene: its ground, its coin at the top and its card, sliding with the pager. */
function Page({ index, page, width, scale, still, children }: PageProps) {
  // Reduced motion: scenes cross-fade in place.
  const slide = useAnimatedStyle(() =>
    still ? { opacity: 1 - Math.min(1, Math.abs(index - page.get())) } : { transform: [{ translateX: (index - page.get()) * width }] },
  );
  // The coin arrives turning: edge-on half-way, face-on when the scene is in front.
  const coin = useDerivedValue(() =>
    coinPicture(COINS[index - 1].icon, still ? 1 : Math.cos(Math.PI * Math.min(1, Math.max(-1, index - page.get())))),
  );
  return (
    <Animated.View style={[styles.fill, styles.centre, { backgroundColor: GROUNDS[index - 1] }, slide]}>
      <View style={[styles.stage, { transform: [{ scale }] }]}>
        {children}
        <Canvas style={[styles.coin, { top: COIN_TOP[index - 1] - COIN_BOX / 2 }]} pointerEvents="none">
          <Picture picture={coin} />
        </Canvas>
      </View>
    </Animated.View>
  );
}

/**
 * A feature tour. Three coins ride the rings round a coral disc; a tap on one pushes the camera
 * through the rings while the coin flies to the top of the screen and the disc opens into that
 * product's card. Swipes go from scene to scene, each on its own ground, and a swipe down or a
 * tap on the coin pulls back out to the orbit. In the orbit a drag spins the rings.
 */
export function OrbitCoinTour({
  caption = 'Your financial\nwellness account',
  scenes = SCENES,
  balance = 23520,
  symbol = '$',
  autoPlay = false,
  onScene,
  onAction,
}: Props) {
  const still = useReducedMotion();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const scale = Math.min(size.width / STAGE_WIDTH, size.height / STAGE_HEIGHT);
  const left = (size.width - STAGE_WIDTH * scale) / 2;
  const top = (size.height - STAGE_HEIGHT * scale) / 2;

  // 0 is the orbit, 1 to 3 the scene in front.
  const [scene, setScene] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // A finger is down: the loop waits.
  const [holding, setHolding] = useState(false);

  // How far the push from the orbit into the tour has gone, at a steady pace.
  const tour = useSharedValue(0);
  // The scene in front, 1 to 3, between two of them while they slide.
  const page = useSharedValue(1);
  // The scene the disc opens into, and shrinks back from.
  const hero = useSharedValue(1);
  // The orbit's clock in seconds, and what the hand added to the rings in radians.
  const phase = useSharedValue(PHASE_AT_START);
  const spin = useSharedValue(0);
  const first = useSharedValue(0);
  const second = useSharedValue(0);
  const third = useSharedValue(0);
  // The drag: 1 spins the rings, 2 turns the pages, 3 pulls down to leave.
  const mode = useSharedValue(0);
  const grab = useSharedValue(0);

  const run = (from: number) => {
    phase.set(from);
    phase.set(withTiming(from + FOREVER, { duration: FOREVER * 1000, easing: LINEAR, reduceMotion: NEVER }));
  };

  useEffect(() => {
    if (still) return;
    phase.set(PHASE_AT_START);
    phase.set(withTiming(PHASE_AT_START + FOREVER, { duration: FOREVER * 1000, easing: LINEAR, reduceMotion: NEVER }));
    return () => cancelAnimation(phase);
  }, [phase, still]);

  useEffect(
    () => () => {
      [tour, page, spin, first, second, third].forEach(cancelAnimation);
    },
    [tour, page, spin, first, second, third],
  );

  /** Starts scene `to`'s contents coming in, `after` seconds from now. */
  const reveal = (to: number, after: number) => {
    const value = [first, second, third][to - 1];
    value.set(still ? 1 : 0);
    if (!still) value.set(withDelay(after * 1000, withTiming(1, { duration: REVEAL * 1000, easing: LINEAR, reduceMotion: NEVER })));
  };

  /** From the orbit into scene `to`. */
  const open = (to: number) => {
    setScene(to);
    onScene?.(to);
    hero.set(to);
    page.set(to);
    reveal(to, PUSH * 0.8);
    tour.set(
      withTiming(1, { duration: (still ? FADE : PUSH) * 1000, easing: LINEAR, reduceMotion: NEVER }, (finished) => {
        // Nothing of the orbit shows any more.
        if (finished) cancelAnimation(phase);
      }),
    );
  };

  /** To scene `to` from the one in front, in `seconds`. */
  const go = (to: number, seconds = SLIDE) => {
    if (to !== scene) {
      setScene(to);
      onScene?.(to);
      reveal(to, seconds === SLIDE ? REVEAL_AFTER : seconds * 0.6);
    }
    hero.set(to);
    page.set(withTiming(to, { duration: (still ? FADE : seconds) * 1000, easing: seconds === SLIDE ? IN_OUT : OUT, reduceMotion: NEVER }));
  };

  /** Back out to the orbit, whose clock starts again. */
  const close = () => {
    setScene(0);
    onScene?.(0);
    if (!still) run(0);
    tour.set(withTiming(0, { duration: (still ? FADE : PUSH) * 1000, easing: LINEAR, reduceMotion: NEVER }));
  };

  const unspin = () => spin.set(withTiming(0, { duration: UNSPIN, easing: OUT, reduceMotion: NEVER }));

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ open, go, close, unspin, scene });
  useEffect(() => {
    latest.current = { open, go, close, unspin, scene };
  });

  useEffect(() => {
    if (!autoPlay || holding) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, step: () => void) => timers.push(setTimeout(step, ms));
    const cycle = (origin: number) => {
      after(origin + OPEN_AT * 1000 - Date.now(), () => latest.current.open(1));
      after(origin + SECOND_AT * 1000 - Date.now(), () => latest.current.go(2));
      after(origin + THIRD_AT * 1000 - Date.now(), () => latest.current.go(3));
      after(origin + CLOSE_AT * 1000 - Date.now(), () => latest.current.close());
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        // Back to the orbit with the rings as they were; a loop begins this long after a pull back.
        const inside = latest.current.scene !== 0;
        if (inside) latest.current.close();
        latest.current.unspin();
        after((inside ? PHASE_AT_START : UNSPIN / 1000) * 1000, () => cycle(Date.now()));
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, holding]);

  /** A touch takes over from the autoPlay loop, which starts again from the orbit a while later. */
  const touched = () => setTouches((n) => n + 1);

  const pick = (to: number) => {
    touched();
    tick();
    open(to);
  };

  const leave = () => {
    touched();
    tick();
    go(scene || 1, SETTLE);
    close();
  };

  const settle = (to: number) => {
    if (to !== scene) tick();
    go(to, SETTLE);
  };

  const released = () => {
    setHolding(false);
    touched();
  };

  const press = Gesture.Tap().onEnd((event) => {
    const x = (event.x - left) / scale;
    const y = (event.y - top) / scale;
    if (tour.get() === 0) {
      for (let i = 0; i < COINS.length; i++) {
        const place = coinPlace(i, phase.get(), spin.get());
        if (Math.hypot(x - place.x, y - place.y) < COIN + 10) {
          scheduleOnRN(pick, i + 1);
          return;
        }
      }
    } else if (tour.get() === 1) {
      // The coin at the top of a scene is the way back.
      if (Math.hypot(x - CX, y - COIN_TOP[Math.round(page.get()) - 1]) < COIN + 10) scheduleOnRN(leave);
    }
  });

  const drag = Gesture.Pan()
    .minDistance(8)
    .onStart((event) => {
      scheduleOnRN(setHolding, true);
      if (tour.get() === 0) {
        mode.set(1);
        cancelAnimation(spin);
        grab.set(Math.atan2((event.y - top) / scale - CY, (event.x - left) / scale - CX));
      } else if (tour.get() === 1) {
        const across = Math.abs(event.translationX) > Math.abs(event.translationY);
        mode.set(across ? 2 : 3);
        cancelAnimation(page);
        grab.set(page.get() + event.translationX / size.width);
      } else {
        mode.set(0);
      }
    })
    .onUpdate((event) => {
      if (mode.get() === 1) {
        // The rings turn by the angle the finger sweeps round the centre.
        const angle = Math.atan2((event.y - top) / scale - CY, (event.x - left) / scale - CX);
        let swept = angle - grab.get();
        swept -= 2 * Math.PI * Math.round(swept / (2 * Math.PI));
        spin.set(spin.get() + swept);
        grab.set(angle);
      } else if (mode.get() === 2) {
        page.set(Math.min(3 + OVER, Math.max(1 - OVER, grab.get() - event.translationX / size.width)));
      }
    })
    .onEnd((event) => {
      if (mode.get() === 1) {
        // They coast on at the speed the finger had round the centre.
        const rx = (event.x - left) / scale - CX;
        const ry = (event.y - top) / scale - CY;
        const speed = (rx * event.velocityY - ry * event.velocityX) / scale / Math.max(1, rx * rx + ry * ry);
        spin.set(withTiming(spin.get() + speed * COAST_REACH, { duration: COAST * 1000, easing: COAST_CURVE, reduceMotion: NEVER }));
      } else if (mode.get() === 2) {
        const to = Math.round(page.get() - (event.velocityX / size.width) * FLICK);
        scheduleOnRN(settle, Math.min(3, Math.max(1, to)));
      } else if (mode.get() === 3 && event.translationY > LEAVE) {
        scheduleOnRN(leave);
      }
    })
    .onFinalize(() => {
      mode.set(0);
      scheduleOnRN(released);
    });

  const orbit = useDerivedValue(() => orbitPicture(size.width, size.height, scale, still ? 0 : tour.get(), hero.get(), phase.get(), spin.get()));
  // The scenes take over at the end of the push, when the disc has become the card's top.
  const orbitStyle = useAnimatedStyle(() => ({ opacity: still ? 1 - tour.get() : 1 - smooth(tour.get(), 0.85, 1) }));
  const captionStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(tour.get(), 0, 0.3) }));
  const pagerStyle = useAnimatedStyle(() => ({ opacity: still ? tour.get() : smooth(tour.get(), 0.8, 0.95) }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const shared = { balance, symbol };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style={scene === 2 ? 'light' : 'dark'} />
      <GestureDetector gesture={Gesture.Exclusive(drag, press)}>
        <View
          style={styles.fill}
          accessible={scene === 0}
          accessibilityRole="adjustable"
          accessibilityLabel={scene === 0 ? caption.replace('\n', ' ') : scenes[scene - 1].title}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            const to = scene + (event.nativeEvent.actionName === 'increment' ? 1 : -1);
            if (scene === 0 && to === 1) pick(1);
            else if (to === 0) leave();
            else if (to >= 1 && to <= 3) settle(to);
          }}
        >
          <Animated.View style={[styles.fill, orbitStyle]} pointerEvents="none">
            <Canvas style={styles.fill}>
              <Picture picture={orbit} />
            </Canvas>
            <View style={[styles.fill, styles.centre]}>
              <View style={[styles.stage, { transform: [{ scale }] }]}>
                <Animated.Text style={[styles.caption, captionStyle]}>{caption}</Animated.Text>
              </View>
            </View>
          </Animated.View>

          <Animated.View style={[styles.fill, styles.pager, pagerStyle]} pointerEvents={scene === 0 ? 'none' : 'auto'}>
            <Page index={1} page={page} width={size.width} scale={scale} still={still}>
              <Savings reveal={first} text={scenes[0]} onAction={onAction} {...shared} />
            </Page>
            <Page index={2} page={page} width={size.width} scale={scale} still={still}>
              <PayCard reveal={second} text={scenes[1]} {...shared} />
            </Page>
            <Page index={3} page={page} width={size.width} scale={scale} still={still}>
              <Insights reveal={third} text={scenes[2]} {...shared} />
            </Page>
          </Animated.View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SAND,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  centre: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  pager: {
    overflow: 'hidden',
  },
  stage: {
    width: STAGE_WIDTH,
    height: STAGE_HEIGHT,
  },
  coin: {
    position: 'absolute',
    left: CX - COIN_BOX / 2,
    width: COIN_BOX,
    height: COIN_BOX,
  },
  caption: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 683,
    color: INK,
    fontSize: 34,
    lineHeight: 43,
    fontWeight: '300',
    textAlign: 'center',
    includeFontPadding: false,
  },
});

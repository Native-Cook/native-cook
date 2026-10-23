import { Canvas, PaintStyle, Path, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  COMMIT,
  DRAG,
  type Figure,
  HOME_AT,
  LOOP,
  MOVE,
  NEXT_AT,
  PAGES,
  type Page,
  RINGS,
  SETTLE,
  SETTLE_AFTER,
  USER_HOLD,
  breath,
  ease,
  ringValue,
  runLength,
  textShift,
  turned,
} from './figures';

type Props = {
  /** The pages: a title, the figure the page arrives with and the one it settles into. */
  pages?: Page[];
  /** The two lines under every title. */
  body?: string;
  /** The lines of the figures. */
  ink?: string;
  /** The titles and the round button. */
  accent?: string;
  /** Goes through the first four pages and back to the first by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the page's index when a page becomes the one shown. */
  onPageChange?: (index: number) => void;
};

const PAPER = '#FFFFFF';
const INK = '#16305C';
const ACCENT = '#1B3F95';
const MUTE = '#8A8F9A';

/** The artwork's centre, as a fraction of the screen's height. */
const ART_AT = 0.387;
const STROKE = 0.75;
const MARGIN = 24;
const BUTTON = 36;
/** The button's centre from the right edge and from the bottom safe area. */
const BUTTON_RIGHT = 41;
const BUTTON_UP = 58;
const TITLE_GAP = 16;
/** A down arrow in a 14 × 14 box. */
const ARROW = Skia.Path.MakeFromSVGString('M7 1.5V12.5M2.5 8L7 12.5L11.5 8')!;

const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

/** The run in progress, on the UI thread. */
type Run = {
  /** Where the rings were when the run began: RINGS × 6 numbers. */
  from: SharedValue<number[]>;
  /** The figures the run goes to, as places in the list of figures (two per page). */
  first: SharedValue<number>;
  second: SharedValue<number>;
  /** Seconds into the run. A drag sets it by hand, between 0 and MOVE. */
  r: SharedValue<number>;
  /** The page the texts leave and the one they arrive at. */
  leaving: SharedValue<number>;
  arriving: SharedValue<number>;
  /** The page shown: it changes when a change of page is certain. */
  page: SharedValue<number>;
  /** 1 while the run's clock is going or a finger drags. */
  active: SharedValue<number>;
};

/** Where every ring is now, as the flat list a run starts from. */
function snapshot(run: Run, figures: Figure[]) {
  'worklet';
  const from = run.from.get();
  const first = figures[run.first.get()];
  const second = figures[run.second.get()];
  const now: number[] = [];
  for (let k = 0; k < RINGS; k++) for (let i = 0; i < 6; i++) now.push(ringValue(from, first, second, run.r.get(), k, i));
  return now;
}

/** A change of page is on its way and can no longer be stopped. */
function changing(run: Run) {
  'worklet';
  return run.leaving.get() !== run.arriving.get() && run.r.get() < MOVE && run.page.get() === run.arriving.get();
}

/** Lets the run's clock go from where it is to the end of the run. `late`: how long ago it was due. */
function play(run: Run, late: number) {
  'worklet';
  const end = runLength(run.first.get() !== run.second.get());
  run.active.set(1);
  run.r.set(
    withTiming(end, { duration: Math.max(0, end - run.r.get() - late) * 1000, ...LINEAR }, (finished) => {
      if (finished) run.active.set(0);
    }),
  );
}

/** Starts the change to page `to`: the rings go to its figure, wait, and settle. */
function goTo(run: Run, figures: Figure[], to: number, late: number) {
  'worklet';
  if (changing(run)) return;
  run.from.set(snapshot(run, figures));
  run.leaving.set(run.page.get());
  run.arriving.set(to);
  run.page.set(to);
  run.first.set(to * 2);
  run.second.set(to * 2 + 1);
  run.r.set(0);
  play(run, late);
}

/** Brings the rings from wherever they are to the settled figure of the page shown. */
function resettle(run: Run, figures: Figure[]) {
  'worklet';
  const page = run.page.get();
  run.from.set(snapshot(run, figures));
  run.leaving.set(page);
  run.arriving.set(page);
  run.first.set(page * 2 + 1);
  run.second.set(page * 2 + 1);
  run.r.set(0);
  play(run, 0);
}

function Arrow({ color }: { color: string }) {
  return (
    <Canvas style={styles.arrow}>
      <Path path={ARROW} style="stroke" strokeWidth={1.2} strokeCap="round" strokeJoin="round" color={color} />
    </Canvas>
  );
}

function Words({ run, index, calm, title, body, color, bottom }: {
  run: Run;
  index: number;
  calm: boolean;
  title: string;
  body: string;
  color: string;
  bottom: number;
}) {
  const style = useAnimatedStyle(() => {
    const leaving = run.leaving.get();
    const arriving = run.arriving.get();
    if (index !== leaving && index !== arriving) return { opacity: 0, transform: [{ translateY: 0 }] };
    const now = textShift(run.r.get(), leaving === arriving ? 0 : index === arriving ? 1 : -1, calm);
    return { opacity: now.opacity, transform: [{ translateY: now.y }] };
  });
  return (
    <Animated.View style={[styles.words, { bottom }, style]}>
      <Text style={[styles.title, { color }]}>{title}</Text>
      <Text style={styles.body}>{body}</Text>
    </Animated.View>
  );
}

/**
 * An intro pager whose only artwork is one set of hairline ellipses. Every page arranges them
 * into another figure (a cone, two turning spheres, a lens, an hourglass…), and a change of page
 * sends each ellipse from its place in one figure to its place in the next while the texts slide.
 * Swipe up or tap the round arrow for the next page, swipe down for the one before.
 */
export function SpirographPager({
  pages = PAGES,
  body = 'Discover how we can\nelevate your business',
  ink = INK,
  accent = ACCENT,
  autoPlay = false,
  onPageChange,
}: Props) {
  const calm = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [shown, setShown] = useState(0);
  // The rings are on their way or under a finger.
  const [active, setActive] = useState(true);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Two figures per page: the one it arrives with and the one it settles into.
  const figures = useMemo(() => pages.flatMap((item) => [item.figure, item.settled ?? item.figure]), [pages]);
  const count = pages.length;

  // The block opens on the first page as if it had just arrived there.
  const from = useSharedValue(figures[0].flat());
  const first = useSharedValue(0);
  const second = useSharedValue(1);
  const r = useSharedValue(MOVE);
  const leaving = useSharedValue(0);
  const arriving = useSharedValue(0);
  const page = useSharedValue(0);
  const running = useSharedValue(1);
  const run: Run = useMemo(
    () => ({ from, first, second, r, leaving, arriving, page, active: running }),
    [from, first, second, r, leaving, arriving, page, running],
  );
  // Seconds since the block appeared, for the breathing and the spheres' turn.
  const clock = useSharedValue(0);
  // The page a drag started on, and the way it goes: 1 to the next page, -1 to the one before.
  const home = useSharedValue(0);
  const way = useSharedValue(0);
  const byHand = useSharedValue(false);

  useFrameCallback((frame) => {
    clock.set(frame.timeSinceFirstFrame / 1000);
  }, !calm);

  useEffect(() => {
    scheduleOnUI(play, run, 0);
    return () => cancelAnimation(run.r);
  }, [run]);

  const turnedPage = (index: number, hand: boolean) => {
    setShown(index);
    if (hand && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onPageChange?.(index);
  };
  useAnimatedReaction(
    () => run.page.get(),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(turnedPage, now, byHand.get());
    },
  );
  useAnimatedReaction(
    () => run.active.get(),
    (now, old) => {
      if (now !== old) scheduleOnRN(setActive, now === 1);
    },
  );

  const go = (to: number, hand: boolean, late = 0) => {
    byHand.set(hand);
    scheduleOnUI(goTo, run, figures, to, late);
  };

  /** A touch takes over from the autoPlay loop, which starts again from the first page a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ go, shown, active, count });
  useEffect(() => {
    latest.current = { go, shown, active, count };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, step: () => void) => timers.push(setTimeout(step, ms));
    // Every step is due at a fixed time after the loop began, and is told how late it runs.
    const cycle = (origin: number) => {
      const at = (seconds: number, step: (late: number) => void) => {
        const due = origin + seconds * 1000;
        after(due - Date.now(), () => step(Math.max(0, Date.now() - due) / 1000));
      };
      NEXT_AT.forEach((seconds) => at(seconds, (late) => latest.current.go((latest.current.shown + 1) % latest.current.count, false, late)));
      at(HOME_AT, (late) => latest.current.go(0, false, late));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch: USER_HOLD seconds of rest, then back to the first page, which the loop
    // starts from the moment its figure has arrived.
    const rest = (quiet: number) => {
      if (latest.current.active) return after(100, () => rest(0));
      if (quiet < USER_HOLD * 1000) return after(100, () => rest(quiet + 100));
      latest.current.go(0, false);
      cycle(Date.now() + MOVE * 1000);
    };
    if (touches === 0) cycle(Date.now());
    else after(100, () => rest(100));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onStart(() => {
      // A change of page that is on its way is left to finish.
      way.set(0);
      if (changing(run)) return;
      cancelAnimation(run.r);
      const here = run.page.get();
      run.from.set(snapshot(run, figures));
      home.set(here);
      run.leaving.set(here);
      run.arriving.set(here);
      run.first.set(here * 2 + 1);
      run.second.set(here * 2 + 1);
      run.r.set(0);
      run.active.set(1);
      way.set(2);
    })
    .onUpdate((event) => {
      if (way.get() === 0) return;
      const here = home.get();
      const to = event.translationY < 0 ? 1 : -1;
      if (to !== way.get()) {
        // Up goes to the next page's figure (after the last comes the first), down to the settled
        // figure of the page before; the first page has none before it.
        way.set(to);
        const target = to > 0 ? (here + 1) % count : here - 1;
        run.arriving.set(target < 0 ? here : target);
        run.first.set(target < 0 ? here * 2 + 1 : to > 0 ? target * 2 : target * 2 + 1);
        run.second.set(target < 0 ? here * 2 + 1 : target * 2 + 1);
      }
      const far = run.arriving.get() === here ? 0 : Math.min(0.999, Math.abs(event.translationY) / DRAG);
      run.r.set(far * MOVE);
    })
    .onFinalize((event) => {
      if (way.get() === 0) return;
      const here = home.get();
      const flick = way.get() > 0 ? -event.velocityY : event.velocityY;
      const far = run.r.get() / MOVE;
      way.set(0);
      if (run.arriving.get() !== here && (far > COMMIT || (far > 0.05 && flick > 600))) {
        byHand.set(true);
        run.page.set(run.arriving.get());
        // Up arrives at the figure and lets it settle; down arrives at the settled figure.
        return play(run, 0);
      }
      run.r.set(
        withTiming(0, { duration: far * MOVE * 1000, ...LINEAR }, (finished) => {
          if (finished) resettle(run, figures);
        }),
      );
    });

  const cx = width / 2;
  const cy = Math.round(height * ART_AT);
  const buttonY = height - Math.max(insets.bottom, 16) - BUTTON_UP;

  const picture = useDerivedValue(() => {
    const now = run.r.get();
    const start = run.from.get();
    const one = figures[run.first.get()];
    const two = figures[run.second.get()];
    const time = calm ? 0 : clock.get();
    return createPicture(
      (canvas) => {
        const pen = Skia.Paint();
        pen.setStyle(PaintStyle.Stroke);
        pen.setStrokeWidth(STROKE);
        pen.setAntiAlias(true);
        pen.setColor(Skia.Color(ink));
        const size = calm ? 1 : breath(time);
        const oval = (x: number, y: number, w: number, h: number, alpha: number) => {
          if (alpha <= 0.002) return;
          pen.setAlphaf(alpha);
          canvas.drawOval({ x: cx + (x - w / 2) * size, y: cy + (y - h / 2) * size, width: w * size, height: h * size }, pen);
        };
        if (calm) {
          // Nothing travels: the figure the rings come from fades into the one they go to.
          const settling = now >= MOVE;
          const u = settling ? ease((now - MOVE - SETTLE_AFTER) / SETTLE) : ease(now / MOVE);
          for (let k = 0; k < RINGS; k++) {
            const to = settling ? two[k] : one[k];
            if (settling) oval(one[k][0], one[k][1], one[k][2], one[k][3], one[k][4] * (1 - u));
            else oval(start[k * 6], start[k * 6 + 1], start[k * 6 + 2], start[k * 6 + 3], start[k * 6 + 4] * (1 - u));
            oval(to[0], to[1], to[2], to[3], to[4] * u);
          }
          return;
        }
        for (let k = 0; k < RINGS; k++) {
          const spin = ringValue(start, one, two, now, k, 5);
          oval(
            ringValue(start, one, two, now, k, 0),
            ringValue(start, one, two, now, k, 1),
            ringValue(start, one, two, now, k, 2) * turned(k, spin, time),
            ringValue(start, one, two, now, k, 3),
            ringValue(start, one, two, now, k, 4),
          );
        }
      },
      { width, height },
    );
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const next = (shown + 1) % count;

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
        <StatusBar style="dark" />
        <View pointerEvents="none" style={styles.fill}>
          <Canvas style={styles.fill}>
            <Picture picture={picture} />
          </Canvas>
        </View>

        {pages.map((item, index) => (
          <Words
            key={index}
            run={run}
            index={index}
            calm={calm}
            title={item.title}
            body={body}
            color={accent}
            bottom={height - buttonY - 16}
          />
        ))}

        <Pressable
          style={[styles.button, { borderColor: accent, left: width - BUTTON_RIGHT - BUTTON / 2, top: buttonY - BUTTON / 2 }]}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel={`Next: ${pages[next].title.replace('\n', ' ')}`}
          onPress={() => go(next, true)}
        >
          {({ pressed }) => (
            <Animated.View style={{ opacity: pressed ? 0.5 : 1, transitionProperty: 'opacity', transitionDuration: 120 }}>
              <Arrow color={accent} />
            </Animated.View>
          )}
        </Pressable>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAPER,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  words: {
    position: 'absolute',
    left: MARGIN,
    right: BUTTON_RIGHT + BUTTON / 2 + 12,
  },
  title: {
    marginBottom: TITLE_GAP,
    fontSize: 30,
    lineHeight: 30,
    fontWeight: '600',
    letterSpacing: -0.8,
    includeFontPadding: false,
  },
  body: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 16,
    includeFontPadding: false,
  },
  button: {
    position: 'absolute',
    width: BUTTON,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arrow: {
    width: 14,
    height: 14,
  },
});

// Ink Wash Page Turn — a poetry reader whose pages turn by ink bleeding through wet paper.
//
// Inkwell shows one short poem to a page. The three pages are baked into images once (iwp-pages.ts)
// and one Skia runtime effect (iwp-shader.ts) draws the turn: from the point the finger touched, a
// noisy, fibrous front spreads across the paper, dark with ink at its edge, and the next page shows
// through behind it. The turn is one number, `progress`, so a swipe scrubs it: left for the next
// page, right for the one before, and a release completes the turn or lets it draw back, carrying the
// finger's speed. A tap turns the page by itself from where it lands.
//
// autoPlay: LOOP = 10 s. A ghost finger swipes page 1 to 2 from the lower right, 2 to 3 from the upper
// left, then taps the middle and page 1 blooms back; every curve is a function of the loop time, so
// frame 10 s equals frame 0. A touch takes over at once; IDLE seconds after the last touch the reader
// bleeds back to page 1 if it is elsewhere and the loop restarts at t = 0. Reduced motion: pages
// change at once, no bleed and no loop.
//
// Thread model: one useFrameCallback on the UI thread writes the turn (pages, progress, origin) and
// the ghost; the canvas, the page dots and the page number are driven by those shared values. The
// only JS calls are the haptics at a touch-down and at a committed turn.

import { Canvas, Fill, ImageShader, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { IDLE, LOOP, PAGES, T, TURN, ghostAlpha, ghostPress, ghostX, ghostY, scrub, seg, settle, smooth, swipeAt, tapAt, turnAt } from './iwp-motion';
import { PAPER, bakePages } from './iwp-pages';
import { INK_TONE, IWP_SKSL, SHEEN_TONE } from './iwp-shader';

export { LOOP };

const COPY = { app: 'Inkwell', book: 'Small Hours', of: 'of 3', hint: 'Swipe to turn the page' } as const;

const TEXT = '#26231E';
const MUTE = '#8C8576';
const GHOST = 46;
/** the page dots: their pitch and the moving pill's width, pt */
const DOT = 6;
const PITCH = 16;
const PILL = 18;
const MODE_AUTO = 0;
/** a finger is down, or was a moment ago */
const MODE_TOUCH = 1;
/** bleeding back to page 1 before the loop restarts */
const MODE_RETURN = 2;

const EFFECT = Skia.RuntimeEffect.Make(IWP_SKSL);

type Props = { autoPlay?: boolean };

function tick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** how much page `index` is the one on show, 0…1: the number and the dots change over the middle of a turn */
function weight(index: number, from: number, to: number, progress: number) {
  'worklet';
  const e = smooth(seg(progress, 0.25, 0.75));
  return (index === from ? 1 - e : 0) + (index === to ? e : 0);
}

function PageNumber({ index, from, to, progress }: { index: number; from: SharedValue<number>; to: SharedValue<number>; progress: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: weight(index, from.get(), to.get(), progress.get()) }));
  return <Animated.Text style={[styles.number, index > 0 && styles.numberOver, style]}>{`${index + 1} ${COPY.of}`}</Animated.Text>;
}

export function InkWashPageTurn({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const pages = useMemo(() => bakePages({ width, height, top: insets.top, bottom: insets.bottom }), [width, height, insets.top, insets.bottom]);

  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  // the turn: the page on show, the page arriving, how far the ink has spread, where it started
  const from = useSharedValue(0);
  const to = useSharedValue(1);
  const progress = useSharedValue(0);
  const originX = useSharedValue(width * T.first[2]);
  const originY = useSharedValue(height * T.first[3]);
  // a finger's turn
  const down = useSharedValue(0);
  const grab = useSharedValue(0);
  const travel = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  // the settle after a release (or a tap, or the way back to page 1): running, since, from, speed, target, omega, blend window
  const running = useSharedValue(0);
  const runAt = useSharedValue(0);
  const runFrom = useSharedValue(0);
  const runSpeed = useSharedValue(0);
  const runTarget = useSharedValue(1);
  const runTap = useSharedValue(0);
  // the ghost finger
  const ghostPX = useSharedValue(0);
  const ghostPY = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const ghostP = useSharedValue(0);
  const hint = useSharedValue(1);

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();

    if (mode.get() === MODE_AUTO) {
      if (!auto) {
        autoStart.set(now);
        return;
      }
      const t = (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const k = turnAt(t);
      from.set(k);
      to.set((k + 1) % PAGES);
      if (k === 0) {
        originX.set(width * T.first[2]);
        originY.set(height * T.first[3]);
        progress.set(swipeAt(t, T.first[0], T.first[1]));
      } else if (k === 1) {
        originX.set(width * T.second[2]);
        originY.set(height * T.second[3]);
        progress.set(swipeAt(t, T.second[0], T.second[1]));
      } else {
        originX.set(width * T.tap[1]);
        originY.set(height * T.tap[2]);
        progress.set(tapAt(t));
      }
      ghostPX.set(ghostX(t) * width);
      ghostPY.set(ghostY(t) * height);
      ghostA.set(ghostAlpha(t));
      ghostP.set(ghostPress(t));
      return;
    }

    if (running.get() === 1) {
      const tau = now - runAt.get();
      const tap = runTap.get() === 1;
      const until = tap ? TURN.tapSettle[1] : TURN.settle[1];
      progress.set(settle(runFrom.get(), runSpeed.get(), runTarget.get(), tau, tap ? TURN.tapOmega : TURN.omega, tap ? TURN.tapSettle[0] : TURN.settle[0], until));
      if (tau >= until) {
        running.set(0);
        if (runTarget.get() === 1) {
          // the turn is done: the page that arrived is the page on show
          from.set(to.get());
          to.set((to.get() + 1) % PAGES);
        }
        progress.set(0);
        if (mode.get() === MODE_RETURN) {
          autoStart.set(now);
          mode.set(MODE_AUTO);
          hint.set(withTiming(1, { duration: 400 }));
        }
      }
      return;
    }

    if (mode.get() === MODE_TOUCH && auto && down.get() === 0 && now - lastTouch.get() >= IDLE) {
      if (from.get() === 0) {
        // already on page 1: the loop picks up from its rest
        autoStart.set(now);
        mode.set(MODE_AUTO);
        hint.set(withTiming(1, { duration: 400 }));
      } else {
        mode.set(MODE_RETURN);
        to.set(0);
        originX.set(width * T.tap[1]);
        originY.set(height * T.tap[2]);
        runFrom.set(0);
        runSpeed.set(0);
        runTarget.set(1);
        runTap.set(1);
        runAt.set(now);
        running.set(1);
      }
    }
  }, false);

  useEffect(() => {
    ticker.setActive(true);
    return () => ticker.setActive(false);
  }, [ticker]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((e) => {
      const wasAuto = mode.get() === MODE_AUTO;
      mode.set(MODE_TOUCH);
      down.set(1);
      grab.set(0);
      travel.set(0);
      lastTouch.set(clock.get());
      ghostA.set(withTiming(0, { duration: 150 }));
      hint.set(withTiming(0, { duration: 250 }));
      if (running.get() === 1) return;
      const p = progress.get();
      if (wasAuto && p > 0 && p < 1) {
        // the loop was mid-turn: let that turn finish by itself
        runFrom.set(p);
        runSpeed.set(0);
        runTarget.set(1);
        runTap.set(0);
        runAt.set(clock.get());
        running.set(1);
        return;
      }
      if (p >= 1) {
        from.set(to.get());
        progress.set(0);
      }
      grab.set(1);
      originX.set(e.x);
      originY.set(e.y);
      scheduleOnRN(tick);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (grab.get() === 0) return;
      travel.set(Math.max(travel.get(), Math.abs(e.translationX), Math.abs(e.translationY)));
      // left for the next page, right for the one before; crossing back over the start swaps them
      to.set((from.get() + (e.translationX <= 0 ? 1 : PAGES - 1)) % PAGES);
      if (reduced) return;
      progress.set(scrub(Math.abs(e.translationX) / (TURN.scrub * width)));
    })
    .onEnd((e) => {
      if (grab.get() === 0) return;
      grab.set(0);
      const tapped = travel.get() < TURN.tap;
      if (tapped) to.set((from.get() + (e.x < width * TURN.backZone ? PAGES - 1 : 1)) % PAGES);
      const speed = ((e.translationX <= 0 ? -e.velocityX : e.velocityX) / (TURN.scrub * width)) * (tapped ? 0 : 1);
      const commit = tapped || progress.get() + TURN.project * speed > TURN.commit || (reduced && Math.abs(e.translationX) > 40);
      if (reduced) {
        if (commit) {
          from.set(to.get());
          scheduleOnRN(tick);
        }
        progress.set(0);
        return;
      }
      runFrom.set(tapped ? 0 : progress.get());
      runSpeed.set(Math.max(-4, Math.min(4, speed)));
      runTarget.set(commit ? 1 : 0);
      runTap.set(tapped ? 1 : 0);
      runAt.set(clock.get());
      running.set(1);
      if (commit && !tapped) scheduleOnRN(tick);
    })
    .onFinalize(() => {
      if (grab.get() === 1) {
        // cancelled mid-drag: draw back
        grab.set(0);
        runFrom.set(progress.get());
        runSpeed.set(0);
        runTarget.set(0);
        runTap.set(0);
        runAt.set(clock.get());
        running.set(progress.get() > 0 ? 1 : 0);
      }
      down.set(0);
      lastTouch.set(clock.get());
    });

  const uniforms = useDerivedValue(() => {
    const ox = originX.get();
    const oy = originY.get();
    const fx = Math.max(ox, width - ox);
    const fy = Math.max(oy, height - oy);
    return {
      res: [width, height],
      origin: [ox, oy],
      turn: [from.get(), to.get(), progress.get()],
      reach: Math.sqrt(fx * fx + fy * fy),
      inkTone: INK_TONE,
      sheenTone: SHEEN_TONE,
    };
  });

  const pill = useAnimatedStyle(() => {
    const p = progress.get();
    const e = smooth(seg(p, 0.25, 0.75));
    const at = from.get() + (to.get() - from.get()) * e;
    return { transform: [{ translateX: at * PITCH - (PILL - DOT) / 2 }, { scaleX: 1 + 0.35 * Math.sin(Math.PI * e) }] };
  });
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghostA.get(),
    transform: [{ translateX: ghostPX.get() - GHOST / 2 }, { translateY: ghostPY.get() - GHOST / 2 }, { scale: 1 - 0.16 * ghostP.get() }],
  }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() }));

  const rect = { x: 0, y: 0, width, height };
  const compact = height < 740;

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen}>
        <StatusBar style="dark" />
        {pages && EFFECT ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={EFFECT} uniforms={uniforms}>
                <ImageShader image={pages[0]} fit="fill" rect={rect} />
                <ImageShader image={pages[1]} fit="fill" rect={rect} />
                <ImageShader image={pages[2]} fit="fill" rect={rect} />
              </Shader>
            </Fill>
          </Canvas>
        ) : null}

        <View pointerEvents="none" style={[styles.head, { top: insets.top + (compact ? 8 : 14) }]}>
          <Text style={styles.app}>{COPY.app}</Text>
          <Text style={styles.book}>{COPY.book}</Text>
          <View style={styles.numbers}>
            <PageNumber index={0} from={from} to={to} progress={progress} />
            <PageNumber index={1} from={from} to={to} progress={progress} />
            <PageNumber index={2} from={from} to={to} progress={progress} />
          </View>
        </View>

        <View pointerEvents="none" style={[styles.foot, { bottom: Math.max(insets.bottom, 12) + (compact ? 10 : 18) }]}>
          <View style={styles.dots}>
            <View style={styles.dot} />
            <View style={styles.dot} />
            <View style={styles.dot} />
            <Animated.View style={[styles.pill, pill]} />
          </View>
          <Animated.Text style={[styles.hint, hintStyle]}>{COPY.hint}</Animated.Text>
        </View>

        <Animated.View pointerEvents="none" style={[styles.ghost, ghostStyle]}>
          <View style={styles.ghostCore} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER, overflow: 'hidden' },
  head: { position: 'absolute', left: 32, right: 32, flexDirection: 'row', alignItems: 'baseline' },
  app: { color: TEXT, fontSize: 15, fontWeight: '600', letterSpacing: 0.1 },
  book: { color: MUTE, fontSize: 15, fontWeight: '400', marginLeft: 8, flex: 1 },
  numbers: { alignItems: 'flex-end' },
  number: { color: MUTE, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  numberOver: { position: 'absolute', right: 0, top: 0 },
  foot: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  dots: { flexDirection: 'row', width: PITCH * (PAGES - 1) + DOT, height: DOT, justifyContent: 'space-between' },
  dot: { width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: 'rgba(38,35,30,0.2)' },
  pill: { position: 'absolute', left: 0, top: 0, width: PILL, height: DOT, borderRadius: DOT / 2, backgroundColor: TEXT },
  hint: { color: MUTE, fontSize: 13, fontWeight: '500', marginTop: 12 },
  // a soft ink-dark touch on the paper: a faint halo, a thin ring and a darker core
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(38,35,30,0.08)',
    borderWidth: 1.5,
    borderColor: 'rgba(38,35,30,0.34)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 0 16px rgba(38,35,30,0.16), inset 0 0 10px rgba(38,35,30,0.1)',
  },
  ghostCore: { width: 14, height: 14, borderRadius: 7, backgroundColor: 'rgba(38,35,30,0.3)' },
});

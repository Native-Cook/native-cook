import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  interpolateColor,
  useAnimatedStyle,
  useAnimatedReaction,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { DiscoverSheet } from './DiscoverSheet';
import { CloseGlyph } from './Icons';
import {
  BG_FADE_MS,
  COLLAPSE,
  CONTENT_IN,
  DESC_FADE_MS,
  DISCOVERING_MS,
  DISCOVER_MS,
  DISMISS,
  EXPAND,
  LABEL_SWITCH_MS,
  LOGO_IN,
  LOOP_MS,
  PRESENT,
  PRESS,
  PUSH,
  RESUME_AFTER_MS,
  REVEAL_AMOUNT_MS,
  RM_FADE_MS,
  SCRIPT,
  SETTLE,
  SNAP,
  UNDER_SHIFT,
  giftCardPickerSnapshotAt,
} from './motion';
import { Portfolio } from './Portfolio';
import { StockSheet } from './StockSheet';
import { SelectionFlight } from './SelectionFlight';
import { STOCKS } from './stocks';
import { COLORS, SCREEN, SHEET, STOCK, TOUCH } from './theme';

// freezeAt (dev only): stop the self-playing loop at this time (ms) for frame-exact screenshots
type Props = { autoPlay?: boolean; freezeAt?: number };

const LAST = STOCKS.length - 1;
const NEVER = ReduceMotion.Never;

// One autoplay entry: loop time (ms) → what a finger would have done.
type Cue = { at: number; run: 'present' | 'discover' | 'choose' | 'swipe' | 'pick' | 'dismiss' };
const CUES: Cue[] = [
  { at: SCRIPT.present, run: 'present' },
  { at: SCRIPT.discover, run: 'discover' },
  { at: SCRIPT.choose, run: 'choose' },
  ...SCRIPT.swipes.flatMap((at) => [
    { at, run: 'swipe' as const },
    { at: at + LABEL_SWITCH_MS, run: 'pick' as const },
  ]),
  { at: SCRIPT.dismiss, run: 'dismiss' },
];

export function GiftCardPicker({ autoPlay = false, freezeAt = -1 }: Props) {
  // many worklets below close over render-scope values; keep the React Compiler's cache out
  'use no memo';
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  // the certificate's serial is set in JetBrains Mono (OFL, bundled); system mono until it loads
  useFonts({ 'JetBrainsMono-Medium': require('./assets/fonts/JetBrainsMono-Medium.ttf') });
  const auto = autoPlay && !reduceMotion;
  const frozenSnapshot = useMemo(() => freezeAt >= 0 ? giftCardPickerSnapshotAt(freezeAt) : null, [freezeAt]);
  // Which layer screen readers see: 0 portfolio, 1 light step, 2 stocks (changes a few times a loop).
  const [layer, setLayer] = useState(frozenSnapshot?.layer ?? 0);
  const [committedIndex, setCommittedIndex] = useState(0);

  const sheetTop = insets.top + (SHEET.top - 62); // measured on a 62 pt top inset (iPhone 17 Pro)
  const sheetH = height - sheetTop;
  const ky = sheetH / (SCREEN.height - SHEET.top);

  // Scene state, all on the UI thread.
  const sheet = useSharedValue(frozenSnapshot?.sheet ?? 0); // 0 portfolio, 1 sheet up
  const stage = useSharedValue(frozenSnapshot?.stage ?? 0); // 0 cloud, 1 sphere, 2 amount
  const push = useSharedValue(frozenSnapshot?.push ?? 0); // 0 light step, 1 dark step
  const reveal = useSharedValue(frozenSnapshot?.reveal ?? 0); // 0 skeleton, 1 content, 2 logos
  const scroll = useSharedValue(frozenSnapshot?.scroll ?? 0);
  const active = useSharedValue(frozenSnapshot?.active ?? 0);
  const trackFade = useSharedValue(1);
  const bg0 = useSharedValue(frozenSnapshot?.backgrounds[0] ?? 1);
  const bg1 = useSharedValue(frozenSnapshot?.backgrounds[1] ?? 0);
  const bg2 = useSharedValue(frozenSnapshot?.backgrounds[2] ?? 0);
  const bg3 = useSharedValue(frozenSnapshot?.backgrounds[3] ?? 0);
  const bg4 = useSharedValue(frozenSnapshot?.backgrounds[4] ?? 0);
  const d0 = useSharedValue(frozenSnapshot?.descriptions[0] ?? 1);
  const d1 = useSharedValue(frozenSnapshot?.descriptions[1] ?? 0);
  const d2 = useSharedValue(frozenSnapshot?.descriptions[2] ?? 0);
  const d3 = useSharedValue(frozenSnapshot?.descriptions[3] ?? 0);
  const d4 = useSharedValue(frozenSnapshot?.descriptions[4] ?? 0);
  const bg = useMemo(() => [bg0, bg1, bg2, bg3, bg4], [bg0, bg1, bg2, bg3, bg4]);
  const desc = useMemo(() => [d0, d1, d2, d3, d4], [d0, d1, d2, d3, d4]);
  const pressGift = useSharedValue(0);
  const pressLight = useSharedValue(0);
  const pressDark = useSharedValue(0);
  const commit = useSharedValue(0);

  const motionTime = useSharedValue((frozenSnapshot?.at ?? 0) / 1000);
  const time = useSharedValue((frozenSnapshot?.at ?? 0) / 1000); // visible sphere's Skia clock
  const clock = useSharedValue(frozenSnapshot?.at ?? -1); // ms into the loop
  const loopStart = useSharedValue(0);
  const seek = useSharedValue(0); // ≥ 0: restart the loop clock at this time on the next frame
  const playing = useSharedValue(auto && frozenSnapshot === null);
  const dragStart = useSharedValue(0);
  const frozen = useSharedValue(frozenSnapshot !== null);

  // ---- commands (worklets: the script and the fingers use the same ones) ----

  const resetContent = useCallback(() => {
    'worklet';
    for (const v of [stage, push, reveal, scroll, commit]) {
      cancelAnimation(v);
      v.set(0);
    }
    active.set(0);
    trackFade.set(1);
    for (let i = 0; i < bg.length; i++) {
      cancelAnimation(bg[i]);
      cancelAnimation(desc[i]);
      bg[i].set(i === 0 ? 1 : 0);
      desc[i].set(i === 0 ? 1 : 0);
    }
  }, [stage, push, reveal, scroll, commit, active, trackFade, bg, desc]);

  useEffect(() => {
    if (frozenSnapshot) {
      frozen.set(true);
      playing.set(false);
      for (const value of [sheet, stage, push, reveal, scroll, trackFade, commit]) cancelAnimation(value);
      for (let index = 0; index < bg.length; index++) {
        cancelAnimation(bg[index]);
        cancelAnimation(desc[index]);
      }
      sheet.set(frozenSnapshot.sheet);
      stage.set(frozenSnapshot.stage);
      push.set(frozenSnapshot.push);
      reveal.set(frozenSnapshot.reveal);
      scroll.set(frozenSnapshot.scroll);
      active.set(frozenSnapshot.active);
      trackFade.set(1);
      commit.set(0);
      time.set(frozenSnapshot.at / 1000);
      motionTime.set(frozenSnapshot.at / 1000);
      clock.set(frozenSnapshot.at);
      frozenSnapshot.backgrounds.forEach((value, index) => bg[index].set(value));
      frozenSnapshot.descriptions.forEach((value, index) => desc[index].set(value));
      const timer = setTimeout(() => setLayer(frozenSnapshot.layer), 0);
      return () => clearTimeout(timer);
    }
    frozen.set(false);
    sheet.set(0);
    resetContent();
    time.set(0);
    motionTime.set(0);
    clock.set(-1);
    seek.set(0);
    playing.set(auto);
    const timer = setTimeout(() => setLayer(0), 0);
    return () => clearTimeout(timer);
  }, [frozenSnapshot, frozen, playing, sheet, stage, push, reveal, scroll, active, trackFade, commit, time, motionTime, clock, seek, bg, desc, resetContent, auto]);

  const present = useCallback(() => {
    'worklet';
    if (sheet.get() > 0.01) return;
    resetContent();
    scheduleOnRN(setLayer, 1);
    sheet.set(reduceMotion ? withTiming(1, { duration: RM_FADE_MS, reduceMotion: NEVER }) : withTiming(1, PRESENT));
  }, [sheet, reduceMotion, resetContent]);

  const dismiss = useCallback(() => {
    'worklet';
    const done = (finished?: boolean) => {
      'worklet';
      if (finished) {
        resetContent();
        scheduleOnRN(setLayer, 0);
      }
    };
    sheet.set(reduceMotion ? withTiming(0, { duration: RM_FADE_MS, reduceMotion: NEVER }, done) : withTiming(0, DISMISS, done));
  }, [sheet, reduceMotion, resetContent]);

  const discover = useCallback(() => {
    'worklet';
    if (stage.get() > 0.001 || sheet.get() < 0.99) return;
    const linear = { easing: Easing.linear, reduceMotion: NEVER };
    stage.set(withSequence(
      withTiming(1, { duration: DISCOVER_MS, ...linear }),
      withDelay(DISCOVERING_MS, withTiming(2, { duration: REVEAL_AMOUNT_MS, ...linear })),
    ));
  }, [stage, sheet]);

  const choose = useCallback(() => {
    'worklet';
    if (stage.get() < 1.99 || push.get() > 0.001) return;
    scheduleOnRN(setLayer, 2);
    push.set(reduceMotion
      ? withTiming(1, { duration: RM_FADE_MS, reduceMotion: NEVER })
      : withDelay(PUSH.delay, withTiming(1, { duration: PUSH.duration, easing: PUSH.easing })));
    reveal.set(withSequence(
      withDelay(PUSH.delay + CONTENT_IN.delay, withTiming(1, { duration: CONTENT_IN.duration, reduceMotion: NEVER })),
      withTiming(2, { duration: LOGO_IN.duration, reduceMotion: NEVER }),
    ));
  }, [stage, push, reveal, reduceMotion]);

  const commitSelection = useCallback(() => {
    'worklet';
    if (push.get() < 0.99 || reveal.get() < 0.99 || commit.get() > 0.001) return;
    scheduleOnRN(setCommittedIndex, active.get());
    scheduleOnRN(setLayer, 3);
    const duration = reduceMotion ? 420 : 2540;
    commit.set(withTiming(1, { duration, easing: Easing.linear, reduceMotion: NEVER }, (finished) => {
      if (!finished) return;
      resetContent();
      sheet.set(0);
      scheduleOnRN(setLayer, 0);
    }));
    sheet.set(withDelay(
      reduceMotion ? 200 : 1500,
      withTiming(0, {
        duration: reduceMotion ? 200 : 620,
        easing: reduceMotion ? Easing.inOut(Easing.quad) : Easing.bezier(0.32, 0.72, 0, 1),
        reduceMotion: NEVER,
      }),
    ));
  }, [push, reveal, commit, active, reduceMotion, resetContent, sheet]);

  // Backdrops are stacked by index: everything at or below the active card is opaque, so
  // moving forward fades the new one in on top and moving back fades the upper ones out.
  const pick = useCallback(
    (j: number) => {
      'worklet';
      const prev = active.get();
      if (j === prev || j < 0 || j > LAST) return;
      active.set(j);
      const fade = { duration: BG_FADE_MS, reduceMotion: NEVER };
      if (j > prev) {
        for (let i = 0; i < j; i++) bg[i].set(1);
        bg[j].set(withTiming(1, fade));
      } else {
        bg[j].set(1);
        for (let i = j + 1; i <= LAST; i++) if (bg[i].get() > 0) bg[i].set(withTiming(0, fade));
      }
      for (let i = 0; i <= LAST; i++) desc[i].set(withTiming(i === j ? 1 : 0, { duration: DESC_FADE_MS, reduceMotion: NEVER }));
    },
    [active, bg, desc],
  );

  const goTo = useCallback(
    (j: number, velocity = 0) => {
      'worklet';
      const t = Math.max(0, Math.min(LAST, j));
      pick(t);
      if (reduceMotion) {
        trackFade.set(withTiming(0, { duration: 90, reduceMotion: NEVER }, () => {
          scroll.set(t);
          trackFade.set(withTiming(1, { duration: 130, reduceMotion: NEVER }));
        }));
        return;
      }
      scroll.set(withSpring(t, { ...SNAP, velocity }));
    },
    [pick, reduceMotion, scroll, trackFade],
  );

  // The pin's swipe: a flick, then a critically damped settle (fitted to the pin's frames).
  const swipe = useCallback(() => {
    'worklet';
    const i = Math.round(scroll.get());
    if (i >= LAST) return;
    scroll.set(withSpring(i + 1, SETTLE));
  }, [scroll]);


  const run = useCallback(
    (cue: Cue['run']) => {
      'worklet';
      if (cue === 'present') present();
      else if (cue === 'discover') discover();
      else if (cue === 'choose') choose();
      else if (cue === 'swipe') swipe();
      else if (cue === 'pick') pick(active.get() + 1);
      else dismiss();
    },
    [present, discover, choose, swipe, pick, dismiss, active],
  );

  // The loop clock reads the frame timestamp rather than summing frame deltas, so a dropped
  // frame never pushes the script out of its 19.5 s period.
  const frameDriver = useFrameCallback((f) => {
    if (frozen.get()) return;
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 64);
    // while self-playing, the drift and the spin follow the loop clock, so every loop (and every
    // frozen frame) shows them in the same place; under a finger they keep running freely
    motionTime.set(playing.get() ? Math.max(0, clock.get()) / 1000 : motionTime.get() + dt / 1000);
    // An offscreen Metal canvas can wait for a drawable when repeatedly invalidated.
    // Keep the logical clock exact, but publish only while the sphere contributes pixels.
    const sphereStage = stage.get();
    if (sheet.get() > 0 && sphereStage > COLLAPSE.sphereIn && sphereStage < 1 + EXPAND.fadeEnd) {
      time.set(motionTime.get());
    }
    if (!playing.get()) return;
    if (freezeAt >= 0 && clock.get() >= freezeAt) {
      // hold every value where it is at this instant
      frozen.set(true);
      for (const v of [sheet, stage, push, reveal, scroll, trackFade, commit]) cancelAnimation(v);
      for (let i = 0; i < bg.length; i++) {
        cancelAnimation(bg[i]);
        cancelAnimation(desc[i]);
      }
      return;
    }
    if (seek.get() >= 0) {
      loopStart.set(f.timestamp - seek.get());
      seek.set(-1);
    }
    const prev = clock.get();
    const now = f.timestamp - loopStart.get();
    if (now >= LOOP_MS) {
      for (let i = 0; i < CUES.length; i++) if (CUES[i].at > prev) run(CUES[i].run);
      loopStart.set(loopStart.get() + LOOP_MS);
      cancelAnimation(sheet);
      sheet.set(0);
      resetContent();
      scheduleOnRN(setLayer, 0);
      clock.set(-1);
      return;
    }
    for (let i = 0; i < CUES.length; i++) if (CUES[i].at > prev && CUES[i].at <= now) run(CUES[i].run);
    clock.set(now);
  }, false);
  useEffect(() => {
    frameDriver.setActive(freezeAt < 0 && !reduceMotion);
    return () => frameDriver.setActive(false);
  }, [frameDriver, freezeAt, reduceMotion]);


  // ---- touches: any touch stops the script; 3.5 s after the last one it resumes from rest ----

  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resume = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      // an open sheet closes the way the script closes it, then the loop starts again
      let at = 0;
      if (sheet.get() > 0.01) {
        dismiss();
        at = LOOP_MS - 600;
      } else resetContent();
      clock.set(at - 1);
      seek.set(at);
      playing.set(true);
    });
  }, [clock, seek, sheet, playing, dismiss, resetContent]);
  const onTouchStart = useCallback(() => {
    if (!auto) return;
    playing.set(false);
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
  }, [auto, playing]);
  const onTouchEnd = useCallback(() => {
    if (!auto) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = setTimeout(resume, RESUME_AFTER_MS);
  }, [auto, resume]);
  useEffect(() => () => {
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
  }, []);

  const tick = useCallback(() => Haptics.selectionAsync(), []);
  const tap = useCallback(() => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light), []);
  // a detent ticks under a finger (never while self-playing)
  useAnimatedReaction(
    () => active.get(),
    (v, prev) => {
      if (prev === null || v === prev) return;
      if (!playing.get()) scheduleOnRN(tick);
    },
  );

  const pressIn = (v: typeof pressGift) => () => {
    v.set(withTiming(1, { duration: PRESS.in, reduceMotion: NEVER }));
  };
  const pressOut = (v: typeof pressGift) => () => {
    v.set(withTiming(0, { duration: PRESS.out, reduceMotion: NEVER }));
  };

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .maxPointers(1)
        .activeOffsetX([-10, 10])
        .failOffsetY([-14, 14])
        .onStart(() => {
          cancelAnimation(scroll);
          dragStart.set(scroll.get());
        })
        .onUpdate((e) => {
          let v = dragStart.get() - e.translationX / STOCK.spacing;
          // rubber band past the ends
          if (v < 0) v = -(-v * 0.35) / (1 + -v * 0.35);
          if (v > LAST) v = LAST + ((v - LAST) * 0.35) / (1 + (v - LAST) * 0.35);
          scroll.set(v);
          const r = Math.max(0, Math.min(LAST, Math.round(v)));
          if (r !== active.get()) pick(r); // the detent tick fires from a reaction, not from here
        })
        .onEnd((e) => {
          const v = -e.velocityX / STOCK.spacing;
          const base = Math.round(dragStart.get());
          let t = Math.round(scroll.get() + v * 0.18);
          t = Math.max(base - 1, Math.min(base + 1, t));
          goTo(t, v);
        }),
    [scroll, dragStart, active, pick, goTo],
  );

  // ---- layout styles ----

  const dim = useAnimatedStyle(() => ({ opacity: sheet.get() }));
  const sheetStyle = useAnimatedStyle(() =>
    reduceMotion
      ? { opacity: sheet.get(), transform: [{ translateY: 0 }] }
      : { opacity: 1, transform: [{ translateY: (1 - sheet.get()) * (sheetH + 20) }] },
  );
  const lightStyle = useAnimatedStyle(() => ({ transform: [{ translateX: reduceMotion ? 0 : -push.get() * width * UNDER_SHIFT }] }));
  const darkStyle = useAnimatedStyle(() =>
    reduceMotion
      ? { opacity: push.get(), transform: [{ translateX: 0 }] }
      : { opacity: push.get() > 0.001 ? 1 : 0, transform: [{ translateX: (1 - push.get()) * width }] },
  );
  const closeStyle = useAnimatedStyle(() => {
    const r = Math.min(1, Math.max(0, reveal.get()));
    const backgroundColor =
      r > 0 ? interpolateColor(r, [0, 1], ['#5a5a5e', 'rgba(255,255,255,0.06)']) : interpolateColor(push.get(), [0, 1], ['#ffffff', '#5a5a5e']);
    return { backgroundColor, shadowOpacity: 0.08 * (1 - push.get()) };
  });
  const closeDark = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, (push.get() - 0.9) * 10)) }));
  const closeLight = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, push.get() * 8) }));
  const closeCommit = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, commit.get() * (2540 / 160)) }));

  return (
    <View pointerEvents={freezeAt >= 0 ? 'none' : 'auto'} style={styles.root} onTouchStart={onTouchStart} onTouchEnd={onTouchEnd} onTouchCancel={onTouchEnd}>
      <StatusBar style="dark" />
      <View style={StyleSheet.absoluteFill} accessibilityElementsHidden={layer !== 0} importantForAccessibility={layer !== 0 ? 'no-hide-descendants' : 'auto'}>
      <Portfolio
        width={width}
        dy={insets.top - 62}
        bottom={insets.bottom - 34}
        press={pressGift}
        landing={commit}
        onGiftPressIn={pressIn(pressGift)}
        onGiftPressOut={pressOut(pressGift)}
        onGift={() => {
          tap();
          scheduleOnUI(present);
        }}
      />
      </View>
      <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dim]} pointerEvents="none" />

      <Animated.View style={[styles.sheet, { top: sheetTop, height: sheetH }, sheetStyle]}
        pointerEvents={layer === 1 || layer === 2 ? 'auto' : 'none'}
        accessibilityElementsHidden={layer === 0 || layer === 3}
        importantForAccessibility={layer === 0 || layer === 3 ? 'no-hide-descendants' : 'auto'}>
        <Animated.View style={[StyleSheet.absoluteFill, lightStyle]} accessibilityElementsHidden={layer !== 1} importantForAccessibility={layer !== 1 ? 'no-hide-descendants' : 'auto'}>
          <DiscoverSheet
            width={width}
            height={sheetH}
            ky={ky}
            stage={stage}
            time={time}
            press={pressLight}
            reduceMotion={reduceMotion}
            onPressIn={pressIn(pressLight)}
            onPressOut={pressOut(pressLight)}
            onDiscover={() => {
              tap();
              scheduleOnUI(discover);
            }}
            onChoose={() => {
              tap();
              scheduleOnUI(choose);
            }}
          />
        </Animated.View>

        <Animated.View
          style={[StyleSheet.absoluteFill, styles.dark, darkStyle]}
          pointerEvents={layer === 2 ? 'auto' : 'none'}
          accessibilityElementsHidden={layer !== 2}
          importantForAccessibility={layer !== 2 ? 'no-hide-descendants' : 'auto'}
        >
          <StockSheet
            width={width}
            height={sheetH}
            ky={ky}
            trackFade={trackFade}
            reveal={reveal}
            scroll={scroll}
            active={active}
            bg={bg}
            desc={desc}
            press={pressDark}
            pan={pan}
            onCard={(i) => {
              scheduleOnUI(goTo, i, 0);
            }}
            commit={commit}
            onChoose={() => {
              tap();
              scheduleOnUI(commitSelection);
            }}
            onPressIn={pressIn(pressDark)}
            onPressOut={pressOut(pressDark)}
          />
        </Animated.View>

        <Animated.View style={[styles.close, closeStyle, closeCommit]} pointerEvents={layer === 1 || layer === 2 ? 'auto' : 'none'}>
          <Pressable pressRetentionOffset={TOUCH.retention}
            style={styles.closeHit}
            onPress={() => {
              tap();
              scheduleOnUI(dismiss);
            }}
            accessibilityRole="button"
            accessibilityLabel="Close"
            disabled={layer !== 1 && layer !== 2}
            accessible={layer === 1 || layer === 2}
          >
            <Animated.View style={[styles.glyph, closeLight]}>
              <CloseGlyph color={COLORS.ink} />
            </Animated.View>
            <Animated.View style={[styles.glyph, closeDark]}>
              <CloseGlyph color={COLORS.white} />
            </Animated.View>
          </Pressable>
        </Animated.View>
      </Animated.View>

      <SelectionFlight
        width={width}
        sheetTop={sheetTop}
        sheetHeight={sheetH}
        progress={commit}
        selected={committedIndex}
        active={layer === 3}
        reduceMotion={reduceMotion}
      />

    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.page, overflow: 'hidden' },
  dim: { backgroundColor: COLORS.dim },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    borderTopLeftRadius: SHEET.radius,
    borderTopRightRadius: SHEET.radius,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: COLORS.sheetLight,
  },
  dark: { borderTopLeftRadius: SHEET.radius, borderTopRightRadius: SHEET.radius, borderCurve: 'continuous', overflow: 'hidden' },
  close: {
    position: 'absolute',
    left: SHEET.closeLeft,
    top: SHEET.closeTop,
    width: SHEET.closeSize,
    height: SHEET.closeSize,
    borderRadius: SHEET.closeSize / 2,
    shadowColor: '#000000',
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
  },
  closeHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  glyph: { position: 'absolute' },
});

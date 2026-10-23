import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  type FrameInfo,
  interpolate,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { Home } from './Home';
import { easeOut, easeSheet, PRESS_IN_MS, PRESS_OUT_MS, PRESS_SCALE, REDUCED_FADE_MS, RESUME_AFTER_MS, T, TOUCH } from './motion';
import { Player } from './Player';
import { sampleScene } from './script';
import { COLORS, FONTS, HOME } from './theme';
import { useResume } from './use-resume';

const SNAP = { duration: 400, dampingRatio: 0.8 };
/** Touch playback reads at the pin's average pace. */
const READ_MS = T.readT[T.readT.length - 1] - T.readT[0];
const READ_LINES = T.readLines[T.readLines.length - 1];
/** Words start a moment after the book begins to open, as in the pin. */
const WORDS_DELAY = T.readT[0] - T.open[0];
const STEP = HOME.row.coverW + HOME.row.gap;

export function ForYouCardStack({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const canAutoPlay = autoPlay && !reduced && !frozen;
  const [fontsLoaded] = useFonts({
    ForYouAnton: require('./assets/fonts/Anton-Regular.ttf'),
    [FONTS.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONTS.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONTS.heavy]: require('./assets/fonts/Inter-ExtraBold.ttf'),
  });

  const deck = useSharedValue(0);
  const push = useSharedValue(0);
  const playing = useSharedValue(0);
  const open = useSharedValue(0);
  const close = useSharedValue(0);
  const words = useSharedValue(0);
  const mode = useSharedValue(canAutoPlay ? 0 : 1);
  const loopStart = useSharedValue(-1);
  const dragStart = useSharedValue(0);
  const pressBack = useSharedValue(1);
  const pressPlay = useSharedValue(1);

  const frameDriver = useFrameCallback(useCallback((info: FrameInfo) => {
    'worklet';
    if (mode.get() !== 0) return;
    const now = info.timestamp;
    if (loopStart.get() < 0) loopStart.set(now);
    const s = sampleScene(now - loopStart.get());
    deck.set(s.deck);
    push.set(s.push);
    playing.set(s.playing);
    open.set(s.open);
    close.set(s.close);
    words.set(s.words);
  }, [close, deck, loopStart, mode, open, playing, push, words]), false);

  useEffect(() => {
    frameDriver.setActive(canAutoPlay);
    if (frozen) {
      const scene = sampleScene(freezeAt);
      deck.set(scene.deck);
      push.set(scene.push);
      playing.set(scene.playing);
      open.set(scene.open);
      close.set(scene.close);
      words.set(scene.words);
      mode.set(1);
      loopStart.set(-1);
    } else if (reduced) {
      for (const value of [deck, push, playing, open, close, words, pressBack, pressPlay]) cancelAnimation(value);
      deck.set(Math.round(deck.get()));
      push.set(push.get() >= 0.5 ? 1 : 0);
      playing.set(playing.get() >= 0.5 ? 1 : 0);
      open.set(open.get() >= 0.5 ? 1 : 0);
      close.set(0);
      words.set(open.get() >= 0.5 ? READ_LINES : 0);
      pressBack.set(1);
      pressPlay.set(1);
      mode.set(1);
      loopStart.set(-1);
    } else if (canAutoPlay) {
      deck.set(0);
      push.set(0);
      playing.set(0);
      open.set(0);
      close.set(0);
      words.set(0);
      mode.set(0);
      loopStart.set(-1);
    } else {
      mode.set(1);
    }
    return () => frameDriver.setActive(false);
  }, [canAutoPlay, close, deck, frameDriver, freezeAt, frozen, loopStart, mode, open, playing, pressBack, pressPlay, push, reduced, words]);

  // status bar follows the screen on top
  const [onPlayer, setOnPlayer] = useState(false);
  useAnimatedReaction(
    () => push.get() > 0.5,
    (on, was) => {
      if (on !== was) scheduleOnRN(setOnPlayer, on);
    },
  );

  // ---- touch: stop the script, take the live values, resume from rest after a pause ---------------------
  const toRest = useCallback(() => {
    const ms = TOUCH.pushMs;
    deck.set(withTiming(0, { duration: ms, easing: easeOut }));
    playing.set(withTiming(0, { duration: 150 }));
    words.set(withTiming(0, { duration: 150 }));
    open.set(withTiming(0, { duration: 150 }));
    close.set(withTiming(0, { duration: 150 }));
    push.set(
      withTiming(0, { duration: ms, easing: easeSheet }, (done) => {
        if (!done) return;
        loopStart.set(-1);
        mode.set(0);
      }),
    );
  }, [close, deck, loopStart, mode, open, playing, push, words]);
  const { hold, release } = useResume(canAutoPlay, RESUME_AFTER_MS, toRest);

  const grab = useCallback(() => {
    mode.set(1);
    hold();
  }, [hold, mode]);

  const light = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, []);

  const openBook = useCallback(() => {
    grab();
    push.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : TOUCH.pushMs, easing: easeSheet }));
    release();
  }, [grab, push, reduced, release]);

  const back = useCallback(() => {
    grab();
    cancelAnimation(words);
    playing.set(withTiming(0, { duration: 150 }));
    push.set(
      withTiming(0, { duration: reduced ? REDUCED_FADE_MS : TOUCH.pushMs, easing: easeSheet }, (done) => {
        if (!done) return;
        open.set(0);
        close.set(0);
        words.set(0);
      }),
    );
    release();
  }, [close, grab, open, playing, push, reduced, release, words]);

  const togglePlay = useCallback(() => {
    grab();
    light();
    const fade = { duration: 150, easing: easeOut, reduceMotion: ReduceMotion.Never };
    if (reduced) {
      const opening = playing.get() < 0.5;
      playing.set(withTiming(opening ? 1 : 0, fade));
      close.set(0);
      open.set(opening ? 1 : 0);
      words.set(opening ? READ_LINES : 0);
      release();
      return;
    }
    if (playing.get() < 0.5) {
      playing.set(withTiming(1, fade));
      if (close.get() > 0) {
        close.set(0);
        open.set(0);
      }
      const left = (1 - open.get()) * TOUCH.playMs;
      open.set(withTiming(1, { duration: left }));
      words.set(0);
      words.set(withDelay(WORDS_DELAY, withTiming(READ_LINES, { duration: READ_MS })));
    } else {
      playing.set(withTiming(0, fade));
      cancelAnimation(words);
      if (open.get() < 1) {
        // interrupted while opening: reverse from where it is
        open.set(withTiming(0, { duration: open.get() * TOUCH.playMs }));
      } else {
        close.set(
          withTiming(1, { duration: TOUCH.closeMs }, (done) => {
            if (!done) return;
            open.set(0);
            close.set(0);
            words.set(0);
          }),
        );
      }
    }
    release();
  }, [close, grab, light, open, playing, reduced, release, words]);

  const press = useCallback(
    (which: 'back' | 'play', down: boolean) => {
      const sv = which === 'back' ? pressBack : pressPlay;
      if (reduced) {
        sv.set(1);
        return;
      }
      sv.set(withTiming(down ? PRESS_SCALE : 1, { duration: down ? PRESS_IN_MS : PRESS_OUT_MS, easing: easeOut }));
    },
    [pressBack, pressPlay, reduced],
  );

  // the New & trending row: pull the next cover out of the deck, or push it back
  const rowGesture = Gesture.Pan()
    .maxPointers(1)
    .activeOffsetX([-10, 10])
    .onBegin(() => {
      scheduleOnRN(grab);
      cancelAnimation(deck);
      dragStart.set(deck.get());
    })
    .onUpdate((e) => {
      let d = dragStart.get() + e.translationX / STEP;
      if (d < 0) d *= 0.3;
      if (d > 1) d = 1 + (d - 1) * 0.3;
      deck.set(d);
    })
    .onEnd((e) => {
      const projected = deck.get() + (e.velocityX * 0.2) / STEP;
      const target = projected > 0.5 ? 1 : 0;
      deck.set(reduced ? target : withSpring(target, { ...SNAP, velocity: e.velocityX / STEP }));
      if (target !== Math.round(dragStart.get())) scheduleOnRN(light);
    })
    .onFinalize(() => scheduleOnRN(release));

  const homeStyle = useAnimatedStyle(() => {
    const v = push.get();
    return reduced ? { opacity: 1 - v } : { transform: [{ translateX: -width * 0.3 * v }] };
  });
  const dimStyle = useAnimatedStyle(() => ({ opacity: reduced ? 0 : 0.12 * push.get() }));
  const playerStyle = useAnimatedStyle(() => {
    const v = push.get();
    return reduced ? { opacity: v } : { transform: [{ translateX: interpolate(v, [0, 1], [width, 0]) }] };
  });

  if (!fontsLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style={onPlayer ? 'light' : 'dark'} />
      <Animated.View style={[StyleSheet.absoluteFill, homeStyle]}>
        <Home deck={deck} rowGesture={rowGesture} onOpenBook={openBook} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.dim, dimStyle]} pointerEvents="none" />
      </Animated.View>
      <Animated.View
        style={[StyleSheet.absoluteFill, styles.playerShadow, playerStyle]}
        pointerEvents={onPlayer ? 'auto' : 'none'}
      >
        <Player
          width={width}
          open={open}
          close={close}
          playing={playing}
          words={words}
          onBack={back}
          onPlay={togglePlay}
          pressBack={pressBack}
          pressPlay={pressPlay}
          onPressIn={(w) => press(w, true)}
          onPressOut={(w) => press(w, false)}
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.page, overflow: 'hidden' },
  dim: { backgroundColor: '#000000' },
  playerShadow: { shadowColor: '#000000', shadowOpacity: 0.18, shadowRadius: 18, shadowOffset: { width: -4, height: 0 } },
});

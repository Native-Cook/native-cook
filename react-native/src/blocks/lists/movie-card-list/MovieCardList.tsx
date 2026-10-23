import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation, Easing, ReduceMotion, scrollTo, useAnimatedReaction, useAnimatedRef, useAnimatedStyle, useReducedMotion,
  useSharedValue, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { Backdrops, CardDeck, CardInfo } from './CardDeck';
import { BuyButton, DetailSheet } from './DetailSheet';
import { CloseIcon, TargetIcon } from './icons';
import {
  BUILD, CAST_SPRING, COVER, type Cue, IDLE_RESUME_MS, LOOP_MS, OPEN, PAGE_SPRING, PAY, REDUCED_FADE_MS, RESET, SCRIPT,
} from './motion';
import { MOVIES, START_PAGE } from './movies';
import { PayOverlay } from './PayOverlay';
import { SeatScreen } from './SeatScreen';
import { CAROUSEL, COLORS, DETAIL, DISPLAY, SANS, SERIF_DISPLAY, TOP } from './theme';

type Stage = 'cards' | 'detail' | 'seats' | 'pay';
type AccessibleStage = Stage | 'transition';
const NEVER = ReduceMotion.Never;
const LAST = MOVIES.length - 1;
const SEAT_CLOCK_MS = 6000;
const PAY_CLOCK_MS = PAY.checkAt + PAY.checkMs + 600;
const SEATS_ACCESSIBLE_AT = BUILD.delay + BUILD.rowMs * 0.35;
const PAY_ACCESSIBLE_AT = PAY.blackAt + PAY.blackMs * 0.35;

/** The script's state at time t, used to freeze a frame for comparison with the pin (dev only). */
function frameAt(t: number) {
  const f = { page: START_PAGE, stage: 'cards' as Stage, open: 0, cast: 0, cover: 0, seatClock: 0, payClock: 0, picked: [] as string[] };
  for (const cue of SCRIPT) {
    if (t < cue.at) continue;
    const dt = t - cue.at;
    if (cue.kind === 'page' && dt > 500) f.page = cue.to;
    if (cue.kind === 'open' && dt > OPEN.ms) { f.open = 1; f.stage = 'detail'; }
    if (cue.kind === 'cast' && dt > 600) f.cast = cue.dx;
    if (cue.kind === 'buy') { f.stage = 'seats'; f.cover = Math.min(1, dt / COVER.ms); f.seatClock = Math.min(SEAT_CLOCK_MS, dt); }
    if (cue.kind === 'seat') f.picked.push(cue.seat);
    if (cue.kind === 'pay') { f.stage = 'pay'; f.payClock = Math.min(PAY_CLOCK_MS, dt); }
  }
  return f;
}

function accessibleStageAt(frame: ReturnType<typeof frameAt>): AccessibleStage {
  if (frame.stage === 'detail') return frame.open >= 0.35 ? 'detail' : 'transition';
  if (frame.stage === 'seats') return frame.seatClock >= SEATS_ACCESSIBLE_AT ? 'seats' : 'transition';
  if (frame.stage === 'pay') return frame.payClock >= PAY_ACCESSIBLE_AT ? 'pay' : 'transition';
  return 'cards';
}

export function MovieCardList({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  const [frozen] = useState(() => (freezeAt !== undefined ? frameAt(freezeAt) : null));
  const [fontsLoaded] = useFonts({
    [DISPLAY]: require('./assets/fonts/Anton-Regular.ttf'),
    [SANS.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
    [SANS.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
    [SANS.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
    [SERIF_DISPLAY]: require('./assets/fonts/DMSerifDisplay-Regular.ttf'),
  });
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const canAutoPlay = autoPlay && !reduced && freezeAt === undefined;
  const canAutoPlayRef = useRef(canAutoPlay);
  useLayoutEffect(() => {
    canAutoPlayRef.current = canAutoPlay;
  }, [canAutoPlay]);

  const [stage, setStage] = useState<Stage>(frozen?.stage ?? 'cards');
  const [accessibleStage, setAccessibleStage] = useState<AccessibleStage>(() => frozen ? accessibleStageAt(frozen) : 'cards');
  const [active, setActive] = useState(frozen?.page ?? START_PAGE);
  const [picked, setPicked] = useState<Set<string>>(() => new Set(frozen?.picked ?? []));
  const [time, setTime] = useState(0);

  const page = useSharedValue(frozen?.page ?? START_PAGE);
  const open = useSharedValue(frozen?.open ?? 0);
  const cover = useSharedValue(frozen?.cover ?? 0);
  const seatClock = useSharedValue(frozen?.seatClock ?? 0);
  const payClock = useSharedValue(frozen?.payClock ?? 0);
  const night = useSharedValue(0);
  const veil = useSharedValue(0); // Reduce Motion: page changes cross-fade under this
  const castX = useSharedValue(frozen?.cast ?? 0);
  const buttonHidden = useSharedValue(frozen && frozen.stage !== 'cards' && frozen.stage !== 'detail' ? 1 : 0);
  const castRef = useAnimatedRef<Animated.ScrollView>();
  const scripted = useSharedValue(false); // the script is driving (no haptics)
  const accessibilityGeneration = useSharedValue(0);
  const exposeStageAfter = useCallback((next: AccessibleStage, delay: number) => {
    const generation = accessibilityGeneration.get() + 1;
    accessibilityGeneration.set(generation);
    setAccessibleStage(delay <= 0 ? next : 'transition');
    if (delay > 0) setTimeout(() => {
      if (accessibilityGeneration.get() === generation) setAccessibleStage(next);
    }, delay);
  }, [accessibilityGeneration]);

  useAnimatedReaction(() => castX.get(), (x, prev) => {
    if (prev !== null && x !== prev) scrollTo(castRef, -x, 0, false);
  });

  // ---- actions shared by the script and the hand ----
  const goPage = useCallback((to: number) => {
    const target = Math.max(0, Math.min(LAST, to));
    setActive(target);
    if (!scripted.get()) Haptics.selectionAsync().catch(() => {});
    if (reduced) {
      veil.set(withSequence(withTiming(1, { duration: REDUCED_FADE_MS / 2, reduceMotion: NEVER }),
        withTiming(0, { duration: REDUCED_FADE_MS / 2, reduceMotion: NEVER })));
      page.set(withSequence(withTiming(page.get(), { duration: REDUCED_FADE_MS / 2, reduceMotion: NEVER }),
        withTiming(target, { duration: 0, reduceMotion: NEVER })));
    } else {
      page.set(withSpring(target, { ...PAGE_SPRING, reduceMotion: NEVER }));
    }
  }, [page, reduced, scripted, veil]);

  const openFilm = useCallback(() => {
    setStage('detail');
    exposeStageAfter('detail', (reduced ? REDUCED_FADE_MS : OPEN.ms) * 0.35);
    open.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : OPEN.ms, easing: OPEN.easing, reduceMotion: NEVER }));
  }, [exposeStageAfter, open, reduced]);

  const closeFilm = useCallback(() => {
    open.set(withTiming(0, { duration: reduced ? REDUCED_FADE_MS : OPEN.ms * 0.8, easing: OPEN.easing, reduceMotion: NEVER }));
    setStage('cards');
    exposeStageAfter('cards', (reduced ? REDUCED_FADE_MS : OPEN.ms * 0.8) * 0.65);
  }, [exposeStageAfter, open, reduced]);

  const buy = useCallback(() => {
    if (open.get() < 0.5) {
      open.set(1);
    }
    setStage('seats');
    exposeStageAfter('seats', SEATS_ACCESSIBLE_AT);
    buttonHidden.set(withTiming(1, { duration: 160, reduceMotion: NEVER }));
    cover.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : COVER.ms, easing: COVER.easing, reduceMotion: NEVER }));
    seatClock.set(0);
    seatClock.set(withTiming(SEAT_CLOCK_MS, { duration: SEAT_CLOCK_MS, easing: Easing.linear, reduceMotion: NEVER }));
  }, [buttonHidden, cover, exposeStageAfter, open, reduced, seatClock]);

  const backFromSeats = useCallback(() => {
    cover.set(withTiming(0, { duration: reduced ? REDUCED_FADE_MS : COVER.ms * 0.8, easing: COVER.easing, reduceMotion: NEVER }));
    buttonHidden.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
    setStage('detail');
    exposeStageAfter('detail', (reduced ? REDUCED_FADE_MS : COVER.ms * 0.8) * 0.65);
    setPicked(new Set());
  }, [buttonHidden, cover, exposeStageAfter, reduced]);

  const toggleSeat = useCallback((id: string) => {
    if (!scripted.get()) Haptics.selectionAsync().catch(() => {});
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }, [scripted]);

  const resetAll = useCallback((fade: boolean) => {
    // Everything snaps back to the first appearance while the screen is black, then the cards fade up.
    night.set(1);
    setStage('cards');
    exposeStageAfter('cards', fade ? RESET.ms : 0);
    setActive(START_PAGE);
    setPicked(new Set());
    setTime(0);
    page.set(START_PAGE);
    open.set(0);
    cover.set(0);
    buttonHidden.set(0);
    castX.set(0);
    payClock.set(0);
    seatClock.set(0);
    night.set(fade ? withTiming(0, { duration: RESET.ms, easing: Easing.out(Easing.quad), reduceMotion: NEVER }) : 0);
  }, [buttonHidden, castX, cover, exposeStageAfter, night, open, page, payClock, seatClock]);

  const payTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const payResetTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const payGeneration = useRef(0);
  const cancelPayTimers = useCallback(() => {
    payGeneration.current += 1;
    if (payTimer.current) clearTimeout(payTimer.current);
    if (payResetTimer.current) clearTimeout(payResetTimer.current);
    payTimer.current = null;
    payResetTimer.current = null;
  }, []);
  const pay = useCallback(() => {
    setStage('pay');
    exposeStageAfter('pay', PAY_ACCESSIBLE_AT);
    payClock.set(0);
    payClock.set(withTiming(PAY_CLOCK_MS, { duration: PAY_CLOCK_MS, easing: Easing.linear, reduceMotion: NEVER }));
    if (!scripted.get()) {
      cancelPayTimers();
      const owner = payGeneration.current;
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
      payTimer.current = setTimeout(() => {
        if (owner !== payGeneration.current) return;
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
        payResetTimer.current = setTimeout(() => {
          if (owner === payGeneration.current) resetAll(true);
        }, 700);
      }, PAY.checkAt + PAY.checkMs);
    }
  }, [cancelPayTimers, exposeStageAfter, payClock, resetAll, scripted]);

  // ---- autoplay: one schedule of cues per loop ----
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const runCue = useCallback((cue: Cue) => {
    switch (cue.kind) {
      case 'page': goPage(cue.to); break;
      case 'open': openFilm(); break;
      case 'cast': castX.set(withSpring(cue.dx, { ...CAST_SPRING, reduceMotion: NEVER })); break;
      case 'buy': buy(); break;
      case 'seat': toggleSeat(cue.seat); break;
      case 'pay': pay(); break;
      case 'reset': resetAll(true); break;
    }
  }, [buy, castX, goPage, openFilm, pay, resetAll, toggleSeat]);

  const loop = useRef<ReturnType<typeof setInterval> | null>(null);
  const manualOwned = useRef(false);
  const clearTimers = useCallback(() => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    if (loop.current) clearInterval(loop.current);
    loop.current = null;
  }, []);

  const scheduleCues = useCallback(() => {
    timers.current = SCRIPT.map((cue) => setTimeout(() => runCue(cue), cue.at));
  }, [runCue]);

  const startLoop = useCallback(() => {
    if (!canAutoPlay) return;
    manualOwned.current = false;
    clearTimers();
    scripted.set(true);
    scheduleCues();
    loop.current = setInterval(scheduleCues, LOOP_MS);
  }, [canAutoPlay, clearTimers, scheduleCues, scripted]);

  const idle = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resumeInFlight = useRef(false);
  const finishResume = useCallback(() => {
    if (!resumeInFlight.current) return;
    resumeInFlight.current = false;
    if (!canAutoPlayRef.current) return;
    manualOwned.current = false;
    resetAll(true);
    startLoop();
  }, [resetAll, startLoop]);
  const takeOver = useCallback(() => {
    manualOwned.current = true;
    if (idle.current) clearTimeout(idle.current);
    if (resumeInFlight.current) {
      resumeInFlight.current = false;
      cancelAnimation(night);
      night.set(0);
    }
    if (scripted.get()) {
      clearTimers();
      scripted.set(false);
    }
  }, [clearTimers, night, scripted]);
  const scheduleResume = useCallback(() => {
    if (!canAutoPlay) return;
    if (idle.current) clearTimeout(idle.current);
    idle.current = setTimeout(() => {
      if (!canAutoPlayRef.current) return;
      cancelPayTimers();
      resumeInFlight.current = true;
      night.set(withTiming(1, { duration: 220, reduceMotion: NEVER }, (done) => {
        if (done) scheduleOnRN(finishResume);
      }));
    }, IDLE_RESUME_MS);
  }, [canAutoPlay, cancelPayTimers, finishResume, night]);

  useEffect(() => {
    let resetScriptedScene: ReturnType<typeof setTimeout> | null = null;
    const wasScripted = scripted.get();
    if (canAutoPlay && fontsLoaded) {
      if (manualOwned.current) scheduleResume();
      else startLoop();
    } else {
      clearTimers();
      if (idle.current) clearTimeout(idle.current);
      scripted.set(false);
      if (resumeInFlight.current) {
        resumeInFlight.current = false;
        cancelAnimation(night);
        night.set(0);
      }
      if (wasScripted) {
        for (const value of [page, open, cover, seatClock, payClock, night, veil, castX, buttonHidden]) cancelAnimation(value);
        resetScriptedScene = setTimeout(() => {
          if (!canAutoPlayRef.current && !scripted.get()) {
            manualOwned.current = false;
            resetAll(false);
          }
        }, 0);
      }
    }
    return () => {
      if (resetScriptedScene) clearTimeout(resetScriptedScene);
      clearTimers();
      if (idle.current) clearTimeout(idle.current);
      accessibilityGeneration.set(accessibilityGeneration.get() + 1);
    };
  }, [accessibilityGeneration, buttonHidden, canAutoPlay, castX, clearTimers, cover, fontsLoaded, night, open, page, payClock, resetAll, scheduleResume, scripted, seatClock, startLoop, veil]);

  useEffect(() => () => cancelPayTimers(), [cancelPayTimers]);

  // The pan can swallow the touch end, so a finished pan also re-arms the idle timer.
  const released = useSharedValue(0);
  useAnimatedReaction(() => released.get(), (v, prev) => {
    if (prev !== null && v !== prev) scheduleOnRN(scheduleResume);
  });
  // ---- the hand on the cards ----
  const startPage = useSharedValue(START_PAGE);
  const pan = Gesture.Pan().enabled(accessibleStage === 'cards').activeOffsetX([-10, 10])
    .onBegin(() => { startPage.set(page.get()); })
    .onUpdate((e) => {
      const raw = startPage.get() - e.translationX / CAROUSEL.pitch;
      // Rubber band past the ends.
      const over = raw < 0 ? raw : raw > LAST ? raw - LAST : 0;
      page.set(raw - over + over * 0.3);
    })
    .onEnd((e) => {
      const projected = page.get() - (e.velocityX / CAROUSEL.pitch) * 0.12;
      scheduleOnRN(goPage, Math.round(projected));
    })
    .onFinalize(() => { released.set(released.get() + 1); });
  const tap = Gesture.Tap().enabled(accessibleStage === 'cards').onEnd((e) => {
    // Only the centre card opens; a tap on a side card brings it to the centre.
    const dx = e.x - width / 2;
    if (Math.abs(dx) < CAROUSEL.cardW / 2) scheduleOnRN(openFilm);
    else scheduleOnRN(goPage, Math.round(page.get()) + (dx > 0 ? 1 : -1));
  });

  const coverR = Math.hypot(width, height);
  const coverX = width / 2 + 60;
  const coverY = DETAIL.sheetTop + DETAIL.buttonY;
  const coverStyle = useAnimatedStyle(() => ({
    opacity: reduced ? cover.get() : 1,
    transform: [{ scale: reduced ? 1 : Math.max(0.0001, cover.get()) }],
  }));
  const nightStyle = useAnimatedStyle(() => ({ opacity: night.get() }));
  const veilStyle = useAnimatedStyle(() => ({ opacity: veil.get() }));

  if (!fontsLoaded) return <View style={[styles.root, { backgroundColor: COLORS.night }]} />;
  const movie = MOVIES[active];
  const inSeats = stage === 'seats' || stage === 'pay';
  const cardsActive = accessibleStage === 'cards';
  const detailActive = accessibleStage === 'detail';
  const seatsActive = accessibleStage === 'seats';
  const closeActive = detailActive || seatsActive;

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={takeOver} onTouchEnd={scheduleResume} onTouchCancel={scheduleResume}>
        <StatusBar style="light" hidden={false} />
        <Backdrops page={page} open={open} width={width} />
        <GestureDetector gesture={Gesture.Race(pan, tap)}>
          <View style={StyleSheet.absoluteFill}
            accessibilityElementsHidden={!cardsActive}
            importantForAccessibility={cardsActive ? 'auto' : 'no-hide-descendants'}>
            <CardDeck page={page} open={open} width={width} />
            <CardInfo page={page} open={open} width={width} />
          </View>
        </GestureDetector>
        <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.paper }, veilStyle]} pointerEvents="none" />
        <DetailSheet movie={movie} open={open} width={width} height={height} castRef={castRef} active={detailActive} />
        <BuyButton open={open} width={width} onPress={buy} hidden={buttonHidden} enabled={cardsActive || detailActive} />

        <Animated.View pointerEvents="none" style={[styles.cover, {
          left: coverX - coverR, top: coverY - coverR, width: coverR * 2, height: coverR * 2, borderRadius: coverR,
        }, coverStyle]} />
        {inSeats && (
          <SeatScreen width={width} clock={seatClock} payClock={payClock} picked={picked} onSeat={toggleSeat}
            reduced={reduced}
            onPay={pay} time={time} onTime={setTime} active={seatsActive} />
        )}
        {stage === 'pay' && <PayOverlay clock={payClock} width={width} height={height} reduced={reduced} active={accessibleStage === 'pay'} />}

        <Pressable accessibilityRole="button" accessibilityLabel={stage === 'seats' ? 'Back to film' : 'Close'}
          onPress={stage === 'seats' ? backFromSeats : stage === 'detail' ? closeFilm : undefined}
          disabled={!closeActive}
          accessible={closeActive}
          accessibilityElementsHidden={!closeActive}
          importantForAccessibility={closeActive ? 'auto' : 'no-hide-descendants'}
          style={[styles.icon, { left: TOP.iconX - 22, top: TOP.iconY - 22 }]}>
          <CloseIcon size={TOP.icon - 2} color={COLORS.paper} />
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Nearby cinemas"
          style={[styles.icon, { left: TOP.targetX - 22, top: TOP.targetY - 22 }]}>
          <TargetIcon size={TOP.target} color={COLORS.paper} />
        </Pressable>
        <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.night }, nightStyle]} pointerEvents="none" />
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.paper },
  cover: { position: 'absolute', backgroundColor: COLORS.night },
  icon: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
});

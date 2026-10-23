/**
 * The whole interaction on the UI thread. A real pinch and the autoplay's scripted fingers call the same
 * three worklets (pinchBegin / pinchMove / pinchEnd), so the preview shows exactly what a finger does.
 */
import { useCallback, useMemo, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation, Easing, ReduceMotion, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring,
  withTiming, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { clamp01, mix, type Stage } from './geometry';
import { playSummaryHaptic, type SummaryHaptic } from './haptics';
import { AUTOPLAY, LOOP_MS, PINCH, QA_HOLD, RIM, SPRING, TIMING } from './motion';
import { COPY, LAYOUT } from './theme';

export type Mode = 'reading' | 'thinking' | 'summary';
const READING = 0;
const THINKING = 1;
const SUMMARY = 2;
const MODES: Mode[] = ['reading', 'thinking', 'summary'];

export type SummaryValues = {
  p: SharedValue<number>;
  lift: SharedValue<number>;
  driftX: SharedValue<number>;
  driftY: SharedValue<number>;
  think: SharedValue<number>;
  rim: SharedValue<number>;
  reveal: SharedValue<number>;
  summaryAlpha: SharedValue<number>;
  pageAlpha: SharedValue<number>;
  clock: SharedValue<number>;
  bloomAt: SharedValue<number>;
  fingerAX: SharedValue<number>;
  fingerAY: SharedValue<number>;
  fingerBX: SharedValue<number>;
  fingerBY: SharedValue<number>;
  fingerAlpha: SharedValue<number>;
};

type Options = { stage: Stage; ready: boolean; autoPlay: boolean; reduceMotion: boolean; revealTotal: number; lockScroll: (locked: boolean) => void };

const never = ReduceMotion.Never;

/** Apple's rubber band: the further past the edge, the less the page follows (never a hard stop). */
function band(over: number): number {
  'worklet';
  return (over * PINCH.rubber) / (1 + PINCH.rubber * Math.abs(over));
}

function rubber(raw: number): number {
  'worklet';
  if (raw > 1) return 1 + band(raw - 1);
  if (raw < 0) return -band(-raw);
  return raw;
}

/** Where the progress would come to rest if the pinch kept decelerating (exponential decay, per second). */
function project(velocity: number): number {
  'worklet';
  return ((velocity / 1000) * PINCH.deceleration) / (1 - PINCH.deceleration);
}

function ease(u: number): number {
  'worklet';
  const t = clamp01(u);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function useSummaryMotion({ stage, ready, autoPlay, reduceMotion, revealTotal, lockScroll }: Options) {
  const [mode, setMode] = useState<Mode>('reading');
  const p = useSharedValue(0);
  const lift = useSharedValue(0);
  const driftX = useSharedValue(0);
  const driftY = useSharedValue(0);
  const think = useSharedValue(0);
  const rim = useSharedValue(0);
  const reveal = useSharedValue(0);
  const summaryAlpha = useSharedValue(0);
  const pageAlpha = useSharedValue(1);
  const clock = useSharedValue(0);
  const bloomAt = useSharedValue(-1);
  const fingerAX = useSharedValue(0);
  const fingerAY = useSharedValue(0);
  const fingerBX = useSharedValue(0);
  const fingerBY = useSharedValue(0);
  const fingerAlpha = useSharedValue(0);
  // Logic state (UI thread only).
  const modeId = useSharedValue(READING);
  const job = useSharedValue(0);
  const pinch = useSharedValue({ active: false, out: false, startP: 0, fx: 0, fy: 0, track: 0, armed: false, scripted: false, lastScale: 1 });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, segment: 0 });

  const report = useCallback((id: number) => {
    setMode(MODES[id]);
    if (id === SUMMARY) AccessibilityInfo.announceForAccessibility('Summary ready');
  }, []);
  const haptic = useCallback((kind: SummaryHaptic) => playSummaryHaptic(kind), []);

  const setModeId = useCallback((id: number) => {
    'worklet';
    if (modeId.get() === id) return;
    modeId.set(id);
    scheduleOnRN(report, id);
  }, [modeId, report]);

  const buzz = useCallback((kind: SummaryHaptic, scripted: boolean) => {
    'worklet';
    if (!scripted) scheduleOnRN(haptic, kind);
  }, [haptic]);

  /** The model's answer has arrived: the card glides up and the lines stream in. */
  const arrive = useCallback((token: number, scripted: boolean) => {
    'worklet';
    if (job.get() !== token || modeId.get() !== THINKING) return;
    setModeId(SUMMARY);
    buzz('ready', scripted);
    if (!reduceMotion) bloomAt.set(clock.get());
    rim.set(withTiming(0, { duration: reduceMotion ? 0 : TIMING.rimOut, reduceMotion: never }));
    think.set(withTiming(0, { duration: reduceMotion ? TIMING.reducedFade : TIMING.rimOut, reduceMotion: never }));
    lift.set(reduceMotion ? 1 : withSpring(1, { ...SPRING.lift, reduceMotion: never }));
    summaryAlpha.set(1);
    reveal.set(0);
    reveal.set(withTiming(revealTotal, {
      duration: reduceMotion ? TIMING.reducedFade : revealTotal, easing: Easing.linear, reduceMotion: never,
    }));
  }, [bloomAt, buzz, clock, job, lift, modeId, reduceMotion, revealTotal, reveal, rim, setModeId, summaryAlpha, think]);

  /** Page → card. In Reduce Motion the page cross-fades to the card instead of travelling. */
  const collapse = useCallback((velocity: number, scripted: boolean) => {
    'worklet';
    const token = job.get() + 1;
    job.set(token);
    setModeId(THINKING);
    scheduleOnRN(lockScroll, true);
    cancelAnimation(driftX);
    cancelAnimation(driftY);
    driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    if (reduceMotion) {
      lift.set(1);
      pageAlpha.set(withSequence(
        withTiming(0, { duration: TIMING.reducedFade, reduceMotion: never }, () => { p.set(1); }),
        withTiming(1, { duration: TIMING.reducedFade, reduceMotion: never }),
      ));
    } else {
      lift.set(0);
      p.set(withSpring(1, { ...SPRING.collapse, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }, (done) => {
        if (done) buzz('land', scripted);
      }));
      rim.set(withTiming(RIM.think, { duration: TIMING.thinkIn, reduceMotion: never }));
    }
    think.set(withSequence(
      withTiming(1, { duration: TIMING.thinkIn, reduceMotion: never }),
      withDelay(TIMING.thinkMs - TIMING.thinkIn, withTiming(1, { duration: TIMING.tick, reduceMotion: never }, (done) => {
        if (done && QA_HOLD !== 'a') arrive(token, scripted);
      })),
    ));
  }, [arrive, buzz, driftX, driftY, job, lift, lockScroll, p, pageAlpha, reduceMotion, rim, setModeId, think]);

  /** Card → page. Also cancels a summary still being made. */
  const restore = useCallback((velocity: number, scripted: boolean) => {
    'worklet';
    job.set(job.get() + 1);
    setModeId(READING);
    buzz('restore', scripted);
    think.set(withTiming(0, { duration: TIMING.reducedFade, reduceMotion: never }));
    summaryAlpha.set(withTiming(0, { duration: TIMING.reducedFade, reduceMotion: never }));
    driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    const settle = () => {
      'worklet';
      lift.set(0);
      reveal.set(0);
      scheduleOnRN(lockScroll, false);
    };
    if (reduceMotion) {
      rim.set(0);
      pageAlpha.set(withSequence(
        withTiming(0, { duration: TIMING.reducedFade, reduceMotion: never }, () => { p.set(0); settle(); }),
        withTiming(1, { duration: TIMING.reducedFade, reduceMotion: never }),
      ));
      return;
    }
    // The rim flashes along the growing edge, then lets go.
    rim.set(withSequence(
      withTiming(RIM.pinchMax + RIM.armedBoost, { duration: TIMING.rimFlashIn, reduceMotion: never }),
      withTiming(0, { duration: TIMING.rimFlashOut, reduceMotion: never }),
    ));
    p.set(withSpring(0, { ...SPRING.restore, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }, (done) => {
      if (done) settle();
    }));
  }, [buzz, driftX, driftY, job, lift, lockScroll, p, pageAlpha, reduceMotion, reveal, rim, setModeId, summaryAlpha, think]);

  const pinchBegin = useCallback((fx: number, fy: number, scripted: boolean) => {
    'worklet';
    if (!ready) return;
    const out = modeId.get() !== READING;
    cancelAnimation(p);
    cancelAnimation(driftX);
    cancelAnimation(driftY);
    pinch.set({ active: true, out, startP: p.get(), fx, fy, track: p.get(), armed: false, scripted, lastScale: 1 });
    if (!out) scheduleOnRN(lockScroll, true);
  }, [driftX, driftY, lockScroll, modeId, p, pinch, ready]);

  const pinchMove = useCallback((scale: number, fx: number, fy: number) => {
    'worklet';
    const g = pinch.get();
    if (!g.active) return;
    const raw = g.out ? g.startP - (scale - 1) / (PINCH.fullOut - 1) : g.startP + (1 - scale) / (1 - PINCH.fullIn);
    const track = rubber(raw);
    const armed = g.out ? track < PINCH.restoreAt : track > PINCH.commitAt;
    if (armed !== g.armed) buzz(armed ? 'armed' : 'disarmed', g.scripted);
    pinch.set({ ...g, track, armed, lastScale: scale });
    if (reduceMotion) return;
    p.set(track);
    driftX.set((fx - g.fx) * PINCH.follow);
    driftY.set((fy - g.fy) * PINCH.follow);
    if (g.out) {
      summaryAlpha.set(clamp01((track - PINCH.restoreAt) / (TIMING.summaryOutStart - PINCH.restoreAt)));
      rim.set(armed ? RIM.pinchMax + RIM.armedBoost : 0);
    } else {
      rim.set(clamp01(track / PINCH.commitAt) * RIM.pinchMax + (armed ? RIM.armedBoost : 0));
    }
  }, [buzz, driftX, driftY, p, pinch, reduceMotion, rim, summaryAlpha]);

  const pinchEnd = useCallback((velocity: number, cancelled: boolean) => {
    'worklet';
    const g = pinch.get();
    if (!g.active) return;
    pinch.set({ ...g, active: false });
    const track = g.track;
    if (!g.out) {
      // Velocity or distance: a short, fast pinch commits; a slow one that never got far does not.
      const commit = !cancelled && track + project(-velocity / (1 - PINCH.fullIn)) > PINCH.commitAt;
      if (commit) collapse(velocity, g.scripted);
      else {
        scheduleOnRN(lockScroll, false);
        rim.set(withTiming(0, { duration: TIMING.rimLetGo, reduceMotion: never }));
        p.set(withSpring(0, { ...SPRING.restore, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }));
        driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
        driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
      }
      return;
    }
    const back = !cancelled && track + project(-velocity / (PINCH.fullOut - 1)) < PINCH.restoreAt;
    if (back) restore(velocity, g.scripted);
    else {
      rim.set(withTiming(0, { duration: TIMING.rimLetGo, reduceMotion: never }));
      summaryAlpha.set(withTiming(modeId.get() === SUMMARY ? 1 : 0, { duration: TIMING.reducedFade, reduceMotion: never }));
      p.set(withSpring(1, { ...SPRING.collapse, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }));
      driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
      driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    }
  }, [collapse, driftX, driftY, lockScroll, modeId, p, pinch, restore, rim, summaryAlpha]);

  /** A real touch: stop the script now, cancelling its fingers if they are down. */
  const touch = useCallback((down: boolean) => {
    'worklet';
    const s = script.get();
    if (down && !s.paused && pinch.get().active && pinch.get().scripted) pinchEnd(0, true);
    if (down) fingerAlpha.set(withTiming(0, { duration: TIMING.fingerFade, reduceMotion: never }));
    script.set({ ...s, paused: true, realDown: down, quiet: 0, segment: 0 });
  }, [fingerAlpha, pinch, pinchEnd, script]);

  /** Places both scripted fingers on a diagonal through (cx, cy). */
  const placeFingers = useCallback((cx: number, cy: number, spread: number) => {
    'worklet';
    const dx = 0.36 * spread / 2;
    const dy = 0.93 * spread / 2;
    fingerAX.set(cx + dx);
    fingerAY.set(cy - dy);
    fingerBX.set(cx - dx);
    fingerBY.set(cy + dy);
  }, [fingerAX, fingerAY, fingerBX, fingerBY]);

  const runScript = useCallback((dt: number) => {
    'worklet';
    const s = script.get();
    const t = s.time;
    const A = AUTOPLAY;
    const inPinch = t >= A.pinchInStart && t < A.pinchInEnd;
    const outPinch = t >= A.pinchOutStart && t < A.pinchOutEnd;
    const segment = inPinch ? 1 : outPinch ? 2 : 0;
    const cx = stage.width / 2;
    const cyIn = stage.height * 0.46;
    const cyOut = LAYOUT.summaryTop + LAYOUT.cardHeight / 2;
    if (segment !== s.segment) {
      if (s.segment !== 0) {
        // Land the path exactly on its end before lifting, so every loop releases from the same pose.
        if (s.segment === 1) { placeFingers(cx, cyIn, A.spreadIn * A.pinchInScale); pinchMove(A.pinchInScale, cx, cyIn); }
        else { placeFingers(cx, cyOut, A.spreadOut * A.pinchOutScale); pinchMove(A.pinchOutScale, cx, cyOut); }
        pinchEnd(0, false);
        fingerAlpha.set(withTiming(0, { duration: TIMING.fingerFade, reduceMotion: never }));
      }
      if (segment !== 0) {
        const cy = segment === 1 ? cyIn : cyOut;
        placeFingers(cx, cy, segment === 1 ? A.spreadIn : A.spreadOut);
        fingerAlpha.set(withTiming(1, { duration: TIMING.fingerFade, reduceMotion: never }));
        pinchBegin(cx, cy, true);
      }
    }
    if (segment === 1) {
      const u = ease((t - A.pinchInStart) / (A.pinchInEnd - A.pinchInStart));
      const scale = mix(1, A.pinchInScale, u);
      placeFingers(cx, cyIn, A.spreadIn * scale);
      pinchMove(scale, cx, cyIn);
    } else if (segment === 2) {
      const u = ease((t - A.pinchOutStart) / (A.pinchOutEnd - A.pinchOutStart));
      const scale = mix(1, A.pinchOutScale, u);
      placeFingers(cx, cyOut, A.spreadOut * scale);
      pinchMove(scale, cx, cyOut);
    }
    let next = t + dt;
    // QA hold: stop the clock where the requested state is on screen.
    if (QA_HOLD === 'rest') next = 0;
    else if (QA_HOLD !== null && t >= AUTOPLAY.qaHoldAt) next = t;
    if (next >= LOOP_MS) {
      next -= LOOP_MS;
      // The field is hidden at rest; restarting its clock makes every loop of the preview identical.
      clock.set(0);
    }
    script.set({ ...script.get(), time: next, segment });
  }, [clock, fingerAlpha, pinchBegin, pinchEnd, pinchMove, placeFingers, script, stage.height, stage.width]);

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min(frame.timeSincePreviousFrame ?? 0, TIMING.maxFrameMs);
    if (!ready) return;
    // The light clock only runs while something lit is on screen, and never under Reduce Motion.
    if (!reduceMotion && (p.get() > 0.001 || think.get() > 0)) clock.set(clock.get() + dt);
    if (!autoPlay) return;
    const s = script.get();
    if (s.paused) {
      const quiet = s.realDown ? 0 : s.quiet + dt;
      const id = modeId.get();
      const settled = !pinch.get().active && id !== THINKING && (id === SUMMARY || Math.abs(p.get()) < 0.002);
      if (quiet >= AUTOPLAY.resumeDelayMs && settled) {
        script.set({ time: id === SUMMARY ? AUTOPLAY.resumeSummary : AUTOPLAY.resumeReading, paused: false, realDown: false, quiet: 0, segment: 0 });
      } else script.set({ ...s, quiet });
      return;
    }
    runScript(dt);
  });

  const gesture = useMemo(() => Gesture.Pinch()
    .enabled(ready)
    .onTouchesDown(() => { touch(true); })
    .onStart((e) => { pinchBegin(e.focalX, e.focalY, false); })
    .onUpdate((e) => { pinchMove(e.scale, e.focalX, e.focalY); })
    .onEnd((e) => { pinchEnd(e.velocity, false); })
    .onFinalize((_e, success) => {
      if (!success) pinchEnd(0, true);
      touch(false);
    }), [pinchBegin, pinchEnd, pinchMove, ready, touch]);

  const summarize = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (modeId.get() === READING) collapse(0, false);
    });
  }, [collapse, modeId, touch]);

  const showArticle = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (modeId.get() !== READING) restore(0, false);
    });
  }, [modeId, restore, touch]);

  const onTouch = useCallback((down: boolean) => { scheduleOnUI(touch, down); }, [touch]);

  const values = useMemo<SummaryValues>(() => ({
    p, lift, driftX, driftY, think, rim, reveal, summaryAlpha, pageAlpha, clock, bloomAt, fingerAX, fingerAY, fingerBX, fingerBY, fingerAlpha,
  }), [p, lift, driftX, driftY, think, rim, reveal, summaryAlpha, pageAlpha, clock, bloomAt, fingerAX, fingerAY, fingerBX, fingerBY, fingerAlpha]);

  return { values, gesture, mode, summarize, showArticle, onTouch, summaryText: `${COPY.lead} ${COPY.items.map((i) => `${i.title}: ${i.body}`).join(' ')}` };
}

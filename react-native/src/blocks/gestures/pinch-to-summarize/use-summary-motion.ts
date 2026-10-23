/**
 * The whole interaction on the UI thread. A real pinch and the autoplay's scripted fingers call the same three
 * worklets (pinchBegin / pinchMove / pinchEnd), so the preview shows exactly what a finger does.
 *
 * reading → pinch in (sentences lock in, depth follows) → release past the threshold → waiting (the page holds,
 * the picked sentences pulse) → Distill (each sentence flies into its summary line, the page becomes the card) →
 * summary → pinch out, close or tap a point → the sentences fly back and the article returns.
 */
import { useCallback, useMemo, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation, Easing, ReduceMotion, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSequence,
  withSpring, withTiming, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { breathing, clamp01, mix, pageFrame, span, type KeyGeom, type Stage } from './geometry';
import { playSummaryHaptic, type SummaryHaptic } from './haptics';
import { AUTOPLAY, CURVE, DEPTH, FLIGHT, HOLD, LOOP_MS, MARK, PINCH, QA_HOLD, RIM, SPRING, TIMING } from './motion';
import { BACK_MS, summaryTop, type Level, type SummaryLayout } from './summary-layout';
import { LAYOUT } from './theme';

export type Mode = 'reading' | 'thinking' | 'summary';
const READING = 0;
const THINKING = 1;
const SUMMARY = 2;
const MODES: Mode[] = ['reading', 'thinking', 'summary'];

/** Where a chip leaves from: its sentence's top-left on screen, the page scale there, and whether it was in view. */
export type ChipFrom = { x: number; y: number; k: number; vis: number };

/**
 * The article's scroll tied to the page's progress: while `on`, the scroll is `from` at p = p0 and `to` at p = p1, eased
 * between. One value (p) then moves the card and its content together, so they can never drift apart or fight.
 */
export type ScrollLink = { on: boolean; from: number; to: number; p0: number; p1: number };
const NO_LINK: ScrollLink = { on: false, from: 0, to: 0, p0: 0, p1: 1 };

export type SummaryValues = {
  p: SharedValue<number>;
  lift: SharedValue<number>;
  driftX: SharedValue<number>;
  driftY: SharedValue<number>;
  think: SharedValue<number>;
  rim: SharedValue<number>;
  summaryAlpha: SharedValue<number>;
  extraAlpha: SharedValue<number>;
  pageAlpha: SharedValue<number>;
  clock: SharedValue<number>;
  // The waiting page's breathing (a primitive derived from think and the clock: it stays 0, and wakes no one, while
  // the page is not waiting).
  breath: SharedValue<number>;
  bloomAt: SharedValue<number>;
  fingerAX: SharedValue<number>;
  fingerAY: SharedValue<number>;
  fingerBX: SharedValue<number>;
  fingerBY: SharedValue<number>;
  fingerAlpha: SharedValue<number>;
  // The highlighter: how many sentences are marked (continuous while sweeping), their visibility, the lock pops.
  mark: SharedValue<number>;
  markAlpha: SharedValue<number>;
  pops: SharedValue<number>[];
  dim: SharedValue<number>;
  // Time since the commit (drives the pulses and "Summarizing for you…").
  wait: SharedValue<number>;
  level: SharedValue<number>;
  levelPos: SharedValue<number>;
  depthAlpha: SharedValue<number>;
  // The Distill flight: its clock (ms), direction (1 in, −1 back) and where each chip leaves from.
  flightD: SharedValue<number>;
  flightDir: SharedValue<number>;
  chipFrom: SharedValue<ChipFrom[]>;
  // 1 normally; a chip caught mid-flight by a new gesture fades to 0 where it is (and its page sentence fades in).
  chipFade: SharedValue<number>;
  // Fingers on the screen (two or more: a pinch, so presses underneath stay quiet).
  fingers: SharedValue<number>;
  // The article's scroll, and the scroll the motion drives (while `scrolling`).
  scrollY: SharedValue<number>;
  scrollPos: SharedValue<number>;
  scrolling: SharedValue<boolean>;
  scrollLink: SharedValue<ScrollLink>;
  readPct: SharedValue<number>;
  flash: SharedValue<number>;
  flashIdx: SharedValue<number>;
};

type Options = {
  stage: Stage;
  ready: boolean;
  autoPlay: boolean;
  reduceMotion: boolean;
  keys: KeyGeom[];
  maxScroll: number;
  layouts: SummaryLayout[];
  lockScroll: (locked: boolean) => void;
  onLevel: (level: Level) => void;
};

const never = ReduceMotion.Never;
const EASE_SHEET = Easing.bezier(CURVE.sheet[0], CURVE.sheet[1], CURVE.sheet[2], CURVE.sheet[3]);
const EASE_IN_OUT = Easing.bezier(CURVE.inOut[0], CURVE.inOut[1], CURVE.inOut[2], CURVE.inOut[3]);
const EASE_OUT = Easing.bezier(CURVE.out[0], CURVE.out[1], CURVE.out[2], CURVE.out[3]);

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

/**
 * The scripted fingers' travel: a minimum-jerk profile (10t³ − 15t⁴ + 6t⁵), the shape of a real hand's reach. Its
 * speed peaks at 1.9× the average and its acceleration is continuous; the cubic in-out it replaces peaked at 3× and
 * flipped its acceleration in one frame at the midpoint, which the page and the scroll tied to it showed as a lurch.
 */
function ease(u: number): number {
  'worklet';
  const t = clamp01(u);
  return t * t * t * (10 + t * (6 * t - 15));
}

/** The depth the pinch asks for, with a little hysteresis at each boundary so it never chatters. */
function depthFor(track: number, current: number): number {
  'worklet';
  const next = track > DEPTH.tldr ? 2 : track > DEPTH.brief ? 1 : 0;
  if (next === current) return current;
  const edge = Math.max(next, current) === 2 ? DEPTH.tldr : DEPTH.brief;
  return Math.abs(track - edge) < DEPTH.hysteresis ? current : next;
}

export function useSummaryMotion({ stage, ready, autoPlay, reduceMotion, keys, maxScroll, layouts, lockScroll, onLevel }: Options) {
  const [mode, setMode] = useState<Mode>('reading');
  const p = useSharedValue(0);
  const lift = useSharedValue(0);
  const driftX = useSharedValue(0);
  const driftY = useSharedValue(0);
  const think = useSharedValue(0);
  const rim = useSharedValue(0);
  const summaryAlpha = useSharedValue(1);
  const extraAlpha = useSharedValue(1);
  const pageAlpha = useSharedValue(1);
  const clock = useSharedValue(0);
  const bloomAt = useSharedValue(-1);
  const fingerAX = useSharedValue(0);
  const fingerAY = useSharedValue(0);
  const fingerBX = useSharedValue(0);
  const fingerBY = useSharedValue(0);
  const fingerAlpha = useSharedValue(0);
  const mark = useSharedValue(0);
  const locked = useSharedValue(0);
  const markAlpha = useSharedValue(1);
  const pop0 = useSharedValue(0);
  const pop1 = useSharedValue(0);
  const pop2 = useSharedValue(0);
  const pop3 = useSharedValue(0);
  const dim = useSharedValue(0);
  const wait = useSharedValue(0);
  const level = useSharedValue(0);
  const levelPos = useSharedValue(0);
  const depthAlpha = useSharedValue(0);
  const flightD = useSharedValue(0);
  const flightDir = useSharedValue(1);
  const chipFrom = useSharedValue<ChipFrom[]>([]);
  const chipFade = useSharedValue(1);
  const fingers = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const scrollPos = useSharedValue(0);
  const scrolling = useSharedValue(false);
  const scrollLink = useSharedValue<ScrollLink>(NO_LINK);
  const breath = useDerivedValue(() => breathing(think.get(), clock.get(), TIMING.breathe, TIMING.breathePeriod));
  const readPct = useSharedValue(0);
  const saved = useSharedValue(0);
  const flash = useSharedValue(0);
  const flashIdx = useSharedValue(-1);
  // Logic state (UI thread only).
  const modeId = useSharedValue(READING);
  const job = useSharedValue(0);
  const pinch = useSharedValue({ active: false, out: false, startP: 0, fx: 0, fy: 0, track: 0, armed: false, scripted: false });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, segment: 0 });
  const totals = useMemo(() => layouts.map((l) => l.total), [layouts]);
  const linesTop = summaryTop();

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

  /** Drives the article's scroll on its own clock (the page view follows `scrollPos` while `scrolling`). */
  const driveScroll = useCallback((to: number, ms: number, sheet: boolean) => {
    'worklet';
    scrollLink.set(NO_LINK);
    cancelAnimation(scrollPos);
    const from = scrollY.get();
    if (Math.abs(to - from) < 0.5) { scrolling.set(false); return; }
    scrollPos.set(from);
    scrolling.set(true);
    scrollPos.set(withTiming(to, { duration: reduceMotion ? 1 : ms, easing: sheet ? EASE_SHEET : EASE_IN_OUT, reduceMotion: never }, (done) => {
      if (done) scrolling.set(false);
    }));
  }, [reduceMotion, scrollLink, scrollPos, scrollY, scrolling]);

  const stopScroll = useCallback(() => {
    'worklet';
    scrollLink.set(NO_LINK);
    cancelAnimation(scrollPos);
    scrolling.set(false);
  }, [scrollLink, scrollPos, scrolling]);

  /**
   * Ties the scroll to the page: from where it is now (at the current p) to `to` when p reaches `p1`. With no distance
   * left for p to travel it falls back to the timed scroll.
   */
  const linkScroll = useCallback((to: number, p1: number) => {
    'worklet';
    cancelAnimation(scrollPos);
    scrolling.set(false);
    const from = scrollY.get();
    const p0 = p.get();
    if (Math.abs(to - from) < 0.5) { scrollLink.set(NO_LINK); return; }
    if (Math.abs(p1 - p0) < 0.02) { driveScroll(to, FLIGHT.scrollMs, true); return; }
    scrollLink.set({ on: true, from, to, p0, p1 });
  }, [driveScroll, p, scrollLink, scrollPos, scrollY, scrolling]);

  const unlinkScroll = useCallback(() => {
    'worklet';
    if (scrollLink.get().on) scrollLink.set(NO_LINK);
  }, [scrollLink]);

  const popAt = useCallback((i: number) => {
    'worklet';
    const pop = i === 0 ? pop0 : i === 1 ? pop1 : i === 2 ? pop2 : pop3;
    pop.set(0);
    pop.set(withSequence(
      withTiming(1, { duration: MARK.popMs * 0.35, easing: EASE_OUT, reduceMotion: never }),
      withTiming(0, { duration: MARK.popMs * 0.65, easing: EASE_OUT, reduceMotion: never }),
    ));
  }, [pop0, pop1, pop2, pop3]);

  const clearMarks = useCallback(() => {
    'worklet';
    cancelAnimation(mark);
    cancelAnimation(markAlpha);
    mark.set(0);
    locked.set(0);
    markAlpha.set(1);
  }, [locked, mark, markAlpha]);

  /**
   * A gesture caught the chips mid-flight: they stop where they are and fade out while their sentences come back on
   * the page, then the flight clock rests (instead of chips vanishing, or jumping to their lines and starting over).
   */
  const settleChips = useCallback(() => {
    'worklet';
    cancelAnimation(flightD);
    cancelAnimation(chipFade);
    chipFade.set(withTiming(0, { duration: FLIGHT.handoffMs, easing: EASE_OUT, reduceMotion: never }, (done) => {
      if (!done) return;
      flightDir.set(1);
      flightD.set(0);
      chipFade.set(1);
    }));
  }, [chipFade, flightD, flightDir]);

  /** Every chip's start: its sentence on the page as it is now on screen. */
  const fromPage = useCallback((): ChipFrom[] => {
    'worklet';
    const f = pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get(), breath.get());
    const scroll = scrollY.get();
    const out: ChipFrom[] = [];
    for (let i = 0; i < keys.length; i++) {
      const k = keys[i];
      const mid = k.y + k.height / 2 - scroll;
      const vis = mid > stage.insetTop && mid < f.clipHeight - LAYOUT.toolbarHeight ? 1 : 0;
      out.push({ x: f.x + k.x * f.scale, y: f.y + (k.y - scroll - f.trim) * f.scale, k: f.scale, vis });
    }
    return out;
  }, [breath, driftX, driftY, keys, lift, p, scrollY, stage]);

  /** The same for the article at rest, scrolled to `scroll` (where the chips land on the way back). */
  const fromRest = useCallback((scroll: number): ChipFrom[] => {
    'worklet';
    const out: ChipFrom[] = [];
    for (let i = 0; i < keys.length; i++) {
      const k = keys[i];
      const mid = k.y + k.height / 2 - scroll;
      out.push({ x: k.x, y: k.y - scroll, k: 1, vis: mid > stage.insetTop && mid < stage.height - LAYOUT.toolbarHeight ? 1 : 0 });
    }
    return out;
  }, [keys, stage]);

  /** The model's answer has arrived: the Distill flight. */
  const arrive = useCallback((token: number, scripted: boolean) => {
    'worklet';
    if (job.get() !== token || modeId.get() !== THINKING) return;
    setModeId(SUMMARY);
    const total = totals[level.get()] ?? 0;
    chipFrom.set(fromPage());
    cancelAnimation(flightD);
    cancelAnimation(chipFade);
    chipFade.set(1);
    flightDir.set(1);
    flightD.set(0);
    extraAlpha.set(1);
    summaryAlpha.set(1);
    flightD.set(withTiming(total, { duration: total, easing: Easing.linear, reduceMotion: never }));
    // The success haptic and the bloom come with the last chip landing, not with the start of the flight.
    const land = layouts[level.get()]?.land ?? BACK_MS;
    // The glow eases down across the flight (an on-screen morph), so it is still lit as the last chip lands.
    think.set(withTiming(0, { duration: reduceMotion ? TIMING.reducedFade : land, easing: EASE_IN_OUT, reduceMotion: never }, (done) => {
      if (!done || job.get() !== token) return;
      buzz('ready', scripted);
      if (!reduceMotion) bloomAt.set(clock.get());
    }));
    rim.set(withTiming(0, { duration: reduceMotion ? 0 : TIMING.rimOut, easing: EASE_OUT, reduceMotion: never }));
    dim.set(withDelay(reduceMotion ? 0 : FLIGHT.veilHold, withTiming(0, { duration: FLIGHT.veilOut, easing: EASE_OUT, reduceMotion: never })));
    if (reduceMotion) {
      lift.set(1);
      pageAlpha.set(withSequence(
        withTiming(0, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }, () => { p.set(1); driveScroll(0, 1, true); }),
        withTiming(1, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }),
      ));
      return;
    }
    // The card shows the top of the article, as in the source: the scroll rewinds with the page (derived from p).
    linkScroll(0, 1);
    p.set(withSpring(1, { ...SPRING.collapse, reduceMotion: never }, (done) => {
      if (done) unlinkScroll();
    }));
    lift.set(withSpring(1, { ...SPRING.collapse, reduceMotion: never }));
  }, [bloomAt, buzz, chipFade, chipFrom, clock, dim, driveScroll, extraAlpha, flightD, flightDir, fromPage, job, layouts, level, lift, linkScroll,
    modeId, p, pageAlpha, reduceMotion, rim, setModeId, summaryAlpha, think, totals, unlinkScroll]);

  /** A committed pinch (or the button): the page holds, readable, while the summary is made. */
  const collapse = useCallback((velocity: number, scripted: boolean, depth: number, track: number) => {
    'worklet';
    const token = job.get() + 1;
    job.set(token);
    setModeId(THINKING);
    scheduleOnRN(lockScroll, true);
    level.set(depth);
    scheduleOnRN(onLevel, depth as Level);
    buzz('commit', scripted);
    stopScroll();
    cancelAnimation(driftX);
    cancelAnimation(driftY);
    driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    // The sentences not locked yet finish their sweep, in order.
    const m = mark.get();
    if (m < MARK.count) {
      locked.set(MARK.count);
      mark.set(reduceMotion ? MARK.count : withTiming(MARK.count, { duration: (MARK.count - m) * MARK.autoLockMs, easing: Easing.linear, reduceMotion: never }));
    }
    markAlpha.set(1);
    cancelAnimation(flightD);
    flightDir.set(1);
    flightD.set(0);
    dim.set(withTiming(MARK.dimMax, { duration: TIMING.thinkIn, easing: EASE_OUT, reduceMotion: never }));
    depthAlpha.set(withTiming(0, { duration: TIMING.labelFade, easing: EASE_OUT, reduceMotion: never }));
    if (!reduceMotion) {
      lift.set(0);
      const hold = Math.min(HOLD.max, Math.max(HOLD.min, track));
      p.set(withSpring(hold, { ...SPRING.hold, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }));
      rim.set(withTiming(RIM.think, { duration: TIMING.thinkIn, easing: EASE_OUT, reduceMotion: never }));
    }
    think.set(withTiming(1, { duration: TIMING.thinkIn, easing: EASE_OUT, reduceMotion: never }));
    cancelAnimation(wait);
    wait.set(0);
    wait.set(withTiming(TIMING.thinkMs, { duration: TIMING.thinkMs, easing: Easing.linear, reduceMotion: never }, (done) => {
      if (done) arrive(token, scripted);
    }));
  }, [arrive, buzz, depthAlpha, dim, driftX, driftY, flightD, flightDir, job, level, lift, lockScroll, locked, mark, markAlpha, onLevel, p,
    reduceMotion, rim, setModeId, stopScroll, think, wait]);

  /** Back to the article: the sentences fly home, the page expands to where the reader was (or to `target`). */
  const restore = useCallback((velocity: number, scripted: boolean, target: number) => {
    'worklet';
    const from = modeId.get();
    job.set(job.get() + 1);
    setModeId(READING);
    buzz('restore', scripted);
    cancelAnimation(wait);
    think.set(withTiming(0, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }));
    depthAlpha.set(0);
    driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    const key = target >= 0 ? keys[target] : undefined;
    const scroll = key ? Math.min(maxScroll, Math.max(0, key.y - stage.height * 0.38)) : saved.get();
    // Only a summary that has fully landed flies home; one still arriving lets its chips fade where they are.
    const d = flightD.get();
    const midFlight = flightDir.get() > 0 && d > 0 && d < (totals[level.get()] ?? 0);
    const flies = from === SUMMARY && !reduceMotion && !midFlight;
    summaryAlpha.set(withTiming(1, { duration: TIMING.summaryBack, easing: EASE_OUT, reduceMotion: never }));
    // Titles, entity chips and the footer are gone before the first chip moves (the lines swap with their chips).
    extraAlpha.set(withTiming(0, { duration: reduceMotion ? TIMING.reducedFade : FLIGHT.swapMs, easing: EASE_OUT, reduceMotion: never }));
    cancelAnimation(flightD);
    if (flies) {
      const homes = fromRest(scroll);
      chipFrom.set(homes);
      // Heading down to sentences below the summary the lowest chip leaves first, heading up the top one (dir −2 / −1).
      const targets = layouts[level.get()]?.targets ?? [];
      let down = 0;
      for (let i = 0; i < homes.length; i++) {
        const t = targets[i];
        if (t) down += homes[i].y - (linesTop + t.y + t.height / 2);
      }
      flightDir.set(down > 0 ? -2 : -1);
      flightD.set(0);
      flightD.set(withTiming(BACK_MS, { duration: BACK_MS, easing: Easing.linear, reduceMotion: never }));
    } else if (midFlight && !reduceMotion) settleChips();
    else {
      flightDir.set(1);
      flightD.set(0);
    }
    dim.set(withTiming(0, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }));
    // The highlights are back with the sentences, stay a moment (the tapped one flashes), then fade.
    const back = flies ? BACK_MS : 0;
    cancelAnimation(mark);
    mark.set(MARK.count);
    locked.set(MARK.count);
    markAlpha.set(1);
    if (target >= 0) {
      flashIdx.set(target);
      flash.set(0);
      flash.set(withDelay(back, withSequence(
        withTiming(1, { duration: MARK.flashMs, easing: EASE_OUT, reduceMotion: never }),
        withTiming(0, { duration: MARK.fadeMs, easing: EASE_OUT, reduceMotion: never }),
      )));
    }
    markAlpha.set(withDelay(back + MARK.fadeDelay, withTiming(0, { duration: MARK.fadeMs, easing: EASE_OUT, reduceMotion: never }, (done) => {
      if (done) { mark.set(0); locked.set(0); markAlpha.set(1); }
    })));
    const settle = () => {
      'worklet';
      scheduleOnRN(lockScroll, false);
    };
    if (reduceMotion) {
      rim.set(0);
      pageAlpha.set(withSequence(
        withTiming(0, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }, () => { p.set(0); lift.set(0); driveScroll(scroll, 1, true); settle(); }),
        withTiming(1, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }),
      ));
      return;
    }
    rim.set(withSequence(
      withTiming(RIM.pinchMax + RIM.armedBoost, { duration: TIMING.rimFlashIn, easing: EASE_OUT, reduceMotion: never }),
      withTiming(0, { duration: TIMING.rimFlashOut, easing: EASE_OUT, reduceMotion: never }),
    ));
    lift.set(withSpring(0, { ...SPRING.restore, reduceMotion: never }));
    // The article scrolls to the reader's place (or the tapped point's sentence) as the page opens: derived from p, so
    // it is already there when the page is full. A pinch out has done this under the fingers (see pinchBegin).
    linkScroll(scroll, 0);
    p.set(withSpring(0, { ...SPRING.restore, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }, (done) => {
      if (!done) return;
      unlinkScroll();
      settle();
    }));
  }, [buzz, chipFrom, depthAlpha, dim, driftX, driftY, driveScroll, extraAlpha, flash, flashIdx, flightD, flightDir, fromRest, job, keys,
    layouts, level, linesTop, lift, linkScroll, lockScroll, locked, mark, markAlpha, maxScroll, modeId, p, pageAlpha, reduceMotion, rim, saved, setModeId,
    settleChips, stage.height, summaryAlpha, think, totals, unlinkScroll, wait]);

  const pinchBegin = useCallback((fx: number, fy: number, scripted: boolean) => {
    'worklet';
    if (!ready) return;
    const out = modeId.get() !== READING;
    cancelAnimation(p);
    cancelAnimation(driftX);
    cancelAnimation(driftY);
    pinch.set({ active: true, out, startP: p.get(), fx, fy, track: p.get(), armed: false, scripted });
    if (out) {
      // A pinch out while the summary is still being made holds the answer until the fingers decide.
      if (modeId.get() === THINKING) cancelAnimation(wait);
      // The article scrolls back to the reader's place as the fingers open the page (and back to the top if they
      // close it again), so the page is already where the reader was when it is full size.
      if (!reduceMotion) linkScroll(saved.get(), 0);
      return;
    }
    stopScroll();
    clearMarks();
    cancelAnimation(dim);
    cancelAnimation(flash);
    flash.set(0);
    // A fresh pinch on the article: remember where the reader was, and how far they had read.
    saved.set(scrollY.get());
    readPct.set(maxScroll > 0 ? Math.round(clamp01(scrollY.get() / maxScroll) * 100) : 0);
    level.set(0);
    levelPos.set(0);
    // Sentences still flying home fade where they are while the page takes them back; otherwise the clock rests.
    if (flightDir.get() < 0 && flightD.get() < BACK_MS && !reduceMotion) settleChips();
    else {
      cancelAnimation(flightD);
      flightDir.set(1);
      flightD.set(0);
    }
    scheduleOnRN(lockScroll, true);
  }, [clearMarks, dim, driftX, driftY, flash, flightD, flightDir, level, levelPos, linkScroll, lockScroll, maxScroll, modeId, p, pinch, readPct,
    ready, reduceMotion, saved, scrollY, settleChips, stopScroll, wait]);

  const pinchMove = useCallback((scale: number, fx: number, fy: number) => {
    'worklet';
    const g = pinch.get();
    if (!g.active) return;
    const raw = g.out ? g.startP - (scale - 1) / (PINCH.fullOut - 1) : g.startP + (1 - scale) / (1 - PINCH.fullIn);
    const track = rubber(raw);
    const armed = g.out ? track < PINCH.restoreAt : track > PINCH.commitAt;
    pinch.set({ ...g, track, armed });
    if (!g.out) {
      // The highlighter, 1:1 with the fingers: sentence i sweeps over its fifth of the way and locks at its end.
      const progress = clamp01(track / MARK.span);
      const m = Math.min(MARK.count, progress / MARK.step);
      const was = locked.get();
      const now = Math.floor(m + 1e-4);
      if (now > was) {
        locked.set(now);
        for (let i = was; i < now; i++) popAt(i);
        buzz('lock', g.scripted);
      }
      mark.set(reduceMotion ? Math.max(now, was) : Math.max(m, was));
      dim.set(progress * MARK.dimMax);
      // Summary depth follows the pinch.
      const depth = depthFor(track, level.get());
      if (depth !== level.get()) {
        level.set(depth);
        levelPos.set(reduceMotion ? depth : withSpring(depth, { ...DEPTH.lens, reduceMotion: never }));
        buzz('level', g.scripted);
      }
      depthAlpha.set(span(track, DEPTH.showFrom, DEPTH.showFrom + DEPTH.showSpan));
    }
    // The summary dims under the pull (Reduce Motion too: opacity only); it stays readable until the release decides.
    if (g.out) summaryAlpha.set(mix(1, 0.5, span(track, 1, PINCH.restoreAt)));
    if (reduceMotion) return;
    p.set(track);
    driftX.set((fx - g.fx) * PINCH.follow);
    driftY.set((fy - g.fy) * PINCH.follow);
    if (g.out) {
      rim.set(armed ? RIM.pinchMax + RIM.armedBoost : 0);
    } else {
      rim.set(clamp01(track / PINCH.commitAt) * RIM.pinchMax + (armed ? RIM.armedBoost : 0));
    }
  }, [buzz, depthAlpha, dim, driftX, driftY, level, levelPos, locked, mark, p, pinch, popAt, reduceMotion, rim, summaryAlpha]);

  const pinchEnd = useCallback((velocity: number, cancelled: boolean) => {
    'worklet';
    const g = pinch.get();
    if (!g.active) return;
    pinch.set({ ...g, active: false });
    const track = g.track;
    if (!g.out) {
      // Velocity or distance: a short, fast pinch commits; a slow one that never got far does not.
      const commit = !cancelled && track + project(-velocity / (1 - PINCH.fullIn)) > PINCH.commitAt;
      if (commit) {
        collapse(velocity, g.scripted, level.get(), track);
        return;
      }
      scheduleOnRN(lockScroll, false);
      rim.set(withTiming(0, { duration: TIMING.rimLetGo, easing: EASE_OUT, reduceMotion: never }));
      p.set(withSpring(0, { ...SPRING.restore, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }));
      driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
      driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
      dim.set(withTiming(0, { duration: MARK.cancelMs, easing: EASE_OUT, reduceMotion: never }));
      depthAlpha.set(withTiming(0, { duration: TIMING.labelFade, easing: EASE_OUT, reduceMotion: never }));
      markAlpha.set(withTiming(0, { duration: MARK.cancelMs, easing: EASE_OUT, reduceMotion: never }, (done) => {
        if (done) { mark.set(0); locked.set(0); markAlpha.set(1); }
      }));
      return;
    }
    const back = !cancelled && track + project(-velocity / (PINCH.fullOut - 1)) < PINCH.restoreAt;
    if (back) restore(velocity, g.scripted, -1);
    else {
      rim.set(withTiming(0, { duration: TIMING.rimLetGo, easing: EASE_OUT, reduceMotion: never }));
      summaryAlpha.set(withTiming(1, { duration: TIMING.reducedFade, easing: EASE_OUT, reduceMotion: never }));
      const waiting = modeId.get() === THINKING;
      // Back where it was: the card, or the waiting page (whose answer then arrives after the rest of the wait).
      const home = waiting ? Math.min(HOLD.max, Math.max(HOLD.min, g.startP)) : 1;
      p.set(withSpring(home, { ...SPRING.collapse, velocity: -velocity * PINCH.velocityToProgress, reduceMotion: never }));
      if (waiting) {
        const token = job.get();
        const left = Math.max(TIMING.tick, TIMING.thinkMs - wait.get());
        wait.set(withTiming(TIMING.thinkMs, { duration: left, easing: Easing.linear, reduceMotion: never }, (done) => {
          if (done) arrive(token, g.scripted);
        }));
      }
      driftX.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
      driftY.set(withSpring(0, { ...SPRING.drift, reduceMotion: never }));
    }
  }, [arrive, collapse, depthAlpha, dim, driftX, driftY, job, level, lockScroll, locked, mark, markAlpha, modeId, p, pinch, restore, rim,
    summaryAlpha, wait]);

  /** A real touch: stop the script now, cancelling its fingers and its scroll if they are running. */
  const touch = useCallback((down: boolean) => {
    'worklet';
    const s = script.get();
    if (down && !s.paused) {
      if (pinch.get().active && pinch.get().scripted) pinchEnd(0, true);
      if (modeId.get() === READING) stopScroll();
    }
    if (down) fingerAlpha.set(withTiming(0, { duration: TIMING.fingerFade, easing: EASE_OUT, reduceMotion: never }));
    script.set({ ...s, paused: true, realDown: down, quiet: 0, segment: 0 });
  }, [fingerAlpha, modeId, pinch, pinchEnd, script, stopScroll]);

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

  const readTarget = keys.length > 0 ? Math.min(maxScroll, Math.max(0, keys[0].y - stage.insetTop - LAYOUT.readTopGap)) : 0;

  const runScript = useCallback((dt: number) => {
    'worklet';
    const s = script.get();
    const t = s.time;
    const A = AUTOPLAY;
    const segment = t >= A.pinchInStart && t < A.pinchInEnd ? 1
      : t >= A.pinchOutStart && t < A.pinchOutEnd ? 2
        : t >= A.readStart && t < A.readStart + A.readMs ? 3
          : t >= A.topStart && t < A.topStart + A.topMs ? 4 : 0;
    const cx = stage.width / 2;
    const cyIn = stage.height * 0.46;
    const cyOut = LAYOUT.summaryTop + LAYOUT.cardHeight / 2;
    if (segment !== s.segment) {
      if (s.segment === 1 || s.segment === 2) {
        // Land the path exactly on its end before lifting, so every loop releases from the same pose.
        if (s.segment === 1) { placeFingers(cx, cyIn, A.spreadIn * A.pinchInScale); pinchMove(A.pinchInScale, cx, cyIn); }
        else { placeFingers(cx, cyOut, A.spreadOut * A.pinchOutScale); pinchMove(A.pinchOutScale, cx, cyOut); }
        pinchEnd(0, false);
        fingerAlpha.set(withTiming(0, { duration: TIMING.fingerFade, easing: EASE_OUT, reduceMotion: never }));
      }
      if (segment === 1 || segment === 2) {
        const cy = segment === 1 ? cyIn : cyOut;
        placeFingers(cx, cy, segment === 1 ? A.spreadIn : A.spreadOut);
        fingerAlpha.set(withTiming(1, { duration: TIMING.fingerFade, easing: EASE_OUT, reduceMotion: never }));
        pinchBegin(cx, cy, true);
      } else if (segment === 3 && modeId.get() === READING) driveScroll(readTarget, A.readMs, false);
      else if (segment === 4 && modeId.get() === READING) driveScroll(0, A.topMs, false);
    }
    if (segment === 1) {
      const scale = mix(1, A.pinchInScale, ease((t - A.pinchInStart) / (A.pinchInEnd - A.pinchInStart)));
      placeFingers(cx, cyIn, A.spreadIn * scale);
      pinchMove(scale, cx, cyIn);
    } else if (segment === 2) {
      const scale = mix(1, A.pinchOutScale, ease((t - A.pinchOutStart) / (A.pinchOutEnd - A.pinchOutStart)));
      placeFingers(cx, cyOut, A.spreadOut * scale);
      pinchMove(scale, cx, cyOut);
    }
    let next = t + dt;
    if (QA_HOLD === 'rest') next = 0;
    if (next >= LOOP_MS) {
      next -= LOOP_MS;
      // The field is hidden at rest; restarting its clock makes every loop of the preview identical.
      clock.set(0);
    }
    script.set({ ...script.get(), time: next, segment });
  }, [clock, driveScroll, fingerAlpha, modeId, pinchBegin, pinchEnd, pinchMove, placeFingers, readTarget, script, stage.height, stage.width]);

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
      const settled = !pinch.get().active && id !== THINKING && (id === SUMMARY || Math.abs(p.get()) < 0.002) && !scrolling.get();
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

  /** The "+" button and the accessibility action: summarize at the default depth. */
  const summarize = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (modeId.get() !== READING || !ready) return;
      saved.set(scrollY.get());
      readPct.set(maxScroll > 0 ? Math.round(clamp01(scrollY.get() / maxScroll) * 100) : 0);
      clearMarks();
      collapse(0, false, 0, HOLD.min);
    });
  }, [clearMarks, collapse, maxScroll, modeId, readPct, ready, saved, scrollY, touch]);

  /** Close, pinch out, or tap a point (`source`: its sentence, which the article then shows and flashes). */
  const showArticle = useCallback((source = -1) => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (modeId.get() !== READING) restore(0, false, source);
    });
  }, [modeId, restore, touch]);

  const onTouch = useCallback((down: boolean, count: number) => {
    fingers.set(count);
    scheduleOnUI(touch, down);
  }, [fingers, touch]);

  const pops = useMemo(() => [pop0, pop1, pop2, pop3], [pop0, pop1, pop2, pop3]);
  const values = useMemo<SummaryValues>(() => ({
    p, lift, driftX, driftY, think, rim, summaryAlpha, extraAlpha, pageAlpha, clock, breath, bloomAt, fingerAX, fingerAY, fingerBX, fingerBY,
    fingerAlpha, mark, markAlpha, pops, dim, wait, level, levelPos, depthAlpha, flightD, flightDir, chipFrom, chipFade, fingers, scrollY, scrollPos,
    scrolling, scrollLink, readPct, flash, flashIdx,
  }), [p, lift, driftX, driftY, think, rim, summaryAlpha, extraAlpha, pageAlpha, clock, breath, bloomAt, fingerAX, fingerAY, fingerBX, fingerBY,
    fingerAlpha, mark, markAlpha, pops, dim, wait, level, levelPos, depthAlpha, flightD, flightDir, chipFrom, chipFade, fingers, scrollY, scrollPos,
    scrolling, scrollLink, readPct, flash, flashIdx]);

  return { values, gesture, mode, summarize, showArticle, onTouch };
}

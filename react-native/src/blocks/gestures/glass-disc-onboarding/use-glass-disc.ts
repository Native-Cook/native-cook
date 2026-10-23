/**
 * The onboarding on the UI thread. A finger and the autoplay's scripted finger call the same
 * touchBegin / touchMove / touchEnd, so the preview shows exactly what a hand does.
 */
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { AccessibilityInfo } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import {
  Easing, ReduceMotion, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming, type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { AUTOPLAY, BACK, BURST, DEBUG_FREEZE_MS, EASE_OUT, FADE, LOOP_MS, PULL, RISE, SPRING, UPGRADE } from './motion';
import { HIDDEN, initialStickers, launch, leave, stepStickers } from './sticker-sim';
import { COPY, LAYOUT } from './theme';

const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);

const INTRO = 0;
const PULLING = 1;
const LANDING = 2;
const COMMUNITY = 3;
const BACKDRAG = 4;
const REWINDING = 5;
const never = ReduceMotion.Never;

export type Stage = 'intro' | 'community' | 'moving';

export type DiscValues = {
  discX: SharedValue<number>;
  discY: SharedValue<number>;
  discR: SharedValue<number>;
  ring: SharedValue<number>;
  reveal: SharedValue<number>;
  // 0: the lens shows the lone headline (rising); 1: it shows the second screen in place (bead, rewind).
  texMode: SharedValue<number>;
  introAlpha: SharedValue<number>;
  hintAlpha: SharedValue<number>;
  headIn: SharedValue<number>;
  restIn: SharedValue<number>;
  // Upgrade: touch-down lift, squash/stretch, rim glint, dispersion, bead pulse, and the clocks they run on.
  nudge: SharedValue<number>;
  stretch: SharedValue<number>;
  glint: SharedValue<number>;
  glintAng: SharedValue<number>;
  spreadBoost: SharedValue<number>;
  squash: SharedValue<number>;
  clock: SharedValue<number>;
  landAt: SharedValue<number>;
};

type Options = { width: number; height: number; ready: boolean; autoPlay: boolean; reduceMotion: boolean };

function smooth(u: number): number {
  'worklet';
  const t = Math.min(1, Math.max(0, u));
  return t * t * (3 - 2 * t);
}

/** Catmull-Rom through keyframes (ts ascending), clamped at the ends. */
function curve(ts: readonly number[], vs: readonly number[], t: number): number {
  'worklet';
  const n = ts.length;
  if (t <= ts[0]) return vs[0];
  if (t >= ts[n - 1]) return vs[n - 1];
  let i = 0;
  while (i < n - 2 && t > ts[i + 1]) i += 1;
  const u = (t - ts[i]) / (ts[i + 1] - ts[i]);
  const p0 = vs[Math.max(0, i - 1)];
  const p1 = vs[i];
  const p2 = vs[i + 1];
  const p3 = vs[Math.min(n - 1, i + 2)];
  return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
}

function buzz(kind: 'tick' | 'commit' | 'land' | 'back') {
  if (kind === 'tick') void Haptics.selectionAsync().catch(() => {});
  else if (kind === 'commit') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function useGlassDisc({ width, height, ready, autoPlay, reduceMotion }: Options) {
  const R0 = width * LAYOUT.domeRadius;
  const Y0 = height * LAYOUT.domeTop + R0;
  const beadY = height * LAYOUT.beadY;
  const restY = height * LAYOUT.beadRestY;
  const restR = LAYOUT.beadRestRadius;
  const beadR = LAYOUT.beadRadius;
  const backR = width * LAYOUT.backRadius;
  const [stage, setStage] = useState<Stage>('intro');
  const discX = useSharedValue(width / 2);
  const discY = useSharedValue(Y0);
  const discR = useSharedValue(R0);
  const ring = useSharedValue(0);
  const reveal = useSharedValue(0);
  const texMode = useSharedValue(0);
  const introAlpha = useSharedValue(1);
  const hintAlpha = useSharedValue(1);
  const headIn = useSharedValue(0);
  const restIn = useSharedValue(0);
  const landed = useSharedValue(0);
  const nudge = useSharedValue(0);
  const stretch = useSharedValue(0);
  const glint = useSharedValue(0);
  const glintAng = useSharedValue(0);
  const glintAt = useSharedValue(-1e9);
  const spreadBoost = useSharedValue(0);
  const squash = useSharedValue(0);
  const clock = useSharedValue(0);
  const landAt = useSharedValue(-1e9);
  const lastY = useSharedValue(Y0);
  const fingerAt = useSharedValue({ x: 0, y: 0, t: 0 });
  const phase = useSharedValue(INTRO);
  const drag = useSharedValue({ y0: 0, pull: 0, ticks: 0, scripted: false });
  const stickers = useSharedValue(initialStickers(width, height));
  const finger = useSharedValue({ x: 0, y: 0, alpha: 0 });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, segment: 0 });

  const report = useCallback((next: Stage) => {
    setStage(next);
    if (next === 'community') AccessibilityInfo.announceForAccessibility(`${COPY.headline} ${COPY.sub.join(' ')}`);
  }, []);
  const haptic = useCallback((kind: 'tick' | 'commit' | 'land' | 'back', scripted: boolean) => {
    'worklet';
    if (!scripted) scheduleOnRN(buzz, kind);
  }, []);

  /** The disc for a pull of p points: the source's path through frames 1–24, rubber past the threshold. */
  const place = useCallback((p: number) => {
    'worklet';
    const t = Math.min(1, p / PULL.threshold);
    const over = Math.max(0, p - PULL.threshold);
    const extra = (PULL.rubber * over) / (over + PULL.rubber);
    discX.set(width * curve(LAYOUT.pullT, LAYOUT.pullX, t));
    discR.set(width * curve(LAYOUT.pullT, LAYOUT.pullR, t));
    discY.set(height * curve(LAYOUT.pullT, LAYOUT.pullY, t) - extra);
    ring.set(t < PULL.ringPeakAt ? smooth(t / PULL.ringPeakAt) : 1 - smooth((t - PULL.ringPeakAt) / (PULL.ringOutAt - PULL.ringPeakAt)));
    reveal.set(smooth((t - PULL.revealFrom) / (PULL.revealTo - PULL.revealFrom)));
    introAlpha.set(1 - smooth(t / PULL.introOutBy));
    hintAlpha.set(1 - smooth(t / PULL.hintOutBy));
  }, [discR, discX, discY, height, hintAlpha, introAlpha, reveal, ring, width]);

  /** Back drag: the bead follows the finger down and grows into a lens over the text. */
  const placeBack = useCallback((p: number) => {
    'worklet';
    discY.set(restY + p);
    discR.set(restR + (backR - restR) * smooth(p / BACK.growBy));
  }, [backR, discR, discY, restR, restY]);

  const toIntro = useCallback(() => {
    'worklet';
    phase.set(INTRO);
    texMode.set(0);
    scheduleOnRN(report, 'intro');
  }, [phase, report, texMode]);

  const toCommunity = useCallback(() => {
    'worklet';
    phase.set(COMMUNITY);
    scheduleOnRN(report, 'community');
  }, [phase, report]);

  /** The bead has landed: the second screen comes in and the bubbles leave the bead. */
  const land = useCallback(() => {
    'worklet';
    landAt.set(clock.get());
    texMode.set(1);
    reveal.set(1);
    const ms = reduceMotion ? FADE.reducedMs : FADE.headlineMs;
    headIn.set(withTiming(1, { duration: ms, easing: Easing.out(Easing.cubic), reduceMotion: never }));
    restIn.set(withDelay(reduceMotion ? 0 : FADE.restDelay, withTiming(1, {
      duration: reduceMotion ? FADE.reducedMs : FADE.restMs, easing: Easing.out(Easing.quad), reduceMotion: never,
    }, (done) => { if (done) toCommunity(); })));
    stickers.modify((s) => {
      'worklet';
      launch(s, width / 2, beadY, reduceMotion);
      return s;
    });
    const rise = { duration: reduceMotion ? 0 : RISE.ms, easing: Easing.inOut(Easing.cubic), reduceMotion: never };
    // Finish landing, then rise and tighten while the bubbles leave.
    const settle = { ...SPRING.bead, reduceMotion: never };
    discY.set(withSequence(withSpring(beadY, settle), withTiming(restY, rise)));
    discR.set(withSequence(withSpring(LAYOUT.beadRadius, settle), withTiming(restR, rise)));
  }, [beadY, clock, discR, discY, headIn, landAt, reduceMotion, restIn, restR, restY, reveal, stickers, texMode, toCommunity, width]);

  const commit = useCallback((scripted: boolean, vy = 0) => {
    'worklet';
    phase.set(LANDING);
    scheduleOnRN(report, 'moving');
    haptic('commit', scripted);
    nudge.set(withSpring(0, { ...UPGRADE.nudgeSpring, reduceMotion: never }));
    glintAt.set(clock.get());
    ring.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
    introAlpha.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
    hintAlpha.set(0);
    if (reduceMotion) {
      reveal.set(0);
      discX.set(width / 2);
      discY.set(beadY);
      discR.set(beadR);
      land();
      return;
    }
    reveal.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
    landed.set(0);
    discX.set(withSpring(width / 2, { ...SPRING.bead, reduceMotion: never }));
    discR.set(withSpring(beadR, { ...SPRING.bead, reduceMotion: never }));
    discY.set(withSpring(beadY, { ...SPRING.bead, velocity: vy, reduceMotion: never }));
    // Land when the bead is close enough to read as arrived, not when its spring fully rests.
    landed.set(withDelay(FADE.settle + 160, withTiming(1, { duration: 1, reduceMotion: never }, (ok) => {
      if (!ok) return;
      haptic('land', scripted);
      land();
    })));
  }, [beadR, beadY, clock, discR, discX, discY, glintAt, haptic, hintAlpha, introAlpha, land, landed, nudge, phase, reduceMotion, report, reveal, ring, width]);

  const rewind = useCallback((scripted: boolean, vy: number) => {
    'worklet';
    phase.set(REWINDING);
    scheduleOnRN(report, 'moving');
    haptic('back', scripted);
    headIn.set(withTiming(0, { duration: FADE.outMs, easing: easeOut, reduceMotion: never }));
    restIn.set(withTiming(0, { duration: FADE.outMs, easing: easeOut, reduceMotion: never }));
    reveal.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
    introAlpha.set(withDelay(FADE.outMs, withTiming(1, { duration: FADE.introBack, easing: easeOut, reduceMotion: never })));
    hintAlpha.set(withDelay(FADE.outMs, withTiming(1, { duration: FADE.introBack, easing: easeOut, reduceMotion: never })));
    stickers.modify((s) => {
      'worklet';
      leave(s);
      return s;
    });
    if (reduceMotion) {
      discY.set(Y0);
      discR.set(R0);
      toIntro();
      return;
    }
    discR.set(withSpring(R0, { ...SPRING.dome, reduceMotion: never }));
    discY.set(withSpring(Y0, { ...SPRING.dome, velocity: vy, reduceMotion: never }, (done) => { if (done) toIntro(); }));
  }, [R0, Y0, discR, discY, haptic, headIn, hintAlpha, introAlpha, phase, reduceMotion, report, restIn, reveal, stickers, toIntro]);

  const touchBegin = useCallback((x: number, y: number, scripted: boolean) => {
    'worklet';
    if (!ready) return;
    const ph = phase.get();
    if (ph === COMMUNITY) {
      if (Math.hypot(x - width / 2, y - restY) < restR + 22) {
        phase.set(BACKDRAG);
        drag.set({ y0: y, pull: 0, ticks: 0, scripted });
        return;
      }
      fingerAt.set({ x, y, t: clock.get() });
      stickers.modify((s) => {
        'worklet';
        s.finger = true; s.fx = x; s.fy = y; s.fvx = 0; s.fvy = 0;
        return s;
      });
      return;
    }
    if (ph !== INTRO) return;
    // Only the dome takes the pull.
    if (y < discY.get() - discR.get() - 24) return;
    phase.set(PULLING);
    texMode.set(0);
    drag.set({ y0: y, pull: 0, ticks: 0, scripted });
    // Anticipation: the dome answers the touch within a frame.
    if (!reduceMotion) nudge.set(withSpring(UPGRADE.nudge, { ...UPGRADE.nudgeSpring, reduceMotion: never }));
  }, [clock, discR, discY, drag, fingerAt, nudge, phase, ready, reduceMotion, restR, restY, stickers, texMode, width]);

  const touchMove = useCallback((x: number, y: number) => {
    'worklet';
    const ph = phase.get();
    if (ph === COMMUNITY) {
      const f = fingerAt.get();
      const dtf = Math.max(8, clock.get() - f.t) / 1000;
      fingerAt.set({ x, y, t: clock.get() });
      stickers.modify((s) => {
        'worklet';
        s.fvx = (x - f.x) / dtf; s.fvy = (y - f.y) / dtf;
        s.fx = x; s.fy = y;
        return s;
      });
      return;
    }
    const g = drag.get();
    if (ph === PULLING) {
      const p = Math.max(0, g.y0 - y);
      const ticks = Math.floor(Math.min(1, p / PULL.threshold) / PULL.tickEvery);
      if (ticks > g.ticks) haptic('tick', g.scripted);
      drag.set({ ...g, pull: p, ticks: Math.max(ticks, g.ticks) });
      place(p);
    } else if (ph === BACKDRAG) {
      const p = Math.max(0, y - g.y0);
      drag.set({ ...g, pull: p });
      placeBack(p);
    }
  }, [clock, drag, fingerAt, haptic, phase, place, placeBack, stickers]);

  const touchEnd = useCallback((vy: number, cancelled: boolean) => {
    'worklet';
    const ph = phase.get();
    const g = drag.get();
    if (ph === COMMUNITY) {
      stickers.modify((s) => {
        'worklet';
        s.finger = false;
        return s;
      });
      return;
    }
    if (ph === PULLING) {
      const t = g.pull / PULL.threshold;
      if (!cancelled && (t >= PULL.commitAt || (vy < PULL.flickVelocity && t > PULL.flickMin))) {
        commit(g.scripted, vy);
        return;
      }
      phase.set(INTRO);
      nudge.set(withSpring(0, { ...UPGRADE.nudgeSpring, velocity: -vy, reduceMotion: never }));
      const spring = { ...SPRING.back, reduceMotion: never };
      discX.set(withSpring(width / 2, spring));
      discY.set(withSpring(Y0, { ...spring, velocity: vy }));
      discR.set(withSpring(R0, spring));
      ring.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
      reveal.set(withTiming(0, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
      introAlpha.set(withTiming(1, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
      hintAlpha.set(withTiming(1, { duration: FADE.settle, easing: easeOut, reduceMotion: never }));
      return;
    }
    if (ph === BACKDRAG) {
      if (!cancelled && (g.pull >= BACK.commitBy || (vy > BACK.flickVelocity && g.pull > BACK.flickMin))) {
        rewind(g.scripted, vy);
        return;
      }
      phase.set(COMMUNITY);
      const spring = { ...SPRING.bead, reduceMotion: never };
      discY.set(withSpring(restY, { ...spring, velocity: vy }));
      discR.set(withSpring(restR, spring));
    }
  }, [R0, Y0, commit, discR, discX, discY, drag, hintAlpha, introAlpha, nudge, phase, restR, restY, reveal, rewind, ring, stickers, width]);

  /** A real touch stops the script at once; the script resumes from rest 3.5 s after the last touch. */
  const touch = useCallback((down: boolean) => {
    'worklet';
    const s = script.get();
    if (down && !s.paused && s.segment !== 0) touchEnd(0, true);
    if (down) finger.set({ ...finger.get(), alpha: 0 });
    script.set({ ...s, paused: true, realDown: down, quiet: 0, segment: 0 });
  }, [finger, script, touchEnd]);

  const runScript = useCallback((dt: number) => {
    'worklet';
    const s = script.get();
    const t = s.time;
    const A = AUTOPLAY;
    const segment = t >= A.pullStart && t < A.pullEnd ? 1 : t >= A.sweepStart && t < A.sweepEnd ? 2 : t >= A.backStart && t < A.backEnd ? 3 : 0;
    const pathAt = (seg: number, time: number) => {
      'worklet';
      if (seg === 1) {
        const u = (time - A.pullStart) / (A.pullEnd - A.pullStart);
        return { x: width * A.startX, y: height * A.startY - A.pullBy * smooth(u) };
      }
      if (seg === 2) {
        const u = smooth((time - A.sweepStart) / (A.sweepEnd - A.sweepStart));
        return { x: width * (0.1 + 0.8 * u), y: height * (0.2 - 0.06 * Math.sin(u * Math.PI)) };
      }
      const u = (time - A.backStart) / (A.backEnd - A.backStart);
      return { x: width / 2 + 6, y: restY + A.backBy * smooth(u) };
    };
    if (segment !== s.segment) {
      if (s.segment !== 0) {
        const end = pathAt(s.segment, s.segment === 1 ? A.pullEnd : s.segment === 2 ? A.sweepEnd : A.backEnd);
        touchMove(end.x, end.y);
        touchEnd(0, false);
      }
      if (segment === 0) finger.set({ ...finger.get(), alpha: 0 });
      else {
        const at = pathAt(segment, t);
        finger.set({ x: at.x, y: at.y, alpha: 1 });
        touchBegin(at.x, at.y, true);
      }
    }
    if (segment !== 0) {
      const at = pathAt(segment, t);
      finger.set({ x: at.x, y: at.y, alpha: 1 });
      touchMove(at.x, at.y);
    }
    let next = DEBUG_FREEZE_MS >= 0 && t >= DEBUG_FREEZE_MS ? t : t + dt;
    if (next >= LOOP_MS) next -= LOOP_MS;
    script.set({ ...s, time: next, segment });
  }, [finger, height, restY, script, touchBegin, touchEnd, touchMove, width]);

  useFrameCallback((frame) => {
    'worklet';
    if (!ready) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 0, 250);
    const now = clock.get() + dt;
    clock.set(now);
    // Squash/stretch and dispersion from the disc's vertical speed.
    const y = discY.get();
    const v = dt > 0 ? ((y - lastY.get()) / dt) * 1000 : 0;
    lastY.set(y);
    const st = reduceMotion ? 0 : Math.min(UPGRADE.stretchMax, Math.abs(v) * UPGRADE.stretchPerSpeed);
    stretch.set(stretch.get() + (st - stretch.get()) * UPGRADE.stretchFollow);
    spreadBoost.set(reduceMotion ? 0 : Math.min(UPGRADE.dispersionMax, Math.abs(v) * UPGRADE.dispersionPerSpeed));
    // The rim glint: every few seconds on the resting dome, and once on commit.
    const ph = phase.get();
    let g = 0;
    let gt = now - glintAt.get();
    if ((ph === INTRO || ph === COMMUNITY) && gt > UPGRADE.glintMs) gt = now % UPGRADE.glintEveryMs;
    if (!reduceMotion && gt >= 0 && gt < UPGRADE.glintMs && (ph === INTRO || ph === LANDING || ph === COMMUNITY)) {
      const u = gt / UPGRADE.glintMs;
      g = Math.sin(Math.PI * u);
      glintAng.set(UPGRADE.glintFrom + (UPGRADE.glintTo - UPGRADE.glintFrom) * (1 - Math.pow(1 - u, 3)));
    }
    glint.set(g);
    // The bead tightens with each pressure ring of the burst.
    let sq = 0;
    if (!reduceMotion) {
      for (let i = 0; i < UPGRADE.pulses.length; i++) {
        const u = (now - landAt.get() - BURST.delayMs - UPGRADE.pulses[i]) / UPGRADE.pulseMs;
        if (u > 0 && u < 1) sq += Math.sin(Math.PI * Math.min(1, u * 3)) * (1 - u);
      }
    }
    squash.set(Math.min(1, sq) * UPGRADE.pulseSquash);
    stickers.modify((s) => {
      'worklet';
      stepStickers(s, dt, height, reduceMotion);
      return s;
    });
    if (!autoPlay) return;
    const s = script.get();
    if (s.paused) {
      const quiet = s.realDown ? 0 : s.quiet + dt;
      if (quiet >= AUTOPLAY.resumeDelayMs && phase.get() === INTRO && stickers.get().phase === HIDDEN) {
        script.set({ time: 0, paused: false, realDown: false, quiet: 0, segment: 0 });
      } else script.set({ ...s, quiet });
      return;
    }
    runScript(dt);
  });

  const gesture = useMemo(() => Gesture.Pan()
    .enabled(ready)
    .minDistance(0)
    .maxPointers(1)
    .onTouchesDown(() => { touch(true); })
    .onBegin((e) => { touchBegin(e.x, e.y, false); })
    .onUpdate((e) => { touchMove(e.x, e.y); })
    .onEnd((e) => { touchEnd(e.velocityY, false); })
    .onFinalize((_e, success) => {
      if (!success) touchEnd(0, true);
      touch(false);
    }), [ready, touch, touchBegin, touchEnd, touchMove]);

  const enter = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (phase.get() === INTRO) commit(false);
    });
  }, [commit, phase, touch]);
  const startOver = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      touch(false);
      if (phase.get() === COMMUNITY) rewind(false, 0);
    });
  }, [phase, rewind, touch]);

  const values = useMemo<DiscValues>(() => ({
    discX, discY, discR, ring, reveal, texMode, introAlpha, hintAlpha, headIn, restIn, nudge, stretch, glint, glintAng, spreadBoost, squash, clock, landAt,
  }),
    [discX, discY, discR, ring, reveal, texMode, introAlpha, hintAlpha, headIn, restIn, nudge, stretch, glint, glintAng, spreadBoost, squash, clock, landAt]);
  return { values, stickers, finger, gesture, stage, enter, startOver };
}

/**
 * The espresso lever's mechanism. A finger and the autoplay script call the same worklets:
 * pullBegin → pullMove → pullEnd.
 *
 * rest → pumping: each downstroke past −16° draws a charge into the group: +3 bar on the gauge, a puff of steam
 * from the vent, a tick. Let go between strokes and the spring takes the lever back up; the pressure stays.
 * pumping → full: the third stroke reaches 9 bar and the brew zone lights. Hold, then let go:
 * full → brewing: the spring pushes the lever back up slowly while the shot pours (two streams, the cup fills and
 * its crema forms, the needle falls, the timer counts to 27 s).
 * brewing → done: the warm pilot lamp comes on and the steam rises.
 * done → pumping: a new pull swaps the cup (the full one slides out, an empty one slides in) and starts again.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Gesture } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  Easing,
  ReduceMotion,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { playLeverHaptic, type LeverHaptic } from './lever-haptics';
import { BREW, DEMO, LOOP_MS, PUMP, RESUME_MS, SPRING, TIMING } from './motion';
import { GEO } from './theme';

export const STAGE = { rest: 0, pumping: 1, full: 2, brewing: 3, done: 4 } as const;
const NEVER = { reduceMotion: ReduceMotion.Never };
const LOOP_S = LOOP_MS / 1000;
const LINEAR = Easing.linear;
const LEVER_LENGTH = GEO.lever.handle.to;

export type Readout = { stage: number; bar: number; seconds: number };
const START: Readout = { stage: STAGE.rest, bar: 0, seconds: 0 };

type Segment = { down: number; moveEnd: number; up: number };

function ease(u: number): number {
  'worklet';
  const k = Math.min(Math.max(u, 0), 1);
  return (1 - Math.cos(Math.PI * k)) / 2;
}

/** Past a stop the lever gives less and less: at most `max` degrees, half of it after `overSoftness` pt. */
function rubber(raw: number): number {
  'worklet';
  const soft = PUMP.overSoftness * PUMP.degPerPt;
  if (raw > PUMP.bottom) {
    const over = raw - PUMP.bottom;
    return PUMP.bottom + (PUMP.overDown * over) / (over + soft);
  }
  if (raw < PUMP.rest) {
    const over = PUMP.rest - raw;
    return PUMP.rest - (PUMP.overUp * over) / (over + soft);
  }
  return raw;
}

function wrap180(a: number): number {
  'worklet';
  return ((((a + 180) % 360) + 360) % 360) - 180;
}

/** Where the lever's pivot is on screen, and the machine's scale: a finger on the handle turns it about there. */
type Pivot = { x: number; y: number; scale: number };
type Options = { autoPlay: boolean; reduceMotion: boolean; pivot: Pivot };

export function usePullLever({ autoPlay, reduceMotion, pivot }: Options) {
  const [readout, setReadout] = useState<Readout>(START);

  // Drawn values.
  const angle = useSharedValue<number>(PUMP.rest);
  const bar = useSharedValue(0); // what the needle reads
  const zone = useSharedValue(0); // 0–1, the brew zone lit
  const brew = useSharedValue(0); // 0–1 through the pour
  const ready = useSharedValue(0); // 0–1, the pilot lamp
  const cupX = useSharedValue(0);
  const cupA = useSharedValue(1);
  const shot = useSharedValue(0); // the timer, in seconds of the shot
  const clock = useSharedValue(0);
  const puffAt = useSharedValue(-99);

  // Mechanism.
  const phase = useSharedValue<number>(STAGE.rest);
  const strokes = useSharedValue(0);
  const rearmed = useSharedValue(true);
  const held = useSharedValue(false);
  const grabAngle = useSharedValue(0);
  const grabAtan = useSharedValue(0);
  const byAngle = useSharedValue(false);
  const lastSecond = useSharedValue(-1);
  const px = useSharedValue(pivot.x);
  const py = useSharedValue(pivot.y);
  const k = useSharedValue(pivot.scale);
  useEffect(() => {
    px.set(pivot.x);
    py.set(pivot.y);
    k.set(pivot.scale);
  }, [k, pivot.scale, pivot.x, pivot.y, px, py]);

  // Autoplay.
  const demoT = useSharedValue(0);
  const demoPaused = useSharedValue(!autoPlay);
  const realDown = useSharedValue(false);
  const quiet = useSharedValue(0);
  const scriptDown = useSharedValue(false);

  const announce = useCallback((stage: number, pressure: number, seconds: number) => {
    setReadout({ stage, bar: pressure, seconds });
  }, []);

  const haptic = useCallback((kind: LeverHaptic, silent: boolean) => {
    'worklet';
    if (!silent) scheduleOnRN(playLeverHaptic, kind);
  }, []);

  const fade = useCallback((target: number, duration: number) => {
    'worklet';
    return withTiming(target, { duration: reduceMotion ? TIMING.reduced : duration, easing: TIMING.easeOut, ...NEVER });
  }, [reduceMotion]);

  /** One stroke counted: the piston drew a charge, the needle steps up, the vent hisses. */
  const stroke = useCallback((silent: boolean) => {
    'worklet';
    const n = Math.min(strokes.get() + 1, PUMP.strokes);
    strokes.set(n);
    const p = n * PUMP.barPerStroke;
    bar.set(reduceMotion ? withTiming(p, { duration: TIMING.reduced, ...NEVER }) : withSpring(p, SPRING.needle));
    puffAt.set(clock.get());
    if (n >= PUMP.strokes) {
      phase.set(STAGE.full);
      zone.set(fade(1, TIMING.zone));
      haptic('full', silent);
      scheduleOnRN(announce, STAGE.full, p, 0);
    } else {
      phase.set(STAGE.pumping);
      haptic('tick', silent);
      scheduleOnRN(announce, STAGE.pumping, p, 0);
    }
  }, [announce, bar, clock, fade, haptic, phase, puffAt, reduceMotion, strokes, zone]);

  /** Let go at 9 bar: the spring takes the lever up against the puck and the shot pours. */
  const brewStart = useCallback(() => {
    'worklet';
    phase.set(STAGE.brewing);
    held.set(false);
    cancelAnimation(angle);
    angle.set(withTiming(PUMP.rest, { duration: BREW.pour, easing: BREW.lever, ...NEVER }));
    brew.set(withTiming(1, { duration: BREW.pour, easing: LINEAR, ...NEVER }));
    shot.set(withTiming(BREW.seconds, { duration: BREW.pour, easing: LINEAR, ...NEVER }));
    bar.set(withTiming(BREW.endBar, { duration: BREW.pour, easing: Easing.bezier(0.45, 0, 0.6, 1), ...NEVER }));
    zone.set(withDelay(BREW.pour * 0.22, fade(0, 700)));
    lastSecond.set(0);
    scheduleOnRN(announce, STAGE.brewing, BREW.bar, 0);
  }, [angle, announce, bar, brew, fade, held, lastSecond, phase, shot, zone]);

  const land = useCallback((silent: boolean) => {
    'worklet';
    phase.set(STAGE.done);
    ready.set(fade(1, TIMING.ready));
    bar.set(withTiming(0, { duration: reduceMotion ? TIMING.reduced : TIMING.settle, easing: TIMING.easeInOut, ...NEVER }));
    haptic('done', silent);
    scheduleOnRN(announce, STAGE.done, BREW.bar, BREW.seconds);
  }, [announce, bar, fade, haptic, phase, ready, reduceMotion]);

  /** The full cup slides out, an empty one slides in, the timer goes back to 00: the first frame again. */
  const swap = useCallback(() => {
    'worklet';
    strokes.set(0);
    ready.set(fade(0, TIMING.readyOut));
    zone.set(fade(0, TIMING.readyOut));
    bar.set(fade(0, TIMING.readyOut));
    if (reduceMotion) {
      cupA.set(withSequence(withTiming(0, { duration: TIMING.reduced, ...NEVER }), withTiming(1, { duration: TIMING.reduced, ...NEVER })));
      brew.set(withDelay(TIMING.reduced, withTiming(0, { duration: 0, ...NEVER })));
    } else {
      const out = { duration: TIMING.cupOut, easing: TIMING.easeOut, ...NEVER };
      const back = { duration: TIMING.cupIn, easing: TIMING.easeOut, ...NEVER };
      cupX.set(withSequence(withTiming(TIMING.cupTravel, out), withTiming(TIMING.cupTravel, { duration: 0, ...NEVER }), withTiming(0, back)));
      cupA.set(withSequence(withTiming(0, out), withTiming(1, back)));
      brew.set(withDelay(TIMING.cupOut, withTiming(0, { duration: 0, ...NEVER })));
    }
    shot.set(withDelay(TIMING.timerReset, withTiming(0, { duration: 0, ...NEVER }))); // the timer's reels roll back to 00
    lastSecond.set(-1);
    scheduleOnRN(announce, STAGE.rest, 0, 0);
  }, [announce, bar, brew, cupA, cupX, fade, lastSecond, ready, reduceMotion, shot, strokes, zone]);

  /** Begin a pull at (x, y) on screen; `script` grabs by the lever's own angle. A grab takes the lever as it is. */
  const pullBegin = useCallback((x: number, y: number, script: boolean) => {
    'worklet';
    const state = phase.get();
    if (state === STAGE.brewing) return; // the spring and the puck have it: nothing to hold
    if (state === STAGE.done) {
      swap();
      phase.set(STAGE.rest);
    }
    cancelAnimation(angle);
    held.set(true);
    grabAngle.set(angle.get());
    rearmed.set(angle.get() < PUMP.rearmAt);
    // On the lever's side of the pivot and far enough out, the finger turns the lever about the pivot (1:1 at
    // the grab point); anywhere else its travel down turns it as if it held the handle.
    const dx = x - px.get();
    const dy = y - py.get();
    const at = (Math.atan2(dy, dx) * 180) / Math.PI;
    byAngle.set(!script && Math.hypot(dx, dy) > PUMP.grabFrom * LEVER_LENGTH * k.get() && at > -125 && at < 35);
    grabAtan.set(at);
  }, [angle, byAngle, grabAngle, grabAtan, held, k, phase, px, py, rearmed, swap]);

  /** Move the lever to a raw angle (before the rubber band) and count strokes. */
  const drive = useCallback((raw: number, silent: boolean) => {
    'worklet';
    if (!held.get()) return;
    const a = rubber(raw);
    angle.set(a);
    if (phase.get() !== STAGE.full && rearmed.get() && a >= PUMP.strokeAt) {
      rearmed.set(false);
      stroke(silent);
    } else if (a <= PUMP.rearmAt) rearmed.set(true);
  }, [angle, held, phase, rearmed, stroke]);

  const pullMove = useCallback((x: number, y: number, dropY: number) => {
    'worklet';
    if (byAngle.get()) {
      const at = (Math.atan2(y - py.get(), x - px.get()) * 180) / Math.PI;
      drive(grabAngle.get() + wrap180(at - grabAtan.get()), false);
    } else drive(grabAngle.get() + dropY * PUMP.degPerPt, false);
  }, [byAngle, drive, grabAngle, grabAtan, px, py]);

  /** `omega` is the lever's angular velocity at release, deg/s. */
  const pullEnd = useCallback((omega: number) => {
    'worklet';
    if (!held.get()) return;
    held.set(false);
    if (phase.get() === STAGE.full) {
      brewStart();
      return;
    }
    // Velocity handoff: the hand's speed carries into the spring back up to the stop.
    angle.set(reduceMotion
      ? withTiming(PUMP.rest, { duration: TIMING.reduced, easing: TIMING.easeOut, ...NEVER })
      : withSpring(PUMP.rest, { ...SPRING.home, velocity: omega }));
  }, [angle, brewStart, held, phase, reduceMotion]);

  /** Back to the very first frame: the demo resumes from here. */
  const resetAll = useCallback(() => {
    'worklet';
    cancelAnimation(angle);
    angle.set(withSpring(PUMP.rest, SPRING.home));
    phase.set(STAGE.rest);
    strokes.set(0);
    bar.set(fade(0, TIMING.readyOut));
    zone.set(fade(0, TIMING.readyOut));
    scheduleOnRN(announce, STAGE.rest, 0, 0);
  }, [angle, announce, bar, fade, phase, strokes, zone]);

  // ——— Autoplay: a scripted finger on the handle, on one clock ———
  const scriptFinger = useCallback((t0: number, t1: number, s: Segment) => {
    'worklet';
    if (t0 < s.down && t1 >= s.down) {
      scriptDown.set(true);
      pullBegin(0, 0, true);
    }
    if (!scriptDown.get()) return;
    const from = grabAngle.get();
    const to = PUMP.bottom + 1.5; // a firm stroke, a little into the rubber band
    if (t1 >= s.down && t1 < s.up) drive(from + (to - from) * ease((t1 - s.down) / (s.moveEnd - s.down)), true);
    if (t0 < s.up && t1 >= s.up) {
      drive(to, true);
      scriptDown.set(false);
      pullEnd(0);
    }
  }, [drive, grabAngle, pullBegin, pullEnd, scriptDown]);

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const demo = autoPlay && !demoPaused.get();
    clock.set(demo ? demoT.get() : clock.get() + dt);
    if (phase.get() === STAGE.brewing) {
      const s = Math.floor(shot.get());
      if (s !== lastSecond.get() && s < BREW.seconds) {
        lastSecond.set(s);
        scheduleOnRN(announce, STAGE.brewing, BREW.bar, s);
      }
      if (brew.get() >= 0.9995) land(demo);
    }
    if (!autoPlay) return;
    if (demoPaused.get()) {
      if (!realDown.get()) quiet.set(quiet.get() + dt);
      const state = phase.get();
      if (quiet.get() < RESUME_MS / 1000 || held.get()) return;
      if (state === STAGE.done) {
        // Let the script swap the cup itself, then carry on from the top of the loop.
        demoT.set(DEMO.swap - 0.001);
        demoPaused.set(false);
      } else if (state === STAGE.rest || state === STAGE.pumping) {
        resetAll();
        demoT.set(0);
        demoPaused.set(false);
      }
      return;
    }
    const t0 = demoT.get();
    const t1 = t0 + dt;
    for (let i = 0; i < DEMO.strokes.length; i++) scriptFinger(t0, t1, DEMO.strokes[i]);
    if (t0 < DEMO.swap && t1 >= DEMO.swap && phase.get() === STAGE.done) {
      swap();
      phase.set(STAGE.rest);
    }
    demoT.set(t1 >= LOOP_S ? t1 - LOOP_S : t1);
  });

  /** A real touch stops the script at once, leaving the machine where it is. */
  const interrupt = useCallback(() => {
    'worklet';
    realDown.set(true);
    quiet.set(0);
    if (demoPaused.get()) return;
    demoPaused.set(true);
    if (scriptDown.get()) {
      scriptDown.set(false);
      pullEnd(0);
    }
  }, [demoPaused, pullEnd, quiet, realDown, scriptDown]);

  const release = useCallback(() => {
    'worklet';
    realDown.set(false);
    quiet.set(0);
  }, [quiet, realDown]);

  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(4)
    .onBegin(interrupt)
    .onStart((e) => pullBegin(e.x - e.translationX, e.y - e.translationY, false))
    .onUpdate((e) => pullMove(e.x, e.y, e.translationY))
    .onFinalize((e) => {
      release();
      // The finger's speed as the lever's: about the pivot, or down the screen at the handle's radius.
      const dx = e.x - px.get();
      const dy = e.y - py.get();
      const r2 = Math.max(dx * dx + dy * dy, 1);
      const omega = byAngle.get() ? ((dx * e.velocityY - dy * e.velocityX) / r2) * (180 / Math.PI) : e.velocityY * PUMP.degPerPt;
      pullEnd(omega);
    }), [byAngle, interrupt, pullBegin, pullEnd, pullMove, px, py, release]);

  /** The accessible alternative: one action pumps to 9 bar and lets go; on a finished shot it swaps the cup. */
  const pullShot = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      interrupt();
      release();
      const state = phase.get();
      if (state === STAGE.done) {
        swap();
        phase.set(STAGE.rest);
        return;
      }
      if (state === STAGE.brewing) return;
      strokes.set(PUMP.strokes - 1);
      stroke(false);
      held.set(true);
      pullEnd(0);
    });
  }, [held, interrupt, phase, pullEnd, release, stroke, strokes, swap]);

  return {
    gesture,
    pullShot,
    readout,
    values: { angle, bar, zone, brew, ready, cupX, cupA, shot, clock, puffAt },
  };
}

export type MachineValues = ReturnType<typeof usePullLever>['values'];

import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withSpring, withTiming, type FrameInfo } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { FLING_PROJECTION_S, PAN_REACH, PAN_RESIST, SNAP_SPRING } from '../../gestures/choose-your-ball/ballPickerGeometry';

export const LOOP = 13.5;
const HOLD = 3 / LOOP;
const CROSS = 1.5 / LOOP;
const SLOT = HOLD + CROSS;
/** Each specimen rests for 3 s, performs its signature move, then crosses for 1.5 s. */
export const SNAP_TIMES = [0, SLOT, SLOT * 2] as const;
export const SIGNATURE_MS = 1800;
/** One landing squash after a specimen arrives (HabitatRig.landEnvelope). */
export const LAND_MS = 620;
const SIGNATURE_START = 0.55 / LOOP;
const SIGNATURE = SIGNATURE_MS / 1000 / LOOP;
/**
 * How long a move's particles outlive the move itself (seconds from its start): leaves fall and
 * settle, drops land and splash, embers burn out. It ends with the slot, before the next specimen.
 */
export const FX_S = 3.9;
export type HabitatTab = 0 | 1 | 2;

export function wrapPosition(value: number) {
  'worklet';
  return ((value % 3) + 3) % 3;
}
export function specimenDistance(index: number, position: number) {
  'worklet';
  return wrapPosition(index - position + 1.5) - 1.5;
}
export function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.max(low, Math.min(high, value));
}
export function easeCrossing(x: number) {
  'worklet';
  let t = clamp(x);
  for (let i = 0; i < 7; i++) {
    const a = 1 - t;
    const delta = 3 * a * a * t * 0.77 + 3 * a * t * t * 0.175 + t * t * t - x;
    const derivative = 3 * a * a * 0.77 + 6 * a * t * (0.175 - 0.77) + 3 * t * t * (1 - 0.175);
    if (Math.abs(derivative) < 0.00001) break;
    t = clamp(t - delta / derivative);
  }
  return 3 * (1 - t) * t * t + t * t * t;
}
function slotOf(progress: number) {
  'worklet';
  const p = clamp(progress);
  const index = Math.min(2, Math.floor(p / SLOT));
  return { index, local: p - index * SLOT };
}
export function timelinePosition(progress: number) {
  'worklet';
  const { index, local } = slotOf(progress);
  return local < HOLD ? index : index + easeCrossing((local - HOLD) / CROSS);
}
/** Signature progress (0..1) while a resting specimen performs; 0 otherwise. */
export function timelineSignature(progress: number) {
  'worklet';
  const { local } = slotOf(progress);
  const t = (local - SIGNATURE_START) / SIGNATURE;
  return t > 0 && t < 1 ? t : 0;
}
/**
 * The specimen whose slot this is: it performs, and its move's particles keep belonging to it
 * through the crossing that follows (not to the specimen arriving, which rounding would give).
 */
export function timelinePerformer(progress: number) {
  'worklet';
  return slotOf(progress).index;
}
/** Seconds since the resting specimen's move began, for its particles; -1 outside a move and its afterlife. */
export function timelineEffect(progress: number) {
  'worklet';
  const { local } = slotOf(progress);
  const seconds = (local - SIGNATURE_START) * LOOP;
  return seconds > 0 && seconds < FX_S ? seconds : -1;
}
/** The demo visits every panel: About → Base stats (morphing across two swipes) → Evolution. */
export function timelineTab(progress: number): HabitatTab {
  'worklet';
  const { index, local } = slotOf(progress);
  if (index === 0) return local < HOLD - 0.5 / LOOP ? 0 : 1;
  if (index === 1) return 1;
  if (local < 0.2 / LOOP) return 1;
  return local < HOLD ? 2 : 0;
}
/** Anticipation and release envelope of a signature move. */
export function signatureEnvelope(t: number) {
  'worklet';
  if (t <= 0 || t >= 1) return 0;
  const rise = clamp((t - 0.08) / 0.14);
  const fall = clamp((t - 0.6) / 0.4);
  return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall));
}
/** Squash on the ground, recoil, then a damped settle. Origin is the creature's feet. */
export function signaturePose(t: number, index: number) {
  'worklet';
  if (t <= 0 || t >= 1) return { sx: 1, sy: 1, tx: 0, ty: 0 };
  const crouch = clamp(t / 0.11);
  const anticipation = t < 0.12 ? crouch * crouch : 1 - clamp((t - 0.12) / 0.04);
  const seconds = Math.max(0, t - 0.12) * (SIGNATURE_MS / 1000);
  const spring = t < 0.12 ? 0 : Math.exp(-seconds * 6.5) * Math.sin(seconds * 21);
  const lift = index === 0 ? 1.5 : index === 2 ? 1.2 : 0.6;
  return {
    sx: 1 + 0.024 * anticipation - 0.018 * spring,
    sy: 1 - 0.042 * anticipation + 0.034 * spring * lift,
    tx: index === 1 ? -5 * spring : 0,
    ty: 2.5 * anticipation - 5 * Math.max(0, spring) * lift,
  };
}
export function contentOpacity(index: number, position: number, reduced: boolean) {
  'worklet';
  if (reduced) return Math.round(wrapPosition(position)) % 3 === index ? 1 : 0;
  const t = clamp((Math.abs(specimenDistance(index, position)) - 0.4) / 0.09);
  return 1 - t * t * (3 - 2 * t);
}

/** How far the specimens drift across a crossing, as a fraction of the screen width. */
export const EXCHANGE_DRIFT = 0.16;
/**
 * The exchange, for flat cut-out art: nothing turns, because a picture has no other side. The
 * incoming habitat's front wipes across the stage and brings its specimen with it; each specimen is
 * drawn only on its own side of that front, so the two never overlap or ghost. A short parallax
 * gives the layers depth: the incoming one settles in from the front's side and lands from slightly
 * nearer, the outgoing one is pushed back and away. `role` 0 is outgoing, 1 incoming, -1 off stage;
 * `drift` is in units of EXCHANGE_DRIFT. HABITAT_EFFECT's contact shadows repeat these formulas.
 */
export function exchangePose(index: number, position: number, reduced: boolean) {
  'worklet';
  const p = wrapPosition(reduced ? Math.round(position) : position);
  const from = Math.floor(p) % 3;
  const f = reduced ? 0 : p - Math.floor(p);
  if (index === from) return { role: 0, f, drift: -0.5 * f * f, scale: 1 - 0.05 * f };
  const u = 1 - f;
  if (index === (from + 1) % 3 && f > 0.0005) return { role: 1, f, drift: u * u, scale: 1 + 0.05 * u * u };
  return { role: -1, f, drift: 0, scale: 1 };
}

// Neighbours enter only during a crossing; a settled specimen has no edge fragments.
export function creatureOpacity(index: number, position: number, reduced: boolean) {
  'worklet';
  if (reduced) return Math.round(wrapPosition(position)) % 3 === index ? 1 : 0;
  const t = clamp((Math.abs(specimenDistance(index, position)) - 0.60) / 0.38);
  return 1 - t * t * (3 - 2 * t);
}

// A signed current follows both the finger's travel and its velocity.
export function gestureInfluence(translation: number, velocity: number, gap: number) {
  'worklet';
  return clamp(velocity / (gap * 4) + clamp(translation / gap, -1, 1) * 0.18, -1, 1);
}

const PRESS_IN = Easing.bezier(0.23, 1, 0.32, 1);
const LOOK_SPRING = { duration: 520, dampingRatio: 0.9 } as const;

export function useHabitatMotion({ autoPlay = true, previewTime, previewReducedMotion, gap, width, onSignature, onLand, onPet }: {
  autoPlay?: boolean; previewTime?: number; previewReducedMotion?: boolean; gap: number;
  /** Screen width, for the head's look towards the finger. */
  width: number;
  /** Called on the JS thread when a person (not the demo) starts a signature move. */
  onSignature?: () => void;
  /** Called on the JS thread when a specimen a person sent lands. */
  onLand?: () => void;
  /** Called on the JS thread when a hold starts petting the specimen. */
  onPet?: () => void;
}) {
  const systemReduced = useReducedMotion();
  const reduced = previewReducedMotion ?? systemReduced;
  const initialTime = previewTime == null ? 0 : clamp(previewTime / LOOP) % 1;
  const rawPosition = useSharedValue(timelinePosition(initialTime));
  const time = useSharedValue(initialTime);
  const ambientTime = useSharedValue(initialTime);
  const influence = useSharedValue(0);
  const position = useDerivedValue(() => wrapPosition(rawPosition.get()));
  const reducedValue = useSharedValue(reduced);
  const dragging = useSharedValue(false);
  const manual = useSharedValue(false);
  const dragOrigin = useSharedValue(0);
  const now = useSharedValue(0);
  const resumeAt = useSharedValue(0);
  const foreground = useSharedValue(AppState.currentState !== 'background' && AppState.currentState !== 'inactive');
  const routeFocused = useSharedValue(false);
  const resumePending = useSharedValue(false);
  const [focused, setFocused] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(Math.round(wrapPosition(timelinePosition(initialTime))) % 3);
  const initialIndex = Math.round(wrapPosition(timelinePosition(initialTime))) % 3;
  /** Signature progress 0..1 and the specimen performing it; both 0/idle outside a move. */
  const power = useSharedValue(previewTime == null ? 0 : timelineSignature(initialTime));
  const powerIndex = useSharedValue(initialIndex);
  /** Seconds since the move began, running past it while its particles land (FX_S); -1 when idle. */
  const fx = useSharedValue(previewTime == null ? -1 : timelineEffect(initialTime));
  /** After a person's move the demo resumes mid-slot; its own move's afterlife must not replay then. */
  const fxHeld = useSharedValue(false);
  const autoTab = useSharedValue<HabitatTab>(timelineTab(initialTime));
  const [demoTab, setDemoTab] = useState<HabitatTab>(timelineTab(initialTime));
  const signatureCallback = useMemo(() => () => onSignature?.(), [onSignature]);
  const landCallback = useMemo(() => () => onLand?.(), [onLand]);
  const petCallback = useMemo(() => () => onPet?.(), [onPet]);
  /** Anticipation while a finger rests on the specimen, 0..1. */
  const press = useSharedValue(0);
  /** Where the head looks, -1 (left) .. 1 (right), springing back to 0. */
  const look = useSharedValue(0);
  /** Petting under a hold: amount 0..1, seconds since it began, and the clock at release. */
  const pet = useSharedValue(0);
  const petClock = useSharedValue(0);
  const petUntil = useSharedValue(-1);
  /** One landing squash (0 → 1 over LAND_MS) for the specimen that just arrived. */
  const landing = useSharedValue(1);
  const landingIndex = useSharedValue(initialIndex);
  const landed = useSharedValue(initialIndex);
  /** The finger on the stage (stage points): x, y, velocity x, y, down; and the last tap: x, y, frame ms. */
  const touch = useSharedValue<readonly number[]>([-999, -999, 0, 0, 0]);
  const tapAt = useSharedValue<readonly number[]>([0, 0, -1]);
  const touchClock = useSharedValue(0);

  useEffect(() => {
    reducedValue.set(reduced);
    if (reduced) { cancelAnimation(influence); influence.set(0); }
  }, [influence, reduced, reducedValue]);
  const suspendManual = useCallback(() => {
    cancelAnimation(rawPosition);
    cancelAnimation(influence); influence.set(0);
    dragging.set(false);
    resumePending.set(true);
  }, [dragging, influence, rawPosition, resumePending]);
  const restoreManual = useCallback(() => {
    resumePending.set(true);
    if (!manual.get()) return;
    rawPosition.set(withSpring(Math.round(rawPosition.get()), {
      duration: 400, dampingRatio: 1,
      reduceMotion: reducedValue.get() ? ReduceMotion.Always : ReduceMotion.Never,
    }));
  }, [manual, rawPosition, reducedValue, resumePending]);
  useFocusEffect(useCallback(() => {
    routeFocused.set(true);
    setFocused(true);
    if (foreground.get()) restoreManual();
    return () => {
      routeFocused.set(false);
      setFocused(false);
      suspendManual();
    };
  }, [foreground, restoreManual, routeFocused, suspendManual]));
  useEffect(() => {
    const listener = AppState.addEventListener('change', state => {
      const active = state === 'active';
      foreground.set(active);
      if (!active) suspendManual();
      else if (routeFocused.get()) restoreManual();
    });
    return () => listener.remove();
  }, [foreground, restoreManual, routeFocused, suspendManual]);
  useEffect(() => {
    if (previewTime == null) return;
    const p = clamp(previewTime / LOOP);
    cancelAnimation(rawPosition);
    cancelAnimation(influence); influence.set(0);
    manual.set(false); dragging.set(false); time.set(p % 1); ambientTime.set(p % 1); rawPosition.set(timelinePosition(p));
    cancelAnimation(power); power.set(timelineSignature(p % 1)); powerIndex.set(timelinePerformer(p % 1));
    cancelAnimation(fx); fx.set(timelineEffect(p % 1));
    autoTab.set(timelineTab(p % 1));
  }, [ambientTime, autoTab, dragging, fx, influence, manual, power, powerIndex, previewTime, rawPosition, time]);
  useAnimatedReaction(() => autoTab.get(), (tab, previous) => { if (tab !== previous) scheduleOnRN(setDemoTab, tab); });

  useAnimatedReaction(() => Math.round(wrapPosition(rawPosition.get())) % 3, (index, previous) => {
    if (index !== previous) scheduleOnRN(setSelectedIndex, index);
  });
  // A specimen lands when its hop ends (8% before the slot), whether the demo, a fling or a slow drag brought it.
  useAnimatedReaction(() => {
    const p = rawPosition.get();
    const index = Math.round(wrapPosition(p)) % 3;
    return Math.abs(specimenDistance(index, p)) < 0.08 ? index : -1;
  }, index => {
    if (index < 0 || index === landed.get()) return;
    landed.set(index);
    if (reducedValue.get()) return;
    landingIndex.set(index);
    landing.set(0);
    landing.set(withTiming(1, { duration: LAND_MS, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    if (manual.get()) scheduleOnRN(landCallback);
  });
  const tick = useCallback((info: FrameInfo) => {
    'worklet';
    if (!routeFocused.get() || !foreground.get() || previewTime != null) return;
    const elapsed = Math.min(info.timeSincePreviousFrame ?? 0, 64) / (LOOP * 1000);
    if (!reducedValue.get()) ambientTime.set((ambientTime.get() + elapsed) % 1);
    now.set(info.timestamp);
    // Hearts follow the wall clock (slow frames must not slow the stream), within reason.
    const seconds = Math.min(info.timeSincePreviousFrame ?? 0, 250) / 1000;
    if (pet.get() > 0 || petClock.get() < petUntil.get() + 1.4) petClock.set(petClock.get() + seconds);
    if (!autoPlay) return;
    if (resumePending.get()) {
      resumePending.set(false);
      if (manual.get()) resumeAt.set(info.timestamp + 2000);
      return;
    }
    if (manual.get()) {
      if (dragging.get() || info.timestamp < resumeAt.get()) return;
      const index = Math.round(wrapPosition(rawPosition.get())) % 3;
      // Resume after this specimen's demo move, so a person never sees it replayed out of turn.
      rawPosition.set(index); time.set(SNAP_TIMES[index] + 2.4 / LOOP); manual.set(false); fxHeld.set(true);
      return;
    }
    const p = (time.get() + elapsed) % 1;
    time.set(p); rawPosition.set(timelinePosition(p));
    const tab = timelineTab(p);
    if (tab !== autoTab.get()) autoTab.set(tab);
    if (reducedValue.get()) { power.set(0); fx.set(-1); return; }
    power.set(timelineSignature(p));
    const effect = timelineEffect(p);
    // A person's move keeps its performer and clock until the demo's next move begins.
    if (fxHeld.get()) { if (effect < 0) fxHeld.set(false); }
    else {
      if (effect !== fx.get()) fx.set(effect);
      powerIndex.set(timelinePerformer(p));
    }
  }, [ambientTime, autoPlay, autoTab, dragging, foreground, fx, fxHeld, manual, now, pet, petClock, petUntil, power, powerIndex, previewTime, rawPosition, reducedValue, resumeAt, resumePending, routeFocused, time]);
  const frame = useFrameCallback(tick, false);
  useEffect(() => { frame.setActive(focused && previewTime == null); }, [focused, frame, previewTime]);

  const pan = useMemo(() => Gesture.Pan().minDistance(3)
    .onBegin(() => {
      if (!routeFocused.get() || !foreground.get()) return;
      cancelAnimation(rawPosition); cancelAnimation(influence);
      manual.set(true); dragging.set(true); dragOrigin.set(rawPosition.get()); resumeAt.set(Number.MAX_SAFE_INTEGER);
    })
    .onUpdate(event => {
      if (!dragging.get() || !routeFocused.get() || !foreground.get()) return;
      // Choose Your Ball's rule: one wipe, one specimen. The drag tracks the finger 1:1 up to one
      // slot from where it began, then resists asymptotically instead of stopping dead.
      const home = Math.round(dragOrigin.get());
      const drift = dragOrigin.get() - event.translationX / gap - home;
      const over = Math.abs(drift) - PAN_REACH;
      rawPosition.set(over <= 0 ? home + drift : home + Math.sign(drift) * (PAN_REACH + over / (1 + over * PAN_RESIST)));
      influence.set(reducedValue.get() ? 0 : gestureInfluence(event.translationX, event.velocityX, gap));
      if (!reducedValue.get()) look.set(withSpring(clamp((event.x - width / 2) / (width * 0.4), -1, 1), LOOK_SPRING));
    })
    .onEnd(event => {
      if (!dragging.get()) return;
      const velocity = -event.velocityX / gap;
      const home = Math.round(dragOrigin.get());
      const projected = rawPosition.get() + velocity * FLING_PROJECTION_S;
      const target = Math.round(Math.max(home - PAN_REACH, Math.min(home + PAN_REACH, projected)));
      // The same landing spring as the ball: settles without a visible bounce but overshoots once.
      rawPosition.set(withSpring(target, { ...SNAP_SPRING, velocity, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
      influence.set(withSpring(0, { duration: 650, dampingRatio: 1, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
      dragging.set(false); resumeAt.set(now.get() + 2000);
    })
    .onFinalize(() => {
      look.set(withSpring(0, LOOK_SPRING));
      if (!dragging.get()) return;
      rawPosition.set(withSpring(Math.round(rawPosition.get()), { duration: 400, dampingRatio: 1, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
      influence.set(withSpring(0, { duration: 650, dampingRatio: 1, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
      dragging.set(false); resumeAt.set(now.get() + 2000);
    }), [dragOrigin, dragging, foreground, gap, influence, look, manual, now, rawPosition, reduced, reducedValue, resumeAt, routeFocused, width]);

  // A tap on a resting specimen performs its signature move; a drag always wins the race.
  const tap = useMemo(() => Gesture.Tap().maxDuration(450).maxDistance(10).onBegin(event => {
    // Feedback lands with the finger: the specimen gathers itself and turns towards the touch.
    if (!routeFocused.get() || reducedValue.get()) return;
    press.set(withTiming(1, { duration: 140, easing: PRESS_IN }));
    look.set(withSpring(clamp((event.x - width / 2) / (width * 0.4), -1, 1), LOOK_SPRING));
  }).onFinalize(() => {
    press.set(withTiming(0, { duration: 200, easing: PRESS_IN }));
    if (pet.get() === 0) look.set(withSpring(0, LOOK_SPRING));
  }).onEnd((event, success) => {
    if (!success || !routeFocused.get() || !foreground.get() || reducedValue.get()) return;
    // The release sends a ring of air through the particles.
    tapAt.set([event.x, event.y, now.get()]);
    const current = rawPosition.get();
    const index = Math.round(wrapPosition(current)) % 3;
    if (Math.abs(specimenDistance(index, current)) > 0.04) return;
    const running = power.get();
    if (running > 0 && running < 0.72) return;
    cancelAnimation(rawPosition); rawPosition.set(Math.round(current));
    manual.set(true); dragging.set(false); resumeAt.set(now.get() + SIGNATURE_MS + 2000);
    powerIndex.set(index);
    power.set(0);
    power.set(withTiming(1, { duration: SIGNATURE_MS, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    fx.set(0);
    fx.set(withTiming(FX_S, { duration: FX_S * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    scheduleOnRN(signatureCallback);
  }), [dragging, foreground, fx, look, manual, now, pet, power, powerIndex, press, rawPosition, reducedValue, resumeAt, routeFocused, signatureCallback, tapAt, width]);
  // A hold pets the specimen: it squints, wiggles and hearts rise for as long as the finger stays.
  const hold = useMemo(() => Gesture.LongPress().minDuration(380).maxDistance(12).onStart(() => {
    if (!routeFocused.get() || !foreground.get()) return;
    const current = rawPosition.get();
    if (Math.abs(specimenDistance(Math.round(wrapPosition(current)) % 3, current)) > 0.04) return;
    cancelAnimation(rawPosition); rawPosition.set(Math.round(current));
    manual.set(true); dragging.set(false); resumeAt.set(Number.MAX_SAFE_INTEGER);
    petClock.set(0); petUntil.set(Number.MAX_SAFE_INTEGER);
    pet.set(withTiming(1, { duration: 220, easing: PRESS_IN }));
    scheduleOnRN(petCallback);
  }).onFinalize(() => {
    // Only a hold that became a pet releases one; every failed hold (each tap) ends here too.
    if (petUntil.get() <= 1e9) return;
    petUntil.set(petClock.get());
    pet.set(withTiming(0, { duration: 260, easing: PRESS_IN }));
    look.set(withSpring(0, LOOK_SPRING));
    resumeAt.set(now.get() + 2000);
  }), [dragging, foreground, look, manual, now, pet, petCallback, petClock, petUntil, rawPosition, resumeAt, routeFocused]);
  // Every finger on the stage stirs the particle world: the pan follows each touch from the moment it
  // lands. These callbacks are added to an existing gesture, out of the Babel plugin's sight, so they
  // say 'worklet' themselves (a gesture mixing worklet and JS callbacks stops working).
  const gesture = useMemo(() => Gesture.Race(pan.onTouchesDown(event => {
    'worklet';
    const point = event.allTouches[0];
    if (!point) return;
    touch.set([point.x, point.y, 0, 0, 1]);
    touchClock.set(now.get());
  }).onTouchesMove(event => {
    'worklet';
    const point = event.allTouches[0];
    if (!point) return;
    const previous = touch.get();
    const elapsed = Math.max(8, now.get() - touchClock.get()) / 1000;
    touch.set([point.x, point.y,
      previous[2] + ((point.x - previous[0]) / elapsed - previous[2]) * 0.5,
      previous[3] + ((point.y - previous[1]) / elapsed - previous[3]) * 0.5, 1]);
    touchClock.set(now.get());
  }).onTouchesUp(event => {
    'worklet';
    if (event.numberOfTouches > 0) return;
    const previous = touch.get();
    touch.set([previous[0], previous[1], previous[2], previous[3], 0]);
  }).onTouchesCancelled(() => {
    'worklet';
    const previous = touch.get();
    touch.set([previous[0], previous[1], previous[2], previous[3], 0]);
  }), hold, tap), [hold, now, pan, tap, touch, touchClock]);
  /** Any other control the person touches keeps the demo paused for `ms`. */
  const holdDemo = useCallback((ms: number) => {
    if (!routeFocused.get()) return;
    if (!manual.get()) { cancelAnimation(rawPosition); rawPosition.set(Math.round(rawPosition.get())); }
    manual.set(true); resumeAt.set(now.get() + ms);
  }, [manual, now, rawPosition, resumeAt, routeFocused]);

  const select = useCallback((index: number) => {
    if (!routeFocused.get() || !foreground.get()) return;
    cancelAnimation(rawPosition);
    const current = rawPosition.get();
    const target = current + specimenDistance(index, current);
    influence.set(withSpring(0, { duration: 650, dampingRatio: 1, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
    manual.set(true); dragging.set(false); resumeAt.set(now.get() + 2000);
    rawPosition.set(withSpring(target, { ...SNAP_SPRING, reduceMotion: reduced ? ReduceMotion.Always : ReduceMotion.Never }));
  }, [dragging, foreground, influence, manual, now, rawPosition, reduced, resumeAt, routeFocused]);

  return { position, time: ambientTime, influence, reduced, reducedValue, selectedIndex, pan, gesture, select, hold: holdDemo, power, powerIndex, fx, demoTab, press, look, pet, petClock, petUntil, landing, landingIndex, manual, touch, tapAt };
}

// Card Vacuum Seal — the film's controller (everything but the drawing): one state value `p`, the autoplay loop, the
// finger on the shield tile (hold), the finger on the film (squeegee, dimple, peel), haptics, sound, seed and
// amplitude of the creases, and the `sealed` state for the host. All on the UI thread; React state changes only at
// the lock and at the release.
//
//   mode 0  the loop plays: `look` = lookAt(loop time), a pure function of time
//   mode 1  a finger (or nothing) moves p: `look` is built from the shared values below
// A touch takes over from the loop's current look without a jump; 2 s after the last touch the film glides to rest
// and the loop restarts at 0 (RESUME_AFTER).
import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, type GestureResponderHandlers } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import {
  Easing,
  type FrameCallback,
  useAnimatedReaction,
  type SharedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { hapticAirBack, hapticDetent, hapticDimple, hapticLock, hapticOnset, hapticRigid } from './cvs-haptics';
import {
  COMMIT_AT,
  creaseAmpOf,
  fingerP,
  LABEL,
  labelAt,
  LOOP,
  type Look,
  lookAt,
  REST,
  RESUME_AFTER,
  SEAL_MS,
  SPRING,
  SQUEEGEE,
  TAP_MAX,
  TICKS,
  UNSEAL_MS,
} from './cvs-motion';
import { AIR_RATE, useVacuumSound } from './cvs-sound';
import { COPY } from './cvs-theme';

export type SealOptions = {
  autoPlay?: boolean;
  /** the film's state 0 no film · 0.3 draped · 1 sealed. Read it, or drive it (a spring, a timing) while no loop plays. */
  progress?: SharedValue<number>;
  sealed?: boolean;
  defaultSealed?: boolean;
  onSealedChange?: (sealed: boolean) => void;
  gesture?: 'squeegee' | 'none';
  holdMs?: number;
  commitAt?: number;
  creaseSeed?: number;
  haptics?: boolean;
  sound?: boolean;
  /** the hold target's size in pt (a finger within 16 pt of it still holds) */
  holdSize?: { w: number; h: number };
  /** a <VacuumSeal> that is handed a controller makes none of its own: this one sleeps */
  disabled?: boolean;
  /** Only the demo registers the development pin hook. */
  devHook?: boolean;
};

/** what a finger is doing: kind 0 holds the shield tile, 1 squeegees the film, 2 dents the sealed film */
type Touch = { down: boolean; kind: number; onTile: boolean; t0: number; p0: number; dir: number; off: number; ready: boolean };
const NO_TOUCH: Touch = { down: false, kind: 0, onTile: false, t0: 0, p0: 1, dir: -1, off: 0, ready: false };

export type VacuumSealHandle = ReturnType<typeof useVacuumSeal>;

export function useVacuumSeal(opts: SealOptions = {}) {
  const {
    autoPlay = false,
    sealed: sealedProp,
    defaultSealed = true,
    onSealedChange,
    gesture = 'squeegee',
    holdMs = SEAL_MS,
    commitAt = COMMIT_AT,
    creaseSeed = 0,
    haptics = true,
    sound = false,
    holdSize = { w: 112, h: 112 },
  } = opts;
  const reduce = useReducedMotion();
  const disabled = opts.disabled === true;
  const frameRef = useRef<FrameCallback | null>(null);
  const activateClock = useCallback((active: boolean) => frameRef.current?.setActive(active), []);
  const seedFixed = opts.creaseSeed !== undefined;
  const initialP = (sealedProp ?? defaultSealed) ? 1 : 0;
  const normalizedSeed = ((Math.trunc(creaseSeed) % 3) + 3) % 3;

  // ---- state, all on the UI thread
  const look = useSharedValue<Look>({ ...REST, p: reduce ? 1 : initialP });
  const loopT = useSharedValue(0);
  const ambient = useSharedValue(0);
  const ambOff = useSharedValue(0); // keeps the light and shimmer continuous when the loop restarts after a touch
  const mode = useSharedValue(autoPlay && sealedProp === undefined && !reduce ? 0 : 1);
  const ownP = useSharedValue(initialP);
  const pLive = opts.progress ?? ownP;
  const press = useSharedValue(0);
  const flash = useSharedValue(0);
  const settle = useSharedValue(1);
  const glint = useSharedValue(0);
  const discGlint = useSharedValue(0);
  const tug = useSharedValue(0);
  const tugSide = useSharedValue(0);
  const target = useSharedValue(initialP); // the committed state: 1 sealed, 0 open
  const touch = useSharedValue<Touch>(NO_TOUCH);
  const lastTouch = useSharedValue(-1e9);
  const resuming = useSharedValue(false);
  const lastP = useSharedValue(initialP);
  const lockArmed = useSharedValue(false);
  const dimple = useSharedValue({ x: 0, y: 0 }); // where the finger presses the film (design units)
  const dimpleK = useSharedValue(0);
  const finger = useSharedValue({ x: 0, y: 0 }); // where the finger squeegees the film (design units)
  const fingerK = useSharedValue(0);
  const stretch = useSharedValue(0);
  const sqTarget = useSharedValue(initialP);
  const velocity = useSharedValue(0); // p/s, average of the last three rendered frames
  const velocity1 = useSharedValue(0);
  const velocity2 = useSharedValue(0);
  const holdActive = useSharedValue(false);
  const userDriven = useSharedValue(false);
  const peakSpeed = useSharedValue(0);
  const seed = useSharedValue(normalizedSeed);
  const amp = useSharedValue(1);
  const extra = useSharedValue(0);
  const fade = useSharedValue(reduce ? initialP : 1);
  const pinned = useSharedValue(-1);
  const tilt = useSharedValue(0); // rad, written by <DeviceTilt>
  const lastAir = useSharedValue(-1);
  // the tile's label and shield: 1 "Protected" … 0 "Protect Card", a timed morph at the lock and when air gets back in
  const labelK = useSharedValue(initialP >= 1 ? 1 : 0);
  const labelOn = useSharedValue(initialP >= 1);
  // the card's geometry, written by the component on layout: design unit in pt, half size in design units
  const geom = useSharedValue({ S: 1, hw: 177, hh: 111.5 });

  // ---- what the host sees
  const [sealedState, setSealedState] = useState(defaultSealed);
  const sealed = sealedProp ?? sealedState; // controlled: the host's value; the film's own lock only reports it
  const snd = useVacuumSound(sound && !reduce);
  const requested = useSharedValue<boolean | null>(null);
  const notified = useSharedValue(sealedProp ?? defaultSealed); // the last state the host was told about
  const announce = useCallback(
    (on: boolean) => {
      if (sealedProp !== undefined) {
        if (on !== sealedProp && requested.get() !== on) {
          requested.set(on);
          onSealedChange?.(on);
          // The host remains authoritative. Its next render can accept the requested state;
          // otherwise the film returns to the supplied state from its current value.
          userDriven.set(false);
          target.set(sealedProp ? 1 : 0);
          pLive.set(reduce ? (sealedProp ? 1 : 0) : withSpring(sealedProp ? 1 : 0, sealedProp ? SPRING.complete : SPRING.revert));
          if (reduce) fade.set(withTiming(sealedProp ? 1 : 0, { duration: 200 }));
        } else if (on === sealedProp) requested.set(null);
        return;
      }
      if (notified.get() !== on) {
        notified.set(on);
        setSealedState(on);
        onSealedChange?.(on);
        AccessibilityInfo.announceForAccessibility(on ? COPY.a11y.sealed : COPY.a11y.open);
      }
    },
    [onSealedChange, notified, sealedProp, requested, userDriven, target, pLive, reduce, fade],
  );
  const hLock = useCallback(() => {
    if (haptics) hapticLock();
    if (sound) snd.tup();
  }, [haptics, sound, snd]);
  const hAirBack = useCallback(() => {
    if (haptics) hapticAirBack();
    if (sound) snd.inhale();
  }, [haptics, sound, snd]);
  const hDetent = useCallback(
    (hard: boolean) => {
      if (haptics) (hard ? hapticRigid : hapticDetent)();
    },
    [haptics],
  );
  const hOnset = useCallback(() => {
    if (haptics) hapticOnset();
  }, [haptics]);
  const hDimple = useCallback(() => {
    if (haptics) hapticDimple();
  }, [haptics]);
  const hAir = snd.air;

  // A supplied progress value owns the initial visual state; read it after render.
  useEffect(() => {
    if (!opts.progress || sealedProp !== undefined || autoPlay) return;
    const p = Math.min(1, Math.max(0, pLive.get()));
    const on = p >= commitAt;
    target.set(on ? 1 : 0);
    lastP.set(p);
    notified.set(on);
    queueMicrotask(() => setSealedState(on));
    look.set({ ...REST, p: reduce ? 1 : p });
  }, [opts.progress, sealedProp, autoPlay, commitAt, pLive, target, lastP, notified, look, reduce]);

  // controlled `sealed`: the host changes it → the film follows (a timing, never the loop)
  useEffect(() => {
    if (sealedProp === undefined) return;
    requested.set(null);
    notified.set(sealedProp);
    userDriven.set(false);
    activateClock(!reduce);
    target.set(sealedProp ? 1 : 0);
    if (reduce) {
      pLive.set(sealedProp ? 1 : 0);
      fade.set(withTiming(sealedProp ? 1 : 0, { duration: 200 }));
    } else {
      mode.set(1);
      lastTouch.set(ambient.get());
      pLive.set(withTiming(sealedProp ? 1 : 0, { duration: 900, easing: Easing.bezier(0.4, 0, 0.3, 1) }));
    }
  }, [sealedProp, notified, reduce, target, fade, mode, lastTouch, ambient, pLive, userDriven, activateClock, requested]);

  // development only: `__cvs.pin(t)` freezes the loop at t s, `.play()` resumes, `.progress(x)` holds p = x
  useEffect(() => {
    if (!__DEV__ || !opts.devHook) return;
    const globals = globalThis as Record<string, unknown>;
    const previous = globals.__cvs;
    const hook = {
      pin: (t: number) => {
        pinned.set(t);
        activateClock(true);
      },
      play: () => {
        pinned.set(-1);
        activateClock(autoPlay && !reduce);
      },
      progress: (x: number) => {
        pinned.set(1000 + Math.min(1, Math.max(0, x)));
        activateClock(true);
      },
      seed: (n: number) => seed.set(((Math.trunc(n) % 3) + 3) % 3),
      state: () => ({ look: look.get(), mode: mode.get(), loopT: loopT.get(), seed: seed.get(), amp: amp.get(), v: velocity.get() }),
    };
    globals.__cvs = hook;
    return () => {
      if (globals.__cvs === hook) {
        if (previous === undefined) delete globals.__cvs;
        else globals.__cvs = previous;
      }
    };
  }, [pinned, look, mode, loopT, seed, amp, velocity, opts.devHook, activateClock, autoPlay, reduce]);

  // the label follows the film's state, never its progress (held half-way through a pause, it read as two labels)
  const syncLabel = (p: number) => {
    'worklet';
    if (!labelOn.get() && p >= LABEL.on) {
      labelOn.set(true);
      labelK.set(withTiming(1, { duration: LABEL.ms, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
    } else if (labelOn.get() && p < LABEL.off) {
      labelOn.set(false);
      labelK.set(withTiming(0, { duration: LABEL.ms, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
    }
  };

  const frameClock = useFrameCallback(
    (frame) => {
      'worklet';
      const dt = Math.min((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
      const pin = pinned.get();
      if (pin >= 1000) {
        look.set({ ...REST, p: pin - 1000 });
        labelOn.set(pin - 1000 >= LABEL.on);
        labelK.set(pin - 1000 >= LABEL.on ? 1 : 0);
        scheduleOnRN(activateClock, false);
        return;
      }
      if (pin >= 0) {
        ambient.set(pin % LOOP);
        look.set(lookAt(pin % LOOP));
        labelK.set(labelAt(pin % LOOP));
        labelOn.set(labelAt(pin % LOOP) > 0.5);
        scheduleOnRN(activateClock, false);
        return;
      }
      const now = ambient.get() + dt;
      if (reduce) {
        const pr = press.get();
        if (Math.abs(look.get().press - pr) > 0.001) look.set({ ...REST, p: target.get(), press: pr });
        scheduleOnRN(activateClock, false);
        return;
      }
      if (mode.get() === 0) {
        const t = (loopT.get() + dt) % LOOP;
        loopT.set(t);
        ambient.set(t + ambOff.get());
        const loopLook = lookAt(t);
        look.set(loopLook);
        pLive.set(loopLook.p);
        const lk = labelAt(t);
        labelK.set(lk);
        labelOn.set(lk > 0.5);
        return;
      }
      // the finger's state
      const tc = touch.get();
      let p = pLive.get();
      const prev = lastP.get();
      // nothing moves and no loop plays: the canvas stays still, the frame callback costs nothing
      const moving =
        tc.down ||
        resuming.get() ||
        Math.abs(p - prev) > 0.00005 ||
        Math.abs(settle.get() - 1) > 0.001 ||
        glint.get() > 0.001 ||
        discGlint.get() > 0.001 ||
        Math.abs(press.get()) > 0.001 ||
        flash.get() > 0.001 ||
        Math.abs(tug.get()) > 0.001 ||
        Math.abs(tugSide.get()) > 0.001 ||
        Math.abs(dimpleK.get()) > 0.001 ||
        fingerK.get() > 0.001 ||
        Math.abs(stretch.get()) > 0.001;
      if (!moving && !(autoPlay && sealedProp === undefined)) {
        if (lastAir.get() > 0) {
          lastAir.set(-1);
          scheduleOnRN(hAir, null);
        }
        scheduleOnRN(activateClock, false);
        return;
      }
      ambient.set(now);
      if (tc.down) {
        if (tc.kind === 0 && tc.onTile) {
          const rate = tc.dir > 0 ? 1000 / holdMs : 1000 / UNSEAL_MS;
          p = Math.min(1, Math.max(0, p + tc.dir * rate * dt));
          pLive.set(p);
        } else if (tc.kind === 1) {
          if (!tc.ready) {
            // Anchor at the completed drop; subsequent pointer travel is exactly 1:1 with no catch-up lag.
            if (p >= SQUEEGEE.base - 0.005) {
              const at = finger.get();
              const g = geom.get();
              const off = p - fingerP(at.x - g.hw, at.y - g.hh, g.hw, g.hh);
              touch.set({ ...tc, ready: true, off });
              sqTarget.set(p);
            }
          } else {
            p = Math.min(1, Math.max(SQUEEGEE.base, sqTarget.get()));
            pLive.set(p);
          }
        }
      }
      // the speed the air is pressed out at (p/s), and what it leaves in the creases
      const v = dt > 0 ? (p - prev) / dt : 0;
      velocity.set((v + velocity1.get() + velocity2.get()) / 3);
      velocity2.set(velocity1.get());
      velocity1.set(v);
      if (tc.down && tc.kind !== 2 && p > SQUEEGEE.base && p < 0.95) {
        const sp = Math.abs(velocity.get());
        if (sp > peakSpeed.get()) {
          peakSpeed.set(sp);
          amp.set(creaseAmpOf(sp));
          extra.set(sp > SQUEEGEE.flick ? 1 : 0);
        }
      }
      // haptics and sound at the creases, the lock and the sheet leaving
      if (tc.down && tc.kind !== 2) {
        for (let i = 0; i < TICKS.length; i++) {
          const k = TICKS[i];
          if ((prev < k && p >= k) || (prev > k && p <= k)) scheduleOnRN(hDetent, i === TICKS.length - 1);
        }
        if (p > 0.02 && p < 0.985 && Math.abs(velocity.get()) > 0.05) {
          if (now - lastAir.get() > 0.07) {
            lastAir.set(now);
            scheduleOnRN(hAir, AIR_RATE.min + (AIR_RATE.max - AIR_RATE.min) * Math.min(1, Math.max(0, p)));
          }
        } else if (lastAir.get() > 0) {
          lastAir.set(-1);
          scheduleOnRN(hAir, null);
        }
      } else if (lastAir.get() > 0) {
        lastAir.set(-1);
        scheduleOnRN(hAir, null);
      }
      syncLabel(p);
      if (p < 0.9) lockArmed.set(true);
      if (lockArmed.get() && p >= 0.999) {
        lockArmed.set(false);
        if (userDriven.get()) scheduleOnRN(hLock);
        scheduleOnRN(announce, true);
        const boost = 1 + 0.6 * extra.get();
        settle.set(withSequence(withTiming(1.03, { duration: 60 }), withSpring(1, { damping: 12, stiffness: 120, mass: 1 })));
        glint.set(withSequence(withTiming(boost, { duration: 70 }), withTiming(0, { duration: 260 })));
        discGlint.set(withDelay(60, withSequence(withTiming(1, { duration: 40 }), withTiming(0, { duration: 80 }))));
        tug.set(withSequence(withTiming(1, { duration: 70 }), withSpring(0, SPRING.tug)));
        tugSide.set(withDelay(40, withSequence(withTiming(1, { duration: 70 }), withSpring(0, SPRING.tug))));
      }
      if (prev > 0.001 && p <= 0.001) {
        if (userDriven.get()) scheduleOnRN(hAirBack);
        scheduleOnRN(announce, false);
        // a new crease layout and a fresh amplitude for the next seal
        peakSpeed.set(0);
        amp.set(1);
        extra.set(0);
        if (!seedFixed) seed.set((seed.get() + 1) % 3);
      }
      lastP.set(p);
      look.set({
        p,
        press: press.get(),
        flash: flash.get(),
        puff: 0,
        settle: settle.get(),
        glint: glint.get(),
        sweep: -1,
        sweepK: 0,
        tug: tug.get(),
        tugSide: tugSide.get(),
        discGlint: discGlint.get(),
      });
      // the loop comes back 2 s after the last touch: the film eases to rest, then the timeline restarts at 0
      if (
        autoPlay &&
        sealedProp === undefined &&
        !tc.down &&
        now - lastTouch.get() > RESUME_AFTER &&
        dimpleK.get() < 0.01 &&
        fingerK.get() < 0.01
      ) {
        if (!resuming.get()) {
          resuming.set(true);
          userDriven.set(false);
          target.set(1);
          if (p < 0.999) pLive.set(withSpring(1, SPRING.glide));
        } else if (Math.abs(p - 1) < 0.002) {
          resuming.set(false);
          loopT.set(0);
          ambOff.set(now % LOOP);
          seed.set(normalizedSeed);
          mode.set(0);
        }
      }
    },
    !disabled && !reduce && autoPlay && sealedProp === undefined,
  );

  useEffect(() => {
    frameRef.current = frameClock;
    return () => {
      frameClock.setActive(false);
      frameRef.current = null;
    };
  }, [frameClock]);

  // Start the demo clock only after its snapshot/fonts are ready; also honor later autoplay changes.
  useEffect(() => {
    const play = autoPlay && sealedProp === undefined && !reduce && !disabled && !touch.get().down;
    mode.set(play ? 0 : 1);
    if (play) loopT.set(0);
    activateClock(play || (!disabled && !reduce && touch.get().down));
  }, [autoPlay, sealedProp, reduce, disabled, touch, mode, loopT, activateClock]);

  // External progress still redraws the material while the frame clock sleeps.
  useAnimatedReaction(
    () => pLive.get(),
    (p) => {
      if (mode.get() === 1 && !touch.get().down && pinned.get() < 0) {
        look.set({ ...look.get(), p: reduce ? 1 : Math.min(1, Math.max(0, p)) });
        syncLabel(Math.min(1, Math.max(0, p)));
        if (reduce) fade.set(withTiming(p >= commitAt ? 1 : 0, { duration: 200 }));
        if (p <= 0.001 || p >= 0.999) {
          const on = p >= 0.999;
          target.set(on ? 1 : 0);
          if (sealedProp !== undefined || notified.get() !== on) scheduleOnRN(announce, on);
        }
      }
    },
  );

  // ---- touches
  const takeOver = () => {
    'worklet';
    userDriven.set(true);
    velocity1.set(0);
    velocity2.set(0);
    velocity.set(0);
    scheduleOnRN(activateClock, !reduce);
    if (mode.get() === 0) {
      const l = look.get();
      pLive.set(l.p);
      lastP.set(l.p);
      target.set(l.p >= 0.5 ? 1 : 0);
      lockArmed.set(l.p < 0.9);
      mode.set(1);
    }
    resuming.set(false);
  };
  const toggleReduced = () => {
    'worklet';
    const next = target.get() > 0.5 ? 0 : 1;
    target.set(next);
    pLive.set(next);
    fade.set(withTiming(next, { duration: 200 }));
    scheduleOnRN(next === 1 ? hLock : hAirBack);
    scheduleOnRN(announce, next === 1);
  };
  const holdW = holdSize.w;
  const holdH = holdSize.h;
  const startHold = () => {
    'worklet';
    if (pinned.get() >= 0 || disabled) return;
    holdActive.set(true);
    takeOver();
    press.set(withSpring(1, SPRING.press));
    flash.set(withSequence(withTiming(1, { duration: 90 }), withTiming(0, { duration: 320 })));
    scheduleOnRN(hOnset);
    if (reduce) return;
    const p = pLive.get();
    touch.set({ down: true, kind: 0, onTile: true, t0: ambient.get(), p0: p, dir: target.get() > 0.5 ? -1 : 1, off: 0, ready: true });
    pLive.set(p); // stop any running spring where it is
  };
  const updateHold = (x: number, y: number) => {
    'worklet';
    const tc = touch.get();
    if (!tc.down || tc.kind !== 0) return;
    const slop = 16;
    const on = x > -slop && y > -slop && x < holdW + slop && y < holdH + slop;
    if (on !== tc.onTile) {
      touch.set({ ...tc, onTile: on });
      press.set(withSpring(on ? 1 : 0.45, SPRING.press));
    }
  };
  const finishHold = () => {
    'worklet';
    if (!holdActive.get()) return;
    holdActive.set(false);
    press.set(withSpring(0, SPRING.press));
    lastTouch.set(ambient.get());
    if (reduce) {
      toggleReduced();
      return;
    }
    const tc = touch.get();
    if (!tc.down || tc.kind !== 0) return;
    touch.set({ ...tc, down: false });
    const p = pLive.get();
    const dur = ambient.get() - tc.t0;
    const sealing = tc.dir > 0;
    if (dur < TAP_MAX && Math.abs(p - tc.p0) < 0.2) {
      // a tap runs the whole change by itself, at the hold's pace
      const next = sealing ? 1 : 0;
      target.set(next);
      const ms = Math.abs(next - p) * (sealing ? holdMs : UNSEAL_MS);
      pLive.set(withTiming(next, { duration: ms, easing: Easing.bezier(0.4, 0, 0.3, 1) }));
      return;
    }
    const rate = sealing ? 1000 / holdMs : 1000 / UNSEAL_MS;
    const commit = sealing ? p >= commitAt : p < commitAt;
    const next = commit === sealing ? 1 : 0;
    target.set(next);
    if (next === 1) pLive.set(withSpring(1, { ...SPRING.complete, velocity: sealing ? rate : 0 }));
    else pLive.set(withSpring(0, { ...SPRING.revert, velocity: sealing ? 0 : -rate }));
  };
  // RNGH registers the callback; the ref is read later on JS by scheduleOnRN, never during render.
  const hold = Gesture.Pan()
    .minDistance(0)
    // eslint-disable-next-line react-hooks/refs
    .onBegin(startHold)
    .onUpdate((e) => updateHold(e.x, e.y))
    .onFinalize(finishHold);
  // Use either this adapter or `hold` on a target, never both.
  const holdProps: GestureResponderHandlers = {
    onStartShouldSetResponder: () => true,
    onResponderGrant: () => startHold(),
    onResponderMove: (e) => updateHold(e.nativeEvent.locationX, e.nativeEvent.locationY),
    onResponderRelease: () => finishHold(),
    onResponderTerminate: () => finishHold(),
    onResponderTerminationRequest: () => false,
  };

  // the finger on the film: squeegees the air out where it is, drops the sheet on a bare card, peels a sealed film from
  // the bottom or right edge, or dents it. e.x / e.y are in pt from the film's top left corner.
  const squeegee = Gesture.Pan()
    .minDistance(0)
    .enabled(!disabled && gesture === 'squeegee')
    // RNGH registers this callback; activateClock reads the ref only after an actual gesture.
    // eslint-disable-next-line react-hooks/refs
    .onBegin((e) => {
      if (pinned.get() >= 0 || reduce || disabled) return;
      takeOver();
      const g = geom.get();
      const qx = e.x / g.S - g.hw;
      const qy = e.y / g.S - g.hh;
      const p0 = pLive.get();
      lastTouch.set(ambient.get());
      if (p0 >= 0.95) {
        const nearEdge = e.y / g.S > 2 * g.hh - SQUEEGEE.edge || e.x / g.S > 2 * g.hw - SQUEEGEE.edge;
        if (!nearEdge) {
          touch.set({ ...NO_TOUCH, down: true, kind: 2 });
          dimple.set({ x: g.hw + qx, y: g.hh + qy });
          dimpleK.set(withSpring(1, SPRING.dimple));
          scheduleOnRN(hDimple);
          return;
        }
        // peel: the same map, reversed, continuing from where the film is now
        const off = 1 - fingerP(qx, qy, g.hw, g.hh);
        sqTarget.set(1);
        touch.set({ ...NO_TOUCH, down: true, kind: 1, p0, off, ready: true, t0: ambient.get() });
        finger.set({ x: g.hw + qx, y: g.hh + qy });
        fingerK.set(withTiming(1, { duration: 90 }));
        scheduleOnRN(hOnset);
        return;
      }
      peakSpeed.set(0);
      amp.set(1);
      extra.set(0);
      const ready = p0 >= SQUEEGEE.base - 0.005;
      if (!ready) pLive.set(withSpring(SQUEEGEE.base, SPRING.drop));
      target.set(1);
      const mapped = fingerP(qx, qy, g.hw, g.hh);
      const off = ready ? p0 - mapped : 0;
      touch.set({ ...NO_TOUCH, down: true, kind: 1, p0, off, ready, t0: ambient.get() });
      sqTarget.set(ready ? p0 : mapped);
      finger.set({ x: g.hw + qx, y: g.hh + qy });
      fingerK.set(withTiming(1, { duration: 90 }));
      scheduleOnRN(hOnset);
    })
    .onUpdate((e) => {
      const tc = touch.get();
      if (!tc.down) return;
      const g = geom.get();
      const qx = e.x / g.S - g.hw;
      const qy = e.y / g.S - g.hh;
      if (tc.kind === 2) {
        dimple.set({ x: g.hw + qx, y: g.hh + qy });
        return;
      }
      if (tc.kind !== 1) return;
      const mapped = Math.min(1, Math.max(SQUEEGEE.base, fingerP(qx, qy, g.hw, g.hh) + tc.off));
      sqTarget.set(mapped);
      if (tc.ready) pLive.set(mapped);
      finger.set({ x: g.hw + Math.min(qx, g.hw), y: g.hh + Math.min(qy, g.hh) });
      // pulled past the bottom edge: the lip stretches with the finger
      stretch.set(Math.min(SQUEEGEE.stretchMax, Math.max(0, qy - g.hh) * SQUEEGEE.stretch));
    })
    .onFinalize(() => {
      lastTouch.set(ambient.get());
      const tc = touch.get();
      if (!tc.down) return;
      touch.set({ ...tc, down: false });
      if (tc.kind === 2) {
        dimpleK.set(withSpring(0, SPRING.dimple));
        return;
      }
      if (tc.kind !== 1) return;
      fingerK.set(withTiming(0, { duration: 160 }));
      stretch.set(withSpring(0, SPRING.tug));
      const p = pLive.get();
      const vp = velocity.get();
      // the release: where the air is heading decides whether the film seals or lets go
      const next = p + SQUEEGEE.commitLead * vp >= commitAt ? 1 : 0;
      target.set(next);
      if (next === 1) pLive.set(withSpring(1, { ...SPRING.complete, velocity: vp }));
      else pLive.set(withSpring(0, { ...SPRING.revert, velocity: vp }));
    });

  /** VoiceOver / TalkBack: the reduced sequence, at once */
  const toggle = useCallback(
    (next: boolean) => {
      target.set(next ? 1 : 0);
      activateClock(!reduce);
      if (reduce) {
        pLive.set(next ? 1 : 0);
        fade.set(withTiming(next ? 1 : 0, { duration: 200 }));
      } else {
        mode.set(1);
        pLive.set(withTiming(next ? 1 : 0, { duration: 220 }));
      }
      lastTouch.set(ambient.get());
      announce(next);
    },
    [target, fade, reduce, mode, pLive, lastTouch, ambient, announce, activateClock],
  );

  return {
    /** the film's state: read it or drive it (while no loop plays) */
    progress: pLive,
    /** gesture for the shield tile (hold seals, slide off pauses, tap runs it) */
    hold,
    holdProps,
    autoPlay,
    /** gesture for the film itself (squeegee, dimple, peel) */
    squeegee,
    sealed,
    /** the tile's label and shield, 0 "Protect Card" … 1 "Protected": a timed morph when the state changes */
    label: labelK,
    toggle,
    reduce,
    // for <VacuumSeal> and the screen around it
    look,
    ambient,
    geom,
    film: { look, ambient, dimple, dimpleK, finger, fingerK, stretch, fade, seed, amp, extra, tilt },
  };
}

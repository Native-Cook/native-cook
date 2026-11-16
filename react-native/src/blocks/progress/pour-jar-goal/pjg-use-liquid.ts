import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { useAnimatedReaction, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { clamp, impulse, LOOP, LOOP_FIELDS, makeField, PHYSICS, rubber, segment, springAt, stepField, timeline } from './pjg-motion';
import { makeHaptics, type Cue } from './pjg-haptics';
import type { LiquidGaugeProProps } from './pjg-types';
import { volumeToY, yToVolume, type VesselTable } from './pjg-vessel';

class FrameSwitch {
  private clock: { setActive: (active: boolean) => void } | null = null;
  attach(clock: { setActive: (active: boolean) => void } | null) {
    this.clock = clock;
  }
  active(value: boolean) {
    this.clock?.setActive(value);
  }
}

type SoundCue = (cue: 'pour' | 'land' | 'flow' | 'tail' | 'stop', level: number, gain?: number) => void;
export function useLiquid(
  props: LiquidGaugeProProps,
  table: VesselTable,
  scaleX: number,
  scaleY: number,
  reduced: boolean,
  soundCue: SoundCue,
) {
  const {
    min = 0,
    max,
    step = 10,
    autoPlay = false,
    editable = true,
    allowDecrease = false,
    viscosity = 3.2,
    undoSeconds = 4,
    onChange,
    onCommit,
    onTick,
    level: externalLevel,
  } = props;
  const range = Math.max(1, max - min),
    initial = clamp(props.value ?? props.defaultValue ?? 2240, min, max);
  const base = useSharedValue(initial),
    amount = useSharedValue(initial),
    target = useSharedValue(initial);
  const displayAmount = useSharedValue(initial),
    statAmount = useSharedValue(initial),
    percentAmount = useSharedValue(initial);
  const field = useSharedValue(makeField()),
    heights = useSharedValue(LOOP_FIELDS[0]);
  const mode = useSharedValue(autoPlay && !reduced ? 0 : 1); // 0 timeline, 1 live, 2 pin
  const time = useSharedValue(0),
    cycleStart = useSharedValue(-1),
    pin = useSharedValue(-1);
  const touching = useSharedValue(0),
    finger = useSharedValue(table.width / 2),
    fingerY = useSharedValue(volumeToY((initial - min) / range, table));
  const secondFingerY = useSharedValue(0);
  const secondFinger = useSharedValue(-1000),
    age = useSharedValue(10),
    liveTime = useSharedValue(0);
  const toast = useSharedValue(0),
    toastAge = useSharedValue(100),
    delta = useSharedValue(0),
    undoValue = useSharedValue(initial);
  const grip = useSharedValue(0),
    stream = useSharedValue(0),
    impact = useSharedValue(0),
    bubbleTime = useSharedValue(0);
  const dropOriginY = useSharedValue(0),
    dropX = useSharedValue(table.width / 2);
  const dropY = useSharedValue(-100),
    dropRadius = useSharedValue(0),
    dropAge = useSharedValue(100),
    dropSpeed = useSharedValue(0);
  const pouring = useSharedValue(0),
    pourAge = useSharedValue(0),
    accumulator = useSharedValue(0);
  const lastNotify = useSharedValue(-10),
    lastTick = useSharedValue(Math.floor(((initial - min) / range) * 4));
  const lastStep = useSharedValue(Math.round(initial / step)),
    limited = useSharedValue(0),
    cancelled = useSharedValue(0);
  const committed = useSharedValue(initial),
    returnFrom = useSharedValue(initial);
  const [accessibleValue, setAccessibleValue] = useState(initial);
  const [undoUntil, setUndoUntil] = useState(0);
  const userCommitTime = useSharedValue(-100);
  useEffect(() => {
    if (!undoUntil) return;
    const timer = setTimeout(() => setUndoUntil(0), Math.max(0, undoUntil - Date.now()));
    return () => clearTimeout(timer);
  }, [undoUntil]);
  const haptic = useMemo(() => makeHaptics(), []);
  const notify = useCallback((v: number) => onChange?.(v), [onChange]);
  const finish = useCallback(
    (v: number, d: number) => {
      setAccessibleValue(v);
      onCommit?.(v, d);
      if (d) {
        setUndoUntil(Date.now() + undoSeconds * 1000);
      }
    },
    [onCommit, undoSeconds],
  );
  const tickCallback = useCallback(
    (fraction: number) => {
      onTick?.(fraction);
      haptic('detent');
    },
    [haptic, onTick],
  );
  const cue = useCallback((c: Cue) => haptic(c), [haptic]);
  const startRate = props.holdToPour?.startRate ?? 40,
    maxRate = props.holdToPour?.maxRate ?? 400,
    rampSeconds = props.holdToPour?.rampSeconds ?? 1.5;
  const controlled = props.value !== undefined;
  const loopFields = useMemo(() => LOOP_FIELDS, []);
  const ticks = props.ticks ?? [0.25, 0.5, 0.75, 1];

  const control = useMemo(() => new FrameSwitch(), []);
  const wake = useCallback(() => control.active(true), [control]);
  const sleep = useCallback(() => control.active(false), [control]);
  useAnimatedReaction(
    () => externalLevel?.get() ?? -1,
    (level, previous) => {
      if (externalLevel && level !== previous) {
        mode.set(1);
        target.set(min + clamp(level) * range);
        age.set(0);
        scheduleOnRN(wake);
      }
    },
  );
  const begin = useCallback(() => {
    'worklet';
    if (mode.get() !== 1) {
      field.modify((f) => {
        const h = heights.get();
        f.n = 64;
        for (let i = 0; i < 64; i++) {
          f.h[i] = h[i];
          f.output[i] = h[i];
          f.v[i] = 0;
        }
        return f;
      });
    }
    mode.set(1);
    target.set(amount.get());
    committed.set(base.get());
    age.set(0);
    cancelled.set(0);
    limited.set(0);
    pin.set(-1);
    toastAge.set(100);
    scheduleOnRN(wake);
    scheduleOnRN(cue, 'onset');
  }, [age, amount, base, cancelled, committed, cue, field, heights, limited, mode, pin, target, toastAge, wake]);
  const commit = useCallback(() => {
    'worklet';
    const previous = committed.get();
    const saved = clamp(min + Math.round((amount.get() - min) / step) * step, allowDecrease ? min : previous, max);
    userCommitTime.set(performance.now() / 1000);
    target.set(saved);
    delta.set(saved - previous);
    undoValue.set(previous);
    touching.set(0);
    pouring.set(0);
    age.set(0);
    toastAge.set(saved !== previous ? 0 : 100);
    if (!controlled) base.set(saved);
    scheduleOnRN(finish, saved, saved - previous);
  }, [
    age,
    allowDecrease,
    amount,
    base,
    committed,
    controlled,
    delta,
    finish,
    max,
    min,
    pouring,
    step,
    target,
    toastAge,
    touching,
    undoValue,
    userCommitTime,
  ]);
  const cancel = useCallback(() => {
    'worklet';
    target.set(committed.get());
    cancelled.set(1);
    touching.set(0);
    pouring.set(0);
    age.set(0);
    secondFinger.set(-1000);
    scheduleOnRN(soundCue, 'stop', 0);
  }, [soundCue, age, cancelled, committed, pouring, secondFinger, target, touching]);
  const updateFinger = useCallback(
    (x: number, y: number) => {
      'worklet';
      finger.set(clamp(x / scaleX, 0, table.width));
      fingerY.set(rubber(y / scaleY, 8, table.height - 8));
      const v = min + yToVolume(fingerY.get(), table) * range;
      target.set(rubber(v, allowDecrease ? min : committed.get(), max));
    },
    [allowDecrease, committed, finger, fingerY, max, min, range, scaleX, scaleY, table, target],
  );

  const drag = useMemo(
    () =>
      Gesture.Pan()
        .enabled(editable && !externalLevel)
        .minDistance(0)
        .onBegin((e) => {
          begin();
          touching.set(1);
          updateFinger(e.x, e.y);
        })
        .onTouchesMove((e) => {
          if (cancelled.get()) return;
          if (e.allTouches.length > 1) {
            secondFinger.set(clamp(e.allTouches[1].x / scaleX, 0, table.width));
            secondFingerY.set(e.allTouches[1].y / scaleY);
            updateFinger(e.allTouches[0].x, e.allTouches[0].y);
          } else secondFinger.set(-1000);
        })
        .onUpdate((e) => {
          if (e.x < -24 || e.x > table.width * scaleX + 24) {
            cancel();
            return;
          }
          if (!cancelled.get() && e.numberOfPointers === 1) updateFinger(e.x, e.y);
        })
        .onEnd((e) => {
          if (cancelled.get()) return;
          if (!reduced)
            field.modify((f) => {
              impulse(f, finger.get(), -e.velocityY / scaleY, table.width - 6);
              return f;
            });
          if (-e.velocityY / scaleY > PHYSICS.skipThreshold && !reduced) {
            dropOriginY.set(fingerY.get());
            dropX.set(finger.get());
            dropAge.set(0);
            dropSpeed.set(
              Math.min(440, Math.sqrt(4 * 1200 * Math.max(0, Math.min(40, fingerY.get() - 10)))) *
                clamp(-e.velocityY / scaleY / 2200, 0.5, 1),
            );
          }
          secondFinger.set(-1000);
          commit();
        })
        .onFinalize((_e, success) => {
          if (!success) cancel();
        }),
    [
      begin,
      cancel,
      cancelled,
      commit,
      dropAge,
      dropOriginY,
      dropX,
      dropSpeed,
      editable,
      externalLevel,
      field,
      finger,
      fingerY,
      reduced,
      scaleX,
      scaleY,
      secondFinger,
      secondFingerY,
      table,
      touching,
      updateFinger,
    ],
  );

  const pour = useMemo(
    () =>
      Gesture.Pan()
        .enabled(editable && !externalLevel)
        .minDistance(0)
        .onBegin(() => {
          begin();
          touching.set(2);
          pouring.set(1);
          pourAge.set(0);
          scheduleOnRN(soundCue, 'pour', clamp((amount.get() - min) / range), 0.6);
        })
        .onEnd(() => {
          const held = pourAge.get();
          pouring.set(0);
          if (held < 0.3) {
            scheduleOnRN(soundCue, 'tail', clamp((amount.get() - min) / range));
            target.set(committed.get());
            // A tap's 300 ms drop / 700 ms rise is driven on the UI thread below.
            pourAge.set(-0.001);
            touching.set(3);
          } else {
            scheduleOnRN(soundCue, 'stop', clamp((amount.get() - min) / range));
            commit();
          }
        })
        .onFinalize((_e, success) => {
          if (!success) cancel();
        }),
    [amount, begin, cancel, commit, committed, editable, externalLevel, min, pourAge, pouring, range, soundCue, target, touching],
  );

  const clock = useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.667) / 1000, 1 / 30);
    if (mode.get() !== 1) {
      if (cycleStart.get() < 0) cycleStart.set(frame.timestamp / 1000 - time.get());
      const t = reduced ? 0 : mode.get() === 2 ? pin.get() : (frame.timestamp / 1000 - cycleStart.get()) % LOOP;
      const k = timeline(t);
      time.set(t);
      const b = base.get(),
        add = Math.min(step * 10, max - b);
      amount.set(b + add * k.lift);
      displayAmount.set(b + add * timeline(t - 0.02).lift);
      statAmount.set(b + add * timeline(t - 0.04).lift);
      percentAmount.set(b + add * timeline(t - 0.08).lift);
      heights.set(reduced ? loopFields[0] : loopFields[Math.min(960, Math.round(k.t * 120))]);
      grip.set(k.grip);
      const userAge = mode.get() === 2 ? 100 : performance.now() / 1000 - userCommitTime.get();
      toast.set(userAge < undoSeconds ? clamp(springAt(userAge - 0.35)) : k.toast);
      if (userAge >= undoSeconds) delta.set(add);
      impact.set(k.impact);
      bubbleTime.set(k.bubbleTime);
      if (mode.get() === 2) scheduleOnRN(sleep);
      return;
    }
    if (touching.get() === 3) {
      const previous = pourAge.get();
      pourAge.set(previous + dt);
      const t = pourAge.get(),
        initialTarget = Math.min(max, committed.get() + step * 10);
      target.set(committed.get() + (initialTarget - committed.get()) * segment(t, 0.3, 1));
      finger.set(table.width / 2);
      dropX.set(table.width / 2);
      fingerY.set(volumeToY((amount.get() - min) / range, table));
      dropY.set(10 + (fingerY.get() - 10) * clamp((t - 0.06) / 0.24) ** 2);
      dropRadius.set(t > 0.06 && t < 0.3 && !reduced ? 4 : 0);
      if (t >= 0.3 && previous < 0.3) {
        if (!reduced)
          field.modify((f) => {
            impulse(f, table.width / 2, 620, table.width - 6);
            return f;
          });
        impact.set(1);
        scheduleOnRN(cue, 'impact');
        scheduleOnRN(soundCue, 'land', clamp((amount.get() - min) / range));
      }
      if (t >= (reduced ? 0.3 : 1.1)) {
        amount.set(initialTarget);
        commit();
      }
    }
    const isTouched = touching.get() > 0;
    if (isTouched) age.set(0);
    else age.set(age.get() + dt);
    liveTime.set(liveTime.get() + dt);
    if (externalLevel && !isTouched && !pouring.get()) target.set(min + clamp(externalLevel.get()) * range);
    if (pouring.get()) {
      pourAge.set(pourAge.get() + dt);
      if (pourAge.get() > 0.3) {
        const rate = startRate + (maxRate - startRate) * segment(pourAge.get() - 0.3, 0, Math.max(rampSeconds, 0.01));
        target.set(clamp(target.get() + rate * dt, min, max));
        stream.set(reduced ? 0 : 3 + 6 * clamp(rate / Math.max(1, maxRate)));
      }
    } else stream.set(stream.get() * Math.exp(-dt * 24));
    const desired = target.get();
    amount.set(amount.get() + (desired - amount.get()) * (1 - Math.exp(-dt / (reduced ? 0.07 : PHYSICS.lag))));
    displayAmount.set(reduced ? amount.get() : displayAmount.get() + (amount.get() - displayAmount.get()) * (1 - Math.exp(-dt / 0.02)));
    statAmount.set(statAmount.get() + (amount.get() - statAmount.get()) * (1 - Math.exp(-dt / 0.04)));
    percentAmount.set(percentAmount.get() + (amount.get() - percentAmount.get()) * (1 - Math.exp(-dt / 0.08)));
    grip.set(grip.get() + ((isTouched && touching.get() === 1 ? 1 : 0) - grip.get()) * (1 - Math.exp(-dt * 32)));
    accumulator.set(Math.min(1 / 15, accumulator.get() + dt));
    if (!reduced) {
      const centerY = volumeToY((amount.get() - min) / range, table);
      const mound = touching.get() === 1 ? clamp(centerY - fingerY.get(), -48, 48) : 0;
      const mound2 = touching.get() === 1 && secondFinger.get() > -100 ? clamp(centerY - secondFingerY.get(), -48, 48) : 0;
      const row = clamp(Math.floor((centerY / table.height) * 128), 0, 127);
      const left = table.left[row],
        surfaceWidth = Math.max(8, table.right[row] - left - 6);
      const columns = Math.round(32 + clamp(surfaceWidth / Math.max(1, table.width - 6)) * 32);
      let steps = Math.floor(accumulator.get() * 120);
      field.modify((f) => {
        while (steps-- > 0)
          stepField(f, 1 / 120, surfaceWidth, viscosity, finger.get() - left, mound, secondFinger.get() - left, columns, mound2);
        return f;
      });
      accumulator.set(accumulator.get() % (1 / 120));
      heights.modify(() => field.get().output, true);
      if (field.get().energy > 0.5 || isTouched || pouring.get()) bubbleTime.set(bubbleTime.get() + dt);
    } else heights.set(loopFields[0]);
    toastAge.set(toastAge.get() + dt);
    const ta = toastAge.get();
    const successTime = reduced ? 0.01 : 0.35;
    if (ta >= successTime && ta - dt < successTime && delta.get() !== 0) scheduleOnRN(cue, 'success');
    toast.set(clamp(reduced ? ta / 0.15 : springAt(ta - 0.35)) * (1 - segment(ta, undoSeconds || 2.2, (undoSeconds || 2.2) + 0.34)));
    dropAge.set(dropAge.get() + dt);
    const da = dropAge.get();
    if (touching.get() === 3) {
      /* tap path above owns the drop */
    } else if (da < 0.7 && !reduced) {
      const y = dropOriginY.get() - dropSpeed.get() * da + 1200 * da * da;
      const col = clamp(Math.round((dropX.get() / table.width) * 63), 0, 63);
      const surface = volumeToY((amount.get() - min) / range, table) - heights.get()[col];
      dropY.set(clamp(y, 10, table.height - 10));
      dropRadius.set(y < surface ? 4 : 0);
      if (da > 0.05 && y >= surface && dropRadius.get() === 0 && dropSpeed.get() > 0) {
        field.modify((f) => {
          impulse(f, dropX.get(), 240, table.width - 6);
          return f;
        });
        impact.set(1);
        dropSpeed.set(0);
        scheduleOnRN(soundCue, 'land', clamp((amount.get() - min) / range));
      }
    } else dropRadius.set(0);
    impact.set(impact.get() * Math.exp(-dt * 9));
    // JS bridge only at 10 Hz while touching, never once per rendered frame.
    if ((isTouched || pouring.get()) && liveTime.get() - lastNotify.get() >= PHYSICS.callbackInterval) {
      lastNotify.set(liveTime.get());
      const snapped = clamp(min + Math.round((amount.get() - min) / step) * step, min, max);
      scheduleOnRN(notify, snapped);
      if (pouring.get()) scheduleOnRN(soundCue, 'flow', clamp((amount.get() - min) / range), clamp(stream.get() / 9) * 0.6);
      if (Math.round(snapped / step) !== lastStep.get()) {
        lastStep.set(Math.round(snapped / step));
        scheduleOnRN(cue, 'detent');
      }
      const fraction = (snapped - min) / range;
      for (let i = 0; i < ticks.length; i++) {
        const crossed = fraction >= ticks[i] && lastTick.get() < i;
        if (crossed) scheduleOnRN(tickCallback, ticks[i]);
      }
      let newTick = -1;
      for (let i = 0; i < ticks.length; i++) if (fraction >= ticks[i]) newTick = i;
      lastTick.set(newTick);
      if (desired >= max && !limited.get()) {
        limited.set(1);
        scheduleOnRN(cue, 'limit');
      }
    }
    if (!isTouched && !pouring.get() && age.get() > PHYSICS.resumeDelay && autoPlay && !reduced) {
      if (age.get() - dt <= PHYSICS.resumeDelay) returnFrom.set(amount.get());
      const r = segment(age.get(), PHYSICS.resumeDelay, PHYSICS.resumeDelay + PHYSICS.returnSeconds);
      amount.set(returnFrom.get() + (base.get() - returnFrom.get()) * r);
      if (r >= 1) {
        mode.set(0);
        time.set(0);
        cycleStart.set(-1);
        toast.set(0);
        bubbleTime.set(0);
      }
    }
    if (
      !autoPlay &&
      !isTouched &&
      !pouring.get() &&
      age.get() > Math.max(undoSeconds + 0.5, 2.7) &&
      field.get().energy < 0.5 &&
      Math.abs(amount.get() - target.get()) < 0.05
    )
      scheduleOnRN(sleep);
  }, false);
  useEffect(() => {
    control.attach(clock);
    return () => {
      clock.setActive(false);
      control.attach(null);
    };
  }, [clock, control]);

  const undo = useCallback(() => {
    if (undoUntil < Date.now() || undoSeconds === 0) return;
    const previous = undoValue.get(),
      previousBase = base.get();
    mode.set(1);
    userCommitTime.set(-100);
    setUndoUntil(0);
    target.set(previous);
    toastAge.set(100);
    age.set(0);
    if (!controlled) base.set(previous);
    setAccessibleValue(previous);
    onCommit?.(previous, previous - previousBase);
    haptic('undo');
    wake();
  }, [age, base, controlled, haptic, mode, onCommit, target, toastAge, undoSeconds, undoUntil, undoValue, userCommitTime, wake]);
  const adjust = useCallback(
    (direction: number, confirm = false) => {
      if (!editable || externalLevel) return;
      mode.set(1);
      pin.set(-1);
      age.set(0);
      committed.set(base.get());
      const next = clamp(target.get() + direction * step, allowDecrease ? min : base.get(), max);
      target.set(next);
      wake();
      if (confirm) {
        const d = next - base.get();
        if (!controlled) base.set(next);
        delta.set(d);
        undoValue.set(next - d);
        toastAge.set(0);
        userCommitTime.set(performance.now() / 1000);
        finish(next, d);
      } else {
        setAccessibleValue(next);
        onChange?.(next);
        haptic('detent');
      }
    },
    [
      age,
      allowDecrease,
      base,
      committed,
      controlled,
      delta,
      editable,
      externalLevel,
      finish,
      haptic,
      max,
      min,
      mode,
      onChange,
      pin,
      step,
      target,
      toastAge,
      undoValue,
      userCommitTime,
      wake,
    ],
  );

  useEffect(() => {
    if (props.value === undefined) return;
    const v = clamp(props.value, min, max);
    base.set(v);
    target.set(v);
    wake();
  }, [base, max, min, props.value, target, wake]);
  useEffect(() => {
    if ((autoPlay && !reduced) || externalLevel) wake();
    else {
      mode.set(1);
      if (reduced) heights.set(LOOP_FIELDS[0]);
    }
  }, [autoPlay, externalLevel, heights, mode, reduced, wake]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        cycleStart.set(-1);
        wake();
      } else {
        clock.setActive(false);
        soundCue('stop', 0);
      }
    });
    return () => {
      subscription.remove();
      clock.setActive(false);
    };
  }, [clock, cycleStart, soundCue, wake]);
  useEffect(() => {
    if (!__DEV__) return;
    const root = globalThis as typeof globalThis & { __pjg?: { pin: (t: number) => void; play: () => void } };
    const hook = {
      pin: (t: number) => {
        pin.set(clamp(t, 0, LOOP));
        mode.set(2);
        wake();
      },
      play: () => {
        pin.set(-1);
        time.set(0);
        mode.set(autoPlay && !reduced ? 0 : 1);
        cycleStart.set(-1);
        wake();
      },
    };
    root.__pjg = hook;
    return () => {
      if (root.__pjg === hook) delete root.__pjg;
    };
  }, [autoPlay, cycleStart, mode, pin, reduced, time, wake]);

  return {
    amount,
    displayAmount,
    statAmount,
    percentAmount,
    heights,
    time,
    toast,
    delta,
    grip,
    finger,
    fingerY,
    stream,
    impact,
    bubbleTime,
    dropY,
    dropX,
    dropRadius,
    drag,
    pour,
    undo,
    adjust,
    hasUndo: undoUntil > 0,
    accessibleValue: props.value ?? accessibleValue,
    mode,
    touching,
    age,
  };
}

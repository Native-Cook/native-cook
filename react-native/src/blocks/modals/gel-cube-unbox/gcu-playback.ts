import { useCallback, useEffect, useRef } from 'react';
import { AppState } from 'react-native';
import { cancelAnimation, type SharedValue, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { LOOP, MOTION, progressAt } from './gcu-motion';

/** JS-only timer ownership stays outside gesture worklets; the film clock stays on the UI thread. */
export function useGcuPlayback(
  enabled: boolean,
  defaultOpen: boolean,
  p: SharedValue<number>,
  t: SharedValue<number>,
  mode: SharedValue<number>,
  closing: SharedValue<boolean>,
  closeLead: SharedValue<number>,
) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const alive = useRef(true);
  const origin = useSharedValue(-1);
  const film = useFrameCallback((frame) => {
    if (mode.get() !== 0) return;
    if (origin.get() < 0) origin.set(frame.timestamp - t.get() * 1000);
    const time = ((frame.timestamp - origin.get()) / 1000) % LOOP;
    t.set(time);
    p.set(progressAt(time));
  }, false);
  const stopFilm = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    film.setActive(false);
  }, [film]);
  const restart = useCallback(() => {
    if (!alive.current || !enabled) return;
    t.set(0);
    origin.set(-1);
    mode.set(0);
    film.setActive(true);
  }, [enabled, film, mode, origin, t]);
  const afterTouch = useCallback(() => {
    if (!enabled) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      closing.set(true);
      closeLead.set(MOTION.fanLead);
      p.set(
        withTiming(0, { duration: MOTION.reset }, (finished) => {
          if (finished) {
            closing.set(false);
            scheduleOnRN(restart);
          }
        }),
      );
    }, MOTION.resume);
  }, [closeLead, closing, enabled, p, restart]);
  useEffect(() => {
    alive.current = true;
    if (enabled && !defaultOpen) restart();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active' && mode.get() === 0) {
        origin.set(-1);
        film.setActive(enabled);
      } else stopFilm();
    });
    return () => {
      alive.current = false;
      subscription.remove();
      stopFilm();
      cancelAnimation(p);
    };
  }, [defaultOpen, enabled, film, mode, origin, p, restart, stopFilm]);
  return { stopFilm, restart, afterTouch };
}

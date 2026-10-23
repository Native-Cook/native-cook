import { useCallback, useLayoutEffect, useRef } from 'react';

/** Only the latest enabled owner may resume after the final release. */
export function useResume(enabled: boolean, delay: number, resume: () => void) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const generation = useRef(0);
  const latest = useRef({ enabled, resume });
  useLayoutEffect(() => {
    latest.current = { enabled, resume };
  }, [enabled, resume]);

  const hold = useCallback(() => {
    generation.current += 1;
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }, []);
  const release = useCallback(() => {
    hold();
    if (!latest.current.enabled) return;
    const owner = generation.current;
    timer.current = setTimeout(() => {
      timer.current = null;
      if (owner !== generation.current || !latest.current.enabled) return;
      latest.current.resume();
    }, delay);
  }, [delay, hold]);

  useLayoutEffect(() => {
    hold();
    return hold;
  }, [enabled, delay, hold]);
  return { hold, release };
}

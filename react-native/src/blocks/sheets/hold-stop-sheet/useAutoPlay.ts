import { useEffect, useRef, useState } from 'react';

/** Seconds after the last touch before the loop starts again from rest. */
const USER_HOLD = 3;

export type Step = [at: number, run: () => void];

/**
 * Runs `steps` at absolute times from the start of every loop (`origin + n * loop + at`), so
 * nothing drifts. Returns `touched`: call it on any touch to hand the screen to the user; `rest`
 * puts the screen back at its start, and the loop begins again `USER_HOLD` seconds later.
 */
export function useAutoPlay(enabled: boolean, loop: number, steps: Step[], rest: () => void) {
  const [touches, setTouches] = useState(0);
  const latest = useRef({ steps, rest });
  useEffect(() => {
    latest.current = { steps, rest };
  });

  useEffect(() => {
    if (!enabled) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, ms)));
    const cycle = (origin: number) => {
      latest.current.steps.forEach(([at], index) => {
        after(origin + at * 1000 - Date.now(), () => latest.current.steps[index][1]());
      });
      after(origin + loop * 1000 - Date.now(), () => cycle(origin + loop * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        latest.current.rest();
        cycle(Date.now());
      });
    return () => timers.forEach(clearTimeout);
  }, [enabled, loop, touches]);

  return () => setTouches((n) => n + 1);
}

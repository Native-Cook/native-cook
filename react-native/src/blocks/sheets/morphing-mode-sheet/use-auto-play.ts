import { useEffect, useRef } from 'react';

export type Step = {
  /** Seconds from the start of the loop. */
  at: number;
  run: () => void;
};

type Options = {
  autoPlay: boolean;
  /** Seconds in one loop. */
  loop: number;
  /** What happens, and when. The last step puts everything back as it was at the start. */
  steps: Step[];
  /** Puts everything back at rest, at once. */
  rest: () => void;
  /** Changes on every touch; the loop stops for `hold` seconds after the last one. */
  touches: number;
  hold: number;
};

/**
 * Plays `steps` every `loop` seconds. Every step is timed from the start of its loop, never from
 * the step before it, so the loop does not drift; a touch cancels what is left of it.
 */
export function useAutoPlay({ autoPlay, loop, steps, rest, touches, hold }: Options) {
  // The timers read the latest steps when they fire.
  const latest = useRef({ steps, rest });
  useEffect(() => {
    latest.current = { steps, rest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, ms)));
    const cycle = (origin: number) => {
      latest.current.steps.forEach((step, index) => after(origin + step.at * 1000 - Date.now(), () => latest.current.steps[index].run()));
      after(origin + loop * 1000 - Date.now(), () => cycle(origin + loop * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(hold * 1000, () => {
        latest.current.rest();
        cycle(Date.now());
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, loop, touches, hold]);
}

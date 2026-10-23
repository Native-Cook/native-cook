/** All times are simulation seconds; no wall-clock scheduling. */
export const LOOP_PERIOD = 10.4;
export const LOOP_START = 9.25;
export const FIXED_DT = 1 / 240;
export const RESUME_DELAY = 3.5;

export type DemoFinger = { control: number; down: boolean; dx: number; velocity: number };

/** dx and velocity use travel units. The adapter converts through D.sw.travel and k. */
export function demoTouch(t: number, still = false): DemoFinger {
  'worklet';
  let control = -2;
  if ((t >= 0.9 && t < 1.06) || (t >= 7.5 && t < 7.64)) control = -1;
  if (t >= 2 && t < 2.12) control = 0;
  if (t >= 2.8 && t < 2.92) control = 1;
  if (t >= 3.6 && t < 3.72) control = 2;
  if (t >= 5.5 && t < 5.62) control = 3;
  if (!still && t >= 4.4 && t < 4.98) {
    const p = Math.min(1, (t - 4.4) / 0.42);
    return { control: 3, down: true, dx: 0.46 * (1 - (1 - p) ** 3),
      velocity: p < 1 ? 0.46 * 3 * (1 - p) ** 2 / 0.42 : 0 };
  }
  return { control, down: control !== -2, dx: 0, velocity: 0 };
}

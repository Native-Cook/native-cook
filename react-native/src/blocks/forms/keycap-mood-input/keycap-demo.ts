import type { KeycapLayout } from './keycap-theme';

export type KeycapPointer = { down: boolean; x: number; y: number };
export const DEMO_PERIOD = 13000;
function glide(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * Math.max(0, Math.min(1, t));
}
export function demoPointer(tMs: number, layout: KeycapLayout): KeycapPointer {
  'worklet';
  const t = ((tMs % 13000) + 13000) % 13000;
  const cx = layout.width / 2;
  const top = layout.keys[0].y + layout.K / 2;
  const moodY = top + 3 * layout.P;
  let x = cx;
  let y = top;
  let down = false;
  if (t >= 1000 && t < 1260) { down = true; y = top + 2 * layout.P; }
  else if (t >= 2200 && t < 2760) { down = true; y = glide(top, top + 2 * layout.P, (t - 2260) / 380); }
  else if (t >= 3900 && t < 4200) { down = true; y = moodY; }
  else if (t >= 6100 && t < 8850) {
    down = true; y = moodY;
    const starts = [6850, 7250, 7600, 7950, 8300];
    for (let i = 0; i < starts.length; i++) {
      if (t < starts[i]) break;
      const mini = layout.minis[4 - i];
      const previous = i === 0 ? { x: cx, y: moodY } : layout.minis[5 - i];
      x = glide(previous.x, mini.x, (t - starts[i]) / 200);
      y = glide(previous.y, mini.y, (t - starts[i]) / 200);
    }
  } else if (t >= 10300 && t < 11000) { down = true; y = glide(top, moodY, (t - 10360) / 500); }
  // Preserve the last contact position on up (flood origin / sticky fan hit testing).
  else if (t >= 1260 && t < 2200) y = top + 2 * layout.P;
  else if (t >= 2760 && t < 3900) y = top + 2 * layout.P;
  else if (t >= 4200 && t < 6100) y = moodY;
  else if (t >= 8850 && t < 10300) { x = layout.minis[0].x; y = layout.minis[0].y; }
  else if (t >= 11000) y = moodY;
  return { down, x, y };
}

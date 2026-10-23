/** Where everything sits for a given preview height. Worklets, read every frame. */
import { FRAMES, SIZES } from './motion';
import { LAYOUT } from './theme';

/** 0 up to the large size, 1 at full screen. */
export function fullness(h: number): number {
  'worklet';
  return Math.min(Math.max((h - SIZES[1]) / (SIZES[2] - SIZES[1]), 0), 1);
}

/** The source's preview changes aspect between sizes, so width and top are interpolated per segment. */
function along(h: number, key: 'width' | 'top'): number {
  'worklet';
  const i = h <= SIZES[1] ? 0 : 1;
  const u = (h - SIZES[i]) / (SIZES[i + 1] - SIZES[i]);
  return FRAMES[i][key] + (FRAMES[i + 1][key] - FRAMES[i][key]) * u;
}

export function previewFrame(h: number, width: number) {
  'worklet';
  const f = fullness(h);
  const w = along(h, 'width') * (width / 402);
  return { top: Math.max(along(h, 'top'), 0), left: (width - w) / 2, width: w, height: h, radius: LAYOUT.previewRadius * (1 - f), f };
}

/** How far the controls and timeline have moved down from where they sit at the compact size. */
export function belowShift(h: number): number {
  'worklet';
  return along(h, 'top') + h - (FRAMES[0].top + SIZES[0]);
}

/** 0 at the compact size, 1 from the large size up: the rows under the preview close up by SQUEEZE. */
export function squeeze(h: number): number {
  'worklet';
  return Math.min(Math.max((h - SIZES[0]) / (SIZES[1] - SIZES[0]), 0), 1);
}

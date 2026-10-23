import { Canvas, Picture, Skia, TileMode, createPicture, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { StepsView } from './steps-data';

// A bar fades from its top into the blue; the bar that is now, or the one under the finger, is solid at the top.
const BAR_TOP = [1, 1, 1, 0.42];
const BAR_BOTTOM = [1, 1, 1, 0.03];
const LIT_TOP = [1, 1, 1, 1];
const LIT_MIDDLE = [0.78, 0.76, 1, 0.75];
const LIT_BOTTOM = [0.6, 0.58, 1, 0.08];

// Bars are this share of their column, between these widths; a day of a month is a hairline.
const BAR_SHARE = 0.62;
const BAR_MAX = 30;
const SPIKE = 3;

// Motion, in seconds from a switch: the old bars sink together, then the new ones rise one after another.
export const SINK_FOR = 0.3;
export const RISE_FOR = 0.45;
const RISE_GAP_BARS = 0.04;
const RISE_GAP_SPIKES = 0.012;
export const SWITCH_FOR = SINK_FOR + RISE_FOR + 0.4;

export type ChartBox = { left: number; width: number; base: number; height: number };

/** Where each bar of a view stands: its centre, width and full height. */
export function barsOf(view: StepsView, box: ChartBox) {
  const step = box.width / view.values.length;
  const spiky = view.values.length > 7;
  const width = spiky ? SPIKE : Math.min(BAR_MAX, step * BAR_SHARE);
  return view.values.map((value, i) => ({
    x: box.left + step * (i + 0.5),
    width,
    height: (Math.min(value, view.max) / view.max) * box.height,
    delay: SINK_FOR + i * (spiky ? RISE_GAP_SPIKES : RISE_GAP_BARS),
  }));
}

function easeIn(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * x;
}

function easeOut(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return 1 - (1 - x) ** 3;
}

type Props = {
  width: number;
  height: number;
  box: ChartBox;
  views: StepsView[];
  /** The view switched from and to, and seconds since the switch. */
  from: SharedValue<number>;
  to: SharedValue<number>;
  since: SharedValue<number>;
  /** The bar under the finger, or -1. */
  picked: SharedValue<number>;
};

/** The bars of the blue field, drawn in one picture: they sink and rise again on every switch. */
export function StepsChart({ width, height, box, views, from, to, since, picked }: Props) {
  const layouts = useMemo(
    () => views.map((view) => barsOf(view, box).map((bar) => [bar.x, bar.width, bar.height, bar.delay])),
    [views, box],
  );
  const currents = views.map((view) => view.current);

  const picture = useDerivedValue(() => {
    const a = from.get();
    const b = to.get();
    const t = since.get();
    const finger = picked.get();
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const draw = (bars: number[][], lit: number, grown: number[]) => {
        for (let i = 0; i < bars.length; i++) {
          const h = bars[i][2] * grown[i];
          if (h < 0.5) continue;
          const w = bars[i][1];
          const top = box.base - h;
          const solid = i === lit;
          paint.setShader(
            Skia.Shader.MakeLinearGradient(
              vec(0, top),
              vec(0, box.base),
              solid
                ? [Float32Array.of(...LIT_TOP), Float32Array.of(...LIT_MIDDLE), Float32Array.of(...LIT_BOTTOM)]
                : [Float32Array.of(...BAR_TOP), Float32Array.of(...BAR_BOTTOM)],
              solid ? [0, 0.3, 1] : null,
              TileMode.Clamp,
            ),
          );
          const radius = Math.min(w / 2, 7);
          canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bars[i][0] - w / 2, top, w, h), radius, radius), paint);
        }
      };
      if (a !== b && t < SINK_FOR) {
        const sunk: number[] = [];
        const left = 1 - easeIn(t / SINK_FOR);
        for (let i = 0; i < layouts[a].length; i++) sunk.push(left);
        draw(layouts[a], currents[a], sunk);
      }
      const risen: number[] = [];
      for (let i = 0; i < layouts[b].length; i++) risen.push(a === b ? 1 : easeOut((t - layouts[b][i][3]) / RISE_FOR));
      draw(layouts[b], finger >= 0 ? finger : currents[b], risen);
    });
  });

  return (
    <Canvas style={{ width, height }}>
      <Picture picture={picture} />
    </Canvas>
  );
}

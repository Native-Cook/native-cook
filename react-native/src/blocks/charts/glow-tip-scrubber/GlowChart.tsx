import {
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  Line,
  LinearGradient,
  Path,
  RadialGradient,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { clamp01, priceAt } from './glow-series';

export const GREEN = '#3ddc97';
const LIT = '#b8ffe3';
const RULE = '#5a5a5e';
const CURSOR = '#ffffff';
const LINE = 2;
const LIT_LINE = 2.6;
// The lit stretch of line reaches this far either side of the tip; its bloom is wider and blurred.
const LIT_REACH = 26;
const BLOOM_LINE = 7;
const BLOOM_BLUR = 5;
const HALO = 28;
const HALO_COLORS = ['rgba(225,255,242,0.6)', 'rgba(61,220,151,0.28)', 'rgba(61,220,151,0)'];
const HALO_AT = [0, 0.45, 1];
// The whole line has a faint glow of its own.
const GLOW_BLUR = 6;
const GLOW_ALPHA = 0.35;
// Under a finger the rest of the line steps back.
const DIM = 0.55;
const DOTS = [1.5, 5];

type Props = {
  width: number;
  height: number;
  /** The plot's padding above and below, so the glow is not cut. */
  pad: number;
  prices: SharedValue<number[]>;
  low: SharedValue<number>;
  high: SharedValue<number>;
  tip: SharedValue<number>;
  held: SharedValue<number>;
  lit: SharedValue<number>;
  reveal: SharedValue<number>;
  shown: SharedValue<number>;
  marks: SharedValue<number>;
};

/**
 * The price line, a dashed rule at the opening price, and the glowing tip: a lit stretch of the
 * line, its bloom and a soft halo, all centred where the tip meets the line.
 */
export function GlowChart({ width, height, pad, prices, low, high, tip, held, lit, reveal, shown, marks }: Props) {
  const plot = height - pad * 2;
  const path = useDerivedValue(() => {
    const p = prices.get();
    const lo = low.get();
    const span = high.get() - lo || 1;
    const builder = Skia.PathBuilder.Make();
    const dx = width / (p.length - 1);
    for (let i = 0; i < p.length; i++) {
      const y = pad + (1 - (p[i] - lo) / span) * plot;
      if (i === 0) builder.moveTo(0, y);
      else builder.lineTo(i * dx, y);
    }
    return builder.build();
  });
  const point = useDerivedValue(() => {
    const x = clamp01(tip.get());
    const lo = low.get();
    const span = high.get() - lo || 1;
    return vec(x * width, pad + (1 - (priceAt(prices.get(), x) - lo) / span) * plot);
  });
  const open = useDerivedValue(() => {
    const p = prices.get();
    const lo = low.get();
    const span = high.get() - lo || 1;
    return pad + (1 - (p[0] - lo) / span) * plot;
  });
  const openFrom = useDerivedValue(() => vec(0, open.get()));
  const openTo = useDerivedValue(() => vec(width, open.get()));
  const clip = useDerivedValue(() => Skia.XYWHRect(-HALO, 0, HALO + reveal.get() * width, height));
  const base = useDerivedValue(() => shown.get() * (1 - DIM * held.get()));
  const litFrom = useDerivedValue(() => vec(point.get().x - LIT_REACH, 0));
  const litTo = useDerivedValue(() => vec(point.get().x + LIT_REACH, 0));
  const cursorTop = useDerivedValue(() => vec(point.get().x, 0));
  const cursorBottom = useDerivedValue(() => vec(point.get().x, height));

  return (
    <Canvas style={{ width, height }}>
      <Group opacity={marks}>
        <Line p1={openFrom} p2={openTo} color={RULE} strokeWidth={1.5} strokeCap="round">
          <DashPathEffect intervals={DOTS} />
        </Line>
      </Group>
      <Line p1={cursorTop} p2={cursorBottom} color={CURSOR} strokeWidth={1} opacity={held} />
      <Group clip={clip}>
        <Group opacity={base}>
          <Path path={path} style="stroke" strokeWidth={LINE} strokeJoin="round" strokeCap="round" color={GREEN} opacity={GLOW_ALPHA}>
            <BlurMask blur={GLOW_BLUR} style="normal" />
          </Path>
          <Path path={path} style="stroke" strokeWidth={LINE} strokeJoin="round" strokeCap="round" color={GREEN} />
        </Group>
        <Group opacity={lit}>
          <Path path={path} style="stroke" strokeWidth={BLOOM_LINE} strokeJoin="round" strokeCap="round">
            <LinearGradient start={litFrom} end={litTo} colors={['rgba(61,220,151,0)', 'rgba(61,220,151,0.9)', 'rgba(61,220,151,0)']} />
            <BlurMask blur={BLOOM_BLUR} style="normal" />
          </Path>
          <Path path={path} style="stroke" strokeWidth={LIT_LINE} strokeJoin="round" strokeCap="round">
            <LinearGradient start={litFrom} end={litTo} colors={['rgba(184,255,227,0)', LIT, 'rgba(184,255,227,0)']} />
          </Path>
        </Group>
      </Group>
      <Circle c={point} r={HALO} opacity={lit}>
        <RadialGradient c={point} r={HALO} colors={HALO_COLORS} positions={HALO_AT} />
      </Circle>
    </Canvas>
  );
}

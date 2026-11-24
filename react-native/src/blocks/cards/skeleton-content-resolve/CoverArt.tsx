import { Circle, Group, LinearGradient, Rect, RoundedRect, vec } from '@shopify/react-native-skia';
import { memo, type JSX } from 'react';

import type { CoverSpec } from './hearthData';

/** A seeded 0 … 1 value, the same on every run. */
function hash(i: number) {
  const x = Math.sin(i * 12.9898 + 4.1414) * 43758.5453;
  return x - Math.floor(x);
}

function motif(spec: CoverSpec, s: number): JSX.Element {
  switch (spec.motif) {
    case 'sun':
      return (
        <>
          <Circle color={spec.accent} cx={s * 0.6} cy={s * 0.44} r={s * 0.21} />
          <Rect color={spec.ink} height={s * 0.4} width={s} x={0} y={s * 0.62} />
          <Rect color={spec.accent} height={s * 0.02} opacity={0.5} width={s * 0.5} x={s * 0.35} y={s * 0.7} />
          <Rect color={spec.accent} height={s * 0.02} opacity={0.32} width={s * 0.34} x={s * 0.43} y={s * 0.78} />
          <Rect color={spec.accent} height={s * 0.02} opacity={0.18} width={s * 0.2} x={s * 0.5} y={s * 0.86} />
        </>
      );
    case 'rings':
      return (
        <>
          {[0.2, 0.36, 0.52, 0.68, 0.84].map((r, i) => (
            <Circle color={spec.ink} cx={s * 0.3} cy={s * 0.74} key={r} opacity={0.95 - i * 0.16} r={s * r} strokeWidth={s * 0.028} style="stroke" />
          ))}
          <Circle color={spec.accent} cx={s * 0.3} cy={s * 0.74} r={s * 0.085} />
        </>
      );
    case 'bars':
      return (
        <>
          {[0, 1, 2, 3, 4, 5].map((i) => {
            const h = s * (0.22 + 0.5 * hash(i + 3));
            const bw = s * 0.085;
            return <RoundedRect color={i === 2 ? spec.accent : spec.ink} height={h} key={i} r={bw / 2} width={bw} x={s * 0.16 + i * s * 0.122} y={s * 0.5 - h / 2} />;
          })}
        </>
      );
    case 'stripes':
      return (
        <Group origin={vec(s / 2, s / 2)} transform={[{ rotate: -0.6 }]}>
          {[0, 1, 2, 3, 4, 5, 6].map((i) => (
            <Rect color={i === 3 ? spec.accent : spec.ink} height={s * 0.07} key={i} opacity={i === 3 ? 1 : 0.85 - 0.08 * Math.abs(i - 3)} width={s * 1.6} x={-s * 0.3} y={s * 0.08 + i * s * 0.135} />
          ))}
        </Group>
      );
    case 'dots':
      return (
        <>
          {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15].map((i) => {
            const col = i % 4;
            const row = Math.floor(i / 4);
            const big = hash(i + 11);
            return <Circle color={i === 6 ? spec.accent : spec.ink} cx={s * (0.2 + col * 0.2)} cy={s * (0.2 + row * 0.2)} key={i} opacity={i === 6 ? 1 : 0.55 + 0.4 * big} r={s * (0.03 + 0.045 * big)} />;
          })}
        </>
      );
    case 'arc':
      return (
        <>
          <Circle color={spec.ink} cx={s * 0.08} cy={s * 1.0} r={s * 0.72} />
          <Circle color={spec.ground[1]} cx={s * 0.08} cy={s * 1.0} r={s * 0.48} />
          <Circle color={spec.accent} cx={s * 0.08} cy={s * 1.0} r={s * 0.26} />
          <Circle color={spec.accent} cx={s * 0.76} cy={s * 0.24} opacity={0.9} r={s * 0.055} />
        </>
      );
    case 'steps':
      return (
        <>
          {[0, 1, 2, 3, 4].map((i) => (
            <Rect color={i === 4 ? spec.accent : spec.ink} height={s * 0.11} key={i} opacity={i === 4 ? 1 : 0.5 + i * 0.12} width={s * (0.3 + i * 0.14)} x={0} y={s * (0.24 + i * 0.14)} />
          ))}
        </>
      );
  }
}

/** One cover, drawn in a size × size box: a three-stop ground, a motif, a soft light from the top left. */
export const CoverArt = memo(function CoverArt({ spec, size }: { spec: CoverSpec; size: number }) {
  return (
    <>
      <Rect height={size} width={size} x={0} y={0}>
        <LinearGradient colors={spec.ground} end={vec(size * 0.25, size)} positions={[0, 0.55, 1]} start={vec(size * 0.1, 0)} />
      </Rect>
      {motif(spec, size)}
      <Rect height={size} width={size} x={0} y={0}>
        <LinearGradient colors={['rgba(255,255,255,0.2)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.16)']} end={vec(size, size)} positions={[0, 0.45, 1]} start={vec(0, 0)} />
      </Rect>
    </>
  );
});

/**
 * Line icons and livery marks of Race Result Standings, drawn in code. Each takes the screen scale S
 * (points per reference point) and draws in reference points through a viewBox.
 */
import Svg, { Circle, Defs, G, Line, LinearGradient, Path, Polyline, Stop } from 'react-native-svg';

import { colors, marks, type MarkKind } from './theme';

type Scaled = { s: number };

export function BackArrow({ s, size }: Scaled & { size: number }) {
  // 42.33 pt circle; the arrow is 16.3 pt long with 6.7 pt arms, 2 pt round stroke.
  const c = size / 2;
  return (
    <Svg width={size * s} height={size * s} viewBox={`0 0 ${size} ${size}`}>
      <G stroke={colors.ink} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none">
        <Line x1={c + 8.33} y1={c} x2={c - 7.97} y2={c} />
        <Polyline points={`${c - 0.87},${c - 6.7} ${c - 7.97},${c} ${c - 0.87},${c + 6.7}`} />
      </G>
    </Svg>
  );
}

/** One chevron of the ⇅ switch: 7.05 × 3.7 pt, pointing up or down. */
export function Chevron({ s, up, stroke }: Scaled & { up: boolean; stroke: number }) {
  const w = 8.33;
  const h = 4.95;
  const pts = up ? `0.65,4.3 4.17,0.65 7.68,4.3` : `0.65,0.65 4.17,4.3 7.68,0.65`;
  return (
    <Svg width={w * s} height={h * s} viewBox={`0 0 ${w} ${h}`}>
      <Polyline points={pts} stroke={colors.ink} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** Stopwatch with a bolt: the fastest lap. Box 24 × 24 pt centred on the dial. */
export function Stopwatch({ s }: Scaled) {
  return (
    <Svg width={24 * s} height={24 * s} viewBox="0 0 24 24">
      <G stroke={colors.stopwatch} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" fill="none">
        <Circle cx={12} cy={13.6} r={9.55} />
        <Line x1={12.33} y1={4.05} x2={12.33} y2={2.2} />
        <Line x1={9.6} y1={1.35} x2={14.7} y2={1.35} />
        <Line x1={18.9} y1={6.7} x2={20.2} y2={5.3} />
        <Polyline points="13.2,9.2 10.2,12.3 13.9,14.3 10.6,17.8" />
      </G>
    </Svg>
  );
}

/** Rosette: driver of the day. Box 24 × 24 pt, ring centre at (12, 9.8). */
export function Award({ s }: Scaled) {
  const left = 'M9.6 15.3L3.3 18.3L6.7 19.2L7.9 22.2L12 17.1';
  const right = 'M14.4 15.3L20.7 18.3L17.3 19.2L16.1 22.2L12 17.1';
  return (
    <Svg width={24 * s} height={24 * s} viewBox="0 0 24 24">
      <G stroke={colors.award} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" fill="none">
        <Circle cx={12} cy={9.8} r={6.25} />
        <Path d={left} />
        <Path d={right} />
      </G>
    </Svg>
  );
}

/** Tyre: fastest pit stop. Box 24 × 24 pt centred on the wheel. */
export function Wheel({ s }: Scaled) {
  const spokes = Array.from({ length: 8 }, (_, i) => {
    const a = (i * Math.PI) / 4 + Math.PI / 8;
    return { x1: 12 + Math.cos(a) * 2.1, y1: 12 + Math.sin(a) * 2.1, x2: 12 + Math.cos(a) * 5, y2: 12 + Math.sin(a) * 5 };
  });
  return (
    <Svg width={24 * s} height={24 * s} viewBox="0 0 24 24">
      <Circle cx={12} cy={12} r={10.3} stroke={colors.wheel} strokeWidth={1.7} fill="none" />
      <Circle cx={12} cy={12} r={6.35} fill={colors.wheel} />
      <Circle cx={12} cy={12} r={4.75} fill={colors.shade} />
      {spokes.map((l, i) => (
        <Line key={i} {...l} stroke={colors.wheel} strokeWidth={1.25} />
      ))}
      <Circle cx={12} cy={12} r={2.15} fill={colors.wheel} />
      <Circle cx={12} cy={12} r={1.05} fill={colors.shade} />
    </Svg>
  );
}

export type BadgeKind = 'up' | 'down' | 'same';

/** The places-gained badge: double chevrons (green up, red down) or a green "=". Box 6.67 × 7.33 pt. */
export function ChangeBadge({ s, kind }: Scaled & { kind: BadgeKind }) {
  const w = 6.67;
  const h = 7.33;
  const color = kind === 'down' ? colors.down : colors.up;
  return (
    <Svg width={w * s} height={h * s} viewBox={`0 0 ${w} ${h}`}>
      <G stroke={color} strokeWidth={kind === 'same' ? 1 : 1.3} strokeLinecap="round" strokeLinejoin="round" fill="none">
        {kind === 'same' ? (
          <>
            <Line x1={0.5} y1={2.5} x2={6.17} y2={2.5} />
            <Line x1={0.5} y1={4.6} x2={6.17} y2={4.6} />
          </>
        ) : kind === 'up' ? (
          <>
            <Polyline points="0.6,3.15 3.33,0.6 6.07,3.15" />
            <Polyline points="0.6,6.75 3.33,4.2 6.07,6.75" />
          </>
        ) : (
          <>
            <Polyline points="0.6,0.6 3.33,3.15 6.07,0.6" />
            <Polyline points="0.6,4.2 3.33,6.75 6.07,4.2" />
          </>
        )}
      </G>
    </Svg>
  );
}

/** A driver's livery mark: a fading speed streak and slanted stripes, 30 × 8.33 pt. */
export function LiveryMark({ s, kind, color, id }: Scaled & { kind: MarkKind; color: string; id: string }) {
  const m = marks[kind];
  return (
    <Svg width={30 * s} height={8.33 * s} viewBox="0 0 30 8.33">
      <Defs>
        <LinearGradient id={id} x1="0" y1="0" x2="1" y2="0">
          <Stop offset="0" stopColor={color} stopOpacity={0} />
          <Stop offset="0.75" stopColor={color} stopOpacity={0.9} />
          <Stop offset="1" stopColor={color} stopOpacity={1} />
        </LinearGradient>
      </Defs>
      {m.streak ? <Path d={m.streak} fill={`url(#${id})`} /> : null}
      <Path d={m.bars} fill={color} />
    </Svg>
  );
}

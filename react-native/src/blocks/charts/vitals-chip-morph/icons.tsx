/** Vitals Chip Morph — the small vector icons (chips, check row, mini chart, tab bar). Sizes in pt × s. */

import Svg, { Circle, Defs, G, LinearGradient, Path, Rect, Stop, Text as SvgText } from 'react-native-svg';

import { colors } from './theme';

type IconProps = { s: number };

function arc(cx: number, cy: number, r: number, fromDeg: number, toDeg: number) {
  // Angles clockwise from 12 o'clock.
  const a0 = ((fromDeg - 90) * Math.PI) / 180;
  const a1 = ((toDeg - 90) * Math.PI) / 180;
  const large = toDeg - fromDeg > 180 ? 1 : 0;
  return `M ${cx + r * Math.cos(a0)} ${cy + r * Math.sin(a0)} A ${r} ${r} 0 ${large} 1 ${cx + r * Math.cos(a1)} ${cy + r * Math.sin(a1)}`;
}

const HEART_D =
  'M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.04 6.04 0 0 1 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.5L12 21.35Z';

export function HeartGlyph({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={HEART_D} fill={color} />
    </Svg>
  );
}

/** First chip: a pink heart over two grey lines (the summary). */
export function SummaryIcon({ s }: IconProps) {
  return (
    <Svg width={30 * s} height={40 * s} viewBox="0 0 30 40">
      <Defs>
        <LinearGradient id="vcmSummary" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={colors.summaryHeartTop} />
          <Stop offset="1" stopColor={colors.summaryHeartBottom} />
        </LinearGradient>
      </Defs>
      <G transform="translate(4 1) scale(0.92)">
        <Path d={HEART_D} fill="url(#vcmSummary)" />
      </G>
      <Rect x={2} y={27} width={26} height={2} rx={1} fill={colors.faint} opacity={0.75} />
      <Rect x={2} y={33.5} width={17} height={2} rx={1} fill={colors.faint} opacity={0.75} />
    </Svg>
  );
}

/** Sleep: a three-colour score ring with "84" inside. */
export function SleepIcon({ s }: IconProps) {
  const c = 16;
  const r = 13;
  return (
    <Svg width={32 * s} height={32 * s} viewBox="0 0 32 32">
      <Path d={arc(c, c, r, 268, 358)} stroke={colors.sleepTeal} strokeWidth={5.2} fill="none" />
      <Path d={arc(c, c, r, 4, 58)} stroke={colors.sleepCoral} strokeWidth={5.2} fill="none" />
      <Path d={arc(c, c, r, 64, 262)} stroke={colors.sleepBlue} strokeWidth={5.2} fill="none" />
      <SvgText x={c} y={c + 4.3} fontSize={12} fontWeight="700" fill={colors.ink} textAnchor="middle">
        84
      </SvgText>
    </Svg>
  );
}

/** Readiness: the gauge in small, with its dot and "7". */
export function ReadinessIcon({ s }: IconProps) {
  const c = 16;
  const r = 12;
  return (
    <Svg width={32 * s} height={32 * s} viewBox="0 0 32 32">
      <Path d={arc(c, c + 2, r, -78, 78)} stroke={colors.track} strokeWidth={4.6} strokeLinecap="round" fill="none" />
      <Path d={arc(c, c + 2, r, -2, 33)} stroke={colors.segment} strokeWidth={4.6} strokeLinecap="round" fill="none" />
      <Circle cx={c + r * Math.sin((30 * Math.PI) / 180)} cy={c + 2 - r * Math.cos((30 * Math.PI) / 180)} r={2.6} fill={colors.blue} />
      <SvgText x={c - 0.5} y={c + 9} fontSize={11} fontWeight="700" fill={colors.blue} textAnchor="middle">
        7
      </SvgText>
    </Svg>
  );
}

/** Fitness: three rings in small (outer red, green, cyan). */
export function FitnessIcon({ s }: IconProps) {
  const c = 16;
  const rings = [
    { r: 12.6, p: 0.75, color: colors.moveStart, track: colors.moveTrack },
    { r: 8.4, p: 0.63, color: colors.exerciseStart, track: colors.exerciseTrack },
    { r: 4.2, p: 0.33, color: colors.standStart, track: colors.standTrack },
  ];
  return (
    <Svg width={32 * s} height={32 * s} viewBox="0 0 32 32">
      {rings.map((ring) => (
        <Circle key={`t${ring.r}`} cx={c} cy={c} r={ring.r} stroke={ring.track} strokeWidth={3.8} fill="none" />
      ))}
      {rings.map((ring) => (
        <Path key={`p${ring.r}`} d={arc(c, c, ring.r, 0, 360 * ring.p)} stroke={ring.color} strokeWidth={3.8} strokeLinecap="round" fill="none" />
      ))}
    </Svg>
  );
}

/** Vitals: four small rings in a diamond. */
export function VitalsIcon({ s }: IconProps) {
  const pts = [
    [15, 7, colors.vitalsPink],
    [7, 15, colors.vitalsBlue],
    [23, 15, colors.vitalsBlue],
    [15, 23, colors.vitalsBlue],
  ] as const;
  return (
    <Svg width={30 * s} height={30 * s} viewBox="0 0 30 30">
      {pts.map(([x, y, col]) => (
        <Circle key={`${x}-${y}`} cx={x} cy={y} r={4.8} stroke={col} strokeWidth={2.8} fill="none" />
      ))}
    </Svg>
  );
}

// ─── Readiness check row ─────────────────────────────────────────────────────────────────────

export function TargetIcon({ s }: IconProps) {
  return (
    <Svg width={19 * s} height={19 * s} viewBox="0 0 19 19">
      <Circle cx={9.5} cy={9.5} r={8.6} fill={colors.iconGrey} opacity={0.9} />
      <Circle cx={9.5} cy={9.5} r={6.6} fill="none" stroke="#FFFFFF" strokeOpacity={0.55} strokeWidth={0.9} />
      <Circle cx={9.5} cy={9.5} r={4.4} fill="none" stroke="#FFFFFF" strokeOpacity={0.55} strokeWidth={0.9} />
      <Circle cx={9.5} cy={9.5} r={2.2} fill="none" stroke="#FFFFFF" strokeOpacity={0.55} strokeWidth={0.9} />
      <Circle cx={9.5} cy={9.5} r={0.9} fill="#FFFFFF" />
    </Svg>
  );
}

export function DotsIcon({ s }: IconProps) {
  const pts = [
    [9.5, 3.6, 1],
    [3.6, 9.5, 0.55],
    [15.4, 9.5, 0.55],
    [9.5, 15.4, 0.55],
  ] as const;
  return (
    <Svg width={19 * s} height={19 * s} viewBox="0 0 19 19">
      {pts.map(([x, y, o]) => (
        <Circle key={`${x}-${y}`} cx={x} cy={y} r={2.7} stroke={colors.iconGrey} strokeOpacity={o} strokeWidth={1.5} fill="none" />
      ))}
    </Svg>
  );
}

export function BedIcon({ s }: IconProps) {
  return (
    <Svg width={22 * s} height={16 * s} viewBox="0 0 22 16">
      <Rect x={2} y={1} width={18} height={6} rx={1.6} fill={colors.iconGrey} />
      <Rect x={4} y={3} width={6} height={3} rx={1} fill="#FFFFFF" opacity={0.4} />
      <Rect x={12} y={3} width={6} height={3} rx={1} fill="#FFFFFF" opacity={0.4} />
      <Rect x={0.5} y={7} width={21} height={6} rx={1.4} fill={colors.iconGrey} />
      <Rect x={0.5} y={12} width={2.4} height={3.5} rx={0.6} fill={colors.iconGrey} />
      <Rect x={19.1} y={12} width={2.4} height={3.5} rx={0.6} fill={colors.iconGrey} />
    </Svg>
  );
}

export function CheckCircle({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20">
      <Circle cx={10} cy={10} r={10} fill={colors.check} />
      <Path d="M5.6 10.4 8.6 13.3 14.4 7" stroke="#FFFFFF" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function Chevron({ s, color = colors.faint }: { s: number; color?: string }) {
  return (
    <Svg width={12 * s} height={16 * s} viewBox="0 0 12 16">
      <Path d="m3.5 2.5 5 5.5-5 5.5" fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

// ─── mini chart icons (blue) ──────────────────────────────────────────────────────────────────

export function MiniChartIcon({ kind, s }: { kind: 'heart' | 'lungs' | 'temp' | 'oxygen' | 'wave'; s: number }) {
  const c = colors.link;
  const size = 14 * s;
  if (kind === 'heart') return <HeartGlyph size={size} color={c} />;
  return (
    <Svg width={size} height={size} viewBox="0 0 14 14">
      {kind === 'lungs' && (
        <>
          <Path d="M6.2 2v4.2C6.2 9 4.8 12 2.6 12 1.3 12 1 10.8 1 9.6 1 7 2.6 4 4.6 4c.9 0 1.6.6 1.6 1.6" fill={c} />
          <Path d="M7.8 2v4.2C7.8 9 9.2 12 11.4 12 12.7 12 13 10.8 13 9.6 13 7 11.4 4 9.4 4c-.9 0-1.6.6-1.6 1.6" fill={c} />
        </>
      )}
      {kind === 'temp' && (
        <>
          <Rect x={5.1} y={0.8} width={3.8} height={9} rx={1.9} fill="none" stroke={c} strokeWidth={1.1} />
          <Circle cx={7} cy={11.2} r={2.4} fill={c} />
          <Rect x={6.4} y={4} width={1.2} height={6} fill={c} />
        </>
      )}
      {kind === 'oxygen' && (
        <>
          <Path d="M6 1.2C4.4 3.6 2.4 6 2.4 8.4a3.6 3.6 0 0 0 7.2 0C9.6 6 7.6 3.6 6 1.2Z" fill="none" stroke={c} strokeWidth={1.2} />
          <Circle cx={6} cy={8.6} r={1.5} fill={c} />
          <SvgText x={11.4} y={13.4} fontSize={4.4} fontWeight="700" fill={c} textAnchor="middle">2</SvgText>
        </>
      )}
      {kind === 'wave' && (
        <>
          <Rect x={0.6} y={2.2} width={12.8} height={9.6} rx={1.6} fill={c} />
          <Path d="M2.2 7.6 3.8 5.2 5.4 8.8 7 5.2 8.6 8.8 10.2 5.2 11.8 7.4" fill="none" stroke="#FFFFFF" strokeWidth={1} strokeLinejoin="round" />
        </>
      )}
    </Svg>
  );
}

// ─── tab bar ────────────────────────────────────────────────────────────────────────────────

export function LongevityIcon({ s }: IconProps) {
  const petals = Array.from({ length: 8 }, (_, i) => i * 45);
  return (
    <Svg width={26 * s} height={26 * s} viewBox="0 0 26 26">
      <Circle cx={13} cy={13} r={12} fill={colors.ink} />
      {petals.map((deg) => (
        <Path
          key={deg}
          d="M13 13 L13 1.6 A11.4 11.4 0 0 1 17.4 2.5 Z"
          fill="#FFFFFF"
          opacity={0.92}
          transform={`rotate(${deg + 12} 13 13)`}
        />
      ))}
      <Circle cx={13} cy={13} r={4.2} fill="#FFFFFF" />
      <Circle cx={13} cy={13} r={2.6} fill={colors.ink} />
    </Svg>
  );
}

export function BrowseIcon({ s }: IconProps) {
  return (
    <Svg width={24 * s} height={24 * s} viewBox="0 0 24 24">
      {[0, 1].flatMap((row) =>
        [0, 1].map((col) => <Rect key={`${row}-${col}`} x={1 + col * 12} y={1 + row * 12} width={10} height={10} rx={2.2} fill={colors.ink} />),
      )}
    </Svg>
  );
}

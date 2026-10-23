import Svg, { Circle, G, Path, Rect, Text as SvgText } from 'react-native-svg';

import { COLORS } from './theme';
import type { MarkKind } from './stocks';

type IconProps = { size?: number; color?: string };

export function CloseGlyph({ size = 18, color = COLORS.ink }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 18 18">
      <Path d="M2 2 L16 16 M16 2 L2 16" stroke={color} strokeWidth={2} strokeLinecap="round" />
    </Svg>
  );
}

export function BackGlyph({ size = 20, color = COLORS.ink }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20">
      <Path d="M13 3 L6 10 L13 17" stroke={color} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

// Two-tone line icons of the portfolio tiles: black outline, orange second stroke.
export function DepositGlyph({ size = 28 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 28 28">
      <Path d="M15 7.5 h2.5 a6.5 6.5 0 0 1 0 13 H15" stroke={COLORS.orange} strokeWidth={2.2} fill="none" strokeLinecap="round" />
      <Path
        d="M7.5 6.5 h4 v4.5 h4.5 v4 h-4.5 v4.5 h-4 v-4.5 H3 v-4 h4.5 z"
        stroke={COLORS.ink}
        strokeWidth={2.2}
        fill="none"
        strokeLinejoin="round"
      />
    </Svg>
  );
}

export function AnalyzeGlyph({ size = 28 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 28 28">
      <Path d="M13 6.5 a9 9 0 1 0 9 9 H13 z" stroke={COLORS.ink} strokeWidth={2.2} fill="none" strokeLinejoin="round" />
      <Path d="M16 3.5 a8.5 8.5 0 0 1 8.5 8.5 H16 z" stroke={COLORS.orange} strokeWidth={2.2} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

export function TransferGlyph({ size = 28 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 28 28">
      <Path d="M13 7 L20.5 14 L13 21" stroke={COLORS.orange} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <Path d="M5 7.5 v13 a1 1 0 0 0 1.6 0.8 L14 15 a1.3 1.3 0 0 0 0 -2 L6.6 6.7 A1 1 0 0 0 5 7.5 z" stroke={COLORS.ink} strokeWidth={2.2} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

const TAB_GREY = '#6d6d72';

export function HomeGlyph({ size = 26 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M4 11.5 L13 4 L22 11.5 V21.5 a1 1 0 0 1 -1 1 H16 v-6 h-6 v6 H5 a1 1 0 0 1 -1 -1 z" stroke={TAB_GREY} strokeWidth={2.1} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

export function PayGlyph({ size = 26 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M3.5 6 v14 L12 13 z M12 6 v14 L21 13 z" stroke={TAB_GREY} strokeWidth={2.1} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

export function InvestGlyph({ size = 26 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M5 22 v-5 M10 22 v-8 M15 22 v-10 M20 22 v-13" stroke={COLORS.orange} strokeWidth={2.4} strokeLinecap="round" />
      <Path d="M4 13 C 10 11, 15 8, 21 3.5" stroke={COLORS.orange} strokeWidth={2.2} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

export function ChatGlyph({ size = 26 }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M5 5 h16 a2 2 0 0 1 2 2 v10 a2 2 0 0 1 -2 2 H11 l-4.5 3.5 V19 H5 a2 2 0 0 1 -2 -2 V7 a2 2 0 0 1 2 -2 z" stroke={TAB_GREY} strokeWidth={2.1} fill="none" strokeLinejoin="round" />
      <Circle cx={8.5} cy={12} r={1.3} fill={TAB_GREY} />
      <Circle cx={13} cy={12} r={1.3} fill={TAB_GREY} />
      <Circle cx={17.5} cy={12} r={1.3} fill={TAB_GREY} />
    </Svg>
  );
}

// Abstract company marks drawn for the invented stocks (none is a real logo).
export function BrandMark({ kind, size }: { kind: MarkKind; size: number }) {
  const s = size;
  if (kind === 'rings') {
    // two interlocked rounded loops, white on blue
    return (
      <Svg width={s} height={s} viewBox="0 0 100 100">
        <G stroke="#ffffff" strokeWidth={7.5} fill="none">
          <Rect x={18} y={34} width={38} height={32} rx={16} transform="rotate(-12 37 50)" />
          <Rect x={44} y={34} width={38} height={32} rx={16} transform="rotate(12 63 50)" />
        </G>
      </Svg>
    );
  }
  if (kind === 'petals') {
    // four quarter-round petals in a pinwheel
    return (
      <Svg width={s} height={s} viewBox="0 0 100 100">
        <Path d="M49 49 V27 a22 22 0 0 0 -22 22 z" fill="#f35b2c" />
        <Path d="M51 49 H73 a22 22 0 0 0 -22 -22 z" fill="#8cc63f" />
        <Path d="M49 51 H27 a22 22 0 0 0 22 22 z" fill="#19a9f0" />
        <Path d="M51 51 V73 a22 22 0 0 0 22 -22 z" fill="#ffc21a" />
      </Svg>
    );
  }
  if (kind === 'ribbon') {
    // a folded red ribbon letter R
    return (
      <Svg width={s} height={s} viewBox="0 0 100 100">
        <Path d="M34 24 h10 v52 h-10 z" fill="#b20710" />
        <Path d="M44 24 h9 a14 14 0 0 1 0 28 h-9 z" fill="#e50914" />
        <Path d="M44 46 h9 l15 30 h-11 z" fill="#e50914" />
        <Path d="M44 24 h4 v52 h-4 z" fill="#8a050c" />
      </Svg>
    );
  }
  if (kind === 'lens') {
    // an eye-shaped lens with a spiral, white on green
    return (
      <Svg width={s} height={s} viewBox="0 0 100 100">
        <Path d="M18 50 C 32 30, 68 30, 82 50 C 68 70, 32 70, 18 50 z" stroke="#ffffff" strokeWidth={6} fill="none" strokeLinejoin="round" />
        <Path d="M50 50 m -3 0 a 3 3 0 1 1 6 0 a 8 8 0 1 1 -16 0 a 13 13 0 1 1 26 0" stroke="#ffffff" strokeWidth={5.5} fill="none" strokeLinecap="round" />
      </Svg>
    );
  }
  return (
    <Svg width={s} height={s} viewBox="0 0 100 100">
      <SvgText x={50} y={59} fill="#ffffff" fontSize={26} fontWeight="900" fontStyle="italic" textAnchor="middle" letterSpacing={-0.5}>
        NOVA
      </SvgText>
    </Svg>
  );
}

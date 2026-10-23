import Svg, { Circle, Path } from 'react-native-svg';

type GlyphProps = { size?: number; color: string; stroke?: number };

/** Thin arrow pointing up-left (send) or down-left (receive), as on the card's actions. */
export function ArrowGlyph({ size = 18, color, stroke = 1.6, direction }: GlyphProps & { direction: 'upLeft' | 'downLeft' | 'upRight' }) {
  const d = direction === 'upLeft' ? 'M15 15 L4 4 M4 4 L4 12 M4 4 L12 4'
    : direction === 'downLeft' ? 'M15 4 L4 15 M4 15 L4 7 M4 15 L12 15'
      : 'M4 15 L15 4 M15 4 L7 4 M15 4 L15 12';
  return (
    <Svg width={size} height={size} viewBox="0 0 19 19">
      <Path d={d} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** Transaction glyph: the same arrow standing on a base line. */
export function LedgerGlyph({ size = 20, color, stroke = 1.5, incoming }: GlyphProps & { incoming: boolean }) {
  const arrow = incoming ? 'M15 3 L5 13 M5 13 L5 6 M5 13 L12 13' : 'M15 14 L5 4 M5 4 L5 11 M5 4 L12 4';
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20">
      <Path d={arrow} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <Path d="M3 17.5 L17 17.5" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </Svg>
  );
}

export function PlusGlyph({ size = 18, color, stroke = 1.6 }: GlyphProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 18 18">
      <Path d="M9 2 L9 16 M2 9 L16 9" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </Svg>
  );
}

export function CloseGlyph({ size = 16, color, stroke = 1.8 }: GlyphProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 16 16">
      <Path d="M2 2 L14 14 M14 2 L2 14" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </Svg>
  );
}

export function GridGlyph({ size = 18, color }: GlyphProps) {
  const dots = [];
  for (let r = 0; r < 3; r++) {
    for (let c = 0; c < 3; c++) dots.push(<Circle key={`${r}${c}`} cx={3 + c * 6} cy={3 + r * 6} r={1.9} fill={color} />);
  }
  return <Svg width={size} height={size} viewBox="0 0 18 18">{dots}</Svg>;
}


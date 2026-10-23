import Svg, { Path, Rect } from 'react-native-svg';

type P = { color: string; size?: number; stroke?: number };

/** Three left-aligned bars of different lengths, as in the pin's menu button. */
export function MenuGlyph({ color, size = 22 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 22 22">
      <Path d="M2 5 H12 M2 11 H21 M2 17 H15" stroke={color} strokeWidth={2.2} strokeLinecap="round" />
    </Svg>
  );
}

export function PlusGlyph({ color, size = 22, stroke = 2 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 22 22">
      <Path d="M11 2 V20 M2 11 H20" stroke={color} strokeWidth={stroke} strokeLinecap="round" />
    </Svg>
  );
}

export function SwapGlyph({ color, size = 16 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 16 16">
      <Path d="M3 6 H13 L10 3 M13 10 H3 L6 13" stroke={color} strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function EyeGlyph({ color, size = 17, crossed }: P & { crossed: boolean }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 18 18">
      <Path d="M1.5 9 C3.6 5.2 6.1 3.6 9 3.6 C11.9 3.6 14.4 5.2 16.5 9 C14.4 12.8 11.9 14.4 9 14.4 C6.1 14.4 3.6 12.8 1.5 9 Z"
        stroke={color} strokeWidth={1.5} fill="none" strokeLinejoin="round" />
      <Path d="M6.6 9 A2.4 2.4 0 1 0 11.4 9 A2.4 2.4 0 1 0 6.6 9" stroke={color} strokeWidth={1.5} fill="none" />
      {crossed && <Path d="M2.5 2.5 L15.5 15.5" stroke={color} strokeWidth={1.6} strokeLinecap="round" />}
    </Svg>
  );
}

export function HomeGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M4 11.5 L13 4 L22 11.5 V20.5 C22 21.6 21.1 22.5 20 22.5 H6 C4.9 22.5 4 21.6 4 20.5 Z"
        stroke={color} strokeWidth={2.2} strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function ChartGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M3 3 V22.5 H23.5" stroke={color} strokeWidth={1.8} strokeLinecap="round" fill="none" />
      <Path d="M7 17 L11.5 11.5 L15 15 L22.5 8" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function PulseGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Path d="M2.5 14 H7 L9.5 9 L12.5 19 L15.5 7 L18 14 H23.5" stroke={color} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function CardGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 26 26">
      <Rect x={2.5} y={5} width={21} height={16} rx={3} stroke={color} strokeWidth={1.8} fill="none" />
      <Path d="M2.5 10 H23.5 M6 16.5 H9" stroke={color} strokeWidth={1.8} strokeLinecap="round" />
    </Svg>
  );
}

/** Four corner brackets and a bar: the scan button. */
export function ScanGlyph({ color, size = 30 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 30 30">
      <Path d="M4 10 V4 H10 M20 4 H26 V10 M26 20 V26 H20 M10 26 H4 V20 M4 15 H26"
        stroke={color} strokeWidth={2.2} strokeLinecap="butt" strokeLinejoin="miter" fill="none" />
    </Svg>
  );
}

/** Contactless waves (upgrade: seen once the card is out of the pocket). */
export function WaveGlyph({ color, size = 24 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M7 7.5 C8.8 10.3 8.8 13.7 7 16.5 M11 5 C13.9 9.4 13.9 14.6 11 19 M15 2.5 C19 8.6 19 15.4 15 21.5"
        stroke={color} strokeWidth={1.9} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

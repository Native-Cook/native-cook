import Svg, { Circle, Path } from 'react-native-svg';

type P = { size: number; color: string };

export function CloseIcon({ size, color }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M5 5l14 14M19 5L5 19" stroke={color} strokeWidth={2.2} strokeLinecap="round" />
    </Svg>
  );
}

export function TargetIcon({ size, color }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Circle cx={12} cy={12} r={9.6} stroke={color} strokeWidth={2} fill="none" />
      <Circle cx={12} cy={12} r={3.6} fill={color} />
    </Svg>
  );
}

const STAR = 'M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.2l-6.1 3.4 1.4-6.8L2.2 9.1l6.9-.8z';
export function Star({ size, color, filled }: P & { filled: boolean }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={STAR} fill={filled ? color : 'none'} stroke={color} strokeWidth={filled ? 0 : 1.6} strokeLinejoin="round" />
    </Svg>
  );
}

export function Caret({ size, color }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M6 9l6 6 6-6z" fill={color} />
    </Svg>
  );
}

/** Seat glyph: back, two arms and the cushion, as the pin draws it. */
export function SeatGlyph({ size, color }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M5.5 10V7a4 4 0 0 1 4-4h5a4 4 0 0 1 4 4v3" fill={color} />
      <Path d="M3 11.5a2 2 0 0 1 4 0v4h10v-4a2 2 0 0 1 4 0V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" fill={color} />
    </Svg>
  );
}

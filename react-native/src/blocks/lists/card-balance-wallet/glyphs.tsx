import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { COLOR } from './theme';

// One family: 1.9 pt rounded strokes on a 24 pt grid, filled where the pin fills.
type P = { color: string; size?: number };

export function BellGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M6 16.5 V11 C6 7.4 8.6 4.8 12 4.8 C15.4 4.8 18 7.4 18 11 V16.5 L19.6 18.3 H4.4 Z"
        stroke={color} strokeWidth={1.9} strokeLinejoin="round" fill="none" />
      <Path d="M10 20.6 C10.4 21.4 11.1 21.8 12 21.8 C12.9 21.8 13.6 21.4 14 20.6" stroke={color} strokeWidth={1.9} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

export function PlusGlyph({ color, size = 24 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M12 4 V20 M4 12 H20" stroke={color} strokeWidth={2.2} strokeLinecap="round" />
    </Svg>
  );
}

export function ArrowGlyph({ color, size = 24 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M4 12 H19.5 M13.5 5.8 L19.8 12 L13.5 18.2" stroke={color} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function ScanGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M3.5 8.5 V6.5 C3.5 4.8 4.8 3.5 6.5 3.5 H8.5 M15.5 3.5 H17.5 C19.2 3.5 20.5 4.8 20.5 6.5 V8.5 M20.5 15.5 V17.5 C20.5 19.2 19.2 20.5 17.5 20.5 H15.5 M8.5 20.5 H6.5 C4.8 20.5 3.5 19.2 3.5 17.5 V15.5"
        stroke={color} strokeWidth={2} strokeLinecap="round" fill="none" />
      <Rect x={7.2} y={7.2} width={4} height={4} rx={1.3} fill={color} />
      <Rect x={12.8} y={7.2} width={4} height={4} rx={1.3} fill={color} />
      <Rect x={7.2} y={12.8} width={4} height={4} rx={1.3} fill={color} />
      <Rect x={12.8} y={12.8} width={4} height={4} rx={1.3} fill={color} />
    </Svg>
  );
}

export function GridGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Rect x={3.5} y={3.5} width={7.5} height={9.5} rx={1.8} fill={color} />
      <Rect x={13} y={3.5} width={7.5} height={6} rx={1.8} fill={color} />
      <Rect x={3.5} y={15} width={7.5} height={5.5} rx={1.8} fill={color} />
      <Rect x={13} y={11.5} width={7.5} height={9} rx={1.8} fill={color} />
    </Svg>
  );
}

export function ChartGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Rect x={6} y={10} width={3} height={9} rx={1.2} fill={color} />
      <Rect x={10.6} y={5} width={3} height={14} rx={1.2} fill={color} />
      <Rect x={15.2} y={12} width={3} height={7} rx={1.2} fill={color} />
      <Path d="M3.5 20.5 H20.5" stroke={color} strokeWidth={1.9} strokeLinecap="round" />
    </Svg>
  );
}

export function ChatGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M5 5 H19 C20.1 5 21 5.9 21 7 V15 C21 16.1 20.1 17 19 17 H13.5 L9 20.5 V17 H5 C3.9 17 3 16.1 3 15 V7 C3 5.9 3.9 5 5 5 Z" fill={color} />
      <Circle cx={8.3} cy={11} r={1.2} fill={COLOR.tabCircle} />
      <Circle cx={12} cy={11} r={1.2} fill={COLOR.tabCircle} />
      <Circle cx={15.7} cy={11} r={1.2} fill={COLOR.tabCircle} />
    </Svg>
  );
}

export function WalletGlyph({ color, size = 26 }: P) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M4 7.5 C4 6.1 5.1 5 6.5 5 H16.5 L18 7.5 Z" fill={color} opacity={0.55} />
      <Rect x={3} y={7} width={18} height={13} rx={3} fill={color} />
      <Rect x={14} y={11.5} width={7} height={4.5} rx={1.6} fill={COLOR.white} />
      <Circle cx={16.6} cy={13.75} r={1} fill={color} />
    </Svg>
  );
}

/** An invented card network mark: a coral and an amber rounded square, overlapping (the pin's badge footprint). */
export function NetworkMark({ size = 22 }: { size?: number }) {
  return (
    <Svg width={size} height={size * 0.62} viewBox="0 0 22 14">
      <Rect x={1.6} y={1.6} width={10.8} height={10.8} rx={3.2} fill={COLOR.markCoral} />
      <Rect x={9.6} y={1.6} width={10.8} height={10.8} rx={3.2} fill={COLOR.markAmber} opacity={0.9} />
    </Svg>
  );
}

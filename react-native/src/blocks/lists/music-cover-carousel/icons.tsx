import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { GLYPH } from './theme';

// Each glyph is drawn in pt on a box the size of its ink on the pin (GLYPH), so it is laid out at 1:1.
type P = { color: string };

export function Rewind({ color }: P) {
  const [w, h] = GLYPH.rewind;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d="M7.4 0.5V8.2L0.5 4.35zM14.8 0.5V8.2L7.9 4.35z" fill={color} stroke={color} strokeWidth={1} strokeLinejoin="round" />
    </Svg>
  );
}
export function Forward({ color }: P) {
  const [w, h] = GLYPH.forward;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d="M0.5 0.5V8.2L7.5 4.35zM8.1 0.5V8.2L15.1 4.35z" fill={color} stroke={color} strokeWidth={1} strokeLinejoin="round" />
    </Svg>
  );
}
export function PauseGlyph({ color }: P) {
  const [w, h] = GLYPH.pause;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Rect x={0} y={0} width={3.2} height={h} rx={0.9} fill={color} />
      <Rect x={w - 3.2} y={0} width={3.2} height={h} rx={0.9} fill={color} />
    </Svg>
  );
}
export function PlayGlyph({ color }: P) {
  const [w, h] = GLYPH.pause;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d={`M1 1.6c0-.8.9-1.3 1.6-.9l${w - 2.6} ${h / 2 - 1.5}c.6.4.6 1.4 0 1.8L2.6 ${h - 0.7}c-.7.4-1.6-.1-1.6-.9z`} fill={color} />
    </Svg>
  );
}
export function MoreGlyph({ size, color }: P & { size: number }) {
  return <Svg width={size} height={size} viewBox="0 0 24 24">{[5, 12, 19].map((x) => <Circle key={x} cx={x} cy={12} r={1.9} fill={color} />)}</Svg>;
}
export function LyricsGlyph({ color }: P) {
  const [w, h] = GLYPH.lyrics;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d="M2.8 0.75h7.4a2.05 2.05 0 0 1 2.05 2.05v4.6a2.05 2.05 0 0 1-2.05 2.05H6.1L3.3 11.3V9.45h-0.5A2.05 2.05 0 0 1 0.75 7.4V2.8A2.05 2.05 0 0 1 2.8 0.75z"
        stroke={color} strokeWidth={1.5} fill="none" strokeLinejoin="round" />
      {[4.6, 8.2].map((x) => (
        <Path key={x} d={`M${x - 1.3} 4.4a1.3 1.3 0 1 1 2.6 0c0 1.5-.8 2.5-2.1 3`} stroke={color} strokeWidth={1.1} fill={color} strokeLinecap="round" />
      ))}
    </Svg>
  );
}
export function ListGlyph({ color }: P) {
  const [w, h] = GLYPH.list;
  const rows = [0.7, h / 2, h - 0.7];
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      {rows.map((y) => <Circle key={`d${y}`} cx={0.75} cy={y} r={0.75} fill={color} />)}
      {rows.map((y) => <Path key={`l${y}`} d={`M3.3 ${y}H${w - 0.7}`} stroke={color} strokeWidth={1.4} strokeLinecap="round" />)}
    </Svg>
  );
}
export function VolumeGlyph({ color }: P) {
  const [w, h] = GLYPH.volume;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d="M0 2.9h2.4L5.6 0.2v8.6L2.4 6.1H0z" fill={color} />
      <Path d="M7.6 2.6a2.6 2.6 0 0 1 0 3.8M9.9 0.9a5.3 5.3 0 0 1 0 7.2" stroke={color} strokeWidth={1.4} fill="none" strokeLinecap="round" />
    </Svg>
  );
}
/** The mini player's "now playing" mark: one wide pill with a raised centre, as in the pin. */
export function WaveGlyph({ color }: P) {
  const [w, h] = GLYPH.wave;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Rect x={0} y={0.8} width={2.2} height={h - 1.6} rx={1.1} fill={color} />
      <Rect x={2.9} y={0} width={w - 5.8} height={h} rx={1.6} fill={color} />
      <Rect x={w - 2.2} y={0.8} width={2.2} height={h - 1.6} rx={1.1} fill={color} />
    </Svg>
  );
}

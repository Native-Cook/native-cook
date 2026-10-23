/** The few glyphs the screen draws, on one 24 pt grid and one stroke weight. */
import Svg, { Path } from 'react-native-svg';

import { GLYPH } from './glyph-paths';

type Props = { size?: number; color: string; weight?: number };

export function Stroke({ d, size = 24, color, weight = 1.75 }: Props & { d: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={weight} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function Plane({ size = 16, color }: Props) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={GLYPH.plane} fill={color} />
    </Svg>
  );
}

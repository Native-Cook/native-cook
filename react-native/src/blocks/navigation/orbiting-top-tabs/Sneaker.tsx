import Svg, { Ellipse, Path } from 'react-native-svg';

export type SneakerColors = {
  upper: string;
  /** The toe cap and the lace panel. */
  shade: string;
  /** The heel and the stripes. */
  accent: string;
  midsole: string;
  outsole: string;
};

// A sneaker seen from the side, toe to the left, drawn in a 300 × 160 box.
export const SNEAKER_RATIO = 160 / 300;
const OUTSOLE = 'M22 131 Q13 140 25 147 L268 147 Q283 146 281 131 Z';
const MIDSOLE = 'M20 118 Q9 126 22 134 L281 134 Q287 120 280 103 L196 112 Q120 122 60 118 Z';
const UPPER =
  'M20 118 Q22 96 60 88 Q118 76 148 48 L160 34 Q168 30 176 40 Q200 66 236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L196 112 Q120 122 60 118 Z';
const TOE = 'M20 118 Q22 96 60 88 Q78 84 92 82 Q84 102 96 119 Q76 119 60 118 Z';
const HEEL = 'M236 54 L258 36 Q268 34 270 46 Q278 74 280 103 L232 108 Q244 80 236 54 Z';
const PANEL = 'M100 80 Q130 70 150 46 L160 34 Q168 30 176 40 L184 50 Q160 80 122 96 Z';
const STRIPES = 'M150 113 L186 66 L198 70 L164 113 Z M178 112 L208 72 L218 76 L190 111 Z';
const COLLAR = 'M176 40 Q200 66 236 54 Q206 78 180 52 Z';
const LACES = 'M112 80 L126 90 M124 72 L140 82 M136 62 L153 72 M147 52 L165 61';
const INSIDE = '#16161A';
const LACE = '#F6F4EE';

/** A sneaker drawn with paths, so that it sits on the white page without a photo's rectangle. */
export function Sneaker({ width, colors }: { width: number; colors: SneakerColors }) {
  return (
    <Svg width={width} height={width * SNEAKER_RATIO} viewBox="0 0 300 160">
      <Ellipse cx={152} cy={150} rx={138} ry={7} fill="#000000" opacity={0.07} />
      <Path d={OUTSOLE} fill={colors.outsole} />
      <Path d={MIDSOLE} fill={colors.midsole} />
      <Path d={UPPER} fill={colors.upper} />
      <Path d={TOE} fill={colors.shade} />
      <Path d={PANEL} fill={colors.shade} />
      <Path d={HEEL} fill={colors.accent} />
      <Path d={STRIPES} fill={colors.accent} />
      <Path d={COLLAR} fill={INSIDE} />
      <Path d={LACES} stroke={LACE} strokeWidth={3} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

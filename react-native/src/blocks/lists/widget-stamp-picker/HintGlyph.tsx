import Svg, { G, Path, Rect } from 'react-native-svg';

import { colors, layout } from './theme';

const { width: W, height: H } = layout.hint;

// "Swipe along the arc": a smile-shaped track with an arrowhead at each end and a hand whose
// finger points up at it (traced from the pin, 50 x 28 pt).
export function HintGlyph() {
  return (
    <Svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
      <Path d="M2.5 4.6 Q25 22 47.5 4.6" stroke={colors.hint} strokeWidth={2.4} fill="none" strokeLinecap="round" />
      <Path
        d="M1.2 1.4 L2.5 4.6 L6 4.1 M48.8 1.4 L47.5 4.6 L44 4.1"
        stroke={colors.hint}
        strokeWidth={2.2}
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <G rotation={-22} origin="28, 21">
        <Rect x={23.4} y={12.2} width={3.6} height={9} rx={1.8} fill={colors.hint} />
        <Rect x={22.6} y={18} width={11.6} height={8.6} rx={3.4} fill={colors.hint} />
        <Rect x={20.4} y={19.6} width={4} height={5} rx={2} fill={colors.hint} />
      </G>
    </Svg>
  );
}

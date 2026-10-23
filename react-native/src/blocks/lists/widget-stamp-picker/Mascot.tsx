import Svg, { Circle, Ellipse, G, Path, Rect } from 'react-native-svg';

import { colors, layout } from './theme';

const { width: W, height: H } = layout.mascot;

// Three blob creatures peeking out from behind a door (drawn in the pin's 64 x 78 pt box).
const BLOBS = [
  { cx: 52, cy: 14 },
  { cx: 50, cy: 38 },
  { cx: 51, cy: 62 },
];

// A puffy cloud: five overlapping circles around the centre.
function Blob({
  cx,
  cy,
  color,
  closed,
  tail,
}: {
  cx: number;
  cy: number;
  color: string;
  closed: boolean;
  tail?: boolean;
}) {
  const puffs = [
    [0, -6.5, 7],
    [6.5, -2, 6.5],
    [5, 6, 6.5],
    [-4, 6.5, 6.5],
    [-6, -2, 6.5],
  ];
  return (
    <G>
      {puffs.map(([dx, dy, r], i) => (
        <Circle key={i} cx={cx + dx} cy={cy + dy} r={r} fill={color} />
      ))}
      <Circle cx={cx} cy={cy} r={7.5} fill={color} />
      {tail && <Path d={`M${cx - 6} ${cy + 4} L${cx - 9} ${cy + 11} L${cx - 1} ${cy + 7} Z`} fill={color} />}
      {closed ? (
        <G>
          <Rect x={cx - 3.4} y={cy + 1.5} width={3.4} height={1.4} rx={0.7} fill="#FFFFFF" />
          <Rect x={cx + 2.3} y={cy + 1.5} width={3.4} height={1.4} rx={0.7} fill="#FFFFFF" />
        </G>
      ) : (
        <G>
          <Ellipse cx={cx - 1.7} cy={cy + 2.2} rx={1.65} ry={2.35} fill="#FFFFFF" />
          <Ellipse cx={cx + 4} cy={cy + 2.2} rx={1.65} ry={2.35} fill="#FFFFFF" />
        </G>
      )}
    </G>
  );
}

export function Mascot({ closed }: { closed: readonly boolean[] }) {
  return (
    <Svg width={W} height={H} viewBox={`0 0 ${W} ${H}`}>
      {/* floor line the door stands on */}
      <Path d="M0 74.5 H18" stroke="#EEF0F4" strokeWidth={1.2} strokeLinecap="round" />
      {BLOBS.map((b, i) => (
        <Blob key={i} cx={b.cx} cy={b.cy} color={colors.blobs[i]} closed={closed[i]} tail={i === 1} />
      ))}
      {/* the door, ajar: its far edge is shorter, its near edge hides the blobs' left halves */}
      <Path d="M14 9 Q14 4 19 3.6 L39.5 1.6 L41 1.6 L41 76 L39.5 76 L17 73.6 Q14 73.3 14 70 Z" fill={colors.door} />
      <Path d="M39.5 1 L41.5 1 L41.5 76 L39.5 76 Z" fill={colors.doorEdge} />
      <Circle cx={36.2} cy={40} r={1.1} fill="#FFFFFF" />
    </Svg>
  );
}

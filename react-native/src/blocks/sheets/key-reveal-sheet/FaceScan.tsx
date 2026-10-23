import Animated, { useAnimatedProps } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

const AnimatedPath = Animated.createAnimatedComponent(Path);

// The four corners of a face-scan frame in an 18 x 18 box, each about 12 points of line.
const CORNERS = [
  'M1 6V3a2 2 0 0 1 2-2h3',
  'M12 1h3a2 2 0 0 1 2 2v3',
  'M17 12v3a2 2 0 0 1-2 2h-3',
  'M6 17H3a2 2 0 0 1-2-2v-3',
];
const CORNER_LENGTH = 12;
const SIZE = 18;
// Scan 0 to 1: the corners draw in over the first part, a line sweeps across the rest.
const DRAW_BY = 0.4;
const LINE_FROM = 4;
const LINE_TO = 14;

type Value = { get(): number };

/** A face-scan icon whose corners draw themselves and a line sweeps across while `scan` runs 0 to 1. */
export function FaceScan({ scan, color }: { scan: Value; color: string }) {
  const corners = useAnimatedProps(() => ({ strokeDashoffset: CORNER_LENGTH * (1 - Math.min(1, scan.get() / DRAW_BY)) }));
  const line = useAnimatedProps(() => {
    const s = scan.get();
    const sweep = Math.min(1, Math.max(0, (s - 0.15) / 0.8));
    const x = LINE_FROM + (LINE_TO - LINE_FROM) * sweep;
    return { d: `M${x} 5V13`, opacity: s >= 1 ? 0 : Math.min(1, sweep * 6) };
  });
  return (
    <Svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`}>
      {CORNERS.map((d) => (
        <AnimatedPath
          key={d}
          d={d}
          fill="none"
          stroke={color}
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeDasharray={[CORNER_LENGTH, CORNER_LENGTH]}
          animatedProps={corners}
        />
      ))}
      <AnimatedPath d={`M${LINE_FROM} 5V13`} fill="none" stroke={color} strokeWidth={1.8} strokeLinecap="round" animatedProps={line} />
    </Svg>
  );
}

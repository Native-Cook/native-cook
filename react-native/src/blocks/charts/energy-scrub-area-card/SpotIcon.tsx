import { Canvas, Circle, Group, Line, LinearGradient, Path, vec } from '@shopify/react-native-skia';

const SIZE = 22;
// Violet through pink to orange, top left to bottom right.
const COLORS = ['#7c3aed', '#e0457b', '#f97316'];
// A four-pointed sparkle at the lens's upper right.
const SPARKLE = 'M16.5 1.5Q17 5 20.5 5.5Q17 6 16.5 9.5Q16 6 12.5 5.5Q16 5 16.5 1.5Z';

/** A magnifier with a sparkle, in one gradient: "find savings". */
export function SpotIcon() {
  return (
    <Canvas style={{ width: SIZE, height: SIZE }}>
      <Group>
        <LinearGradient start={vec(2, 3)} end={vec(20, 21)} colors={COLORS} />
        <Circle cx={9.5} cy={12} r={6.5} style="stroke" strokeWidth={2.2} />
        <Line p1={vec(14.3, 16.8)} p2={vec(19, 21.5)} style="stroke" strokeWidth={2.2} strokeCap="round" />
        <Path path={SPARKLE} />
      </Group>
    </Canvas>
  );
}

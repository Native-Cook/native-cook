import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

export const INDIGO = '#5b3fe0';
const POINTS = 16;

// An eight-point rosette in a 64 × 64 box: the outer and inner radius alternate.
const ROSETTE = (() => {
  let d = '';
  for (let i = 0; i < POINTS; i += 1) {
    const radius = i % 2 === 0 ? 31 : 26;
    const angle = (i / POINTS) * Math.PI * 2 - Math.PI / 2;
    d += `${i === 0 ? 'M' : 'L'}${(32 + Math.cos(angle) * radius).toFixed(2)} ${(32 + Math.sin(angle) * radius).toFixed(2)}`;
  }
  return `${d}Z`;
})();

/** The indigo rosette with a white bolt that stands for the picks. */
export function Badge({ size, color = INDIGO }: { size: number; color?: string }) {
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} viewBox="0 0 64 64" style={{ position: 'absolute' }}>
        <Path d={ROSETTE} fill={color} strokeLinejoin="round" stroke={color} strokeWidth={2} />
      </Svg>
      <Ionicons name="flash" size={size * 0.42} color="#ffffff" />
    </View>
  );
}

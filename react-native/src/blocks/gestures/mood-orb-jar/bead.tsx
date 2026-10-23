/**
 * One mood as a glass bead, drawn at the origin: a body lit from the upper left (light, base, deep), the light that
 * passes through it pooling on the lower right, a small hard highlight and a hairline rim. No face: the colour is
 * the mood. `mood` can be fixed or read from the simulation each frame (a pooled orb changes mood when reused).
 */
import { Circle, Group, Oval, RadialGradient, vec } from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue } from 'react-native-reanimated';

import { MOODS } from './theme';

type Props = { r: number; mood: number | DerivedValue<number> };

function moodAt(m: number) {
  'worklet';
  return MOODS[Math.max(0, Math.min(MOODS.length - 1, Math.round(m)))];
}

export function Bead({ r, mood }: Props) {
  const body = useDerivedValue(() => {
    const m = moodAt(typeof mood === 'number' ? mood : mood.value);
    return [m.light, m.base, m.deep];
  });
  const glow = useDerivedValue(() => {
    const m = moodAt(typeof mood === 'number' ? mood : mood.value);
    return [m.light, `${m.light}00`];
  });
  return (
    <Group>
      <Circle cx={0} cy={0} r={r}>
        <RadialGradient c={vec(-0.34 * r, -0.4 * r)} r={1.45 * r} colors={body} positions={[0, 0.48, 1]} />
      </Circle>
      {/* Light through the glass, gathered on the far side. */}
      <Circle cx={0} cy={0} r={r} opacity={0.55}>
        <RadialGradient c={vec(0.36 * r, 0.46 * r)} r={0.62 * r} colors={glow} />
      </Circle>
      <Circle cx={0} cy={0} r={r - 0.5} style="stroke" strokeWidth={1} color="rgba(30, 24, 16, 0.14)" />
      {/* The window's reflection: a small hard highlight, softened at its edge. */}
      <Group transform={[{ translateX: -0.36 * r }, { translateY: -0.44 * r }, { rotate: -0.6 }]}>
        <Oval x={-0.2 * r} y={-0.11 * r} width={0.4 * r} height={0.22 * r} color="rgba(255, 255, 255, 0.92)" />
      </Group>
      <Circle cx={0.16 * r} cy={-0.55 * r} r={0.06 * r} color="rgba(255, 255, 255, 0.7)" />
    </Group>
  );
}

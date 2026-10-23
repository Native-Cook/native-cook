import { Blur, Canvas, ColorMatrix, Group, Paint, RoundedRect } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import type { Box } from './geometry';

// Blur + alpha threshold: the pill, its tab and the blooming sheet melt into one black body,
// which is what gives the pin its drop-from-the-island look.
const GOO_BLUR = 14;
const GOO_MATRIX = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 24, -11.5];

function Shape({ box, color }: { box: SharedValue<Box>; color: string }) {
  const x = useDerivedValue(() => box.get().x);
  const y = useDerivedValue(() => box.get().y);
  const w = useDerivedValue(() => box.get().w);
  const h = useDerivedValue(() => box.get().h);
  const r = useDerivedValue(() => box.get().r);
  return <RoundedRect x={x} y={y} width={w} height={h} r={r} color={color} />;
}

export function IslandGoo({ width, height, pill, tab, sheet, fade, reduced }: {
  width: number; height: number; reduced: boolean;
  pill: SharedValue<Box>; tab: SharedValue<Box>; sheet: SharedValue<Box>; fade: SharedValue<number>;
}) {
  return (
    <Canvas style={[StyleSheet.absoluteFill, { width, height }]} pointerEvents="none">
      <Group layer={<Paint><Blur blur={GOO_BLUR} /><ColorMatrix matrix={GOO_MATRIX} /></Paint>}>
        <Shape box={pill} color="black" />
        <Shape box={tab} color="black" />
        {!reduced && <Shape box={sheet} color="black" />}
      </Group>
      {/* Reduce Motion: the sheet does not grow, it cross-fades in place, outside the goo. */}
      {reduced && <Group opacity={fade}><Shape box={sheet} color="black" /></Group>}
    </Canvas>
  );
}

import { Canvas, Group, RoundedRect, Shader } from '@shopify/react-native-skia';
import { View, StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';
import { holoEffect, holoUniforms } from './holo-shader';
import { D, sessions, tone, wrap } from './holo-theme';
import type { HoloValues } from './use-holo-pager';

function Dot({ index, values }: {
  index: number;
  values: HoloValues;
}) {
  const { pos } = values;

  const opacity = useDerivedValue(() => Math.max(0, 1 - Math.abs(wrap(index - pos.get()))));

  const width = useDerivedValue(() => D.dot + (D.dotActive - D.dot) * opacity.get());

  const transform = useDerivedValue(() => {
    let x = (D.width - (D.dot * 4 + D.dotActive - D.dot + D.dotGap * 3)) / 2;
    for (let i = 0; i < index; i++) {
      const weight = Math.max(0, 1 - Math.abs(wrap(i - pos.get())));
      x += D.dot + (D.dotActive - D.dot) * weight + D.dotGap;
    }
    return [{ translateX: x }];
  });
  // Fixed phase: small accents only repaint as the finger changes their position.

  const uniforms = useDerivedValue(() => holoUniforms(index, [D.dotActive, D.dot], D.dot / 2, 0,
    wrap(index - pos.get())));
  return <Group transform={transform}>
    <RoundedRect x={0} y={0} width={width} height={D.dot} r={D.dot / 2} color={tone.dot} />
    <RoundedRect x={0} y={0} width={width} height={D.dot} r={D.dot / 2} opacity={opacity}>
      <Shader source={holoEffect!} uniforms={uniforms} />
    </RoundedRect>
  </Group>;
}

export function HoloPager({ k, top, values, onReady }: {
  k: number;
  top: number;
  values: HoloValues;
  onReady: () => void;
}) {
  return <View pointerEvents="none" accessibilityElementsHidden onLayout={onReady}
    importantForAccessibility="no-hide-descendants" style={{
      position: 'absolute', top: top - D.dot
        * k / 2, width: D.width * k, height: D.dot * k
    }}>
    <Canvas style={StyleSheet.absoluteFill}>
      <Group transform={[{ scale: k }]}>
        {sessions.map((session, index) => <Dot key={session.label} index={index} values={values} />)}
      </Group>
    </Canvas>
  </View>;
}

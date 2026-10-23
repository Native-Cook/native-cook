import { BlurMask, Canvas, Group, RoundedRect, Shader } from '@shopify/react-native-skia';
import { Text } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import { holoEffect, holoUniforms } from './holo-shader';
import { arrivalScale, cardDepth, cardPose, clamp, D, M, mod, sessions, T, tone, wrap } from './holo-theme';
import type { HoloValues } from './use-holo-pager';
type Props = {
  index: number;
  k: number;
  centreY: number;
  values: HoloValues;
  onReady: () => void;
};
/** One card: its foil face (a Skia canvas), its contact shadow and its label. */
export function HoloCard({ index, k, centreY, values, onReady }: Props) {
  const { pos, vel, clock, centre, arrive, arrivalAge, surge, pressed, press, squeeze } = values;
  const size = D.card + D.guard * 2;

  // The outer view only takes its place in the stack. Everything that moves the card is on the
  // inner view: iOS depth-sorts sibling layers that carry a perspective transform (a tilted side
  // card would jump in front of the centre one), and it flattens a tilted layer inside a scaled
  // parent at 1x, which makes the label blocky while the carousel moves.
  const stack = useAnimatedStyle(() => ({ zIndex: cardDepth(pos.get(), index) }));

  const move = useAnimatedStyle(() => {
    const pose = cardPose(wrap(index - pos.get()));
    const follow = mod(centre.get()) === index ? arrivalScale(arrivalAge.get()) : 1;
    const squeezed = pressed.get() === index ? press.get() : 1;
    // Fixed notation: a settling velocity would otherwise print as "1e-7deg".
    const tilt = clamp(-vel.get() * M.tilt, -M.tiltMax, M.tiltMax);
    return {
      opacity: pose.opacity,
      transform: [
        { translateX: pose.x * k },
        { perspective: M.perspective },
        { rotateY: `${tilt.toFixed(3)}deg` },
        { scale: follow * k * pose.side / D.card * squeeze.get() * squeezed },
      ],
    };
  });

  const uniforms = useDerivedValue(() => {
    const centred = mod(centre.get()) === index;
    return holoUniforms(index, [D.card, D.card], D.radius, clock.get(), wrap(index - pos.get()),
      vel.get(), centred ? arrive.get() : 0, 1, centred ? arrivalAge.get() : T.arrival, 0,
      surge.get()[index]);
  });
  return <Animated.View onLayout={onReady} pointerEvents="none" accessible={false}
    accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[{
      position: 'absolute', left: (D.width * k - size) / 2, top: centreY - size / 2,
      width: size, height: size
    }, stack]}>
    <Animated.View style={[{ width: size, height: size }, move]}>
      <Canvas style={{ width: size, height: size }}>
        <RoundedRect x={D.guard} y={D.guard + D.shadow.y} width={D.card} height={D.card}
          r={D.radius} color="black" opacity={D.shadow.opacity}><BlurMask blur={D.shadow.blur}
            style="normal" /></RoundedRect>
        <Group transform={[{ translateX: D.guard }, { translateY: D.guard }]}>
          <RoundedRect x={0} y={0} width={D.card} height={D.card} r={D.radius}>
            <Shader source={holoEffect!} uniforms={uniforms} />
          </RoundedRect>
        </Group>
      </Canvas>
      <Text numberOfLines={1} style={{
        position: 'absolute', left: D.guard, top: D.guard,
        width: D.card, height: D.card, lineHeight: D.card, textAlign: 'center', includeFontPadding: false,
        color: tone.label, fontSize: D.type.label, fontWeight: '700', letterSpacing: D.tracking.label
      }}>{sessions[index].label}</Text>
    </Animated.View>
  </Animated.View>;
}

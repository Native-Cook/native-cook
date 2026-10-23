import {
  BlurMask, Canvas, Group, LinearGradient, Rect, RoundedRect, Shader, vec,
} from '@shopify/react-native-skia';
import { View, StyleSheet } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';
import { holoEffect, holoUniforms } from './holo-shader';
import { arrivalScale, cardAtDepth, cardPose, D, mod, sessions, T, wrap } from './holo-theme';
import type { HoloValues } from './use-holo-pager';
type LightProps = {
  index: number;
  values: HoloValues;
  centreY: number;
  width: number;
};

function Glow({ index, values, centreY, width, wide }: LightProps & {
  wide: boolean;
}) {
  const { pos, vel, clock, centre, arrive, arrivalAge, surge, squeeze } = values;

  const transform = useDerivedValue(() => {
    const pose = cardPose(wrap(index - pos.get()));
    const pulse = mod(centre.get()) === index ? arrive.get() : 0;
    const follow = mod(centre.get()) === index ? arrivalScale(arrivalAge.get()) : 1;
    const spread = wide ? D.glow.wideScale + pulse * D.glow.bloomScale : D.glow.tightScale;
    const side = pose.side * spread * squeeze.get() * follow;
    return [
      { translateX: width / 2 + pose.x - side / 2 },
      { translateY: centreY - side / 2 },
      { scale: side / D.card },
    ];
  });

  const opacity = useDerivedValue(() => {
    const pulse = mod(centre.get()) === index ? arrive.get() : 0;
    return cardPose(wrap(index - pos.get())).opacity
      * (wide ? D.glow.wideOpacity : D.glow.tightOpacity) * (1 + pulse * D.glow.pulse);
  });

  const uniforms = useDerivedValue(() => holoUniforms(index, [D.card, D.card], D.radius,
    clock.get(), wrap(index - pos.get()), vel.get(), 0, 0, T.arrival, 0, surge.get()[index]));
  return <Group transform={transform} opacity={opacity}>
    <RoundedRect x={0} y={0} width={D.card} height={D.card} r={D.radius}>
      <Shader source={holoEffect!} uniforms={uniforms} />
      <BlurMask blur={wide ? D.glow.wideBlur : D.glow.tightBlur} style="normal" />
    </RoundedRect>
  </Group>;
}

function Reflection({ depth, values, centreY, width }: Omit<LightProps, 'index'> & {
  depth: number;
}) {
  const { pos, vel, clock, squeeze, centre, arrivalAge, surge } = values;

  const state = useDerivedValue(() => {
    const index = cardAtDepth(pos.get(), depth);
    const d = wrap(index - pos.get());
    const pose = cardPose(d);
    const follow = mod(centre.get()) === index ? arrivalScale(arrivalAge.get()) : 1;
    const side = pose.side * squeeze.get() * follow;
    // A mirror 6 pt below the bottom puts the reflected bottom 12 pt below the real one.
    const y = centreY + side / 2 + D.reflect.gap * 2;
    return { index, d, pose, side, y };
  });

  const transform = useDerivedValue(() => {
    const { pose, side, y } = state.get();
    return [
      { translateX: width / 2 + pose.x - side / 2 },
      { translateY: y + side },
      { scaleX: side / D.card },
      { scaleY: -side / D.card },
    ];
  });

  const opacity = useDerivedValue(() => state.get().pose.opacity * D.reflect.opacity);

  const uniforms = useDerivedValue(() => {
    const { index, d, side } = state.get();
    return holoUniforms(index, [D.card, D.card], D.radius, clock.get(), d, vel.get(), 0, 0,
      T.arrival, D.reflect.height * D.card / side, surge.get()[index]);
  });
  return <Group transform={transform} opacity={opacity}>
    <RoundedRect x={0} y={0} width={D.card} height={D.card} r={D.radius}>
      <Shader source={holoEffect!} uniforms={uniforms} />
      <BlurMask blur={D.reflect.blur} style="normal" />
    </RoundedRect>
  </Group>;
}

export function HoloBackdrop({ k, width, height, centreY, values, onReady }: {
  k: number;
  width: number;
  height: number;
  centreY: number;
  values: HoloValues;
  onReady: () => void;
}) {
  const w = width / k;
  const h = height / k;
  const feather = D.glow.edgeFade;
  // The light falls to black before the full-window canvas edge, even during an arrival bloom.
  const edges = [
    { x: 0, y: 0, width: feather, height: h, start: vec(0, 0), end: vec(feather, 0) },
    { x: w - feather, y: 0, width: feather, height: h, start: vec(w, 0), end: vec(w - feather, 0) },
    { x: 0, y: 0, width: w, height: feather, start: vec(0, 0), end: vec(0, feather) },
    { x: 0, y: h - feather, width: w, height: feather, start: vec(0, h), end: vec(0, h - feather) },
  ];
  return <View pointerEvents="none" accessible={false} accessibilityElementsHidden
    importantForAccessibility="no-hide-descendants" onLayout={onReady}
    style={{ position: 'absolute', width, height }}>
    <Canvas style={StyleSheet.absoluteFill}>
      <Group transform={[{ scale: k }]}>
        {/* Depth 0 is the farthest card: its reflection is drawn first, the centre card's last. */}
        {[0, 1, 2, 3].map((depth) => <Reflection key={depth} depth={depth} width={w}
          centreY={centreY / k} values={values} />)}
        {sessions.map((session, index) => <Glow key={`${session.label}-wide`} index={index}
          wide width={w} centreY={centreY / k} values={values} />)}
        {sessions.map((session, index) => <Glow key={session.label} index={index} wide={false}
          width={w} centreY={centreY / k} values={values} />)}
        {edges.map(({ start, end, ...rect }, index) => <Rect key={index} {...rect}>
          <LinearGradient start={start} end={end} colors={['#000000', '#000000F2', '#00000080',
            '#00000000']} positions={[0, 0.15, 0.5, 1]} />
        </Rect>)}
      </Group>
    </Canvas>
  </View>;
}

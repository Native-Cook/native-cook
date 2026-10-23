/** The replica of the source: the dark door, the wire and the bulb sprite swinging and lighting up, nothing else. */
import {
  Blur, Canvas, Circle, ColorMatrix, Group, Image, LinearGradient, RadialGradient, Rect, useImage, vec, type Transforms3d,
} from '@shopify/react-native-skia';
import { StyleSheet, useWindowDimensions } from 'react-native';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import { REPLICA_HOLD, REPLICA_PERIOD, REPLICA_IGNITE, REPLICA_LOOP_MS, REPLICA_SWING_PHASE } from './motion';
import { FIT, REPLICA_ART as A } from './theme';

function ignite(t: number) {
  'worklet';
  const ts = REPLICA_IGNITE.times;
  const vs = REPLICA_IGNITE.values;
  for (let i = 0; i < ts.length - 1; i++) {
    if (t <= ts[i + 1]) return vs[i] + ((vs[i + 1] - vs[i]) * (t - ts[i])) / (ts[i + 1] - ts[i]);
  }
  return 1;
}

export function BulbReplica() {
  const { width, height } = useWindowDimensions();
  const wall = useImage(require('./assets/wall.jpg'));
  const lit = useImage(require('./assets/bulb-lit.png'));
  const dim = useImage(require('./assets/bulb-dim.png'));
  const lightAdd = useImage(require('./assets/light-add.png'));
  const lightMul = useImage(require('./assets/light-mul.png'));
  const clock = useSharedValue(0);
  useFrameCallback((f) => {
    'worklet';
    if (REPLICA_HOLD >= 0) clock.set(REPLICA_HOLD);
    else clock.set((clock.get() + (f.timeSincePreviousFrame ?? 0) / 1000) % (REPLICA_LOOP_MS / 1000));
  });

  const s = width / 402;
  const k = FIT.k * s;
  const socketTop = A.socketTopSrc * k;
  const pivotY = socketTop - 2000 * s; // the wire hangs from far above the frame, as in the source
  const arm = socketTop - pivotY + A.glassCenterSrc * k;
  const centreX = A.centreSrc * k + FIT.cropX * s;
  const sw = A.spriteW * A.spriteScale * s;
  const sh = A.spriteH * A.spriteScale * s;

  const angle = useDerivedValue(() => (A.swingSrc * k / arm) * Math.sin((2 * Math.PI * clock.value) / REPLICA_PERIOD + REPLICA_SWING_PHASE));
  const on = useDerivedValue(() => ignite(clock.value));
  const off = useDerivedValue(() => 1 - on.value);
  const bulb = useDerivedValue<Transforms3d>(() => [
    { translateX: centreX }, { translateY: pivotY }, { rotate: -angle.value }, { translateY: socketTop - pivotY },
  ]);
  const glass = useDerivedValue(() => vec(centreX + Math.sin(angle.value) * arm, pivotY + Math.cos(angle.value) * arm));
  const halo = useDerivedValue(() => on.value);
  const lightMapO = useDerivedValue(() => on.value * A.lightMap);
  const mapT = useDerivedValue<Transforms3d>(() => [{ translateX: glass.value.x - width }]);
  // Each stile glows more when the bulb swings toward it.
  const leftLit = useDerivedValue(() => on.value * (0.65 - 0.35 * Math.sin(angle.value * 12)));
  const rightLit = useDerivedValue(() => on.value * (0.65 + 0.35 * Math.sin(angle.value * 12)));
  const px = s / 3; // reference pixels to points

  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Rect x={0} y={0} width={width} height={height} color="#0D0E10" />
      <Image image={wall} x={A.wallX * s} y={0} width={A.wallW * s} height={height} fit="fill">
        <ColorMatrix matrix={A.wallTint} />
      </Image>
      {/* The bulb's blue light on the wall and the door's stiles. */}
      <Group opacity={halo} blendMode="screen">
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={glass} r={width * A.ambientR} colors={A.ambient} />
        </Rect>
        <Rect x={0} y={0} width={width} height={height}>
          <RadialGradient c={glass} r={width * A.spillR} colors={A.spillFall} positions={A.spillStops} />
        </Rect>
        <Circle c={glass} r={A.halo * s}>
          <RadialGradient c={glass} r={A.halo * s} colors={[A.haloColor, 'rgba(31, 125, 255, 0)']} />
        </Circle>
        <Rect x={0} y={A.railY[0] * px} width={width} height={(A.railY[1] - A.railY[0]) * px} color={A.railColor} />
      </Group>
      <Group opacity={leftLit} blendMode="screen">
        <Rect x={A.stileLeft[0] * px} y={A.stileGlow[0] * px} width={(A.stileLeft[1] - A.stileLeft[0]) * px} height={(A.stileGlow[2] - A.stileGlow[0]) * px}>
          <LinearGradient start={vec(0, A.stileGlow[0] * px)} end={vec(0, A.stileGlow[2] * px)} colors={['rgba(36,104,210,0)', A.stileColor, 'rgba(36,104,210,0)']} />
          <Blur blur={3} />
        </Rect>
      </Group>
      <Group opacity={rightLit} blendMode="screen">
        <Rect x={A.stileRight[0] * px} y={A.stileGlow[0] * px} width={(A.stileRight[1] - A.stileRight[0]) * px} height={(A.stileGlow[2] - A.stileGlow[0]) * px}>
          <LinearGradient start={vec(0, A.stileGlow[0] * px)} end={vec(0, A.stileGlow[2] * px)} colors={['rgba(36,104,210,0)', A.stileColor, 'rgba(36,104,210,0)']} />
          <Blur blur={3} />
        </Rect>
      </Group>
      <Group transform={bulb}>
        <Rect x={-A.wireWidth / 2} y={pivotY - socketTop} width={A.wireWidth} height={socketTop - pivotY + 4} color={A.wireColor} />
        {/* The sprites are on black: a screen blend keeps only their light. */}
        <Group opacity={off}>
          <Image image={dim} x={-A.anchorX * A.spriteScale * s} y={-A.anchorY * A.spriteScale * s} width={sw} height={sh} fit="fill" blendMode="screen" />
        </Group>
        <Group opacity={on}>
          <Image image={lit} x={-A.anchorX * A.spriteScale * s} y={-A.anchorY * A.spriteScale * s} width={sw} height={sh} fit="fill" blendMode="screen">
            <ColorMatrix matrix={A.interior} />
          </Image>
          {/* A blurred bright copy: the neon's bloom. */}
          <Image image={lit} x={-A.anchorX * A.spriteScale * s} y={-A.anchorY * A.spriteScale * s} width={sw} height={sh} fit="fill" blendMode="screen" opacity={A.bloomOpacity}>
            <ColorMatrix matrix={A.brightPass} />
            <Blur blur={A.bloom} />
          </Image>
        </Group>
      </Group>
      {/* The source's baked light field around the bulb (low frequency), moving with it. */}
      <Group transform={mapT} opacity={lightMapO}>
        <Image image={lightMul} x={0} y={0} width={width * 2} height={height} fit="fill" blendMode="multiply" />
        <Image image={lightAdd} x={0} y={0} width={width * 2} height={height} fit="fill" blendMode="plus" opacity={0.25} />
      </Group>
    </Canvas>
  );
}

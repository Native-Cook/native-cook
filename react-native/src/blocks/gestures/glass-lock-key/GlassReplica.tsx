/** The replica of the source render: Blender layers (body, shackle, key) registered to the screen, as in the video. */
import { Canvas, Group, Image, Rect, useImage, type Transforms3d } from '@shopify/react-native-skia';
import { StyleSheet, useWindowDimensions } from 'react-native';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import { REPLICA_HOLD, REPLICA_LOOP_MS, REPLICA_T } from './motion';
import { REPLICA_BG, REPLICA_FIT as F } from './theme';

function clamp(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
function phase(t: number, r: readonly [number, number]) {
  'worklet';
  return clamp((t - r[0]) / (r[1] - r[0]));
}
function easeOutBack(u: number) {
  'worklet';
  const c = 1.7;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}

type Fit = readonly [number, number, number, number, number] | readonly [number, number, number, number, number, number];

/** The layer's fitted pose, as screen transforms (k = pt per source px). */
function fitT(q: Fit, c: readonly [number, number], k: number): Transforms3d {
  'worklet';
  return [
    { translateX: (c[0] + q[3]) * k },
    { translateY: (c[1] + q[4]) * k },
    { rotate: (-q[2] * Math.PI) / 180 },
    { skewX: -Math.atan((q.length > 5 ? (q as readonly number[])[5] : 0) / q[1]) }, // Skia skews the other way from OpenCV
    { scaleX: q[0] },
    { scaleY: q[1] },
    { translateX: -c[0] * k },
    { translateY: -c[1] * k },
  ];
}
function mix(a: Fit, b: Fit, u: number): Fit {
  'worklet';
  const a5 = a.length > 5 ? (a as readonly number[])[5] : 0;
  const b5 = b.length > 5 ? (b as readonly number[])[5] : 0;
  return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u, a[4] + (b[4] - a[4]) * u, a5 + (b5 - a5) * u];
}
function smooth(u: number) {
  'worklet';
  return u * u * (3 - 2 * u);
}

export function GlassReplica() {
  const { width, height } = useWindowDimensions();
  const body = useImage(require('./assets/padlock-body.png'));
  const closed = useImage(require('./assets/shackle-closed.png'));
  const open = useImage(require('./assets/shackle-open.png'));
  const keyIn = useImage(require('./assets/key-in.png'));
  const keyInA = useImage(require('./assets/key-in-a.png'));
  const keyFront = useImage(require('./assets/key-front.png'));
  const shadow = useImage(require('./assets/shadow.png'));
  const clock = useSharedValue(0);
  useFrameCallback((f) => {
    'worklet';
    if (REPLICA_HOLD >= 0) clock.set(REPLICA_HOLD);
    else clock.set((clock.get() + (f.timeSincePreviousFrame ?? 0) / 1000) % (REPLICA_LOOP_MS / 1000));
  });
  // The lone key turns about its long axis (its width goes through cos).
  const keyT = useDerivedValue<Transforms3d>(() => {
    const u = phase(clock.value, REPLICA_T.keySpin);
    const cx = (603 / 1206) * width;
    return [{ translateX: cx }, { scaleX: Math.max(0.06, Math.abs(Math.cos(Math.PI * u))) }, { translateX: -cx }];
  });
  const lockO = useDerivedValue(() => phase(clock.value, REPLICA_T.toLock) * (1 - phase(clock.value, REPLICA_T.toKey)));
  const keyO = useDerivedValue(() => 1 - lockO.value);
  const up = useDerivedValue(() => {
    const t = clock.value;
    return t < REPLICA_T.close[0] ? easeOutBack(phase(t, REPLICA_T.open)) : 1 - phase(t, REPLICA_T.close);
  });
  // Open/closed shackle layers cross-fade while the shackle travels between their positions.
  const openO = useDerivedValue(() => clamp(up.value));
  const closedO = useDerivedValue(() => 1 - clamp(up.value));
  const closedT = useDerivedValue<Transforms3d>(() => [{ translateX: (23 / 1206) * width * up.value }, { translateY: (-90 / 2622) * height * up.value }]);
  const openT = useDerivedValue<Transforms3d>(() => [{ translateX: (-23 / 1206) * width * (1 - up.value) }, { translateY: (90 / 2622) * height * (1 - up.value) }]);
  const k = width / 1206;
  const lockT = fitT(F.lock, F.lockCentre, k);
  const frontT = fitT(F.front, F.frontCentre, k);
  // The key settles from its shut pose to its turned pose as the shackle rises, and back as it closes.
  const keyU = useDerivedValue(() => {
    const t = clock.value;
    return t < REPLICA_T.close[0] ? smooth(phase(t, [REPLICA_T.open[0], 3.0])) : 1 - smooth(phase(t, [REPLICA_T.close[0], REPLICA_T.toKey[0]]));
  });
  const keyInT = useDerivedValue<Transforms3d>(() => fitT(mix(F.keyClosed, F.keyOpen, keyU.value), F.keyCentre, k));
  const keyAO = useDerivedValue(() => 1 - keyU.value);
  const img = { x: 0, y: 0, width, height, fit: 'fill' as const };
  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Rect x={0} y={0} width={width} height={height} color={REPLICA_BG} />
      <Group opacity={keyO} transform={keyT}>
        <Group transform={frontT}>
          <Image image={keyFront} {...img} />
        </Group>
      </Group>
      <Group opacity={lockO}>
        <Group transform={lockT}>
          <Image image={shadow} {...img} />
          <Group transform={[{ translateX: F.shackleClosed[0] * k }, { translateY: F.shackleClosed[1] * k }]}>
            <Group transform={closedT} opacity={closedO}>
              <Image image={closed} {...img} />
            </Group>
          </Group>
          <Group transform={[{ translateX: F.shackleOpen[0] * k }, { translateY: F.shackleOpen[1] * k }]}>
            <Group transform={openT} opacity={openO}>
              <Image image={open} {...img} />
            </Group>
          </Group>
          <Image image={body} {...img} />
        </Group>
        {/* Two renders of the key: shut (ref a) and turned (ref b), cross-faded as it turns. */}
        <Group transform={keyInT}>
          <Group opacity={keyAO}>
            <Image image={keyInA} {...img} />
          </Group>
          <Group opacity={keyU}>
            <Image image={keyIn} {...img} />
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

import { BlurMask, Canvas, Group, Path, Skia, type SkPath } from '@shopify/react-native-skia';
import { StyleSheet, Text, View } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

const INK = '#FFFFFF';
const STROKE = 22;
// The space between two segments, measured between their rounded ends.
const GAP = 6;
const GLOW_BLUR = 9;
const GLOW_ALPHA = 0.5;

type Props = {
  size: number;
  colors: string[];
  /** Each segment's share in the two views; the donut moves between them as `progress` goes 0 → 1. */
  from: number[];
  to: number[];
  progress: SharedValue<number>;
  label: string;
};

/** A ring split into rounded segments, one per level, whose lengths morph between two sets of shares. */
export function LevelDonut({ size, colors, from, to, progress, label }: Props) {
  const radius = (size - STROKE) / 2 - GLOW_BLUR / 2;
  const centre = size / 2;
  // Round ends reach half a stroke past each segment, so the gap in degrees counts them in.
  const gapDegrees = (((GAP + STROKE) / radius) * 180) / Math.PI;
  const oval = Skia.XYWHRect(centre - radius, centre - radius, radius * 2, radius * 2);

  const paths = useDerivedValue(() => {
    const p = progress.get();
    const shares: number[] = [];
    let total = 0;
    for (let i = 0; i < from.length; i++) {
      const share = Math.max(0, from[i] + (to[i] - from[i]) * p);
      shares.push(share);
      total += share;
    }
    const free = 360 - gapDegrees * from.length;
    let start = -90 + gapDegrees / 2;
    const out: SkPath[] = [];
    for (let i = 0; i < shares.length; i++) {
      // A segment with almost nothing left is still a dot, as the round ends draw it.
      const sweep = Math.max(0.01, total > 0 ? (shares[i] / total) * free : 0);
      out.push(Skia.PathBuilder.Make().addArc(oval, start, sweep).build());
      start += sweep + gapDegrees;
    }
    return out;
  });

  return (
    <View style={{ width: size, height: size }}>
      <Canvas style={{ width: size, height: size }}>
        {colors.map((color, i) => (
          <Segment key={color} index={i} paths={paths} color={color} />
        ))}
      </Canvas>
      <View style={styles.centre} pointerEvents="none">
        <Text style={styles.label}>{label}</Text>
      </View>
    </View>
  );
}

function Segment({ index, paths, color }: { index: number; paths: SharedValue<SkPath[]>; color: string }) {
  const path = useDerivedValue(() => paths.get()[index] ?? Skia.PathBuilder.Make().build());
  return (
    <Group>
      <Path path={path} style="stroke" strokeWidth={STROKE} strokeCap="round" color={color} opacity={GLOW_ALPHA}>
        <BlurMask blur={GLOW_BLUR} style="normal" />
      </Path>
      <Path path={path} style="stroke" strokeWidth={STROKE} strokeCap="round" color={color} />
    </Group>
  );
}

const styles = StyleSheet.create({
  centre: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
});

import { Canvas, Circle, Group, Line, Path, Skia, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

const GRID = '#2C2C30';
const LINE = '#F4F4F5';
const HAIRLINE = '#4A4A50';
const HALO = 'rgba(255,255,255,0.14)';
const RING = '#17171A';

const LINE_WIDTH = 2;
const DOT = 2.6;
// The scrubbed dot: its radius at rest and grown, the halo around it, the ring that parts it from the line.
const PICKED = 3;
const PICKED_GROWN = 5;
const PICKED_HALO = 11;
const PICKED_RING = 1.5;
// What is left of the line and the other dots while one is scrubbed.
const DIMMED = 0.35;

export type Plot = { left: number; top: number; width: number; height: number };

type Props = {
  width: number;
  height: number;
  plot: Plot;
  /** Horizontal grid lines, bottom to top, as fractions of the plot's height. */
  grid: number[];
  /** Every point in canvas coordinates, flattened as [x0, y0, x1, y1, …]. */
  points: SharedValue<number[]>;
  /** The scrubbed point, in canvas coordinates. */
  pickedX: SharedValue<number>;
  pickedY: SharedValue<number>;
  /** 0 at rest, 1 while a point is scrubbed. */
  scrub: SharedValue<number>;
  /** Where the hairline over the scrubbed point starts, from the top of the canvas. */
  hairlineTop: number;
  /** The whole drawing's opacity; dips briefly when the data jumps under reduced motion. */
  opacity: SharedValue<number>;
};

/** The chart card's drawing: grid lines, a line through every session with a dot on each, and the scrub mark. */
export function DotLineChart({ width, height, plot, grid, points, pickedX, pickedY, scrub, hairlineTop, opacity }: Props) {
  const gridPath = useMemo(() => {
    const builder = Skia.PathBuilder.Make();
    for (const at of grid) {
      const y = Math.round(plot.top + plot.height * (1 - at)) + 0.5;
      builder.moveTo(plot.left, y).lineTo(plot.left + plot.width, y);
    }
    return builder.build();
  }, [grid, plot]);

  const line = useDerivedValue(() => {
    const at = points.get();
    const builder = Skia.PathBuilder.Make();
    for (let i = 0; i + 1 < at.length; i += 2) {
      if (i === 0) builder.moveTo(at[i], at[i + 1]);
      else builder.lineTo(at[i], at[i + 1]);
    }
    return builder.build();
  });

  const dots = useDerivedValue(() => {
    const at = points.get();
    const builder = Skia.PathBuilder.Make();
    for (let i = 0; i + 1 < at.length; i += 2) builder.addCircle(at[i], at[i + 1], DOT);
    return builder.build();
  });

  const restOpacity = useDerivedValue(() => 1 - (1 - DIMMED) * scrub.get());
  const hairlineFrom = useDerivedValue(() => vec(pickedX.get(), hairlineTop));
  const hairlineTo = useDerivedValue(() => vec(pickedX.get(), plot.top + plot.height));
  const pickedRadius = useDerivedValue(() => PICKED + (PICKED_GROWN - PICKED) * scrub.get());
  const ringRadius = useDerivedValue(() => pickedRadius.get() + PICKED_RING);
  const haloRadius = useDerivedValue(() => PICKED_HALO * scrub.get());

  return (
    <Canvas style={{ width, height }}>
      <Path path={gridPath} style="stroke" strokeWidth={1} color={GRID} />
      <Group opacity={opacity}>
        <Group opacity={scrub}>
          <Line p1={hairlineFrom} p2={hairlineTo} strokeWidth={1} color={HAIRLINE} />
        </Group>
        <Group opacity={restOpacity}>
          <Path path={line} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={LINE} />
          <Path path={dots} color={LINE} />
        </Group>
        <Group opacity={scrub}>
          <Circle cx={pickedX} cy={pickedY} r={haloRadius} color={HALO} />
          <Circle cx={pickedX} cy={pickedY} r={ringRadius} color={RING} />
          <Circle cx={pickedX} cy={pickedY} r={pickedRadius} color={LINE} />
        </Group>
      </Group>
    </Canvas>
  );
}

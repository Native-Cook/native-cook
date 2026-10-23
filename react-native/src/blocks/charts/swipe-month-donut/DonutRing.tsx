import { Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { clamp01 } from './donut-data';

type Props = {
  size: number;
  stroke: number;
  /** The visible gap between two segments, in points along the ring. */
  gap: number;
  /** How far a tapped segment moves out. */
  pop: number;
  colors: string[];
  /** The share of each category, interpolated by the pager. */
  shares: SharedValue<number[]>;
  /** The tapped category, or -1, and how far it is out (0 to 1). */
  focus: SharedValue<number>;
  out: SharedValue<number>;
};

type Arc = { start: number; sweep: number; width: number; dx: number; dy: number };

const DEGREES = 180 / Math.PI;

/**
 * One segment of the ring. Its start and sweep come from the shares, not from a stored path, so a
 * segment that leaves shrinks into its neighbour's end and one that arrives grows out of it; one
 * shorter than its round caps is drawn as a dot that shrinks to nothing instead.
 */
function Segment({ index, color, arcs, size, inset }: { index: number; color: string; arcs: SharedValue<Arc[]>; size: number; inset: number }) {
  const path = useDerivedValue(() => {
    const arc = arcs.get()[index];
    const builder = Skia.PathBuilder.Make();
    builder.addArc({ x: inset, y: inset, width: size - inset * 2, height: size - inset * 2 }, arc.start, Math.max(arc.sweep, 0.01));
    return builder.build();
  });
  const width = useDerivedValue(() => arcs.get()[index].width);
  const transform = useDerivedValue(() => {
    const arc = arcs.get()[index];
    return [{ translateX: arc.dx }, { translateY: arc.dy }];
  });
  const opacity = useDerivedValue(() => (arcs.get()[index].width > 0.2 ? 1 : 0));
  return (
    <Group transform={transform} opacity={opacity}>
      <Path path={path} style="stroke" strokeWidth={width} strokeCap="round" color={color} />
    </Group>
  );
}

/** The month's spending as a ring of round-capped segments with gaps, drawn clockwise from the top. */
export function DonutRing({ size, stroke, gap, pop, colors, shares, focus, out }: Props) {
  // The canvas leaves room round the ring for a segment that pops out.
  const inset = stroke / 2 + pop + 1;
  const radius = size / 2 - inset;
  // Half a gap plus a round cap, as an angle: what each end of a segment gives up.
  const trim = ((stroke / 2 + gap / 2) / radius) * DEGREES;

  const arcs = useDerivedValue(() => {
    const list = shares.get();
    const tapped = focus.get();
    const away = out.get();
    const result: Arc[] = [];
    let at = -90;
    for (let i = 0; i < list.length; i++) {
      const raw = list[i] * 360;
      const sweep = raw - trim * 2;
      const mid = (at + raw / 2) / DEGREES;
      const shift = i === tapped ? pop * away : 0;
      result.push({
        start: sweep > 0 ? at + trim : at + raw / 2,
        sweep: Math.max(0, sweep),
        // Shorter than its two caps: a dot, as wide as there is room for.
        width: stroke * clamp01(raw / (trim * 2)),
        dx: Math.cos(mid) * shift,
        dy: Math.sin(mid) * shift,
      });
      at += raw;
    }
    return result;
  });

  return (
    <Canvas style={{ width: size, height: size }}>
      {colors.map((color, index) => (
        <Segment key={index} index={index} color={color} arcs={arcs} size={size} inset={inset} />
      ))}
    </Canvas>
  );
}

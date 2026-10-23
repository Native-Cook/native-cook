import { Blur, Canvas, Circle, ColorMatrix, Group, Paint, RoundedRect } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export type Box = { x: number; y: number; w: number; h: number };

// The pill is one drop of ink. Opening it, each edge travels on its own share of the morph, so the
// edge in front leaves first and the one behind catches up: the top rises before the bottom, the
// right edge runs out before the left, and the pill stretches like a drop pulled across glass.
// Each pair is [start, length] as shares of the morph (0…1). Played backwards for closing, the same
// shares make the bottom and the left lead the way down.
const TOP: [number, number] = [0, 0.8];
const BOTTOM: [number, number] = [0.14, 0.86];
const LEFT: [number, number] = [0.1, 0.8];
const RIGHT: [number, number] = [0, 0.75];
// A drop keeps its volume: for every point it is drawn out taller than it would be, it gets
// NARROWING pt narrower on each side.
const NARROWING = 0.4;
// Two smaller drops ride inside it, the magnifier's drop and a little one behind it, and lag
// further: they part from the pill on a thread of ink and merge into it again.
const DROP: [number, number] = [0.3, 0.7];
const TAIL: [number, number] = [0.44, 0.56];
export const DROP_RADIUS = 17;
const TAIL_RADIUS = 8;
// Where the magnifier sits, from the pill's left edge.
export const ICON_INSET = 25;
// The goo: everything is blurred by GOO_BLUR, then its alpha is pushed through a steep ramp
// (alpha × GOO_GAIN − GOO_CUT), so shapes that come close melt into one with a crisp edge.
const GOO_BLUR = 9;
const GOO_GAIN = 22;
const GOO_CUT = 10;
// A press pinches the pill in by PRESS_INSET pt all round.
const PRESS_INSET = 3;

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function phase(t: number, share: [number, number]) {
  'worklet';
  return easeInOut(clamp01((t - share[0]) / share[1]));
}

/** How far an edge with this share has moved; under reduced motion it jumps at the middle. */
function edge(t: number, share: [number, number], still: boolean) {
  'worklet';
  return still ? (t < 0.5 ? 0 : 1) : phase(t, share);
}

function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

/**
 * The pill's edges and its two drops at a point `t` of the morph from `closed` (0) to `open` (1).
 * Under reduced motion it is one or the other, never in between.
 */
export function pillAt(t: number, closed: Box, open: Box, still: boolean) {
  'worklet';
  const top = mix(closed.y, open.y, edge(t, TOP, still));
  const bottom = mix(closed.y + closed.h, open.y + open.h, edge(t, BOTTOM, still));
  const rest = mix(closed.h, open.h, edge(t, [0.07, 0.83], still));
  const stretch = Math.max(0, bottom - top - rest) * NARROWING;
  const left = mix(closed.x, open.x, edge(t, LEFT, still)) + stretch;
  const right = mix(closed.x + closed.w, open.x + open.w, edge(t, RIGHT, still)) - stretch;
  const drop = edge(t, DROP, still);
  const tail = edge(t, TAIL, still);
  return {
    left,
    top,
    right,
    bottom,
    dropX: mix(closed.x + ICON_INSET, open.x + ICON_INSET, drop),
    dropY: mix(closed.y + closed.h / 2, open.y + open.h / 2, drop),
    tailX: mix(closed.x + ICON_INSET + 14, open.x + ICON_INSET + 14, tail),
    tailY: mix(closed.y + closed.h / 2, open.y + open.h / 2, tail),
  };
}

type Props = {
  width: number;
  height: number;
  closed: Box;
  open: Box;
  t: SharedValue<number>;
  press: SharedValue<number>;
  still: boolean;
  color: string;
};

/** The ink pill, drawn through the goo filter. */
export function LiquidPill({ width, height, closed, open, t, press, still, color }: Props) {
  const shape = useDerivedValue(() => {
    const p = pillAt(t.get(), closed, open, still);
    const inset = PRESS_INSET * press.get();
    return { ...p, left: p.left + inset, right: p.right - inset, top: p.top + inset, bottom: p.bottom - inset };
  });
  const x = useDerivedValue(() => shape.get().left);
  const y = useDerivedValue(() => shape.get().top);
  // Never negative: a hard press on a pill squeezed by the stretch must not flip the rectangle.
  const w = useDerivedValue(() => Math.max(0, shape.get().right - shape.get().left));
  const h = useDerivedValue(() => Math.max(0, shape.get().bottom - shape.get().top));
  // Always fully round, so a drawn-out pill reads as a drop, never as a box.
  const r = useDerivedValue(() => Math.max(0, Math.min(shape.get().bottom - shape.get().top, shape.get().right - shape.get().left) / 2));
  const dropX = useDerivedValue(() => shape.get().dropX);
  const dropY = useDerivedValue(() => shape.get().dropY);
  const tailX = useDerivedValue(() => shape.get().tailX);
  const tailY = useDerivedValue(() => shape.get().tailY);
  // Under reduced motion the pill fades out at the jump and back in after it.
  const opacity = useDerivedValue(() => (still ? Math.abs(1 - 2 * t.get()) : 1));

  return (
    <Canvas pointerEvents="none" style={[styles.canvas, { width, height }]}>
      <Group
        opacity={opacity}
        layer={
          <Paint>
            <Blur blur={GOO_BLUR} />
            <ColorMatrix matrix={[1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, GOO_GAIN, -GOO_CUT]} />
          </Paint>
        }
      >
        <RoundedRect x={x} y={y} width={w} height={h} r={r} color={color} />
        <Circle cx={dropX} cy={dropY} r={DROP_RADIUS} color={color} />
        <Circle cx={tailX} cy={tailY} r={TAIL_RADIUS} color={color} />
      </Group>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});

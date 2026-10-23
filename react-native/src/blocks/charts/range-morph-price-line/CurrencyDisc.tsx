import { Canvas, Circle, Group, Rect, Skia } from '@shopify/react-native-skia';

const SIZE = 28;
const R = SIZE / 2;
const CLIP = Skia.Path.Circle(R, R, R);

// A blue disc with a ring of twelve gold dots.
const RING = 8.5;
const DOT = 1.5;
const RING_DOTS = Array.from({ length: 12 }, (_, i) => {
  const a = (i / 12) * Math.PI * 2;
  return { x: R + Math.cos(a) * RING, y: R + Math.sin(a) * RING };
});

// Thirteen red and white stripes under a blue corner.
const STRIPE = SIZE / 13;
const STRIPES = Array.from({ length: 7 }, (_, i) => i * 2 * STRIPE);

/** A flat round badge for a currency: a simplified flag, no detail that needs to be read. */
export function CurrencyDisc({ kind }: { kind: 'eur' | 'usd' }) {
  return (
    <Canvas style={{ width: SIZE, height: SIZE }}>
      <Group clip={CLIP}>
        {kind === 'eur' ? (
          <>
            <Rect x={0} y={0} width={SIZE} height={SIZE} color="#1f4fbf" />
            {RING_DOTS.map((dot, index) => (
              <Circle key={index} cx={dot.x} cy={dot.y} r={DOT} color="#ffd43a" />
            ))}
          </>
        ) : (
          <>
            <Rect x={0} y={0} width={SIZE} height={SIZE} color="#ffffff" />
            {STRIPES.map((y) => (
              <Rect key={y} x={0} y={y} width={SIZE} height={STRIPE} color="#d4313f" />
            ))}
            <Rect x={0} y={0} width={SIZE * 0.5} height={STRIPE * 7} color="#2b3f8c" />
          </>
        )}
      </Group>
      <Circle cx={R} cy={R} r={R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.35)" />
    </Canvas>
  );
}

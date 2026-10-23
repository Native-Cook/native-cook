import { Canvas, Circle, Group, Path, RadialGradient, Shadow, Skia, Text as SkiaText, matchFont, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';

// A disc of red sealing wax pressed with a monogram. The rim is not a circle: wax spreads unevenly,
// so its radius wanders by a few points on three ripples.
const SIZE = 92;
const RADIUS = 34;
const RIM = [
  { k: 5, amp: 1.7, phase: 0.3 },
  { k: 9, amp: 1.1, phase: 1.2 },
  { k: 14, amp: 0.6, phase: 2.1 },
];
const RING = 24;
const FAMILY = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';

function rimPath() {
  const c = SIZE / 2;
  const builder = Skia.PathBuilder.Make();
  const steps = 120;
  for (let i = 0; i <= steps; i += 1) {
    const a = (i / steps) * Math.PI * 2;
    const r = RIM.reduce((sum, w) => sum + w.amp * Math.sin(w.k * a + w.phase), RADIUS);
    const x = c + r * Math.cos(a);
    const y = c + r * Math.sin(a);
    if (i === 0) builder.moveTo(x, y);
    else builder.lineTo(x, y);
  }
  return builder.close().build();
}

const RIM_PATH = rimPath();

/** The wax seal, SIZE pt square. */
export function WaxSeal({ monogram }: { monogram: string }) {
  // Baskerville SemiBold, like every other serif on the page.
  const font = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: 21, fontWeight: '600' }), []);
  const width = useMemo(() => font.measureText(monogram).width, [font, monogram]);
  const c = SIZE / 2;
  const x = c - width / 2;
  const y = c + 7;
  return (
    <Canvas style={{ width: SIZE, height: SIZE }}>
      <Path path={RIM_PATH}>
        <RadialGradient c={vec(c - 9, c - 11)} r={RADIUS * 1.35} colors={['#c8333a', '#9d1b23', '#6a0d14']} positions={[0, 0.55, 1]} />
        <Shadow dx={0} dy={3} blur={5} color="rgba(30, 4, 6, 0.45)" />
      </Path>
      {/* The pressed ring: a dark groove with light catching its lower edge. */}
      <Circle cx={c} cy={c + 0.8} r={RING} style="stroke" strokeWidth={1.4} color="rgba(255, 190, 180, 0.22)" />
      <Circle cx={c} cy={c} r={RING} style="stroke" strokeWidth={1.6} color="rgba(60, 0, 8, 0.55)" />
      <Group>
        <SkiaText x={x + 0.6} y={y + 0.9} text={monogram} font={font} color="rgba(255, 190, 180, 0.28)" />
        <SkiaText x={x} y={y} text={monogram} font={font} color="#5c0910" />
      </Group>
      {/* A soft gloss on the upper left of the wax. */}
      <Circle cx={c - 13} cy={c - 14} r={9}>
        <RadialGradient c={vec(c - 13, c - 14)} r={9} colors={['rgba(255, 220, 215, 0.35)', 'rgba(255, 220, 215, 0)']} />
      </Circle>
    </Canvas>
  );
}

export const WAX_SEAL_SIZE = SIZE;

import { Canvas, Path, Skia } from '@shopify/react-native-skia';

const LINE = '#dedfe3';
const RINGS = 7;
const STEPS = 72;

// Where the contour maps sit, as fractions of the screen, and how wide their outer ring is.
const HILLS = [
  { x: 0.05, y: 0.04, reach: 0.34, phase: 0.4 },
  { x: 0.98, y: 0.62, reach: 0.4, phase: 2.1 },
  { x: 0.1, y: 0.97, reach: 0.3, phase: 4.2 },
];

/** A ring around (cx, cy) whose radius wobbles with a few fixed waves, as a contour line does. */
function ring(cx: number, cy: number, radius: number, phase: number) {
  let d = '';
  for (let i = 0; i <= STEPS; i++) {
    const angle = (i / STEPS) * Math.PI * 2;
    const wobble = 1 + 0.12 * Math.sin(angle * 3 + phase) + 0.07 * Math.sin(angle * 5 + phase * 2) + 0.04 * Math.sin(angle * 8 + phase * 3);
    const x = cx + Math.cos(angle) * radius * wobble;
    const y = cy + Math.sin(angle) * radius * wobble;
    d += `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  return Skia.Path.MakeFromSVGString(`${d}Z`)!;
}

/** The faint topographic lines behind the page. */
export function Contours({ width, height }: { width: number; height: number }) {
  return (
    <Canvas style={{ width, height, position: 'absolute' }} pointerEvents="none">
      {HILLS.flatMap((hill, h) =>
        Array.from({ length: RINGS }, (_, r) => (
          <Path
            key={`${h}-${r}`}
            path={ring(hill.x * width, hill.y * height, ((r + 1) / RINGS) * hill.reach * width, hill.phase + r * 0.15)}
            style="stroke"
            strokeWidth={1}
            color={LINE}
          />
        )),
      )}
    </Canvas>
  );
}

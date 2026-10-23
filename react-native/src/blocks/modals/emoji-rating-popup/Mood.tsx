import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Shadow,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export const FACE_SIZE = 84;
const CENTER = FACE_SIZE / 2;
const RING = 39;
const DISC = 32;
// The features are drawn for a face 50 across and scaled to the disc.
const FEATURE_SCALE = DISC / 25;

const EYE_X = 8;
const EYE_Y = -4;
const INK = '#3d2300';
const HEART_TOP = '#ff6b81';
const HEART_BOTTOM = '#e0193a';
const MOUTH_TOP = '#5a1020';
const MOUTH_BOTTOM = '#8e2034';
const TONGUE = '#ff7d8f';
const TEETH = '#ffffff';
const BLUSH = 'rgba(255,110,90,0.38)';

// Faces by place in the row, from the saddest to the happiest. The shapes sit around (0, 0).
const FROWN = Skia.Path.MakeFromSVGString('M-8 12Q0 4 8 12')!;
const BROW_LEFT = Skia.Path.MakeFromSVGString('M-12 -12Q-8 -14 -4 -11')!;
const BROW_RIGHT = Skia.Path.MakeFromSVGString('M12 -12Q8 -14 4 -11')!;
const FLAT = Skia.Path.MakeFromSVGString('M-7 9L7 9')!;
const SMILE = Skia.Path.MakeFromSVGString('M-9 6Q0 15 9 6')!;
const HAPPY_EYE = Skia.Path.MakeFromSVGString('M-4 2Q0 -5 4 2')!;
const OPEN_MOUTH = Skia.Path.MakeFromSVGString('M-12 2Q0 4 12 2Q11 18 0 18Q-11 18 -12 2Z')!;
const GRIN_TEETH = Skia.Path.MakeFromSVGString('M-11.4 2.6Q0 4.6 11.4 2.6L11 6Q0 7.6 -11 6Z')!;
const SMALL_OPEN_MOUTH = Skia.Path.MakeFromSVGString('M-9 5Q0 7 9 5Q8 16 0 16Q-8 16 -9 5Z')!;
const SMALL_TEETH = Skia.Path.MakeFromSVGString('M-8.6 5.4Q0 7.4 8.6 5.4L8.3 8.2Q0 9.8 -8.3 8.2Z')!;
const HEART = Skia.Path.MakeFromSVGString('M0 6.5C-10 0 -8 -9 0 -4.5C8 -9 10 0 0 6.5Z')!;

export const FACES = 5;

function Eyes() {
  return (
    <>
      {[-EYE_X, EYE_X].map((x) => (
        <Group key={x}>
          <Oval x={x - 2.6} y={EYE_Y - 3.8} width={5.2} height={7.6} color={INK} />
          <Circle cx={x + 0.8} cy={EYE_Y - 1.8} r={1.1} color="rgba(255,255,255,0.85)" />
        </Group>
      ))}
    </>
  );
}

function Stroke({ path, width = 2.6 }: { path: typeof FLAT; width?: number }) {
  return <Path path={path} style="stroke" strokeWidth={width} strokeCap="round" strokeJoin="round" color={INK} />;
}

function Cheeks() {
  return (
    <>
      <Oval x={-17} y={3} width={8} height={5} color={BLUSH}>
        <BlurMask blur={1.6} style="normal" />
      </Oval>
      <Oval x={9} y={3} width={8} height={5} color={BLUSH}>
        <BlurMask blur={1.6} style="normal" />
      </Oval>
    </>
  );
}

/** An open mouth with a dark gradient, a row of teeth and a tongue. */
function Mouth({ shape, teeth, tongueY, tongueR }: { shape: typeof FLAT; teeth: typeof FLAT; tongueY: number; tongueR: number }) {
  return (
    <Group clip={shape}>
      <Path path={shape}>
        <LinearGradient start={vec(0, 2)} end={vec(0, 18)} colors={[MOUTH_TOP, MOUTH_BOTTOM]} />
      </Path>
      <Path path={teeth} color={TEETH} />
      <Circle cx={0} cy={tongueY} r={tongueR} color={TONGUE} />
    </Group>
  );
}

/** One expression, shown at full strength when `face` equals its place and faded out a step away. */
function Expression({ face, place }: { face: SharedValue<number>; place: number }) {
  const opacity = useDerivedValue(() => Math.min(1, Math.max(0, 1 - Math.abs(face.get() - place))));
  return (
    <Group opacity={opacity} transform={[{ translateX: CENTER }, { translateY: CENTER }, { scale: FEATURE_SCALE }]}>
      {place === 0 && (
        <>
          <Stroke path={BROW_LEFT} width={2} />
          <Stroke path={BROW_RIGHT} width={2} />
          <Eyes />
          <Stroke path={FROWN} />
        </>
      )}
      {place === 1 && (
        <>
          <Eyes />
          <Stroke path={FLAT} />
        </>
      )}
      {place === 2 && (
        <>
          <Cheeks />
          <Eyes />
          <Stroke path={SMILE} />
        </>
      )}
      {place === 3 && (
        <>
          <Cheeks />
          <Group transform={[{ translateX: -EYE_X }, { translateY: EYE_Y - 1 }]}>
            <Stroke path={HAPPY_EYE} />
          </Group>
          <Group transform={[{ translateX: EYE_X }, { translateY: EYE_Y - 1 }]}>
            <Stroke path={HAPPY_EYE} />
          </Group>
          <Mouth shape={OPEN_MOUTH} teeth={GRIN_TEETH} tongueY={19} tongueR={6.5} />
        </>
      )}
      {place === 4 && (
        <>
          <Cheeks />
          {[-EYE_X - 1, EYE_X + 1].map((x) => (
            <Group key={x} transform={[{ translateX: x }, { translateY: EYE_Y - 1 }]}>
              <Path path={HEART}>
                <LinearGradient start={vec(0, -8)} end={vec(0, 7)} colors={[HEART_TOP, HEART_BOTTOM]} />
              </Path>
              <Oval x={-5.5} y={-5} width={3} height={2} color="rgba(255,255,255,0.7)" />
            </Group>
          ))}
          <Mouth shape={SMALL_OPEN_MOUTH} teeth={SMALL_TEETH} tongueY={17} tongueR={5} />
        </>
      )}
    </Group>
  );
}

/**
 * The emoji in the dialog's header: a glossy yellow disc in a thin ring, with five expressions drawn
 * on top of each other. `face` runs from 0 (frown) to 4 (heart eyes); between two whole numbers the
 * neighbouring expressions cross-fade.
 */
export function Mood({ face }: { face: SharedValue<number> }) {
  return (
    <Canvas style={{ width: FACE_SIZE, height: FACE_SIZE }}>
      <Circle cx={CENTER} cy={CENTER} r={RING} color="#ffffff">
        <Shadow dx={0} dy={3} blur={5} color="rgba(120,80,0,0.18)" />
      </Circle>
      <Circle cx={CENTER} cy={CENTER} r={RING - 1.5} style="stroke" strokeWidth={2} color="#f6d66a" />
      {/* The disc: warm yellow at the top to orange at the chin, a darker rim, a soft gloss. */}
      <Circle cx={CENTER} cy={CENTER} r={DISC}>
        <RadialGradient c={vec(CENTER, CENTER - DISC * 0.35)} r={DISC * 1.45} colors={['#ffe680', '#ffc53a', '#ff9a1f', '#f2731a']} positions={[0, 0.45, 0.8, 1]} />
      </Circle>
      <Circle cx={CENTER} cy={CENTER} r={DISC - 0.5} style="stroke" strokeWidth={1} color="rgba(214,110,0,0.35)" />
      <Oval x={CENTER - DISC * 0.55} y={CENTER - DISC * 0.88} width={DISC * 1.1} height={DISC * 0.5} color="rgba(255,255,255,0.45)">
        <BlurMask blur={3} style="normal" />
      </Oval>
      {Array.from({ length: FACES }, (_, place) => (
        <Expression key={place} face={face} place={place} />
      ))}
    </Canvas>
  );
}

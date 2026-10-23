import {
  BlurMask,
  Circle,
  Group,
  Oval,
  Path,
  RadialGradient,
  type SkFont,
  Text as SkiaText,
  vec,
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

/**
 * The faces painted on the rocks, in rock units (a 200 × 200 box centred on the rock). Each face
 * has two parts: the paint, which is clipped to the stone and takes its grain and shading, and
 * the stickers (googly eyes), which sit on top of it with their own little shadow.
 */

export const FACE_COUNT = 6;
/** How each face reads in the caption, in the same order as the faces. */
export const MOODS = ['all eyes', 'in a mood', 'smitten', 'freshly tagged', 'giddy', 'half asleep'];

export type FaceFonts = { love: SkFont; tag: SkFont; zed: SkFont };

type Eye = { x: number; y: number; r: number };
// Googly eyes of each face, if it has any.
const EYES: Eye[][] = [
  [
    { x: -30, y: -30, r: 25 },
    { x: 22, y: -28, r: 22 },
  ],
  [],
  [],
  [
    { x: -22, y: -46, r: 15 },
    { x: 12, y: -48, r: 14 },
  ],
  [
    { x: -27, y: -40, r: 19 },
    { x: 23, y: -41, r: 18 },
  ],
  [],
];

const INK = '#17171a';
// A pupil is this share of its eye, and travels at most this far from the centre.
const PUPIL = 0.55;
const PUPIL_TRAVEL = 0.38;

const HEART =
  'M 0 64 C -40 36 -84 8 -84 -24 C -84 -52 -60 -70 -37 -70 C -19 -70 -6 -59 0 -47 C 6 -59 19 -70 37 -70 C 60 -70 84 -52 84 -24 C 84 8 40 36 0 64 Z';
const GIDDY_MOUTH = 'M -48 0 C -30 -6 30 -6 48 0 C 48 50 -48 50 -48 0 Z';
const GIDDY_THROAT = 'M -37 7 C -22 3 22 3 37 7 C 35 38 -35 38 -37 7 Z';
const GIDDY_TONGUE = 'M -21 34 C -18 21 18 21 21 34 C 13 41 -13 41 -21 34 Z';

/** Paint softened by a halo of overspray, like a spray can held a little too far away. */
function Spray({ path, color, width }: { path: string; color: string; width?: number }) {
  const stroke = width !== undefined;
  return (
    <Group>
      <Path path={path} color={color} opacity={0.35} style={stroke ? 'stroke' : 'fill'} strokeWidth={(width ?? 0) + 7} strokeCap="round" strokeJoin="round">
        <BlurMask blur={5} style="normal" />
      </Path>
      <Path path={path} color={color} style={stroke ? 'stroke' : 'fill'} strokeWidth={width ?? 0} strokeCap="round" strokeJoin="round">
        <BlurMask blur={0.7} style="normal" />
      </Path>
    </Group>
  );
}

/** Text drawn like a spray-painted letter: a dark outline under a coloured fill. */
function Lettering({ text, font, x, y, fill, outline, width }: { text: string; font: SkFont; x: number; y: number; fill: string; outline: string; width: number }) {
  return (
    <Group>
      <SkiaText x={x + 2.5} y={y + 3} text={text} font={font} color={outline} opacity={0.5} style="stroke" strokeWidth={width} strokeJoin="round">
        <BlurMask blur={3} style="normal" />
      </SkiaText>
      <SkiaText x={x} y={y} text={text} font={font} color={outline} style="stroke" strokeWidth={width} strokeJoin="round" />
      <SkiaText x={x} y={y} text={text} font={font} color={fill} />
    </Group>
  );
}

/** The painted part of a face. */
export function FacePaint({ face, fonts }: { face: number; fonts: FaceFonts }) {
  switch (face) {
    case 0: {
      // Googly: a blue swoosh hugging the top of the stone and a black tag scrawled below.
      return (
        <Group>
          <Spray path="M -82 -18 C -78 -64 -28 -88 16 -84 C 54 -80 80 -58 86 -14" color="#3a8ed8" width={7} />
          <Spray path="M -70 -6 C -66 -48 -24 -70 14 -68" color="#3a8ed8" width={3} />
          <Spray
            path="M -56 28 C -62 4 -22 -6 -16 18 C -10 42 -46 52 -40 30 C -34 10 2 2 10 24 C 18 44 -6 54 0 34 C 6 10 40 6 46 28 C 50 48 24 56 30 38 C 34 22 60 22 64 40"
            color={INK}
            width={4.5}
          />
          <Spray path="M -52 54 C -20 64 22 66 58 54" color={INK} width={3.5} />
        </Group>
      );
    }
    case 1: {
      // Grumpy: a yellow disc with brows pulled down and a frown.
      return (
        <Group>
          <Group>
            <Circle cx={0} cy={4} r={70} color="#f1c42e" opacity={0.35}>
              <BlurMask blur={6} style="normal" />
            </Circle>
            <Circle cx={0} cy={4} r={64} color="#f1c42e">
              <BlurMask blur={0.8} style="normal" />
            </Circle>
          </Group>
          <Spray path="M -46 -28 L -12 -12" color="#2a1f14" width={8} />
          <Spray path="M 46 -30 L 12 -12" color="#2a1f14" width={8} />
          <Oval x={-28} y={-8} width={12} height={16} color="#2a1f14" />
          <Oval x={16} y={-8} width={12} height={16} color="#2a1f14" />
          <Spray path="M -27 38 Q 0 14 27 38" color="#2a1f14" width={7} />
        </Group>
      );
    }
    case 2: {
      // Smitten: a red heart with LOVE lettered across it.
      const width = fonts.love.measureText('LOVE').width;
      return (
        <Group>
          <Spray path={HEART} color="#d8343b" />
          <Path path={HEART} style="stroke" strokeWidth={3} color="#9d1c24" opacity={0.6}>
            <BlurMask blur={2} style="normal" />
          </Path>
          <Group transform={[{ rotate: -0.08 }]}>
            <SkiaText x={-width / 2} y={8} text="LOVE" font={fonts.love} color={INK} />
          </Group>
          <Spray path="M -40 24 Q 0 34 44 20" color={INK} width={4} />
        </Group>
      );
    }
    case 3: {
      // Tagged: bubble letters in teal with a dark outline.
      const width = fonts.tag.measureText('blub').width;
      return (
        <Group transform={[{ rotate: -0.12 }]}>
          <Lettering text="blub" font={fonts.tag} x={-width / 2} y={24} fill="#47b8c6" outline="#0f2f3b" width={9} />
          <Spray path="M -58 40 C -30 50 30 48 60 34" color="#0f2f3b" width={3} />
        </Group>
      );
    }
    case 4: {
      // Giddy: a wide purple grin.
      return (
        <Group>
          <Spray path={GIDDY_MOUTH} color="#8b3f99" />
          <Path path={GIDDY_THROAT} color="#3a1346" />
          <Path path={GIDDY_TONGUE} color="#e37b9c" />
          <Spray path="M -60 -6 Q -54 -14 -46 -10" color="#2d8c5c" width={5} />
          <Spray path="M 60 -6 Q 54 -14 46 -10" color="#2d8c5c" width={5} />
        </Group>
      );
    }
    default: {
      // Sleepy: closed eyes, rosy cheeks, a small "o" and a few z's.
      return (
        <Group>
          <Spray path="M -44 -14 Q -27 2 -10 -14" color={INK} width={5} />
          <Spray path="M 10 -14 Q 27 2 44 -14" color={INK} width={5} />
          <Spray path="M -40 -8 L -45 -1 M -27 -5 L -28 3 M -15 -8 L -11 -1" color={INK} width={2.5} />
          <Spray path="M 15 -8 L 11 -1 M 27 -5 L 28 3 M 40 -8 L 45 -1" color={INK} width={2.5} />
          <Circle cx={-40} cy={16} r={12} color="#ec8a8a" opacity={0.75}>
            <BlurMask blur={6} style="normal" />
          </Circle>
          <Circle cx={40} cy={16} r={12} color="#ec8a8a" opacity={0.75}>
            <BlurMask blur={6} style="normal" />
          </Circle>
          <Oval x={-7} y={26} width={14} height={17} style="stroke" strokeWidth={4} color={INK} />
          <Lettering text="z" font={fonts.zed} x={30} y={-42} fill="#f4f1ea" outline={INK} width={4} />
          <Group transform={[{ translateX: 52 }, { translateY: -56 }, { scale: 0.72 }]}>
            <Lettering text="z" font={fonts.zed} x={0} y={0} fill="#f4f1ea" outline={INK} width={4} />
          </Group>
        </Group>
      );
    }
  }
}

type PupilProps = { px: SharedValue<number>; py: SharedValue<number> };

/** One googly eye: a white dome with a loose black pupil that rolls with the rock's motion. */
function GooglyEye({ x, y, r, px, py }: Eye & PupilProps) {
  const pupil = useDerivedValue(() => [
    { translateX: px.get() * r * PUPIL_TRAVEL },
    { translateY: py.get() * r * PUPIL_TRAVEL },
  ]);
  return (
    <Group>
      <Circle cx={x + 1} cy={y + 3} r={r} color="rgba(30,22,14,0.45)">
        <BlurMask blur={3.5} style="normal" />
      </Circle>
      <Circle cx={x} cy={y} r={r}>
        <RadialGradient c={vec(x - r * 0.3, y - r * 0.35)} r={r * 1.35} colors={['#ffffff', '#f3f1ec', '#c7c3bb']} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={x} cy={y} r={r - 0.5} style="stroke" strokeWidth={1} color="rgba(0,0,0,0.22)" />
      <Group transform={pupil}>
        <Circle cx={x} cy={y} r={r * PUPIL} color="#0c0c0d" />
      </Group>
      <Oval x={x - r * 0.62} y={y - r * 0.72} width={r * 0.55} height={r * 0.32} color="rgba(255,255,255,0.8)" />
    </Group>
  );
}

/** The googly eyes of a face, drawn above the paint. */
export function FaceStickers({ face, px, py }: { face: number } & PupilProps) {
  const eyes = EYES[face] ?? [];
  if (eyes.length === 0) return null;
  return (
    <Group>
      {eyes.map((eye, k) => (
        <GooglyEye key={k} {...eye} px={px} py={py} />
      ))}
    </Group>
  );
}

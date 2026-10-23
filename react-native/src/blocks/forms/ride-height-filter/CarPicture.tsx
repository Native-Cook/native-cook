import {
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The picture is drawn in a box of BOX_WIDTH × BOX_HEIGHT and scaled to the width it is given: a
// planter with a bush and a round kerb on the left, the front quarter of a car on the right, all in
// the matte light greys of a clay render. Only the car's body moves; the wheel stays on the ground.
export const BOX_WIDTH = 496;
export const BOX_HEIGHT = 250;

const GROUND = 216;
const WHEEL = { x: 422, y: 155 };
const TYRE = 61;
const RIM = 49;

// The body at its lowest: the bonnet line from the right edge down to the nose, the front face,
// the splitter, the bumper's underside, the arch over the wheel, and the sill behind it. The arch
// is a circle of radius 70 round (422, 158): 3 below the wheel's centre, so 6 clear of the tyre.
const BODY = Skia.Path.MakeFromSVGString(
  'M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C266 104 260 120 256 140C254 152 251 162 249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L500 188Z',
)!;
// The arch's edge alone, for the lip that turns into it.
const ARCH_EDGE = Skia.Path.MakeFromSVGString('M356.2 181.9A70 70 0 1 1 491.3 167.7')!;
// What shows inside the arch: its dark liner, down to the sill.
const LINER = Skia.Path.MakeFromSVGString('M356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L354 188Z')!;
// The top of the wing: between the bonnet line and the crease that runs back from the headlight.
const SHOULDER = Skia.Path.MakeFromSVGString('M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C300 88 330 84 352 79C400 70 450 64 500 60Z')!;
const CREASE = Skia.Path.MakeFromSVGString('M352 79C400 70 450 64 500 60')!;
// The bulge of the bumper, from the grille round to the arch.
const BUMPER_LINE = Skia.Path.MakeFromSVGString('M262 131C290 128 328 120 354 104')!;
const UNDERSIDE = Skia.Path.MakeFromSVGString('M246 176C280 184 320 188 352 192')!;
const SPLITTER = Skia.Path.MakeFromSVGString('M249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L351.5 186C320 182.5 285 178 257 172.5Z')!;
const NOSE = Skia.Path.MakeFromSVGString('M272 92C266 104 260 120 256 140C254 152 251 162 249 171L275 176C278 150 286 118 300 92Z')!;
const INTAKE = Skia.Path.MakeFromSVGString('M265 142L309 132C312 131.4 314 132.6 314.8 135L321 159C321.6 161.4 321 163.6 319.4 165.4L313.6 172C312 173.8 309.8 174.4 307.6 173.6L270 161C267.6 160.2 266.2 158.4 266 156L264.4 144.6C264.2 143.2 264.2 142.2 265 142Z')!;
// The headlamp's lens under the bonnet's edge, and the bright strip at its outer end.
const LENS = Skia.Path.MakeFromSVGString('M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5C322 104 300 105 281 105.5C277 104 276 100 277 97Z')!;
const LENS_BROW = Skia.Path.MakeFromSVGString('M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5')!;
const HEADLIGHT = Skia.Path.MakeFromSVGString('M331 104C338 96 346 86 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5Z')!;
const HEADLIGHT_LINE = Skia.Path.MakeFromSVGString('M336 100C342 93 348 85 353.5 79')!;
const FOG = { x: 289, y: 151, r: 10 };

/** The grille seen from the side: short slats down the front face. */
function slats() {
  const builder = Skia.PathBuilder.Make();
  for (let y = 99; y <= 160; y += 3.4) {
    // The front face leans back: further left the lower it goes.
    const x = 268 - (y - 99) * 0.26;
    builder.moveTo(x + 1.5, y).lineTo(x + 8.5, y - 0.6);
  }
  return builder.build();
}

/** A mesh of crossed diagonals over the intake's box. */
function mesh() {
  const builder = Skia.PathBuilder.Make();
  for (let x = 220; x < 330; x += 3.6) {
    builder.moveTo(x, 180).lineTo(x + 52, 128);
    builder.moveTo(x + 52, 180).lineTo(x, 128);
  }
  return builder.build();
}

const SPOKES = 5;
const SPOKE_TURN = -95;

/** Five spokes round the origin: narrow at the hub, wide and a little twisted at the rim. */
function spokes() {
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < SPOKES; i++) {
    const at = ((SPOKE_TURN + (i * 360) / SPOKES) * Math.PI) / 180;
    const point = (radius: number, offset: number, twist: number) => {
      const angle = at + twist;
      return [Math.cos(angle) * radius - Math.sin(angle) * offset, Math.sin(angle) * radius + Math.cos(angle) * offset];
    };
    const [ax, ay] = point(8, -6.5, 0);
    const [bx, by] = point(46, -11.5, 0.1);
    const [cx, cy] = point(46, 11.5, 0.1);
    const [dx, dy] = point(8, 6.5, 0);
    builder.moveTo(ax, ay).lineTo(bx, by).lineTo(cx, cy).lineTo(dx, dy).close();
  }
  return builder.build();
}

/** One edge of every spoke, which is drawn darker to give them relief. */
function spokeEdges() {
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < SPOKES; i++) {
    const at = ((SPOKE_TURN + (i * 360) / SPOKES) * Math.PI) / 180;
    const twisted = at + 0.1;
    builder
      .moveTo(Math.cos(at) * 10 - Math.sin(at) * 6.5, Math.sin(at) * 10 + Math.cos(at) * 6.5)
      .lineTo(Math.cos(twisted) * 45 - Math.sin(twisted) * 11.5, Math.sin(twisted) * 45 + Math.cos(twisted) * 11.5);
  }
  return builder.build();
}

const SLATS = slats();
const MESH = mesh();
const SPOKE_PATH = spokes();
const SPOKE_EDGES = spokeEdges();

// The bush: a mound, dark where one looks into it, covered in leaves of four greys. The leaves are
// scattered by a fixed sequence, so the bush is the same every time.
const MOUND = Skia.Path.MakeFromSVGString('M-4 134C-6 112 2 92 20 84C28 68 52 60 66 70C82 62 104 70 108 88C122 94 128 114 124 134Z')!;
const LEAF_TONES = ['#c1bfc2', '#d2d0d3', '#e1dfe2', '#f0eef1'];
const LEAVES = 520;
// A leaf may start this far outside the mound, so that its outline is ragged.
const RAGGED = 3;

function leaves() {
  const builders = LEAF_TONES.map(() => Skia.PathBuilder.Make());
  let seed = 7;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  for (let i = 0; i < LEAVES; i++) {
    const x = -4 + random() * 130;
    const y = 62 + random() * 74;
    const turn = random() * Math.PI;
    const long = 3.2 + random() * 3.2;
    const wide = long * (0.36 + random() * 0.14);
    const shade = random();
    // Kept if it is in the mound, or just above its edge.
    if (!MOUND.contains(x, y) && !MOUND.contains(x, y + RAGGED)) continue;
    // Lighter towards the top left, where the light comes from.
    const light = 0.62 - (x - 60) / 260 - (y - 98) / 110 + (shade - 0.5) * 0.9;
    const tone = Math.max(0, Math.min(LEAF_TONES.length - 1, Math.floor(light * LEAF_TONES.length)));
    const cos = Math.cos(turn);
    const sin = Math.sin(turn);
    builders[tone]
      .moveTo(x - cos * long, y - sin * long)
      .quadTo(x - sin * wide * 2, y + cos * wide * 2, x + cos * long, y + sin * long)
      .quadTo(x + sin * wide * 2, y - cos * wide * 2, x - cos * long, y - sin * long)
      .close();
  }
  return builders.map((builder) => builder.build());
}

const LEAF_PATHS = leaves();

// How much lighter and smaller the body's shadow on the ground gets at full height.
const SHADOW_FADE = 0.45;

type Props = {
  width: number;
  /** How far the body is up from its lowest, in units of the box. */
  lift: SharedValue<number>;
  /** The body's lean about the wheel, in radians: positive lifts the nose. */
  pitch: SharedValue<number>;
  /** The lift at which the shadow is at its lightest. */
  maxLift: number;
};

/** The front quarter of a car beside a kerb. The body rides up and down on its wheel. */
export function CarPicture({ width, lift, pitch, maxLift }: Props) {
  const scale = width / BOX_WIDTH;
  const body = useDerivedValue(() => [{ translateY: -lift.get() }, { rotate: pitch.get() }]);
  const shadow = useDerivedValue(() => 1 - SHADOW_FADE * Math.min(1, Math.max(0, lift.get() / maxLift)));
  // The shadow spreads as the body leaves the ground.
  const shadowBlur = useDerivedValue(() => 5 + 4 * Math.min(1, Math.max(0, lift.get() / maxLift)));

  return (
    <Canvas style={{ width, height: BOX_HEIGHT * scale }}>
      <Group transform={[{ scale }]}>
        {/* The bush, the planter it grows in, and the kerb in front. */}
        <Path path={MOUND} color="#aeacaf" />
        {LEAF_PATHS.map((path, index) => (
          <Path key={index} path={path} color={LEAF_TONES[index]} />
        ))}
        <Rect x={-2} y={132} width={134} height={50}>
          <LinearGradient start={vec(0, 132)} end={vec(0, 182)} colors={['#d9d7da', '#cfcdd0']} />
        </Rect>
        <Path path="M132 132L139 130L139 178L132 182Z" color="#c1bfc2" />
        <Rect x={-2} y={131} width={134} height={2.2} color="#ecebee" />
        <Rect x={-4} y={176} width={146} height={7} color="rgba(0,0,0,0.16)">
          <BlurMask blur={3} style="normal" />
        </Rect>
        <Rect x={-10} y={GROUND} width={232} height={3} color="rgba(0,0,0,0.3)">
          <BlurMask blur={2.5} style="normal" />
        </Rect>
        <RoundedRect x={-12} y={174} width={231} height={44} r={9}>
          <LinearGradient start={vec(0, 174)} end={vec(0, 218)} colors={['#eae8eb', '#dddbde', '#c8c6c9']} positions={[0, 0.45, 1]} />
        </RoundedRect>
        <Group clip={Skia.RRectXY(Skia.XYWHRect(-12, 174, 231, 44), 9, 9)}>
          <Rect x={196} y={174} width={24} height={44} color="rgba(0,0,0,0.045)" />
          <Rect x={195.4} y={174} width={1.1} height={44} color="rgba(0,0,0,0.13)" />
          <Rect x={-12} y={174.6} width={231} height={1.4} color="rgba(255,255,255,0.7)" />
        </Group>

        {/* The car's shadow on the ground, and the tyre's own where it touches. */}
        <Oval x={262} y={208} width={250} height={13} color="rgba(0,0,0,0.36)" opacity={shadow}>
          <BlurMask blur={shadowBlur} style="normal" />
        </Oval>
        <Oval x={384} y={211} width={82} height={8} color="rgba(0,0,0,0.3)">
          <BlurMask blur={3} style="normal" />
        </Oval>

        {/* The body rides on the wheel: the arch's dark liner first, then the panels. The arch always clears the tyre, so the wheel is drawn last. */}
        <Group transform={body} origin={vec(WHEEL.x, WHEEL.y)}>
          <Path path={LINER}>
            <LinearGradient start={vec(0, 88)} end={vec(0, 190)} colors={['#313133', '#48484a', '#757376']} positions={[0, 0.55, 1]} />
          </Path>
          <Path path={BODY}>
            <LinearGradient start={vec(0, 36)} end={vec(0, 192)} colors={['#e9e7ea', '#e3e1e4', '#dedcdf', '#d1cfd2']} positions={[0, 0.3, 0.6, 1]} />
          </Path>
          <Group clip={BODY}>
            {/* Soft shading: the wing's top catches the light, the front face and the underside turn away from it. */}
            <Path path={SHOULDER} color="#f4f2f5">
              <BlurMask blur={2.5} style="normal" />
            </Path>
            <Path path={CREASE} style="stroke" strokeWidth={5} color="rgba(0,0,0,0.07)" transform={[{ translateY: 4 }]}>
              <BlurMask blur={3} style="normal" />
            </Path>
            <Path path={NOSE} color="rgba(0,0,0,0.05)">
              <BlurMask blur={7} style="normal" />
            </Path>
            <Path path={BUMPER_LINE} style="stroke" strokeWidth={5} color="rgba(0,0,0,0.085)" transform={[{ translateY: 3.5 }]}>
              <BlurMask blur={3} style="normal" />
            </Path>
            <Path path={BUMPER_LINE} style="stroke" strokeWidth={1.4} strokeCap="round" color="rgba(255,255,255,0.75)">
              <BlurMask blur={0.7} style="normal" />
            </Path>
            <Path path={UNDERSIDE} style="stroke" strokeWidth={12} color="rgba(0,0,0,0.1)">
              <BlurMask blur={5} style="normal" />
            </Path>
            <Path path={ARCH_EDGE} style="stroke" strokeWidth={9} color="rgba(0,0,0,0.11)">
              <BlurMask blur={3} style="normal" />
            </Path>
            <Path path={ARCH_EDGE} style="stroke" strokeWidth={2.2} color="rgba(255,255,255,0.55)">
              <BlurMask blur={0.6} style="normal" />
            </Path>
          </Group>

          <Path path={SLATS} style="stroke" strokeWidth={1.3} strokeCap="round" color="rgba(0,0,0,0.2)" />

          <Path path={INTAKE}>
            <LinearGradient start={vec(0, 132)} end={vec(0, 174)} colors={['#a3a1a4', '#b9b7ba']} />
          </Path>
          <Group clip={INTAKE}>
            <Path path={MESH} style="stroke" strokeWidth={0.8} color="rgba(255,255,255,0.42)" />
            <Path path={INTAKE} style="stroke" strokeWidth={5} color="rgba(0,0,0,0.2)">
              <BlurMask blur={2.5} style="normal" />
            </Path>
          </Group>
          <Path path={INTAKE} style="stroke" strokeWidth={1.2} color="rgba(255,255,255,0.7)" />
          <Circle cx={FOG.x} cy={FOG.y + 1.2} r={FOG.r + 1} color="rgba(0,0,0,0.22)">
            <BlurMask blur={2} style="normal" />
          </Circle>
          <Circle cx={FOG.x} cy={FOG.y} r={FOG.r}>
            <RadialGradient c={vec(FOG.x - 3, FOG.y - 4)} r={FOG.r * 1.6} colors={['#fbf9fc', '#dedcdf']} />
          </Circle>
          <Circle cx={FOG.x} cy={FOG.y} r={FOG.r - 3.2} style="stroke" strokeWidth={1} color="rgba(0,0,0,0.1)" />

          <Path path={LENS} color="#ecebee" />
          <Group clip={LENS}>
            <Path path={LENS_BROW} style="stroke" strokeWidth={7} color="rgba(0,0,0,0.16)">
              <BlurMask blur={2.5} style="normal" />
            </Path>
          </Group>
          <Path path={LENS} style="stroke" strokeWidth={0.8} strokeJoin="round" color="rgba(0,0,0,0.1)" />
          <Path path={HEADLIGHT} color="#f9f7fa" />
          <Path path={HEADLIGHT} style="stroke" strokeWidth={1} strokeJoin="round" color="rgba(0,0,0,0.16)" />
          <Path path={HEADLIGHT_LINE} style="stroke" strokeWidth={0.9} color="rgba(0,0,0,0.14)" />

          <Path path={SPLITTER} color="#efedf0" />
          <Path path={SPLITTER} style="stroke" strokeWidth={0.8} strokeJoin="round" color="rgba(0,0,0,0.12)" />
        </Group>

        <Group transform={[{ translateX: WHEEL.x }, { translateY: WHEEL.y }]}>
          <Circle cx={0} cy={0} r={TYRE}>
            <RadialGradient c={vec(-16, -26)} r={TYRE * 1.5} colors={['#f1eff2', '#e2e0e3', '#c9c7ca']} positions={[0, 0.6, 1]} />
          </Circle>
          <Circle cx={0} cy={0} r={TYRE - 2.4} style="stroke" strokeWidth={3.6} color="rgba(0,0,0,0.075)">
            <DashPathEffect intervals={[1.6, 4.54]} />
          </Circle>
          <Circle cx={0} cy={0} r={RIM + 1} style="stroke" strokeWidth={1.6} color="rgba(0,0,0,0.14)">
            <BlurMask blur={1} style="normal" />
          </Circle>
          {/* Behind the spokes: the brake disc in the shade of the barrel. */}
          <Circle cx={0} cy={0} r={RIM - 2}>
            <RadialGradient c={vec(0, 0)} r={RIM - 2} colors={['#b4b2b5', '#aba9ac', '#8b898c', '#7e7c7f']} positions={[0, 0.58, 0.62, 1]} />
          </Circle>
          <Path path={SPOKE_PATH}>
            <RadialGradient c={vec(-10, -16)} r={RIM * 1.5} colors={['#f4f2f5', '#e1dfe2']} />
          </Path>
          <Path path={SPOKE_EDGES} style="stroke" strokeWidth={1.6} color="rgba(0,0,0,0.14)">
            <BlurMask blur={0.8} style="normal" />
          </Path>
          <Circle cx={0} cy={0} r={RIM - 2.5} style="stroke" strokeWidth={5}>
            <RadialGradient c={vec(-14, -22)} r={RIM * 1.6} colors={['#f5f3f6', '#dcdadd']} />
          </Circle>
          <Circle cx={0} cy={0} r={12.5}>
            <RadialGradient c={vec(-3, -5)} r={16} colors={['#f3f1f4', '#d9d7da']} />
          </Circle>
          {Array.from({ length: SPOKES }, (_, index) => {
            const at = ((SPOKE_TURN + 36 + (index * 360) / SPOKES) * Math.PI) / 180;
            return <Circle key={index} cx={Math.cos(at) * 8} cy={Math.sin(at) * 8} r={1.7} color="#b3b1b4" />;
          })}
          <Circle cx={0} cy={0} r={3.6} color="#cfcdd0" />
        </Group>
      </Group>
    </Canvas>
  );
}

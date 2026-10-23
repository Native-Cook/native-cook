import { BlurMask, Group, LinearGradient, Oval, Path, Rect, Skia, rect, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// A paper cup seen a little from above, drawn round the middle of its base: x to the right, y down,
// so the cup stands in negative y. Every circle of the cup shows as an ellipse this flat.
const FLAT = 0.27;
// Radii of the body at its base and at its mouth; the body's height is what stretches.
const BASE = 22;
const MOUTH = 30;
// The sleeve keeps its height. It sits this far under the mouth on the shortest cup (body
// SHORTEST) and slides down by SLEEVE_SLIDE of whatever a taller body adds; it stands SLEEVE_PAD
// off the paper.
const SLEEVE = 31;
const SLEEVE_DROP = 15;
const SLEEVE_SLIDE = 0.35;
const SLEEVE_PAD = 1.2;
const SHORTEST = 62;
// The lid: a rim wider than the mouth, and a lower dome on it that narrows to its top.
const RIM = 35.5;
const RIM_HEIGHT = 6;
const DOME = 29.5;
const DOME_TOP = 27;
const DOME_HEIGHT = 7.5;
// Radians the lid tips for each point it is lifted.
const LID_TIP = 0.008;

const PAPER = ['#C9BFAE', '#F7F2E8', '#E6DECF', '#AEA391'];
const KRAFT = ['#86542A', '#BC844C', '#A9713D', '#744721'];
const LID = ['#D3CABB', '#FCF9F2', '#EDE6D9', '#B9AE9C'];
const SHADE_AT = [0, 0.3, 0.64, 1];
const LID_TOP = ['#FFFDF8', '#E9E1D3'];
const BEAN = '#3A2315';
const INSIDE = ['#6B4A30', '#2A1C12'];

/** How wide a cup is, and how tall with a body of `body` points. */
export const CUP_WIDTH = RIM * 2;

export function cupHeight(body: number) {
  'worklet';
  return body + RIM_HEIGHT + DOME_HEIGHT + DOME_TOP * FLAT + BASE * FLAT;
}

/** The body's radius at height `y` (negative, up from the base). */
function radiusAt(y: number, body: number) {
  'worklet';
  return BASE + ((MOUTH - BASE) * -y) / body;
}

function sleeveTop(body: number) {
  'worklet';
  return -body + SLEEVE_DROP + SLEEVE_SLIDE * (body - SHORTEST);
}

/** The paper: straight sides from the mouth down to the front half of the base's ellipse. */
function paperOutline(body: number) {
  'worklet';
  return `M${-MOUTH} ${-body}L${-BASE} 0A${BASE} ${BASE * FLAT} 0 0 0 ${BASE} 0L${MOUTH} ${-body}Z`;
}

/** The sleeve: a band of the cone between two front halves of ellipses. */
function sleeveOutline(body: number) {
  'worklet';
  const top = sleeveTop(body);
  const bottom = top + SLEEVE;
  const wide = radiusAt(top, body) + SLEEVE_PAD;
  const narrow = radiusAt(bottom, body) + SLEEVE_PAD;
  return `M${-wide} ${top}A${wide} ${wide * FLAT} 0 0 0 ${wide} ${top}L${narrow} ${bottom}A${narrow} ${narrow * FLAT} 0 0 1 ${-narrow} ${bottom}Z`;
}

// The lid, round the middle of the mouth: the rim's wall and the dome's wall (each the band between
// the front halves of two ellipses), and a coffee bean round the origin with the line down it.
const RIM_WALL = Skia.Path.MakeFromSVGString(
  `M${-RIM} ${-RIM_HEIGHT}L${-RIM} 1A${RIM} ${RIM * FLAT} 0 0 0 ${RIM} 1L${RIM} ${-RIM_HEIGHT}A${RIM} ${RIM * FLAT} 0 0 1 ${-RIM} ${-RIM_HEIGHT}Z`,
)!;
const DOME_WALL = Skia.Path.MakeFromSVGString(
  `M${-DOME} ${-RIM_HEIGHT}A${DOME} ${DOME * FLAT} 0 0 0 ${DOME} ${-RIM_HEIGHT}L${DOME_TOP} ${-RIM_HEIGHT - DOME_HEIGHT}A${DOME_TOP} ${DOME_TOP * FLAT} 0 0 1 ${-DOME_TOP} ${-RIM_HEIGHT - DOME_HEIGHT}Z`,
)!;
const BEAN_LINE = Skia.Path.MakeFromSVGString('M0.6 -8.4C4 -3.4 -3.8 2.6 -0.4 8.4')!;

/** The box of the ellipse a circle of `radius` shows as, `y` above or below the origin. */
function oval(y: number, radius: number) {
  return rect(-radius, y - radius * FLAT, radius * 2, radius * 2 * FLAT);
}

export type CupShape = ReturnType<typeof useCupShape>;

/**
 * The parts of a cup that follow its body's height and its lid's lift. Cups of the same size share
 * one shape, so their paths are built once a frame.
 */
export function useCupShape(body: SharedValue<number>, lift: SharedValue<number>) {
  const paper = useDerivedValue(() => Skia.Path.MakeFromSVGString(paperOutline(body.get()))!);
  const sleeve = useDerivedValue(() => Skia.Path.MakeFromSVGString(sleeveOutline(body.get()))!);
  // The beans sit in the middle of the sleeve's front, which hangs lower than its sides.
  const mark = useDerivedValue(() => {
    const middle = sleeveTop(body.get()) + SLEEVE / 2;
    return [{ translateY: middle + (radiusAt(middle, body.get()) + SLEEVE_PAD) * FLAT * 0.96 }];
  });
  const mouth = useDerivedValue(() => [{ translateY: -body.get() }]);
  const lid = useDerivedValue(() => [{ translateY: -body.get() - lift.get() }, { rotate: lift.get() * LID_TIP }]);
  // The lid's shadow on the paper, which fades as the lid leaves it.
  const shadowFrom = useDerivedValue(() => vec(0, -body.get()));
  const shadowTo = useDerivedValue(() => vec(0, -body.get() + 22));
  const shadow = useDerivedValue(() => 1 / (1 + lift.get() / 6));
  const shadowBox = useDerivedValue(() => rect(-MOUTH, -body.get(), MOUTH * 2, 34));
  return { paper, sleeve, mark, mouth, lid, shadowFrom, shadowTo, shadow, shadowBox };
}

/** One cup, standing on the origin. */
export function Cup({ shape }: { shape: CupShape }) {
  return (
    <>
      <Oval x={-BASE - 9} y={-4} width={BASE * 2 + 22} height={15} color="rgba(0,0,0,0.5)">
        <BlurMask blur={5} style="normal" />
      </Oval>

      <Path path={shape.paper}>
        <LinearGradient start={vec(-MOUTH, 0)} end={vec(MOUTH, 0)} colors={PAPER} positions={SHADE_AT} />
      </Path>
      <Group clip={shape.paper}>
        <Rect rect={shape.shadowBox} opacity={shape.shadow}>
          <LinearGradient start={shape.shadowFrom} end={shape.shadowTo} colors={['rgba(40,28,14,0.42)', 'rgba(40,28,14,0)']} />
        </Rect>
      </Group>
      <Path path={shape.sleeve}>
        <LinearGradient start={vec(-MOUTH, 0)} end={vec(MOUTH, 0)} colors={KRAFT} positions={SHADE_AT} />
      </Path>
      <Group transform={shape.mark}>
        <Group transform={[{ translateX: -5.5 }, { rotate: -0.42 }]}>
          <Oval x={-6.4} y={-9} width={12.8} height={18} color={BEAN} />
          <Path path={BEAN_LINE} style="stroke" strokeWidth={1.3} strokeCap="round" color={KRAFT[2]} />
        </Group>
        <Group transform={[{ translateX: 6.5 }, { translateY: 0.5 }, { rotate: 0.36 }]}>
          <Oval x={-6.4} y={-9} width={12.8} height={18} color={BEAN} />
          <Path path={BEAN_LINE} style="stroke" strokeWidth={1.3} strokeCap="round" color={KRAFT[2]} />
        </Group>
      </Group>

      {/* The open mouth, which shows only while the lid is in the air. */}
      <Group transform={shape.mouth}>
        <Oval rect={oval(0, MOUTH)} color={PAPER[1]} />
        <Oval rect={oval(0.6, MOUTH - 2.4)}>
          <LinearGradient start={vec(0, -MOUTH * FLAT)} end={vec(0, MOUTH * FLAT)} colors={INSIDE} />
        </Oval>
      </Group>

      <Group transform={shape.lid}>
        <Path path={RIM_WALL}>
          <LinearGradient start={vec(-RIM, 0)} end={vec(RIM, 0)} colors={LID} positions={SHADE_AT} />
        </Path>
        <Oval rect={oval(-RIM_HEIGHT, RIM)}>
          <LinearGradient start={vec(-RIM, 0)} end={vec(RIM, 0)} colors={['#E4DCCE', '#FBF8F1', '#D6CDBE']} />
        </Oval>
        <Path path={DOME_WALL}>
          <LinearGradient start={vec(-DOME, 0)} end={vec(DOME, 0)} colors={LID} positions={SHADE_AT} />
        </Path>
        <Oval rect={oval(-RIM_HEIGHT - DOME_HEIGHT, DOME_TOP)}>
          <LinearGradient start={vec(-DOME_TOP, -RIM_HEIGHT - DOME_HEIGHT - 6)} end={vec(DOME_TOP, -RIM_HEIGHT - DOME_HEIGHT + 6)} colors={LID_TOP} />
        </Oval>
        {/* The sunken middle of the lid and the hole to drink from. */}
        <Oval rect={oval(-RIM_HEIGHT - DOME_HEIGHT + 0.7, DOME_TOP - 5.5)}>
          <LinearGradient start={vec(-DOME_TOP, 0)} end={vec(DOME_TOP, 0)} colors={['#E2DACB', '#F6F1E7', '#DDD4C4']} />
        </Oval>
        <Oval x={-4.2} y={-RIM_HEIGHT - DOME_HEIGHT - DOME_TOP * FLAT * 0.62 - 1.5} width={8.4} height={3} color="#74685A" />
      </Group>
    </>
  );
}

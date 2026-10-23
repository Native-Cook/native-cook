import {
  BlurMask,
  Circle,
  Group,
  ImageShader,
  LinearGradient,
  Oval,
  Path,
  RoundedRect,
  Skia,
  type SkImage,
  type SkPath,
  type SkRect,
  vec,
} from '@shopify/react-native-skia';
import { Easing, type SharedValue, useDerivedValue } from 'react-native-reanimated';

/** One jar of the onboarding scene, in pt for a 402 pt wide screen (scaled by the block). */
export type JarSpec = {
  /** Unsplash photo id painted onto the glaze. */
  photo: string;
  /** Centre of the jar's mouth, from the middle of the jar stage. */
  x: number;
  y: number;
  /** Widest part of the body, its height from the rim to the foot, and the mouth's width. */
  body: number;
  height: number;
  mouth: number;
  /** Resting tilt, in radians. */
  tilt: number;
  /** Seconds per bob, and where in the bob it starts. */
  period: number;
  phase: number;
};

/** A coin in flight: when it appeared (block time, s), the jar it falls into and its sideways offset. */
export type Coin = { t0: number; jar: number; dx: number; user: boolean };

export const COIN_SLOTS = 5;
export const NO_COIN: Coin = { t0: -100, jar: 0, dx: 0, user: false };

// A coin hovers, spinning, for COIN_HOVER s while it sinks COIN_SINK pt, then falls with gravity.
export const COIN_HOVER = 0.32;
export const COIN_SINK = 8;
export const COIN_GRAVITY = 1900;
const COIN_R = 12;
// Thickness of the coin's milled edge, seen as it turns.
const COIN_EDGE = 2.4;
// Spin while hovering and the extra spin a fall adds, in rad/s.
const SPIN = 7;
const SPIN_FALL = 9;

// The lip stands this far proud of the opening; the opening is seen from above, squashed to RIM_SQUASH.
const LIP = 4;
// The glaze photo is drawn this many times larger than the jar.
const GLAZE_ZOOM = 1.7;
const RIM_SQUASH = 0.34;

// A landing coin dips the jar by up to DIP pt, rings at DIP_FREQ rad/s and dies away at DIP_DECAY /s.
const DIP = 7;
const DIP_FREQ = 19;
const DIP_DECAY = 6;
// Gentle float: BOB pt up and down, WOBBLE rad of sway.
const BOB = 6;
const WOBBLE = 0.025;
// The jars come in over this share of the board's entrance, one after another.
const APPEAR_FROM = 0.2;
const APPEAR_SPAN = 0.4;
const APPEAR_LAG = 0.09;
// Dragging tilts a jar by its sideways speed, up to TILT_MAX rad.
const TILT_PER_SPEED = 0.00035;
const TILT_MAX = 0.4;

const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);

function seg(v: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (v - a) / (b - a)));
}

/** Seconds from a coin's appearance to the moment it drops through the rim of a jar `drop` pt below. */
export function landingTime(drop: number) {
  'worklet';
  return COIN_HOVER + Math.sqrt((2 * Math.max(0, drop - COIN_SINK)) / COIN_GRAVITY);
}

export type JarGeometry = {
  body: SkPath;
  rimFront: SkPath;
  lipFront: SkPath;
  back: SkRect;
  opening: SkRect;
  bounds: SkRect;
  /** Half the body's width and its height, in pt. */
  half: number;
  height: number;
};

/**
 * The jar's outline in its own space: the origin is the centre of the mouth. The body's top edge is
 * the near half of the opening, so whatever is drawn before the body (a coin) disappears into it.
 */
export function jarGeometry(spec: JarSpec, k: number): JarGeometry {
  const rin = (spec.mouth / 2) * k;
  const rout = rin + LIP * k;
  const ry = rin * RIM_SQUASH;
  const ryo = rout * RIM_SQUASH;
  const bw = (spec.body / 2) * k;
  const h = spec.height * k;
  const neck = rout * 0.9;
  const neckY = h * 0.14;
  const collar = 7 * k;
  const opening = Skia.XYWHRect(-rin, -ry, rin * 2, ry * 2);
  const back = Skia.XYWHRect(-rout, -ryo, rout * 2, ryo * 2);

  const body = Skia.PathBuilder.Make();
  body.moveTo(-rout, 0);
  body.lineTo(-rin, 0);
  // From the left of the opening round its near side to the right.
  body.arcToOval(opening, 180, -180, false);
  body.lineTo(rout, 0);
  body.lineTo(rout, collar);
  body.lineTo(neck, collar + 3 * k);
  body.lineTo(neck, neckY);
  body.cubicTo(neck, neckY + h * 0.1, bw, neckY + h * 0.04, bw, neckY + h * 0.24);
  body.cubicTo(bw * 1.03, h * 0.62, bw, h * 0.86, bw * 0.84, h - 4 * k);
  body.quadTo(0, h + ry * 1.5, -bw * 0.84, h - 4 * k);
  body.cubicTo(-bw, h * 0.86, -bw * 1.03, h * 0.62, -bw, neckY + h * 0.24);
  body.cubicTo(-bw, neckY + h * 0.04, -neck, neckY + h * 0.1, -neck, neckY);
  body.lineTo(-neck, collar + 3 * k);
  body.lineTo(-rout, collar);
  body.close();
  const bodyPath = body.build();

  const rimFront = Skia.PathBuilder.Make().addArc(opening, 180, -180).build();
  const lipFront = Skia.PathBuilder.Make().addArc(back, 172, -164).build();

  return {
    body: bodyPath,
    rimFront,
    lipFront,
    back,
    opening,
    bounds: Skia.XYWHRect(-bw * 1.05, -ryo, bw * 2.1, h + ryo + ry * 1.5),
    half: bw,
    height: h,
  };
}

type CoinProps = {
  slot: number;
  jar: number;
  /** Fall from the coins' line down to this jar's rim, in pt. */
  drop: number;
  coins: SharedValue<Coin[]>;
  time: SharedValue<number>;
  reducedMotion: boolean;
};

/** A gold coin that spins above a jar, then drops into it. Drawn in the jar's space. */
function JarCoin({ slot, jar, drop, coins, time, reducedMotion }: CoinProps) {
  const state = useDerivedValue(() => {
    const coin = coins.get()[slot];
    const t = time.get() - coin.t0;
    if (coin.jar !== jar || t < 0 || t > 2) return { x: 0, y: 0, spin: 0, alpha: 0, scale: 1 };
    if (reducedMotion) {
      // No fall: the coin fades in at the mouth and fades away into it.
      const a = seg(t, 0, 0.15) * (1 - seg(t, 0.45, 0.7));
      return { x: 0, y: -14, spin: 0, alpha: a, scale: 1 };
    }
    const land = landingTime(drop) - COIN_HOVER;
    const tf = Math.max(0, t - COIN_HOVER);
    const y =
      t < COIN_HOVER
        ? -drop + COIN_SINK * EASE_OUT(t / COIN_HOVER)
        : -drop + COIN_SINK + 0.5 * COIN_GRAVITY * tf * tf;
    // Fully swallowed once it is a coin's height inside the rim.
    const alpha = seg(t, 0, 0.14) * (y > COIN_R * 3 ? 0 : 1);
    return {
      x: coin.dx * (1 - EASE_IN_OUT(Math.min(1, tf / Math.max(land, 0.01)))),
      y,
      spin: SPIN * t + SPIN_FALL * tf,
      alpha,
      scale: 0.7 + 0.3 * EASE_OUT(seg(t, 0, 0.25)),
    };
  });
  const place = useDerivedValue(() => {
    const c = state.get();
    return [{ translateX: c.x }, { translateY: c.y }, { scale: c.scale }];
  });
  const opacity = useDerivedValue(() => state.get().alpha);
  // The coin turns about its horizontal axis: its face flattens, and its edge shows under it.
  const face = useDerivedValue(() => [{ scaleY: Math.max(0.07, Math.abs(Math.cos(state.get().spin))) }]);
  const edge = useDerivedValue(() => {
    const spin = state.get().spin;
    return [{ translateY: COIN_EDGE * Math.abs(Math.sin(spin)) }, { scaleY: Math.max(0.07, Math.abs(Math.cos(spin))) }];
  });
  return (
    <Group transform={place} opacity={opacity}>
      <Group transform={edge}>
        <Circle cx={0} cy={0} r={COIN_R} color="#94600f" />
      </Group>
      <Group transform={face}>
        <Circle cx={0} cy={0} r={COIN_R}>
          <LinearGradient start={vec(-COIN_R, -COIN_R)} end={vec(COIN_R, COIN_R)} colors={['#fff4bf', '#f5c64f', '#c58a1e']} />
        </Circle>
        <Circle cx={0} cy={0} r={COIN_R * 0.68} style="stroke" strokeWidth={1.3} color="#c48a1d" opacity={0.85} />
        <Circle cx={-COIN_R * 0.3} cy={-COIN_R * 0.35} r={COIN_R * 0.22} color="#fffbe6" opacity={0.8} />
      </Group>
    </Group>
  );
}

type JarProps = {
  index: number;
  spec: JarSpec;
  geometry: JarGeometry;
  /** Centre of the mouth on screen, at rest. */
  x: number;
  y: number;
  drop: number;
  image: SkImage | null;
  /** Board entrance 0…1, block time (s), drag offsets and speeds, and each jar's last landing. */
  board: SharedValue<number>;
  time: SharedValue<number>;
  offsetX: SharedValue<number[]>;
  offsetY: SharedValue<number[]>;
  speedX: SharedValue<number[]>;
  landed: SharedValue<number[]>;
  coins: SharedValue<Coin[]>;
  reducedMotion: boolean;
};

/**
 * A glazed jar floating in the dark: the photo is its glaze, shaded into a body by a dark edge on
 * both sides, a soft streak of light down its left and a lit rim. Coins fall into its mouth.
 */
export function PillRiseJar({
  index,
  spec,
  geometry,
  x,
  y,
  drop,
  image,
  board,
  time,
  offsetX,
  offsetY,
  speedX,
  landed,
  coins,
  reducedMotion,
}: JarProps) {
  const { body, rimFront, lipFront, back, opening, bounds, half, height } = geometry;

  const transform = useDerivedValue(() => {
    const t = time.get();
    const from = APPEAR_FROM + index * APPEAR_LAG;
    const appear = EASE_OUT(seg(board.get(), from, from + APPEAR_SPAN));
    const bob = reducedMotion ? 0 : BOB * Math.sin((Math.PI * 2 * t) / spec.period + spec.phase);
    const sway = reducedMotion ? 0 : WOBBLE * Math.sin((Math.PI * 2 * t) / (spec.period * 1.3) + spec.phase * 2);
    const since = t - landed.get()[index];
    const dip =
      reducedMotion || since < 0 || since > 1.4 ? 0 : DIP * Math.exp(-since * DIP_DECAY) * Math.sin(since * DIP_FREQ);
    const tilt = Math.max(-TILT_MAX, Math.min(TILT_MAX, speedX.get()[index] * TILT_PER_SPEED));
    const rise = reducedMotion ? 0 : (1 - appear) * 46;
    const grow = reducedMotion ? 1 : 0.84 + 0.16 * appear;
    // Turned and scaled about the middle of the jar, not its mouth.
    return [
      { translateX: x + offsetX.get()[index] },
      { translateY: y + offsetY.get()[index] + bob + dip + rise + height / 2 },
      { rotate: spec.tilt + sway + tilt },
      { scaleX: grow * (1 + dip * 0.005) },
      { scaleY: grow * (1 - dip * 0.008) },
      { translateY: -height / 2 },
    ];
  });
  const opacity = useDerivedValue(() => {
    const from = APPEAR_FROM + index * APPEAR_LAG;
    return seg(board.get(), from, from + APPEAR_SPAN * 0.6);
  });

  // The photo is spread over GLAZE_ZOOM times the jar, so its strokes read as runs of glaze.
  const glazeRect = Skia.XYWHRect(
    bounds.x - (bounds.width * (GLAZE_ZOOM - 1)) / 2,
    bounds.y - (bounds.height * (GLAZE_ZOOM - 1)) / 2,
    bounds.width * GLAZE_ZOOM,
    bounds.height * GLAZE_ZOOM,
  );
  const glaze = image ? (
    <ImageShader image={image} fit="cover" rect={glazeRect} />
  ) : (
    <LinearGradient start={vec(bounds.x, 0)} end={vec(bounds.x + bounds.width, 0)} colors={['#c2352b', '#f0a63a', '#2f8fd6']} />
  );
  const left = bounds.x;
  const right = bounds.x + bounds.width;

  return (
    <Group transform={transform} opacity={opacity}>
      {/* A bloom of the jar's own colours behind it. */}
      <Path path={body} opacity={0.22}>
        {glaze}
        <BlurMask blur={30} style="normal" />
      </Path>
      {/* The far side of the lip, then the dark inside of the jar. */}
      <Oval rect={back}>{glaze}</Oval>
      <Oval rect={back} color="rgba(255,255,255,0.16)" />
      <Oval rect={opening}>
        <LinearGradient start={vec(0, opening.y)} end={vec(0, opening.y + opening.height)} colors={['#040405', '#2b2420']} />
      </Oval>
      {Array.from({ length: COIN_SLOTS }, (_, slot) => (
        <JarCoin key={slot} slot={slot} jar={index} drop={drop} coins={coins} time={time} reducedMotion={reducedMotion} />
      ))}
      <Path path={body}>{glaze}</Path>
      <Group clip={body}>
        {/* Round the body: dark at both edges, a little light left of centre. */}
        <Path path={body}>
          <LinearGradient
            start={vec(left, 0)}
            end={vec(right, 0)}
            positions={[0, 0.1, 0.32, 0.6, 0.86, 1]}
            colors={[
              'rgba(6,4,12,0.85)',
              'rgba(6,4,12,0.35)',
              'rgba(255,255,255,0.14)',
              'rgba(255,255,255,0)',
              'rgba(6,4,12,0.45)',
              'rgba(6,4,12,0.9)',
            ]}
          />
        </Path>
        {/* Shadow under the lip and towards the foot. */}
        <Path path={body}>
          <LinearGradient
            start={vec(0, 0)}
            end={vec(0, height)}
            positions={[0, 0.16, 0.7, 1]}
            colors={['rgba(0,0,0,0.42)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0.38)']}
          />
        </Path>
        {/* The glossy streak and the hot spot on the shoulder. */}
        <RoundedRect x={-half * 0.6} y={height * 0.27} width={half * 0.17} height={height * 0.5} r={half * 0.085} color="rgba(255,255,255,0.5)">
          <BlurMask blur={4} style="normal" />
        </RoundedRect>
        <Circle cx={-half * 0.42} cy={height * 0.25} r={4.5} color="rgba(255,255,255,0.85)">
          <BlurMask blur={2.5} style="normal" />
        </Circle>
        {/* A cooler rim light down the right edge. */}
        <RoundedRect x={half * 0.74} y={height * 0.3} width={half * 0.1} height={height * 0.46} r={half * 0.05} color="rgba(200,225,255,0.3)">
          <BlurMask blur={3} style="normal" />
        </RoundedRect>
      </Group>
      <Path path={lipFront} style="stroke" strokeWidth={1.2} color="rgba(0,0,0,0.3)" />
      <Path path={rimFront} style="stroke" strokeWidth={1.4} color="rgba(255,255,255,0.55)" />
    </Group>
  );
}

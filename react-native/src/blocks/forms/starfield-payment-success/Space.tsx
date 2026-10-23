import { BlurMask, BlurStyle, Canvas, Circle, Group, LinearGradient, Path, Picture, Rect, Skia, vec } from '@shopify/react-native-skia';
import { StyleSheet } from 'react-native';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The clock of a payment, in seconds since Confirm was released. `duration` is the wait: the time
// from Confirm to the moment the paper plane leaves.

/** autoPlay: one payment every LOOP seconds. */
export const LOOP = 11;
/** autoPlay presses a key every KEY_EVERY from FIRST_KEY_AT, each held for KEY_FOR. */
export const FIRST_KEY_AT = 0.8;
export const KEY_EVERY = 0.35;
export const KEY_FOR = 0.12;
/** autoPlay holds Confirm down at PRESS_AT for PRESS_FOR, and leaves the result LEAVE_AFTER the check. */
export const PRESS_AT = 2.6;
export const PRESS_FOR = 0.15;
export const LEAVE_AFTER = 3.6;
/** After a touch autoPlay waits this long, and for the form to be idle, before it starts over. */
export const USER_HOLD = 3;

// Confirm drops off the bottom edge while the form comes apart.
export const DROP = 0.28;
export const DISSOLVE_AT = 0.06;
export const DISSOLVE_FOR = 0.45;
export const STARS_AT = 0.4;
export const STARS_IN = 0.6;
export const TEXT_AT = 0.5;
export const TEXT_IN = 0.35;
// The light condenses, then the plane shows on it.
const GLOW_AT = 0.75;
const GLOW_IN = 0.4;
const PLANE_AT = 1;
const PLANE_IN = 0.25;
// The plane and the text leave, and the check lands this long after the wait ends.
export const WAIT_OUT = 0.3;
export const CHECK_AFTER = 0.35;
const CHECK_IN = 0.5;
const RING_FOR = 0.8;
// After the check: the bar rises, the check swells and darkens, then turns over into the coin.
export const BAR_AFTER = 0.55;
export const BAR_IN = 0.45;
const SWELL_AFTER = 0.85;
const SWELL_FOR = 0.3;
const TURN_AFTER = SWELL_AFTER + SWELL_FOR;
// The turn eases up to the coin's speed: one turn every SPIN_PERIOD.
const TURN_EASE = 0.3;
const SPIN_PERIOD = 1.3;
export const RESULT_AFTER = TURN_AFTER + 0.3;
export const RESULT_IN = 0.35;
/** Leaving the result: the sky fades over the first part of RETURN while the form comes back. */
export const RETURN = 0.6;

// Where things sit, as fractions of the screen's height.
const PLANE_Y = 0.398;
export const MARK_Y = 0.36;

const STAR_COUNT = 240;
// Depth a second while waiting, and the extra depth covered in the rush when the check lands.
const STAR_SPEED = 0.05;
const STAR_RUSH = 0.3;
// The nearest depth, as a fraction of the farthest: a star grows 1 / NEAR times on its way.
const NEAR = 0.18;
// The blur of a near star's glow.
const STAR_HALO = 2.2;
// The far plane's half-size, as a fraction of the screen's height.
const REACH = 0.5;

const GLOW = 27;
const GLOW_REACH = 11;
const CHECK = 87;
const SWELL = 1.3;
const COIN = 112;
const COIN_EDGE = 14;
// The coin leans a little, in radians.
const COIN_TILT = -0.14;
const R = COIN / 2;

const RING = '#e5432a';
const GREEN = [30, 224, 122];
const GREEN_DARK = [18, 150, 79];
const PLANE_INK = '#1b1206';
const CHECK_INK = '#06140c';

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** Ease out with a 10% overshoot. */
export function backOut(t: number) {
  'worklet';
  const u = clamp01(t) - 1;
  return 1 + 2.70158 * u * u * u + 1.70158 * u * u;
}

/** Radians turned `t` seconds into the turn. */
function turned(t: number) {
  'worklet';
  const speed = (2 * Math.PI) / SPIN_PERIOD;
  if (t <= 0) return 0;
  return t < TURN_EASE ? (speed * t * t) / (2 * TURN_EASE) : speed * (t - TURN_EASE / 2);
}

/** A fixed field: each star's direction from the centre (x, y in -1…1), its depth, its brightness. */
function field(count: number) {
  const stars: number[][] = [];
  let seed = 20261012;
  const next = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };
  while (stars.length < count) {
    const x = next() * 2 - 1;
    const y = next() * 2 - 1;
    const star = [x, y, next(), 0.55 + 0.45 * next()];
    // None dead ahead: it would sit in the middle and swell without moving.
    if (Math.hypot(x, y) > 0.08) stars.push(star);
  }
  return stars;
}

const STARS = field(STAR_COUNT);

// The light behind the plane: four discs that wander round its centre. Each: its angle in
// radians, its speed in radians a second, its phase, its size.
const LOBES = [
  [0.4, 2.3, 0, 1.05],
  [2.0, 1.7, 1.9, 0.86],
  [3.5, 2.9, 3.4, 1],
  [5.2, 2.1, 5.0, 0.8],
];

// A paper plane pointing up and to the right in a 24 × 24 box, and the three streaks behind it.
const PLANE = 'M22 2L14.6 21.6L11.2 12.8L2.4 9.4Z';
const STREAKS = 'M2.2 17.4L6.4 13.2M4.8 21L9.6 16.2M9 22.4L11.8 19.6';
const PLANE_SCALE = 1.45;
// The check, drawn round the disc's centre.
const TICK = 'M-15.2 1L-5.7 10.5L15.2 -10.5';
// A dollar sign in a 24 × 24 box.
const DOLLAR = 'M12 1v22M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6';
const DOLLAR_SCALE = 2.5;
const FACE_CLIP = Skia.Path.Circle(0, 0, R);

// The coin's edge from top to bottom: dark brass with two glints.
const EDGE = ['#6b4a12', '#f6dc92', '#8a6119', '#2e1f06', '#4a3209', '#c9a046', '#3a2707'];
const EDGE_AT = [0, 0.12, 0.26, 0.5, 0.7, 0.86, 1];

type LobeProps = {
  index: number;
  elapsed: SharedValue<number>;
  still: boolean;
  radius: number;
  color: string;
  blur: number;
};

function Lobe({ index, elapsed, still, radius, color, blur }: LobeProps) {
  const transform = useDerivedValue(() => {
    const t = still ? 0 : elapsed.get();
    const [angle, speed, phase] = LOBES[index];
    const a = angle + 0.4 * Math.sin(t * speed + phase);
    const d = GLOW_REACH * (1 + 0.35 * Math.sin(t * speed * 1.4 + phase * 2));
    // Wider than tall, like the light in the design.
    return [{ translateX: Math.cos(a) * d * 1.25 }, { translateY: Math.sin(a) * d }];
  });
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={radius * LOBES[index][3]} color={color}>
        <BlurMask blur={blur} style="normal" />
      </Circle>
    </Group>
  );
}

/** One face of the coin, drawn flat round its centre: a raised rim, a groove, and the sign on the front. */
function Face({ front }: { front: boolean }) {
  return (
    <>
      <Circle cx={0} cy={0} r={R}>
        <LinearGradient start={vec(-R, -R)} end={vec(R, R)} colors={['#fcefc9', '#e8c57f', '#b98a35']} />
      </Circle>
      <Circle cx={0} cy={0} r={R - 2.5} style="stroke" strokeWidth={5}>
        <LinearGradient start={vec(-R, -R)} end={vec(R, R)} colors={['#c7963c', '#f3d894', '#fbeaba']} />
      </Circle>
      <Circle cx={0} cy={0} r={R - 9} style="stroke" strokeWidth={1.4} color="rgba(150,104,30,0.75)" />
      <Circle cx={0} cy={0} r={R - 10.3} style="stroke" strokeWidth={1} color="rgba(255,246,216,0.7)" />
      {front ? (
        <Group transform={[{ scale: DOLLAR_SCALE }, { translateX: -12 }, { translateY: -12 }]}>
          {/* Struck into the metal: a shadow below and a light edge above the sign. */}
          <Group transform={[{ translateX: 0.45 }, { translateY: 0.5 }]}>
            <Path path={DOLLAR} style="stroke" strokeWidth={2.5} strokeCap="round" strokeJoin="round" color="#8f6420" />
          </Group>
          <Group transform={[{ translateX: -0.35 }, { translateY: -0.35 }]}>
            <Path path={DOLLAR} style="stroke" strokeWidth={2.5} strokeCap="round" strokeJoin="round" color="#fff7dc" />
          </Group>
          <Path path={DOLLAR} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#d9b266" />
        </Group>
      ) : (
        <Circle cx={0} cy={0} r={R - 19}>
          <LinearGradient start={vec(-R, -R)} end={vec(R, R)} colors={['#f1d596', '#d2a551']} />
        </Circle>
      )}
    </>
  );
}

type Props = {
  /** Seconds since Confirm was released. */
  elapsed: SharedValue<number>;
  /** Seconds from Confirm to the moment the plane leaves. */
  duration: number;
  /** Reduced motion: the stars stand still, the light does not wobble and the coin does not spin. */
  still: boolean;
  width: number;
  height: number;
};

/**
 * What a payment travels through: stars flying past, a paper plane on a wobbling light while it
 * waits, then a shock ring and a green check that swells, turns over and comes round as a
 * spinning gold coin.
 */
export function Space({ elapsed, duration, still, width, height }: Props) {
  const cx = width / 2;
  const planeY = height * PLANE_Y;
  const markY = height * MARK_Y;
  const check = duration + CHECK_AFTER;
  const turn = check + TURN_AFTER;

  const stars = useDerivedValue(() => {
    const e = elapsed.get();
    const shown = smooth(e, STARS_AT, STARS_AT + STARS_IN);
    const travel = still ? 0 : STAR_SPEED * e + STAR_RUSH * smooth(e, check - 0.1, check + 0.9);
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const paint = Skia.Paint();
    paint.setColor(Skia.Color('#ffffff'));
    // The near stars glow.
    const halo = Skia.Paint();
    halo.setColor(Skia.Color('#ffffff'));
    halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, STAR_HALO, true));
    const reach = REACH * height;
    for (let i = 0; i < STARS.length; i++) {
      const star = STARS[i];
      let z = (star[2] - travel) % 1;
      if (z <= 0) z += 1;
      const scale = 1 / (NEAR + (1 - NEAR) * z);
      const x = cx + star[0] * reach * scale;
      const y = markY + star[1] * reach * scale;
      if (x < -6 || x > width + 6 || y < -6 || y > height + 6) continue;
      const near = 1 - z;
      // A star comes out of the dark far away, and is gone before its depth wraps round.
      const alpha = shown * star[3] * (0.5 + 0.5 * near) * smooth(z, 1, 0.9) * smooth(z, 0, 0.06);
      const r = 0.65 + 1.7 * near * near * near;
      if (r > 1.2) {
        halo.setAlphaf(alpha * 0.5);
        canvas.drawCircle(x, y, r * 1.8, halo);
      }
      paint.setAlphaf(alpha);
      canvas.drawCircle(x, y, r, paint);
    }
    return recorder.finishRecordingAsPicture();
  });

  const glowOpacity = useDerivedValue(() => {
    const e = elapsed.get();
    return smooth(e, GLOW_AT, GLOW_AT + GLOW_IN) * (1 - smooth(e, duration, duration + WAIT_OUT));
  });
  const glowTransform = useDerivedValue(() => {
    const e = elapsed.get();
    const grown = 0.5 + 0.5 * smooth(e, GLOW_AT, GLOW_AT + GLOW_IN);
    const breath = still ? 1 : 1 + 0.05 * Math.sin(e * 3.3);
    const bob = still ? 0 : 2.5 * Math.sin(e * 2.1);
    return [{ translateX: cx }, { translateY: planeY + bob }, { scale: grown * breath }];
  });
  const planeOpacity = useDerivedValue(() => smooth(elapsed.get(), PLANE_AT, PLANE_AT + PLANE_IN));
  const planeTransform = useDerivedValue(() => {
    const e = elapsed.get();
    // It trembles on the light, as if under way.
    const lean = still ? 0 : 0.05 * Math.sin(e * 4.6);
    return [{ rotate: lean }, { scale: PLANE_SCALE }, { translateX: -12 }, { translateY: -12 }];
  });

  const ringRadius = useDerivedValue(() => {
    const u = 1 - clamp01((elapsed.get() - check) / RING_FOR);
    return 16 + 0.42 * height * (1 - u * u * u);
  });
  const ringOpacity = useDerivedValue(() => {
    const p = clamp01((elapsed.get() - check) / RING_FOR);
    return still || p <= 0 ? 0 : 0.6 * (1 - p);
  });
  const farRingRadius = useDerivedValue(() => ringRadius.get() * 2.2);
  const farRingOpacity = useDerivedValue(() => ringOpacity.get() * 0.45);

  // The check turns about its vertical axis; past edge-on, the coin is what comes round.
  const angle = useDerivedValue(() => (still ? 0 : turned(elapsed.get() - turn)));
  const swap = useDerivedValue(() => (still ? smooth(elapsed.get(), turn, turn + 0.3) : angle.get() >= Math.PI / 2 ? 1 : 0));
  const discTransform = useDerivedValue(() => {
    const e = elapsed.get();
    const size = (still ? 1 : backOut((e - check) / CHECK_IN)) * (1 + (SWELL - 1) * smooth(e, check + SWELL_AFTER, turn));
    return [{ translateX: cx }, { translateY: markY }, { scaleX: Math.max(0.001, Math.cos(Math.min(angle.get(), Math.PI / 2))) }, { scale: size }];
  });
  const discOpacity = useDerivedValue(() => smooth(elapsed.get(), check, check + 0.2) * (1 - swap.get()));
  const discColor = useDerivedValue(() => {
    const dark = smooth(elapsed.get(), check + SWELL_AFTER, turn);
    const mixed = GREEN.map((from, i) => Math.round(from + (GREEN_DARK[i] - from) * dark));
    return `rgb(${mixed[0]},${mixed[1]},${mixed[2]})`;
  });

  // The coin's own angle: 0 with its front to the viewer, which is where the turn brings it first.
  const coin = useDerivedValue(() => (still ? 0 : angle.get() - Math.PI));
  const coinTransform = useDerivedValue(() => [{ translateX: cx }, { translateY: markY }, { rotate: COIN_TILT }]);
  // The face towards the viewer, and the one behind it, each half the edge's width off the middle.
  const faceTransform = useDerivedValue(() => {
    const c = Math.cos(coin.get());
    const offset = (c >= 0 ? 1 : -1) * (COIN_EDGE / 2) * Math.sin(coin.get());
    return [{ translateX: offset }, { scaleX: Math.max(0.001, Math.abs(c)) }];
  });
  const behindTransform = useDerivedValue(() => {
    const c = Math.cos(coin.get());
    const offset = (c >= 0 ? -1 : 1) * (COIN_EDGE / 2) * Math.sin(coin.get());
    return [{ translateX: offset }, { scaleX: Math.max(0.001, Math.abs(c)) }];
  });
  const edgeX = useDerivedValue(() => -(COIN_EDGE / 2) * Math.abs(Math.sin(coin.get())));
  const edgeWidth = useDerivedValue(() => COIN_EDGE * Math.abs(Math.sin(coin.get())));
  const frontOpacity = useDerivedValue(() => (Math.cos(coin.get()) >= 0 ? 1 : 0));
  const backOpacity = useDerivedValue(() => (Math.cos(coin.get()) >= 0 ? 0 : 1));
  // A face turned away from the light goes dark, and a band of light crosses it as it turns.
  const shade = useDerivedValue(() => 0.66 * Math.pow(1 - Math.abs(Math.cos(coin.get())), 0.8));
  const sheenTransform = useDerivedValue(() => [{ translateX: 1.5 * R * Math.sin(coin.get()) }, { rotate: 0.35 }]);

  return (
    <Canvas style={StyleSheet.absoluteFill}>
      <Picture picture={stars} />

      <Group transform={glowTransform} opacity={glowOpacity}>
        {LOBES.map((_, index) => (
          <Lobe key={index} index={index} elapsed={elapsed} still={still} radius={GLOW + 4} color="rgba(255,78,10,0.9)" blur={7} />
        ))}
        {LOBES.map((_, index) => (
          <Lobe key={index} index={index} elapsed={elapsed} still={still} radius={GLOW} color="#ffc61c" blur={2.5} />
        ))}
        <Circle cx={0} cy={0} r={20} color="#fff4ae">
          <BlurMask blur={9} style="normal" />
        </Circle>
        <Group transform={planeTransform} opacity={planeOpacity}>
          <Path path={PLANE} color={PLANE_INK} />
          <Path path={STREAKS} style="stroke" strokeWidth={1.3} strokeCap="round" color={PLANE_INK} />
        </Group>
      </Group>

      <Circle cx={cx} cy={markY} r={farRingRadius} style="stroke" strokeWidth={1.5} color={RING} opacity={farRingOpacity}>
        <BlurMask blur={1.5} style="normal" />
      </Circle>
      <Circle cx={cx} cy={markY} r={ringRadius} style="stroke" strokeWidth={1.5} color={RING} opacity={ringOpacity}>
        <BlurMask blur={1.5} style="normal" />
      </Circle>

      <Group transform={discTransform} opacity={discOpacity}>
        <Circle cx={0} cy={0} r={CHECK / 2} color={discColor} />
        <Path path={TICK} style="stroke" strokeWidth={5} strokeCap="round" strokeJoin="round" color={CHECK_INK} />
      </Group>

      <Group transform={coinTransform} opacity={swap}>
        <Circle cx={0} cy={0} r={R * 1.5} color="rgba(255,178,58,0.14)">
          <BlurMask blur={30} style="normal" />
        </Circle>
        <Group transform={behindTransform}>
          <Circle cx={0} cy={0} r={R}>
            <LinearGradient start={vec(0, -R)} end={vec(0, R)} colors={EDGE} positions={EDGE_AT} />
          </Circle>
        </Group>
        <Rect x={edgeX} y={-R} width={edgeWidth} height={COIN}>
          <LinearGradient start={vec(0, -R)} end={vec(0, R)} colors={EDGE} positions={EDGE_AT} />
        </Rect>
        <Group transform={faceTransform} clip={FACE_CLIP}>
          <Group opacity={frontOpacity}>
            <Face front />
          </Group>
          <Group opacity={backOpacity}>
            <Face front={false} />
          </Group>
          <Circle cx={0} cy={0} r={R} color="#1a0e00" opacity={shade} />
          <Group transform={sheenTransform}>
            <Rect x={-0.5 * R} y={-1.5 * R} width={R} height={3 * R}>
              <LinearGradient
                start={vec(-0.5 * R, 0)}
                end={vec(0.5 * R, 0)}
                colors={['rgba(255,255,255,0)', 'rgba(255,252,235,0.6)', 'rgba(255,255,255,0)']}
              />
            </Rect>
          </Group>
        </Group>
      </Group>
    </Canvas>
  );
}

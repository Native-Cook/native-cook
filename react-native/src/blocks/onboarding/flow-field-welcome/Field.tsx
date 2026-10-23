import { Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

// The field is a square lattice of short strokes. Each one's place and angle are computed every
// frame from how far the field has opened (0 packed, 1 the open vortex), so nothing is tweened
// between two stored pictures.

/** autoPlay: the field opens and closes twice a LOOP. */
export const LOOP = 8.6;
export const CYCLE = 4.3;
export const OPEN_AT = 0.3;
export const CLOSE_AT = 2.7;
/** Seconds from packed to the open vortex, and back. */
export const MORPH = 1.4;
/** Reduced motion: the two figures cross-fade in this long. */
export const FADE = 0.3;
/** After a touch autoPlay waits this long, then closes the field and starts over. */
export const USER_HOLD = 3;

/** The canvas: the open lattice plus room for the strokes that are flung out. */
export const FIELD = 360;
const GRID = 18;
const COUNT = GRID * GRID;
const OPEN_SIZE = 260;
const PITCH = OPEN_SIZE / (GRID - 1);
// The packed lattice is this much of the open one: 150 points.
const PACKED = 0.58;
// Before it opens, and as the last thing when it closes, the block squeezes to 140 points.
const SQUEEZE = 0.042;
const SQUEEZE_FOR = 0.3;
// Half-way the lattice is twisted, the outside more than the inside, which tears its corners out.
const TWIST = (50 * Math.PI) / 180;
const TWIST_INSIDE = 0.25;
// The outside starts this much of the way later than the inside: the motion ripples outwards.
const RIPPLE = 0.06;
// Half-way a few strokes are flung further out, and all of them lean off their line.
const FLING = 26;
const STRAY = (40 * Math.PI) / 180;
// Packed, a stroke points along the swirl give or take SPREAD, and twitches by JITTER a few times a second.
const SPREAD = (70 * Math.PI) / 180;
const JITTER = (10 * Math.PI) / 180;
const JITTER_HZ = 5;
const JITTER_STEPS = Math.round(LOOP * JITTER_HZ);
const JITTER_BLEND = 0.5;
// Open, the strokes circle an eye that itself circles the centre.
const EYE = 24;
export const EYE_FROM = -Math.PI / 2;
/** Radians a second. */
export const EYE_SPEED = 1.2;
const DASH = 5;
const THICK = 1.2;
// A finger pushes the strokes within REPEL_REACH away by up to REPEL, and turns those within TURN_REACH towards itself.
const REPEL = 14;
const REPEL_REACH = 60;
const TURN_REACH = 120;

/** A number from 0 to 1 that depends on `n` alone, the same on every platform. */
function hash(n: number) {
  'worklet';
  let h = Math.imul(n ^ 0x9e3779b9, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

// Per stroke: its home on the open lattice, how far that is from the centre (0 to 1), and its own
// share of the spread, the fling and the stray.
const HOME_X: number[] = [];
const HOME_Y: number[] = [];
const FAR: number[] = [];
const SPREAD_OF: number[] = [];
const FLING_OF: number[] = [];
const STRAY_OF: number[] = [];
for (let i = 0; i < COUNT; i++) {
  const x = ((i % GRID) - (GRID - 1) / 2) * PITCH;
  const y = (Math.floor(i / GRID) - (GRID - 1) / 2) * PITCH;
  HOME_X.push(x);
  HOME_Y.push(y);
  FAR.push(Math.hypot(x, y) / ((OPEN_SIZE / 2) * Math.SQRT2));
  SPREAD_OF.push((hash(i * 4 + 1) * 2 - 1) * SPREAD);
  FLING_OF.push(Math.pow(hash(i * 4 + 2), 3) * FLING);
  STRAY_OF.push((hash(i * 4 + 3) * 2 - 1) * STRAY);
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function ease(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/**
 * Every stroke of the field as one path, in a FIELD × FIELD box. `progress` is how far the field
 * has opened and `opening` which way it is going; `clock` is seconds within a LOOP, for the
 * twitch; `eye` is the angle of the vortex's eye round the centre; `fingerX`, `fingerY` (from the
 * centre) and `pull` (0 to 1) are the touch.
 */
export function fieldPath(progress: number, opening: boolean, clock: number, eye: number, fingerX: number, fingerY: number, pull: number) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  const ticks = clock * JITTER_HZ;
  const step = Math.floor(ticks) % JITTER_STEPS;
  const before = (step + JITTER_STEPS - 1) % JITTER_STEPS;
  const u = clamp01((ticks - Math.floor(ticks)) / JITTER_BLEND);
  const blend = u * u * (3 - 2 * u);
  const eyeOut = EYE * ease(progress);
  const eyeX = eyeOut * Math.cos(eye);
  const eyeY = eyeOut * Math.sin(eye);
  for (let i = 0; i < COUNT; i++) {
    const far = FAR[i];
    // The inside leads both ways.
    const own = clamp01(progress * (1 + RIPPLE) - RIPPLE * (opening ? far : 1 - far));
    const q = ease(own);
    const tear = Math.sin(Math.PI * q);
    const squeeze = Math.sin(Math.PI * Math.min(1, own / SQUEEZE_FOR));
    const scale = PACKED + (1 - PACKED) * q - SQUEEZE * squeeze * squeeze;
    const twist = TWIST * tear * (TWIST_INSIDE + (1 - TWIST_INSIDE) * far * far);
    const cos = Math.cos(twist);
    const sin = Math.sin(twist);
    let x = (HOME_X[i] * cos - HOME_Y[i] * sin) * scale;
    let y = (HOME_X[i] * sin + HOME_Y[i] * cos) * scale;
    const reach = Math.hypot(x, y) || 1;
    const fling = FLING_OF[i] * tear;
    x += (x / reach) * fling;
    y += (y / reach) * fling;
    const twitch = hash(i * 64 + 1000 + before) * (1 - blend) + hash(i * 64 + 1000 + step) * blend;
    let angle =
      Math.atan2(y - eyeY, x - eyeX) + Math.PI / 2 + twist + (1 - q) * (SPREAD_OF[i] + JITTER * (twitch * 2 - 1)) + tear * STRAY_OF[i];
    if (pull > 0) {
      const dx = x - fingerX;
      const dy = y - fingerY;
      const gap = Math.hypot(dx, dy) || 1;
      if (gap < REPEL_REACH) {
        const k = 1 - gap / REPEL_REACH;
        x += (dx / gap) * REPEL * pull * k * k;
        y += (dy / gap) * REPEL * pull * k * k;
      }
      if (gap < TURN_REACH) {
        const k = 1 - gap / TURN_REACH;
        // A stroke has no head: it turns the short way to the line through the finger.
        let turn = Math.atan2(dy, dx) - angle;
        turn -= Math.PI * Math.round(turn / Math.PI);
        angle += turn * pull * k * k;
      }
    }
    const hx = (Math.cos(angle) * DASH) / 2;
    const hy = (Math.sin(angle) * DASH) / 2;
    builder.moveTo(FIELD / 2 + x - hx, FIELD / 2 + y - hy).lineTo(FIELD / 2 + x + hx, FIELD / 2 + y + hy);
  }
  return builder.build();
}

const NOTHING = Skia.PathBuilder.Make().build();

type Props = {
  /** How far the field has opened, 0 to 1, moving at a steady pace. */
  progress: SharedValue<number>;
  opening: SharedValue<boolean>;
  /** Seconds within a LOOP. */
  clock: SharedValue<number>;
  /** The angle of the vortex's eye round the centre. */
  eye: SharedValue<number>;
  /** The touch, from the field's centre, and how strongly it pulls (0 to 1). */
  finger: SharedValue<{ x: number; y: number }>;
  pull: SharedValue<number>;
  /** Reduced motion: the packed and the open figure cross-fade, and nothing twitches. */
  still: boolean;
  /** The field's size on screen, as a share of FIELD. */
  scale: number;
  color: string;
};

export function Field({ progress, opening, clock, eye, finger, pull, still, scale, color }: Props) {
  const moving = useDerivedValue(() =>
    still ? NOTHING : fieldPath(progress.get(), opening.get(), clock.get(), eye.get(), finger.get().x, finger.get().y, pull.get()),
  );
  const figures = useMemo(() => (still ? [fieldPath(0, true, 0, EYE_FROM, 0, 0, 0), fieldPath(1, true, 0, EYE_FROM, 0, 0, 0)] : null), [still]);
  const packed = useDerivedValue(() => 1 - progress.get());
  const size = FIELD * scale;
  const stroke = { style: 'stroke', strokeWidth: THICK, strokeCap: 'round', color } as const;
  return (
    <Canvas style={{ width: size, height: size }}>
      <Group transform={[{ scale }]}>
        {figures ? (
          <>
            <Path path={figures[0]} opacity={packed} {...stroke} />
            <Path path={figures[1]} opacity={progress} {...stroke} />
          </>
        ) : (
          <Path path={moving} {...stroke} />
        )}
      </Group>
    </Canvas>
  );
}

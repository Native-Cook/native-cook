import { BlurMask, Canvas, Circle, Group, Line, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export const BAND = '#070909';
// The band above the bottom inset, and the least the inset counts for.
export const BAND_HEIGHT = 62;
export const BAND_INSET = 34;
// The icons' centres: under the band's top edge, and the first and last from the screen's sides.
export const ICON_Y = 40;
export const ICON_INSET = 52;
const TOP_RADIUS = 40;
// The valley starts this far inside the end icons, takes SLOPE to reach its floor and is DEPTH deep.
const SHOULDER = 40;
const SLOPE = 52;
const DEPTH = 74;
const RING = 58;
// A ring in flight is this much wider, and its outline this much thinner.
const RING_STRETCH = 0.5;
const RING_THIN = 0.4;
export const CAPSULE_WIDTH = 136;
export const CAPSULE_HEIGHT = 42;
// The cross and the heart from the capsule's ends.
export const MARK_INSET = 29;
// The side of the capsule a verdict lights.
const PILL_WIDTH = 54;
const PILL_HEIGHT = 38;
const MARKER_WIDTH = 30;
const MARKER_HEIGHT = 20;
const MARKER_SLIDE = 6;
const GLOW = 16;
const STROKE = 1.6;
const CROSS_STROKE = 2;
const CLOCK = 8.5;
const CROSS = 5;
// The heart of the capsule next to the heart of the tab, and how much a verdict's mark grows.
const HEART_SMALL = 0.83;
const GROW = 0.15;
const FLASH = 0.06;

// A heart 16.8 points wide around its own centre.
const HEART = Skia.Path.MakeFromSVGString(
  'M0 8.2 C0 8.2 -8.4 3.4 -8.4 -2.6 C-8.4 -5.4 -6.2 -7.4 -3.7 -7.4 C-2.1 -7.4 -0.7 -6.5 0 -5.2 C0.7 -6.5 2.1 -7.4 3.7 -7.4 C6.2 -7.4 8.4 -5.4 8.4 -2.6 C8.4 3.4 0 8.2 0 8.2 Z',
)!;

export function iconX(index: number, width: number) {
  'worklet';
  return ICON_INSET + (index * (width - ICON_INSET * 2)) / 3;
}

export function smooth(x: number, from: number, to: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (x - from) / (to - from)));
  return t * t * (3 - 2 * t);
}

/** The band: a sheet with round top corners whose top edge sinks `depth` between its shoulders. */
function bandPath(width: number, height: number, depth: number) {
  'worklet';
  const from = ICON_INSET + SHOULDER;
  const to = width - from;
  const half = SLOPE / 2;
  return (
    `M0 ${height} L0 ${TOP_RADIUS} Q0 0 ${TOP_RADIUS} 0 L${from} 0 ` +
    `C${from + half} 0 ${from + half} ${depth} ${from + SLOPE} ${depth} L${to - SLOPE} ${depth} ` +
    `C${to - half} ${depth} ${to - half} 0 ${to} 0 L${width - TOP_RADIUS} 0 Q${width} 0 ${width} ${TOP_RADIUS} L${width} ${height} Z`
  );
}

type Verdicts = { reject: SharedValue<number>; like: SharedValue<number> };

/**
 * The two middle tabs on their way to the capsule: at 0 a clock and an outlined heart, at 1 the
 * cross its hands have opened into and the same heart, smaller and solid.
 */
function Middle({ morph, opacity, width, reject, like, still }: Verdicts & { morph: SharedValue<number>; opacity: SharedValue<number>; width: number; still: boolean }) {
  const crossX = width / 2 - CAPSULE_WIDTH / 2 + MARK_INSET;
  const heartX = width / 2 + CAPSULE_WIDTH / 2 - MARK_INSET;
  const clockAt = useDerivedValue(() => {
    const m = morph.get();
    return [{ translateX: iconX(1, width) + (crossX - iconX(1, width)) * m }, { translateY: ICON_Y }, { scale: still ? 1 : 1 + GROW * reject.get() }];
  });
  const face = useDerivedValue(() => CLOCK * (1 - 0.6 * morph.get()));
  const faceOpacity = useDerivedValue(() => 1 - smooth(morph.get(), 0, 0.6));
  // The hands leave the centre and become the two strokes of the cross.
  const hourFrom = useDerivedValue(() => ({ x: -CROSS * morph.get(), y: CROSS * morph.get() }));
  const hourTo = useDerivedValue(() => ({ x: CROSS * morph.get(), y: -4.5 - (CROSS - 4.5) * morph.get() }));
  const minuteFrom = useDerivedValue(() => ({ x: -CROSS * morph.get(), y: -CROSS * morph.get() }));
  const minuteTo = useDerivedValue(() => ({ x: 3 + (CROSS - 3) * morph.get(), y: 1.8 + (CROSS - 1.8) * morph.get() }));
  const stroke = useDerivedValue(() => STROKE + (CROSS_STROKE - STROKE) * morph.get());
  const heartAt = useDerivedValue(() => {
    const m = morph.get();
    const scale = (1 - (1 - HEART_SMALL) * m) * (still ? 1 : 1 + GROW * like.get());
    return [{ translateX: iconX(2, width) + (heartX - iconX(2, width)) * m }, { translateY: ICON_Y }, { scale }];
  });
  const halo = useDerivedValue(() => (still ? 0 : 0.4 * like.get()));
  return (
    <Group opacity={opacity}>
      <Group transform={clockAt} style="stroke" strokeCap="round" color="#FFFFFF">
        <Circle cx={0} cy={0} r={face} strokeWidth={STROKE} opacity={faceOpacity} />
        <Line p1={hourFrom} p2={hourTo} strokeWidth={stroke} />
        <Line p1={minuteFrom} p2={minuteTo} strokeWidth={stroke} />
      </Group>
      <Group transform={heartAt}>
        <Circle cx={0} cy={0} r={13} color="#FFFFFF" opacity={halo}>
          <BlurMask blur={8} style="normal" />
        </Circle>
        <Path path={HEART} color="#FFFFFF" opacity={morph} />
        <Path path={HEART} style="stroke" strokeWidth={STROKE} strokeJoin="round" color="#FFFFFF" />
      </Group>
    </Group>
  );
}

/** One side of the capsule lit by a verdict: its colour from the end toward the middle, and a glow. */
function Pill({ x, color, strength, flash, inward, still }: { x: number; color: string; strength: SharedValue<number>; flash: SharedValue<number>; inward: number; still: boolean }) {
  const y = ICON_Y - PILL_HEIGHT / 2;
  const transform = useDerivedValue(() => [{ scale: still ? 1 : 1 + FLASH * flash.get() }]);
  const box = { x, y, width: PILL_WIDTH, height: PILL_HEIGHT, r: PILL_HEIGHT / 2 };
  const outer = inward > 0 ? x : x + PILL_WIDTH;
  return (
    <Group opacity={strength} transform={transform} origin={vec(x + PILL_WIDTH / 2, ICON_Y)}>
      {!still && (
        <RoundedRect {...box} color={`${color}99`}>
          <BlurMask blur={GLOW} style="normal" />
        </RoundedRect>
      )}
      <RoundedRect {...box}>
        <LinearGradient start={vec(outer, 0)} end={vec(outer + inward * PILL_WIDTH, 0)} colors={[color, `${color}73`]} />
      </RoundedRect>
      <RoundedRect {...box} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.2)" />
    </Group>
  );
}

type Props = {
  width: number;
  height: number;
  /** 0 flat, 1 with the valley open; a spring may carry it a little past either. */
  valley: SharedValue<number>;
  /** Where the ring is, in tabs, and the tabs it left and is going to. */
  ring: SharedValue<number>;
  ringFrom: SharedValue<number>;
  ringTo: SharedValue<number>;
  /** How far the top card is from its place, and whether it is flying off: 0 to 1, toward `side`. */
  cardX: SharedValue<number>;
  fly: SharedValue<number>;
  side: SharedValue<number>;
  /** The pull that lights a side fully. */
  full: number;
  accent: string;
  likeColor: string;
  rejectColor: string;
  /** Reduced motion: the two states of the band cross-fade, and verdicts only change colour. */
  still: boolean;
};

/**
 * The bottom band of the swipe deck: four line icons and a ring on the current one. On the deck's
 * tab its top edge sinks into a valley, and the two middle icons meet in a glass capsule as the
 * cross and the heart that judge a card.
 */
export function Toolbar({ width, height, valley, ring, ringFrom, ringTo, cardX, fly, side, full, accent, likeColor, rejectColor, still }: Props) {
  const capsuleX = width / 2 - CAPSULE_WIDTH / 2;
  const capsuleY = ICON_Y - CAPSULE_HEIGHT / 2;

  const band = useDerivedValue(() => Skia.Path.MakeFromSVGString(bandPath(width, height, still ? DEPTH : DEPTH * valley.get()))!);
  const flat = Skia.Path.MakeFromSVGString(bandPath(width, height, 0))!;
  const open = useDerivedValue(() => Math.min(1, Math.max(0, valley.get())));
  const cover = useDerivedValue(() => 1 - open.get());

  // In flight the ring is longer along its way and its outline thinner.
  const flight = useDerivedValue(() => {
    const span = ringTo.get() - ringFrom.get();
    if (still || span === 0) return 0;
    return Math.sin(Math.PI * Math.min(1, Math.max(0, (ring.get() - ringFrom.get()) / span)));
  });
  const ringAt = useDerivedValue(() => [{ translateX: iconX(ring.get(), width) }, { translateY: ICON_Y }]);
  const ringX = useDerivedValue(() => (-RING * (1 + RING_STRETCH * flight.get())) / 2);
  const ringWidth = useDerivedValue(() => RING * (1 + RING_STRETCH * flight.get()));
  const ringStroke = useDerivedValue(() => 1 - RING_THIN * flight.get());

  // A verdict grows with the card's pull; a card that flies off lights its side fully, then fades.
  // The shared values are read in the derived values themselves: one that reads them only inside
  // a function it calls is not told when they change, and stays at what it was first.
  const strength = (to: number, x: number, q: number, toward: number, opened: number) => {
    'worklet';
    const pulled = Math.min(1, Math.max(0, (to * x) / full));
    const flown = q > 0 && toward === to ? smooth(q, 0, 0.25) : 0;
    return Math.max(pulled, flown) * (1 - smooth(q, 0.5, 1)) * opened;
  };
  const reject = useDerivedValue(() => strength(-1, cardX.get(), fly.get(), side.get(), open.get()));
  const like = useDerivedValue(() => strength(1, cardX.get(), fly.get(), side.get(), open.get()));
  const flash = useDerivedValue(() => Math.sin(Math.PI * fly.get()));

  const glass = useDerivedValue(() => (still ? open.get() : smooth(open.get(), 0.3, 1)));
  const markerX = useDerivedValue(() => width / 2 - MARKER_WIDTH / 2 + (still ? 0 : MARKER_SLIDE * (like.get() - reject.get())));
  const markerOpacity = useDerivedValue(() => (still ? 1 : smooth(open.get(), 0.7, 1)) * (1 - Math.max(reject.get(), like.get())));
  const whole = useDerivedValue(() => 1);
  const none = useDerivedValue(() => 0);

  return (
    <Canvas style={{ width, height }}>
      <Path path={band} color={BAND} />
      {still && <Path path={flat} color={BAND} opacity={cover} />}

      <Group transform={ringAt}>
        <RoundedRect x={ringX} y={-RING / 2} width={ringWidth} height={RING} r={RING / 2} color="rgba(255,255,255,0.04)" />
        <RoundedRect x={ringX} y={-RING / 2} width={ringWidth} height={RING} r={RING / 2} style="stroke" strokeWidth={ringStroke}>
          <LinearGradient start={vec(-RING / 2, -RING / 2)} end={vec(RING / 2, RING / 2)} colors={['rgba(255,255,255,0.35)', `${accent}99`]} />
        </RoundedRect>
      </Group>

      {/* A person: a head over a rounded body. */}
      <Group transform={[{ translateX: iconX(0, width) }, { translateY: ICON_Y }]} style="stroke" strokeWidth={STROKE} color="#FFFFFF">
        <Circle cx={0} cy={-4.2} r={3.6} />
        <RoundedRect x={-6.5} y={2.3} width={13} height={6.4} r={3.2} />
      </Group>

      {/* Two tilted cards, the upper one in the accent colour. */}
      <Group transform={[{ translateX: iconX(3, width) }, { translateY: ICON_Y }]}>
        <Group transform={[{ translateX: -1.6 }, { translateY: 1.2 }, { rotate: -0.17 }]}>
          <RoundedRect x={-4.5} y={-6} width={9} height={12} r={2.5} style="stroke" strokeWidth={STROKE} color="#FFFFFF" />
        </Group>
        <Group transform={[{ translateX: 2.4 }, { translateY: -1.2 }, { rotate: 0.25 }]}>
          <RoundedRect x={-4.5} y={-6} width={9} height={12} r={2.5} color={BAND} />
          <RoundedRect x={-4.5} y={-6} width={9} height={12} r={2.5} style="stroke" strokeWidth={STROKE} color={accent} />
        </Group>
      </Group>

      <Group opacity={glass}>
        <RoundedRect x={capsuleX} y={capsuleY} width={CAPSULE_WIDTH} height={CAPSULE_HEIGHT} r={CAPSULE_HEIGHT / 2} color="rgba(14,34,51,0.7)" />
        <RoundedRect
          x={capsuleX}
          y={capsuleY}
          width={CAPSULE_WIDTH}
          height={CAPSULE_HEIGHT}
          r={CAPSULE_HEIGHT / 2}
          style="stroke"
          strokeWidth={1}
          color="rgba(255,255,255,0.12)"
        />
        <Pill x={capsuleX + 2} color={rejectColor} strength={reject} flash={flash} inward={1} still={still} />
        <Pill x={capsuleX + CAPSULE_WIDTH - 2 - PILL_WIDTH} color={likeColor} strength={like} flash={flash} inward={-1} still={still} />
        <RoundedRect
          x={markerX}
          y={ICON_Y - MARKER_HEIGHT / 2}
          width={MARKER_WIDTH}
          height={MARKER_HEIGHT}
          r={MARKER_HEIGHT / 2}
          style="stroke"
          strokeWidth={1}
          color="rgba(255,255,255,0.25)"
          opacity={markerOpacity}
        />
      </Group>

      {still ? (
        <>
          <Middle morph={none} opacity={cover} width={width} reject={reject} like={like} still />
          <Middle morph={whole} opacity={open} width={width} reject={reject} like={like} still />
        </>
      ) : (
        <Middle morph={open} opacity={whole} width={width} reject={reject} like={like} still={false} />
      )}
    </Canvas>
  );
}

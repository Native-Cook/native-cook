import { Blur, BlurMask, Canvas, Group, Line, LinearGradient, Paint, Path, Rect, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

// The roller control, in points. The bar is a capsule of BAR height; the drum sits DRUM_AT from its
// left end with the arrow tile beside it. A paper tape runs behind the bar and over the drum: one
// label every PITCH, with TAIL of blank paper past the first and the last.
export const BAR = 84;
export const PITCH = 53;
export const TAIL = 49;
export const TAPE = 54;
export const DRUM_AT = 62;
export const DRUM_WIDTH = 70;
export const DRUM_HEIGHT = 50;
export const TILE_AT = 124;
export const TILE_WIDTH = 36;
export const TILE_HEIGHT = 50;
// Room round the bar for the shadows, on every side of the canvas.
export const BLEED = 24;

const CAP = 12.5;
const DRUM_RADIUS = 8;
const WELL = 6;
const LABEL_WIDTH = 37;
const LABEL_HEIGHT = 26;
// The tape wraps a quarter turn of the drum each way from its middle.
const WRAP = DRUM_HEIGHT / 2;
const ARC = (WRAP * Math.PI) / 2;
// The moving tape is smeared along its way, and the drum's labels with it: this many points of
// blur for every label a second it travels, up to SMEAR_MAX.
const SMEAR = 0.8;
const SMEAR_MAX = 6.5;

const INK = '#2a2a2c';
const PAPER = ['#f4f3f1', '#fdfdfc', '#f1f0ee'];
const ORANGE = ['#8c3a0c', '#e9620f', '#ff9440', '#f26a1b', '#7e330a'];

// The numerals, written by hand with a marker in a 20 × 26 box.
const DIGITS = [
  'M10 3.5C5.5 3.5 4.5 11 5 16C5.5 22 9 24 11.5 22.5C15.5 20 16 9.5 14 5.5C13 3.8 11.5 3.3 10 3.5',
  'M6.5 8C8.5 6.5 10 5 11.5 3C11 9 10.6 16 10.2 23',
  'M5 8C6 3.5 13 2.5 14 7.5C14.8 12 8 17 4.5 22.5C8 20.5 12 22.5 16 21.5',
  'M5.5 5C8.5 2.5 14.5 3 14 7.5C13.6 10.5 10.5 11.8 8.5 12.2C12 12 15.5 14 14.8 18C14 22.5 7.5 23.8 4.5 20.5',
  'M12.5 3C10 8 7 12.5 4.5 15.5C8.5 15.8 12.5 15.5 16.5 15M13 9C12.6 14 12.3 19 12 23.5',
  'M14.5 3.5C11.8 3.3 9.5 3.6 7.5 4C7 6.8 6.6 9.2 6.2 12C9 10.2 14.5 10.8 14.8 15.5C15 20.5 9 24 4.8 20.8',
  'M13.5 3C8.5 6 5 12 5.5 18C6 23.5 13.5 24 14.5 18.5C15.2 14 9 12.5 6 16.5',
  'M4.5 4.5C8 4.8 12 4.4 15.5 3.5C12.5 9.5 9.8 16 8 23.5',
  'M10.5 12.5C5.5 10 6.5 3.5 10.8 3.5C15.5 3.5 15.2 9.5 10.5 12.5C5 15.5 4.5 23 10 23C15.5 23 15.5 15.5 10.5 12.5',
  'M14.5 8.5C13 2.5 5.5 3.5 6 9C6.4 13.5 12.5 13 14.5 8.5C14.6 13.5 13.5 19 11 23.5',
].map((path) => Skia.Path.MakeFromSVGString(path)!);
// No two labels are written quite alike: each leans by its own few degrees.
const LEAN = [-5, 4, -3, 5, -4, 3, -5, 4, -2, 5];
const DIGIT_SCALE = 0.8;
const PAIR_SCALE = 0.62;

const UP = Skia.Path.MakeFromSVGString('M-6 3.6L0 -3.6L6 3.6Z')!;
const DOWN = Skia.Path.MakeFromSVGString('M-6 -3.6L0 3.6L6 -3.6Z')!;

/** A label of the tape round the origin: a faint box and its number in ink. */
function Label({ value }: { value: number }) {
  const digits = String(value).split('').map(Number);
  const scale = digits.length > 1 ? PAIR_SCALE : DIGIT_SCALE;
  const advance = 15 * scale;
  return (
    <Group>
      <RoundedRect x={-LABEL_WIDTH / 2} y={-LABEL_HEIGHT / 2} width={LABEL_WIDTH} height={LABEL_HEIGHT} r={5} color="rgba(255,255,255,0.55)" />
      <RoundedRect x={-LABEL_WIDTH / 2} y={-LABEL_HEIGHT / 2} width={LABEL_WIDTH} height={LABEL_HEIGHT} r={5} style="stroke" strokeWidth={1} color="rgba(0,0,0,0.11)" />
      <Group transform={[{ rotate: (LEAN[value % LEAN.length] * Math.PI) / 180 }]}>
        {digits.map((digit, index) => (
          <Path
            key={index}
            path={DIGITS[digit]}
            transform={[{ translateX: (index - (digits.length - 1) / 2) * advance - 10 * scale }, { translateY: -13 * scale }, { scale }]}
            style="stroke"
            strokeWidth={2.5 / scale}
            strokeCap="round"
            strokeJoin="round"
            color={INK}
          />
        ))}
      </Group>
    </Group>
  );
}

type WrappedProps = { value: number; place: SharedValue<number>; x: number; y: number };

/** The same label where the tape lies on the drum: it squashes as it turns over the top or the bottom. */
function Wrapped({ value, place, x, y }: WrappedProps) {
  const transform = useDerivedValue(() => {
    const along = Math.max(-ARC, Math.min(ARC, (value - place.get()) * PITCH));
    const turn = along / WRAP;
    return [{ translateX: x }, { translateY: y + WRAP * Math.sin(turn) }, { scaleY: Math.max(0.02, Math.cos(turn)) }];
  });
  const opacity = useDerivedValue(() => (Math.abs((value - place.get()) * PITCH) < ARC ? 1 : 0));
  return (
    <Group transform={transform} opacity={opacity}>
      <Label value={value} />
    </Group>
  );
}

type Props = {
  /** The canvas is as wide as the screen; the bar is inset by `margin` on both sides. */
  width: number;
  margin: number;
  /** The highest number on the tape. */
  max: number;
  /** Where the tape is: the number in the drum's window, changing smoothly between two. */
  place: SharedValue<number>;
  /** 1 while the up or the down arrow is held, 0 otherwise. */
  upHeld: SharedValue<number>;
  downHeld: SharedValue<number>;
  canUp: boolean;
  canDown: boolean;
  /** Reduced motion: no smear. */
  still: boolean;
};

/** How far the tape reaches above and below the bar, for whoever lays the roller out. */
export function reach(max: number) {
  return (max - 1) * PITCH + TAIL - BAR / 2;
}

/**
 * The add-to-cart bar's quantity roller: a paper tape of hand-written numbers behind a frosted
 * capsule, an orange drum the tape turns over, and a tile with two arrows. It only draws: the
 * touches are handled by whoever places it.
 */
export function Roller({ width, margin, max, place, upHeld, downHeld, canUp, canDown, still }: Props) {
  const above = reach(max) + BLEED;
  const cy = above + BAR / 2;
  const bar = { x: margin, y: above, width: width - margin * 2, height: BAR };
  const drumX = margin + DRUM_AT;
  const drum = { x: drumX - DRUM_WIDTH / 2, y: cy - DRUM_HEIGHT / 2 };
  const tapeX = drumX - TAPE / 2;
  const tileX = margin + TILE_AT;
  const tile = { x: tileX, y: cy - TILE_HEIGHT / 2 };
  const numbers = Array.from({ length: max }, (_, index) => index + 1);

  // The tape moves up as the number grows: the next one comes from below.
  const travel = useDerivedValue(() => [{ translateY: -(place.get() - 1) * PITCH }]);
  // The tape's speed, in labels a second, measured frame by frame.
  const before = useSharedValue<number | null>(null);
  const speed = useSharedValue(0);
  useFrameCallback((frame) => {
    const elapsed = frame.timeSincePreviousFrame;
    const last = before.get();
    if (elapsed && last !== null) speed.set(Math.abs(place.get() - last) / (elapsed / 1000));
    before.set(place.get());
  });
  const smear = useDerivedValue(() => ({ x: 0, y: still ? 0 : Math.min(SMEAR_MAX, SMEAR * speed.get()) }));
  const upShade = useDerivedValue(() => 0.1 * upHeld.get());
  const downShade = useDerivedValue(() => 0.1 * downHeld.get());
  const sink = useDerivedValue(() => [{ translateY: upHeld.get() * -0.6 + downHeld.get() * 0.6 }]);

  return (
    <Canvas style={{ width, height: above * 2 + BAR }}>
      {/* The tape: paper with its shadow on the page, smeared along its way while it runs. */}
      <Group
        layer={
          <Paint>
            <Blur blur={smear} />
          </Paint>
        }
      >
        <Group transform={travel}>
          <RoundedRect x={tapeX + 5} y={cy - TAIL + 7} width={TAPE} height={(max - 1) * PITCH + TAIL * 2} r={4} color="rgba(0,0,0,0.2)">
            <BlurMask blur={8} style="normal" />
          </RoundedRect>
          <RoundedRect x={tapeX} y={cy - TAIL} width={TAPE} height={(max - 1) * PITCH + TAIL * 2} r={3}>
            <LinearGradient start={vec(tapeX, 0)} end={vec(tapeX + TAPE, 0)} colors={PAPER} />
          </RoundedRect>
          {numbers.map((value) => (
            <Group key={value} transform={[{ translateX: drumX }, { translateY: cy + (value - 1) * PITCH }]}>
              <Label value={value} />
            </Group>
          ))}
        </Group>
      </Group>

      {/* The bar: a raised capsule of frosted clay. The tape behind it shows through as a paler lane. */}
      <RoundedRect x={bar.x} y={bar.y + 12} width={bar.width} height={bar.height} r={BAR / 2} color="rgba(60,50,40,0.26)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={bar.x} y={bar.y} width={bar.width} height={bar.height} r={BAR / 2}>
        <LinearGradient
          start={vec(0, bar.y)}
          end={vec(0, bar.y + BAR)}
          colors={['rgba(240,239,237,0.93)', 'rgba(226,224,222,0.9)', 'rgba(217,214,212,0.9)', 'rgba(234,233,231,0.93)']}
          positions={[0, 0.3, 0.62, 1]}
        />
      </RoundedRect>
      <RoundedRect x={bar.x + 1} y={bar.y + 1} width={bar.width - 2} height={bar.height - 2} r={BAR / 2 - 1} style="stroke" strokeWidth={2}>
        <LinearGradient start={vec(0, bar.y)} end={vec(0, bar.y + BAR)} colors={['#ffffff', 'rgba(255,255,255,0.75)', 'rgba(255,255,255,0.95)']} />
      </RoundedRect>

      {/* The well the drum turns in, and the ends of its axle. */}
      <RoundedRect x={drum.x - WELL} y={drum.y - WELL + 1} width={DRUM_WIDTH + WELL * 2} height={DRUM_HEIGHT + WELL * 2 - 2} r={DRUM_RADIUS + 4} color="#cfccca" />
      <Group clip={Skia.RRectXY(Skia.XYWHRect(drum.x - WELL, drum.y - WELL + 1, DRUM_WIDTH + WELL * 2, DRUM_HEIGHT + WELL * 2 - 2), DRUM_RADIUS + 4, DRUM_RADIUS + 4)}>
        <RoundedRect x={drum.x - WELL} y={drum.y - WELL - 3} width={DRUM_WIDTH + WELL * 2} height={DRUM_HEIGHT + WELL * 2} r={DRUM_RADIUS + 4} style="stroke" strokeWidth={5} color="rgba(0,0,0,0.3)">
          <BlurMask blur={3} style="normal" />
        </RoundedRect>
      </Group>
      <RoundedRect x={drum.x - WELL} y={drum.y - WELL + 1} width={DRUM_WIDTH + WELL * 2} height={DRUM_HEIGHT + WELL * 2 - 2} r={DRUM_RADIUS + 4} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.8)" />
      {[drum.x - 5, drum.x + DRUM_WIDTH - 2].map((x) => (
        <RoundedRect key={x} x={x} y={cy - 13} width={7} height={26} r={2}>
          <LinearGradient start={vec(0, cy - 13)} end={vec(0, cy + 13)} colors={['#7d8482', '#c9cfcd', '#9ba19f', '#6f7573']} positions={[0, 0.35, 0.7, 1]} />
        </RoundedRect>
      ))}

      {/* The drum: paper round its middle, ribbed orange caps, and the shade that makes it round. */}
      <RoundedRect x={drum.x + 1} y={drum.y + 4} width={DRUM_WIDTH - 2} height={DRUM_HEIGHT} r={DRUM_RADIUS} color="rgba(0,0,0,0.3)">
        <BlurMask blur={4} style="normal" />
      </RoundedRect>
      <Group clip={Skia.RRectXY(Skia.XYWHRect(drum.x, drum.y, DRUM_WIDTH, DRUM_HEIGHT), DRUM_RADIUS, DRUM_RADIUS)}>
        <Rect x={drum.x} y={drum.y} width={DRUM_WIDTH} height={DRUM_HEIGHT} color="#fbfaf9" />
        <Group
          layer={
            <Paint>
              <Blur blur={smear} />
            </Paint>
          }
        >
          {numbers.map((value) => (
            <Wrapped key={value} value={value} place={place} x={drumX} y={cy} />
          ))}
        </Group>
        {[drum.x, drum.x + DRUM_WIDTH - CAP].map((x, side) => (
          <Group key={x}>
            <Rect x={x} y={drum.y} width={CAP} height={DRUM_HEIGHT}>
              <LinearGradient start={vec(0, drum.y)} end={vec(0, drum.y + DRUM_HEIGHT)} colors={ORANGE} positions={[0, 0.2, 0.42, 0.72, 1]} />
            </Rect>
            {/* The ribs of the cap, and the line where it meets the paper. */}
            {[2.4, 5, 7.6, 10.2].map((rib) => (
              <Line key={rib} p1={vec(x + rib, drum.y)} p2={vec(x + rib, drum.y + DRUM_HEIGHT)} strokeWidth={1} color="rgba(255,214,170,0.5)" />
            ))}
            <Rect x={side === 0 ? x + CAP - 1 : x} y={drum.y} width={1} height={DRUM_HEIGHT} color="rgba(90,30,0,0.55)" />
          </Group>
        ))}
        <Rect x={drum.x} y={drum.y} width={DRUM_WIDTH} height={DRUM_HEIGHT}>
          <LinearGradient
            start={vec(0, drum.y)}
            end={vec(0, drum.y + DRUM_HEIGHT)}
            colors={['rgba(0,0,0,0.42)', 'rgba(0,0,0,0.06)', 'rgba(255,255,255,0.14)', 'rgba(0,0,0,0.05)', 'rgba(0,0,0,0.4)']}
            positions={[0, 0.2, 0.4, 0.74, 1]}
          />
        </Rect>
      </Group>

      {/* The tile with the two arrows; the half that is held goes darker and gives a little. */}
      <RoundedRect x={tile.x} y={tile.y + 3} width={TILE_WIDTH} height={TILE_HEIGHT} r={8} color="rgba(0,0,0,0.2)">
        <BlurMask blur={4} style="normal" />
      </RoundedRect>
      <Group transform={sink}>
        <RoundedRect x={tile.x} y={tile.y} width={TILE_WIDTH} height={TILE_HEIGHT} r={8}>
          <LinearGradient start={vec(0, tile.y)} end={vec(0, tile.y + TILE_HEIGHT)} colors={['#f7f6f4', '#eceae8']} />
        </RoundedRect>
        <RoundedRect x={tile.x + 0.5} y={tile.y + 0.5} width={TILE_WIDTH - 1} height={TILE_HEIGHT - 1} r={7.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.9)" />
        <Group clip={Skia.RRectXY(Skia.XYWHRect(tile.x, tile.y, TILE_WIDTH, TILE_HEIGHT), 8, 8)}>
          <Rect x={tile.x} y={tile.y} width={TILE_WIDTH} height={TILE_HEIGHT / 2} color="black" opacity={upShade} />
          <Rect x={tile.x} y={cy} width={TILE_WIDTH} height={TILE_HEIGHT / 2} color="black" opacity={downShade} />
        </Group>
        <Rect x={tile.x + 8} y={cy - 0.5} width={TILE_WIDTH - 16} height={1} color="rgba(0,0,0,0.08)" />
        <Path path={UP} transform={[{ translateX: tile.x + TILE_WIDTH / 2 }, { translateY: cy - 11.5 }]} color={INK} opacity={canUp ? 0.9 : 0.25} />
        <Path path={DOWN} transform={[{ translateX: tile.x + TILE_WIDTH / 2 }, { translateY: cy + 11.5 }]} color={INK} opacity={canDown ? 0.9 : 0.25} />
      </Group>
    </Canvas>
  );
}

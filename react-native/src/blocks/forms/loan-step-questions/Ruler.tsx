import { Group, LinearGradient, Path, Rect, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

/** Points between two ticks; one tick is one step of the value. */
export const TICK_STEP = 10;
// Every fifth tick is taller.
const TALL_EVERY = 5;
const TICK = 14;
const TICK_TALL = 22;
const TICK_WIDTH = 1.2;
const MARKER_WIDTH = 5;
const MARKER_HEIGHT = 40;
// The ticks fade out over this much at both ends of the ruler.
const FADE = 44;

type Props = {
  /** How far the ruler is pulled, in points: 0 at the least value, TICK_STEP more for every step. */
  offset: SharedValue<number>;
  /** The ticks run from `left` to `right`; the marker stands in the middle of the screen, at `centre`. */
  left: number;
  right: number;
  centre: number;
  /** The middle of the ticks' height. */
  y: number;
  tickColor: string;
  markerColor: string;
  /** What is behind the ruler, for the fade at its ends. */
  background: string;
};

/** Ticks spaced TICK_STEP apart round x = 0, wide enough to cover the ruler pulled by up to one period. */
function ticks(reach: number) {
  const count = Math.ceil(reach / TICK_STEP) + TALL_EVERY;
  const lines: string[] = [];
  for (let i = -count; i <= count; i++) {
    const half = (i % TALL_EVERY === 0 ? TICK_TALL : TICK) / 2;
    lines.push(`M${i * TICK_STEP} ${-half}V${half}`);
  }
  return Skia.Path.MakeFromSVGString(lines.join(''))!;
}

/**
 * A ruler that moves under a marker fixed in the middle. The ticks repeat every five, so the
 * strip is drawn once and shifted by the pull's remainder: it has no ends, and the value is
 * limited where the pull is.
 */
export function Ruler({ offset, left, right, centre, y, tickColor, markerColor, background }: Props) {
  const period = TICK_STEP * TALL_EVERY;
  const strip = useMemo(() => ticks(Math.max(centre - left, right - centre)), [centre, left, right]);
  const transform = useDerivedValue(() => {
    const pulled = ((offset.get() % period) + period) % period;
    return [{ translateX: centre - pulled }, { translateY: y }];
  });
  const clear = `${background}00`;
  return (
    <>
      <Group clip={Skia.XYWHRect(left, y - TICK_TALL, right - left, TICK_TALL * 2)}>
        <Group transform={transform}>
          <Path path={strip} style="stroke" strokeWidth={TICK_WIDTH} strokeCap="round" color={tickColor} />
        </Group>
      </Group>
      <Rect x={left} y={y - TICK_TALL} width={FADE} height={TICK_TALL * 2}>
        <LinearGradient start={vec(left, 0)} end={vec(left + FADE, 0)} colors={[background, clear]} />
      </Rect>
      <Rect x={right - FADE} y={y - TICK_TALL} width={FADE} height={TICK_TALL * 2}>
        <LinearGradient start={vec(right - FADE, 0)} end={vec(right, 0)} colors={[clear, background]} />
      </Rect>
      <RoundedRect
        x={centre - MARKER_WIDTH / 2}
        y={y - MARKER_HEIGHT / 2}
        width={MARKER_WIDTH}
        height={MARKER_HEIGHT}
        r={MARKER_WIDTH / 2}
        color={markerColor}
      />
    </>
  );
}

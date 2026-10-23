import { Canvas, DashPathEffect, Group, Path, Rect, RoundedRect, Skia, rrect, vec, type SkPath } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { ReduceMotion, type SharedValue, useDerivedValue, withTiming } from 'react-native-reanimated';

import { STAGE_COLORS, type Night, type Stage, stagesOf } from './sleep-data';

const GRID = 'rgba(255,255,255,0.16)';
const BAND = 'rgba(255,255,255,0.05)';
const HATCH = 'rgba(255,255,255,0.07)';
const AVERAGE = 'rgba(255,255,255,0.85)';
const RING = 'rgba(255,255,255,0.6)';

const HATCH_STEP = 7;
const RING_WIDTH = 2;
const RING_GAP = 2;
// The scrubbed column widens by this share; the others keep this much of their light.
const LIFT = 0.12;
const DIMMED = 0.7;
// Milliseconds for a column to take or give up the lift.
const LIFT_FOR = 160;

export type ChartFrame = {
  width: number;
  /** Where the time axis starts and ends on the canvas. */
  top: number;
  bottom: number;
  /** The first minute (top) and the last (bottom) of the axis. */
  from: number;
  to: number;
  /** Centre of the first column and the step to the next. */
  first: number;
  step: number;
  column: number;
  plotWidth: number;
};

type Bands = {
  /** Shaded band of the ideal time to fall asleep, in minutes. */
  ideal: [number, number];
  /** Hatched band of the usual wake-up time, in minutes. */
  wake: [number, number];
  grid: number[];
  averageAsleep: number;
  averageAwake: number;
};

type Props = {
  frame: ChartFrame;
  nights: Night[];
  bands: Bands;
  /** The scrubbed column, and 1 while a finger is on the chart. */
  selected: SharedValue<number>;
  press: SharedValue<number>;
  still: boolean;
};

export function yOf(frame: ChartFrame, minutes: number) {
  'worklet';
  return frame.top + ((minutes - frame.from) / (frame.to - frame.from)) * (frame.bottom - frame.top);
}

type ColumnProps = {
  index: number;
  x: number;
  top: number;
  bottom: number;
  width: number;
  stages: { color: string; path: SkPath }[];
  selected: SharedValue<number>;
  press: SharedValue<number>;
  still: boolean;
};

/** One night's column: its stage runs clipped to a capsule, widened and ringed while scrubbed. */
function Column({ index, x, top, bottom, width, stages, selected, press, still }: ColumnProps) {
  const on = useDerivedValue(() =>
    withTiming(selected.get() === index ? 1 : 0, { duration: still ? 0 : LIFT_FOR, reduceMotion: ReduceMotion.Never }),
  );
  const transform = useDerivedValue(() => [{ scaleX: 1 + (still ? 0 : LIFT) * on.get() * press.get() }]);
  const opacity = useDerivedValue(() => 1 - (1 - DIMMED) * press.get() * (1 - on.get()));
  const ring = useDerivedValue(() => on.get() * press.get());
  const shape = rrect(Skia.XYWHRect(x - width / 2, top, width, bottom - top), width / 2, width / 2);
  const outline = rrect(
    Skia.XYWHRect(x - width / 2 - RING_GAP, top - RING_GAP, width + RING_GAP * 2, bottom - top + RING_GAP * 2),
    width / 2 + RING_GAP,
    width / 2 + RING_GAP,
  );
  return (
    <Group transform={transform} origin={vec(x, (top + bottom) / 2)} opacity={opacity}>
      <Group clip={shape}>
        {stages.map(({ color, path }) => (
          <Path key={color} path={path} color={color} />
        ))}
      </Group>
      <Group opacity={ring}>
        <RoundedRect rect={outline} style="stroke" strokeWidth={RING_WIDTH} color={RING} />
      </Group>
    </Group>
  );
}

/** The chart's drawing: bands, grid, average lines and one column of sleep stages per night. */
export function SleepColumns({ frame, nights, bands, selected, press, still }: Props) {
  const columns = useMemo(
    () =>
      nights.map((night, index) => {
        const builders = new Map<Stage, ReturnType<typeof Skia.PathBuilder.Make>>();
        const x = frame.first + frame.step * index;
        let at = night.asleep;
        for (const run of stagesOf(night, index)) {
          const builder = builders.get(run.stage) ?? Skia.PathBuilder.Make();
          builders.set(run.stage, builder);
          const y0 = yOf(frame, at);
          const y1 = yOf(frame, at + run.minutes);
          // A hair of overlap so no seam shows between runs.
          builder.addRect(Skia.XYWHRect(x - frame.column, y0, frame.column * 2, y1 - y0 + 0.5));
          at += run.minutes;
        }
        const stages = [...builders.entries()].map(([stage, builder]) => ({ color: STAGE_COLORS[stage], path: builder.build() }));
        return { x, top: yOf(frame, night.asleep), bottom: yOf(frame, night.awake), stages };
      }),
    [nights, frame],
  );

  const lines = useMemo(() => {
    const grid = Skia.PathBuilder.Make();
    for (const minutes of bands.grid) {
      const y = Math.round(yOf(frame, minutes)) + 0.5;
      grid.moveTo(0, y).lineTo(frame.width, y);
    }
    const hatch = Skia.PathBuilder.Make();
    const y0 = yOf(frame, bands.wake[0]);
    const y1 = yOf(frame, bands.wake[1]);
    for (let x = -(y1 - y0); x < frame.width; x += HATCH_STEP) hatch.moveTo(x, y1).lineTo(x + (y1 - y0), y0);
    const asleep = Math.round(yOf(frame, bands.averageAsleep)) + 0.5;
    const awake = Math.round(yOf(frame, bands.averageAwake)) + 0.5;
    return {
      grid: grid.build(),
      hatch: hatch.build(),
      hatchClip: Skia.XYWHRect(0, y0, frame.width, y1 - y0),
      ideal: Skia.XYWHRect(0, yOf(frame, bands.ideal[0]), frame.width, yOf(frame, bands.ideal[1]) - yOf(frame, bands.ideal[0])),
      asleep: Skia.PathBuilder.Make().moveTo(0, asleep).lineTo(frame.plotWidth, asleep).build(),
      awake: Skia.PathBuilder.Make().moveTo(0, awake).lineTo(frame.plotWidth, awake).build(),
    };
  }, [bands, frame]);

  return (
    <Canvas style={{ width: frame.width, height: frame.bottom + 24 }}>
      <Rect rect={lines.ideal} color={BAND} />
      <Group clip={lines.hatchClip}>
        <Rect rect={lines.hatchClip} color={BAND} />
        <Path path={lines.hatch} style="stroke" strokeWidth={2} color={HATCH} />
      </Group>
      <Path path={lines.grid} style="stroke" strokeWidth={1} color={GRID}>
        <DashPathEffect intervals={[1.5, 5]} />
      </Path>
      {columns.map((column, index) => (
        <Column
          key={index}
          index={index}
          x={column.x}
          top={column.top}
          bottom={column.bottom}
          width={frame.column}
          stages={column.stages}
          selected={selected}
          press={press}
          still={still}
        />
      ))}
      <Path path={lines.asleep} style="stroke" strokeWidth={1.5} color={AVERAGE} />
      <Path path={lines.awake} style="stroke" strokeWidth={1.5} color={AVERAGE}>
        <DashPathEffect intervals={[4, 4]} />
      </Path>
    </Canvas>
  );
}

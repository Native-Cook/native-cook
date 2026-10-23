import { BlurStyle, Canvas, PaintStyle, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, Text } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { LEVEL_COLORS, type Period, levelAt } from './stress-data';

const LABEL = '#6C6C70';
const ROW_LINE = [0.17, 0.17, 0.19];
const NOW_RING = '#8E8E93';

// Layout, in points.
const HEADER = 24;
const ROW = 28;
// The row labels' column: day names are short, week ranges are not.
const DAY_LABELS = 40;
const WEEK_LABELS = 92;
// Dot radii: a day's period, a day of the four weeks, and a cell with no data.
const DOT_DAY = 4;
const DOT_WEEK = 5;
const DOT_EMPTY = 2;
const NOW_RADIUS = 6;
const GLOW_BLUR = 3;
const GLOW_ALPHA = 0.45;

// Motion, in seconds of the morph: each dot moves for DOT_FOR, starting up to STAGGER later the
// farther it is from the top left.
export const DOT_FOR = 0.6;
export const STAGGER = 0.3;
export const MORPH_FOR = DOT_FOR + STAGGER;
const LABELS_OVER = 0.4;

export function gridHeight(period: Period) {
  return HEADER + period.rows.length * ROW;
}

const RGB: Record<number, number[]> = {
  0: hex(LEVEL_COLORS.none),
  1: hex(LEVEL_COLORS.low),
  2: hex(LEVEL_COLORS.normal),
  3: hex(LEVEL_COLORS.high),
};

function hex(color: string) {
  return [parseInt(color.slice(1, 3), 16) / 255, parseInt(color.slice(3, 5), 16) / 255, parseInt(color.slice(5, 7), 16) / 255];
}

/** Smooth start and stop over 0…1. */
function ease(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2;
}

function geometry(period: Period, width: number, labels: number) {
  const pitch = (width - labels) / period.columns.length;
  return {
    x: (column: number) => labels + pitch * (column + 0.5),
    y: (row: number) => HEADER + ROW * (row + 0.5),
  };
}

type Props = {
  width: number;
  days: Period;
  weeks: Period;
  /** 0 shows the days, 1 the weeks; between, every dot is on its way. */
  morph: SharedValue<number>;
  /** The grid's opacity; dips when the view jumps under reduced motion. */
  opacity: SharedValue<number>;
};

/**
 * The stress heat map. Every two-hour dot of the week has a place in the four-week grid: the day's
 * row becomes that weekday's column, and its hours split into the four week rows, so three dots
 * meet in each cell of the four weeks and the grid can morph either way dot for dot.
 */
export function DotGrid({ width, days, weeks, morph, opacity }: Props) {
  const dots = useMemo(() => {
    const from = geometry(days, width, DAY_LABELS);
    const to = geometry(weeks, width, WEEK_LABELS);
    const list: number[][] = [];
    const reach = Math.hypot(width, gridHeight(days));
    for (let r = 0; r < days.rows.length; r++) {
      for (let c = 0; c < days.columns.length; c++) {
        const level = levelAt(days, r, c);
        const row = Math.min(weeks.rows.length - 1, Math.floor((c * weeks.rows.length) / days.columns.length));
        const column = Math.min(weeks.columns.length - 1, Math.floor((r * weeks.columns.length) / days.rows.length));
        const target = levelAt(weeks, row, column);
        const x = from.x(c);
        const y = from.y(r);
        list.push([
          x,
          y,
          level ? DOT_DAY : DOT_EMPTY,
          ...RGB[level],
          to.x(column),
          to.y(row),
          target ? DOT_WEEK : DOT_EMPTY,
          ...RGB[target],
          (Math.hypot(x, y) / reach) * STAGGER,
        ]);
      }
    }
    const lines = (period: Period, labels: number) => {
      const at = geometry(period, width, labels);
      return period.rows.map((_, r) => [at.x(0), at.x(period.columns.length - 1), at.y(r)]);
    };
    const now = [from.x(days.now[1]), from.y(days.now[0]), to.x(weeks.now[1]), to.y(weeks.now[0])];
    return { list, dayLines: lines(days, DAY_LABELS), weekLines: lines(weeks, WEEK_LABELS), now };
  }, [days, weeks, width]);

  const picture = useDerivedValue(() => {
    const m = morph.get();
    const whole = ease(m);
    const { list, dayLines, weekLines, now } = dots;
    return createPicture((canvas) => {
      const line = Skia.Paint();
      line.setStrokeWidth(1);
      line.setColor(Float32Array.of(ROW_LINE[0], ROW_LINE[1], ROW_LINE[2], 1 - whole));
      for (let i = 0; i < dayLines.length; i++) canvas.drawLine(dayLines[i][0], dayLines[i][2], dayLines[i][1], dayLines[i][2], line);
      line.setColor(Float32Array.of(ROW_LINE[0], ROW_LINE[1], ROW_LINE[2], whole));
      for (let i = 0; i < weekLines.length; i++) canvas.drawLine(weekLines[i][0], weekLines[i][2], weekLines[i][1], weekLines[i][2], line);

      const glow = Skia.Paint();
      glow.setAntiAlias(true);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, GLOW_BLUR, true));
      const dot = Skia.Paint();
      dot.setAntiAlias(true);
      for (let i = 0; i < list.length; i++) {
        const d = list[i];
        const k = ease((m * MORPH_FOR - d[12]) / DOT_FOR);
        const x = d[0] + (d[6] - d[0]) * k;
        const y = d[1] + (d[7] - d[1]) * k;
        const radius = d[2] + (d[8] - d[2]) * k;
        const red = d[3] + (d[9] - d[3]) * k;
        const green = d[4] + (d[10] - d[4]) * k;
        const blue = d[5] + (d[11] - d[5]) * k;
        // Only dots with data glow; an empty one fades its glow out as it fills.
        const lit = (d[2] > DOT_EMPTY ? 1 - k : 0) + (d[8] > DOT_EMPTY ? k : 0);
        if (lit > 0) {
          glow.setColor(Float32Array.of(red, green, blue, GLOW_ALPHA * lit));
          canvas.drawCircle(x, y, radius + 1, glow);
        }
        dot.setColor(Float32Array.of(red, green, blue, 1));
        canvas.drawCircle(x, y, radius, dot);
      }

      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(1.5);
      ring.setColor(Skia.Color(NOW_RING));
      canvas.drawCircle(now[0] + (now[2] - now[0]) * whole, now[1] + (now[3] - now[1]) * whole, NOW_RADIUS, ring);
    });
  });

  // The labels hand over without overlapping: the old ones leave in the first part of the morph, the new arrive in the last.
  const daysStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.max(0, morph.get() / LABELS_OVER)) }));
  const weeksStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, (morph.get() - 1 + LABELS_OVER) / LABELS_OVER)) }));
  const dayHeight = gridHeight(days);
  const weekHeight = gridHeight(weeks);
  const boxStyle = useAnimatedStyle(() => ({
    height: dayHeight + (weekHeight - dayHeight) * ease(morph.get()),
    opacity: opacity.get(),
  }));

  const labels = (period: Period, labelWidth: number) => {
    const at = geometry(period, width, labelWidth);
    return (
      <>
        {period.columns.map((label, c) => (
          <Text key={`c${c}`} style={[styles.label, styles.column, { left: at.x(c) - 20 }]}>
            {label}
          </Text>
        ))}
        {period.rows.map((label, r) => (
          <Text key={`r${r}`} style={[styles.label, { left: 0, top: at.y(r) - 7 }]} numberOfLines={1}>
            {label}
          </Text>
        ))}
      </>
    );
  };

  return (
    <Animated.View style={[styles.box, boxStyle]}>
      <Canvas style={{ width, height: Math.max(dayHeight, weekHeight) }}>
        <Picture picture={picture} />
      </Canvas>
      <Animated.View style={[StyleSheet.absoluteFill, daysStyle]} pointerEvents="none">
        {labels(days, DAY_LABELS)}
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, weeksStyle]} pointerEvents="none">
        {labels(weeks, WEEK_LABELS)}
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  box: {
    overflow: 'hidden',
  },
  label: {
    position: 'absolute',
    color: LABEL,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  column: {
    top: 2,
    width: 40,
    textAlign: 'center',
  },
});

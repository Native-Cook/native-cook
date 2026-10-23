import { Canvas, Circle, DashPathEffect, Group, Line, LinearGradient, Path, Skia, type SkPath, vec } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

import { FADE, type Frame, GROW, MOVE, POINTS, SPREAD, clamp01, easeInOut, lineAt } from './candle-data';

export const LINE_GREEN = '#1fa463';
const UP = '#00c076';
const DOWN = '#f6465d';
const AREA = ['rgba(31,164,99,0.45)', 'rgba(31,164,99,0.12)', 'rgba(31,164,99,0)'];
const AREA_AT = [0, 0.6, 1];
const CURSOR = '#71767b';
const LINE = 1.6;
const WICK = 1;
const DOT = 4.5;
const DASH = [3, 4];

type Props = {
  width: number;
  height: number;
  /** Room above and below the prices. */
  pad: number;
  /** The morph: from one frame to another, `time` seconds in, `length` seconds long. */
  from: SharedValue<Frame>;
  to: SharedValue<Frame>;
  time: SharedValue<number>;
  length: SharedValue<number>;
  cursor: SharedValue<number>;
  held: SharedValue<number>;
};

type Picture = {
  line: SkPath;
  area: SkPath;
  upBodies: SkPath;
  downBodies: SkPath;
  upWicks: SkPath;
  downWicks: SkPath;
  lineAlpha: number;
  values: number[];
  low: number;
  high: number;
};

/** How far along candle `i` is: line to candles grows left to right after the fade, back mirrors it. */
export function candleProgress(from: Frame, to: Frame, x: number, time: number) {
  'worklet';
  if (from.lineAlpha > 0.5 && to.lineAlpha < 0.5) return easeInOut((time - FADE - x * SPREAD) / GROW);
  if (from.lineAlpha < 0.5 && to.lineAlpha > 0.5) return easeInOut((time - x * SPREAD) / GROW);
  return easeInOut(time / MOVE);
}

/** The line fades out first on the way to candles, and in last on the way back. */
export function lineAlphaAt(from: Frame, to: Frame, time: number, length: number) {
  'worklet';
  if (from.lineAlpha > to.lineAlpha) return from.lineAlpha * (1 - clamp01(time / FADE));
  if (from.lineAlpha < to.lineAlpha) return from.lineAlpha + (to.lineAlpha - from.lineAlpha) * clamp01((time - (length - FADE)) / FADE);
  return to.lineAlpha;
}

/** The frame on screen `time` seconds into a morph, so a new morph can start from it. */
export function frameAt(from: Frame, to: Frame, time: number, length: number): Frame {
  'worklet';
  if (time >= length) return to;
  const e = easeInOut(time / length);
  const out: Frame = {
    o: [],
    h: [],
    l: [],
    c: [],
    x: [],
    w: [],
    line: [],
    low: from.low + (to.low - from.low) * e,
    high: from.high + (to.high - from.high) * e,
    lineAlpha: lineAlphaAt(from, to, time, length),
    candles: Math.max(from.candles, to.candles),
  };
  for (let i = 0; i < POINTS; i++) {
    const u = candleProgress(from, to, from.x[i], time);
    out.o.push(from.o[i] + (to.o[i] - from.o[i]) * u);
    out.h.push(from.h[i] + (to.h[i] - from.h[i]) * u);
    out.l.push(from.l[i] + (to.l[i] - from.l[i]) * u);
    out.c.push(from.c[i] + (to.c[i] - from.c[i]) * u);
    out.x.push(from.x[i] + (to.x[i] - from.x[i]) * u);
    out.w.push(from.w[i] + (to.w[i] - from.w[i]) * u);
    out.line.push(from.line[i] + (to.line[i] - from.line[i]) * e);
  }
  return out;
}

/**
 * The price as a line with a green area, or as candles: one picture built from a morph between
 * two frames. Every candle is drawn every frame, so 30m candles merge into 4h ones by moving
 * and widening, and a line grows candles by opening each one out of its own point.
 */
export function CandleChart({ width, height, pad, from, to, time, length, cursor, held }: Props) {
  const plot = height - pad * 2;

  const picture = useDerivedValue<Picture>(() => {
    const a = from.get();
    const b = to.get();
    const t = time.get();
    const d = length.get();
    const now = frameAt(a, b, t, d);
    const span = now.high - now.low || 1;
    const y = (v: number) => pad + (1 - (v - now.low) / span) * plot;

    const line = Skia.PathBuilder.Make();
    const area = Skia.PathBuilder.Make();
    for (let i = 0; i < POINTS; i++) {
      const px = ((i + 0.5) / POINTS) * width;
      const py = y(now.line[i]);
      if (i === 0) {
        line.moveTo(px, py);
        area.moveTo(px, height);
        area.lineTo(px, py);
      } else {
        line.lineTo(px, py);
        area.lineTo(px, py);
      }
    }
    area.lineTo(((POINTS - 0.5) / POINTS) * width, height).close();

    const upBodies = Skia.PathBuilder.Make();
    const downBodies = Skia.PathBuilder.Make();
    const upWicks = Skia.PathBuilder.Make();
    const downWicks = Skia.PathBuilder.Make();
    if (now.candles > 0) {
      // Between two candle views a flat candle keeps a hairline body; growing from the line it starts at nothing.
      const floor = a.candles > 0 && b.candles > 0 ? 1 : 0;
      for (let i = 0; i < POINTS; i++) {
        const top = y(Math.max(now.o[i], now.c[i]));
        const bottom = Math.max(top + floor, y(Math.min(now.o[i], now.c[i])));
        const wickTop = y(now.h[i]);
        const wickBottom = y(now.l[i]);
        if (wickBottom - wickTop < 0.3) continue;
        const cx = now.x[i] * width;
        const half = (now.w[i] * width) / 2;
        const up = now.c[i] >= now.o[i];
        (up ? upWicks : downWicks).addRect({ x: cx - WICK / 2, y: wickTop, width: WICK, height: wickBottom - wickTop });
        if (bottom - top >= 0.3) (up ? upBodies : downBodies).addRect({ x: cx - half, y: top, width: half * 2, height: bottom - top });
      }
    }
    return {
      line: line.build(),
      area: area.build(),
      upBodies: upBodies.build(),
      downBodies: downBodies.build(),
      upWicks: upWicks.build(),
      downWicks: downWicks.build(),
      lineAlpha: now.lineAlpha,
      values: now.line,
      low: now.low,
      high: now.high,
    };
  });

  const line = useDerivedValue(() => picture.get().line);
  const area = useDerivedValue(() => picture.get().area);
  const upBodies = useDerivedValue(() => picture.get().upBodies);
  const downBodies = useDerivedValue(() => picture.get().downBodies);
  const upWicks = useDerivedValue(() => picture.get().upWicks);
  const downWicks = useDerivedValue(() => picture.get().downWicks);
  const lineAlpha = useDerivedValue(() => picture.get().lineAlpha);
  const dot = useDerivedValue(() => {
    const p = picture.get();
    const x = cursor.get();
    return vec(x * width, pad + (1 - (lineAt(p.values, x) - p.low) / (p.high - p.low || 1)) * plot);
  });
  const dotAlpha = useDerivedValue(() => held.get() * picture.get().lineAlpha);
  const cursorTop = useDerivedValue(() => vec(cursor.get() * width, 0));
  const cursorBottom = useDerivedValue(() => vec(cursor.get() * width, height));

  return (
    <Canvas style={{ width, height }}>
      <Group opacity={lineAlpha}>
        <Path path={area}>
          <LinearGradient start={vec(0, pad)} end={vec(0, height)} colors={AREA} positions={AREA_AT} />
        </Path>
        <Path path={line} style="stroke" strokeWidth={LINE} strokeJoin="round" strokeCap="round" color={LINE_GREEN} />
      </Group>
      <Path path={upWicks} color={UP} />
      <Path path={downWicks} color={DOWN} />
      <Path path={upBodies} color={UP} />
      <Path path={downBodies} color={DOWN} />
      <Line p1={cursorTop} p2={cursorBottom} color={CURSOR} strokeWidth={1} opacity={held}>
        <DashPathEffect intervals={DASH} />
      </Line>
      <Circle c={dot} r={DOT} color={LINE_GREEN} opacity={dotAlpha} />
    </Canvas>
  );
}

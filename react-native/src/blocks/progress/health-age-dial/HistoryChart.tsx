import {
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  LinearGradient,
  Line,
  Path,
  Skia,
  vec,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { clamp01 } from './motion';
import { colors, historyPoints, layout } from './theme';

const CH = layout.chart;
export const CHART_W = CH.baseRight - CH.left;
export const CHART_H = CH.bottom - CH.top + 4;
const GRID_W = CH.right - CH.left;
const BASE_Y = CH.base - CH.top;
const POINTS = historyPoints.map(([x, y]) => [x, BASE_Y + y] as const);
const LAST = POINTS[POINTS.length - 1];
const LOWEST = Math.max(...POINTS.map((p) => p[1]));

/** y of the history line at x (linear between traced points). */
function lineYAt(x: number): number {
  'worklet';
  if (x <= POINTS[0][0]) return POINTS[0][1];
  for (let i = 1; i < POINTS.length; i++) {
    const [x1, y1] = POINTS[i];
    if (x <= x1) {
      const [x0, y0] = POINTS[i - 1];
      return y0 + ((y1 - y0) * (x - x0)) / (x1 - x0);
    }
  }
  return LAST[1];
}

/**
 * Health Age History: grid, the actual-age line, the area between it and the history line,
 * and the line itself. `progress` (0–1) draws the line left to right with its dot riding the
 * tip; `opacity` fades the drawn part when the chart leaves the screen.
 */
export function HistoryChart({ progress, opacity }: { progress: SharedValue<number>; opacity: SharedValue<number> }) {
  const { line, area } = useMemo(() => {
    const l = Skia.Path.Make();
    POINTS.forEach(([x, y], i) => (i === 0 ? l.moveTo(x, y) : l.lineTo(x, y)));
    const a = l.copy();
    a.lineTo(LAST[0], BASE_Y);
    a.lineTo(0, BASE_Y);
    a.close();
    return { line: l, area: a };
  }, []);

  const headX = useDerivedValue(() => progress.get() * LAST[0]);
  // Nothing of the line or its area shows before the draw starts (round caps reach 1.5 pt back).
  const clip = useDerivedValue(() => {
    const p = progress.get();
    return Skia.XYWHRect(-4, 0, p <= 0 ? 0 : headX.get() + 4 + (p >= 1 ? 8 : 0), CHART_H);
  });
  const dotY = useDerivedValue(() => lineYAt(headX.get()));
  const dotOpacity = useDerivedValue(() => clamp01(progress.get() / 0.06) * opacity.get());

  const columns = Array.from({ length: CH.columns + 1 }, (_, i) => (GRID_W * i) / CH.columns);
  return (
    <Canvas style={{ width: CHART_W, height: CHART_H }} pointerEvents="none">
      {columns.map((x) => (
        <Line key={x} p1={vec(x + 0.5, 0)} p2={vec(x + 0.5, CH.bottom - CH.top)} color={colors.grid} strokeWidth={1}>
          <DashPathEffect intervals={[1.6, 3.4]} />
        </Line>
      ))}
      <Line p1={vec(0, 0.5)} p2={vec(GRID_W, 0.5)} color={colors.grid} strokeWidth={1}>
        <DashPathEffect intervals={[1.6, 3.4]} />
      </Line>
      <Line p1={vec(0, CH.bottom - CH.top)} p2={vec(GRID_W, CH.bottom - CH.top)} color={colors.grid} strokeWidth={1} />
      <Group clip={clip} opacity={opacity}>
        <Path path={area}>
          <LinearGradient start={vec(0, BASE_Y)} end={vec(0, LOWEST)} colors={[colors.fillTop, colors.fillBottom]} />
        </Path>
        <Path path={line} style="stroke" strokeWidth={CH.lineWidth} strokeJoin="round" strokeCap="round">
          <LinearGradient start={vec(0, 0)} end={vec(LAST[0], 0)} colors={[colors.line, colors.lineEnd]} />
        </Path>
      </Group>
      <Line p1={vec(0, BASE_Y)} p2={vec(CHART_W, BASE_Y)} color={colors.baseline} strokeWidth={1.3} />
      <Circle cx={headX} cy={dotY} r={CH.dotR + CH.dotRing} color={colors.white} opacity={dotOpacity} />
      <Circle cx={headX} cy={dotY} r={CH.dotR} color={colors.chartDot} opacity={dotOpacity} />
    </Canvas>
  );
}

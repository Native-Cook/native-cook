import { Canvas, DashPathEffect, Group, Path, Skia } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { useMemo } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { clamp01, FLOW_PER_TURN, FLOW_PERIOD } from "./fanMotion";
import { GREEN } from "./fanScene";

/** Height of each current as a fraction of the area, its wave's size (pt) and phase, and how strong it is. */
const LINES = [
  { y: 0.2, amp: 11, phase: 0.4, weight: 0.62 },
  { y: 0.34, amp: 15, phase: 1.9, weight: 1 },
  { y: 0.47, amp: 12, phase: 3.1, weight: 0.8 },
  { y: 0.6, amp: 16, phase: 4.4, weight: 0.95 },
  { y: 0.73, amp: 10, phase: 5.5, weight: 0.6 },
  { y: 0.88, amp: 13, phase: 0.9, weight: 0.72 },
  { y: 1.02, amp: 9, phase: 2.5, weight: 0.5 },
] as const;
const DASH = 26;
const STEPS = 28;
const STROKE = 1.5;
const FULL_SPEED = 3.2;
const LINE_ALPHA = 0.34;

function wave(width: number, y: number, amp: number, phase: number) {
  const b = Skia.PathBuilder.Make();
  for (let i = 0; i <= STEPS; i++) {
    const k = i / STEPS;
    const px = -20 + (width + 40) * k;
    const py = y + amp * Math.sin(k * Math.PI * 2.2 + phase) + amp * 0.35 * Math.sin(k * Math.PI * 5.1 + phase * 1.7);
    if (i === 0) b.moveTo(px, py);
    else b.lineTo(px, py);
  }
  return b.build();
}

type FlowLineProps = {
  width: number;
  height: number;
  line: (typeof LINES)[number];
  turns: SharedValue<number>;
};

function FlowLine({ width, height, line, turns }: FlowLineProps): JSX.Element {
  const path = useMemo(() => wave(width, height * line.y, line.amp, line.phase), [height, line, width]);
  const phase = useDerivedValue(() => -(((turns.get() * FLOW_PER_TURN) % 1) * FLOW_PERIOD));
  return (
    <Path color={GREEN} path={path} strokeCap="round" strokeWidth={STROKE} style="stroke">
      <DashPathEffect intervals={[DASH * line.weight + 8, FLOW_PERIOD - (DASH * line.weight + 8)]} phase={phase} />
    </Path>
  );
}

export type FanAirflowProps = {
  width: number;
  height: number;
  turns: SharedValue<number>;
  speed: SharedValue<number>;
};

/** The air: thin currents drifting left to right across the screen, as strong as the blades are fast. */
export function FanAirflow({ width, height, turns, speed }: FanAirflowProps): JSX.Element {
  const opacity = useDerivedValue(() => clamp01(speed.get() / FULL_SPEED) * LINE_ALPHA);
  return (
    <Canvas pointerEvents="none" style={[styles.canvas, { width, height }]}>
      <Group opacity={opacity}>
        {LINES.map((l) => (
          <FlowLine height={height} key={l.y} line={l} turns={turns} width={width} />
        ))}
      </Group>
    </Canvas>
  );
}

const styles = StyleSheet.create({
  canvas: { position: "absolute", left: 0, top: 0 },
});

import React, { memo, useMemo } from 'react';
import { Canvas, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { LAMPS_SKSL } from './fx-shaders';
import { M, clamp, flyBack, flyIn, panel, smooth } from './motion';
import { gantry as G, stars as ST } from './theme';

/**
 * Full-screen overlay for the moment the five start lights become the composer's five stars (and back). A light
 * leaves the gantry, arcs down with a short comet trail and morphs (signed-distance blend in LAMPS_SKSL) from a red
 * lamp into the white outline star that lands in its slot; on Close the stars rise back into red lamps.
 * Window coordinates: `top` is the hero box's window y, `slots` the star centres (x, y) when the composer is open.
 * Per frame only plain number arrays are built; no Skia object is created.
 */
const LAMPS = Skia.RuntimeEffect.Make(LAMPS_SKSL);
const TRAIL = 3;
const TRAIL_STEP = 0.07;
const WARM = { lamp: new Array(80).fill(0), look: [0.01, 1, 2, 0, ...new Array(76).fill(0)] };
type Props = { width: number; height: number; scale: number; top: number; slots: SharedValue<number[]>; scene: SharedValue<number> };

/** Point on the arc from the gantry light to the star slot, u 0…1. */
export function arc(gx: number, gy: number, px: number, py: number, i: number, u: number, s: number): [number, number] {
  'worklet';
  const cx = (gx + px) / 2 + (i - 2) * 16 * s, cy = gy + (py - gy) * 0.12 - 40 * s;
  const a = 1 - u;
  return [a * a * gx + 2 * a * u * cx + u * u * px, a * a * gy + 2 * a * u * cy + u * u * py];
}

export const PitStopReviewLights = memo(function PitStopReviewLights({ width, height, scale, top, slots, scene }: Props) {
  const uniforms = useDerivedValue(() => {
    const s = scene.get();
    const sl = slots.get();
    const lamp: number[] = [];
    const look: number[] = [];
    const lift = (1 - panel(s)) * M.panelRise * scale;
    for (let i = 0; i < 5; i++) {
      const fin = flyIn(s, i), back = flyBack(s, i);
      const going = fin > 0 && fin < 1, returning = back > 0 && back < 1;
      const u = going ? fin : 1 - back;
      const gx = (G.cx + (i - 2) * G.pitch) * scale, gy = top + G.cy * scale;
      const px = sl[i * 2] ?? gx, py = (sl[i * 2 + 1] ?? gy) + lift;
      const m = smooth((u - 0.2) / 0.6);
      const r = (G.radius + (ST.radius - G.radius) * m) * scale;
      const live = (going || returning) && sl.length >= 10;
      for (let k = 0; k <= TRAIL; k++) {
        const uk = clamp(going ? u - k * TRAIL_STEP : u + k * TRAIL_STEP);
        const [x, y] = arc(gx, gy, px, py, i, uk, scale);
        lamp.push(x, y, r * (1 - k * 0.18), m);
        // Head: full; trail: fainter, with no stroke so it reads as light, not as extra stars.
        look.push(live ? (k === 0 ? 1 : (0.32 - k * 0.08) * (1 - Math.abs(m - 0.5))) : 0, k === 0 ? 1 : 0.6, k === 0 ? 2 * scale : 0, 0);
      }
    }
    return { lamp, look };
  });
  const boxY = useMemo(() => top + (G.cy - 3.2 * G.radius) * scale, [top, scale]);
  /** The shader only runs while a light is in flight; otherwise the rect is empty. */
  const boxH = useDerivedValue(() => {
    const s = scene.get();
    for (let i = 0; i < 5; i++) { const a = flyIn(s, i), b = flyBack(s, i); if ((a > 0 && a < 1) || (b > 0 && b < 1)) return height - boxY; }
    return 0;
  });
  if (!LAMPS) return null;
  return (
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
      {/* Warm-up: compiles the lamp shader on mount (1 pt, transparent sprite) instead of on the first flight. */}
      <Rect x={0} y={0} width={1} height={1}><Shader source={LAMPS} uniforms={WARM} /></Rect>
      <Rect x={0} y={boxY} width={width} height={boxH}>
        <Shader source={LAMPS} uniforms={uniforms} />
      </Rect>
    </Canvas>
  );
});

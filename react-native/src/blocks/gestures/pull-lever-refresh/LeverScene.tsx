/**
 * The espresso machine as one Skia scene in its studio: the sweep and the machine's shadows, the body with its
 * gauge face and timer window, the drip tray, the group head and portafilter, the cup with the shot, the streams,
 * the steam, and the lever on top. Everything that moves is a transform or a uniform, so a frame is a handful of
 * shader draws and no JS. The props may be plain values or derived values.
 */
import { Group, Rect, Shader, type Transforms3d, type Uniforms } from '@shopify/react-native-skia';
import type { DerivedValue } from 'react-native-reanimated';

import { SHADERS } from './lever-shaders';
import { GEO } from './theme';

type Live<T> = T | DerivedValue<T>;

export type LeverSceneProps = {
  width: number;
  height: number;
  /** The lever's pivot on screen, the machine's scale, and the screen's pixels per point. */
  px: number;
  py: number;
  scale: number;
  pixelRatio: number;
  turn: Live<Transforms3d>;
  lever: Live<Uniforms>;
  body: Live<Uniforms>;
  tray: Live<Uniforms>;
  cup: Live<Uniforms>;
  cupAt: Live<Transforms3d>;
  cupOpacity: Live<number>;
  streams: Live<Uniforms>;
  steam: Live<Uniforms>;
};

const B = GEO.body;
const LV = GEO.lever;
const CAST_RECT = { x: B.left - 30, y: B.top - 50, width: GEO.tray.right - B.left + 150, height: GEO.floor - B.top + 90 };
const BODY_RECT = { x: B.left - 2, y: B.top - 40, width: B.right - B.left + 4, height: GEO.floor - B.top + 44 };
const TRAY_RECT = { x: GEO.tray.left - 2, y: GEO.tray.top - 2, width: GEO.tray.right - GEO.tray.left + 4, height: GEO.floor - GEO.tray.top + 4 };
const GROUP_RECT = { x: GEO.neck.from - 2, y: GEO.yoke.top - 2, width: GEO.pf.wood.to - GEO.neck.from + 8, height: GEO.pf.spout.tip - GEO.yoke.top + 6 };
const CUP_RECT = { x: -62, y: GEO.cup.rim - 16, width: 150, height: GEO.cup.base - GEO.cup.rim + 30 };
const STREAM_RECT = { x: -22, y: GEO.pf.spout.tip - 2, width: 44, height: GEO.cup.rim - GEO.pf.spout.tip + 18 };
const STEAM_RECT = { x: -70, y: -40, width: 160, height: GEO.cup.rim + 38 };
const LEVER_RECT = { x: -LV.boss - 2, y: -LV.handle.r1 - LV.boss, width: LV.handle.to + LV.boss + 6, height: 2 * (LV.handle.r1 + LV.boss) };
const LEVER_SHADOW_RECT = { x: -60, y: -70, width: LV.handle.to + 120, height: 140 };

export function LeverScene(p: LeverSceneProps) {
  const aa = 1 / (p.scale * p.pixelRatio);
  return (
    <Group>
      <Rect x={0} y={0} width={p.width} height={p.height}>
        <Shader source={SHADERS.panel} uniforms={{ uSize: [p.width, p.height], uPivot: [p.px, p.py], uK: p.scale, uPR: p.pixelRatio }} />
      </Rect>

      <Group transform={[{ translateX: p.px }, { translateY: p.py }, { scale: p.scale }]}>
        <Rect {...CAST_RECT}>
          <Shader source={SHADERS.cast} uniforms={{}} />
        </Rect>
        <Rect {...TRAY_RECT}>
          <Shader source={SHADERS.tray} uniforms={p.tray} />
        </Rect>
        <Rect {...BODY_RECT}>
          <Shader source={SHADERS.body} uniforms={p.body} />
        </Rect>
        <Rect {...GROUP_RECT}>
          <Shader source={SHADERS.group} uniforms={{ uAA: aa }} />
        </Rect>

        <Group transform={p.cupAt} opacity={p.cupOpacity}>
          <Rect {...CUP_RECT}>
            <Shader source={SHADERS.cup} uniforms={p.cup} />
          </Rect>
        </Group>
        <Rect {...STREAM_RECT}>
          <Shader source={SHADERS.streams} uniforms={p.streams} />
        </Rect>
        <Group transform={p.cupAt}>
          <Rect {...STEAM_RECT}>
            <Shader source={SHADERS.steam} uniforms={p.steam} />
          </Rect>
        </Group>

        <Group transform={p.turn}>
          <Rect {...LEVER_SHADOW_RECT}>
            <Shader source={SHADERS.leverShadow} uniforms={p.lever} />
          </Rect>
          <Rect {...LEVER_RECT}>
            <Shader source={SHADERS.lever} uniforms={p.lever} />
          </Rect>
        </Group>
      </Group>
    </Group>
  );
}

/** Over the gauge's numerals: the needle, its shadow, the cap and the glass, in a box the bezel's size (units). */
export function GaugeGlass({ size, scale, needle }: { size: number; scale: number; needle: Live<Uniforms> }) {
  return (
    <Group transform={[{ scale }, { translateX: size / 2 }, { translateY: size / 2 }]}>
      <Rect x={-GEO.gauge.dial - 1} y={-GEO.gauge.dial - 1} width={2 * GEO.gauge.dial + 2} height={2 * GEO.gauge.dial + 2}>
        <Shader source={SHADERS.gaugeGlass} uniforms={needle} />
      </Rect>
    </Group>
  );
}

/** Over the timer's digits: the drums' curve, the shadow of the window's lip and the glass. */
export function TimerGlass({ width, height }: { width: number; height: number }) {
  return (
    <Rect x={0} y={0} width={width} height={height}>
      <Shader source={SHADERS.timerGlass} uniforms={{ uSize: [width, height], uR: GEO.timer.radius }} />
    </Rect>
  );
}

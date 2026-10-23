// One retained crystal draw, with reusable UI-runtime geometry and an adaptive optical budget.
import { Rect, Shader, Skia } from '@shopify/react-native-skia';
import { PixelRatio } from 'react-native';
import { useAnimatedReaction, useDerivedValue, useSharedValue } from 'react-native-reanimated';

import { CAMERA, REST, type GemLayerProps } from './gem-contract';
import { GEM_PLANES, gemOutline, makeOutlineScratch } from './gem-cut';
import { GEM_SKSL } from './gem-shader';

const effect = Skia.RuntimeEffect.Make(GEM_SKSL);
if (!effect) throw new Error('Gem Orb Collect: crystal shader did not compile.');
const pixelRatio = PixelRatio.get();

function initialGeometry() {
  const cy = Math.cos(REST.yaw), sy = Math.sin(REST.yaw), cp = Math.cos(REST.pitch), sp = Math.sin(REST.pitch);
  const cr = Math.cos(REST.roll), sr = Math.sin(REST.roll);
  const row0 = [cy * cr, -cy * sr, sy];
  const row1 = [cp * sr + sp * sy * cr, cp * cr - sp * sy * sr, -sp * cy];
  const row2 = [sp * sr - cp * sy * cr, sp * cr + cp * sy * sr, cp * cy];
  const scratch = makeOutlineScratch();
  gemOutline(row0, row1, row2, CAMERA, scratch);
  return { scratch, row0, row1, row2,
    yaw: REST.yaw as number, pitch: REST.pitch as number, roll: REST.roll as number, time: -1, speed: 0 };
}

export function GemLayer({ centreX, centreY, radius, yaw, pitch, roll, stretch,
  charge, flare, lights, time, opacity }: GemLayerProps): React.JSX.Element {
  const geometry = useSharedValue(initialGeometry);
  // A named modifier avoids callback lifting inside a worklet. Arrays belong to the UI runtime;
  // .modify publishes their mutation without allocating projection/visibility/outline buffers.
  function updateGeometry(state: ReturnType<typeof initialGeometry>) {
    'worklet';
    const y = yaw.get(), p = pitch.get(), r = roll.get(), now = time.get();
    const dt = now - state.time;
    if (state.time >= 0 && dt > 0 && dt < 0.15) {
      const velocity = Math.hypot(y - state.yaw, p - state.pitch, r - state.roll) / dt;
      state.speed += (velocity - state.speed) * (1 - Math.exp(-dt * 12));
    } else if (dt < 0) state.speed = 0;
    // Gesture writes can precede the next simulation-clock tick; retain that motion for its dt.
    if (dt !== 0 || state.time < 0) {
      state.yaw = y; state.pitch = p; state.roll = r; state.time = now;
    }
    const cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p);
    const cr = Math.cos(r), sr = Math.sin(r);
    const a = state.row0, b = state.row1, c = state.row2;
    a[0] = cy * cr; a[1] = -cy * sr; a[2] = sy;
    b[0] = cp * sr + sp * sy * cr; b[1] = cp * cr - sp * sy * sr; b[2] = -sp * cy;
    c[0] = sp * sr - cp * sy * cr; c[1] = sp * cr + cp * sy * sr; c[2] = cp * cy;
    gemOutline(a, b, c, CAMERA, state.scratch);
    return state;
  }
  useAnimatedReaction(
    () => [yaw.get(), pitch.get(), roll.get(), time.get()],
    () => { geometry.modify(updateGeometry); },
  );
  const uniforms = useDerivedValue(() => {
    'worklet';
    const state = geometry.get();
    const lamps = lights.get();
    let outside = 0, inside = 0;
    for (let i = 3; i < lamps.length; i += 4) {
      if (lamps[i] > 0) outside++;
      if (lamps[i] < 0) inside++;
    }
    const flash = Math.max(0, Math.min(1, flare.get()));
    const extra = Math.max(flash, Math.max(0, Math.min(1, (state.speed - 0.8) / 3)));
    return {
      uCentre: [centreX.get(), centreY.get()], uRadius: Math.max(radius.get(), 0.01),
      uPixelRatio: pixelRatio, uCamera: CAMERA,
      uRow0: state.row0, uRow1: state.row1, uRow2: state.row2, uOutline: state.scratch.result,
      uStretch: Math.max(0.5, Math.min(1.5, stretch.get())),
      uCharge: Math.max(0, Math.min(1, charge.get())), uFlare: flash,
      // Quiet facets retain two optical returns; motion and the burst restore all five.
      uBounces: radius.get() <= 18 ? 2 : 2 + 3 * extra,
      uOutsideLights: outside, uInsideLights: inside, uLights: lamps,
      uTime: time.get(), uOpacity: Math.max(0, Math.min(1, opacity.get())), uPlanes: GEM_PLANES,
    };
  });
  const bounds = useDerivedValue(() => {
    'worklet';
    const extent = Math.max(radius.get(), 0.01) * 2.25;
    return { x: centreX.get() - extent, y: centreY.get() - extent, width: extent * 2, height: extent * 2 };
  });
  return <Rect rect={bounds}><Shader source={effect!} uniforms={uniforms} /></Rect>;
}

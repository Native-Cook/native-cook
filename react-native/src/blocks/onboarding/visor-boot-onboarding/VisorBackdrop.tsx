import { Canvas, Rect, Shader, Skia } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { blendChannel, cameraAt, cameraBasis, clamp01, filmSweep, project } from "./visorMotion";
import { HFOV_DEG, PLINTH_R } from "./visorScene";

/**
 * The studio behind the see-through Filament view, as one shader pass: a blue-grey sweep lit from above the
 * helmet, a soft pool of light on the floor round the plinth, the plinth's contact shadow on that floor (projected
 * with the stage's camera, so it stays under the plinth as the camera moves), grain and a vignette. Output is
 * converted from sRGB to Display P3 (Skia reads runtime-effect output as P3 on iOS).
 */
const STUDIO = Skia.RuntimeEffect.Make(`
uniform float2 res;
uniform float2 c;      // plinth base centre on screen
uniform float2 rad;    // plinth base half-axes on screen
uniform float sweep;   // the Get started light sweep, 0..1

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float3 toP3(float3 col) {
  float3 l = pow(clamp(col, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

half4 main(float2 p) {
  float2 uv = p / res;
  // the sweep: lit from above and behind the helmet, falling off to a deep edge
  float2 d = (p - float2(res.x * 0.5, res.y * 0.3)) / res.y;
  float r = length(d * float2(1.25, 1.0));
  float3 centre = float3(0.157, 0.188, 0.227);
  float3 mid = float3(0.086, 0.106, 0.129);
  float3 edge = float3(0.035, 0.043, 0.055);
  float3 col = mix(centre, mid, smoothstep(0.0, 0.42, r));
  col = mix(col, edge, smoothstep(0.38, 0.95, r));
  // a warm lift low in the frame where the floor meets the light (barely there)
  col += float3(0.012, 0.009, 0.006) * smoothstep(0.75, 0.35, abs(uv.y - 0.62) * 2.0);
  // floor pool round the plinth, brighter while the sweep passes
  float2 e = (p - c) / max(rad * 2.6, float2(1.0));
  float pool = exp(-dot(e, e) * 1.6);
  col += float3(0.05, 0.056, 0.064) * pool * (1.0 + 0.8 * sweep);
  // contact shadow on the floor: dark right at the base, a soft skirt round it
  float2 s = (p - c) / max(rad, float2(1.0));
  float sr = length(s);
  float shadow = 0.82 * (1.0 - smoothstep(0.85, 1.45, sr)) + 0.25 * exp(-sr * sr * 0.45);
  col *= 1.0 - clamp(shadow, 0.0, 0.9);
  // vignette and grain
  float v = length((uv - 0.5) * float2(1.0, 0.85));
  col *= 1.0 - 0.38 * smoothstep(0.35, 0.85, v);
  col += (hash(p) - 0.5) * 0.018;
  return half4(half3(toP3(col)), 1.0);
}
`)!;

export type VisorBackdropProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  page: SharedValue<number>;
  m: SharedValue<number>;
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
};

export function VisorBackdrop(p: VisorBackdropProps): JSX.Element {
  const { width: w, height: h } = p;
  const uniforms = useDerivedValue(() => {
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(p.t.get(), p.page.get(), p.m.get(), p.yaw.get(), p.pitch.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const o = [0, 0, 0];
    const ok = project(c, b, w, h, [0, 0, 0], o);
    // the base circle seen from the camera: half-width R·f/z, half-height scaled by how steeply we look down
    const rx = ok ? (PLINTH_R * b[8]) / o[2] : 0;
    const dist = Math.hypot(c[0], c[1], c[2]) || 1;
    const ry = rx * Math.max(0.08, Math.abs(c[1]) / dist);
    const sw = [0, 0];
    filmSweep(p.t.get(), sw);
    return {
      res: [w, h],
      c: [o[0], o[1]],
      rad: [rx, ry],
      sweep: clamp01(blendChannel(sw[0], 0, p.m.get())),
    };
  });
  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Rect height={h} width={w} x={0} y={0}>
        <Shader source={STUDIO} uniforms={uniforms} />
      </Rect>
    </Canvas>
  );
}

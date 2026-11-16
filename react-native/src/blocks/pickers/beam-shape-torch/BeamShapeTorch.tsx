// Beam Shape Torch — the demo screen of Beam Control: the pin's magenta wallpaper, and the capsule hanging from the
// Dynamic Island with its torch. The component is `BeamControl` (BeamControl.tsx); this file is only the page behind it.
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { PixelRatio, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Image, LinearGradient, Rect, Shader, Skia, useImage, vec } from '@shopify/react-native-skia';

import { BeamControl } from './BeamControl';
import { LOOP } from './bst-motion';
import { ref } from './bst-theme';

export { LOOP };

const WALL_W = 1206;
const WALL_H = 2622;
const C = ref.color;

// Film grain over the wallpaper, one speck per device pixel, ±3.5 % of white or black. The wallpaper is a smooth generated image
// with soft folds: the grain breaks its banding and the JPEG's weave, and gives the surface a material, photographic grain.
const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uPx;
float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
half4 main(float2 xy) {
  float g = hash(floor(xy * uPx)) - 0.5;
  float a = abs(g) * 0.07;
  float lum = g > 0.0 ? 1.0 : 0.0;
  return half4(half3(lum * a), half(a));
}
`);

export function BeamShapeTorch({ autoPlay = false }: { autoPlay?: boolean }) {
  const { width: W, height: H } = useWindowDimensions();
  const wallpaper = useImage(require('./assets/wallpaper-pro.jpg'));

  // the wallpaper covers the screen, anchored to the top (a shorter phone loses the bottom of the dome)
  const wall = useMemo(() => {
    const k = Math.max(W / WALL_W, H / WALL_H);
    const w = WALL_W * k;
    return { x: (W - w) / 2, y: 0, w, h: WALL_H * k };
  }, [W, H]);

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          {/* the wallpaper's own colours stand in while the image decodes, so nothing shifts */}
          <Rect x={0} y={0} width={W} height={H}>
            <LinearGradient start={vec(0, 0)} end={vec(0, H)} colors={[C.wallTop, C.wallMid, C.wallLow]} positions={[0, 0.45, 1]} />
          </Rect>
          {wallpaper ? <Image image={wallpaper} x={wall.x} y={wall.y} width={wall.w} height={wall.h} fit="fill" /> : null}
          {GRAIN ? (
            <Rect x={0} y={0} width={W} height={H}>
              <Shader source={GRAIN} uniforms={{ uPx: PixelRatio.get() }} />
            </Rect>
          ) : null}
        </Canvas>
      </View>
      <BeamControl autoPlay={autoPlay} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: ref.color.wallTop },
});

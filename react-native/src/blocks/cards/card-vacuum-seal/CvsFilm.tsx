// Card Vacuum Seal — the film's canvas: one Skia canvas, one runtime effect (cvs-shaders.ts). Its children are the
// snapshot of the wrapped view (the card) and the baked crease slope map. Nothing here runs per frame except the
// uniform packing (cvs-uniforms.ts), on the UI thread, and only when the film's state changes.
import { Canvas, FilterMode, ImageShader, MipmapMode, Rect, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { useDerivedValue } from 'react-native-reanimated';

import type { VacuumSealHandle } from './cvs-controller';
import { FILM_SKSL } from './cvs-shaders';
import { type FilmGeom, filmUniforms } from './cvs-uniforms';

const filmFx = Skia.RuntimeEffect.Make(FILM_SKSL);
export const FILM_AVAILABLE = filmFx !== null;
if (!filmFx && __DEV__) console.warn('card-vacuum-seal: the film shader did not compile (see stderr)');

type Props = {
  geom: FilmGeom;
  /** the canvas, in pt: the card plus the room the sheet falls through and the bubble leaves by */
  width: number;
  height: number;
  map: SkImage;
  /** the baked crease slope map (assets/cvs-creases.png), or null while it is not decoded: the shader then uses its ridged-noise fallback */
  crease: SkImage | null;
  film: VacuumSealHandle['film'];
  azimuth: number; // rad, the key light
  tint: readonly [number, number, number];
};

export function CvsFilm({ geom, width, height, map, crease, film, azimuth, tint }: Props) {
  const creaseOn = crease ? 1 : 0;
  const { look, ambient, dimple, dimpleK, finger, fingerK, stretch, fade, seed, amp, extra, tilt } = film;
  const uniforms = useDerivedValue(() => {
    const d = dimple.get();
    const f = finger.get();
    return filmUniforms(geom, {
      look: look.get(),
      time: ambient.get(),
      azimuth,
      tilt: tilt.get(),
      dimpleX: d.x + geom.cx - geom.hw,
      dimpleY: d.y + geom.cy - geom.hh,
      dimpleK: dimpleK.get(),
      fingerX: f.x + geom.cx - geom.hw,
      fingerY: f.y + geom.cy - geom.hh,
      fingerK: fingerK.get(),
      stretch: stretch.get(),
      fade: fade.get(),
      seed: seed.get(),
      amp: amp.get(),
      extra: extra.get(),
      tint,
      creaseOn,
    });
  });
  const style = useMemo(() => ({ width, height }), [width, height]);
  if (!filmFx) return null;
  return (
    <View pointerEvents="none" style={[styles.wrap, style]}>
      <Canvas style={StyleSheet.absoluteFill}>
        <Rect x={0} y={0} width={width} height={height}>
          <Shader source={filmFx} uniforms={uniforms}>
            <ImageShader image={map} fit="none" tx="decal" ty="decal" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
            <ImageShader
              image={crease ?? map}
              fit="none"
              tx="clamp"
              ty="clamp"
              sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
            />
          </Shader>
        </Rect>
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: 0, top: 0 },
});

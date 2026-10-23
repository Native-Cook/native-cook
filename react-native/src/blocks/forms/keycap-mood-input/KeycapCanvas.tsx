import { Blur, Canvas, Fill, FilterMode, MipmapMode, Group, ImageShader, Path, Shader, Skia, useImage, type SkImage } from '@shopify/react-native-skia';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PixelRatio } from 'react-native';
import { useAnimatedReaction, useDerivedValue, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { LEGENDS } from './keycap-glyphs';
import { LOOK, capUniforms, clamp01, legendColor, legendRects, legendTransform, sceneUniforms, srgb, staticDots, type SceneValues } from './keycap-scene';
import { GLASS_SKSL, OPAQUE_SKSL, SCENE_SKSL } from './keycap-shader';
import { type KeycapLayout } from './keycap-theme';
import { type KeycapMotion } from './use-keycap-motion';

const opaqueEffect = Skia.RuntimeEffect.Make(OPAQUE_SKSL);
const glassEffect = Skia.RuntimeEffect.Make(GLASS_SKSL);
const sceneEffect = Skia.RuntimeEffect.Make(SCENE_SKSL);
if (!opaqueEffect || !glassEffect || !sceneEffect) throw new Error('Keycap Mood Input: shader compilation failed.');

function dotPath(rects: number[][]) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  for (let i = 0; i < rects.length; i++) {
    const r = rects[i]; builder.addRect(Skia.XYWHRect(r[0], r[1], r[2], r[3]));
  }
  return builder.build();
}
function CapShader({ layout, index, mini = -1, reduced, ratio, values, data, picture }: {
  layout: KeycapLayout; index: number; mini?: number; reduced: boolean; ratio: number;
  values: SharedValue<SceneValues>; data: SkImage; picture?: SkImage;
}) {
  const uniforms = useDerivedValue(() => capUniforms(layout, index, values.get(), mini, reduced, ratio));
  return <Shader source={index === 1 ? glassEffect! : opaqueEffect!} uniforms={uniforms}>
    <ImageShader image={data} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
    {picture && <ImageShader image={picture} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />}
  </Shader>;
}

function Legend({ layout, index, ratio, motion }: { layout: KeycapLayout; index: number; ratio: number; motion: KeycapMotion }) {
  const legend = motion.legends[index];
  const { travel, mood, rim, scene, dim } = motion;
  const u = layout.u;
  const cached = useMemo(() => LEGENDS[index].map((_dots, choice) => dotPath(staticDots(index, choice, u, ratio))), [index, ratio, u]);
  const path = useDerivedValue(() => {
    const l = legend.get();
    if (l.t < 0 || l.mode === 2) return cached[l.to];
    const m = mood.get();
    return dotPath(legendRects(index, l, u, ratio, m.ox, m.oy));
  });
  const fromPath = useDerivedValue(() => cached[legend.get().from]);
  const fadeFrom = useDerivedValue(() => { const l = legend.get(); return l.t >= 0 && l.mode === 2 ? 1 - clamp01(l.t / LOOK.legend.fadeMs) : 0; });
  const fadeTo = useDerivedValue(() => 1 - fadeFrom.get());
  const transform = useDerivedValue(() => legendTransform(layout, index, travel.get()[index], ratio));
  const opacity = useDerivedValue(() => index === 3 ? 1 : dim.get());
  const color = useDerivedValue(() => { const c = legendColor(index, rim.get()[index], scene.get()); return Float32Array.of(c[0], c[1], c[2], 1); });
  const glowColor = useDerivedValue(() => { const c = scene.get(); return Float32Array.of(srgb(c[0]), srgb(c[1]), srgb(c[2]), 1); });
  const glowAlpha = useDerivedValue(() => LOOK.legend.backlightAlpha * rim.get()[index]);
  const layers = [{ path: fromPath, opacity: fadeFrom }, { path, opacity: fadeTo }];
  return <Group transform={transform} opacity={opacity}>
    {layers.map((item, i) => <Group key={i} opacity={item.opacity}>
      <Path path={item.path} color={glowColor} opacity={glowAlpha} blendMode="plus"><Blur blur={LOOK.legend.backlightBlur * u} /></Path>
      {index === 1
        ? <Path path={item.path} color={LOOK.legend.shadow} opacity={LOOK.legend.shadowAlpha}
            transform={[{ translateX: LOOK.legend.shadowX * u }, { translateY: LOOK.legend.shadowY * u }]}><Blur blur={LOOK.legend.shadowBlur * u} /></Path>
        : <Path path={item.path} color="white" opacity={LOOK.legend.debossAlpha}
            transform={[{ translateX: LOOK.legend.debossX * u }, { translateY: LOOK.legend.debossY * u }]} />}
      <Path path={item.path} color={color} />
    </Group>)}
  </Group>;
}

export function KeycapCanvas({ layout, motion, reduced, reveal, onReady }: {
  layout: KeycapLayout; motion: KeycapMotion; reduced: boolean; reveal: SharedValue<number>; onReady(ready: boolean): void;
}) {
  const opaque = useImage(require('./assets/keycap-opaque.png'));
  const glass = useImage(require('./assets/keycap-glass.png'));
  const glassLight = useImage(require('./assets/keycap-glass-light.png'));
  const mini = useImage(require('./assets/keycap-mini.png'));
  const well = useImage(require('./assets/well-glow.png'));
  const canvasSize = useSharedValue({ width: 0, height: 0 });
  const [laidOut, setLaidOut] = useState(false);
  const reportSize = useCallback((next: boolean) => setLaidOut(next), []);
  useAnimatedReaction(() => canvasSize.get().width > 0 && canvasSize.get().height > 0, (next, previous) => {
    if (next !== previous) scheduleOnRN(reportSize, next);
  });
  const loaded = !!(opaque && glass && glassLight && mini && well);
  useEffect(() => { onReady(laidOut && loaded); }, [laidOut, loaded, onReady]);
  const { travel, rim, dim, mood, scene, charge, burst, ring, fan, miniTravel, selected } = motion;
  // Only shared handles enter this closure: never capture the gesture-bearing hook result.
  const values = useDerivedValue<SceneValues>(() => ({ travel: travel.get(), rim: rim.get(), dim: dim.get(), mood: mood.get(), scene: scene.get(),
    charge: charge.get(), burst: burst.get(), ring: ring.get(), fan: fan.get(), miniTravel: miniTravel.get(), selected: selected.get() }));
  const uniforms = useDerivedValue(() => sceneUniforms(layout, values.get(), reduced));
  const ratio = PixelRatio.get();
  return <Canvas style={{ width: layout.width, height: layout.height }} onSize={canvasSize} pointerEvents="none" accessible={false}>
    <Fill color="#000" />
    {opaque && glass && glassLight && mini && well && <Group opacity={reveal}>
      <Fill><Shader source={sceneEffect!} uniforms={uniforms}>
        <ImageShader image={well} fit="none" sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
        {[0, 1, 2, 3].map(index => <CapShader key={index} layout={layout} index={index} reduced={reduced} ratio={ratio} values={values}
          data={index === 1 ? glassLight : opaque} picture={index === 1 ? glass : undefined} />)}
        {[0, 1, 2, 3, 4, 5].map(index => <CapShader key={`mini-${index}`} layout={layout} index={3} mini={index} reduced={reduced} ratio={ratio} values={values} data={mini} />)}
      </Shader></Fill>
      {[0, 1, 2, 3].map(index => <Legend key={index} layout={layout} index={index} ratio={ratio} motion={motion} />)}
    </Group>}
  </Canvas>;
}

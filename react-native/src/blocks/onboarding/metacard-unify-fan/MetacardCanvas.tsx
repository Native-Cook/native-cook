import { Canvas, Fill, Group, Paint, Rect, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { useDerivedValue } from 'react-native-reanimated';
import { D, designFrame } from './metacard-layout';
import { COLOR_RGB } from './metacard-theme';
import { BLOOM_SKSL, DARKNESS_SKSL, PAGE_SKSL, HALO_SKSL, BOUNCE_SKSL } from './metacard-shaders';
import { effectsState, heroUniforms, bounceUniforms } from './metacard-timeline';
import { MetacardFanCard } from './MetacardFanCard';
import { MetacardHero } from './MetacardHero';
import { MetacardText, type makeParagraphs } from './MetacardText';
import type { MetacardValues } from './use-metacard-motion';

const page = Skia.RuntimeEffect.Make(PAGE_SKSL), bloom = Skia.RuntimeEffect.Make(BLOOM_SKSL);
const darkness = Skia.RuntimeEffect.Make(DARKNESS_SKSL), halo = Skia.RuntimeEffect.Make(HALO_SKSL);
const bounce = Skia.RuntimeEffect.Make(BOUNCE_SKSL);
if (!page || !bloom || !darkness || !halo || !bounce) throw new Error('Metacard scene shader failed to compile.');
export function MetacardCanvas({ width, height, values, faces, chip, shadow, paragraphs, reduced }: {
  width: number; height: number; values: MetacardValues; faces: SkImage[]; chip: SkImage; shadow: SkImage;
  paragraphs: ReturnType<typeof makeParagraphs>; reduced: boolean;
}) {
  const frame = designFrame(width, height), { u, forward, clock, spin, anchorSpin } = values;
  const effects = useDerivedValue(() => effectsState(u.get(), forward.get(), clock.get(), reduced));
  const pageUniforms = useDerivedValue(() => ({ pageMix: reduced ? u.get() : effects.get().pageMix, frame: [frame.x, frame.y, frame.k] }));
  const bloomUniforms = useDerivedValue(() => {
    const h = heroUniforms(u.get(), spin.get(), clock.get(), forward.get());
    return { amount: effects.get().bloom, reach: effects.get().bloomReach, centre: h.centre,
      halfSize: h.halfSize, corner: h.corner, angle: spin.get() * Math.PI / 180, colors: COLOR_RGB };
  });
  const bounceLight = useDerivedValue(() => bounceUniforms(u.get(), spin.get(), forward.get(), anchorSpin.get(), reduced));
  const darkUniforms = useDerivedValue(() => ({ amount: reduced ? 1 : effects.get().darkness }));
  const haloUniforms = useDerivedValue(() => ({ halo: effects.get().halo }));
  const fanAlpha = useDerivedValue(() => reduced ? 1 - u.get() : 1);
  const heroAlpha = useDerivedValue(() => reduced ? u.get() : 1);
  return <Canvas style={{ width, height }} pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Fill><Shader source={page!} uniforms={pageUniforms} /></Fill>
    <Group transform={[{ translateX: frame.x }, { translateY: frame.y }, { scale: frame.k }]}>
      <Rect x={-160} y={0} width={722} height={874} blendMode="plus"><Shader source={bounce!} uniforms={bounceLight} /></Rect>
      <Rect x={-160} y={80} width={722} height={794}><Shader source={bloom!} uniforms={bloomUniforms} /></Rect>
      <Group layer={<Paint opacity={fanAlpha} />}>
        {faces.map((face, index) => <MetacardFanCard key={index} index={index} face={face} shadow={shadow} values={values} reduced={reduced} />)}
        <MetacardFanCard index={0} face={faces[0]} shadow={shadow} values={values} seam reduced={reduced} />
        <Rect x={0} y={200} width={D.width} height={460} blendMode="srcATop"><Shader source={darkness!} uniforms={darkUniforms} /></Rect>
        {reduced && <MetacardHero values={values} chip={chip} reduced still={0} />}
      </Group>
      <Group layer={reduced ? <Paint opacity={heroAlpha} /> : undefined}><MetacardHero values={values} chip={chip} reduced={reduced} still={1} /></Group>
      <Rect x={-50} y={160} width={502} height={520}><Shader source={halo!} uniforms={haloUniforms} /></Rect>
      <MetacardText values={values} paragraphs={paragraphs} reduced={reduced} />
    </Group>
  </Canvas>;
}

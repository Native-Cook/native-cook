import { type ReactNode, useEffect, useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { makeMutable, type SharedValue, useAnimatedReaction, useAnimatedStyle } from 'react-native-reanimated';
import { Blur, Canvas, ColorMatrix, Group, Mask, LinearGradient, measureText, Oval, Path, Rect, RoundedRect, Shader, Text, TextPath, TgfxImage, useFonts, useImage } from 'react-native-tgfx';

import { AMETHYST_MATRIX, ART, BEAR_MATRIX } from './fhr-art';
import { clamp, ease, HEART, seg, type Scene } from './fhr-motion';
import { CAUSTIC_SKSL, SLAB_SKSL, SPOTLIGHT_SKSL, SPRITE_MASK_SKSL, TAG_SKSL, WAVE_MASK_SKSL } from './fhr-shaders';

const FONTS = { 'FHR Inter': [require('./assets/fonts/Inter-Regular.ttf'), require('./assets/fonts/Inter-SemiBold.ttf'), require('./assets/fonts/Inter-Bold.ttf')] };
const FAMILY = 'FHR Inter';
const PAW_MATRIX = [.570302, .130380, .057030, 0, .073195, .012287, .323003, .008781, 0, .079719, .087032, .052223, .635386, 0, .045082, 0, 0, 0, 1, 0];
const IDENTITY_MATRIX = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0];
/** a ∘ b for 4×5 colour matrices (b applied first). A nested ColorMatrix replaces its parent's in
 * tgfx instead of composing with it, so a sprite's own matrix is folded into the level colour here. */
function composeMatrix(a: number[], b: number[]) {
  const out = new Array<number>(20).fill(0);
  for (let i = 0; i < 4; i++) {
    for (let j = 0; j < 5; j++) {
      let v = j === 4 ? a[i * 5 + 4] : 0;
      for (let k = 0; k < 4; k++) v += a[i * 5 + k] * b[k * 5 + j];
      out[i * 5 + j] = v;
    }
  }
  return out;
}
const BADGE = 'M302 154 C326 165 348 191 352 218';
const BADGE_BASELINE = 'M298 158 C322 169 344 195 348 222';
const BADGE_HIGHLIGHT = 'M298 158.65 C322 169.65 344 195.65 348 222.65';
const PROGRESS_PATH = 'M175 175 A126 103 0 1 1 174.999 175';
const SLOT = { time: 0, hue: 1, wink: 2, open: 3, push: 4, light: 5, gem: 6, restBear: 7, tiltX: 8, tiltY: 9, jelly: 10, blur: 11, raised: 12, tagY: 13, first: 14, next: 15, whiteFirst: 16, whiteNext: 17, metalFirst: 18, metalNext: 19, mist: 20, heart: 21, polish: 22, pulse: 23, progress: 24, progressAlpha: 25, bearScaleX: 26, bearScaleY: 27, tagBlur: 28, core: 29 } as const;
const INITIAL = [0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0.55, 0, 1, 1, 0, 1, 0, 0, 0, 0.62, 1, 0, 1, 0, 0, 1, 1, 0, 0];

type Props = { scene: SharedValue<Scene>; tilt: SharedValue<{ x: number; y: number }>; ring: SharedValue<number>; jelly: SharedValue<number>; width: number; height: number; offsetY: number; reduced: boolean; gpuLayer?: ReactNode; gpuAvailable: boolean; onReady: (ready: boolean) => void };

/** Two compiled tgfx scenes leave a real compositor slot for the gem between torso and paws. */
export function FhrAtelierStage({ scene, tilt, jelly, width, height, offsetY, reduced, gpuLayer, gpuAvailable, onReady }: Props) {
  const slots = useMemo(() => INITIAL.map(value => makeMutable(value)), []);
  const waveVisible = useMemo(() => makeMutable<boolean>(false), []);
  const finalVisible = useMemo(() => makeMutable<boolean>(false), []);
  const restVisible = useMemo(() => makeMutable<boolean>(true), []);
  const hugVisible = useMemo(() => makeMutable<boolean>(false), []);
  const frostVisible = useMemo(() => makeMutable<boolean>(true), []);
  const lightVisible = useMemo(() => makeMutable<boolean>(false), []);
  const causticVisible = useMemo(() => makeMutable<boolean>(false), []);
  const bear = useImage(require('./assets/fhr-bear.png'));
  const hug = useImage(require('./assets/fhr-bear-hug.png'));
  const heart = useImage(require('./assets/fhr-heart.png'));
  const paw = useImage(require('./assets/fhr-paw.png'));
  const frontPaw = useImage(require('./assets/fhr-front-paw.png'));
  const fontsReady = useFonts(FONTS);
  const ready = !!(bear && hug && heart && paw && frontPaw && fontsReady);
  useEffect(() => { onReady(ready); }, [onReady, ready]);
  useEffect(() => {
    if (!__DEV__) return;
    const inspect = () => ({ frostVisible: frostVisible.get(), heartAlpha: slots[SLOT.heart].get(), open: slots[SLOT.open].get() });
    Object.defineProperty(globalThis, '__fhrStage', { value: inspect, configurable: true });
    return () => { if (Reflect.get(globalThis, '__fhrStage') === inspect) Reflect.deleteProperty(globalThis, '__fhrStage'); };
  }, [frostVisible, slots]);
  const slabWidth = useMemo(() => fontsReady ? measureText('lumi', { fontFamily: FAMILY, fontSize: 47, fontWeight: 700 }).width : 0, [fontsReady]);
  useAnimatedReaction(
    () => ({ scene: scene.get(), tilt: tilt.get(), jelly: jelly.get() }),
    ({ scene: sc, tilt: pose, jelly: j }) => {
      const keep = 1 - sc.reset;
      const light = sc.spotlight * keep;
      const open = clamp(sc.display) * keep;
      // Mask allocates its layers before child opacity is applied. Skip truly empty poses.
      restVisible.set(open < 1);
      hugVisible.set(open > 0);
      const gem = gpuAvailable ? sc.gem * keep : 0;
      // The original native raster remains the registered outer frost skin.
      // Crystal coverage removes it facet by facet; only its last subpixel halo
      // fades after every facet has cleared, before the case opens.
      const frostHalo = 1 - ease(seg(sc.t, 3.8, 3.9));
      const heartAlpha = gpuAvailable ? Math.max(frostHalo, sc.reset) : 1;
      // Native filters/masks still execute under zero opacity. Cull only exact zeros.
      frostVisible.set(heartAlpha > 0);
      lightVisible.set(light > 0);
      causticVisible.set(gem > 0);
      const hue = sc.hue * keep;
      waveVisible.set(!reduced && hue > 0 && hue < 1);
      finalVisible.set(reduced ? hue > 0 : hue >= 1);
      const tagBlend = sc.flip >= 0.5 ? 1 : 0;
      const first = reduced ? 1 - hue : (1 - tagBlend) * keep + sc.reset;
      const next = reduced ? hue : tagBlend * keep;
      // Before spotlight, the time-dependent shader terms all have zero weight.
      // Retain the exact native image instead of invalidating it for an invisible sweep.
      slots[SLOT.time].set(light <= 0 && gem <= 0 ? 0 : sc.t);
      slots[SLOT.hue].set(hue);
      slots[SLOT.wink].set((sc.wink + Math.abs(j)) * keep);
      slots[SLOT.open].set(open);
      slots[SLOT.push].set(1 + sc.push * keep);
      slots[SLOT.light].set(light);
      slots[SLOT.gem].set(gem);
      slots[SLOT.restBear].set(1 - open);
      slots[SLOT.tiltX].set(reduced ? 0 : pose.x * keep);
      slots[SLOT.tiltY].set(reduced ? 0 : pose.y * keep);
      slots[SLOT.jelly].set(reduced ? 0 : j * keep);
      slots[SLOT.blur].set(reduced ? 0.55 : 0.55 + Math.sin(sc.reset * Math.PI) * 2.2);
      slots[SLOT.raised].set(open * 0.13);
      slots[SLOT.tagBlur].set(reduced ? 0 : 2.8 * Math.sin(tagBlend * Math.PI) * keep);
      slots[SLOT.tagY].set(reduced ? 1 : Math.max(0.015, Math.abs(Math.cos(sc.flip * Math.PI)) * keep + sc.reset));
      slots[SLOT.first].set(first);
      slots[SLOT.next].set(next);
      slots[SLOT.whiteFirst].set(first * (1 - light));
      slots[SLOT.whiteNext].set(next * (1 - light));
      slots[SLOT.metalFirst].set(first * light);
      slots[SLOT.metalNext].set(next * light);
      // The atmosphere stays continuous while the frost clears.
      slots[SLOT.mist].set(0.62 * (1 - sc.cut * keep));
      slots[SLOT.heart].set(heartAlpha);
      slots[SLOT.polish].set(sc.polish * keep);
      slots[SLOT.core].set((reduced ? 0 : sc.core) * keep);
      slots[SLOT.pulse].set(1 + sc.pulse * keep);
      const progress = clamp((sc.balance - 1650) / 75);
      slots[SLOT.progress].set(progress);
      slots[SLOT.progressAlpha].set(0.2 * progress * Math.sin(Math.min(1, progress * 3) * Math.PI / 2) * (1 - sc.cut) * keep);
      const squash = reduced ? 0 : j * keep;
      slots[SLOT.bearScaleX].set(1 - 0.03 * squash);
      slots[SLOT.bearScaleY].set(1 + 0.015 * squash);
    },
  );
  // Swap the two faces only at the edge-on point of one perspective rotateX.
  const tagTurn = useAnimatedStyle(() => {
    const sc = scene.get();
    const angle = sc.flip * 180;
    const folded = angle > 90 ? angle - 180 : angle;
    return { transform: [{ perspective: 700 }, { rotateX: `${reduced ? 0 : folded * (1 - sc.reset)}deg` }] };
  });
  const S = width / 402;
  const view = [StyleSheet.absoluteFill, { width, height }];
  const page = [{ translateY: offsetY }, { scale: S }];
  const hero = [{ translateX: HEART.x }, { translateY: HEART.y }, { translateX: slots[SLOT.tiltX] }, { translateY: slots[SLOT.tiltY] }, { scale: slots[SLOT.push] }, { translateX: -HEART.x }, { translateY: -HEART.y }];
  const slab = ART.slab, cx = slab.x + slab.width / 2, cy = slab.y + slab.height / 2;
  const tx = cx - slabWidth / 2, ty = slab.y + slab.height * 0.70;
  if (!ready) return null;
  const sprite = (image: typeof bear, box: { x: number; y: number; width: number; height: number; rotation: number }, matrix = IDENTITY_MATRIX) => {
    const angle = box.rotation * Math.PI / 180, c = Math.abs(Math.cos(angle)), sn = Math.abs(Math.sin(angle));
    // Native Mask uses two saveLayers. A real visibility gate skips them, and a clipped surface
    // only covers this rotated sprite plus 12 pt for the largest blur used during the return.
    const w = box.width * c + box.height * sn + 24, h = box.width * sn + box.height * c + 24;
    const bounds = { x: box.x + box.width / 2 - w / 2, y: box.y + box.height / 2 - h / 2, width: w, height: h };
    const level = composeMatrix(AMETHYST_MATRIX, matrix);
    const finalMatrix = reduced ? [...level.slice(0, 18), slots[SLOT.hue], 0] : level;
    const transform = [{ translateX: box.x + box.width / 2 }, { translateY: box.y + box.height / 2 }, { rotate: angle }, { translateX: -box.x - box.width / 2 }, { translateY: -box.y - box.height / 2 }];
    return <>
      <Group><ColorMatrix matrix={matrix} /><Group transform={transform}><TgfxImage image={image} {...box} fit="fill" /></Group></Group>
      <Group visible={waveVisible} clip={bounds}>
        <Mask mask={<Rect {...bounds}><Shader source={WAVE_MASK_SKSL} uniforms={{ wave: slots[SLOT.hue], reduced: 0 }} /></Rect>}>
          <Group><ColorMatrix matrix={level} /><Group transform={transform}><TgfxImage image={image} {...box} fit="fill" /></Group></Group>
        </Mask>
      </Group>
      <Group visible={finalVisible}><ColorMatrix matrix={finalMatrix} /><Group transform={transform}><TgfxImage image={image} {...box} fit="fill" /></Group></Group>
    </>;
  };
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Canvas style={view} composite="texture" colorSpace="srgb">
      <Group transform={page}>
        <Group transform={hero}>
          <Group visible={hugVisible} opacity={slots[SLOT.open]}><Oval x={58} y={193} width={238} height={178} color="rgba(235,201,221,0.15)"><Blur blur={22} /></Oval></Group>
          <Group transform={[{ translateX: 175 }, { translateY: 245 }, { scaleX: slots[SLOT.bearScaleX] }, { scaleY: slots[SLOT.bearScaleY] }, { translateX: -175 }, { translateY: -245 }]}>
          <Group visible={restVisible}><Mask mask={<Rect x={30} y={100} width={310} height={310}><Shader source={SPRITE_MASK_SKSL} uniforms={{ floorY: 292, fade: 40 }} /></Rect>}>
            <Group opacity={slots[SLOT.restBear]}><Blur blur={slots[SLOT.blur]} />{sprite(bear, ART.bear, BEAR_MATRIX)}</Group>
          </Mask></Group>
          <Group visible={hugVisible}><Mask mask={<Rect x={30} y={100} width={310} height={310}><Shader source={SPRITE_MASK_SKSL} uniforms={{ floorY: 310, fade: 40 }} /></Rect>}>
            <Group opacity={slots[SLOT.open]}><Blur blur={slots[SLOT.blur]} />{sprite(hug, ART.bear, BEAR_MATRIX)}</Group>
          </Mask></Group>
          <Group opacity={slots[SLOT.wink]}><Path path="M193 183 Q200 189 207 182" style="stroke" strokeWidth={2.6} strokeCap="round" color="#6E3B73" /></Group>
          </Group>
          <Group visible={frostVisible}><Mask mask={<Rect x={35} y={160} width={295} height={245}><Shader source={SPRITE_MASK_SKSL} uniforms={{ floorY: 306, fade: 62 }} /></Rect>}>
            <Group opacity={slots[SLOT.heart]} transform={[{ translateX: HEART.x }, { translateY: HEART.y }, { scale: slots[SLOT.pulse] }, { translateX: -HEART.x }, { translateY: -HEART.y }]}><Blur blur={slots[SLOT.blur]} /><Group transform={[{ translateX: ART.heart.x + ART.heart.width / 2 }, { translateY: ART.heart.y + ART.heart.height / 2 }, { rotate: ART.heart.rotation * Math.PI / 180 }, { translateX: -ART.heart.x - ART.heart.width / 2 }, { translateY: -ART.heart.y - ART.heart.height / 2 }]}><TgfxImage image={heart} {...ART.heart} fit="fill" /></Group></Group>
          </Mask></Group>
          <Path path={PROGRESS_PATH} style="stroke" strokeWidth={0.75} strokeCap="round" color="#E3E1F7" end={slots[SLOT.progress]} opacity={slots[SLOT.progressAlpha]} />
        </Group>
      </Group>
    </Canvas>
    {gpuLayer}
    <Canvas style={view} composite="texture" colorSpace="srgb" fonts={FONTS}>
      <Group transform={page}>
        <Group transform={hero}>
          <Group visible={restVisible} opacity={slots[SLOT.restBear]} transform={[{ translateX: 257 }, { translateY: 217 }, { rotate: slots[SLOT.raised] }, { translateX: -257 }, { translateY: -217 }]} clip="M308 208.5 A21.5 21.5 0 1 0 265 208.5 A21.5 21.5 0 1 0 308 208.5"><Blur blur={slots[SLOT.blur]} />{sprite(paw, ART.raisedPaw)}</Group>
        </Group>
        <Group transform={hero}>
          <Oval x={87} y={317} width={190} height={63} color="#B8BED4" opacity={slots[SLOT.mist]}><Blur blur={16} /></Oval>
          <Group visible={causticVisible}><Rect x={42} y={285} width={286} height={108} blendMode="plusLighter"><Shader source={CAUSTIC_SKSL} uniforms={{ time: slots[SLOT.time], light: slots[SLOT.gem], display: slots[SLOT.open], polish: slots[SLOT.polish] }} /></Rect></Group>
        </Group>
        {/* The fingertips occlude the crystal and its mist, retaining their glossy surface. */}
        <Group>{sprite(frontPaw, { x: 54.5, y: 288.5, width: 90, height: 94.5, rotation: 0 }, PAW_MATRIX)}</Group>
        {/* Only the plaque's lighting changes; its authored transform never follows the heart. */}
        <Group transform={[{ translateX: 0 }, { translateY: 0 }, { translateX: cx }, { translateY: cy }, { rotate: slab.rotation }, { scaleX: 1 }, { scaleY: 1 }, { translateX: -cx }, { translateY: -cy }]}>
          <Blur blur={0.55} />
          <RoundedRect x={slab.x} y={slab.y + 5} width={slab.width} height={slab.height} r={39}><LinearGradient start={{ x: cx, y: slab.y }} end={{ x: cx, y: slab.y + slab.height + 6 }} colors={['#F9B9ED', '#DE2DC7', '#76196F']} /></RoundedRect>
          <RoundedRect x={slab.x} y={slab.y} width={slab.width} height={slab.height} r={38}><LinearGradient start={{ x: cx, y: slab.y }} end={{ x: cx, y: slab.y + slab.height }} colors={['#FD2DDE', '#E92ACD', '#A10CA0']} positions={[0, 0.46, 1]} /></RoundedRect>
          <Rect x={slab.x - 1} y={slab.y - 1} width={slab.width + 2} height={slab.height + 2}><Shader source={SLAB_SKSL} uniforms={{ time: slots[SLOT.time], spotlight: slots[SLOT.light], polish: slots[SLOT.polish], core: slots[SLOT.core] }} /></Rect>
          <Text text="lumi" x={tx + 0.8} y={ty + 2} fontFamily={FAMILY} fontWeight={700} fontSize={47} color="#6F256D"><Blur blur={1.5} /></Text>
          <Text text="lumi" x={tx} y={ty + 0.8} fontFamily={FAMILY} fontWeight={700} fontSize={47} color="#B77AAD" />
          <Text text="lumi" x={tx - 0.4} y={ty - 0.5} fontFamily={FAMILY} fontWeight={700} fontSize={47}><LinearGradient start={{ x: cx, y: ty - 36 }} end={{ x: cx, y: ty + 1 }} colors={['#FFFFFF', '#FDF3FF', '#D6BDD8']} positions={[0, 0.55, 1]} /></Text>
        </Group>
        <Group visible={lightVisible}><Rect x={0} y={96} width={402} height={390}><Shader source={SPOTLIGHT_SKSL} uniforms={{ light: slots[SLOT.light] }} /></Rect></Group>
      </Group>
    </Canvas>
    <Animated.View style={[{ position: 'absolute', left: 280 * S, top: 136 * S + offsetY, width: 92 * S, height: 112 * S }, tagTurn]}>
      <Canvas style={StyleSheet.absoluteFill} composite="texture" colorSpace="srgb" fonts={FONTS}>
        <Group transform={[{ scale: S }, { translateX: -280 }, { translateY: -136 }]}>
          <Path path={BADGE} style="stroke" strokeWidth={29} strokeCap="round"><Shader source={TAG_SKSL} uniforms={{ light: slots[SLOT.light], time: slots[SLOT.time], polish: slots[SLOT.polish], platinum: slots[SLOT.next] }} /></Path>
          <TextPath path={BADGE_BASELINE} text="Level 3" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#FFFFFF" opacity={slots[SLOT.whiteFirst]} />
          <TextPath path={BADGE_BASELINE} text="Level 4" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#FFFFFF" opacity={slots[SLOT.whiteNext]} />
          <TextPath path={BADGE_HIGHLIGHT} text="Level 3" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#B9C8DE" opacity={slots[SLOT.metalFirst]} />
          <TextPath path={BADGE_HIGHLIGHT} text="Level 4" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#FFFFFF" opacity={slots[SLOT.metalNext]} />
          <TextPath path={BADGE_BASELINE} text="Level 3" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#263143" opacity={slots[SLOT.metalFirst]} />
          <TextPath path={BADGE_BASELINE} text="Level 4" fontFamily={FAMILY} fontWeight={600} fontSize={17.7} color="#53535B" opacity={slots[SLOT.metalNext]} />
        </Group>
      </Canvas>
    </Animated.View>
  </View>;
}

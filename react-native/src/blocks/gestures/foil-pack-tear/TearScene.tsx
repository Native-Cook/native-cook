// A retained Skia scene. Motion supplies the pose; one UI clock owns all ambient movement.
import {
  Blur, Canvas, Fill, Group, ImageShader, Path, Rect, Shader, Skia, useImage,
  type SkImage,
} from '@shopify/react-native-skia';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import {
  useDerivedValue, useFrameCallback, useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';

import { CARD_SKSL } from './card-shader';
import { CURL_SKSL, FOIL_SKSL, LIP_SKSL } from './foil-shader';
import { FRAME_SKSL, RAYS_SKSL } from './frame-shader';
import { LIGHT_SKSL, MOUTH_SKSL, SHADOW_SKSL, SPARK_SKSL } from './light-shader';
import { SCENE_ASSETS } from './scene-assets';
import {
  cardUniforms, curlMatrix, curlUniforms, foilUniforms, frameUniforms, freeCurlMatrix, lightUniforms,
  packMatrix, particlePose, raysUniforms, stageMatrix,
} from './scene-uniforms';
import { stageFit, type TearPose, type TearValues } from './tear-contract';
import { traceMouth, tracePack, traceTornEdge } from './tear-geometry';

function compile(source: string) {
  const effect = Skia.RuntimeEffect.Make(source);
  if (!effect) throw new Error('Foil Pack Tear: a scene shader did not compile.');
  return effect;
}

const FRAME_EFFECT = compile(FRAME_SKSL);
const FOIL_EFFECT = compile(FOIL_SKSL);
const CURL_EFFECT = compile(CURL_SKSL);
const LIP_EFFECT = compile(LIP_SKSL);
const CARD_EFFECT = compile(CARD_SKSL);
const LIGHT_EFFECT = compile(LIGHT_SKSL);
const MOUTH_EFFECT = compile(MOUTH_SKSL);
const RAYS_EFFECT = compile(RAYS_SKSL);
const SHADOW_EFFECT = compile(SHADOW_SKSL);
const SPARK_EFFECT = compile(SPARK_SKSL);
const BURST_SEEDS = Array.from({ length: 48 }, (_, i) => i);
const DUST_SEEDS = Array.from({ length: 18 }, (_, i) => i + 100);

export type TearSceneProps = {
  width: number;
  height: number;
  values: TearValues;
  reduceMotion: boolean;
  onReady?: (ready: boolean) => void;
};

type ParticleProps = {
  image: SkImage;
  pose: SharedValue<TearPose>;
  clock: SharedValue<number>;
  seed: number;
  burst: boolean;
};

function Particle({ image, pose, clock, seed, burst }: ParticleProps) {
  const particle = useDerivedValue(() => particlePose(pose.get(), clock.get(), seed, burst));
  const rect = useDerivedValue(() => {
    const p = particle.get();
    return Skia.XYWHRect(-p.size / 2, -p.size / 2, p.size, p.size);
  });
  const alpha = useDerivedValue(() => particle.get().alpha);
  const uniforms = useDerivedValue(() => ({ uSize: particle.get().size }));
  const transform = useDerivedValue(() => {
    const p = particle.get();
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rotation }];
  });
  return (
    <Group transform={transform} opacity={alpha} blendMode="plus">
      <Rect rect={rect}>
        <Shader source={SPARK_EFFECT} uniforms={uniforms}>
          <ImageShader image={image} fit="none" tx="decal" ty="decal" />
        </Shader>
      </Rect>
    </Group>
  );
}

export function TearScene({ width, height, values, reduceMotion, onReady }: TearSceneProps): React.JSX.Element {
  const packMaps = useImage(SCENE_ASSETS.packMaps);
  const packPrint = useImage(SCENE_ASSETS.packPrint);
  const cardFace = useImage(SCENE_ASSETS.cardFace);
  const cardBack = useImage(SCENE_ASSETS.cardBack);
  const cardMaps = useImage(SCENE_ASSETS.cardMaps);
  const tabLabel = useImage(SCENE_ASSETS.tabLabel);
  const spark = useImage(SCENE_ASSETS.spark);
  const [laidOut, setLaidOut] = useState(false);
  const imagesReady = !!(packMaps && packPrint && cardFace && cardBack && cardMaps && tabLabel && spark);
  const ready = imagesReady && laidOut;
  useEffect(() => {
    onReady?.(ready);
  }, [onReady, ready]);
  useEffect(() => () => onReady?.(false), [onReady]);
  const clock = useSharedValue(0);
  const ticker = useFrameCallback(frame => {
    'worklet';
    clock.set((clock.get() + (frame.timeSincePreviousFrame ?? 0) / 1000) % 13);
  }, false);
  useEffect(() => {
    ticker.setActive(!reduceMotion);
    return () => ticker.setActive(false);
  }, [reduceMotion, ticker]);
  const { lightX, lightY, swayX, swayY, calm, packX, packY, packRot, packScale, packOpacity, tearFront, tearLine, stripFree, stripX, stripY, stripRot, stripOpacity, leak, burst, mouth, cardX, cardY, cardScale, cardFlip, cardTiltX, cardTiltY, cardOpacity, sheen, frameHeat, frameRing, tabGlow, sparkleAge, dust } = values;
  const pose = useDerivedValue<TearPose>(() => {
    'worklet';
    return {
      lightX: lightX.get(),
      lightY: lightY.get(),
      swayX: swayX.get(),
      swayY: swayY.get(),
      calm: calm.get(),
      packX: packX.get(),
      packY: packY.get(),
      packRot: packRot.get(),
      packScale: packScale.get(),
      packOpacity: packOpacity.get(),
      tearFront: tearFront.get(),
      tearLine: tearLine.get(),
      stripFree: stripFree.get(),
      stripX: stripX.get(),
      stripY: stripY.get(),
      stripRot: stripRot.get(),
      stripOpacity: stripOpacity.get(),
      leak: leak.get(),
      burst: burst.get(),
      mouth: mouth.get(),
      cardX: cardX.get(),
      cardY: cardY.get(),
      cardScale: cardScale.get(),
      cardFlip: cardFlip.get(),
      cardTiltX: cardTiltX.get(),
      cardTiltY: cardTiltY.get(),
      cardOpacity: cardOpacity.get(),
      sheen: sheen.get(),
      frameHeat: frameHeat.get(),
      frameRing: frameRing.get(),
      tabGlow: tabGlow.get(),
      sparkleAge: sparkleAge.get(),
      dust: dust.get(),
    };
  });
  const frame = useDerivedValue(() => frameUniforms(pose.get(), clock.get()));
  const foil = useDerivedValue(() => foilUniforms(pose.get(), clock.get()));
  const curl = useDerivedValue(() => curlUniforms(pose.get()));
  const card = useDerivedValue(() => cardUniforms(pose.get()));
  const light = useDerivedValue(() => lightUniforms(pose.get(), clock.get()));
  const rays = useDerivedValue(() => raysUniforms(pose.get(), clock.get()));
  const wall = useDerivedValue(() => ({ uLeak: leak.get(), uMouth: mouth.get(), uEdgeY: light.get().uEdgeY }));
  const packBehindAlpha = useDerivedValue(() => cardFlip.get() > 0.02 ? 1 : 0);
  const packInFrontAlpha = useDerivedValue(() => cardFlip.get() > 0.02 ? 0 : 1);
  const mouthGlowAlpha = useDerivedValue(() => leak.get() * 0.60);
  const stageTransform = useDerivedValue(() => stageMatrix(pose.get()));
  const packTransform = useDerivedValue(() => packMatrix(pose.get()));
  const curlTransform = useDerivedValue(() => curlMatrix(pose.get()));
  const curlAlpha = useDerivedValue(() => tearFront.get() > 0.1 && stripFree.get() < 0.5 ? stripOpacity.get() : 0);
  const freeCurlAlpha = useDerivedValue(() => stripFree.get() >= 0.5 ? stripOpacity.get() : 0);
  const freeCurlTransform = useDerivedValue(() => freeCurlMatrix(pose.get()));
  const cardTransform = useDerivedValue(() => [
    { translateX: cardX.get() }, { translateY: cardY.get() }, { scale: cardScale.get() },
  ]);
  const cardShadow = useDerivedValue(() => ({
    uExtent: [90 * Math.abs(Math.cos(cardFlip.get() + cardTiltY.get())), 126],
    uStrength: 0.32,
  }));
  const cut = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    tracePack(builder, tearLine.get(), tearFront.get(), mouth.get());
    return builder.build();
  });
  const lip = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    traceTornEdge(builder, tearLine.get(), tearFront.get(), mouth.get());
    return builder.build();
  });
  const backLip = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    traceTornEdge(builder, tearLine.get(), tearFront.get(), mouth.get(), true);
    return builder.build();
  });
  const backLipAlpha = useDerivedValue(() => mouth.get() * 0.85);
  const opening = useDerivedValue(() => {
    const builder = Skia.PathBuilder.Make();
    traceMouth(builder, tearLine.get(), tearFront.get(), mouth.get());
    return builder.build();
  });
  const lipAlpha = useDerivedValue(() => tearFront.get() > 0.1 ? 0.9 : 0);
  const packFront = imagesReady ? (
    <Group matrix={packTransform} opacity={packOpacity}>
      <Group clip={cut}>
        <Rect x={0} y={0} width={216} height={376}>
          <Shader source={FOIL_EFFECT} uniforms={foil}>
            <ImageShader image={packMaps} fit="none" />
            <ImageShader image={packPrint} fit="none" />
          </Shader>
        </Rect>
        <Path path={lip} style="stroke" strokeWidth={6}>
          <Shader source={LIP_EFFECT} uniforms={{ uColour: [0.72, 1, 0.89, 0.14] }}>
            <ImageShader image={packMaps} fit="none" />
          </Shader>
        </Path>
      </Group>
      <Path path={lip} style="stroke" strokeWidth={0.75} opacity={lipAlpha}>
        <Shader source={LIP_EFFECT} uniforms={{ uColour: [0.89, 0.91, 0.81, 1] }}>
          <ImageShader image={packMaps} fit="none" />
        </Shader>
      </Path>
      <Group matrix={curlTransform} opacity={curlAlpha}>
        <Rect x={-34} y={-12} width={60} height={80}>
          <Shader source={CURL_EFFECT} uniforms={curl} />
        </Rect>
      </Group>
    </Group>
  ) : null;
  const fit = stageFit(width, height);
  const pageScale = Math.max(fit.k, 0.001);
  return (
    <View pointerEvents="none" style={{ width, height }} onLayout={event => {
      setLaidOut(event.nativeEvent.layout.width > 0 && event.nativeEvent.layout.height > 0);
    }}>
      <Canvas pointerEvents="none" style={{ width, height }}>
        <Fill color="#010207" />
        {imagesReady && (
          <Group transform={[{ translateX: fit.x }, { translateY: fit.y }, { scale: fit.k }]}>
            <Group matrix={stageTransform}>
              <Rect x={-fit.x / pageScale - 32} y={-fit.y / pageScale - 32}
                width={width / pageScale + 64} height={height / pageScale + 64}>
                <Shader source={FRAME_EFFECT} uniforms={frame}>
                  <ImageShader image={tabLabel} fit="none" tx="decal" ty="decal" />
                </Shader>
              </Rect>
              <Rect x={-24} y={108} width={450} height={540} blendMode="plus">
                <Shader source={RAYS_EFFECT} uniforms={rays} />
              </Rect>
              {BURST_SEEDS.map(seed => <Particle key={seed} seed={seed} burst image={spark!} pose={pose} clock={clock} />)}
              <Group matrix={packTransform} opacity={packOpacity}>
                <Group transform={[{ translateX: 115 }, { translateY: 203 }]}>
                  <Rect x={-136} y={-210} width={272} height={420}>
                    <Shader source={SHADOW_EFFECT} uniforms={{ uExtent: [104, 178], uStrength: 0.38 }} />
                  </Rect>
                </Group>
                <Path path={opening}>
                  <Shader source={MOUTH_EFFECT} uniforms={wall} />
                </Path>
                <Path path={backLip} style="stroke" strokeWidth={0.65} color="#CEE3D4" opacity={backLipAlpha} />
              </Group>
              <Group opacity={packBehindAlpha}>{packFront}</Group>
              <Group transform={cardTransform} opacity={cardOpacity}>
                <Group transform={[{ translateX: 0 }, { translateY: 8 }]}>
                  <Rect x={-120} y={-156} width={240} height={312}>
                    <Shader source={SHADOW_EFFECT} uniforms={cardShadow} />
                  </Rect>
                </Group>
                <Rect x={-140} y={-164} width={280} height={328}>
                  <Shader source={CARD_EFFECT} uniforms={card}>
                    <ImageShader image={cardFace} fit="none" />
                    <ImageShader image={cardBack} fit="none" />
                    <ImageShader image={cardMaps} fit="none" />
                  </Shader>
                </Rect>
              </Group>
              <Group opacity={packInFrontAlpha}>{packFront}</Group>
              <Group matrix={packTransform} opacity={packOpacity}>
                <Group clip={opening} invertClip>
                  <Rect x={-190} y={-280} width={596} height={520} blendMode="plus">
                    <Shader source={LIGHT_EFFECT} uniforms={light} />
                  </Rect>
                </Group>
                <Path path={opening} opacity={0.12} blendMode="plus">
                  <Shader source={LIGHT_EFFECT} uniforms={light} />
                </Path>
                <Group opacity={mouthGlowAlpha} blendMode="plus">
                  <Path path={lip} style="stroke" strokeWidth={5} color="#FFD27A">
                    <Blur blur={4} />
                  </Path>
                  <Path path={lip} style="stroke" strokeWidth={1.4}>
                    <Shader source={LIP_EFFECT} uniforms={{ uColour: [1, 0.965, 0.847, 1] }}>
                      <ImageShader image={packMaps} fit="none" />
                    </Shader>
                  </Path>
                </Group>
              </Group>
              <Group matrix={freeCurlTransform} opacity={freeCurlAlpha}>
                <Rect x={-34} y={-12} width={60} height={80}>
                  <Shader source={CURL_EFFECT} uniforms={curl} />
                </Rect>
              </Group>
              {DUST_SEEDS.map(seed => <Particle key={seed} seed={seed} burst={false} image={spark!} pose={pose} clock={clock} />)}
            </Group>
          </Group>
        )}
      </Canvas>
    </View>
  );
}

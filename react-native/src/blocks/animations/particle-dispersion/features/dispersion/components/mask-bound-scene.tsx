import { AnimatedParticleRendererView } from '../../../native/subject-segmentation';
import { Blur, Canvas, Group, ImageShader, Rect, Shader, Skia, useImage, vec, type SkImage } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';

import { DUST_DURATION_SECONDS, DUST_START_SECONDS, SUBJECT_IMPACT_PADDING_MULTIPLIER } from '../constants';
import { useDispersionPlayback } from '../hooks/use-dispersion-playback';
import { useSubjectAnimation } from '../hooks/use-subject-animation';
import { maskSilhouetteRuntimeEffect, subjectCutoutRuntimeEffect } from '../shaders/dispersion-shader';
import type { NormalizedPoint, SceneSizeProps, SubjectBounds } from '../types';
import { canvasStyle, makeCoverMapping, mapBounds, resolveSubjectBounds } from '../utils/geometry';
import { dispersionSeed } from '../utils/seed';
import { StillCanvas } from './captured-frame';

type MaskBoundSceneProps = SceneSizeProps &
  Readonly<{
    frame: SkImage;
    frameUri: string;
    maskUri: string;
    playing: boolean;
    reducedMotion: boolean;
    replayToken: number;
    contourPoints?: readonly NormalizedPoint[] | null;
    subjectBounds?: SubjectBounds | null;
    onEffectStart?: () => void;
    onEffectComplete?: () => void;
    onEffectError?: (message: string) => void;
    edgeParticleStrength: number;
  }>;

export function MaskBoundScene({
  frame,
  frameUri,
  maskUri,
  viewport,
  playing,
  reducedMotion,
  backgroundColor,
  replayToken,
  contourPoints,
  subjectBounds,
  onEffectStart,
  onEffectComplete,
  onEffectError,
  edgeParticleStrength,
}: MaskBoundSceneProps) {
  const mask = useImage(maskUri);
  const { progress, nativeReady, rendererFailed, handleNativeReady, handleNativeError } =
    useDispersionPlayback({
      frameUri,
      maskUri,
      maskReady: mask != null,
      shadersReady: subjectCutoutRuntimeEffect != null && maskSilhouetteRuntimeEffect != null,
      playing,
      reducedMotion,
      replayToken,
      onEffectStart,
      onEffectComplete,
      onEffectError,
    });

  const seed = useMemo(
    () => dispersionSeed(`${frameUri}:${maskUri}`),
    [frameUri, maskUri]
  );
  const normalizedBounds = useMemo(
    () => resolveSubjectBounds(subjectBounds, contourPoints),
    [contourPoints, subjectBounds]
  );
  const mapping = useMemo(
    () => makeCoverMapping(frame, viewport),
    [frame, viewport]
  );
  const pixelBounds = useMemo(
    () => mapBounds(normalizedBounds, mapping),
    [mapping, normalizedBounds]
  );
  const skiaBackgroundColor = useMemo(
    () => Array.from(Skia.Color(backgroundColor)),
    [backgroundColor]
  );
  const {
    impactAmplitude,
    nativeAnimatedProps,
    nativeSubjectBounds,
    shadowOpacity,
    shadowTransform,
    shadowUniforms,
    subjectTransform,
    subjectUniforms,
    underlayOpacity,
    underlayTransform,
    underlayUniforms,
  } = useSubjectAnimation({ progress, pixelBounds, viewport, reducedMotion });

  if (
    !mask ||
    !AnimatedParticleRendererView ||
    rendererFailed ||
    !subjectCutoutRuntimeEffect ||
    !maskSilhouetteRuntimeEffect
  ) {
    return (
      <StillCanvas
        backgroundColor={backgroundColor}
        frame={frame}
        viewport={viewport}
      />
    );
  }

  const rect = { x: 0, y: 0, width: viewport.width, height: viewport.height };
  const origin = vec(pixelBounds.centerX, pixelBounds.centerY);
  const shadowBlur = Math.max(8, Math.min(viewport.width, viewport.height) * 0.028);
  // Include the photo taper and an unchanged border during the first scan;
  // the opaque patch must meet the original background without a visible seam.
  const subjectPadding = impactAmplitude * SUBJECT_IMPACT_PADDING_MULTIPLIER +
    Math.max(4, Math.min(viewport.width, viewport.height) * 0.012);
  const shadowPadding = shadowBlur * 2.5 + impactAmplitude;

  return (
    <View pointerEvents="none" style={canvasStyle(viewport)}>
      <AnimatedParticleRendererView
        animatedProps={nativeAnimatedProps}
        backgroundColor={skiaBackgroundColor}
        dustDurationSeconds={DUST_DURATION_SECONDS}
        dustStartSeconds={DUST_START_SECONDS}
        edgeParticleStrength={edgeParticleStrength}
        frameUri={frameUri}
        maskUri={maskUri}
        onError={handleNativeError}
        onReady={handleNativeReady}
        pointerEvents="none"
        reduceMotion={reducedMotion}
        seed={seed}
        style={StyleSheet.absoluteFill}
        subjectBounds={nativeSubjectBounds}
      />
      {!nativeReady && (
        <View style={StyleSheet.absoluteFill}>
          <StillCanvas backgroundColor={backgroundColor} frame={frame} viewport={viewport} />
        </View>
      )}
      <Canvas
        colorSpace="srgb"
        pointerEvents="none"
        style={StyleSheet.absoluteFill}>
        <Group
          opacity={shadowOpacity}
          origin={origin}
          transform={shadowTransform}>
          <Rect
            height={pixelBounds.height + shadowPadding * 2}
            width={pixelBounds.width + shadowPadding * 2}
            x={pixelBounds.x - shadowPadding}
            y={pixelBounds.y - shadowPadding}>
            <Shader source={maskSilhouetteRuntimeEffect} uniforms={shadowUniforms}>
              <ImageShader fit="cover" image={mask} rect={rect} tx="decal" ty="decal" />
            </Shader>
            <Blur blur={shadowBlur} mode="clamp" />
          </Rect>
        </Group>

        <Group
          opacity={underlayOpacity}
          origin={origin}
          transform={underlayTransform}>
          <Rect
            height={pixelBounds.height + subjectPadding * 2}
            width={pixelBounds.width + subjectPadding * 2}
            x={pixelBounds.x - subjectPadding}
            y={pixelBounds.y - subjectPadding}>
            <Shader source={maskSilhouetteRuntimeEffect} uniforms={underlayUniforms}>
              <ImageShader fit="cover" image={mask} rect={rect} tx="decal" ty="decal" />
            </Shader>
          </Rect>
        </Group>

        <Group origin={origin} transform={subjectTransform}>
          <Rect
            height={pixelBounds.height + subjectPadding * 2}
            width={pixelBounds.width + subjectPadding * 2}
            x={pixelBounds.x - subjectPadding}
            y={pixelBounds.y - subjectPadding}>
            <Shader source={subjectCutoutRuntimeEffect} uniforms={subjectUniforms}>
              <ImageShader fit="cover" image={frame} rect={rect} tx="clamp" ty="clamp" />
              <ImageShader fit="cover" image={mask} rect={rect} tx="decal" ty="decal" />
            </Shader>
          </Rect>
        </Group>
      </Canvas>
    </View>
  );
}

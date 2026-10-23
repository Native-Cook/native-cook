import type { ParticleRendererViewProps } from '../../../native/subject-segmentation';
import { Skia, type Transforms3d, type Uniforms } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useAnimatedProps, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { DUST_END_SECONDS, MASTER_DURATION_SECONDS, SUBJECT_IMPACT_AMPLITUDE_RATIO } from '../constants';
import type { DispersionViewport, PixelBounds } from '../types';
import { easeOutCubicWorklet, smoothstepWorklet, subjectTimeSecondsWorklet } from '../utils/motion';

type SubjectAnimationOptions = Readonly<{
  progress: SharedValue<number>;
  pixelBounds: PixelBounds;
  viewport: DispersionViewport;
  reducedMotion: boolean;
}>;

/** Derives Skia uniforms and native-renderer props on the UI thread from the same clock. */
export function useSubjectAnimation({
  progress,
  pixelBounds,
  viewport,
  reducedMotion,
}: SubjectAnimationOptions) {
  const shadowColor = useMemo(
    () => Array.from(Skia.Color('#241914')),
    []
  );
  const underlayColor = useMemo(
    () => Array.from(Skia.Color('#FFF1DE')),
    []
  );
  const impactAmplitude = Math.min(
    pixelBounds.width, pixelBounds.height, viewport.width, viewport.height
  ) * SUBJECT_IMPACT_AMPLITUDE_RATIO;

  const nativeTime = useDerivedValue(() => {
    if (reducedMotion) {
      return 0;
    }
    const time = progress.get() * MASTER_DURATION_SECONDS;
    // Match ParticleRenderer's 0.400 s matte reveal. The capture before it,
    // and the solid background after the dust ends, need only one draw.
    return time < 0.400 ? 0 : Math.min(time, DUST_END_SECONDS);
  });
  const nativeProgress = useDerivedValue(() => reducedMotion ? progress.get() : 0);
  const nativeAnimatedProps = useAnimatedProps<ParticleRendererViewProps>(() => ({
    timeSeconds: nativeTime.get(),
    progress: nativeProgress.get(),
  }));
  const nativeSubjectBounds = useMemo(() => [
    pixelBounds.x / viewport.width,
    pixelBounds.y / viewport.height,
    pixelBounds.width / viewport.width,
    pixelBounds.height / viewport.height,
  ], [pixelBounds, viewport.height, viewport.width]);

  // The subject intentionally rests while the dust is moving. A primitive
  // shared value stops unchanged times here, before allocating uniforms or
  // transforms that would otherwise invalidate the entire Skia canvas.
  const subjectTime = useDerivedValue(() => reducedMotion
    ? 0
    : subjectTimeSecondsWorklet(progress.get() * MASTER_DURATION_SECONDS));

  const subjectMotionUniforms = useDerivedValue<Uniforms>(() => {
    return {
      timeSeconds: subjectTime.get(),
      subjectBounds: [pixelBounds.x, pixelBounds.y, pixelBounds.width, pixelBounds.height],
      impactAmplitude,
      reduceMotion: reducedMotion ? 1 : 0,
    };
  });
  const subjectUniforms = useDerivedValue<Uniforms>(() => ({
    ...subjectMotionUniforms.get(),
    resolution: { x: viewport.width, y: viewport.height },
  }));
  const shadowUniforms = useDerivedValue<Uniforms>(() => ({
    ...subjectMotionUniforms.get(),
    silhouetteColor: shadowColor,
  }));
  const underlayUniforms = useDerivedValue<Uniforms>(() => ({
    ...subjectMotionUniforms.get(),
    silhouetteColor: underlayColor,
  }));

  const subjectScale = useDerivedValue(() => {
    if (reducedMotion) {
      return 1;
    }

    const time = subjectTime.get();
    const phase = (time - 1.480) / 0.270;
    return 1 + 0.05 * easeOutCubicWorklet(phase);
  });

  const subjectTransform = useDerivedValue<Transforms3d>(() => [
    { scale: subjectScale.get() },
  ]);

  const shadowOpacity = useDerivedValue(() => {
    if (reducedMotion) {
      return 0;
    }

    const time = subjectTime.get();
    return 0.34 * smoothstepWorklet((time - 1.460) / 0.300);
  });

  const shadowTransform = useDerivedValue<Transforms3d>(() => {
    const scale = subjectScale.get();
    return [
      { translateX: viewport.width * 0.018 },
      { translateY: viewport.height * 0.012 },
      { scale: scale * 1.008 },
    ];
  });

  const underlayOpacity = useDerivedValue(() => {
    if (reducedMotion) {
      return 0;
    }

    const time = subjectTime.get();
    const rise = smoothstepWorklet((time - 1.475) / 0.150);
    const settle = smoothstepWorklet((time - 1.700) / 0.260);
    return rise * (0.78 - 0.48 * settle);
  });

  const underlayTransform = useDerivedValue<Transforms3d>(() => {
    const time = subjectTime.get();
    const rise = easeOutCubicWorklet((time - 1.480) / 0.190);
    const settle = smoothstepWorklet((time - 1.670) / 0.280);
    const scaleX = 1 + 0.115 * rise - 0.057 * settle;
    const scaleY = 1 + 0.052 * easeOutCubicWorklet((time - 1.480) / 0.270);

    return [
      { translateX: -viewport.width * 0.004 },
      { translateY: -viewport.height * 0.0015 },
      { scaleX },
      { scaleY },
    ];
  });

  return {
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
  };
}

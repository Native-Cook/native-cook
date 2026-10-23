import SubjectSegmentation from '../../../native/subject-segmentation';
import { useImage } from '@shopify/react-native-skia';
import { memo, useEffect, useRef } from 'react';
import { useReducedMotion } from 'react-native-reanimated';

import { DISPERSION_BACKGROUND_COLOR } from '../constants';
import type { ParticleDispersionSceneProps } from '../types';
import { LoadingCanvas, StillCanvas } from './captured-frame';
import { MaskBoundScene } from './mask-bound-scene';

type FrameBoundSceneProps = ParticleDispersionSceneProps &
  Readonly<{
    backgroundColor: string;
    playing: boolean;
    replayToken: number;
    systemReducedMotion: boolean;
  }>;

function FrameBoundScene({
  frameUri,
  maskUri,
  viewport,
  playing,
  reducedMotion,
  systemReducedMotion,
  backgroundColor,
  contourPoints,
  subjectBounds,
  onFrameReady,
  onEffectStart,
  onEffectComplete,
  onEffectError,
  edgeParticleStrength = 0,
  replayToken,
}: FrameBoundSceneProps) {
  const frame = useImage(frameUri);
  const readyCallbackRef = useRef(onFrameReady);
  const didNotifyReadyRef = useRef(false);

  useEffect(() => {
    readyCallbackRef.current = onFrameReady;
  }, [onFrameReady]);

  useEffect(() => {
    if (!frame || didNotifyReadyRef.current) {
      return;
    }

    let secondFrame = 0;
    const firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(() => {
        // Mark delivery only after both frames. Strict Mode may cancel the
        // first setup, and its replay must still be able to pause the camera.
        didNotifyReadyRef.current = true;
        if (SubjectSegmentation.performanceLoggingEnabled) {
          SubjectSegmentation.markPerformance?.('frame-decoded');
        }
        readyCallbackRef.current?.();
      });
    });

    return () => {
      cancelAnimationFrame(firstFrame);
      cancelAnimationFrame(secondFrame);
    };
  }, [frame]);

  if (!frame) {
    return <LoadingCanvas viewport={viewport} />;
  }

  if (!maskUri) {
    return (
      <StillCanvas
        backgroundColor={backgroundColor}
        frame={frame}
        viewport={viewport}
      />
    );
  }

  return (
    <MaskBoundScene
      key={maskUri}
      backgroundColor={backgroundColor}
      contourPoints={contourPoints}
      frame={frame}
      frameUri={frameUri}
      maskUri={maskUri}
      onEffectStart={onEffectStart}
      onEffectComplete={onEffectComplete}
      onEffectError={onEffectError}
      edgeParticleStrength={edgeParticleStrength}
      playing={playing}
      reducedMotion={reducedMotion ?? systemReducedMotion}
      replayToken={replayToken}
      subjectBounds={subjectBounds}
      viewport={viewport}
    />
  );
}

/**
 * Full-viewport captured-frame renderer and one-shot result transition.
 * Mount above VisionCamera, then pause the camera after onFrameReady fires.
 */
export const ParticleDispersionScene = memo(function ParticleDispersionScene({
  frameUri,
  maskUri = null,
  viewport,
  playing = true,
  reducedMotion,
  backgroundColor = DISPERSION_BACKGROUND_COLOR,
  contourPoints = null,
  subjectBounds = null,
  onFrameReady,
  onEffectStart,
  onEffectComplete,
  onEffectError,
  edgeParticleStrength = 0,
  replayToken = 0,
}: ParticleDispersionSceneProps) {
  const systemReducedMotion = useReducedMotion();

  return (
    <FrameBoundScene
      key={frameUri}
      backgroundColor={backgroundColor}
      contourPoints={contourPoints}
      frameUri={frameUri}
      maskUri={maskUri}
      onEffectStart={onEffectStart}
      onEffectComplete={onEffectComplete}
      onEffectError={onEffectError}
      edgeParticleStrength={edgeParticleStrength}
      onFrameReady={onFrameReady}
      playing={playing}
      reducedMotion={reducedMotion}
      replayToken={replayToken}
      subjectBounds={subjectBounds}
      systemReducedMotion={systemReducedMotion}
      viewport={viewport}
    />
  );
});

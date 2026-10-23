import SubjectSegmentation, { type ParticleRendererEvent } from '../../../native/subject-segmentation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { cancelAnimation, Easing, ReduceMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { DISPERSION_DURATION_MS, REDUCED_MOTION_DURATION_MS } from '../constants';

type DispersionPlaybackOptions = Readonly<{
  frameUri: string;
  maskUri: string;
  maskReady: boolean;
  shadersReady: boolean;
  playing: boolean;
  reducedMotion: boolean;
  replayToken: number;
  onEffectStart?: () => void;
  onEffectComplete?: () => void;
  onEffectError?: (message: string) => void;
}>;

/** Starts one UI clock after image/shader handles and the native backplate are ready. */
export function useDispersionPlayback({
  frameUri,
  maskUri,
  maskReady,
  shadersReady,
  playing,
  reducedMotion,
  replayToken,
  onEffectStart,
  onEffectComplete,
  onEffectError,
}: DispersionPlaybackOptions) {
  const progress = useSharedValue(0);
  const [nativeReady, setNativeReady] = useState(false);
  const [rendererFailed, setRendererFailed] = useState(false);
  const rendererFailedRef = useRef(false);
  const startCallbackRef = useRef(onEffectStart);
  const completeCallbackRef = useRef(onEffectComplete);
  const errorCallbackRef = useRef(onEffectError);

  useEffect(() => {
    startCallbackRef.current = onEffectStart;
    completeCallbackRef.current = onEffectComplete;
    errorCallbackRef.current = onEffectError;
  }, [onEffectStart, onEffectComplete, onEffectError]);

  const notifyEffectComplete = useCallback(() => {
    if (SubjectSegmentation.performanceLoggingEnabled) {
      SubjectSegmentation.markPerformance?.('playback-end');
    }
    if (!rendererFailedRef.current) {
      completeCallbackRef.current?.();
    }
  }, []);

  const failRenderer = useCallback((message: string) => {
    if (rendererFailedRef.current) {
      return;
    }
    rendererFailedRef.current = true;
    if (SubjectSegmentation.performanceLoggingEnabled) {
      SubjectSegmentation.markPerformance?.('playback-error');
    }
    cancelAnimation(progress);
    setRendererFailed(true);
    errorCallbackRef.current?.(message);
  }, [progress]);

  const handleNativeReady = useCallback(({ nativeEvent }: ParticleRendererEvent) => {
    if (nativeEvent.frameUri === frameUri && nativeEvent.maskUri === maskUri &&
        !rendererFailedRef.current) {
      setNativeReady(true);
    }
  }, [frameUri, maskUri]);

  const handleNativeError = useCallback(({ nativeEvent }: ParticleRendererEvent) => {
    if (nativeEvent.frameUri === frameUri && nativeEvent.maskUri === maskUri) {
      failRenderer('Could not start the effect. Take another photo or reopen the app.');
      if (__DEV__) {
        console.warn('[ParticleRenderer]', nativeEvent.message);
      }
    }
  }, [failRenderer, frameUri, maskUri]);

  useEffect(() => {
    cancelAnimation(progress);

    if (
      !maskReady ||
      !nativeReady ||
      rendererFailed ||
      !shadersReady
    ) {
      progress.set(0);

      if (!playing) {
        return;
      }

      // Skia handles can still defer GPU work; nativeReady confirms the first
      // backplate, not that every animation pipeline has already executed.
      if (rendererFailed) {
        return;
      }
      const fallbackTimer = setTimeout(() => {
        failRenderer('Could not load the photo or subject mask. Please take another photo.');
      }, 10_000);
      return () => clearTimeout(fallbackTimer);
    }

    // `playing` is a one-shot trigger, not a visibility flag. Keep the current
    // value when it turns false so the completed result remains on screen.
    if (!playing) {
      return;
    }

    progress.set(0);
    const duration = reducedMotion
      ? REDUCED_MOTION_DURATION_MS
      : DISPERSION_DURATION_MS;

    startCallbackRef.current?.();
    if (SubjectSegmentation.performanceLoggingEnabled) {
      SubjectSegmentation.markPerformance?.('playback-start');
    }
    progress.set(
      withTiming(
        1,
        {
          duration,
          easing: Easing.linear,
          reduceMotion: ReduceMotion.Never,
        },
        (finished) => {
          'worklet';
          if (finished) {
            scheduleOnRN(notifyEffectComplete);
          }
        }
      )
    );

    return () => cancelAnimation(progress);
  }, [failRenderer, maskReady, nativeReady, notifyEffectComplete, playing, progress,
    reducedMotion, rendererFailed, replayToken, shadersReady]);

  return { progress, nativeReady, rendererFailed, handleNativeReady, handleNativeError };
}

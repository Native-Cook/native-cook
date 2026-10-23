import SubjectSegmentation from '../../../native/subject-segmentation';
import * as Haptics from 'expo-haptics';
import { useIsFocused } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, useWindowDimensions } from 'react-native';
import { CommonResolutions, useCameraDevice, usePhotoOutput } from 'react-native-vision-camera';

import { PHASE_COPY } from '../constants';
import { createCaptureResources } from '../services/capture-resources';
import type { CaptureArtifacts, CapturePhase } from '../types';
import { describeError, getEffectFrameSize, safelyRunHaptic, toFileUri } from '../utils/capture';

type SubjectSegmentationReadiness = 'preparing' | 'ready' | 'error';
type SubjectSegmentationPreparationFailure =
  | 'download'
  | 'storage'
  | 'unsupported'
  | 'rebuild';

function preparationFailure(
  error: unknown,
): SubjectSegmentationPreparationFailure {
  const code =
    typeof error === 'object' && error != null && 'code' in error
      ? String(error.code)
      : '';

  if (code === 'ERR_SUBJECT_SEGMENTATION_STORAGE') {
    return 'storage';
  }
  if (code === 'ERR_SUBJECT_SEGMENTATION_MODEL_UNSUPPORTED') {
    return 'unsupported';
  }
  return 'download';
}

/** Coordinates capture, segmentation, replay, and a flash-free return to preview. */
export function useCameraCapture(hasPermission: boolean) {
  const isFocused = useIsFocused();
  const { width, height, scale: pixelRatio } = useWindowDimensions();
  const device = useCameraDevice('back');
  const photoOutput = usePhotoOutput({
    containerFormat: 'jpeg',
    quality: 0.94,
    qualityPrioritization: 'balanced',
    targetResolution: CommonResolutions.FHD_4_3,
  });
  const cameraOutputs = useMemo(() => [photoOutput], [photoOutput]);

  const [phase, setPhase] = useState<CapturePhase>('preview');
  const [artifacts, setArtifacts] = useState<CaptureArtifacts | null>(null);
  const [cameraActive, setCameraActive] = useState(true);
  const [previewReady, setPreviewReady] = useState(false);
  const [awaitingPreviewReset, setAwaitingPreviewReset] = useState(false);
  const [replayToken, setReplayToken] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [appState, setAppState] = useState(AppState.currentState);
  const [subjectSegmentationReadiness, setSubjectSegmentationReadiness] =
    useState<SubjectSegmentationReadiness>(
      Platform.OS === 'android' ? 'preparing' : 'ready',
    );
  const [
    subjectSegmentationPreparationFailure,
    setSubjectSegmentationPreparationFailure,
  ] = useState<SubjectSegmentationPreparationFailure | null>(null);

  const mountedRef = useRef(true);
  const generationRef = useRef(0);
  const preparationGenerationRef = useRef(0);
  const preparationPromiseRef = useRef<Promise<void> | null>(null);
  const activeRequestIdRef = useRef<string | null>(null);
  const cameraActiveRef = useRef(true);
  const artifactsRef = useRef<CaptureArtifacts | null>(null);
  const pendingPreviewResetRef = useRef<string | null>(null);
  const [resources] = useState(() => createCaptureResources(SubjectSegmentation));
  const {
    cancel: cancelRequest,
    register: registerUri,
    release: releaseRequest,
    releaseLateUri,
  } = resources;
  const announcedPhaseRef = useRef<CapturePhase>('preview');
  const announcedReadinessRef = useRef<SubjectSegmentationReadiness | null>(null);

  const viewport = useMemo(() => ({ width, height }), [height, width]);
  const status = PHASE_COPY[phase];
  const isAppActive = appState === 'active';
  const shouldRunCamera =
    hasPermission && cameraActive && isAppActive && isFocused && device != null;
  const canCapture =
    phase === 'preview' &&
    subjectSegmentationReadiness === 'ready' &&
    previewReady &&
    shouldRunCamera &&
    !awaitingPreviewReset;

  const transitionTo = useCallback((nextPhase: CapturePhase) => {
    if (!mountedRef.current) {
      return;
    }

    setPhase(nextPhase);
  }, []);

  const commitArtifacts = useCallback((next: CaptureArtifacts | null) => {
    artifactsRef.current = next;
    if (mountedRef.current) {
      setArtifacts(next);
    }
  }, []);

  const updateCameraActive = useCallback((active: boolean) => {
    cameraActiveRef.current = active;
    if (mountedRef.current) {
      setCameraActive(active);
    }
  }, []);

  const runSubjectSegmentationPreparation = useCallback(async () => {
    if (Platform.OS !== 'android') {
      return;
    }

    const preparationGeneration = preparationGenerationRef.current + 1;
    preparationGenerationRef.current = preparationGeneration;
    setSubjectSegmentationPreparationFailure(null);
    setSubjectSegmentationReadiness('preparing');

    if (!SubjectSegmentation.prepareSubjectSegmentation) {
      if (
        mountedRef.current &&
        preparationGenerationRef.current === preparationGeneration
      ) {
        setSubjectSegmentationPreparationFailure('rebuild');
        setSubjectSegmentationReadiness('error');
      }
      return;
    }

    let preparation = preparationPromiseRef.current;
    try {
      if (!preparation) {
        preparation = SubjectSegmentation.prepareSubjectSegmentation();
        preparationPromiseRef.current = preparation;
      }

      await preparation;
      if (
        mountedRef.current &&
        preparationGenerationRef.current === preparationGeneration
      ) {
        setSubjectSegmentationReadiness('ready');
      }
    } catch (error) {
      if (preparationPromiseRef.current === preparation) {
        preparationPromiseRef.current = null;
      }
      if (
        mountedRef.current &&
        preparationGenerationRef.current === preparationGeneration
      ) {
        setSubjectSegmentationPreparationFailure(preparationFailure(error));
        setSubjectSegmentationReadiness('error');
      }
    }
  }, []);

  const retrySubjectSegmentationPreparation = useCallback(() => {
    void runSubjectSegmentationPreparation();
  }, [runSubjectSegmentationPreparation]);

  const isCurrentRequest = useCallback(
    (requestId: string, generation: number) =>
      mountedRef.current &&
      activeRequestIdRef.current === requestId &&
      generationRef.current === generation,
    [],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      setAppState(nextState);
      if (nextState !== 'active') {
        setPreviewReady(false);
      }
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    void photoOutput
      .prepareSettings([
        {
          flashMode: 'off',
          enableDistortionCorrection: false,
          enableShutterSound: true,
        },
      ])
      .catch(() => {
        // Pre-warming is an optional latency optimization.
      });
  }, [photoOutput]);

  useEffect(() => {
    // Effects can be replayed by Strict Mode/Fast Refresh in development.
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      generationRef.current += 1;

      resources.dispose();
    };
  }, [resources]);

  useEffect(() => {
    if (Platform.OS !== 'android' || !hasPermission) {
      return;
    }

    let active = true;
    void Promise.resolve().then(() => {
      if (active) {
        void runSubjectSegmentationPreparation();
      }
    });

    return () => {
      active = false;
    };
  }, [hasPermission, runSubjectSegmentationPreparation]);

  useEffect(() => {
    if (
      Platform.OS !== 'android' ||
      !hasPermission ||
      announcedReadinessRef.current === subjectSegmentationReadiness
    ) {
      return;
    }

    announcedReadinessRef.current = subjectSegmentationReadiness;
    const message =
      subjectSegmentationReadiness === 'preparing'
        ? 'Preparing subject isolation.'
        : subjectSegmentationReadiness === 'ready'
          ? 'Subject isolation is ready.'
          : subjectSegmentationPreparationFailure === 'storage'
            ? 'Subject isolation needs more free storage.'
            : subjectSegmentationPreparationFailure === 'unsupported'
              ? 'Subject isolation is not supported on this device.'
              : subjectSegmentationPreparationFailure === 'rebuild'
                ? 'Rebuild the Android development app to prepare subject isolation.'
                : 'Subject isolation could not be prepared. Check the connection and try again.';

    AccessibilityInfo.announceForAccessibility(message);
  }, [
    hasPermission,
    subjectSegmentationPreparationFailure,
    subjectSegmentationReadiness,
  ]);

  useEffect(() => {
    if (announcedPhaseRef.current === phase) {
      return;
    }

    announcedPhaseRef.current = phase;
    const message =
      phase === 'error' && errorMessage
        ? `${status.label}. ${errorMessage}`
        : status.label;

    AccessibilityInfo.announceForAccessibility(message);
  }, [errorMessage, phase, status.label]);

  const handleCaptureError = useCallback(
    (requestId: string, generation: number, error: unknown) => {
      if (!isCurrentRequest(requestId, generation)) {
        return;
      }

      setErrorMessage(describeError(error));
      transitionTo('error');
      safelyRunHaptic(
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
      );
    },
    [isCurrentRequest, transitionTo],
  );

  const handleCameraRuntimeError = useCallback(
    (error: unknown) => {
      // Once a frozen frame exists, a stopped/tearing-down Camera session is
      // no longer authoritative for the visible effect.
      const pendingResetId = pendingPreviewResetRef.current;
      if (artifactsRef.current && !pendingResetId) {
        updateCameraActive(false);
        return;
      }

      if (pendingResetId) {
        pendingPreviewResetRef.current = null;
        setAwaitingPreviewReset(false);
        commitArtifacts(null);
        updateCameraActive(false);
        void releaseRequest(pendingResetId);
      }

      const staleRequestId = activeRequestIdRef.current;
      generationRef.current += 1;
      activeRequestIdRef.current = null;
      updateCameraActive(false);
      setErrorMessage(describeError(error));
      transitionTo('error');

      if (staleRequestId) {
        void cancelRequest(staleRequestId, true);
      }
    },
    [
      cancelRequest,
      commitArtifacts,
      releaseRequest,
      transitionTo,
      updateCameraActive,
    ],
  );

  const capture = useCallback(async () => {
    if (!canCapture || activeRequestIdRef.current != null) {
      return;
    }

    const generation = generationRef.current + 1;
    generationRef.current = generation;
    const requestId = `dispersion-${Date.now()}-${generation}`;

    activeRequestIdRef.current = requestId;
    resources.begin(requestId);
    setErrorMessage(null);
    transitionTo('capturing');

    try {
      const photo = await photoOutput.capturePhotoToFile(
        {
          flashMode: 'off',
          enableDistortionCorrection: false,
          enableShutterSound: true,
        },
        {
          // Fire after the sensor exposure so feedback cannot disturb the shot.
          onDidCapturePhoto: () => {
            safelyRunHaptic(
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft),
            );
          },
        },
      );
      const sourceUri = toFileUri(photo.filePath);

      if (!registerUri(requestId, sourceUri)) {
        await releaseLateUri(sourceUri);
        return;
      }
      if (!isCurrentRequest(requestId, generation)) {
        await releaseRequest(requestId);
        return;
      }

      const frameSize = getEffectFrameSize(width, height, pixelRatio);
      const prepared = await SubjectSegmentation.prepareEffectFrame({
        sourceUri,
        requestId,
        ...frameSize,
      });

      if (!registerUri(requestId, prepared.frameUri)) {
        await releaseLateUri(prepared.frameUri);
        return;
      }
      if (
        prepared.requestId !== requestId ||
        !isCurrentRequest(requestId, generation)
      ) {
        await releaseRequest(requestId);
        return;
      }

      commitArtifacts({
        requestId,
        sourceUri,
        frameUri: prepared.frameUri,
        maskUri: null,
        width: prepared.width,
        height: prepared.height,
        subjectBounds: null,
        contourPoints: [],
      });
      transitionTo('analyzing');

      const segmented = await SubjectSegmentation.segmentEffectFrame({
        frameUri: prepared.frameUri,
        requestId,
        selectionX: 0.5,
        selectionY: 0.5,
      });

      if (!registerUri(requestId, segmented.maskUri)) {
        await releaseLateUri(segmented.maskUri);
        return;
      }
      if (
        segmented.requestId !== requestId ||
        !isCurrentRequest(requestId, generation)
      ) {
        await releaseRequest(requestId);
        return;
      }

      const currentArtifacts = artifactsRef.current;
      if (!currentArtifacts || currentArtifacts.requestId !== requestId) {
        await releaseRequest(requestId);
        return;
      }

      commitArtifacts({
        ...currentArtifacts,
        maskUri: segmented.maskUri,
        subjectBounds: segmented.subjectBounds,
        contourPoints: segmented.contourPoints,
        selectedInstanceCount: segmented.selectedInstanceCount,
        selectionFallback: segmented.selectionFallback,
      });
      transitionTo('effect');
    } catch (error) {
      handleCaptureError(requestId, generation, error);
    }
  }, [
    canCapture,
    commitArtifacts,
    handleCaptureError,
    height,
    isCurrentRequest,
    photoOutput,
    pixelRatio,
    registerUri,
    releaseLateUri,
    releaseRequest,
    resources,
    transitionTo,
    width,
  ]);

  const finishResetOnLivePreview = useCallback(
    (requestId: string) => {
      pendingPreviewResetRef.current = null;
      setAwaitingPreviewReset(false);
      commitArtifacts(null);
      transitionTo('preview');

      requestAnimationFrame(() => {
        void releaseRequest(requestId);
      });
    },
    [commitArtifacts, releaseRequest, transitionTo],
  );

  const handlePreviewStarted = useCallback(() => {
    setPreviewReady(true);
    const pendingRequestId = pendingPreviewResetRef.current;
    if (pendingRequestId) {
      finishResetOnLivePreview(pendingRequestId);
    }
  }, [finishResetOnLivePreview]);

  const retake = useCallback(() => {
    const staleRequestId = activeRequestIdRef.current;
    generationRef.current += 1;
    activeRequestIdRef.current = null;
    setErrorMessage(null);
    setReplayToken(0);

    if (!staleRequestId) {
      commitArtifacts(null);
      updateCameraActive(true);
      transitionTo('preview');
      return;
    }

    void cancelRequest(staleRequestId, false);

    if (!artifactsRef.current) {
      commitArtifacts(null);
      updateCameraActive(true);
      transitionTo('preview');
      void releaseRequest(staleRequestId);
      return;
    }

    if (cameraActiveRef.current && isAppActive && previewReady) {
      commitArtifacts(null);
      transitionTo('preview');
      requestAnimationFrame(() => {
        void releaseRequest(staleRequestId);
      });
      return;
    }

    pendingPreviewResetRef.current = staleRequestId;
    setAwaitingPreviewReset(true);
    setPreviewReady(false);
    updateCameraActive(true);
    transitionTo('preview');
  }, [
    cancelRequest,
    commitArtifacts,
    isAppActive,
    previewReady,
    releaseRequest,
    transitionTo,
    updateCameraActive,
  ]);

  const handleFrameReady = useCallback(
    (requestId: string) => {
      if (
        activeRequestIdRef.current !== requestId ||
        pendingPreviewResetRef.current != null
      ) {
        return;
      }

      updateCameraActive(false);
    },
    [updateCameraActive],
  );

  const handleEffectComplete = useCallback(
    (requestId: string) => {
      if (activeRequestIdRef.current !== requestId) {
        return;
      }

      transitionTo('result');
      safelyRunHaptic(
        Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
      );
    },
    [transitionTo],
  );

  const handleEffectError = useCallback(
    (requestId: string, message: string) => {
      if (activeRequestIdRef.current !== requestId || !mountedRef.current) {
        return;
      }
      setErrorMessage(message);
      transitionTo('error');
    },
    [transitionTo],
  );

  const replay = useCallback(() => {
    if (!artifactsRef.current?.maskUri) {
      return;
    }

    setReplayToken((value) => value + 1);
    transitionTo('effect');
  }, [transitionTo]);

  const resetAfterError = useCallback(() => {
    if (artifactsRef.current) {
      retake();
      return;
    }

    const staleRequestId = activeRequestIdRef.current;
    generationRef.current += 1;
    activeRequestIdRef.current = null;
    setErrorMessage(null);
    updateCameraActive(true);
    transitionTo('preview');

    if (staleRequestId) {
      void cancelRequest(staleRequestId, true);
    }
  }, [cancelRequest, retake, transitionTo, updateCameraActive]);

  const handlePreviewStopped = useCallback(() => setPreviewReady(false), []);

  return {
    artifacts,
    awaitingPreviewReset,
    cameraOutputs,
    canCapture,
    capture,
    device,
    errorMessage,
    handleCameraRuntimeError,
    handleEffectComplete,
    handleEffectError,
    handleFrameReady,
    handlePreviewStarted,
    handlePreviewStopped,
    phase,
    replay,
    replayToken,
    resetAfterError,
    retake,
    retrySubjectSegmentationPreparation,
    shouldRunCamera,
    subjectSegmentationPreparationFailure,
    subjectSegmentationReadiness,
    viewport,
  };
}

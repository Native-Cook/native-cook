import { hasParticleRenderer } from '../../native/subject-segmentation';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Camera } from 'react-native-vision-camera';

import { PlatformMessage } from '../../components/platform-message';
import {
  CAPTURE_ACTION_LABEL,
  CHROME_COPY,
  FocusScrim,
  ReferenceCameraChrome,
  useCameraAccess,
  useCameraCapture,
} from '../../features/camera';
import { DISPERSION_BACKGROUND_COLOR, ParticleDispersionScene } from '../../features/dispersion';

export function CameraScreen() {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const platformVersion = Number.parseInt(String(Platform.Version), 10);
  const supportsSubjectSegmentation =
    Platform.OS === 'android' ? platformVersion >= 24 : platformVersion >= 17;
  const {
    hasPermission,
    canRequestPermission,
    requestingPermission,
    requestCameraPermission,
  } = useCameraAccess(supportsSubjectSegmentation);
  const {
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
  } = useCameraCapture(hasPermission);
  const captureDate = useMemo(
    () =>
      new Intl.DateTimeFormat('en-US', {
        day: 'numeric',
        month: 'short',
      }).format(new Date()),
    [],
  );

  if (!supportsSubjectSegmentation) {
    return (
      <PlatformMessage
        body={Platform.OS === 'android'
          ? 'Subject isolation requires Android 7 or later and Google Play services.'
          : 'Subject isolation requires iOS 17 or later. Run the development build on a compatible iPhone.'}
        title={Platform.OS === 'android' ? 'Android 7+ required' : 'iOS 17+ required'}
      />
    );
  }

  if (!hasPermission) {
    return (
      <PlatformMessage
        actionLabel={
          canRequestPermission ? 'Allow camera access' : 'Open Settings'
        }
        body="Your photos are processed entirely on your device to isolate the subject and create the effect."
        busy={requestingPermission}
        onAction={requestingPermission ? undefined : requestCameraPermission}
        title="Camera access needed"
      />
    );
  }

  if (!hasParticleRenderer) {
    return (
      <PlatformMessage
        body={`This build is missing the particle effect. Rebuild once with npx expo run:${Platform.OS}, then open the new development build.`}
        title="Rebuild required"
      />
    );
  }

  if (!device) {
    return (
      <PlatformMessage
        body={Platform.OS === 'android'
          ? 'No compatible rear camera was found. Try the development build on a physical Android device.'
          : 'No compatible camera was found. Cameras are usually unavailable in iOS Simulator. Try the development build on a physical iPhone.'}
        busy
        title="Looking for a camera…"
      />
    );
  }

  if (
    subjectSegmentationReadiness === 'error' &&
    subjectSegmentationPreparationFailure === 'rebuild'
  ) {
    return (
      <PlatformMessage
        body="This build cannot prepare the Android subject isolation model. Rebuild the native development app, then open the new build."
        title="Rebuild required"
      />
    );
  }

  if (
    subjectSegmentationReadiness === 'error' &&
    subjectSegmentationPreparationFailure === 'unsupported'
  ) {
    return (
      <PlatformMessage
        body="This device cannot install the Google Play services subject isolation model. Update Google Play services or use another supported Android device."
        title="Subject isolation unavailable"
      />
    );
  }

  const isResult = phase === 'result';
  const isError = phase === 'error';
  const readinessOwnsChrome = phase === 'preview';
  const isPreparingSubjectSegmentation =
    readinessOwnsChrome &&
    subjectSegmentationReadiness === 'preparing';
  const isSubjectSegmentationPreparationError =
    readinessOwnsChrome &&
    subjectSegmentationReadiness === 'error';
  const chromeInstruction = awaitingPreviewReset
    ? 'Reopening camera…\nPlease wait a moment'
    : isError && errorMessage
      ? errorMessage
      : isPreparingSubjectSegmentation
        ? 'Preparing subject isolation…\nThis may take a moment the first time'
        : isSubjectSegmentationPreparationError
          ? subjectSegmentationPreparationFailure === 'storage'
            ? 'Subject isolation needs more free storage\nFree up space, then tap to try again'
            : 'Could not prepare subject isolation\nCheck your connection and tap to try again'
          : CHROME_COPY[phase];
  const primaryAction = isResult
    ? replay
    : isError
      ? resetAfterError
      : isSubjectSegmentationPreparationError
        ? retrySubjectSegmentationPreparation
        : capture;
  const captureDisabled = isPreparingSubjectSegmentation
    ? true
    : isSubjectSegmentationPreparationError
      ? false
      : phase === 'preview'
        ? !canCapture
        : phase !== 'result' && phase !== 'error';
  const captureAccessibilityLabel = isPreparingSubjectSegmentation
    ? 'Preparing subject isolation'
    : isSubjectSegmentationPreparationError
      ? 'Retry subject isolation preparation'
      : CAPTURE_ACTION_LABEL[phase];
  const captureAccessibilityHint = isPreparingSubjectSegmentation
    ? 'Wait for the on-device subject isolation model to be ready'
    : isSubjectSegmentationPreparationError
      ? 'Try preparing the on-device subject isolation model again'
      : isResult
        ? 'Replay the effect on the current photo'
        : isError
          ? 'Return to the camera to try again'
          : 'Take a photo and start the dispersion effect';

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      <Camera
        allowHapticsAndSystemSoundsPlayback
        device={device}
        enableNativeTapToFocusGesture
        enableNativeZoomGesture
        implementationMode="compatible"
        isActive={shouldRunCamera}
        mirrorMode="auto"
        orientationSource="interface"
        onError={handleCameraRuntimeError}
        onPreviewStarted={handlePreviewStarted}
        onPreviewStopped={handlePreviewStopped}
        outputs={cameraOutputs}
        resizeMode="cover"
        style={StyleSheet.absoluteFill}
      />

      {artifacts ? (
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <ParticleDispersionScene
            backgroundColor={DISPERSION_BACKGROUND_COLOR}
            frameUri={artifacts.frameUri}
            maskUri={artifacts.maskUri}
            subjectBounds={artifacts.subjectBounds}
            contourPoints={artifacts.contourPoints}
            onEffectComplete={() =>
              handleEffectComplete(artifacts.requestId)
            }
            onEffectError={(message) => handleEffectError(artifacts.requestId, message)}
            onFrameReady={() => handleFrameReady(artifacts.requestId)}
            playing={phase === 'effect'}
            reducedMotion={reducedMotion}
            replayToken={replayToken}
            viewport={viewport}
          />
        </View>
      ) : null}

      <FocusScrim active={artifacts == null && phase === 'capturing'} />

      <View pointerEvents="box-none" style={styles.overlay}>
        <Text
          accessibilityElementsHidden
          maxFontSizeMultiplier={1.2}
          style={[styles.captureDate, { top: insets.top + 34 }]}
        >
          {captureDate}
        </Text>

        <ReferenceCameraChrome
          bottomInset={insets.bottom}
          cancelAccessibilityHint="Discard the result and return to the live camera"
          cancelAccessibilityLabel="Discard photo and retake"
          captureAccessibilityHint={captureAccessibilityHint}
          captureAccessibilityLabel={captureAccessibilityLabel}
          captureBusy={
            isPreparingSubjectSegmentation ||
            phase === 'capturing' ||
            phase === 'analyzing'
          }
          captureDisabled={captureDisabled}
          confirmAccessibilityHint="Replay the effect on the current photo"
          confirmAccessibilityLabel="Replay effect"
          instruction={chromeInstruction}
          libraryDisabled={
            subjectSegmentationReadiness !== 'ready' ||
            phase !== 'preview' ||
            !canCapture
          }
          mode={isResult ? 'result' : 'capture'}
          onCancel={isResult ? retake : undefined}
          onCapture={primaryAction}
          onConfirm={isResult ? replay : undefined}
          style={styles.cameraChrome}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  cameraChrome: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
  },
  captureDate: {
    color: '#FFFFFF',
    fontFamily: 'Georgia',
    fontSize: 20,
    fontWeight: '400',
    left: 24,
    letterSpacing: -0.35,
    lineHeight: 24,
    position: 'absolute',
    textShadowColor: 'rgba(0, 0, 0, 0.2)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 5,
  },
  overlay: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  root: {
    backgroundColor: '#000000',
    flex: 1,
  },
});

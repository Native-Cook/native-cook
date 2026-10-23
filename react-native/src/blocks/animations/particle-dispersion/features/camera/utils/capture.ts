import { EFFECT_MAX_LONG_EDGE, MIN_EFFECT_EDGE } from '../constants';

export function toFileUri(filePath: string) {
  return filePath.startsWith('file://') ? filePath : `file://${filePath}`;
}

function even(value: number) {
  const rounded = Math.max(MIN_EFFECT_EDGE, Math.round(value));
  return rounded % 2 === 0 ? rounded : rounded - 1;
}

export function getEffectFrameSize(
  width: number,
  height: number,
  pixelRatio: number,
) {
  const physicalWidth = Math.max(MIN_EFFECT_EDGE, width * pixelRatio);
  const physicalHeight = Math.max(MIN_EFFECT_EDGE, height * pixelRatio);
  const scale = Math.min(
    1,
    EFFECT_MAX_LONG_EDGE / Math.max(physicalWidth, physicalHeight),
  );

  return {
    targetWidth: even(physicalWidth * scale),
    targetHeight: even(physicalHeight * scale),
  };
}

export function describeError(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return typeof error === 'string'
    ? error
    : 'Could not process the photo. Try again with a clearer subject.';
}

export function safelyRunHaptic(promise: Promise<void>) {
  void promise.catch(() => {
    // Haptic feedback is non-essential and may be disabled by the system.
  });
}

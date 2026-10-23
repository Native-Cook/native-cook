import type { CapturePhase } from './types';

export const EFFECT_MAX_LONG_EDGE = 1600;
export const MIN_EFFECT_EDGE = 2;

export const PHASE_COPY: Record<
  CapturePhase,
  { label: string; busy?: boolean; tone?: 'neutral' | 'success' | 'error' }
> = {
  preview: { label: 'Ready' },
  capturing: { label: 'Capturing…', busy: true },
  analyzing: { label: 'Isolating subject…', busy: true },
  effect: { label: 'Creating effect…', busy: true },
  result: { label: 'Done', tone: 'success' },
  error: { label: 'Something went wrong', tone: 'error' },
};

export const CHROME_COPY: Record<CapturePhase, string> = {
  preview: 'Center your subject in the frame\nTap to capture!',
  capturing: 'Hold still for a moment…\nTaking your photo',
  analyzing: 'Finding your subject…\nEverything stays on your device',
  effect: 'The background is turning into particles…\nYour effect will be ready in a few seconds',
  result: 'Tap the checkmark to replay\nTap cancel to take another photo',
  error: 'Could not create the effect\nTap to try again',
};

export const CAPTURE_ACTION_LABEL: Record<CapturePhase, string> = {
  preview: 'Take photo',
  capturing: 'Taking photo',
  analyzing: 'Finding subject',
  effect: 'Creating effect',
  result: 'Replay effect',
  error: 'Return to camera to try again',
};

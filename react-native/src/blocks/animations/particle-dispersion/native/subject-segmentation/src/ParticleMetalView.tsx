import { requireNativeView } from 'expo';
import { Platform, type ViewProps } from 'react-native';
import Animated from 'react-native-reanimated';

import SubjectSegmentation from './SubjectSegmentationModule';

export type ParticleRendererEvent = {
  nativeEvent: { frameUri: string; maskUri: string; message?: string };
};

export type ParticleRendererViewProps = ViewProps & {
  frameUri: string;
  maskUri: string;
  timeSeconds?: number;
  progress?: number;
  dustStartSeconds: number;
  dustDurationSeconds: number;
  seed: number;
  reduceMotion: boolean;
  backgroundColor: number[];
  /** Bounds in the displayed viewport, normalized to its width and height. */
  subjectBounds: number[];
  /** Opt-in only: leaves the cutout intact while emitting a few edge-coloured grains. */
  edgeParticleStrength?: number;
  onReady: (event: ParticleRendererEvent) => void;
  onError: (event: ParticleRendererEvent) => void;
};

export const hasParticleRenderer =
  (Platform.OS === 'ios' || Platform.OS === 'android') &&
  (SubjectSegmentation.particleRendererVersion ?? 0) >= 1;

// Do not resolve an unregistered native view in an old development binary.
// The camera screen explicitly asks for a rebuild instead of silently showing
// the old, visually different six-layer particle approximation.
const NativeParticleRendererView = hasParticleRenderer
  ? requireNativeView<ParticleRendererViewProps>('SubjectSegmentation')
  : null;

export const AnimatedParticleRendererView = NativeParticleRendererView
  ? Animated.createAnimatedComponent(NativeParticleRendererView)
  : null;

// Keep the original public names available for callers built against the iOS-only API.
export type ParticleMetalViewProps = ParticleRendererViewProps;
export const hasParticleMetalRenderer = hasParticleRenderer;
export const AnimatedParticleMetalView = AnimatedParticleRendererView;

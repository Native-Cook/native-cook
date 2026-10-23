import type { NormalizedPoint, SubjectBounds } from '../../native/subject-segmentation';

export type { NormalizedPoint, SubjectBounds } from '../../native/subject-segmentation';

export type DispersionViewport = Readonly<{
  width: number;
  height: number;
}>;

export type ParticleDispersionSceneProps = Readonly<{
  frameUri: string;
  maskUri?: string | null;
  viewport: DispersionViewport;
  /** Optional exterior contour used only to recover bounds when native bounds are absent. */
  contourPoints?: readonly NormalizedPoint[] | null;
  /** Subject bounds in the same normalized coordinate system as the contour. */
  subjectBounds?: SubjectBounds | null;
  /** Start when the mask and native renderer are ready. Defaults to true. */
  playing?: boolean;
  /** Overrides the device accessibility preference when supplied. */
  reducedMotion?: boolean;
  /** Solid colour revealed below the old background and particles. */
  backgroundColor?: string;
  /** Called once the frame is decoded by Skia and can safely freeze the camera. */
  onFrameReady?: () => void;
  /** Called after both renderers are ready, immediately before starting the timeline. */
  onEffectStart?: () => void;
  /** Called on the JS thread after the master timeline reaches one. */
  onEffectComplete?: () => void;
  /** Decode/render failures are reported, never passed off as a completed effect. */
  onEffectError?: (message: string) => void;
  /** Default 0 keeps particle colours exclusively in the original background. */
  edgeParticleStrength?: number;
  /** Increment to replay without replacing the captured frame or mask. */
  replayToken?: number;
}>;

export type SceneSizeProps = Readonly<{
  viewport: DispersionViewport;
  backgroundColor: string;
}>;

export type PixelBounds = Readonly<{
  x: number;
  y: number;
  width: number;
  height: number;
  centerX: number;
  centerY: number;
}>;

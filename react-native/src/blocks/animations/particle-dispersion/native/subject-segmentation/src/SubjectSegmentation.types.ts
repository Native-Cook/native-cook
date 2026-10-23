export type PrepareEffectFrameOptions = {
  /** A local file URI or absolute local file path returned by the camera. */
  sourceUri: string;
  /** Unique token used to discard or cancel stale work. */
  requestId: string;
  /** Pixel width of the upright, viewport-cropped effect frame. */
  targetWidth: number;
  /** Pixel height of the upright, viewport-cropped effect frame. */
  targetHeight: number;
};

export type PreparedEffectFrame = {
  requestId: string;
  frameUri: string;
  width: number;
  height: number;
};

export type SegmentEffectFrameOptions = {
  /** URI returned by prepareEffectFrame. */
  frameUri: string;
  requestId: string;
  /** Normalized selection coordinate measured from the image's top-left. */
  selectionX?: number;
  /** Normalized selection coordinate measured from the image's top-left. */
  selectionY?: number;
};

/** A normalized image coordinate measured from the image's top-left. */
export type NormalizedPoint = {
  x: number;
  y: number;
};

/** The selected subject's normalized bounds, measured from the image's top-left. */
export type SubjectBounds = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type SegmentedEffectFrame = {
  requestId: string;
  /** Grayscale PNG: 0 is background, 1 is selected foreground coverage. */
  maskUri: string;
  width: number;
  height: number;
  selectedInstanceCount: number;
  /** True when the selected point hit background and the nearest foreground instance was used. */
  selectionFallback: boolean;
  /** Tight bounds of mask pixels whose foreground coverage is at least 0.5. */
  subjectBounds: SubjectBounds;
  /**
   * Closed exterior contour without a duplicated closing point. Points are uniformly
   * spaced by image-space arc length, start near the subject's top-right, and proceed
   * over the top toward its left side.
   */
  contourPoints: NormalizedPoint[];
};

export type SubjectSegmentationModule = {
  /** Opt-in process diagnostics; absent from older installed binaries. */
  readonly performanceLoggingEnabled?: boolean;
  markPerformance?(stage: 'frame-decoded' | 'playback-start' | 'playback-end' | 'playback-error'): void;
  /** Absent in older installed binaries that do not contain the native particle view. */
  readonly particleRendererVersion?: number;
  /**
   * Prepares platform subject isolation before capture. Android may download its
   * Google Play services model; older binaries and iOS do not expose this hook.
   */
  prepareSubjectSegmentation?(): Promise<void>;
  prepareEffectFrame(options: PrepareEffectFrameOptions): Promise<PreparedEffectFrame>;
  segmentEffectFrame(options: SegmentEffectFrameOptions): Promise<SegmentedEffectFrame>;
  cancel(requestId: string): Promise<void>;
  release(uris: string[]): Promise<void>;
};

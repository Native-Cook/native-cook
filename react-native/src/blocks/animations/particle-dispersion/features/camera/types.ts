import type { NormalizedPoint, SubjectBounds } from '../../native/subject-segmentation';

export type CapturePhase =
  | 'preview'
  | 'capturing'
  | 'analyzing'
  | 'effect'
  | 'result'
  | 'error';

export type CaptureArtifacts = {
  requestId: string;
  sourceUri: string;
  frameUri: string;
  maskUri: string | null;
  width: number;
  height: number;
  subjectBounds: SubjectBounds | null;
  contourPoints: readonly NormalizedPoint[];
  selectedInstanceCount?: number;
  selectionFallback?: boolean;
};

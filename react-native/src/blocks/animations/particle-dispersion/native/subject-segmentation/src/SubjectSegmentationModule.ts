import { requireOptionalNativeModule } from 'expo';

import type { SubjectSegmentationModule } from './SubjectSegmentation.types';

const nativeModule =
  requireOptionalNativeModule<SubjectSegmentationModule>('SubjectSegmentation');

async function unavailable(): Promise<never> {
  throw new Error(
    'SubjectSegmentation is unavailable in this build. Rebuild the native development app after installing the module.',
  );
}

const SubjectSegmentation: SubjectSegmentationModule = nativeModule ?? {
  prepareSubjectSegmentation: unavailable,
  prepareEffectFrame: unavailable,
  segmentEffectFrame: unavailable,
  cancel: async () => {},
  release: async () => {},
};

export default SubjectSegmentation;

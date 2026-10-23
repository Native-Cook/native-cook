import { ComponentStage } from '@/shared/component-stage';

import { SleepRangeScale } from './SleepRangeScale';

export default function SleepRangeScaleDemo() {
  return (
    <ComponentStage background="#cfcdd8" height={460}>
      <SleepRangeScale autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { StepPlannerProgress } from './StepPlannerProgress';

export default function StepPlannerProgressDemo() {
  return (
    <ComponentStage background="#0a0a0a" height={446}>
      <StepPlannerProgress autoPlay />
    </ComponentStage>
  );
}

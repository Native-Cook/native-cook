import { ComponentStage } from '@/shared/component-stage';

import { RecruiterSteps } from './RecruiterSteps';

export default function RecruiterStepsDemo() {
  return (
    <ComponentStage background="#3f6fae" height={240}>
      <RecruiterSteps autoPlay />
    </ComponentStage>
  );
}

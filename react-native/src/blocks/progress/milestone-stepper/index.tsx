import { ComponentStage } from '@/shared/component-stage';

import { MilestoneStepper } from './MilestoneStepper';

export default function MilestoneStepperDemo() {
  return (
    <ComponentStage background="#e7e6e9" height={340}>
      <MilestoneStepper autoPlay />
    </ComponentStage>
  );
}

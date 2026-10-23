import { ComponentStage } from '@/shared/component-stage';

import { BudgetScrubScale } from './BudgetScrubScale';

export default function BudgetScrubScaleDemo() {
  return (
    <ComponentStage background="#e6e6ec" height={520}>
      <BudgetScrubScale autoPlay />
    </ComponentStage>
  );
}

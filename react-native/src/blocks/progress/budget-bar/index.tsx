import { ComponentStage } from '@/shared/component-stage';

import { BudgetBar } from './BudgetBar';

export default function BudgetBarDemo() {
  return (
    <ComponentStage background="#fdfdfd" height={360}>
      <BudgetBar autoPlay />
    </ComponentStage>
  );
}

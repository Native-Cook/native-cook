import { ComponentStage } from '@/shared/component-stage';

import { BranchingPriceWheel } from './BranchingPriceWheel';

export default function BranchingPriceWheelDemo() {
  return (
    <ComponentStage background="#eef3f7" height={580}>
      <BranchingPriceWheel autoPlay />
    </ComponentStage>
  );
}

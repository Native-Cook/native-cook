import { ComponentStage } from '@/shared/component-stage';

import { StreakStepsWidget } from './StreakStepsWidget';

export default function StreakStepsWidgetDemo() {
  return (
    <ComponentStage background="#141414" height={390}>
      <StreakStepsWidget autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { DeliveryStepsProgress } from './DeliveryStepsProgress';

export default function DeliveryStepsProgressDemo() {
  return (
    <ComponentStage background="#fdfdfd" height={280}>
      <DeliveryStepsProgress autoPlay />
    </ComponentStage>
  );
}

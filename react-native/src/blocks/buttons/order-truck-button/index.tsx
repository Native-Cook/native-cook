import { ComponentStage } from '@/shared/component-stage';

import { OrderTruckButton } from './OrderTruckButton';

export default function OrderTruckButtonDemo() {
  return (
    <ComponentStage background="#e1ebf7">
      <OrderTruckButton autoPlay />
    </ComponentStage>
  );
}

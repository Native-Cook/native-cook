import { ComponentStage } from '@/shared/component-stage';

import { PayScanButton } from './PayScanButton';

export default function PayScanButtonDemo() {
  return (
    <ComponentStage background="#e7fdff">
      <PayScanButton autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { BarcodeProgress } from './BarcodeProgress';

export default function BarcodeProgressDemo() {
  return (
    <ComponentStage background="#cccccc" height={280}>
      <BarcodeProgress autoPlay />
    </ComponentStage>
  );
}

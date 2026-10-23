import { ComponentStage } from '@/shared/component-stage';

import { DataUsageRing } from './DataUsageRing';

export default function DataUsageRingDemo() {
  return (
    <ComponentStage background="#f4f4f4" height={440}>
      <DataUsageRing autoPlay />
    </ComponentStage>
  );
}

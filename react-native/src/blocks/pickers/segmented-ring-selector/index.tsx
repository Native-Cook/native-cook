import { ComponentStage } from '@/shared/component-stage';

import { SegmentedRingSelector } from './SegmentedRingSelector';

export default function SegmentedRingSelectorDemo() {
  return (
    <ComponentStage background="#22343a" height={380}>
      <SegmentedRingSelector autoPlay />
    </ComponentStage>
  );
}

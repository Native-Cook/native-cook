import { ComponentStage } from '@/shared/component-stage';

import { ScaleArcWeightPicker } from './ScaleArcWeightPicker';

export default function ScaleArcWeightPickerDemo() {
  return (
    <ComponentStage background="#f4f3fa" height={440}>
      <ScaleArcWeightPicker autoPlay />
    </ComponentStage>
  );
}

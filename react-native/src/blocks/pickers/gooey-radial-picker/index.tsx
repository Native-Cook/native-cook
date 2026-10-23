import { ComponentStage } from '@/shared/component-stage';

import { GooeyRadialPicker } from './GooeyRadialPicker';

export default function GooeyRadialPickerDemo() {
  return (
    <ComponentStage background="#e9eef2" height={440}>
      <GooeyRadialPicker autoPlay />
    </ComponentStage>
  );
}

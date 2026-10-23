import { ComponentStage } from '@/shared/component-stage';

import { ArcActivityPicker } from './ArcActivityPicker';

export default function ArcActivityPickerDemo() {
  return (
    <ComponentStage background="#F3F3F3" height={700}>
      <ArcActivityPicker autoPlay />
    </ComponentStage>
  );
}

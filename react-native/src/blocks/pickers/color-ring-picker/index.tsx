import { ComponentStage } from '@/shared/component-stage';

import { ColorRingPicker } from './ColorRingPicker';

export default function ColorRingPickerDemo() {
  return (
    <ComponentStage background="#121417" height={460}>
      <ColorRingPicker autoPlay />
    </ComponentStage>
  );
}

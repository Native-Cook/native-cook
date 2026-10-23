import { ComponentStage } from '@/shared/component-stage';

import { DiscTrayPicker } from './DiscTrayPicker';

export default function DiscTrayPickerDemo() {
  return (
    <ComponentStage background="#efefef" height={660}>
      <DiscTrayPicker autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { ReloadButton } from './ReloadButton';

export default function ReloadButtonDemo() {
  return (
    <ComponentStage background="#f7f7f7">
      <ReloadButton autoPlay />
    </ComponentStage>
  );
}

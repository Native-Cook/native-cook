import { ComponentStage } from '@/shared/component-stage';

import { GenerateFillButton } from './GenerateFillButton';

export default function GenerateFillButtonDemo() {
  return (
    <ComponentStage background="#ffffff">
      <GenerateFillButton autoPlay />
    </ComponentStage>
  );
}

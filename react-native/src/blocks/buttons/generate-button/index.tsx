import { ComponentStage } from '@/shared/component-stage';

import { GenerateButton } from './GenerateButton';

export default function GenerateButtonDemo() {
  return (
    <ComponentStage background="#111111" height={290}>
      <GenerateButton active />
    </ComponentStage>
  );
}

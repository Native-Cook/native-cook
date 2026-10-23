import { ComponentStage } from '@/shared/component-stage';

import { DepthButtonGroup } from './DepthButton';

export default function DepthButtonDemo() {
  return (
    <ComponentStage background="#f5e9d3">
      <DepthButtonGroup autoPlay />
    </ComponentStage>
  );
}

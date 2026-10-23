import { ComponentStage } from '@/shared/component-stage';

import { GradientSubscribeButton } from './GradientSubscribeButton';

export default function GradientSubscribeButtonDemo() {
  return (
    <ComponentStage background="#000000">
      <GradientSubscribeButton autoPlay />
    </ComponentStage>
  );
}

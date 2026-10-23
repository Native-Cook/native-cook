import { ComponentStage } from '@/shared/component-stage';

import { ThinkingButton } from './ThinkingButton';

export default function ThinkingButtonDemo() {
  return (
    <ComponentStage background="#17191b">
      <ThinkingButton autoPlay />
    </ComponentStage>
  );
}

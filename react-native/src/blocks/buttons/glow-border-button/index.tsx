import { ComponentStage } from '@/shared/component-stage';

import { GlowBorderButton } from './GlowBorderButton';

export default function GlowBorderButtonDemo() {
  return (
    <ComponentStage background="#121317">
      <GlowBorderButton mode="always" />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { LikeBurstButton } from './LikeBurstButton';

export default function LikeBurstButtonDemo() {
  return (
    <ComponentStage background="#feeeef">
      <LikeBurstButton autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { SwipeReplyComposer } from './SwipeReplyComposer';

export default function SwipeReplyComposerDemo() {
  return (
    <ComponentStage background="#111110" height={500}>
      <SwipeReplyComposer autoPlay />
    </ComponentStage>
  );
}

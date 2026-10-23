import { ComponentStage } from '@/shared/component-stage';

import { DestinationSwipeStack } from './DestinationSwipeStack';

export default function DestinationSwipeStackDemo() {
  return (
    <ComponentStage background="#f4f4f7" height={620}>
      <DestinationSwipeStack autoPlay />
    </ComponentStage>
  );
}

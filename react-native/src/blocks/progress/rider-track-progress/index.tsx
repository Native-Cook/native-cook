import { ComponentStage } from '@/shared/component-stage';

import { RiderTrackProgress } from './RiderTrackProgress';

export default function RiderTrackProgressDemo() {
  return (
    <ComponentStage background="#02040d" height={320}>
      <RiderTrackProgress autoPlay />
    </ComponentStage>
  );
}

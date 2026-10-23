import { ComponentStage } from '@/shared/component-stage';

import { TravelPassStack } from './TravelPassStack';

export default function TravelPassStackDemo() {
  return (
    <ComponentStage background="#1d1e30" height={660}>
      <TravelPassStack autoPlay />
    </ComponentStage>
  );
}

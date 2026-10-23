import { ComponentStage } from '@/shared/component-stage';

import { RisingTourStack } from './RisingTourStack';

export default function RisingTourStackDemo() {
  return (
    <ComponentStage background="#ebebeb" height={680}>
      <RisingTourStack autoPlay />
    </ComponentStage>
  );
}

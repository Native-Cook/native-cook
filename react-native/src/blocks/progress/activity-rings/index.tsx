import { ComponentStage } from '@/shared/component-stage';

import { ActivityRings } from './ActivityRings';

export default function ActivityRingsDemo() {
  return (
    <ComponentStage background="#4b4b4b" height={280}>
      <ActivityRings autoPlay />
    </ComponentStage>
  );
}

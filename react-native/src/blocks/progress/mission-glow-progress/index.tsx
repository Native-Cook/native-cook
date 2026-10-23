import { ComponentStage } from '@/shared/component-stage';

import { MissionGlowProgress } from './MissionGlowProgress';

export default function MissionGlowProgressDemo() {
  return (
    <ComponentStage background="#08060a" height={300}>
      <MissionGlowProgress autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { PowerRateSegments } from './PowerRateSegments';

export default function PowerRateSegmentsDemo() {
  return (
    <ComponentStage background="#020403" height={384}>
      <PowerRateSegments autoPlay />
    </ComponentStage>
  );
}

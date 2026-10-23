import { ComponentStage } from '@/shared/component-stage';

import { StressArcGauge } from './StressArcGauge';

export default function StressArcGaugeDemo() {
  return (
    <ComponentStage background="#ffffff" height={290}>
      <StressArcGauge autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { TransitRouteProgress } from './TransitRouteProgress';

export default function TransitRouteProgressDemo() {
  return (
    <ComponentStage background="#e7e7e7" height={250}>
      <TransitRouteProgress autoPlay />
    </ComponentStage>
  );
}

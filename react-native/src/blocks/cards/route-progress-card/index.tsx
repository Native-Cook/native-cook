import { ComponentStage } from '@/shared/component-stage';

import { RouteProgressCard } from './RouteProgressCard';

export default function RouteProgressCardDemo() {
  return (
    <ComponentStage background="#e8e8ea" height={400}>
      <RouteProgressCard autoPlay />
    </ComponentStage>
  );
}

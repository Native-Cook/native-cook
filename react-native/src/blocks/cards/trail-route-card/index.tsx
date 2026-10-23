import { ComponentStage } from '@/shared/component-stage';

import { TrailRouteCard } from './TrailRouteCard';

export default function TrailRouteCardDemo() {
  return (
    <ComponentStage background="#f0f0f0" height={480}>
      <TrailRouteCard autoPlay />
    </ComponentStage>
  );
}

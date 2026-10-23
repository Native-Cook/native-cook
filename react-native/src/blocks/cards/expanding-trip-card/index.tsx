import { ComponentStage } from '@/shared/component-stage';

import { ExpandingTripCard } from './ExpandingTripCard';

export default function ExpandingTripCardDemo() {
  return (
    <ComponentStage background="#e9eaec" height={680}>
      <ExpandingTripCard autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { TypedDestinationCard } from './TypedDestinationCard';

export default function TypedDestinationCardDemo() {
  return (
    <ComponentStage background="#f5f3ee" height={520}>
      <TypedDestinationCard autoPlay />
    </ComponentStage>
  );
}

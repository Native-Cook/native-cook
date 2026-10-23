import { ComponentStage } from '@/shared/component-stage';

import { TravelStatStoryCard } from './TravelStatStoryCard';

export default function TravelStatStoryCardDemo() {
  return (
    <ComponentStage background="#e9e8e4" height={560}>
      <TravelStatStoryCard autoPlay />
    </ComponentStage>
  );
}

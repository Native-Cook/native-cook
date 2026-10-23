import { ComponentStage } from '@/shared/component-stage';

import { ResortWeatherCard } from './ResortWeatherCard';

export default function ResortWeatherCardDemo() {
  return (
    <ComponentStage background="#060709" height={660}>
      <ResortWeatherCard autoPlay />
    </ComponentStage>
  );
}

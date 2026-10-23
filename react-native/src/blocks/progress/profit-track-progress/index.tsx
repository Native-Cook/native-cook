import { ComponentStage } from '@/shared/component-stage';

import { ProfitTrackProgress } from './ProfitTrackProgress';

export default function ProfitTrackProgressDemo() {
  return (
    <ComponentStage background="#fafafa" height={320}>
      <ProfitTrackProgress autoPlay />
    </ComponentStage>
  );
}

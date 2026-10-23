import { ComponentStage } from '@/shared/component-stage';

import { FannedGuideCards } from './FannedGuideCards';

export default function FannedGuideCardsDemo() {
  return (
    <ComponentStage background="#f0f2ef" height={520}>
      <FannedGuideCards autoPlay />
    </ComponentStage>
  );
}

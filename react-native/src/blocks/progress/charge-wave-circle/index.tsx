import { ComponentStage } from '@/shared/component-stage';

import { ChargeWaveCircle } from './ChargeWaveCircle';

export default function ChargeWaveCircleDemo() {
  return (
    <ComponentStage background="#141a16" height={470}>
      <ChargeWaveCircle autoPlay />
    </ComponentStage>
  );
}

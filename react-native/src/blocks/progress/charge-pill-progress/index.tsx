import { ComponentStage } from '@/shared/component-stage';

import { ChargePillProgress } from './ChargePillProgress';

export default function ChargePillProgressDemo() {
  return (
    <ComponentStage background="#ebebeb" height={250}>
      <ChargePillProgress autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { TimerBorderProgress } from './TimerBorderProgress';

export default function TimerBorderProgressDemo() {
  return (
    <ComponentStage background="#e7e7e7" height={320}>
      <TimerBorderProgress autoPlay />
    </ComponentStage>
  );
}

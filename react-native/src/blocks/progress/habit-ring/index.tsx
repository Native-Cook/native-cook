import { ComponentStage } from '@/shared/component-stage';

import { HabitRing } from './HabitRing';

export default function HabitRingDemo() {
  return (
    <ComponentStage background="#25272c" height={450}>
      <HabitRing autoPlay />
    </ComponentStage>
  );
}

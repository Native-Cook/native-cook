import { ComponentStage } from '@/shared/component-stage';

import { DayNightToggle } from './DayNightToggle';

export default function DayNightToggleDemo() {
  return (
    <ComponentStage background="#e0edf6">
      <DayNightToggle autoPlay backdrop />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { SunburstTimerDial } from './SunburstTimerDial';

export default function SunburstTimerDialDemo() {
  return (
    <ComponentStage background="#F5F4F1" height={380}>
      <SunburstTimerDial autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { MoonOrbitTimeScrubber } from './MoonOrbitTimeScrubber';

export default function MoonOrbitTimeScrubberDemo() {
  return (
    <ComponentStage background="#05061A" height={680}>
      <MoonOrbitTimeScrubber autoPlay />
    </ComponentStage>
  );
}

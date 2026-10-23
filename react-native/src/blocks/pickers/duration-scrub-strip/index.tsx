import { ComponentStage } from '@/shared/component-stage';

import { DurationScrubStrip } from './DurationScrubStrip';

export default function DurationScrubStripDemo() {
  return (
    <ComponentStage background="#161616" height={200}>
      <DurationScrubStrip autoPlay />
    </ComponentStage>
  );
}

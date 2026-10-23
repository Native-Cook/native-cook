import { ComponentStage } from '@/shared/component-stage';

import { DateLensStrip } from './DateLensStrip';

export default function DateLensStripDemo() {
  return (
    <ComponentStage background="#E6E6E8" height={220}>
      <DateLensStrip autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { GrowingComposerPill } from './GrowingComposerPill';

export default function GrowingComposerPillDemo() {
  return (
    <ComponentStage background="#f1eff8" height={460}>
      <GrowingComposerPill autoPlay />
    </ComponentStage>
  );
}

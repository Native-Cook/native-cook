import { ComponentStage } from '@/shared/component-stage';

import { MorphingMicPill } from './MorphingMicPill';

export default function MorphingMicPillDemo() {
  return (
    <ComponentStage background="#e6e4df" height={480}>
      <MorphingMicPill autoPlay />
    </ComponentStage>
  );
}

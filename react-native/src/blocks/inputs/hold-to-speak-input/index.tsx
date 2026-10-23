import { ComponentStage } from '@/shared/component-stage';

import { HoldToSpeakInput } from './HoldToSpeakInput';

export default function HoldToSpeakInputDemo() {
  return (
    <ComponentStage background="#2b2b2b" height={300}>
      <HoldToSpeakInput autoPlay />
    </ComponentStage>
  );
}

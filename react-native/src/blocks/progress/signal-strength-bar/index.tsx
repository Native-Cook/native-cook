import { ComponentStage } from '@/shared/component-stage';

import { SignalStrengthBar } from './SignalStrengthBar';

export default function SignalStrengthBarDemo() {
  return (
    <ComponentStage background="#4f6b49" height={310}>
      <SignalStrengthBar autoPlay />
    </ComponentStage>
  );
}

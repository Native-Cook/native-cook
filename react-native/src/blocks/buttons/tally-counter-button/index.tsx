import { ComponentStage } from '@/shared/component-stage';

import { TallyCounterButton } from './TallyCounterButton';

export default function TallyCounterButtonDemo() {
  return (
    <ComponentStage background="#2c2c2c" height={240}>
      <TallyCounterButton autoPlay />
    </ComponentStage>
  );
}

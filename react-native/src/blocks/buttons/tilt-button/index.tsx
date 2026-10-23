import { ComponentStage } from '@/shared/component-stage';

import { TiltButton } from './TiltButton';

export default function TiltButtonDemo() {
  return (
    <ComponentStage background="#000000">
      <TiltButton autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { RulerDial } from './RulerDial';

export default function RulerDialDemo() {
  return (
    <ComponentStage background="#f5f5f6" height={230}>
      <RulerDial autoPlay />
    </ComponentStage>
  );
}

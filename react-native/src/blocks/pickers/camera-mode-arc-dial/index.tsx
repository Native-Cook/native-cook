import { ComponentStage } from '@/shared/component-stage';

import { CameraModeArcDial } from './CameraModeArcDial';

export default function CameraModeArcDialDemo() {
  return (
    <ComponentStage background="#000000" height={360}>
      <CameraModeArcDial autoPlay />
    </ComponentStage>
  );
}

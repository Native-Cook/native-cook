import { ComponentStage } from '@/shared/component-stage';

import { WalkRadiusDial } from './WalkRadiusDial';

export default function WalkRadiusDialDemo() {
  return (
    <ComponentStage background="#ffffff" height={540}>
      <WalkRadiusDial autoPlay />
    </ComponentStage>
  );
}

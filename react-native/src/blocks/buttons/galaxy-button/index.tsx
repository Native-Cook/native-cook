import { ComponentStage } from '@/shared/component-stage';

import { GalaxyButton } from './GalaxyButton';

export default function GalaxyButtonDemo() {
  return (
    <ComponentStage background="#04000f" height={250}>
      <GalaxyButton autoPlay />
    </ComponentStage>
  );
}

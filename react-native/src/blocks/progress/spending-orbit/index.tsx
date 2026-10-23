import { ComponentStage } from '@/shared/component-stage';

import { SpendingOrbit } from './SpendingOrbit';

export default function SpendingOrbitDemo() {
  return (
    <ComponentStage background="#191919" height={370}>
      <SpendingOrbit autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { WaterIntakeFill } from './WaterIntakeFill';

export default function WaterIntakeFillDemo() {
  return (
    <ComponentStage background="#b9cf7a" height={480}>
      <WaterIntakeFill autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { BendingLineHumiditySlider } from './BendingLineHumiditySlider';

export default function BendingLineHumiditySliderDemo() {
  return (
    <ComponentStage background="#070a26" height={640}>
      <BendingLineHumiditySlider autoPlay />
    </ComponentStage>
  );
}

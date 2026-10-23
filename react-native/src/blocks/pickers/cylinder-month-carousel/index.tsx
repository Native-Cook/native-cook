import { ComponentStage } from '@/shared/component-stage';

import { CylinderMonthCarousel } from './CylinderMonthCarousel';

export default function CylinderMonthCarouselDemo() {
  return (
    <ComponentStage background="#1b1b1d" height={540}>
      <CylinderMonthCarousel autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { FrostedDestinationCarousel } from './FrostedDestinationCarousel';

export default function FrostedDestinationCarouselDemo() {
  return (
    <ComponentStage background="#e6eaf0" height={440}>
      <FrostedDestinationCarousel autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { SlideToBookButton } from './SlideToBookButton';

export default function SlideToBookButtonDemo() {
  return (
    <ComponentStage background="#fafafa">
      <SlideToBookButton autoPlay />
    </ComponentStage>
  );
}

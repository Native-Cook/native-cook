import { ComponentStage } from '@/shared/component-stage';

import { StoryProgressBars } from './StoryProgressBars';

export default function StoryProgressBarsDemo() {
  return (
    <ComponentStage background="#101010" height={400}>
      <StoryProgressBars autoPlay />
    </ComponentStage>
  );
}

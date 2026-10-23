import { ComponentStage } from '@/shared/component-stage';

import { ThinkingFrameSearch } from './ThinkingFrameSearch';

export default function ThinkingFrameSearchDemo() {
  return (
    <ComponentStage background="#e8e8e8" height={340}>
      <ThinkingFrameSearch autoPlay />
    </ComponentStage>
  );
}

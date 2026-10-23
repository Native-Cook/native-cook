import { ComponentStage } from '@/shared/component-stage';

import { OrbitPromptPill } from './OrbitPromptPill';

export default function OrbitPromptPillDemo() {
  return (
    <ComponentStage background="#f4f4f4" height={380}>
      <OrbitPromptPill autoPlay />
    </ComponentStage>
  );
}

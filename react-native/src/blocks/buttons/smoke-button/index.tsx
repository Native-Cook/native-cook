import { ComponentStage } from '@/shared/component-stage';

import { SmokeButton } from './SmokeButton';

export default function SmokeButtonDemo() {
  return (
    <ComponentStage background="#050505" height={260}>
      <SmokeButton />
    </ComponentStage>
  );
}

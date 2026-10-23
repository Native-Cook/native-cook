import { ComponentStage } from '@/shared/component-stage';

import { CutoutWindowCard } from './CutoutWindowCard';

export default function CutoutWindowCardDemo() {
  return (
    <ComponentStage background="#0e1411" height={600}>
      <CutoutWindowCard autoPlay />
    </ComponentStage>
  );
}

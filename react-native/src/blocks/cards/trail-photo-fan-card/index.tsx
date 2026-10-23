import { ComponentStage } from '@/shared/component-stage';

import { TrailPhotoFanCard } from './TrailPhotoFanCard';

export default function TrailPhotoFanCardDemo() {
  return (
    <ComponentStage background="#1f9fc4" height={620}>
      <TrailPhotoFanCard autoPlay />
    </ComponentStage>
  );
}

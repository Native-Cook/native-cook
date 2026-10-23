import { ComponentStage } from '@/shared/component-stage';

import { JellyAttachCapsule } from './JellyAttachCapsule';

export default function JellyAttachCapsuleDemo() {
  return (
    <ComponentStage background="#eeeafe" height={240}>
      <JellyAttachCapsule autoPlay />
    </ComponentStage>
  );
}

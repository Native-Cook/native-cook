import { ComponentStage } from '@/shared/component-stage';

import { PostageStampReveal } from './PostageStampReveal';

export default function PostageStampRevealDemo() {
  return (
    <ComponentStage background="#c8d9f0" height={520}>
      <PostageStampReveal autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { GlowBorderChatbar } from './GlowBorderChatbar';

export default function GlowBorderChatbarDemo() {
  return (
    <ComponentStage background="#000000" height={300}>
      <GlowBorderChatbar autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { AttachmentTrayInput } from './AttachmentTrayInput';

export default function AttachmentTrayInputDemo() {
  return (
    <ComponentStage background="#ffffff" height={400}>
      <AttachmentTrayInput autoPlay />
    </ComponentStage>
  );
}

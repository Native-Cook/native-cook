import { ComponentStage } from '@/shared/component-stage';

import { FoldingMessageBar } from './FoldingMessageBar';

export default function FoldingMessageBarDemo() {
  return (
    <ComponentStage background="#00080a" height={390}>
      <FoldingMessageBar autoPlay />
    </ComponentStage>
  );
}

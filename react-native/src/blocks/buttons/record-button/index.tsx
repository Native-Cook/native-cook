import { ComponentStage } from '@/shared/component-stage';

import { RecordButton } from './RecordButton';

export default function RecordButtonDemo() {
  return (
    <ComponentStage background="#050505" height={280}>
      <RecordButton autoPlay />
    </ComponentStage>
  );
}

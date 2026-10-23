import { ComponentStage } from '@/shared/component-stage';

import { LetterWaveButton } from './LetterWaveButton';

export default function LetterWaveButtonDemo() {
  return (
    <ComponentStage background="#fcfcfc">
      <LetterWaveButton autoPlay />
    </ComponentStage>
  );
}

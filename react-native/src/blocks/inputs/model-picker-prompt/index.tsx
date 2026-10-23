import { ComponentStage } from '@/shared/component-stage';

import { ModelPickerPrompt } from './ModelPickerPrompt';

export default function ModelPickerPromptDemo() {
  return (
    <ComponentStage background="#0e0d0c" height={520}>
      <ModelPickerPrompt autoPlay />
    </ComponentStage>
  );
}

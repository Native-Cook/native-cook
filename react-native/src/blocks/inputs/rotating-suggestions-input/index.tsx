import { ComponentStage } from '@/shared/component-stage';

import { RotatingSuggestionsInput } from './RotatingSuggestionsInput';

export default function RotatingSuggestionsInputDemo() {
  return (
    <ComponentStage background="#e4e4e9" height={440}>
      <RotatingSuggestionsInput autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { SubmitConfettiButton } from './SubmitConfettiButton';

export default function SubmitConfettiButtonDemo() {
  return (
    <ComponentStage background="#eff4fc" height={280}>
      <SubmitConfettiButton autoPlay />
    </ComponentStage>
  );
}

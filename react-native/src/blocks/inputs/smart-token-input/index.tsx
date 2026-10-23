import { ComponentStage } from '@/shared/component-stage';

import { SmartTokenInput } from './SmartTokenInput';

export default function SmartTokenInputDemo() {
  return (
    <ComponentStage background="#cfd4d9" height={260}>
      <SmartTokenInput autoPlay />
    </ComponentStage>
  );
}

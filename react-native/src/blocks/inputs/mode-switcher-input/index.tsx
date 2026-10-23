import { ComponentStage } from '@/shared/component-stage';

import { ModeSwitcherInput } from './ModeSwitcherInput';

export default function ModeSwitcherInputDemo() {
  return (
    <ComponentStage background="#0b0c12" height={420}>
      <ModeSwitcherInput autoPlay />
    </ComponentStage>
  );
}

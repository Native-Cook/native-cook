import { ComponentStage } from '@/shared/component-stage';

import { NotchRadialMenu } from './NotchRadialMenu';

export default function NotchRadialMenuDemo() {
  return (
    <ComponentStage background="#120b24" height={440}>
      <NotchRadialMenu autoPlay />
    </ComponentStage>
  );
}

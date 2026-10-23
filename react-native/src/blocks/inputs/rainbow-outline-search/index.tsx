import { ComponentStage } from '@/shared/component-stage';

import { RainbowOutlineSearch } from './RainbowOutlineSearch';

export default function RainbowOutlineSearchDemo() {
  return (
    <ComponentStage background="#f3f3f3" height={240}>
      <RainbowOutlineSearch autoPlay />
    </ComponentStage>
  );
}

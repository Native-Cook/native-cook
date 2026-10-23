import { ComponentStage } from '@/shared/component-stage';

import { GlassLoadingButton } from './GlassLoadingButton';

export default function GlassLoadingButtonDemo() {
  return (
    <ComponentStage background="#1a1a1a" height={240}>
      <GlassLoadingButton autoPlay />
    </ComponentStage>
  );
}

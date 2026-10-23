import { ComponentStage } from '@/shared/component-stage';

import { PulseFabButton } from './PulseFabButton';

export default function PulseFabButtonDemo() {
  return (
    <ComponentStage background="#f4f9ff">
      <PulseFabButton autoPlay />
    </ComponentStage>
  );
}

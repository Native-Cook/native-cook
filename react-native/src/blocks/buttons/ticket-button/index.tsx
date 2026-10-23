import { ComponentStage } from '@/shared/component-stage';

import { TicketButton } from './TicketButton';

export default function TicketButtonDemo() {
  return (
    <ComponentStage background="#190e1c">
      <TicketButton autoPlay />
    </ComponentStage>
  );
}

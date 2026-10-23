import { ComponentStage } from '@/shared/component-stage';

import { StampedTicketPile } from './StampedTicketPile';

export default function StampedTicketPileDemo() {
  return (
    <ComponentStage background="#f7f1f1" height={560}>
      <StampedTicketPile autoPlay />
    </ComponentStage>
  );
}

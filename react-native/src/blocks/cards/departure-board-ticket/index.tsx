import { ComponentStage } from '@/shared/component-stage';

import { DepartureBoardTicket } from './DepartureBoardTicket';

export default function DepartureBoardTicketDemo() {
  return (
    <ComponentStage background="#f3f3f5" height={640}>
      <DepartureBoardTicket autoPlay />
    </ComponentStage>
  );
}

import { ComponentStage } from '@/shared/component-stage';

import { StayBookingCard } from './StayBookingCard';

export default function StayBookingCardDemo() {
  return (
    <ComponentStage background="#ececee" height={520}>
      <StayBookingCard autoPlay />
    </ComponentStage>
  );
}

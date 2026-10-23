import { ComponentStage } from '@/shared/component-stage';

import { FlightStatusCard } from './FlightStatusCard';

export default function FlightStatusCardDemo() {
  return (
    <ComponentStage background="#f1f1f1" height={420}>
      <FlightStatusCard autoPlay />
    </ComponentStage>
  );
}

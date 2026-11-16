import { CardVacuumSeal } from './CardVacuumSeal';

export { CardVacuumSeal, LOOP } from './CardVacuumSeal';
export { type SealOptions, useVacuumSeal, type VacuumSealHandle } from './cvs-controller';
export { VacuumSeal, type VacuumSealProps } from './VacuumSeal';

export default function CardVacuumSealDemo() {
  return <CardVacuumSeal autoPlay />;
}

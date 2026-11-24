import { CoachMarkSpotlight } from './CoachMarkSpotlight';

export { LOOP } from './motion';
export {
  adoptTour,
  CoachMarks,
  goToStep,
  hideTour,
  openTour,
  tipPlaces,
  useCoachMotion,
  type CoachMarksProps,
  type CoachMotion,
} from './CoachMarks';
export { CoachMarkSpotlight, type CoachMarkSpotlightProps } from './CoachMarkSpotlight';

export default function CoachMarkSpotlightDemo() {
  return <CoachMarkSpotlight autoPlay />;
}

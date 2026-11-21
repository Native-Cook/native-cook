import { TumbleCardRoutine } from './TumbleCardRoutine';

/** A health tracker's daily routine as a deck: each card is thrown off through a motion blur as long as its speed, and the next one pulls into focus out of a blurred slot. */
export default function TumbleCardRoutineDemo() {
  return <TumbleCardRoutine autoPlay />;
}

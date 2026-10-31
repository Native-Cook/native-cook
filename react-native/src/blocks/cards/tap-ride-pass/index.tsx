import { TapRidePass } from './TapRidePass';

/** A transit gate: drag the pass to the reader, its field draws the pass in, and the pass becomes the ride ticket. */
export default function TapRidePassDemo() {
  return <TapRidePass autoPlay />;
}

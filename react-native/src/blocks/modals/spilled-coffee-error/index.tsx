import { SpilledCoffeeError } from './SpilledCoffeeError';

/** The error screen, retrying (and failing again) by itself. */
export default function SpilledCoffeeErrorDemo() {
  return <SpilledCoffeeError autoPlay />;
}

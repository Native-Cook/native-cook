import { SkyTimeScrubber } from './SkyTimeScrubber';
import { DEMO_NOW } from './sts-motion';

// The loop opens on golden hour: 18:06, twelve minutes before the 18:18 sunset, the sun 3.4 degrees above the horizon.
export default function SkyTimeScrubDemo() {
  return <SkyTimeScrubber autoPlay now={DEMO_NOW} />;
}

import { SkyScreenTime } from './SkyScreenTime';

export { SkyScreenTime, LOOP } from './SkyScreenTime';

/** Gallery recording uses the deterministic loop; reusable instances default to manual mode. */
export default function SkyScreenTimeDemo() {
  return <SkyScreenTime autoPlay />;
}

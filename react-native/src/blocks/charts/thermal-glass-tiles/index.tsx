import { RunReplay } from './RunReplay';
export { RunReplay, type RunReplayProps } from './RunReplay';
export { ThermalField, GlassTile, ThermalBackdrop, type ThermalFieldProps, type GlassTileProps } from './tgt-material';
export { LOOP } from './tgt-motion';
export default function ThermalGlassTilesDemo() {
  return <RunReplay autoPlay />;
}

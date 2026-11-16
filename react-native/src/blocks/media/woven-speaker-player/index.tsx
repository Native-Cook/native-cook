import { WovenSpeakerPlayer } from './WovenSpeakerPlayer';

export { WovenSpeaker, useWovenDisplay } from './WovenSpeaker';
export type { WovenSpeakerProps, WovenSpeakerProProps, WovenTokens, WovenTrack } from './WovenSpeaker';
export { LOOP } from './wsp-motion';

export default function WovenSpeakerPlayerDemo() {
  return <WovenSpeakerPlayer autoPlay />;
}

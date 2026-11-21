import { router } from 'expo-router';

import { WaveFieldPad } from './WaveFieldPad';

/** A sleep-sound app's mix pad: one ember puck sets two values, and the engraved plate under it dents, rings and, held still, sings its Chladni figure. */
export default function WaveFieldPadDemo() {
  return (
    <WaveFieldPad
      autoPlay
      onClose={() => {
        if (router.canGoBack()) router.back();
        else router.replace('/');
      }}
    />
  );
}

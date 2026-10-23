import { ComponentStage } from '@/shared/component-stage';

import { PlaybackDial } from './PlaybackDial';

export default function PlaybackDialDemo() {
  return (
    <ComponentStage background="#040404" height={340}>
      <PlaybackDial autoPlay />
    </ComponentStage>
  );
}

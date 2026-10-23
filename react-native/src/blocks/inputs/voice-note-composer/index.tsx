import { ComponentStage } from '@/shared/component-stage';

import { VoiceNoteComposer } from './VoiceNoteComposer';

export default function VoiceNoteComposerDemo() {
  return (
    <ComponentStage background="#161d27" height={340}>
      <VoiceNoteComposer autoPlay />
    </ComponentStage>
  );
}

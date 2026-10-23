import { ComponentStage } from '@/shared/component-stage';

import { MoodCharacterDial } from './MoodCharacterDial';

export default function MoodCharacterDialDemo() {
  return (
    <ComponentStage background="#141b1c" height={660}>
      <MoodCharacterDial autoPlay />
    </ComponentStage>
  );
}

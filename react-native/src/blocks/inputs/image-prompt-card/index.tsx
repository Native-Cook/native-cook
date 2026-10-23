import { ComponentStage } from '@/shared/component-stage';

import { ImagePromptCard } from './ImagePromptCard';

export default function ImagePromptCardDemo() {
  return (
    <ComponentStage background="#f3f3f5" height={460}>
      <ImagePromptCard autoPlay />
    </ComponentStage>
  );
}

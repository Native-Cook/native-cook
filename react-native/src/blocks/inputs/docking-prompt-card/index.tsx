import { ComponentStage } from '@/shared/component-stage';

import { DockingPromptCard } from './DockingPromptCard';

export default function DockingPromptCardDemo() {
  return (
    <ComponentStage background="#171717" height={480}>
      <DockingPromptCard autoPlay />
    </ComponentStage>
  );
}

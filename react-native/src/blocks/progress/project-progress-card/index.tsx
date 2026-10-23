import { ComponentStage } from '@/shared/component-stage';

import { ProjectProgressCard } from './ProjectProgressCard';

export default function ProjectProgressCardDemo() {
  return (
    <ComponentStage background="#f5f7f8" height={330}>
      <ProjectProgressCard autoPlay />
    </ComponentStage>
  );
}
